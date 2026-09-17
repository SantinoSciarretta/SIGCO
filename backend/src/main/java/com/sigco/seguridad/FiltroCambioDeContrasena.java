package com.sigco.seguridad;

// Jackson 3: Spring Boot 4 movio las clases de com.fasterxml.jackson a
// tools.jackson. La version 2 sigue en el arbol de dependencias porque la
// arrastra JJWT, pero no es la que usa la API.
import tools.jackson.databind.ObjectMapper;
import com.sigco.common.exception.RespuestaError;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Deja a una cuenta obligada a cambiar la contrasena sin poder hacer otra cosa.
 *
 * ------------------------------------------------------------------
 *  Por que hace falta un filtro y no alcanza con el frontend
 * ------------------------------------------------------------------
 *
 * El frontend ya manda al usuario a la pantalla de cambio en cuanto ve el campo
 * debeCambiarContrasena de la sesion. Eso resuelve el caso normal y no resuelve
 * nada del problema de fondo: quien tiene una contrasena que no le pertenece no
 * va a usar la pantalla, va a llamar a la API directamente. Una obligacion que
 * vive en el navegador se saltea sin herramientas especiales.
 *
 * La regla del proyecto es que la validacion autoritativa esta siempre en el
 * servidor. Este filtro es esa validacion.
 *
 * ------------------------------------------------------------------
 *  Que deja pasar
 * ------------------------------------------------------------------
 *
 *   - Las peticiones sin usuario autenticado. No son asunto de este filtro: el
 *     login, la vidriera publica y el diagnostico se resuelven antes.
 *   - GET /api/sesion, para que el frontend pueda preguntar quien es y por que
 *     esta trabado.
 *   - PATCH /api/usuarios/{propio id}/contrasena, que es la unica salida.
 *   - OPTIONS, que es la consulta previa que hace el navegador por CORS y nunca
 *     lleva datos.
 *
 * Todo lo demas recibe 403 con un codigo que el frontend reconoce.
 */
@Component
public class FiltroCambioDeContrasena extends OncePerRequestFilter {

    /**
     * Marca que el frontend busca en la respuesta para distinguir este 403 de
     * un "no tenes permiso" comun. Sin esto tendria que adivinar leyendo el
     * texto del mensaje, que cambia.
     */
    public static final String CODIGO = "CAMBIO_DE_CONTRASENA_PENDIENTE";

    private final ObjectMapper json;

    public FiltroCambioDeContrasena(ObjectMapper json) {
        this.json = json;
    }

    /**
     * El usuario de esta peticion, leido del contexto de Spring Security.
     *
     * Se lee directo y no a traves de SesionActual, igual que hace FiltroJwt.
     * SesionActual existe para que los modulos de NEGOCIO no queden atados a
     * Spring Security; un filtro de seguridad ya es Spring Security, y pasar
     * por el intermediario solo agregaria una dependencia de servicio a un
     * componente de la capa web.
     */
    private UsuarioAutenticado usuarioDeLaPeticion() {
        Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();

        if (autenticacion != null
                && autenticacion.getPrincipal() instanceof UsuarioAutenticado usuario) {
            return usuario;
        }
        return null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest peticion,
                                    HttpServletResponse respuesta,
                                    FilterChain cadena) throws ServletException, IOException {

        UsuarioAutenticado usuario = usuarioDeLaPeticion();
        boolean obligado = usuario != null && usuario.debeCambiarContrasena();

        if (obligado && !esRutaPermitida(peticion, usuario)) {
            rechazar(peticion, respuesta);
            return;
        }

        cadena.doFilter(peticion, respuesta);
    }

    private boolean esRutaPermitida(HttpServletRequest peticion, UsuarioAutenticado usuario) {
        String metodo = peticion.getMethod();
        String ruta = peticion.getRequestURI();

        if ("OPTIONS".equals(metodo)) {
            return true;
        }
        if ("GET".equals(metodo) && "/api/sesion".equals(ruta)) {
            return true;
        }

        // El cambio, y solo sobre la cuenta propia: si se aceptara cualquier
        // id, una cuenta trabada podria resetear la contrasena de otra para
        // salir del paso. Que el id sea el propio se vuelve a verificar en el
        // controlador; aca se comprueba para no dejar pasar la peticion
        // siquiera.
        return "PATCH".equals(metodo)
                && ("/api/usuarios/" + usuario.getIdUsuario() + "/contrasena").equals(ruta);
    }

    /**
     * Responde con el mismo formato que el resto de los errores de la API.
     *
     * El filtro escribe la respuesta a mano porque corre antes de los
     * controladores: ManejadorGlobalDeErrores no llega a intervenir en algo que
     * nunca entra a un controlador.
     */
    private void rechazar(HttpServletRequest peticion, HttpServletResponse respuesta)
            throws IOException {

        respuesta.setStatus(HttpStatus.FORBIDDEN.value());
        respuesta.setContentType(MediaType.APPLICATION_JSON_VALUE);
        respuesta.setCharacterEncoding("UTF-8");

        respuesta.getWriter().write(json.writeValueAsString(RespuestaError.de(
                HttpStatus.FORBIDDEN.value(),
                CODIGO,
                "Tenés que cambiar tu contraseña antes de usar el sistema.",
                peticion.getRequestURI())));
    }
}
