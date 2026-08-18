package com.sigco.common.exception;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Formato unico de respuesta ante cualquier error de la API.
 *
 * Que todos los errores del sistema tengan la misma forma permite que el
 * frontend los maneje en un solo lugar (el interceptor de Axios) en lugar de
 * interpretar un formato distinto por modulo.
 *
 * Es un "record" de Java 17: una clase inmutable en la que el compilador genera
 * el constructor y los metodos de acceso a partir de esta unica linea.
 *
 * @param momento         fecha y hora en que ocurrio el error
 * @param estado          codigo HTTP (404, 400, 409, 500)
 * @param error           nombre del codigo HTTP ("Not Found", "Bad Request")
 * @param mensaje         descripcion legible de que salio mal
 * @param ruta            endpoint que se estaba llamando
 * @param camposInvalidos campo -> motivo del rechazo. Solo se completa en los
 *                        errores de validacion; en el resto viaja como null
 */
public record RespuestaError(
        LocalDateTime momento,
        int estado,
        String error,
        String mensaje,
        String ruta,
        Map<String, String> camposInvalidos) {

    /** Error sin detalle por campo (404, 409, 500). */
    public static RespuestaError de(int estado, String error, String mensaje, String ruta) {
        return new RespuestaError(LocalDateTime.now(), estado, error, mensaje, ruta, null);
    }
}
