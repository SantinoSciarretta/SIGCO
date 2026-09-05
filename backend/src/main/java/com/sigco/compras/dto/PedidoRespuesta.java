package com.sigco.compras.dto;

import com.sigco.compras.Pedido;
import com.sigco.compras.PedidoMaterial;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Lo que el modulo Compras devuelve.
 *
 * Nunca se devuelve la entidad: el DTO decide que se expone y evita arrastrar
 * relaciones perezosas que reventarian al serializar con la sesion ya cerrada.
 */
public record PedidoRespuesta(
        Long idPedido,
        Long idObra,
        String direccionObra,
        String nombreCliente,
        Long idProveedor,
        String nombreProveedor,
        String zonaCobertura,
        String estado,
        String fotoRemito,
        String notaDiferencia,
        String motivoAnulacion,
        LocalDateTime fechaSolicitud,
        LocalDateTime fechaAprobacion,
        LocalDateTime fechaRecepcion,
        BigDecimal total,
        boolean tieneTodosLosPrecios,
        Integer cantidadMateriales,
        List<LineaRespuesta> materiales) {

    /** Version para el listado: sin las lineas, que ahi no se muestran. */
    public static PedidoRespuesta resumen(Pedido p) {
        return construir(p, null);
    }

    /** Version para el detalle: con cada material pedido. */
    public static PedidoRespuesta completa(Pedido p) {
        return construir(p, p.getMateriales().stream().map(LineaRespuesta::desde).toList());
    }

    private static PedidoRespuesta construir(Pedido p, List<LineaRespuesta> lineas) {
        return new PedidoRespuesta(
                p.getIdPedido(),
                p.getObra().getIdObra(),
                p.getObra().getDireccionObra(),
                p.getObra().getCliente().getNombreApellido(),
                p.getProveedor() != null ? p.getProveedor().getIdProveedor() : null,
                p.getProveedor() != null ? p.getProveedor().getNombreProveedor() : null,
                p.getProveedor() != null ? p.getProveedor().getZonaCobertura() : null,
                p.getEstado(),
                p.getFotoRemito(),
                p.getNotaDiferencia(),
                p.getMotivoAnulacion(),
                p.getFechaSolicitud(),
                p.getFechaAprobacion(),
                p.getFechaRecepcion(),
                p.calcularTotal(),
                p.tieneTodosLosPrecios(),
                p.getMateriales().size(),
                lineas);
    }

    /** Una linea del pedido, con el material resuelto desde el catalogo. */
    public record LineaRespuesta(
            Long idMaterial,
            String nombreMaterial,
            String unidadMedida,
            String nombreRubro,
            BigDecimal cantidad,
            BigDecimal precioUnitario,
            BigDecimal subtotal) {

        static LineaRespuesta desde(PedidoMaterial linea) {
            return new LineaRespuesta(
                    linea.getMaterial().getIdMaterial(),
                    linea.getMaterial().getNombreMaterial(),
                    linea.getMaterial().getUnidadMedida(),
                    linea.getMaterial().getRubro().getNombreRubro(),
                    linea.getCantidad(),
                    linea.getPrecioUnitario(),
                    linea.calcularSubtotal());
        }
    }
}
