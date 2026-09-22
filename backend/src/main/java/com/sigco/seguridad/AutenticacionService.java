package com.sigco.seguridad;

import com.sigco.accesos.Permiso;
import com.sigco.accesos.ServicioAuditoria;
import com.sigco.common.exception.ReglaDeNegocioException;
import com.sigco.usuarios.Usuario;
import com.sigco.usuarios.UsuarioRepository;
import com.sigco.usuarios.dto.UsuarioDtos.Credenciales;
import com.sigco.usuarios.dto.UsuarioDtos.Sesion;
import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ingreso al sistema.
 *
 * ------------------------------------------------------------------
 *  Por que el mensaje de error es siempre el mismo
 * ------------------------------------------------------------------
 *
 * Usuario inexistente y contrasena incorrecta devuelven exactamente el mismo
 * texto. Es deliberado: si el sistema contestara "ese usuario no existe",
 * cualquiera podria probar nombres hasta descubrir cuales son cuentas reales, y
 * despues concentrarse en adivinar la contrasena de una que sabe que existe.
 *
 * Una cuenta dada de baja SI recibe un mensaje distinto: quien la usaba tiene
 * que entender que no es un problema de tipeo, y ya sabia que la cuenta existia.
 */
@Service
public class AutenticacionService {

    private final UsuarioRepository repositorio;
    private final PasswordEncoder codificador;
    private final ServicioJwt servicioJwt;
    private final ServicioAuditoria auditoria;
    private final RegistroDeIntentos intentos;

    public AutenticacionService(UsuarioRepository repositorio,
                                PasswordEncoder codificador,
                                ServicioJwt servicioJwt,
                                ServicioAuditoria auditoria,
                                RegistroDeIntentos intentos) {
        this.repositorio = repositorio;
        this.codificador = codificador;
        this.servicioJwt = servicioJwt;
        this.auditoria = auditoria;
        this.intentos = intentos;
    }

    @Transactional
    public Sesion ingresar(Credenciales credenciales) {
        Optional<Usuario> encontrado = repositorio.porNombre(credenciales.nombreUsuario().trim());

        // El bloqueo se verifica ANTES de comparar la contrasena, y el orden es
        // la diferencia entre defender y aparentar que se defiende.
        //
        // Si se verificara despues, quien esta probando contrasenas podria
        // seguir probandolas todas: el sistema le diria "incorrecta" hasta que
        // acertara, y recien ahi le avisaria que la cuenta esta bloqueada. Le
        // habriamos impedido entrar en ese momento, pero le habriamos confirmado
        // cual era la contrasena, y le alcanza con esperar a que el bloqueo se
        // venza. Verificando antes, durante los minutos del bloqueo NINGUN
        // intento se evalua: el ritmo maximo queda en unos pocos intentos por
        // ventana, y adivinar deja de ser viable.
        if (encontrado.isPresent() && encontrado.get().estaBloqueada()) {
            throw new ReglaDeNegocioException(mensajeDeBloqueo(encontrado.get()));
        }

        // Se verifica la contrasena aunque el usuario no exista, para que el
        // tiempo de respuesta sea parecido en los dos casos: si el sistema
        // contestara instantaneamente ante un usuario inexistente y tardara al
        // comparar el hash, la diferencia de tiempo revelaria cuales existen.
        boolean valida = encontrado
                .map(u -> u.verificarContra(credenciales.contrasena(), codificador::matches))
                .orElseGet(() -> {
                    codificador.matches(credenciales.contrasena(), HASH_DE_DESCARTE);
                    return false;
                });

        if (encontrado.isEmpty() || !valida) {
            // Solo se cuenta el fallo si la cuenta existe: no hay donde anotar
            // los intentos contra un nombre inventado. Es la limitacion de
            // contar por cuenta, y por eso el limite protege la contrasena, no
            // impide que alguien golpee la puerta.
            if (encontrado.isPresent()) {
                boolean quedoBloqueada = intentos.registrarFallo(encontrado.get().getIdUsuario());
                if (quedoBloqueada) {
                    throw new ReglaDeNegocioException(
                            "Demasiados intentos fallidos. La cuenta queda bloqueada "
                            + intentos.getMinutosDeBloqueo() + " minutos.");
                }
            }
            throw new ReglaDeNegocioException("Usuario o contraseña incorrectos.");
        }

        Usuario usuario = encontrado.get();

        if (!usuario.estaActivo()) {
            throw new ReglaDeNegocioException(
                    "Esta cuenta está dada de baja. Hablá con el dueño para reactivarla.");
        }

        // Un ingreso correcto borra la cuenta de intentos: el limite mira
        // intentos CONSECUTIVOS. Si no se limpiara, cinco errores repartidos a
        // lo largo de meses terminarian bloqueando a alguien que siempre entro
        // bien.
        usuario.limpiarIntentosFallidos();
        usuario.registrarIngreso();

        // El ingreso se audita con el usuario explicito: en este momento la
        // peticion todavia no esta autenticada —justamente se esta
        // autenticando—, asi que no se puede tomar de la sesion.
        auditoria.registrar(usuario, "Ingreso al sistema", "Accesos");

        return armarSesion(usuario);
    }

    /**
     * Los datos de la sesion en curso, para cuando el frontend se recarga.
     *
     * El token sigue siendo el que ya tiene el navegador: aca no se emite uno
     * nuevo. Si cada recarga emitiera un token, la sesion no venceria nunca
     * mientras alguien dejara la pestaña abierta.
     */
    public Sesion sesionDe(Usuario usuario, String tokenVigente) {
        return new Sesion(
                tokenVigente,
                servicioJwt.getDuracionEnSegundos(),
                usuario.getIdUsuario(),
                usuario.getNombreUsuario(),
                usuario.getRol().getNombreRol(),
                usuario.getOperario() != null ? usuario.getOperario().getIdOperario() : null,
                usuario.getRol().getPermisos().stream()
                        .map(Permiso::getNombrePermiso)
                        .sorted()
                        .toList(),
                usuario.debeCambiarContrasena());
    }

    private Sesion armarSesion(Usuario usuario) {
        // emitir() y no renovar(): esto es un ingreso, asi que la sesion
        // empieza ahora y el tope absoluto se cuenta desde este momento.
        String token = servicioJwt.emitir(
                usuario.getIdUsuario(),
                usuario.getNombreUsuario(),
                usuario.getRol().getNombreRol(),
                usuario.getVersionSesion());
        return sesionDe(usuario, token);
    }

    /**
     * El aviso de cuenta bloqueada, con los minutos que faltan.
     *
     * ------------------------------------------------------------------
     *  Lo que este mensaje revela, y por que se acepta
     * ------------------------------------------------------------------
     *
     * Decir "esta cuenta esta bloqueada" admite que la cuenta existe, y el
     * resto de este archivo se cuida justamente de no revelar eso. La
     * contradiccion es real y la decision es deliberada:
     *
     *   - Lo que protege el sistema es la contrasena, no la lista de nombres.
     *     En Granica las cuentas son tres y se llaman por el nombre de pila de
     *     gente que cualquiera que conozca la empresa ya conoce. Ocultar que
     *     "ricardo" existe no protege nada real.
     *   - Un mensaje generico deja al usuario legitimo sin entender por que no
     *     entra si esta escribiendo bien la contrasena. Termina llamando por
     *     telefono, o peor, convencido de que el sistema se rompio.
     *
     * Es el mismo criterio que ya se aplica con las cuentas dadas de baja unas
     * lineas mas arriba.
     */
    private String mensajeDeBloqueo(Usuario usuario) {
        long minutos = Math.max(1, java.time.Duration
                .between(java.time.LocalDateTime.now(), usuario.getBloqueadoHasta())
                .toMinutes() + 1);

        return "Demasiados intentos fallidos. Probá de nuevo en "
                + minutos + (minutos == 1 ? " minuto." : " minutos.");
    }

    /**
     * Hash valido pero de una contrasena que nadie usa.
     *
     * Sirve solo para que la comparacion tarde lo mismo cuando el usuario no
     * existe. No corresponde a ninguna cuenta.
     */
    private static final String HASH_DE_DESCARTE =
            "$2a$10$lotlfbgwDv9go7H4KEh1A.qrSnn6MO9AKUUjTM6jR47FPlMsJM2RC";
}
