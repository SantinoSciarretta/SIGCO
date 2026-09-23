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
    /** Entro algo de plata, pero todavia se debe el resto. */
    public static final String ESTADO_PARCIAL = "Parcial";
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

    /**
     * Los pagos recibidos a cuenta de esta cuota.
     *
     * El total cobrado no se guarda: se suma de aca. Un total guardado se puede
     * desincronizar del detalle —basta con que alguien agregue un pago y olvide
     * actualizarlo— y entonces el sistema afirma dos cosas distintas sobre la
     * misma plata.
     *
     * EAGER y no LAZY: no hay ni una operacion sobre una cuota que no necesite
     * saber cuanto se pago. Con LAZY, cada consulta del plan dispararia una
     * consulta extra por cuota.
     */
    @jakarta.persistence.OneToMany(mappedBy = "cuota",
            cascade = jakarta.persistence.CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.EAGER)
    @jakarta.persistence.OrderBy("fechaPago ASC, idPago ASC")
    private java.util.List<Pago> pagos = new java.util.ArrayList<>();

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
     * Registra un pago a cuenta de esta cuota.
     *
     * Puede ser el total o una parte. El estado se recalcula solo a partir de lo
     * que efectivamente entro: no lo decide quien llama.
     *
     * Si el cliente paga fuera de termino NO se cobra recargo, tal como maneja
     * hoy la empresa: por eso el monto de la cuota no se toca al cobrar.
     */
    public void registrarPago(Pago pago) {
        this.pagos.add(pago);

        // Las columnas de la cuota reflejan el ULTIMO pago recibido. Se
        // conservan porque el Diccionario las describe y porque la restriccion
        // ck_cuota_fecha_pago las exige en una cuota abonada.
        this.fechaPago = pago.getFechaPago();
        this.medioPago = pago.getMedioPago();
        this.comprobanteEmitido = pago.getComprobanteEmitido();
        this.motivoAnulacion = null;

        recalcularEstado();
    }

    /**
     * Anula TODOS los pagos: la cuota vuelve a deberse entera.
     *
     * Se anula la cobranza completa y no un pago suelto, y es una decision
     * deliberada: anular una parte de lo cobrado deja un estado de cuenta que
     * nadie puede reconstruir sin ver el historial entero. Si hubo un error en
     * un pago, se anula todo y se vuelven a cargar los que si entraron, que
     * ademas es como se corrige en una planilla.
     *
     * El informe pide dejar el motivo para conservar la trazabilidad.
     */
    public void anularPago(String motivo) {
        this.pagos.clear();
        this.fechaPago = null;
        this.medioPago = null;
        this.comprobanteEmitido = null;
        this.motivoAnulacion = motivo;

        recalcularEstado();
    }

    /**
     * El estado sale de la plata, no al reves.
     *
     * Nadie marca una cuota como abonada: se registran pagos y el estado es su
     * consecuencia. Asi no puede pasar que una cuota figure Abonada con saldo
     * pendiente, que es la clase de inconsistencia que aparece cuando el estado
     * se escribe a mano en un lugar y los montos en otro.
     */
    private void recalcularEstado() {
        BigDecimal pagado = totalPagado();

        if (pagado.compareTo(montoCuota) >= 0) {
            this.estado = ESTADO_ABONADA;
        } else if (pagado.signum() > 0) {
            this.estado = ESTADO_PARCIAL;
        } else if (fechaVencimiento.isBefore(LocalDate.now())) {
            // Sin pagos y con la fecha pasada: vuelve a estar vencida.
            this.estado = ESTADO_VENCIDA;
        } else {
            this.estado = ESTADO_PENDIENTE;
        }
    }

    /** Lo que entro hasta ahora por esta cuota. */
    public BigDecimal totalPagado() {
        return pagos.stream()
                .map(Pago::getMonto)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(DECIMALES, RoundingMode.HALF_UP);
    }

    /** Lo que falta cobrar. Nunca negativo: un pago de mas no genera saldo a favor. */
    public BigDecimal saldo() {
        BigDecimal restante = montoCuota.subtract(totalPagado());
        return restante.signum() < 0 ? BigDecimal.ZERO : restante;
    }

    public java.util.List<Pago> getPagos() {
        return java.util.Collections.unmodifiableList(pagos);
    }

    public boolean tienePagos() {
        return !pagos.isEmpty();
    }


    /**
     * Actualiza el monto por el indice CAC.
     *
     * Una cuota ya cobrada entera no se toca: el informe es explicito, y cobrar
     * de nuevo por algo ya pagado seria cambiar el pasado.
     *
     * ------------------------------------------------------------------
     *  Que pasa con una cuota pagada a medias
     * ------------------------------------------------------------------
     *
     * El coeficiente se aplica SOLO sobre el saldo impago:
     *
     *     nuevoMonto = totalPagado + (saldo x coeficiente)
     *
     * Lo que el cliente ya pago queda como esta —era el valor acordado cuando
     * pago— y lo que todavia debe se actualiza, que es el sentido del ajuste por
     * CAC. Multiplicar el monto entero encareceria retroactivamente una parte ya
     * cobrada, y el cliente terminaria debiendo plata por algo que ya pago.
     */
    public void actualizarPorCac(BigDecimal coeficiente) {
        if (estaAbonada()) {
            return;
        }

        BigDecimal pagado = totalPagado();
        this.montoCuota = pagado
                .add(saldo().multiply(coeficiente))
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
        // Una cuota ya cobrada entera no vence: no se debe nada.
        if (estaAbonada()) {
            return;
        }

        boolean pasoLaFecha = fechaVencimiento.isBefore(hoy);

        // Una cuota pagada a medias y vencida SIGUE vencida: que haya entrado
        // algo no cambia que el resto esta impago y fuera de termino. Es lo que
        // hay que reclamar, asi que tiene que aparecer en las alertas.
        if (pasoLaFecha) {
            this.estado = ESTADO_VENCIDA;
        } else if (tienePagos()) {
            this.estado = ESTADO_PARCIAL;
        } else {
            this.estado = ESTADO_PENDIENTE;
        }
    }

    public boolean esAnticipo() {
        return numeroCuota != null && numeroCuota == 0;
    }

    public boolean estaAbonada() {
        return ESTADO_ABONADA.equals(this.estado);
    }

    /**
     * Si todavia se debe algo de esta cuota.
     *
     * Incluye las vencidas y las pagadas a medias: en los tres casos queda
     * saldo. Lo que NO incluye es la cuota abonada entera.
     */
    public boolean estaPendiente() {
        return !estaAbonada();
    }

    /** Pagada en parte: entro algo y todavia queda saldo. */
    public boolean esParcial() {
        return ESTADO_PARCIAL.equals(this.estado)
                || (tienePagos() && saldo().signum() > 0);
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
