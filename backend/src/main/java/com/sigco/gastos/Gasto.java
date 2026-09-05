package com.sigco.gastos;

import com.sigco.obras.Obra;
import com.sigco.presupuestacion.Rubro;
import com.sigco.presupuestacion.Subrubro;
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
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Un gasto de la obra.
 *
 * Reemplaza la doble carga que hoy hace la empresa: anotar el gasto en una
 * planilla del celular durante la semana y pasarlo el sabado a la planilla
 * grande de la computadora. Aca se carga una sola vez, en el momento, y queda
 * clasificado y comparado contra el presupuesto sin traspaso manual.
 */
@Entity
@Table(name = "gasto")
public class Gasto {

    public static final String TIPO_MATERIAL = "Material";
    public static final String TIPO_MANO_DE_OBRA = "Mano de Obra";
    public static final String TIPO_HORMIGA = "Gasto Hormiga";
    public static final String TIPO_OTRO = "Otro";

    public static final String ESTADO_CONFIRMADO = "Confirmado";
    public static final String ESTADO_ANULADO = "Anulado";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_gasto")
    private Long idGasto;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_obra", nullable = false)
    private Obra obra;

    /**
     * Rubro obligatorio: es la mitad de la comparacion contra el presupuesto.
     * Un gasto sin rubro no se puede comparar con nada, que es la razon de ser
     * del modulo.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_rubro", nullable = false)
    private Rubro rubro;

    /**
     * Subrubro opcional.
     *
     * NOTA: el informe se contradice. La seccion de validaciones dice que es
     * obligatorio; la tabla de campos del formulario lo lista como "Relacion
     * (opcional)" y el Diccionario no lo marca obligatorio. Se implementa
     * opcional porque el semaforo que describe el informe compara POR RUBRO,
     * asi que exigirlo agregaria friccion sin servir al calculo.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_subrubro")
    private Subrubro subrubro;

    @Column(name = "tipo_gasto", nullable = false, length = 15)
    private String tipoGasto;

    @Column(name = "monto", nullable = false, precision = 14, scale = 2)
    private BigDecimal monto;

    /**
     * Cuando ocurrio el gasto, distinto de cuando se cargo al sistema.
     *
     * El informe pide separarlos explicitamente: no siempre coinciden, y
     * tenerlos por separado permite detectar mas adelante si hay demoras
     * sistematicas en la carga.
     */
    @Column(name = "fecha_gasto", nullable = false)
    private LocalDate fechaGasto;

    @Column(name = "fecha_carga", nullable = false)
    private LocalDateTime fechaCarga;

    /** Pedido que lo origino, cuando el gasto nace de una compra recibida. */
    @Column(name = "id_pedido")
    private Long idPedido;

    /** Operario, cuando el gasto es un pago de mano de obra. */
    @Column(name = "id_operario")
    private Long idOperario;

    @Column(name = "descripcion", length = 250)
    private String descripcion;

    /** Referencia en Supabase Storage, nunca el binario. */
    @Column(name = "comprobante_adjunto", length = 255)
    private String comprobanteAdjunto;

    // TODO: vincular a usuario real al integrar el modulo Accesos.
    @Column(name = "id_usuario_registro")
    private Long idUsuarioRegistro;

    @Column(name = "estado", nullable = false, length = 12)
    private String estado;

    @Column(name = "motivo_anulacion", length = 200)
    private String motivoAnulacion;

    protected Gasto() {
    }

    public Gasto(Obra obra, Rubro rubro, Subrubro subrubro, String tipoGasto,
                 BigDecimal monto, LocalDate fechaGasto, String descripcion,
                 String comprobanteAdjunto, Long idPedido, Long idOperario) {
        this.obra = obra;
        this.rubro = rubro;
        this.subrubro = subrubro;
        this.tipoGasto = tipoGasto;
        this.monto = monto;
        this.fechaGasto = fechaGasto;
        this.fechaCarga = LocalDateTime.now();
        this.descripcion = descripcion;
        this.comprobanteAdjunto = comprobanteAdjunto;
        this.idPedido = idPedido;
        this.idOperario = idOperario;
        this.estado = ESTADO_CONFIRMADO;
    }

    /**
     * Cambia los datos del gasto.
     *
     * La obra no se puede cambiar y por eso no esta aca: mover un gasto de obra
     * alteraria el resultado economico de las dos, y para eso corresponde
     * anularlo y cargarlo bien.
     */
    public void actualizar(Rubro rubro, Subrubro subrubro, String tipoGasto,
                           BigDecimal monto, LocalDate fechaGasto,
                           String descripcion, String comprobanteAdjunto,
                           Long idOperario) {
        this.rubro = rubro;
        this.subrubro = subrubro;
        this.tipoGasto = tipoGasto;
        this.monto = monto;
        this.fechaGasto = fechaGasto;
        this.descripcion = descripcion;
        this.comprobanteAdjunto = comprobanteAdjunto;
        this.idOperario = idOperario;
    }

    /**
     * Anula el gasto dejando el motivo.
     *
     * No se elimina: el informe lo pide explicitamente para conservar la
     * trazabilidad completa de la obra. Un gasto anulado deja de sumar en la
     * comparacion contra el presupuesto, pero sigue estando.
     */
    public void anular(String motivo) {
        this.estado = ESTADO_ANULADO;
        this.motivoAnulacion = motivo;
    }

    public boolean estaConfirmado() {
        return ESTADO_CONFIRMADO.equals(this.estado);
    }

    public boolean estaAnulado() {
        return ESTADO_ANULADO.equals(this.estado);
    }

    public boolean esHormiga() {
        return TIPO_HORMIGA.equals(this.tipoGasto);
    }

    // ---------- Metodos de acceso ----------

    public Long getIdGasto() {
        return idGasto;
    }

    public Obra getObra() {
        return obra;
    }

    public Rubro getRubro() {
        return rubro;
    }

    public Subrubro getSubrubro() {
        return subrubro;
    }

    public String getTipoGasto() {
        return tipoGasto;
    }

    public BigDecimal getMonto() {
        return monto;
    }

    public LocalDate getFechaGasto() {
        return fechaGasto;
    }

    public LocalDateTime getFechaCarga() {
        return fechaCarga;
    }

    public Long getIdPedido() {
        return idPedido;
    }

    public Long getIdOperario() {
        return idOperario;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public String getComprobanteAdjunto() {
        return comprobanteAdjunto;
    }

    public Long getIdUsuarioRegistro() {
        return idUsuarioRegistro;
    }

    public String getEstado() {
        return estado;
    }

    public String getMotivoAnulacion() {
        return motivoAnulacion;
    }
}
