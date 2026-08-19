package com.sigco.presupuestacion;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Item de un presupuesto: un trabajo puntual con su cantidad y su precio.
 *
 * Los importes son BigDecimal, nunca double. El informe lo aclara
 * explicitamente: con punto flotante, sumar muchos subtotales acumula errores
 * de redondeo, y aca se esta calculando lo que se le cobra a un cliente.
 */
@Entity
@Table(name = "item_presupuesto")
public class ItemPresupuesto {

    /** Escala de los importes: dos decimales, como NUMERIC(n,2) en la base. */
    private static final int DECIMALES = 2;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_item")
    private Long idItem;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_presupuesto", nullable = false)
    private Presupuesto presupuesto;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_rubro", nullable = false)
    private Rubro rubro;

    /**
     * Opcional: el anteproyecto se carga solo a nivel de rubro, y recien el
     * presupuesto definitivo baja al detalle de subrubro.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_subrubro")
    private Subrubro subrubro;

    @Column(name = "descripcion", nullable = false, length = 250)
    private String descripcion;

    @Column(name = "unidad_medida", nullable = false, length = 20)
    private String unidadMedida;

    @Column(name = "cantidad", nullable = false, precision = 12, scale = 2)
    private BigDecimal cantidad;

    /** Dato interno: no se muestra en el PDF que recibe el cliente. */
    @Column(name = "valor_unitario", nullable = false, precision = 12, scale = 2)
    private BigDecimal valorUnitario;

    @Column(name = "subtotal", nullable = false, precision = 14, scale = 2)
    private BigDecimal subtotal;

    protected ItemPresupuesto() {
    }

    public ItemPresupuesto(Presupuesto presupuesto, Rubro rubro, Subrubro subrubro,
                           String descripcion, String unidadMedida,
                           BigDecimal cantidad, BigDecimal valorUnitario) {
        this.presupuesto = presupuesto;
        this.rubro = rubro;
        this.subrubro = subrubro;
        this.descripcion = descripcion;
        this.unidadMedida = unidadMedida;
        this.cantidad = cantidad;
        this.valorUnitario = valorUnitario;
        this.subtotal = calcularSubtotal(cantidad, valorUnitario);
    }

    /**
     * Cambia los datos del item y recalcula su subtotal.
     *
     * El subtotal nunca se recibe de afuera: se deriva siempre de cantidad y
     * valor unitario. Si llegara como dato, un pedido mal armado podria guardar
     * un total que no se corresponde con sus partes.
     */
    public void actualizar(Rubro rubro, Subrubro subrubro, String descripcion,
                           String unidadMedida, BigDecimal cantidad, BigDecimal valorUnitario) {
        this.rubro = rubro;
        this.subrubro = subrubro;
        this.descripcion = descripcion;
        this.unidadMedida = unidadMedida;
        this.cantidad = cantidad;
        this.valorUnitario = valorUnitario;
        this.subtotal = calcularSubtotal(cantidad, valorUnitario);
    }

    /**
     * cantidad x valor unitario, redondeado a dos decimales.
     *
     * HALF_UP es el redondeo comercial: 0,005 sube a 0,01. Es el que espera
     * cualquiera que revise la cuenta a mano.
     */
    private static BigDecimal calcularSubtotal(BigDecimal cantidad, BigDecimal valorUnitario) {
        return cantidad.multiply(valorUnitario).setScale(DECIMALES, RoundingMode.HALF_UP);
    }

    // ---------- Metodos de acceso ----------

    public Long getIdItem() {
        return idItem;
    }

    public Presupuesto getPresupuesto() {
        return presupuesto;
    }

    public Rubro getRubro() {
        return rubro;
    }

    public Subrubro getSubrubro() {
        return subrubro;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public String getUnidadMedida() {
        return unidadMedida;
    }

    public BigDecimal getCantidad() {
        return cantidad;
    }

    public BigDecimal getValorUnitario() {
        return valorUnitario;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }
}
