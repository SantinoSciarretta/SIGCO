package com.sigco.seguridad;

import tools.jackson.databind.ObjectMapper;
import com.sigco.common.exception.RespuestaError;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Configuracion de Spring Security: donde se activa la seguridad de todo SIGCO.
 *
 * Este archivo es el modulo 14 en una pantalla. Hasta que existio, los trece
 * modulos anteriores se desarrollaron y probaron sin login —decision explicita
 * del proyecto, para no arrastrar la friccion de autenticarse en cada prueba— y
 * la seguridad se puso encima al final.
 *
 * ------------------------------------------------------------------
 *  Como se decide quien puede hacer que
 * ------------------------------------------------------------------
 *
 * En dos niveles, y cada uno resuelve algo distinto:
 *
 *   1. ACA, por ruta: que se puede pedir sin estar autenticado. Son tres cosas
 *      y ninguna mas: el login, el diagnostico y la vidriera publica.
 *
 *   2. En cada controlador, con @PreAuthorize: que permiso hace falta para
 *      cada operacion. Va ahi y no aca porque la regla se lee al lado del
 *      metodo que protege. Una lista central de rutas con permisos queda lejos
 *      del codigo y se desactualiza sin que nadie lo note.
 *
 * ------------------------------------------------------------------
 *  Sin sesiones
 * ------------------------------------------------------------------
 *
 * STATELESS: el servidor no guarda nada entre peticiones. Cada una llega con su
 * token y se resuelve sola. Por eso tampoco hace falta protegerse de CSRF: ese
 * ataque se apoya en que el navegador mande sola una cookie de sesion, y aca no
 * hay cookie de sesion — el token lo adjunta el frontend a mano.
 */
@Configuration
@EnableMethodSecurity
public class ConfiguracionSeguridad {

    private final FiltroJwt filtroJwt;
    private final FiltroCambioDeContrasena filtroCambioDeContrasena;

    /**
     * El mismo ObjectMapper que usa el resto de la API.
     *
     * Ojo con el paquete: Spring Boot 4 usa Jackson 3, cuyas clases viven en
     * tools.jackson y ya no en com.fasterxml.jackson. La version 2 sigue
     * apareciendo en el arbol de dependencias porque la arrastra JJWT para su
     * propio uso, pero no es la que usa la API.
     *
     * Se inyecta en lugar de crear uno nuevo aca: el de Spring ya viene
     * configurado para escribir fechas como texto ISO. Uno recien creado las
     * escribiria como un arreglo de numeros, y el error de sesion vencida
     * llegaria al frontend con un formato distinto al de todos los demas.
     */
    private final ObjectMapper mapeador;

    public ConfiguracionSeguridad(FiltroJwt filtroJwt,
                                  FiltroCambioDeContrasena filtroCambioDeContrasena,
                                  ObjectMapper mapeador) {
        this.filtroJwt = filtroJwt;
        this.filtroCambioDeContrasena = filtroCambioDeContrasena;
        this.mapeador = mapeador;
    }

    /**
     * BCrypt: hash unidireccional con sal, como pide el informe.
     *
     * "Con sal" significa que cada contrasena se cifra junto a un valor
     * aleatorio distinto, asi dos personas con la misma contrasena tienen
     * hashes diferentes y no se puede deducir una de la otra.
     */
    @Bean
    public PasswordEncoder codificadorDeContrasenas() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain cadenaDeFiltros(HttpSecurity http) throws Exception {
        http
                // Toma la configuracion de origenes de CorsConfig. Va primero
                // para que la peticion de verificacion previa (OPTIONS) que el
                // navegador manda sola no termine rechazada por falta de token.
                .cors(Customizer.withDefaults())

                .csrf(csrf -> csrf.disable())

                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .authorizeHttpRequests(rutas -> rutas
                        // El login, obviamente, no puede exigir estar logueado.
                        .requestMatchers(HttpMethod.POST, "/api/sesion").permitAll()

                        // Diagnostico: tiene que poder consultarse aunque nadie
                        // haya entrado, para saber si el servicio esta vivo.
                        .requestMatchers(HttpMethod.GET, "/api/estado").permitAll()

                        // La vidriera del portfolio es publica por diseño: la ve
                        // cualquier visitante. Por eso desde el primer dia vive
                        // en /api/vidriera y no dentro de /api/portfolio, que es
                        // la administracion y si requiere login.
                        .requestMatchers(HttpMethod.GET, "/api/vidriera", "/api/vidriera/**")
                        .permitAll()

                        // Las imagenes del portfolio son publicas por el mismo
                        // motivo que la vidriera: sin esto, la galeria se veria
                        // con los recuadros vacios para un visitante.
                        // Los remitos y comprobantes viven en /privado y no
                        // entran aca.
                        .requestMatchers(HttpMethod.GET, "/api/archivos/publico/**")
                        .permitAll()

                        // La orden de pedido que se le manda al corralon por
                        // WhatsApp. El que la abre no tiene cuenta en SIGCO ni
                        // va a tenerla: lo que la protege es que el token sea
                        // imposible de adivinar y que venza. El PDF, ademas, no
                        // lleva presupuesto, gasto, ganancia ni cliente.
                        .requestMatchers(HttpMethod.GET, "/api/ordenes-publicas/**")
                        .permitAll()

                        // El navegador manda OPTIONS antes de ciertas peticiones
                        // para preguntar si tiene permiso. Esa consulta no lleva
                        // token y no debe rechazarse.
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        .anyRequest().authenticated())

                .exceptionHandling(e -> e
                        .authenticationEntryPoint(this::responder401)
                        .accessDeniedHandler((peticion, respuesta, ex) ->
                                responder(respuesta, HttpServletResponse.SC_FORBIDDEN, "Forbidden",
                                        "Tu rol no tiene permiso para esta acción.",
                                        peticion.getRequestURI())))

                // El filtro del token va antes del de usuario y contrasena de
                // Spring: para cuando ese corre, el usuario ya esta identificado.
                .addFilterBefore(filtroJwt, UsernamePasswordAuthenticationFilter.class)

                // Y este va DESPUES del token, no antes: necesita saber quien es
                // el usuario para preguntarle si tiene que cambiar la
                // contrasena. Antes del token no habria a quien preguntarle.
                .addFilterAfter(filtroCambioDeContrasena, FiltroJwt.class);

        return http.build();
    }

    // ------------------------------------------------------------------
    //  Errores de seguridad con el mismo formato que el resto
    // ------------------------------------------------------------------

    /**
     * Un 401 o un 403 tambien viajan como RespuestaError.
     *
     * Sin esto, Spring Security contesta con su pagina de error por defecto, en
     * HTML. El interceptor de Axios del frontend espera el formato unico de
     * error del sistema, asi que un 401 con HTML se veria como "ocurrio un
     * error" en lugar de "tu sesion vencio".
     */
    private void responder401(jakarta.servlet.http.HttpServletRequest peticion,
                              HttpServletResponse respuesta,
                              org.springframework.security.core.AuthenticationException ex) {
        responder(respuesta, HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized",
                "Tu sesión no es válida o venció. Ingresá de nuevo.",
                peticion.getRequestURI());
    }

    private void responder(HttpServletResponse respuesta, int estado, String error,
                           String mensaje, String ruta) {
        respuesta.setStatus(estado);
        respuesta.setContentType(MediaType.APPLICATION_JSON_VALUE);
        respuesta.setCharacterEncoding("UTF-8");
        try {
            respuesta.getWriter().write(mapeador.writeValueAsString(
                    RespuestaError.de(estado, error, mensaje, ruta)));
        } catch (java.io.IOException ignorado) {
            // Si no se puede escribir la respuesta, el cliente ya se fue. El
            // codigo de estado alcanza.
        }
    }
}
