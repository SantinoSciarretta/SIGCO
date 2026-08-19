package com.sigco.presupuestacion.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Objetos de entrada del modulo Presupuestacion.
 *
 * Van agrupados en un solo archivo porque son cinco records cortos del mismo
 * modulo: repartirlos en cinco archivos de quince lineas haria mas dificil
 * verlos en conjunto, que es como se entienden.
 */
public final class PresupuestoDtos {

    private PresupuestoDtos() {
    }

    /**
     * Alta de un presupuesto.
     *
     * Los campos de la cotizacion inicial (metros y valor por m2) solo se usan
     * cuando el tipo es "Cotizacion inicial"; el servicio los exige en ese caso
     * y los ignora en el resto.
     */
    public record NuevoPresupuesto(

            @NotNull(message = "El presupuesto tiene que pertenecer a una obra")
            Long idObra,

            @NotBlank(message = "El tipo de presupuesto es obligatorio")
            @Pattern(regexp = "Cotización inicial|Anteproyecto|Definitivo|Adicional",
                     message = "Tipo no válido")
            String tipoPresupuesto,

            /** Presupuesto que se toma como punto de partida. Opcional. */
            Long idPresupuestoBase,

            @Positive(message = "Los metros cuadrados tienen que ser mayores a cero")
            BigDecimal metrosCuadrados,

            @Positive(message = "El valor por metro cuadrado tiene que ser mayor a cero")
            BigDecimal valorPorM2,

            @Size(max = 100, message = "El plazo no puede superar los 100 caracteres")
            String plazoEstimadoObra) {
    }

    /** Plan de pago: anticipo, cuotas y plazo. */
    public record PlanDePago(

            @NotNull(message = "El porcentaje de anticipo es obligatorio")
            @DecimalMin(value = "0", message = "El anticipo no puede ser negativo")
            @DecimalMax(value = "100", message = "El anticipo no puede superar el 100%")
            BigDecimal anticipoPorcentaje,

            @NotNull(message = "La cantidad de cuotas es obligatoria")
            @Min(value = 0, message = "La cantidad de cuotas no puede ser negativa")
            Integer cantidadCuotas,

            @Size(max = 100, message = "El plazo no puede superar los 100 caracteres")
            String plazoEstimadoObra) {
    }

    /** Alta o edicion de un item del presupuesto. */
    public record ItemSolicitud(

            @NotNull(message = "El rubro del ítem es obligatorio")
            Long idRubro,

            /** Opcional: el anteproyecto se carga solo a nivel de rubro. */
            Long idSubrubro,

            @NotBlank(message = "La descripción del ítem es obligatoria")
            @Size(max = 250, message = "La descripción no puede superar los 250 caracteres")
            String descripcion,

            @NotBlank(message = "La unidad de medida es obligatoria")
            @Size(max = 20, message = "La unidad no puede superar los 20 caracteres")
            String unidadMedida,

            @NotNull(message = "La cantidad es obligatoria")
            @Positive(message = "La cantidad tiene que ser mayor a cero")
            BigDecimal cantidad,

            @NotNull(message = "El valor unitario es obligatorio")
            @PositiveOrZero(message = "El valor unitario no puede ser negativo")
            BigDecimal valorUnitario) {
    }

    /** Cambio de estado del presupuesto. */
    public record CambioEstadoPresupuesto(

            @NotBlank(message = "El estado es obligatorio")
            @Pattern(regexp = "Enviado|Aprobado|Rechazado",
                     message = "El estado debe ser 'Enviado', 'Aprobado' o 'Rechazado'")
            String estado) {
    }

    /**
     * Duplicacion de un presupuesto como punto de partida de otro.
     *
     * Cubre los dos casos del informe: armar el definitivo a partir del
     * anteproyecto de la misma obra, y reutilizar un presupuesto de otra obra
     * como plantilla.
     */
    public record Duplicacion(

            @NotBlank(message = "El tipo del nuevo presupuesto es obligatorio")
            @Pattern(regexp = "Anteproyecto|Definitivo|Adicional",
                     message = "Tipo no válido para duplicar")
            String tipoPresupuesto,

            /**
             * Obra del nuevo presupuesto. Si no viene, se usa la de origen.
             * Sirve para reutilizar un presupuesto de otra obra como plantilla.
             */
            Long idObraDestino) {
    }
}
