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

    protected OperarioObra() {
    }

    public OperarioObra(Operario operario, Obra obra) {
        this.id = new OperarioObraId(operario.getIdOperario(), obra.getIdObra());
        this.operario = operario;
        this.obra = obra;
        this.fechaAsignacion = LocalDate.now();
    }

    public void desasignar(LocalDate fecha) {
        this.fechaDesasignacion = fecha;
    }

    /** Reabre una asignacion cerrada, en lugar de crear una fila nueva. */
    public void reasignar() {
        this.fechaDesasignacion = null;
        this.fechaAsignacion = LocalDate.now();
    }

    public boolean estaVigente() {
        return fechaDesasignacion == null;
    }

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
