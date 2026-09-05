package com.sigco.personal;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

/**
 * Clave primaria compuesta de operario_obra, tal como la define el Diccionario.
 *
 * Impide por construccion que el mismo operario se asigne dos veces a la misma
 * obra, sin depender de un control aparte.
 */
@Embeddable
public class OperarioObraId implements Serializable {

    private static final long serialVersionUID = 1L;

    @Column(name = "id_operario")
    private Long idOperario;

    @Column(name = "id_obra")
    private Long idObra;

    protected OperarioObraId() {
    }

    public OperarioObraId(Long idOperario, Long idObra) {
        this.idOperario = idOperario;
        this.idObra = idObra;
    }

    public Long getIdOperario() {
        return idOperario;
    }

    public Long getIdObra() {
        return idObra;
    }

    @Override
    public boolean equals(Object otro) {
        if (this == otro) {
            return true;
        }
        if (!(otro instanceof OperarioObraId id)) {
            return false;
        }
        return Objects.equals(idOperario, id.idOperario) && Objects.equals(idObra, id.idObra);
    }

    @Override
    public int hashCode() {
        return Objects.hash(idOperario, idObra);
    }
}
