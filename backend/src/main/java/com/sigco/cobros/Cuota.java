package com.sigco.cobros;

import com.sigco.obras.Obra;
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
import java.time.LocalDate;

/**
 * Una cuota del plan de cobro de la obra.
 *
 * EL ANTICIPO ES LA CUOTA CERO. No es una tabla aparte ni un campo especial: es
 * la primera fila del plan, con numero 0. Asi el saldo, los vencimientos y el
 * total cobrado salen de una sola consulta, sin tener que sumar el anticipo por
 * separado en cada calculo.
 */
@Entity
@Table(name = "cuota")
public class Cuota {

    public static final String ESTADO_PENDIENTE = "Pendiente";
    public static final String ESTADO_ABONADA = "Abonada";
    public static final String ESTADO_VENCIDA = "Vencida";

    private static final int DECIMALES = 2;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_cuota")
    private Long idCuota;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_obra", nullable = false)
    private Obra obra;

    /** Cero es el anticipo. */
    @Column(name = "numero_cuota", nullable = false)
    private Integer numeroCuota;

    @Column(name = "monto_cuota", nullable = false, precision = 14, scale = 2)
    private BigDecimal montoCuota;

    @Column(name = "fecha_vencimiento", nullable = false)
    private LocalDate fechaVencimiento;

    @Column(name = "estado", nullable = false, length = 12)
    private String estado;

    @Column(name = "fecha_pago")
    private LocalDate fechaPago;

    @Column(name = "medio_pago", length = 15)
    private String medioPago;

    @Column(name = "comprobante_emitido", length = 20)
    private String comprobanteEmitido;

    @Column(name = "motivo_anulacion", length = 200)
    private String motivoAnulacion;

    protected Cuota() {
    }

    public Cuota(Obra obra, Integer numeroCuota, BigDecimal montoCuota,
                 LocalDate fechaVencimiento) {
        this.obra = obra;
        this.numeroCuota = numeroCuota;
        this.montoCuota = montoCuota;
        this.fechaVencimiento = fechaVencimiento;
        this.estado = ESTADO_PENDIENTE;
    }

    /**
     * Registra el pago de la cuota.
     *
     * La fecha es obligatoria y la controla el servicio: es regla del informe.
     * Si el cliente paga fuera de termino NO se cobra recargo, tal como maneja
     * hoy la empresa: por eso el monto no se toca al abonar.
     */
    public void abonar(LocalDate fechaPago, String medioPago, String comprobanteEmitido) {
        this.estado = ESTADO_ABONADA;
        this.fechaPago = fechaPago;
        this.medioPago = medioPago;
        this.comprobanteEmitido = comprobanteEmitido;
        this.motivoAnulacion = null;
    }

    /**
     * Anula el pago: la cuota vuelve a deberse.
     *
     * Lo que se anula es EL PAGO, no la cuota. El informe pide dejar el motivo
     * para conservar la trazabilidad del estado de cuenta.
     */
    public void anularPago(String motivo) {
        this.estado = ESTADO_PENDIENTE;
        this.fechaPago = null;
        this.medioPago = null;
        this.comprobanteEmitido = null;
        this.motivoAnulacion = motivo;
    }

    /**
     * Actualiza el monto por el indice CAC.
     *
     * Solo tiene efecto sobre cuotas pendientes: el informe es explicito en que
     * las ya cobradas no se modifican. Cobrar de nuevo por algo ya pagado seria
     * cambiar el pasado.
     */
    public void actualizarPorCac(BigDecimal coeficiente) {
        if (!estaPendiente()) {
            return;
        }
        this.montoCuota = montoCuota.multiply(coeficiente)
                .setScale(DECIMALES, RoundingMode.HALF_UP);
    }

    /**
     * Marca la cuota como vencida si paso su fecha y sigue impaga.
     *
     * El estado Vencida se deriva del calendario, no lo pone nadie a mano: si
     * dependiera de que alguien lo marque, volveria a depender de la memoria
     * del dueño, que es lo que el modulo viene a resolver.
     */
    public void revisarVencimiento(LocalDate hoy) {
        if (estaPendiente() && fechaVencimiento.isBefore(hoy)) {
            this.estado = ESTADO_VENCIDA;
        } else if (ESTADO_VENCIDA.equals(estado) && !fechaVencimiento.isBefore(hoy)) {
            this.estado = ESTADO_PENDIENTE;
        }
    }

    public boolean esAnticipo() {
        return numeroCuota != null && numeroCuota == 0;
    }

    public boolean estaAbonada() {
        return ESTADO_ABONADA.equals(this.estado);
    }

    /** Pendiente incluye las vencidas: siguen debiendose. */
    public boolean estaPendiente() {
        return ESTADO_PENDIENTE.equals(this.estado) || ESTADO_VENCIDA.equals(this.estado);
    }

    public boolean estaVencida() {
        return ESTADO_VENCIDA.equals(this.estado);
    }

    // ---------- Metodos de acceso ----------

    public Long getIdCuota() {
        return idCuota;
    }

    public Obra getObra() {
        return obra;
    }

    public Integer getNumeroCuota() {
        return numeroCuota;
    }

    public BigDecimal getMontoCuota() {
        return montoCuota;
    }

    public LocalDate getFechaVencimiento() {
        return fechaVencimiento;
    }

    public String getEstado() {
        return estado;
    }

    public LocalDate getFechaPago() {
        return fechaPago;
    }

    public String getMedioPago() {
        return medioPago;
    }

    public String getComprobanteEmitido() {
        return comprobanteEmitido;
    }

    public String getMotivoAnulacion() {
        return motivoAnulacion;
    }
}
