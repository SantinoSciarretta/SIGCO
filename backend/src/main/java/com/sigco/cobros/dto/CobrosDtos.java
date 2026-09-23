package com.sigco.cobros.dto;

import com.sigco.cobros.Cuota;
import com.sigco.cobros.RegistroCac;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Datos que entran y salen del modulo Cobros. */
public final class CobrosDtos {

    private CobrosDtos() {
    }

    // ------------------------------------------------------------------
    //  Entrada
    // ------------------------------------------------------------------

    /**
     * Generacion del plan de cobro.
     *
     * Solo lleva la fecha del primer vencimiento: el resto (anticipo, cantidad
     * de cuotas, total) sale del presupuesto definitivo aprobado. Pedirlos de
     * nuevo abriria la puerta a que el plan no coincida con lo que el cliente
     * aceptó.
     */
    public record GenerarPlan(

            @NotNull(message = "La fecha del primer vencimiento es obligatoria")
            LocalDate primerVencimiento) {
    }

    public record RegistrarPago(

            /**
             * Cuánto entró. Puede ser la cuota entera o una parte.
             *
             * Antes no existía: el pago era siempre por el total de la cuota. El
             * servicio verifica que no supere el saldo, porque eso depende de
             * los otros pagos de la misma cuota.
             */
            @NotNull(message = "El monto del pago es obligatorio")
            @DecimalMin(value = "0.01", message = "El monto tiene que ser mayor a cero")
            BigDecimal monto,

            @NotNull(message = "La fecha de pago es obligatoria")
            LocalDate fechaPago,

            @NotBlank(message = "El medio de pago es obligatorio")
            @Pattern(regexp = "Transferencia|Efectivo|Cheque",
                     message = "Medio de pago no válido")
            String medioPago,

            @Pattern(regexp = "Mensaje|Recibo|Planilla",
                     message = "Comprobante no válido")
            String comprobanteEmitido) {
    }

    public record AnularPago(

            @NotBlank(message = "El motivo de anulación es obligatorio")
            @Size(max = 200, message = "El motivo no puede superar los 200 caracteres")
            String motivo) {
    }

    public record NuevoIndiceCac(

            @NotNull(message = "El mes es obligatorio")
            LocalDate mesCorrespondiente,

            @NotNull(message = "El valor del índice es obligatorio")
            @DecimalMin(value = "0.0001", message = "El índice tiene que ser mayor a cero")
            BigDecimal valorIndice) {
    }

    // ------------------------------------------------------------------
    //  Salida
    // ------------------------------------------------------------------

    public record CuotaRespuesta(
            Long idCuota,
            Integer numeroCuota,
            boolean esAnticipo,
            BigDecimal montoCuota,
            /** Lo que entró hasta ahora por esta cuota. */
            BigDecimal totalPagado,
            /** Lo que falta. Con pagos parciales ya no se deduce del estado. */
            BigDecimal saldo,
            LocalDate fechaVencimiento,
            String estado,
            LocalDate fechaPago,
            String medioPago,
            String comprobanteEmitido,
            String motivoAnulacion,
            /** El detalle, para que el cliente pueda verificar cada entrega. */
            List<PagoRespuesta> pagos) {

        public static CuotaRespuesta desde(Cuota c) {
            return new CuotaRespuesta(
                    c.getIdCuota(), c.getNumeroCuota(), c.esAnticipo(), c.getMontoCuota(),
                    c.totalPagado(), c.saldo(),
                    c.getFechaVencimiento(), c.getEstado(), c.getFechaPago(),
                    c.getMedioPago(), c.getComprobanteEmitido(), c.getMotivoAnulacion(),
                    c.getPagos().stream().map(PagoRespuesta::desde).toList());
        }
    }

    /** Un pago recibido a cuenta de una cuota. */
    public record PagoRespuesta(
            Long idPago,
            BigDecimal monto,
            LocalDate fechaPago,
            String medioPago,
            String comprobanteEmitido) {

        public static PagoRespuesta desde(com.sigco.cobros.Pago p) {
            return new PagoRespuesta(p.getIdPago(), p.getMonto(), p.getFechaPago(),
                    p.getMedioPago(), p.getComprobanteEmitido());
        }
    }

    /** Plan de cobro completo de una obra. */
    public record PlanDeCobro(
            Long idObra,
            String direccionObra,
            String nombreCliente,
            BigDecimal totalPlan,
            BigDecimal totalCobrado,
            BigDecimal saldoPendiente,
            Integer cuotasAbonadas,
            Integer cuotasTotales,
            Integer cuotasVencidas,
            LocalDate proximoVencimiento,
            List<CuotaRespuesta> cuotas) {
    }

    /** Una fila de la vista consolidada: cuanto resta cobrar de cada obra. */
    public record ResumenCobro(
            Long idObra,
            String direccionObra,
            String nombreCliente,
            String estadoObra,
            BigDecimal totalPlan,
            BigDecimal totalCobrado,
            BigDecimal saldoPendiente,
            Integer cuotasVencidas,
            LocalDate proximoVencimiento) {
    }

    /** Vista previa de cómo quedarían las cuotas antes de aplicar el CAC. */
    public record PreviaCac(
            LocalDate mesAnterior,
            BigDecimal indiceAnterior,
            LocalDate mesActual,
            BigDecimal indiceActual,
            BigDecimal coeficiente,
            BigDecimal saldoActual,
            BigDecimal saldoActualizado,
            Integer cuotasAfectadas) {
    }

    public record IndiceCacRespuesta(
            Long idCac,
            LocalDate mesCorrespondiente,
            BigDecimal valorIndice) {

        public static IndiceCacRespuesta desde(RegistroCac r) {
            return new IndiceCacRespuesta(
                    r.getIdCac(), r.getMesCorrespondiente(), r.getValorIndice());
        }
    }
}
