package com.sigco.compras;

import com.sigco.obras.Obra;
import com.sigco.proveedores.Proveedor;
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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Pedido de materiales para una obra.
 *
 * Reemplaza el circuito actual por WhatsApp entre el capataz y el dueño, que no
 * deja registro ni permite seguir el estado de un pedido. El informe describe
 * tres momentos bien separados, y cada uno tiene su metodo:
 *
 *   1. Alguien en la obra detecta que faltan materiales y arma el pedido.
 *      Queda "Pendiente de Aprobacion", SIN proveedor: el capataz no decide a
 *      quien comprarle.
 *   2. El dueño lo aprueba y elige el proveedor segun la zona de la obra.
 *      Pasa a "Enviado al Proveedor". Es una accion no delegable.
 *   3. Cuando llega el camion, quien recibe controla contra el remito, saca la
 *      foto y confirma. Queda "Recibido Completo" o "Recibido con Diferencias".
 *
 * Aprobar y enviar son la MISMA transicion, no dos: el informe dice que al
 * aprobarlo el dueño elige el proveedor y el pedido pasa a Enviado. Separarlos
 * agregaria un estado que el circuito real no tiene.
 */
@Entity
@Table(name = "pedido")
public class Pedido {

    public static final String ESTADO_PENDIENTE = "Pendiente de Aprobación";
    public static final String ESTADO_ENVIADO = "Enviado al Proveedor";
    public static final String ESTADO_RECIBIDO_COMPLETO = "Recibido Completo";
    public static final String ESTADO_RECIBIDO_CON_DIFERENCIAS = "Recibido con Diferencias";
    public static final String ESTADO_ANULADO = "Anulado";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_pedido")
    private Long idPedido;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_obra", nullable = false)
    private Obra obra;

    /**
     * Proveedor al que se le compra. Nulo mientras el pedido esta pendiente:
     * lo elige el dueño al aprobar, no quien arma el pedido.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_proveedor")
    private Proveedor proveedor;

    /** Quien lo pidio y quien lo recibio. Los aporta la sesion (modulo Accesos). */
    @Column(name = "id_usuario_solicita")
    private Long idUsuarioSolicita;

    @Column(name = "id_usuario_recibe")
    private Long idUsuarioRecibe;

    @Column(name = "estado", nullable = false, length = 25)
    private String estado;

    /** Referencia al archivo en Supabase Storage, nunca el binario. */
    @Column(name = "foto_remito", length = 255)
    private String fotoRemito;

    @Column(name = "nota_diferencia", length = 300)
    private String notaDiferencia;

    @Column(name = "motivo_anulacion", length = 200)
    private String motivoAnulacion;

    @Column(name = "fecha_solicitud", nullable = false)
    private LocalDateTime fechaSolicitud;

    @Column(name = "fecha_aprobacion")
    private LocalDateTime fechaAprobacion;

    @Column(name = "fecha_recepcion")
    private LocalDateTime fechaRecepcion;

    /**
     * Token del link publico de la orden. NULL = no hay link vigente.
     *
     * El link de WhatsApp no puede adjuntar archivos, asi que el PDF de la
     * orden viaja como enlace dentro del mensaje y tiene que abrirse sin estar
     * logueado: el corralon no tiene cuenta en SIGCO. El token es lo que hace
     * que ese enlace no se pueda adivinar ni enumerar.
     */
    @Column(name = "token_orden", length = 64)
    private String tokenOrden;

    @Column(name = "token_orden_vence")
    private LocalDateTime tokenOrdenVence;

    /**
     * Materiales pedidos. cascade = ALL con orphanRemoval, igual que los items
     * de un presupuesto: una linea de pedido no existe fuera de su pedido.
     */
    @OneToMany(mappedBy = "pedido", fetch = FetchType.LAZY,
               cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PedidoMaterial> materiales = new ArrayList<>();

    protected Pedido() {
    }

    public Pedido(Obra obra, Long idUsuarioSolicita) {
        this.obra = obra;
        this.idUsuarioSolicita = idUsuarioSolicita;
        this.estado = ESTADO_PENDIENTE;
        this.fechaSolicitud = LocalDateTime.now();
    }

    // ------------------------------------------------------------------
    //  Circuito
    // ------------------------------------------------------------------

    public void agregarMaterial(PedidoMaterial linea) {
        this.materiales.add(linea);
    }

    public void quitarMaterial(PedidoMaterial linea) {
        this.materiales.remove(linea);
    }

    /**
     * Aprueba el pedido y lo envia al proveedor elegido.
     *
     * Una sola operacion porque el circuito real lo es: el dueño no aprueba
     * primero y elige el proveedor despues, decide las dos cosas juntas.
     */
    public void aprobar(Proveedor proveedor) {
        this.proveedor = proveedor;
        this.estado = ESTADO_ENVIADO;
        this.fechaAprobacion = LocalDateTime.now();
    }

    /**
     * Confirma la recepcion en obra.
     *
     * El estado sale de si hubo diferencias o no: es un dato, no una eleccion
     * del usuario. Si quien recibe deja una nota de diferencia, el pedido queda
     * "Recibido con Diferencias" y el dueño sabe que tiene que reclamar.
     */
    public void recibir(Long idUsuarioRecibe, String fotoRemito, String notaDiferencia) {
        this.idUsuarioRecibe = idUsuarioRecibe;
        this.fotoRemito = fotoRemito;
        this.notaDiferencia = notaDiferencia;
        this.fechaRecepcion = LocalDateTime.now();
        this.estado = (notaDiferencia == null || notaDiferencia.isBlank())
                ? ESTADO_RECIBIDO_COMPLETO
                : ESTADO_RECIBIDO_CON_DIFERENCIAS;
    }

    public void anular(String motivo) {
        this.estado = ESTADO_ANULADO;
        this.motivoAnulacion = motivo;
    }

    // ------------------------------------------------------------------
    //  Consultas de estado
    // ------------------------------------------------------------------

    public boolean estaPendiente() {
        return ESTADO_PENDIENTE.equals(this.estado);
    }

    public boolean fueEnviado() {
        return ESTADO_ENVIADO.equals(this.estado);
    }

    public boolean fueRecibido() {
        return ESTADO_RECIBIDO_COMPLETO.equals(this.estado)
                || ESTADO_RECIBIDO_CON_DIFERENCIAS.equals(this.estado);
    }

    public boolean estaAnulado() {
        return ESTADO_ANULADO.equals(this.estado);
    }

    /** Un pedido cerrado ya no admite cambios de estado. */
    public boolean estaCerrado() {
        return fueRecibido() || estaAnulado();
    }

    /**
     * Total del pedido: suma de cantidad x precio unitario de cada linea.
     *
     * Es lo que se convierte en el monto del gasto al confirmar la recepcion.
     * Se calcula, no se guarda: guardarlo obligaria a recalcularlo con cada
     * cambio de linea y abriria la posibilidad de que quede desactualizado.
     */
    public BigDecimal calcularTotal() {
        return materiales.stream()
                .map(PedidoMaterial::calcularSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Si falta el precio de alguna linea, el total no representa nada. */
    public boolean tieneTodosLosPrecios() {
        return !materiales.isEmpty()
                && materiales.stream().allMatch(m -> m.getPrecioUnitario() != null);
    }

    // ------------------------------------------------------------------
    //  Metodos de acceso
    // ------------------------------------------------------------------

    public Long getIdPedido() {
        return idPedido;
    }

    public Obra getObra() {
        return obra;
    }

    public Proveedor getProveedor() {
        return proveedor;
    }

    public Long getIdUsuarioSolicita() {
        return idUsuarioSolicita;
    }

    public Long getIdUsuarioRecibe() {
        return idUsuarioRecibe;
    }

    public String getEstado() {
        return estado;
    }

    public String getFotoRemito() {
        return fotoRemito;
    }

    public String getNotaDiferencia() {
        return notaDiferencia;
    }

    public String getMotivoAnulacion() {
        return motivoAnulacion;
    }

    public LocalDateTime getFechaSolicitud() {
        return fechaSolicitud;
    }

    public LocalDateTime getFechaAprobacion() {
        return fechaAprobacion;
    }

    public LocalDateTime getFechaRecepcion() {
        return fechaRecepcion;
    }

    public List<PedidoMaterial> getMateriales() {
        return materiales;
    }

    // ------------------------------------------------------------------
    //  El link publico de la orden
    // ------------------------------------------------------------------

    /**
     * Deja el pedido con un link vigente.
     *
     * El token lo genera el servicio, que es quien tiene la fuente de azar. La
     * entidad solo lo guarda junto con su vencimiento, para que no pueda quedar
     * un token sin fecha o una fecha sin token.
     */
    public void compartirOrden(String token, LocalDateTime vence) {
        this.tokenOrden = token;
        this.tokenOrdenVence = vence;
    }

    /**
     * Corta el acceso al link al instante.
     *
     * Existe porque el caso real es inmediato: si la orden se le mando al
     * corralon equivocado, esperar a que venza no sirve de nada.
     */
    public void dejarDeCompartirOrden() {
        this.tokenOrden = null;
        this.tokenOrdenVence = null;
    }

    /** Si el link sigue sirviendo ahora mismo. */
    public boolean tieneOrdenCompartida() {
        return tokenOrden != null && tokenOrdenVence != null
                && tokenOrdenVence.isAfter(LocalDateTime.now());
    }

    public String getTokenOrden() {
        return tokenOrden;
    }

    public LocalDateTime getTokenOrdenVence() {
        return tokenOrdenVence;
    }
}
