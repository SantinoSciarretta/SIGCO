package com.sigco.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

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
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private final String[] origenesPermitidos;

    public CorsConfig(@Value("${sigco.cors.origenes-permitidos}") String[] origenesPermitidos) {
        this.origenesPermitidos = origenesPermitidos;
    }

    @Override
    public void addCorsMappings(CorsRegistry registro) {
        registro.addMapping("/api/**")
                .allowedOrigins(origenesPermitidos)
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                // Necesario para que el navegador envie la cabecera Authorization
                // con el token JWT cuando se integre el modulo 14 (Accesos).
                .allowCredentials(true);
    }
}
