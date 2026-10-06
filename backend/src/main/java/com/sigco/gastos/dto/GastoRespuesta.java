package com.sigco.gastos.dto;

import com.sigco.gastos.Gasto;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Lo que el modulo Gastos devuelve. Nunca se expone la entidad. */
public record GastoRespuesta(
        Long idGasto,
        Long idObra,
        String direccionObra,
        Long idRubro,
        String nombreRubro,
        Long idSubrubro,
        String nombreSubrubro,
        String tipoGasto,
        BigDecimal monto,
        LocalDate fechaGasto,
        LocalDateTime fechaCarga,
        Long idPedido,
        Long idOperario,
        String descripcion,
        String comprobanteAdjunto,
        String estado,
        String motivoAnulacion) {

    /**
     * Convierte un gasto guardado en el formato que se le envía a la pantalla,
     * con los nombres de la obra, el rubro y el subrubro.
     */
    public static GastoRespuesta desde(Gasto g) {
        return new GastoRespuesta(
                g.getIdGasto(),
                g.getObra().getIdObra(),
                g.getObra().getDireccionObra(),
                g.getRubro().getIdRubro(),
                g.getRubro().getNombreRubro(),
                g.getSubrubro() != null ? g.getSubrubro().getIdSubrubro() : null,
                g.getSubrubro() != null ? g.getSubrubro().getNombreSubrubro() : null,
                g.getTipoGasto(),
                g.getMonto(),
                g.getFechaGasto(),
                g.getFechaCarga(),
                g.getIdPedido(),
                g.getIdOperario(),
                g.getDescripcion(),
                g.getComprobanteAdjunto(),
                g.getEstado(),
                g.getMotivoAnulacion());
    }
}
