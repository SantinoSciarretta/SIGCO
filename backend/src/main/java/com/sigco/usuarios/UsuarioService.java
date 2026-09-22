package com.sigco.usuarios;

import com.sigco.accesos.Rol;
import com.sigco.accesos.RolRepository;
import com.sigco.accesos.ServicioAuditoria;
import com.sigco.common.exception.RecursoNoEncontradoException;
import com.sigco.common.exception.ReglaDeNegocioException;
import com.sigco.personal.Operario;
import com.sigco.personal.OperarioRepository;
import com.sigco.usuarios.dto.UsuarioDtos.BajaUsuario;
import com.sigco.usuarios.dto.UsuarioDtos.CambioContrasena;
import com.sigco.usuarios.dto.UsuarioDtos.CambioRol;
import com.sigco.usuarios.dto.UsuarioDtos.NuevoUsuario;
import com.sigco.usuarios.dto.UsuarioDtos.UsuarioRespuesta;
import com.sigco.usuarios.dto.UsuarioDtos.VinculoOperario;
import java.util.List;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Modulo 13 — Usuarios.
 *
 * Administra las cuentas de acceso: quien puede entrar al sistema y con que
 * rol. Es distinto de Personal, que registra a todos los operarios trabajen o
 * no con el sistema.
 *
 * Va anteultimo por decision del proyecto: los doce modulos de negocio se
 * desarrollaron y probaron sin login, y la autenticacion se puso encima al
 * final.
 */
@Service
public class UsuarioService {

    private final UsuarioRepository repositorio;
    private final RolRepository rolRepositorio;
    private final OperarioRepository operarioRepositorio;
    private final PasswordEncoder codificador;
    private final ServicioAuditoria auditoria;

    /** Rechaza las contrasenas previsibles, que el largo minimo no filtra. */
    private final PoliticaDeContrasenas politica;

    public UsuarioService(UsuarioRepository repositorio,
                          RolRepository rolRepositorio,
                          OperarioRepository operarioRepositorio,
                          PasswordEncoder codificador,
                          ServicioAuditoria auditoria,
                          PoliticaDeContrasenas politica) {
        this.repositorio = repositorio;
        this.rolRepositorio = rolRepositorio;
        this.operarioRepositorio = operarioRepositorio;
        this.codificador = codificador;
        this.auditoria = auditoria;
        this.politica = politica;
    }

    // ------------------------------------------------------------------
    //  Consultas
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<UsuarioRespuesta> listar() {
        return repositorio.todosConRol().stream()
                .map(UsuarioRespuesta::desde)
                .toList();
    }

    @Transactional(readOnly = true)
    public UsuarioRespuesta obtener(Long id) {
        return UsuarioRespuesta.desde(buscarOFallar(id));
    }

    // ------------------------------------------------------------------
    //  Alta
    // ------------------------------------------------------------------

    @Transactional
    public UsuarioRespuesta crear(NuevoUsuario solicitud) {
        // Sin distinguir mayusculas: "Ricardo" y "ricardo" serian dos cuentas
        // que la gente lee como la misma, y al entrar nadie sabria cual usar.
        if (repositorio.existsByNombreUsuarioIgnoreCase(solicitud.nombreUsuario())) {
            throw new ReglaDeNegocioException(
                    "Ya existe una cuenta con el usuario \"" + solicitud.nombreUsuario() + "\".");
        }

        Rol rol = buscarRolOFallar(solicitud.idRol());
        Operario operario = resolverOperario(solicitud.idOperario());

        // La inicial también se valida, aunque el titular esté obligado a
        // cambiarla: mientras no lo haga, es la contraseña que protege la
        // cuenta, y "granica2026" no protege nada.
        politica.validar(solicitud.contrasena(), solicitud.nombreUsuario());

        // La contrasena se cifra ACA y nunca se guarda en claro, ni siquiera un
        // instante en una variable que despues se persista.
        Usuario usuario = new Usuario(
                solicitud.nombreUsuario().trim(),
                codificador.encode(solicitud.contrasena()),
                rol,
                operario);

        Usuario guardado = repositorio.save(usuario);

        auditoria.registrar("Alta de la cuenta \"" + guardado.getNombreUsuario()
                + "\" con rol " + rol.getNombreRol(), "Usuarios");

        return UsuarioRespuesta.desde(guardado);
    }

    // ------------------------------------------------------------------
    //  Contraseña
    // ------------------------------------------------------------------

    /**
     * Cambia la contrasena de una cuenta.
     *
     * Hay dos caminos y la diferencia importa:
     *
     *   - Uno cambia LA PROPIA: tiene que escribir la actual. Asi, alguien que
     *     encuentre una sesion abierta no puede apropiarse de la cuenta.
     *   - El dueño resetea la de OTRO: no necesita la actual, porque no la sabe
     *     (para eso existe el reseteo). Queda auditado.
     *
     * @param esLaPropia si quien pide el cambio es el dueño de la cuenta
     */
    @Transactional
    public void cambiarContrasena(Long id, CambioContrasena cambio, boolean esLaPropia) {
        Usuario usuario = buscarOFallar(id);

        if (esLaPropia) {
            if (cambio.contrasenaActual() == null || cambio.contrasenaActual().isBlank()) {
                throw new ReglaDeNegocioException(
                        "Para cambiar tu contraseña tenés que escribir la actual.");
            }
            // La comparacion la hace la entidad contra su propio hash: asi el
            // hash no sale de Usuario ni para verificarlo.
            if (!usuario.verificarContra(cambio.contrasenaActual(), codificador::matches)) {
                throw new ReglaDeNegocioException("La contraseña actual no es correcta.");
            }
        }

        // La nueva no puede ser la que ya tenia.
        //
        // Sin esta validacion, el cambio obligatorio del primer ingreso se
        // saltea solo: alcanzaria con volver a escribir la contrasena conocida
        // para que el sistema la diera por cambiada y levantara la obligacion.
        if (usuario.verificarContra(cambio.contrasenaNueva(), codificador::matches)) {
            throw new ReglaDeNegocioException(
                    "La contraseña nueva tiene que ser distinta de la actual.");
        }

        // Y no puede ser una previsible. Es el punto donde mas importa: el
        // sistema acaba de obligar a cambiarla, y es cuando mas tienta poner
        // cualquier cosa para salir del paso.
        politica.validar(cambio.contrasenaNueva(), usuario.getNombreUsuario());

        // Dos metodos distintos y no uno con un booleano: cambiar la propia
        // levanta la obligacion de cambiarla, resetear la de otro la impone,
        // porque deja una contrasena que conocen dos personas.
        if (esLaPropia) {
            usuario.cambiarContrasena(codificador.encode(cambio.contrasenaNueva()));
        } else {
            usuario.resetearContrasena(codificador.encode(cambio.contrasenaNueva()));
        }

        auditoria.registrar(esLaPropia
                        ? "Cambio de su propia contraseña"
                        : "Reseteo de la contraseña de \"" + usuario.getNombreUsuario() + "\"",
                "Usuarios");
    }

    // ------------------------------------------------------------------
    //  Rol y vínculo con Personal
    // ------------------------------------------------------------------

    @Transactional
    public UsuarioRespuesta cambiarRol(Long id, CambioRol cambio) {
        Usuario usuario = buscarOFallar(id);
        Rol nuevo = buscarRolOFallar(cambio.idRol());

        // Si el sistema se queda sin ningun dueño activo, nadie puede volver a
        // administrar usuarios ni aprobar un presupuesto: el sistema queda
        // trabado y solo se destraba tocando la base a mano.
        if (usuario.getRol().esDueno() && !nuevo.esDueno()) {
            exigirQueQuedeOtroDueno(usuario);
        }

        usuario.cambiarRol(nuevo);

        auditoria.registrar("Cambio de rol de \"" + usuario.getNombreUsuario()
                + "\" a " + nuevo.getNombreRol(), "Usuarios");

        return UsuarioRespuesta.desde(usuario);
    }

    @Transactional
    public UsuarioRespuesta vincularOperario(Long id, VinculoOperario vinculo) {
        Usuario usuario = buscarOFallar(id);
        usuario.vincularOperario(resolverOperario(vinculo.idOperario()));
        return UsuarioRespuesta.desde(usuario);
    }

    // ------------------------------------------------------------------
    //  Baja y reactivación
    // ------------------------------------------------------------------

    /**
     * Da de baja una cuenta.
     *
     * No se elimina: la auditoria guarda quien hizo cada cosa y borrar el
     * usuario dejaria registros apuntando a nadie. Ademas gasto, pedido, hito e
     * inasistencia lo referencian por clave foranea.
     */
    @Transactional
    public UsuarioRespuesta desactivar(Long id, BajaUsuario baja, Long idQuienPide) {
        Usuario usuario = buscarOFallar(id);

        // Nadie se da de baja a si mismo: quedaria afuera del sistema en el
        // acto, y si ademas era el ultimo dueño, nadie podria reactivarlo.
        if (usuario.getIdUsuario().equals(idQuienPide)) {
            throw new ReglaDeNegocioException(
                    "No podés dar de baja tu propia cuenta. Pedíselo a otro usuario con rol Dueño.");
        }

        if (usuario.getRol().esDueno()) {
            exigirQueQuedeOtroDueno(usuario);
        }

        usuario.desactivar(baja.motivo());

        auditoria.registrar("Baja de la cuenta \"" + usuario.getNombreUsuario()
                + "\": " + baja.motivo(), "Usuarios");

        return UsuarioRespuesta.desde(usuario);
    }

    @Transactional
    public UsuarioRespuesta reactivar(Long id) {
        Usuario usuario = buscarOFallar(id);
        usuario.reactivar();
        auditoria.registrar("Reactivación de la cuenta \"" + usuario.getNombreUsuario() + "\"",
                "Usuarios");
        return UsuarioRespuesta.desde(usuario);
    }

    // ------------------------------------------------------------------
    //  Auxiliares
    // ------------------------------------------------------------------

    private Usuario buscarOFallar(Long id) {
        return repositorio.completo(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario", id));
    }

    private Rol buscarRolOFallar(Long idRol) {
        return rolRepositorio.findById(idRol)
                .orElseThrow(() -> new RecursoNoEncontradoException("Rol", idRol));
    }

    /**
     * El vinculo con Personal es uno a uno: un operario no puede tener dos
     * cuentas. Lo garantiza tambien un indice unico parcial en la base, pero
     * verificarlo aca permite explicar el motivo en lugar de devolver un error
     * de restriccion de integridad.
     */
    private Operario resolverOperario(Long idOperario) {
        if (idOperario == null) {
            return null;
        }
        if (repositorio.existsByOperarioIdOperario(idOperario)) {
            throw new ReglaDeNegocioException(
                    "Ese operario ya tiene una cuenta de acceso.");
        }
        return operarioRepositorio.findById(idOperario)
                .orElseThrow(() -> new RecursoNoEncontradoException("Operario", idOperario));
    }

    private void exigirQueQuedeOtroDueno(Usuario usuario) {
        long duenosActivos = repositorio.countByRolNombreRolAndEstado(
                Rol.DUENO, Usuario.ESTADO_ACTIVO);

        // Se compara contra 1 contando al propio usuario: si es el unico dueño
        // activo, sacarlo deja el sistema sin nadie que pueda administrarlo.
        if (duenosActivos <= 1 && usuario.estaActivo()) {
            throw new ReglaDeNegocioException(
                    "Es la única cuenta activa con rol Dueño. Creá o activá otra antes, "
                    + "o el sistema queda sin nadie que pueda administrarlo.");
        }
    }
}
