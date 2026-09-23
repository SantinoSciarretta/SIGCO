package com.sigco.obras;

import com.sigco.clientes.Cliente;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Obra: un proyecto de Granica SRL.
 *
 * Es la entidad nucleo del sistema. Presupuestacion, Gastos, Cobros,
 * Seguimiento y Personal cuelgan de aca.
 *
 * Refleja exactamente la tabla "obra" del Diccionario de Datos.
 */
@Entity
@Table(name = "obra")
public class Obra {

    /** Tipos de inmueble validos. */
    public static final String INMUEBLE_CASA = "Casa";
    public static final String INMUEBLE_DEPARTAMENTO = "Departamento";
    public static final String INMUEBLE_LOCAL = "Local";

    /** Tipos de obra validos. Determinan el circuito de Presupuestacion. */
    public static final String TIPO_CONSTRUCCION = "Construcción";
    public static final String TIPO_REFORMA = "Reforma";

    /** Estados del ciclo de vida de la obra. */
    public static final String ESTADO_EN_PRESUPUESTACION = "En presupuestación";
    public static final String ESTADO_EN_EJECUCION = "En ejecución";
    public static final String ESTADO_FINALIZADA = "Finalizada";
    public static final String ESTADO_CANCELADA = "Cancelada";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_obra")
    private Long idObra;

    /**
     * Cliente al que pertenece la obra.
     *
     * FetchType.LAZY: al traer una obra no se carga automaticamente el cliente.
     * Se pide solo cuando se lo necesita. Con EAGER, listar cincuenta obras
     * dispararia cincuenta consultas extra sin que nadie se entere; es el
     * problema que la aplicacion tiene configurado para detectar temprano al
     * correr con open-in-view=false.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_cliente", nullable = false)
    private Cliente cliente;

    @Column(name = "direccion_obra", nullable = false, length = 200)
    private String direccionObra;

    @Column(name = "tipo_inmueble", nullable = false, length = 15)
    private String tipoInmueble;

    @Column(name = "tipo_obra", nullable = false, length = 15)
    private String tipoObra;

    @Column(name = "fecha_inicio_real")
    private LocalDate fechaInicioReal;

    @Column(name = "fecha_fin_estimada")
    private LocalDate fechaFinEstimada;

    @Column(name = "notas", columnDefinition = "TEXT")
    private String notas;

    @Column(name = "estado", nullable = false, length = 20)
    private String estado;

    @Column(name = "motivo_cancelacion", length = 200)
    private String motivoCancelacion;

    @Column(name = "fecha_creacion", nullable = false)
    private LocalDateTime fechaCreacion;

    protected Obra() {
    }

    /**
     * Alta de una obra.
     *
     * Toda obra nace "En presupuestacion": antes de cotizar nada, el proyecto
     * ya existe en el sistema. Ese estado y la fecha de creacion no los elige
     * quien carga el formulario.
     */
    public Obra(Cliente cliente, String direccionObra, String tipoInmueble,
                String tipoObra, LocalDate fechaFinEstimada, String notas) {
        this.cliente = cliente;
        this.direccionObra = direccionObra;
        this.tipoInmueble = tipoInmueble;
        this.tipoObra = tipoObra;
        this.fechaFinEstimada = fechaFinEstimada;
        this.notas = notas;
        this.estado = ESTADO_EN_PRESUPUESTACION;
        this.fechaCreacion = LocalDateTime.now();
    }

    /**
     * Edicion de los datos maestros.
     *
     * El cliente no esta: la obra pertenece a quien la encargo y eso no cambia.
     */
    public void actualizarDatos(String direccionObra, String tipoInmueble,
                                LocalDate fechaFinEstimada, String notas) {
        this.direccionObra = direccionObra;
        this.tipoInmueble = tipoInmueble;
        this.fechaFinEstimada = fechaFinEstimada;
        this.notas = notas;
    }

    /**
     * Corrige el tipo de obra.
     *
     * Va aparte de actualizarDatos() porque no se puede hacer siempre: el
     * informe lo bloquea apenas existe un presupuesto de anteproyecto o
     * definitivo, porque el tipo determina el circuito de Presupuestacion —en
     * construccion nueva no hay anteproyecto, en reforma si—. Quien llama tiene
     * que haber comprobado antes que la obra no tenga presupuestos; eso lo hace
     * el servicio, que es el unico que puede consultarlos.
     *
     * Existe porque hasta la auditoria del 22/09 el tipo no se podia cambiar
     * NUNCA, y una obra cargada con el tipo equivocado no tenia arreglo: habia
     * que cancelarla y volver a crearla, perdiendo su historial.
     */
    public void corregirTipoObra(String tipoObra) {
        this.tipoObra = tipoObra;
    }

    /** Registra la fecha en que arrancaron los trabajos en el lugar. */
    public void registrarInicioReal(LocalDate fechaInicioReal) {
        this.fechaInicioReal = fechaInicioReal;
    }

    public void pasarAEjecucion() {
        this.estado = ESTADO_EN_EJECUCION;
    }

    /**
     * Devuelve la obra a presupuestacion y borra la fecha de inicio real.
     *
     * No es una transicion del circuito normal: existe unicamente para deshacer
     * el efecto de aprobar un presupuesto definitivo cuando ese presupuesto se
     * elimina. Sin esto la obra quedaria "En ejecucion" sin ningun presupuesto
     * aprobado que la respalde.
     *
     * La fecha de inicio real se limpia porque el informe la condiciona a que
     * exista un definitivo aprobado. Si esa aprobacion se deshace, la fecha
     * pierde su fundamento.
     */
    public void volverAPresupuestacion() {
        this.estado = ESTADO_EN_PRESUPUESTACION;
        this.fechaInicioReal = null;
    }

    public void finalizar() {
        this.estado = ESTADO_FINALIZADA;
    }

    /** Cancela la obra. El motivo es obligatorio y lo controla el servicio. */
    public void cancelar(String motivo) {
        this.estado = ESTADO_CANCELADA;
        this.motivoCancelacion = motivo;
    }

    public boolean estaCancelada() {
        return ESTADO_CANCELADA.equals(this.estado);
    }

    public boolean estaFinalizada() {
        return ESTADO_FINALIZADA.equals(this.estado);
    }

    public boolean estaEnPresupuestacion() {
        return ESTADO_EN_PRESUPUESTACION.equals(this.estado);
    }

    public boolean estaEnEjecucion() {
        return ESTADO_EN_EJECUCION.equals(this.estado);
    }

    // ---------- Metodos de acceso ----------

    public Long getIdObra() {
        return idObra;
    }

    public Cliente getCliente() {
        return cliente;
    }

    public String getDireccionObra() {
        return direccionObra;
    }

    public String getTipoInmueble() {
        return tipoInmueble;
    }

    public String getTipoObra() {
        return tipoObra;
    }

    public LocalDate getFechaInicioReal() {
        return fechaInicioReal;
    }

    public LocalDate getFechaFinEstimada() {
        return fechaFinEstimada;
    }

    public String getNotas() {
        return notas;
    }

    public String getEstado() {
        return estado;
    }

    public String getMotivoCancelacion() {
        return motivoCancelacion;
    }

    public LocalDateTime getFechaCreacion() {
        return fechaCreacion;
    }
}
