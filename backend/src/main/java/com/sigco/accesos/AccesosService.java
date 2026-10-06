package com.sigco.accesos;

import com.sigco.accesos.dto.AccesosDtos.AuditoriaRespuesta;
import com.sigco.accesos.dto.AccesosDtos.PermisoRespuesta;
import com.sigco.accesos.dto.AccesosDtos.PermisosDelRol;
import com.sigco.accesos.dto.AccesosDtos.RolRespuesta;
import com.sigco.common.exception.RecursoNoEncontradoException;
import com.sigco.common.exception.ReglaDeNegocioException;
import com.sigco.usuarios.Usuario;
import com.sigco.usuarios.UsuarioRepository;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Modulo 14 — Accesos.
 *
 * Administra que puede hacer cada rol y expone la auditoria. Es el ultimo
 * modulo del proyecto y el que habilita la delegacion controlada: los capataces
 * hacen tareas operativas sin ver la informacion financiera.
 *
 * La activacion de Spring Security sobre todo el sistema es la otra mitad de
 * este modulo, y vive en com.sigco.seguridad.
 */
@Service
public class AccesosService {

    /** Cuantos registros de auditoria devuelve una consulta sin filtros. */
    private static final int TOPE_AUDITORIA = 200;

    private static final long CUALQUIER_USUARIO = 0L;
    private static final String CUALQUIER_MODULO = "";
    private static final LocalDateTime DESDE_SIEMPRE = LocalDateTime.of(2000, 1, 1, 0, 0);
    private static final LocalDateTime HASTA_SIEMPRE = LocalDateTime.of(2999, 12, 31, 23, 59);

    /**
     * Presupuestacion y Cobros son, segun el informe, indelegables igual que
     * compras.aprobar: "quedan reservadas exclusivamente al rol de dueño y no
     * pueden asignarse a otros roles". Se bloquean ambos permisos de cada
     * modulo (ver y editar), no solo editar: la matriz del informe tambien le
     * niega la consulta a Capataz General y de Obra en los dos modulos.
     */
    private static final List<String> PERMISOS_RESERVADOS_A_DUENO = List.of(
            "presupuestos.ver", "presupuestos.editar",
            "cobros.ver", "cobros.editar");

    private final RolRepository rolRepositorio;
    private final PermisoRepository permisoRepositorio;
    private final RegistroAuditoriaRepository auditoriaRepositorio;
    private final UsuarioRepository usuarioRepositorio;
    private final ServicioAuditoria auditoria;

    /**
     * Constructor: Spring le entrega las piezas que necesita para leer y
     * guardar roles, permisos, cuentas y registros de auditoría.
     */
    public AccesosService(RolRepository rolRepositorio,
                          PermisoRepository permisoRepositorio,
                          RegistroAuditoriaRepository auditoriaRepositorio,
                          UsuarioRepository usuarioRepositorio,
                          ServicioAuditoria auditoria) {
        this.rolRepositorio = rolRepositorio;
        this.permisoRepositorio = permisoRepositorio;
        this.auditoriaRepositorio = auditoriaRepositorio;
        this.usuarioRepositorio = usuarioRepositorio;
        this.auditoria = auditoria;
    }

    // ------------------------------------------------------------------
    //  Roles y permisos
    // ------------------------------------------------------------------

    /**
     * Devuelve todos los permisos del sistema, ordenados por módulo y por
     * nombre.
     */
    @Transactional(readOnly = true)
    public List<PermisoRespuesta> permisos() {
        return permisoRepositorio.findAllByOrderByModuloAscNombrePermisoAsc().stream()
                .map(PermisoRespuesta::desde)
                .toList();
    }

    /**
     * Devuelve todos los roles con sus permisos y la cantidad de cuentas
     * activas que tiene cada uno.
     */
    @Transactional(readOnly = true)
    public List<RolRespuesta> roles() {
        return rolRepositorio.todosConPermisos().stream()
                .map(r -> RolRespuesta.desde(r, usuarioRepositorio
                        .countByRolNombreRolAndEstado(r.getNombreRol(), Usuario.ESTADO_ACTIVO)))
                .toList();
    }

    /**
     * Reemplaza los permisos de un rol.
     *
     * La regla que protege al sistema de si mismo: al rol Dueño no se le puede
     * quitar la administracion de accesos. Si se pudiera, bastaria una casilla
     * desmarcada por error para que nadie —ni siquiera el dueño— pudiera volver
     * a entrar a la pantalla que lo arregla. El unico camino de vuelta seria
     * editar la base a mano.
     */
    @Transactional
    public RolRespuesta definirPermisos(Long idRol, PermisosDelRol solicitud) {
        Rol rol = rolRepositorio.conPermisos(idRol)
                .orElseThrow(() -> new RecursoNoEncontradoException("Rol", idRol));

        Set<Permiso> nuevos = new LinkedHashSet<>(
                permisoRepositorio.findAllById(solicitud.idsPermisos()));

        if (nuevos.size() != solicitud.idsPermisos().size()) {
            throw new ReglaDeNegocioException(
                    "Alguno de los permisos indicados no existe.");
        }

        if (rol.esDueno()) {
            exigirQueConserve(nuevos, Permiso.ACCESOS_EDITAR,
                    "administrar los permisos");
            exigirQueConserve(nuevos, Permiso.USUARIOS_EDITAR,
                    "administrar las cuentas de acceso");
        }

        // La aprobacion de un pedido es indelegable segun el informe. La regla
        // se sostiene aca y no solo en la carga inicial: sin esto, marcar una
        // casilla en la pantalla de Accesos la anularia en silencio.
        if (!rol.esDueno() && contiene(nuevos, Permiso.COMPRAS_APROBAR)) {
            throw new ReglaDeNegocioException(
                    "La aprobación de pedidos es indelegable: solo el rol Dueño puede tenerla.");
        }

        // El informe (modulo 14) dice que presupuestar y cobrar "quedan
        // reservadas exclusivamente al rol de dueño y no pueden asignarse a
        // otros roles". Hasta ahora eso solo se respetaba por los datos de
        // semilla (la migracion V13 nunca le asigna estos permisos a un
        // capataz), pero nada en este metodo lo impedia: desde esta misma
        // pantalla se le podia tildar presupuestos.editar o cobros.ver a
        // Capataz General sin que el backend lo rechazara. Es el mismo tipo
        // de guarda que ya existe arriba para compras.aprobar, extendida a los
        // otros dos modulos no delegables.
        if (!rol.esDueno()) {
            for (String permisoReservado : PERMISOS_RESERVADOS_A_DUENO) {
                if (contiene(nuevos, permisoReservado)) {
                    throw new ReglaDeNegocioException(
                            "\"" + permisoReservado + "\" es indelegable: solo el rol Dueño "
                            + "puede tenerlo.");
                }
            }
        }

        rol.definirPermisos(nuevos);

        auditoria.registrar("Cambio de permisos del rol " + rol.getNombreRol()
                + " (" + nuevos.size() + " permisos)", "Accesos");

        return RolRespuesta.desde(rol, usuarioRepositorio
                .countByRolNombreRolAndEstado(rol.getNombreRol(), Usuario.ESTADO_ACTIVO));
    }

    // ------------------------------------------------------------------
    //  Auditoría
    // ------------------------------------------------------------------

    /**
     * Ultimas acciones registradas.
     *
     * Devuelve como mucho doscientas: la auditoria es la tabla que mas crece
     * del sistema y traerla entera colgaria la pantalla. Los filtros por usuario
     * y modulo son la forma de llegar a lo viejo.
     *
     * Ningun parametro viaja en null hacia la consulta: PostgreSQL no puede
     * inferir el tipo de un parametro nulo, lo asume bytea y la consulta falla.
     * Por eso los "sin filtro" son 0 y cadena vacia.
     */
    @Transactional(readOnly = true)
    public List<AuditoriaRespuesta> auditoria(Long idUsuario, String modulo) {
        return auditoriaRepositorio.buscar(
                        idUsuario != null ? idUsuario : CUALQUIER_USUARIO,
                        modulo != null ? modulo : CUALQUIER_MODULO,
                        DESDE_SIEMPRE, HASTA_SIEMPRE,
                        PageRequest.of(0, TOPE_AUDITORIA)).stream()
                .map(AuditoriaRespuesta::desde)
                .toList();
    }

    // ------------------------------------------------------------------
    //  Auxiliares
    // ------------------------------------------------------------------

    /**
     * Indica si dentro de un conjunto de permisos está el permiso con ese
     * nombre.
     */
    private boolean contiene(Set<Permiso> permisos, String nombre) {
        return permisos.stream().anyMatch(p -> nombre.equals(p.getNombrePermiso()));
    }

    /**
     * Frena el cambio si al rol Dueño se le está quitando un permiso
     * imprescindible. Muestra un mensaje que explica por qué no se puede.
     */
    private void exigirQueConserve(Set<Permiso> nuevos, String permiso, String queHace) {
        if (!contiene(nuevos, permiso)) {
            throw new ReglaDeNegocioException(
                    "El rol Dueño no puede quedarse sin el permiso para " + queHace
                    + ": nadie podría volver a entrar a esta pantalla para corregirlo.");
        }
    }
}
