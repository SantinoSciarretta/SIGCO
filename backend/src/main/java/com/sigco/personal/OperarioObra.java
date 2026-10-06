package com.sigco.personal;

import com.sigco.obras.Obra;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import java.time.LocalDate;

/**
 * Asignacion de un operario a una obra.
 *
 * La baja NO borra la fila: pone fecha_desasignacion. El informe pide
 * desvincular al operario inactivo de las obras activas pero conservar su
 * historial de obras anteriores, y borrando la fila ese historial se perderia.
 *
 * Una asignacion vigente es la que tiene fechaDesasignacion en null.
 */
@Entity
@Table(name = "operario_obra")
public class OperarioObra {

    @EmbeddedId
    private OperarioObraId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("idOperario")
    @JoinColumn(name = "id_operario", nullable = false)
    private Operario operario;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("idObra")
    @JoinColumn(name = "id_obra", nullable = false)
    private Obra obra;

    @Column(name = "fecha_asignacion", nullable = false)
    private LocalDate fechaAsignacion;

    @Column(name = "fecha_desasignacion")
    private LocalDate fechaDesasignacion;

    /**
     * Constructor vacío que exige la base de datos (JPA) para poder armar el
     * objeto al leerlo. No se usa desde el código.
     */
    protected OperarioObra() {
    }

    /**
     * Crea la asignación de un operario a una obra, con la fecha de hoy como
     * inicio.
     */
    public OperarioObra(Operario operario, Obra obra) {
        this.id = new OperarioObraId(operario.getIdOperario(), obra.getIdObra());
        this.operario = operario;
        this.obra = obra;
        this.fechaAsignacion = LocalDate.now();
    }

    /**
     * Cierra la asignación en la fecha indicada. La fila no se borra, para
     * conservar el historial de en qué obras trabajó.
     */
    public void desasignar(LocalDate fecha) {
        this.fechaDesasignacion = fecha;
    }

    /** Reabre una asignacion cerrada, en lugar de crear una fila nueva. */
    public void reasignar() {
        this.fechaDesasignacion = null;
        this.fechaAsignacion = LocalDate.now();
    }

    /**
     * Indica si la asignación sigue abierta, es decir que el operario todavía
     * trabaja en esa obra.
     */
    public boolean estaVigente() {
        return fechaDesasignacion == null;
    }

    /**
     * Métodos de lectura: devuelven los datos guardados de la asignación. Solo
     * leen, no modifican nada.
     */
    public OperarioObraId getId() {
        return id;
    }

    public Operario getOperario() {
        return operario;
    }

    public Obra getObra() {
        return obra;
    }

    public LocalDate getFechaAsignacion() {
        return fechaAsignacion;
    }

    public LocalDate getFechaDesasignacion() {
        return fechaDesasignacion;
    }
}
