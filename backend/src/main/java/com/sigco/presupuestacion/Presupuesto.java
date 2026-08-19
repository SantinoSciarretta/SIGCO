package com.sigco.presupuestacion;

import com.sigco.obras.Obra;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Una version de presupuesto de una obra.
 *
 * El informe releva tres instancias reales del proceso, y este sistema las
 * representa como tipos distintos de un mismo registro:
 *
 *   Cotizacion inicial → precio estimativo, m2 x valor por m2, sin items
 *   Anteproyecto       → por rubro, solo en reformas
 *   Definitivo         → por rubro, subrubro e item
 *   Adicional          → cambio menor sobre un definitivo ya aprobado
 *
 * El versionado es real: cuando el definitivo se arma tomando el anteproyecto
 * como base, el anteproyecto NO se sobrescribe. Quedan los dos como registros
 * independientes, vinculados por presupuestoBase. Eso es lo que resuelve el
 * problema del relevamiento, donde las versiones se pisan entre si en Excel y
 * despues nadie sabe cual fue la aprobada.
 */
@Entity
@Table(name = "presupuesto")
public class Presupuesto {

    /** Tipos validos. */
    public static final String TIPO_COTIZACION_INICIAL = "Cotización inicial";
    public static final String TIPO_ANTEPROYECTO = "Anteproyecto";
    public static final String TIPO_DEFINITIVO = "Definitivo";
    public static final String TIPO_ADICIONAL = "Adicional";

    /** Estados del ciclo de negociacion con el cliente. */
    public static final String ESTADO_BORRADOR = "Borrador";
    public static final String ESTADO_ENVIADO = "Enviado";
    public static final String ESTADO_APROBADO = "Aprobado";
    public static final String ESTADO_RECHAZADO = "Rechazado";

    private static final int DECIMALES = 2;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_presupuesto")
    private Long idPresupuesto;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_obra", nullable = false)
    private Obra obra;

    @Column(name = "tipo_presupuesto", nullable = false, length = 20)
    private String tipoPresupuesto;

    /**
     * Presupuesto que se uso como punto de partida.
     *
     * Es una autorreferencia: un presupuesto apunta a otro de la misma tabla.
     * Siguiendo esa cadena se reconstruye la historia completa de versiones de
     * una obra sin duplicar informacion.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_presupuesto_base")
    private Presupuesto presupuestoBase;

    @Column(name = "version", nullable = false)
    private Integer version;

    @Column(name = "estado", nullable = false, length = 15)
    private String estado;

    /** Solo en la cotizacion inicial. */
    @Column(name = "metros_cuadrados", precision = 10, scale = 2)
    private BigDecimal metrosCuadrados;

    /** Solo en la cotizacion inicial. */
    @Column(name = "valor_por_m2", precision = 12, scale = 2)
    private BigDecimal valorPorM2;

    @Column(name = "total_presupuesto", precision = 14, scale = 2)
    private BigDecimal totalPresupuesto;

    @Column(name = "anticipo_porcentaje", precision = 5, scale = 2)
    private BigDecimal anticipoPorcentaje;

    @Column(name = "cantidad_cuotas")
    private Integer cantidadCuotas;

    @Column(name = "plazo_estimado_obra", length = 100)
    private String plazoEstimadoObra;

    @Column(name = "fecha_creacion", nullable = false)
    private LocalDateTime fechaCreacion;

    /**
     * Items del presupuesto.
     *
     * cascade = ALL y orphanRemoval: los items no existen fuera de su
     * presupuesto, asi que se guardan y se borran con el. Quitar un item de
     * esta lista lo elimina de la base. No contradice la regla de "nada se
     * elimina": esa regla habla del presupuesto completo, y solo se pueden
     * tocar items mientras esta en Borrador.
     */
    @OneToMany(mappedBy = "presupuesto", fetch = FetchType.LAZY,
               cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ItemPresupuesto> items = new ArrayList<>();

    protected Presupuesto() {
    }

    /**
     * Alta de un presupuesto. Nace siempre en Borrador: todavia no se le mostro
     * nada al cliente.
     */
    public Presupuesto(Obra obra, String tipoPresupuesto, Integer version,
                       Presupuesto presupuestoBase, String plazoEstimadoObra) {
        this.obra = obra;
        this.tipoPresupuesto = tipoPresupuesto;
        this.version = version;
        this.presupuestoBase = presupuestoBase;
        this.plazoEstimadoObra = plazoEstimadoObra;
        this.estado = ESTADO_BORRADOR;
        this.fechaCreacion = LocalDateTime.now();
        this.totalPresupuesto = BigDecimal.ZERO.setScale(DECIMALES);
    }

    // ------------------------------------------------------------------
    //  Calculo del total
    // ------------------------------------------------------------------

    /**
     * Carga los datos de la cotizacion inicial y calcula el precio estimativo.
     *
     * Es la unica instancia que no lleva items: el precio sale de multiplicar
     * la superficie por un valor de referencia, que es exactamente como la
     * empresa lo hace hoy.
     */
    public void calcularCotizacionInicial(BigDecimal metrosCuadrados, BigDecimal valorPorM2) {
        this.metrosCuadrados = metrosCuadrados;
        this.valorPorM2 = valorPorM2;
        this.totalPresupuesto = metrosCuadrados.multiply(valorPorM2)
                .setScale(DECIMALES, RoundingMode.HALF_UP);
    }

    /**
     * Recalcula el total sumando los subtotales de todos los items.
     *
     * Se llama cada vez que cambia un item. El total se guarda calculado, no se
     * deriva al leer: asi un presupuesto aprobado conserva exactamente el numero
     * que se le mostro al cliente.
     */
    public void recalcularTotal() {
        this.totalPresupuesto = items.stream()
                .map(ItemPresupuesto::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(DECIMALES, RoundingMode.HALF_UP);
    }

    public void agregarItem(ItemPresupuesto item) {
        this.items.add(item);
        recalcularTotal();
    }

    public void quitarItem(ItemPresupuesto item) {
        this.items.remove(item);
        recalcularTotal();
    }

    // ------------------------------------------------------------------
    //  Plan de pago
    // ------------------------------------------------------------------

    /**
     * Define el plan de pago: cuanto se pide de anticipo y en cuantas cuotas se
     * divide el resto. Lo consume despues el modulo Cobros para generar el
     * plan de cuotas de la obra.
     */
    public void definirPlanDePago(BigDecimal anticipoPorcentaje, Integer cantidadCuotas,
                                  String plazoEstimadoObra) {
        this.anticipoPorcentaje = anticipoPorcentaje;
        this.cantidadCuotas = cantidadCuotas;
        this.plazoEstimadoObra = plazoEstimadoObra;
    }

    /** Importe del anticipo, a partir del porcentaje definido. */
    public BigDecimal calcularAnticipo() {
        if (anticipoPorcentaje == null || totalPresupuesto == null) {
            return null;
        }
        return totalPresupuesto
                .multiply(anticipoPorcentaje)
                .divide(BigDecimal.valueOf(100), DECIMALES, RoundingMode.HALF_UP);
    }

    /**
     * Importe de cada cuota: el saldo despues del anticipo, dividido en la
     * cantidad de cuotas.
     */
    public BigDecimal calcularMontoDeCuota() {
        BigDecimal anticipo = calcularAnticipo();
        if (anticipo == null || cantidadCuotas == null || cantidadCuotas == 0) {
            return null;
        }
        return totalPresupuesto.subtract(anticipo)
                .divide(BigDecimal.valueOf(cantidadCuotas), DECIMALES, RoundingMode.HALF_UP);
    }

    // ------------------------------------------------------------------
    //  Estados
    // ------------------------------------------------------------------

    public void enviar() {
        this.estado = ESTADO_ENVIADO;
    }

    public void aprobar() {
        this.estado = ESTADO_APROBADO;
    }

    public void rechazar() {
        this.estado = ESTADO_RECHAZADO;
    }

    public boolean esBorrador() {
        return ESTADO_BORRADOR.equals(this.estado);
    }

    public boolean estaAprobado() {
        return ESTADO_APROBADO.equals(this.estado);
    }

    public boolean estaCerrado() {
        return ESTADO_APROBADO.equals(this.estado) || ESTADO_RECHAZADO.equals(this.estado);
    }

    public boolean esCotizacionInicial() {
        return TIPO_COTIZACION_INICIAL.equals(this.tipoPresupuesto);
    }

    public boolean esDefinitivo() {
        return TIPO_DEFINITIVO.equals(this.tipoPresupuesto);
    }

    /** Los tipos que se cargan con items, a diferencia de la cotizacion. */
    public boolean llevaItems() {
        return !esCotizacionInicial();
    }

    // ---------- Metodos de acceso ----------

    public Long getIdPresupuesto() {
        return idPresupuesto;
    }

    public Obra getObra() {
        return obra;
    }

    public String getTipoPresupuesto() {
        return tipoPresupuesto;
    }

    public Presupuesto getPresupuestoBase() {
        return presupuestoBase;
    }

    public Integer getVersion() {
        return version;
    }

    public String getEstado() {
        return estado;
    }

    public BigDecimal getMetrosCuadrados() {
        return metrosCuadrados;
    }

    public BigDecimal getValorPorM2() {
        return valorPorM2;
    }

    public BigDecimal getTotalPresupuesto() {
        return totalPresupuesto;
    }

    public BigDecimal getAnticipoPorcentaje() {
        return anticipoPorcentaje;
    }

    public Integer getCantidadCuotas() {
        return cantidadCuotas;
    }

    public String getPlazoEstimadoObra() {
        return plazoEstimadoObra;
    }

    public LocalDateTime getFechaCreacion() {
        return fechaCreacion;
    }

    public List<ItemPresupuesto> getItems() {
        return items;
    }
}
