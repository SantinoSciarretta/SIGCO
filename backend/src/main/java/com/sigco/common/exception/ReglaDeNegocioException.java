package com.sigco.common.exception;

/**
 * Se lanza cuando una operacion es sintacticamente valida pero viola una regla
 * de negocio de Granica SRL relevada en el informe. Ejemplos concretos:
 *
 *   - Eliminar un cliente que tiene obras asociadas (solo puede marcarse
 *     como inactivo, para no perder la trazabilidad).
 *   - Generar un presupuesto definitivo de una reforma sin anteproyecto previo.
 *   - Guardar un plan de pago cuyo anticipo mas las cuotas no suma el 100%.
 *
 * Se distingue de un error de validacion de formato (que resuelve Bean
 * Validation con @NotBlank o @Size) porque aca los datos estan bien escritos:
 * lo que falla es la regla del negocio. Por eso el manejador global la traduce
 * a HTTP 409 (Conflict) y no a 400.
 */
public class ReglaDeNegocioException extends RuntimeException {

    public ReglaDeNegocioException(String mensaje) {
        super(mensaje);
    }
}
