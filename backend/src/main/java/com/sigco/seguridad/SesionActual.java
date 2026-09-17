package com.sigco.seguridad;

import com.sigco.usuarios.Usuario;
import com.sigco.usuarios.UsuarioRepository;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Quien esta usando el sistema en esta peticion.
 *
 * Existe para que ningun modulo tenga que hablar con SecurityContextHolder, que
 * es una clase de Spring Security con estado global. Si cada servicio la
 * consultara por su cuenta, los trece modulos anteriores quedarian atados a
 * Spring Security y sus tests tendrian que montarlo para correr.
 *
 * ------------------------------------------------------------------
 *  Lo que esto viene a completar
 * ------------------------------------------------------------------
 *
 * Varias tablas guardan quien hizo cada cosa: gasto.id_usuario_registro,
 * pedido.id_usuario_solicita, pedido.id_usuario_recibe, hito.id_usuario_completa
 * e inasistencia.id_usuario_registro. Durante el desarrollo de esos modulos
 * quedaron en null, con un "TODO: vincular a usuario real al integrar modulo
 * Accesos" en cada punto. Este componente es lo que permite completarlos.
 */
@Component
public class SesionActual {

    private final UsuarioRepository usuarioRepositorio;

    public SesionActual(UsuarioRepository usuarioRepositorio) {
        this.usuarioRepositorio = usuarioRepositorio;
    }

    /** El usuario identificado, si la peticion venia autenticada. */
    public Optional<UsuarioAutenticado> autenticado() {
        Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();

        if (autenticacion == null
                || !(autenticacion.getPrincipal() instanceof UsuarioAutenticado usuario)) {
            return Optional.empty();
        }
        return Optional.of(usuario);
    }

    public Optional<Long> idUsuario() {
        return autenticado().map(UsuarioAutenticado::getIdUsuario);
    }

    /**
     * La entidad Usuario, para las tablas que la referencian por clave foranea.
     *
     * Devuelve un Optional en lugar de fallar cuando no hay sesion: los
     * servicios que la usan escriben null en esa columna, que es nullable
     * justamente porque asi se desarrollaron los modulos anteriores. Que una
     * accion sin sesion no pueda registrarse es problema de la seguridad, no del
     * modulo de negocio.
     */
    public Optional<Usuario> usuario() {
        return idUsuario().flatMap(usuarioRepositorio::findById);
    }

    /** El operario vinculado a la cuenta, si la cuenta es de un capataz. */
    public Optional<Long> idOperario() {
        return autenticado().map(UsuarioAutenticado::getIdOperario)
                .filter(java.util.Objects::nonNull);
    }

    /**
     * Si el usuario de esta peticion tiene un permiso.
     *
     * Sirve para los casos en que un modulo no se limita a dejar pasar o
     * rechazar, sino que arma una respuesta DISTINTA segun quien pregunta. Hoy
     * el caso es el Tablero: el informe le da al Capataz General una version
     * reducida, sin la informacion financiera.
     *
     * No reemplaza a @PreAuthorize, que es lo que autoriza el endpoint. Esto
     * decide que contiene la respuesta una vez que el acceso ya se concedio.
     */
    public boolean puede(String permiso) {
        return autenticado()
                .map(u -> u.getAuthorities().stream()
                        .anyMatch(a -> permiso.equals(a.getAuthority())))
                .orElse(false);
    }

    public boolean esDueno() {
        return autenticado()
                .map(u -> com.sigco.accesos.Rol.DUENO.equals(u.getNombreRol()))
                .orElse(false);
    }
}
