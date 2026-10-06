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

    /**
     * Constructor vacío que exige la base de datos (JPA). No se usa desde el
     * código.
     */
    protected OperarioObraId() {
    }

    /**
     * Arma el identificador de una asignación, que es la combinación del número
     * de operario y el número de obra.
     */
    public OperarioObraId(Long idOperario, Long idObra) {
        this.idOperario = idOperario;
        this.idObra = idObra;
    }

    /**
     * Métodos de lectura: devuelven las dos partes del identificador.
     */
    public Long getIdOperario() {
        return idOperario;
    }

    public Long getIdObra() {
        return idObra;
    }

    /**
     * Dos identificadores son iguales si coinciden el operario y la obra. Java
     * lo necesita para comparar asignaciones correctamente.
     */
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

    /**
     * Calcula un número que resume el identificador. Java lo usa junto con
     * equals para guardar asignaciones en colecciones.
     */
    @Override
    public int hashCode() {
        return Objects.hash(idOperario, idObra);
    }
}
