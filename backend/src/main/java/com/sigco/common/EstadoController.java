package com.sigco.common;

import java.time.LocalDateTime;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint de diagnostico del sistema.
 *
 * No pertenece a ningun modulo funcional: existe para verificar de un vistazo
 * que la API responde y que la conexion con la base de datos esta viva. Se usa
 * durante el desarrollo (para comprobar que el frontend alcanza al backend sin
 * problemas de CORS) y despues del despliegue, para confirmar que el servicio
 * levanto correctamente en Railway.
 *
 * Es el primer endpoint del sistema, asi que sirve tambien como referencia del
 * patron que van a seguir todos los demas:
 *   - @RestController        la clase devuelve datos (JSON), no vistas HTML
 *   - @RequestMapping        prefijo comun de la ruta
 *   - inyeccion por constructor de lo que necesita
 */
@RestController
@RequestMapping("/api/estado")
public class EstadoController {

    private final JdbcTemplate jdbcTemplate;

    /**
     * Inyeccion por constructor: Spring entrega el JdbcTemplate al crear la
     * clase. Se prefiere al campo anotado con @Autowired porque deja explicito
     * de que depende esta clase y permite construirla a mano en los tests.
     */
    public EstadoController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Respuesta del diagnostico.
     */
    public record Estado(
            String aplicacion,
            String api,
            String baseDatos,
            LocalDateTime momento) {
    }

    @GetMapping
    public Estado consultarEstado() {
        String baseDatos;
        try {
            // Consulta minima cuyo unico objetivo es comprobar que la conexion
            // con PostgreSQL funciona. Si la base no responde, lanza excepcion.
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            baseDatos = "conectada";
        } catch (Exception ex) {
            baseDatos = "sin conexion: " + ex.getMessage();
        }

        return new Estado("SIGCO", "operativa", baseDatos, LocalDateTime.now());
    }
}
