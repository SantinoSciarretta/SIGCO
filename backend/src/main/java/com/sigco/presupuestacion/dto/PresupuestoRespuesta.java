package com.sigco.presupuestacion.dto;

import com.sigco.presupuestacion.ItemPresupuesto;
import com.sigco.presupuestacion.Presupuesto;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Un presupuesto tal como sale de la API.
 *
 * Sobre el valor unitario: el informe dice que es interno y que NO aparece en
 * el PDF que recibe el cliente. Si aparece aca, porque esta pantalla la usa el
 * dueño, que es quien lo carga y lo necesita ver. La restriccion se aplica al
 * documento que se envia afuera, no a la aplicacion interna.
 */
public record PresupuestoRespuesta(
        Long idPresupuesto,
        Long idObra,
        String direccionObra,
        String nombreCliente,
        String tipoPresupuesto,
        Long idPresupuestoBase,
        Integer version,
        String estado,
        BigDecimal metrosCuadrados,
        BigDecimal valorPorM2,
        BigDecimal totalPresupuesto,
        BigDecimal anticipoPorcentaje,
        BigDecimal montoAnticipo,
        Integer cantidadCuotas,
        BigDecimal montoCuota,
        String plazoEstimadoObra,
        LocalDateTime fechaCreacion,
        List<ItemRespuesta> items,
        List<SubtotalRubro> subtotalesPorRubro) {

    /** Un item del presupuesto. */
    public record ItemRespuesta(
            Long idItem,
            Long idRubro,
            String nombreRubro,
            Long idSubrubro,
            String nombreSubrubro,
            Long idMaterial,
            String nombreMaterial,
            String descripcion,
            String unidadMedida,
            BigDecimal cantidad,
            BigDecimal valorUnitario,
            BigDecimal subtotal) {

        static ItemRespuesta desde(ItemPresupuesto item) {
            return new ItemRespuesta(
                    item.getIdItem(),
                    item.getRubro().getIdRubro(),
                    item.getRubro().getNombreRubro(),
                    item.getSubrubro() != null ? item.getSubrubro().getIdSubrubro() : null,
                    item.getSubrubro() != null ? item.getSubrubro().getNombreSubrubro() : null,
                    item.getMaterial() != null ? item.getMaterial().getIdMaterial() : null,
                    item.getMaterial() != null ? item.getMaterial().getNombreMaterial() : null,
                    item.getDescripcion(),
                    item.getUnidadMedida(),
                    item.getCantidad(),
                    item.getValorUnitario(),
                    item.getSubtotal());
        }
    }

    /**
     * Total acumulado de un rubro.
     *
     * Es lo unico que ve el cliente en el PDF, y ademas es la referencia contra
     * la que el modulo Gastos compara el gasto real de la obra.
     */
    public record SubtotalRubro(Long idRubro, String nombreRubro, BigDecimal subtotal) {
    }

    /** Version completa, con items. Requiere que esten cargados (JOIN FETCH). */
    public static PresupuestoRespuesta completa(Presupuesto p) {
        return construir(p, p.getItems());
    }

    /** Version sin items, para los listados. */
    public static PresupuestoRespuesta resumida(Presupuesto p) {
        return construir(p, List.of());
    }

    private static PresupuestoRespuesta construir(Presupuesto p, List<ItemPresupuesto> items) {
        return new PresupuestoRespuesta(
                p.getIdPresupuesto(),
                p.getObra().getIdObra(),
                p.getObra().getDireccionObra(),
                p.getObra().getCliente().getNombreApellido(),
                p.getTipoPresupuesto(),
                p.getPresupuestoBase() != null ? p.getPresupuestoBase().getIdPresupuesto() : null,
                p.getVersion(),
                p.getEstado(),
                p.getMetrosCuadrados(),
                p.getValorPorM2(),
                p.getTotalPresupuesto(),
                p.getAnticipoPorcentaje(),
                p.calcularAnticipo(),
                p.getCantidadCuotas(),
                p.calcularMontoDeCuota(),
                p.getPlazoEstimadoObra(),
                p.getFechaCreacion(),
                items.stream().map(ItemRespuesta::desde).toList(),
                agruparPorRubro(items));
    }

    /**
     * Agrupa los items por rubro y suma sus subtotales.
     *
     * Se calcula al armar la respuesta y no se guarda en la base: es una suma
     * de datos que ya estan, y guardarla obligaria a mantenerla sincronizada
     * con cada cambio de item.
     */
    private static List<SubtotalRubro> agruparPorRubro(List<ItemPresupuesto> items) {
        Map<Long, BigDecimal> totales = items.stream().collect(Collectors.groupingBy(
                item -> item.getRubro().getIdRubro(),
                Collectors.reducing(BigDecimal.ZERO, ItemPresupuesto::getSubtotal, BigDecimal::add)));

        Map<Long, String> nombres = items.stream().collect(Collectors.toMap(
                item -> item.getRubro().getIdRubro(),
                item -> item.getRubro().getNombreRubro(),
                (uno, otro) -> uno));

        return totales.entrySet().stream()
                .map(entrada -> new SubtotalRubro(
                        entrada.getKey(), nombres.get(entrada.getKey()), entrada.getValue()))
                .sorted(Comparator.comparing(SubtotalRubro::nombreRubro))
                .toList();
    }
}
