package com.sigco.seguimiento.dto;

import com.sigco.seguimiento.Hito;
import com.sigco.seguimiento.PlantillaHito;
import com.sigco.seguimiento.PlantillaHitoDetalle;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Datos que entran y salen del modulo Seguimiento de Obras. */
public final class SeguimientoDtos {

    private SeguimientoDtos() {
    }

    // ------------------------------------------------------------------
    //  Entrada
    // ------------------------------------------------------------------

    /**
     * Configuracion completa de los hitos de una obra.
     *
     * Se define el conjunto entero de una vez y no hito por hito, porque la
     * regla de las ponderaciones aplica al conjunto: la suma tiene que dar 100.
     * Agregarlos de a uno dejaria la obra en un estado invalido entre altas.
     */
    public record ConfiguracionHitos(

            @NotEmpty(message = "Hay que definir al menos un hito")
            @Valid
            List<HitoSolicitud> hitos) {
    }

    public record HitoSolicitud(

            @NotBlank(message = "El nombre del hito es obligatorio")
            @Size(max = 150, message = "El nombre no puede superar los 150 caracteres")
            String nombreHito,

            @NotNull(message = "La ponderación es obligatoria")
            @DecimalMin(value = "0.01", message = "La ponderación tiene que ser mayor a cero")
            BigDecimal ponderacion,

            @NotNull(message = "El orden es obligatorio")
            @Min(value = 1, message = "El orden arranca en 1")
            Integer orden) {
    }

    /**
     * Cumplimiento de un hito.
     *
     * `forzar` habilita completar un hito salteando anteriores pendientes. El
     * informe lo prevee: "no se puede completar un hito posterior si hay hitos
     * anteriores sin completar, SALVO que el dueño lo habilite de forma
     * explicita, ya que en la practica algunas tareas pueden adelantarse".
     */
    public record Cumplimiento(

            @NotNull(message = "La fecha de cumplimiento es obligatoria")
            LocalDate fechaCumplimiento,

            @Size(max = 250, message = "La observación no puede superar los 250 caracteres")
            String observacion,

            /**
             * Boolean y no boolean primitivo: si el cliente omite el campo,
             * Jackson no puede mapear null a un primitivo y devuelve 400. Un
             * flag opcional que rompe la llamada cuando no se manda no es
             * opcional. Se interpreta como false, que es el caso normal.
             */
            Boolean forzar) {

        public boolean forzarOFalso() {
            return Boolean.TRUE.equals(forzar);
        }
    }

    public record ObservacionHito(

            @NotBlank(message = "La observación es obligatoria")
            @Size(max = 250, message = "La observación no puede superar los 250 caracteres")
            String observacion) {
    }

    public record NuevaPlantilla(

            @NotBlank(message = "El nombre de la plantilla es obligatorio")
            @Size(max = 100, message = "El nombre no puede superar los 100 caracteres")
            String nombrePlantilla,

            @NotEmpty(message = "La plantilla necesita al menos una etapa")
            @Valid
            List<HitoSolicitud> etapas) {
    }

    // ------------------------------------------------------------------
    //  Salida
    // ------------------------------------------------------------------

    public record HitoRespuesta(
            Long idHito,
            String nombreHito,
            BigDecimal ponderacion,
            Integer orden,
            String estado,
            LocalDate fechaCumplimiento,
            String observacion) {

        public static HitoRespuesta desde(Hito h) {
            return new HitoRespuesta(h.getIdHito(), h.getNombreHito(), h.getPonderacion(),
                    h.getOrden(), h.getEstado(), h.getFechaCumplimiento(), h.getObservacion());
        }
    }

    /**
     * Panel de avance de la obra.
     *
     * Cruza el avance FISICO (hitos completados) con el FINANCIERO (porcentaje
     * del presupuesto ya gastado). Esa comparacion es la que detecta obras que
     * consumieron plata sin avanzar, y es informacion que hoy no existe en
     * ninguna parte.
     */
    public record AvanceObra(
            Long idObra,
            String direccionObra,
            String estadoObra,
            BigDecimal avanceFisico,
            BigDecimal avanceFinanciero,
            /** avanceFinanciero - avanceFisico. Positivo = se gasta mas rapido de lo que se avanza. */
            BigDecimal desfasaje,
            boolean alertaDesfasaje,
            LocalDate fechaFinEstimada,
            /** Dias hasta la fecha estimada. Negativo = ya se paso. */
            Long diasParaElPlazo,
            boolean atrasada,
            Integer hitosCompletados,
            Integer hitosTotales,
            List<HitoRespuesta> hitos) {
    }

    public record PlantillaRespuesta(
            Long idPlantilla,
            String nombrePlantilla,
            LocalDateTime fechaCreacion,
            BigDecimal sumaPonderaciones,
            List<EtapaRespuesta> etapas) {

        public static PlantillaRespuesta desde(PlantillaHito p) {
            return new PlantillaRespuesta(
                    p.getIdPlantilla(), p.getNombrePlantilla(), p.getFechaCreacion(),
                    p.sumaPonderaciones(),
                    p.getDetalles().stream()
                            .sorted((a, b) -> a.getOrden().compareTo(b.getOrden()))
                            .map(EtapaRespuesta::desde).toList());
        }
    }

    public record EtapaRespuesta(
            Long idDetalle,
            String nombreHito,
            BigDecimal ponderacion,
            Integer orden) {

        static EtapaRespuesta desde(PlantillaHitoDetalle d) {
            return new EtapaRespuesta(d.getIdDetalle(), d.getNombreHito(),
                    d.getPonderacion(), d.getOrden());
        }
    }
}
