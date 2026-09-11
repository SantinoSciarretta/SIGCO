package com.sigco.common.exception;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Manejador central de errores de toda la API.
 *
 * La anotacion @RestControllerAdvice hace que Spring dirija hacia aca cualquier
 * excepcion que escape de cualquier controlador del sistema. Gracias a eso los
 * controladores quedan limpios: se ocupan del camino feliz y lanzan la
 * excepcion que corresponda, sin repetir bloques try/catch en cada metodo.
 *
 * La clase extiende ResponseEntityExceptionHandler, que es la clase base que
 * provee Spring y que ya sabe responder correctamente a sus propios errores:
 * una URL que no existe (404), un metodo HTTP no permitido (405), un JSON mal
 * formado (400), un parametro obligatorio ausente (400). Sin heredar de ella,
 * el metodo de ultima instancia @ExceptionHandler(Exception.class) atraparia
 * tambien esos casos y los devolveria a todos como 500.
 *
 * Lo unico que se redefine de la clase base es el formato del cuerpo de la
 * respuesta (metodo handleExceptionInternal), para que TODOS los errores del
 * sistema, propios y de Spring, salgan con la misma estructura RespuestaError.
 * Asi el frontend los interpreta en un solo lugar.
 *
 * Es codigo que se escribe una sola vez y sirve para los 14 modulos.
 */
@RestControllerAdvice
public class ManejadorGlobalDeErrores extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ManejadorGlobalDeErrores.class);

    // ------------------------------------------------------------------
    //  Excepciones propias de SIGCO
    // ------------------------------------------------------------------

    /**
     * 404 - Se pidio un registro que no existe.
     */
    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ResponseEntity<RespuestaError> manejarRecursoNoEncontrado(
            RecursoNoEncontradoException ex, WebRequest peticion) {

        RespuestaError cuerpo = RespuestaError.de(
                HttpStatus.NOT_FOUND.value(),
                HttpStatus.NOT_FOUND.getReasonPhrase(),
                ex.getMessage(),
                rutaDe(peticion));

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cuerpo);
    }

    /**
     * 409 - Los datos son validos pero la operacion viola una regla de negocio.
     */
    @ExceptionHandler(ReglaDeNegocioException.class)
    public ResponseEntity<RespuestaError> manejarReglaDeNegocio(
            ReglaDeNegocioException ex, WebRequest peticion) {

        RespuestaError cuerpo = RespuestaError.de(
                HttpStatus.CONFLICT.value(),
                HttpStatus.CONFLICT.getReasonPhrase(),
                ex.getMessage(),
                rutaDe(peticion));

        return ResponseEntity.status(HttpStatus.CONFLICT).body(cuerpo);
    }

    /**
     * 403 - El usuario esta identificado pero su rol no alcanza.
     *
     * Lo lanza @PreAuthorize cuando falta un permiso. Hace falta atraparlo ACA y
     * no alcanza con el accessDeniedHandler de la configuracion de seguridad:
     * ese handler solo ve lo que se rechaza en la cadena de filtros, y una
     * anotacion @PreAuthorize se evalua mas adentro, ya dentro de Spring MVC.
     * Sin este metodo, la excepcion cae en la red de contencion de abajo y el
     * frontend recibe un 500 ("error del sistema") en lugar de un 403 ("no
     * tenes permiso"), que son dos cosas muy distintas para quien lo lee.
     *
     * NO se registra en el log como error: que alguien pida algo que no le
     * corresponde es una situacion prevista, no una falla.
     */
    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<RespuestaError> manejarAccesoDenegado(
            org.springframework.security.access.AccessDeniedException ex, WebRequest peticion) {

        RespuestaError cuerpo = RespuestaError.de(
                HttpStatus.FORBIDDEN.value(),
                HttpStatus.FORBIDDEN.getReasonPhrase(),
                "Tu rol no tiene permiso para esta acción.",
                rutaDe(peticion));

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(cuerpo);
    }

    /**
     * 500 - Red de contencion para cualquier error no previsto.
     *
     * El detalle real se registra en el log del servidor, pero al cliente se le
     * devuelve un mensaje generico: la traza de una excepcion puede revelar
     * nombres de tablas y la estructura interna del sistema.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<RespuestaError> manejarErrorInesperado(
            Exception ex, WebRequest peticion) {

        log.error("Error inesperado en {}", rutaDe(peticion), ex);

        RespuestaError cuerpo = RespuestaError.de(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(),
                "Ocurrio un error inesperado. Consulte con el administrador del sistema.",
                rutaDe(peticion));

        return ResponseEntity.internalServerError().body(cuerpo);
    }

    // ------------------------------------------------------------------
    //  Redefiniciones de la clase base de Spring
    // ------------------------------------------------------------------

    /**
     * 400 - Fallo la validacion de formato de los datos recibidos.
     *
     * Se dispara cuando un DTO anotado con @Valid no cumple sus restricciones
     * (@NotBlank, @Size, @Email...). Se devuelven TODOS los campos rechazados
     * juntos, no solo el primero, para que el formulario del frontend pueda
     * marcarlos de una sola vez en lugar de ir corrigiendo de a uno.
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders cabeceras,
            HttpStatusCode estado, WebRequest peticion) {

        // LinkedHashMap conserva el orden en que se declararon los campos,
        // asi el frontend los muestra en el mismo orden que el formulario.
        Map<String, String> campos = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            campos.put(error.getField(), error.getDefaultMessage());
        }

        RespuestaError cuerpo = new RespuestaError(
                LocalDateTime.now(),
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                "Hay campos con datos invalidos",
                rutaDe(peticion),
                campos);

        return new ResponseEntity<>(cuerpo, cabeceras, HttpStatus.BAD_REQUEST);
    }

    /**
     * Punto por el que pasan todas las respuestas de error que arma la clase
     * base de Spring. Se redefine unicamente para reemplazar el cuerpo estandar
     * de Spring por el formato RespuestaError del sistema, conservando el codigo
     * HTTP que Spring ya determino correctamente.
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object cuerpoOriginal, HttpHeaders cabeceras,
            HttpStatusCode estado, WebRequest peticion) {

        HttpStatus estadoHttp = HttpStatus.resolve(estado.value());
        String nombreDelEstado = (estadoHttp != null) ? estadoHttp.getReasonPhrase() : "Error";

        RespuestaError cuerpo = RespuestaError.de(
                estado.value(),
                nombreDelEstado,
                ex.getMessage(),
                rutaDe(peticion));

        return new ResponseEntity<>(cuerpo, cabeceras, estado);
    }

    // ------------------------------------------------------------------

    /**
     * Extrae la ruta que se estaba llamando, para incluirla en la respuesta.
     */
    private String rutaDe(WebRequest peticion) {
        if (peticion instanceof ServletWebRequest servletWebRequest) {
            return servletWebRequest.getRequest().getRequestURI();
        }
        return peticion.getDescription(false);
    }
}
