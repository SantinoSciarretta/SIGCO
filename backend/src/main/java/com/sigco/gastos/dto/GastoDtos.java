package com.sigco.gastos.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Datos que entran al modulo Gastos. */
public final class GastoDtos {

    private GastoDtos() {
    }

    /**
     * Alta y edicion de un gasto.
     *
     * La obra solo esta en el alta y no en la edicion: mover un gasto de obra
     * alteraria el resultado economico de las dos. Por eso el DTO de edicion
     * reusa este record pero el servicio ignora idObra al actualizar.
     */
    public record GastoSolicitud(

            @NotNull(message = "La obra es obligatoria")
            Long idObra,

            @NotNull(message = "El rubro es obligatorio")
            Long idRubro,

            /** Opcional. Ver la nota sobre la contradiccion del informe en Gasto. */
            Long idSubrubro,

            @NotBlank(message = "El tipo de gasto es obligatorio")
            @Pattern(regexp = "Material|Mano de Obra|Gasto Hormiga|Otro",
                     message = "Tipo de gasto no válido")
            String tipoGasto,

            @NotNull(message = "El monto es obligatorio")
            @DecimalMin(value = "0.01", message = "El monto tiene que ser mayor a cero")
            BigDecimal monto,

            @NotNull(message = "La fecha del gasto es obligatoria")
            LocalDate fechaGasto,

            @Size(max = 250, message = "La descripción no puede superar los 250 caracteres")
            String descripcion,

            @Size(max = 255, message = "La referencia del comprobante no puede superar los 255 caracteres")
            String comprobanteAdjunto,

            /** Cuando el gasto es un pago de mano de obra. */
            Long idOperario) {
    }

    /** Anulacion: el informe exige dejar el motivo. */
    public record AnulacionGasto(

            @NotBlank(message = "El motivo de anulación es obligatorio")
            @Size(max = 200, message = "El motivo no puede superar los 200 caracteres")
            String motivo) {
    }
}
