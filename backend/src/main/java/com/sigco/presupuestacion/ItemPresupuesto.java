package com.sigco.presupuestacion;

import com.sigco.materiales.Material;
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

    /**
     * Material del catalogo al que refiere el item. Opcional.
     *
     * No todo item es un material: "Mano de obra de albanileria" o "Direccion
     * de obra" son items legitimos que no salen del catalogo. Cuando si lo es,
     * este vinculo permite responder en que presupuestos se uso un material y
     * evita que el mismo insumo se escriba distinto en cada obra.
     *
     * La descripcion se guarda igual: es lo que se imprime en el PDF y puede
     * necesitar mas detalle que el nombre del catalogo.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_material")
    private Material material;

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

    /**
     * A qué rubro se refiere el ítem, en los rubros especiales (V26).
     *
     * En un ítem de mano de obra dice de qué rubro es esa mano de obra: el ítem
     * pertenece al rubro "Mano de obra", pero el trabajo es de Albañilería. Y en
     * un ítem de imprevistos dice sobre qué rubro se calcula el porcentaje. En
     * los ítems comunes queda vacío.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_rubro_referido")
    private Rubro rubroReferido;

    /**
     * El porcentaje de un ítem de imprevistos. Se guarda el porcentaje y no
     * solo el monto para que el monto se recalcule cuando cambian los
     * materiales o la mano de obra del rubro. En los demás ítems queda vacío.
     */
    @Column(name = "porcentaje", precision = 5, scale = 2)
    private BigDecimal porcentaje;


    /**
     * Constructor vacío que exige la base de datos (JPA) para poder armar el
     * objeto al leerlo. No se usa desde el código.
     */
    protected ItemPresupuesto() {
    }

    /**
     * Crea un ítem del presupuesto con su rubro, subrubro, material (si
     * corresponde), descripción, unidad, cantidad y precio unitario. El
     * subtotal se calcula solo.
     */
    public ItemPresupuesto(Presupuesto presupuesto, Rubro rubro, Subrubro subrubro,
                           Material material, String descripcion, String unidadMedida,
                           BigDecimal cantidad, BigDecimal valorUnitario) {
        this.presupuesto = presupuesto;
        this.rubro = rubro;
        this.subrubro = subrubro;
        this.material = material;
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
    public void actualizar(Rubro rubro, Subrubro subrubro, Material material,
                           String descripcion, String unidadMedida,
                           BigDecimal cantidad, BigDecimal valorUnitario) {
        this.rubro = rubro;
        this.subrubro = subrubro;
        this.material = material;
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
    /**
     * Un ítem de mano de obra: 1 global por el total, en el rubro de mano de
     * obra, recordando de qué rubro es el trabajo.
     */
    public static ItemPresupuesto deManoDeObra(Presupuesto presupuesto, Rubro manoDeObra,
                                               Rubro rubroReferido, String descripcion,
                                               BigDecimal total) {
        ItemPresupuesto item = new ItemPresupuesto(presupuesto, manoDeObra, null, null,
                descripcion, "global", BigDecimal.ONE, total);
        item.rubroReferido = rubroReferido;
        return item;
    }

    /**
     * Un ítem de imprevistos: un porcentaje sobre el total de un rubro. El
     * monto arranca en cero y lo calcula el presupuesto (ver
     * Presupuesto.recalcularTotal), que es quien conoce el total del rubro.
     */
    public static ItemPresupuesto deImprevistos(Presupuesto presupuesto, Rubro imprevistos,
                                                Rubro rubroReferido, BigDecimal porcentaje) {
        ItemPresupuesto item = new ItemPresupuesto(presupuesto, imprevistos, null, null,
                rubroReferido.getNombreRubro(), "%", BigDecimal.ONE, BigDecimal.ZERO);
        item.rubroReferido = rubroReferido;
        item.porcentaje = porcentaje;
        return item;
    }

    /** Una copia de este ítem para otro presupuesto (al duplicar). */
    public ItemPresupuesto copiarPara(Presupuesto destino) {
        ItemPresupuesto copia = new ItemPresupuesto(destino, rubro, subrubro, material,
                descripcion, unidadMedida, cantidad, valorUnitario);
        copia.rubroReferido = rubroReferido;
        copia.porcentaje = porcentaje;
        return copia;
    }

    /** Indica si es un ítem de imprevistos, calculado por porcentaje. */
    public boolean esImprevisto() {
        return porcentaje != null && rubro.esImprevistos();
    }

    /**
     * Fija el monto de un imprevisto: el porcentaje aplicado sobre la base
     * (materiales más mano de obra del rubro al que se refiere).
     */
    void calcularImprevisto(BigDecimal base) {
        this.valorUnitario = base.multiply(porcentaje)
                .divide(BigDecimal.valueOf(100), DECIMALES, RoundingMode.HALF_UP);
        this.subtotal = calcularSubtotal(cantidad, valorUnitario);
    }

    private static BigDecimal calcularSubtotal(BigDecimal cantidad, BigDecimal valorUnitario) {
        return cantidad.multiply(valorUnitario).setScale(DECIMALES, RoundingMode.HALF_UP);
    }

    // ---------- Metodos de acceso ----------

    /**
     * Métodos de lectura: devuelven los datos guardados del ítem. Solo leen, no
     * modifican nada.
     */
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

    public Material getMaterial() {
        return material;
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

    public Rubro getRubroReferido() {
        return rubroReferido;
    }

    public BigDecimal getPorcentaje() {
        return porcentaje;
    }
}
