package com.sigco.common.exception;

/**
 * Se lanza cuando se pide un registro que no existe en la base de datos:
 * por ejemplo, GET /api/clientes/999 con un cliente que nunca existio.
 *
 * El manejador global la traduce a una respuesta HTTP 404 (Not Found).
 *
 * Extiende RuntimeException y no Exception para que los servicios no tengan
 * que declararla con "throws" en cada metodo: es un error de datos del que la
 * capa de negocio no puede recuperarse, y quien la resuelve es siempre el
 * manejador global.
 */
public class RecursoNoEncontradoException extends RuntimeException {

    public RecursoNoEncontradoException(String mensaje) {
        super(mensaje);
    }

    /**
     * Atajo para el caso mas frecuente, que arma un mensaje uniforme.
     * Ejemplo: new RecursoNoEncontradoException("Cliente", 12)
     *          -> "No se encontro Cliente con id 12"
     */
    public RecursoNoEncontradoException(String entidad, Object id) {
        super("No se encontro " + entidad + " con id " + id);
    }
}
