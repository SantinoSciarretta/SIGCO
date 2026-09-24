package com.sigco.seguimiento;

import com.sigco.obras.Obra;
import com.sigco.presupuestacion.Rubro;
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

/**
 * Una etapa de la obra, con el peso que representa en el avance total.
 *
 * Reemplaza el cronograma que hoy se arma al inicio y no se actualiza nunca. El
 * avance deja de evaluarse de memoria durante las visitas del dueño y pasa a
 * ser un numero objetivo.
 *
 * SOLO DOS ESTADOS: Pendiente y Completado. No hay estado intermedio a
 * proposito. El informe pide que el avance se calcule "unicamente sobre los
 * hitos completados y su ponderacion, sin estimaciones intermedias, para que el
 * valor sea siempre objetivo". Un estado intermedio invitaria justamente a esa
 * estimacion, y el numero dejaria de ser objetivo.
 */
@Entity
@Table(name = "hito")
public class Hito {

    public static final String ESTADO_PENDIENTE = "Pendiente";
    public static final String ESTADO_COMPLETADO = "Completado";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_hito")
    private Long idHito;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_obra", nullable = false)
    private Obra obra;

    @Column(name = "nombre_hito", nullable = false, length = 150)
    private String nombreHito;

    /** Que porcentaje del avance total representa completar esta etapa. */
    @Column(name = "ponderacion", nullable = false, precision = 5, scale = 2)
    private BigDecimal ponderacion;

    @Column(name = "orden", nullable = false)
    private Integer orden;

    /**
     * Rubro al que pertenece la etapa. Opcional.
     *
     * Lo pidio Ricardo al cargar las etapas ("demolicion de una pared" es del
     * rubro Albañileria). Sirve para leer el avance por especialidad, no para
     * el calculo: el porcentaje sale de la duracion, no del rubro.
     *
     * Nullable porque los hitos cargados antes de V19 no lo tienen, y tampoco
     * lo tienen los que se cargan por ponderacion directa.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_rubro")
    private Rubro rubro;

    /**
     * Dias que se estima que lleva la etapa. Opcional.
     *
     * Cuando esta presente, es de donde sale la ponderacion. Se guarda aunque
     * la ponderacion ya este calculada porque es el dato que cargo el usuario:
     * si mañana se agrega o se saca una etapa, hay que poder recalcular todos
     * los porcentajes, y para eso hacen falta las duraciones originales.
     */
    @Column(name = "duracion_dias")
    private Integer duracionDias;

    @Column(name = "estado", nullable = false, length = 12)
    private String estado;

    @Column(name = "fecha_cumplimiento")
    private LocalDate fechaCumplimiento;

    @Column(name = "observacion", length = 250)
    private String observacion;

    /** Quien lo marco como completado. Lo aporta la sesion (modulo Accesos). */
    @Column(name = "id_usuario_completa")
    private Long idUsuarioCompleta;

    protected Hito() {
    }

    public Hito(Obra obra, String nombreHito, BigDecimal ponderacion, Integer orden) {
        this(obra, nombreHito, ponderacion, orden, null, null);
    }

    /** Una etapa cargada por duracion: lleva ademas su rubro y sus dias. */
    public Hito(Obra obra, String nombreHito, BigDecimal ponderacion, Integer orden,
                Rubro rubro, Integer duracionDias) {
        this.obra = obra;
        this.nombreHito = nombreHito;
        this.ponderacion = ponderacion;
        this.orden = orden;
        this.rubro = rubro;
        this.duracionDias = duracionDias;
        this.estado = ESTADO_PENDIENTE;
    }

    /**
     * Marca el hito como cumplido.
     *
     * La fecha es obligatoria y la controla el servicio: es regla del informe.
     * Sin ella el hito diria que se completo pero no cuando, y el analisis de
     * plazos, que es la mitad del valor del modulo, no se podria hacer.
     */
    public void completar(LocalDate fechaCumplimiento, String observacion,
                          Long idUsuarioCompleta) {
        this.estado = ESTADO_COMPLETADO;
        this.fechaCumplimiento = fechaCumplimiento;
        this.observacion = observacion;
        // Quien lo marco. Es lo que permite, mas adelante, preguntarle al
        // capataz por un hito que se dio por cumplido y no estaba.
        this.idUsuarioCompleta = idUsuarioCompleta;
    }

    /** Deshace el cumplimiento, si se marco por error. */
    public void reabrir() {
        this.estado = ESTADO_PENDIENTE;
        this.fechaCumplimiento = null;
        this.idUsuarioCompleta = null;
    }

    public void registrarObservacion(String observacion) {
        this.observacion = observacion;
    }

    public boolean estaCompletado() {
        return ESTADO_COMPLETADO.equals(this.estado);
    }

    public Long getIdHito() {
        return idHito;
    }

    public Obra getObra() {
        return obra;
    }

    public String getNombreHito() {
        return nombreHito;
    }

    public BigDecimal getPonderacion() {
        return ponderacion;
    }

    public Integer getOrden() {
        return orden;
    }

    public String getEstado() {
        return estado;
    }

    public LocalDate getFechaCumplimiento() {
        return fechaCumplimiento;
    }

    public String getObservacion() {
        return observacion;
    }

    public Long getIdUsuarioCompleta() {
        return idUsuarioCompleta;
    }

    public Rubro getRubro() {
        return rubro;
    }

    public Integer getDuracionDias() {
        return duracionDias;
    }
}
