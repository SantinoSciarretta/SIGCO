package com.sigco.gastos.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Estado financiero de una obra: presupuestado contra gastado, por rubro.
 *
 * Es la vista que hoy no existe. El dueño conoce el resultado economico de una
 * obra recien cuando termina; esto se lo muestra en cualquier momento.
 */
public record EstadoFinanciero(
        Long idObra,
        String direccionObra,
        String estadoObra,
        Long idPresupuesto,
        BigDecimal totalPresupuestado,
        BigDecimal totalGastado,
        /** Presupuesto menos gastado. Puede ser negativa: ahi la obra pierde plata. */
        BigDecimal gananciaEstimada,
        /** Porcentaje del presupuesto ya consumido. */
        BigDecimal porcentajeConsumido,
        BigDecimal totalGastoHormiga,
        String semaforoGeneral,
        List<RubroFinanciero> rubros) {

    /**
     * Comparacion de un rubro.
     *
     * Incluye rubros con gasto y sin presupuesto: son los mas importantes de
     * ver, porque significan que se esta gastando en algo que nadie presupuesto.
     */
    public record RubroFinanciero(
            Long idRubro,
            String nombreRubro,
            BigDecimal presupuestado,
            BigDecimal gastado,
            /** Presupuestado menos gastado. Negativo = excedido. */
            BigDecimal diferencia,
            BigDecimal porcentaje,
            String semaforo) {
    }
}
