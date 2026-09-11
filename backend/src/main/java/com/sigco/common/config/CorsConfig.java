package com.sigco.common.config;

import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Configuracion de CORS (Cross-Origin Resource Sharing).
 *
 * El frontend y el backend de SIGCO son dos aplicaciones separadas que corren
 * en direcciones distintas: en desarrollo, el frontend en localhost:5173 y la
 * API en localhost:8080. Por seguridad, los navegadores bloquean por defecto
 * que una pagina llame a un servidor de otro origen.
 *
 * Esta clase le indica al backend que origenes tiene permitido atender. Sin
 * ella, la primera llamada del frontend fallaria con un error de CORS aunque
 * el codigo de ambos lados sea correcto.
 *
 * Los origenes NO estan escritos aca: se leen de application-dev.properties o
 * application-prod.properties segun el perfil activo. Asi el mismo codigo sirve
 * en desarrollo y en produccion, donde el unico origen habilitado es el dominio
 * del frontend publicado en Vercel.
 *
 * ------------------------------------------------------------------
 *  Por que un CorsConfigurationSource y no un WebMvcConfigurer
 * ------------------------------------------------------------------
 *
 * Hasta el modulo 14 esta clase implementaba WebMvcConfigurer, que alcanza
 * cuando CORS lo resuelve Spring MVC. Con Spring Security activo eso ya no
 * sirve: los filtros de seguridad corren ANTES que Spring MVC, asi que la
 * peticion de verificacion previa (OPTIONS) —que el navegador manda sin token—
 * quedaria rechazada con un 401 antes de que MVC llegue a contestarla.
 *
 * Publicandolo como bean CorsConfigurationSource, la configuracion la toma la
 * cadena de filtros de seguridad y sigue habiendo un solo lugar donde se
 * declaran los origenes permitidos.
 */
@Configuration
public class CorsConfig {

    private final List<String> origenesPermitidos;

    public CorsConfig(@Value("${sigco.cors.origenes-permitidos}") String[] origenesPermitidos) {
        this.origenesPermitidos = Arrays.asList(origenesPermitidos);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuracion = new CorsConfiguration();
        configuracion.setAllowedOrigins(origenesPermitidos);
        configuracion.setAllowedMethods(
                List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuracion.setAllowedHeaders(List.of("*"));
        // Necesario para que el navegador envie la cabecera Authorization con
        // el token JWT.
        configuracion.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource fuente = new UrlBasedCorsConfigurationSource();
        fuente.registerCorsConfiguration("/api/**", configuracion);
        return fuente;
    }
}
