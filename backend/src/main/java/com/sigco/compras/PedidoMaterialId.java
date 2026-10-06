package com.sigco.compras;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

/**
 * Clave primaria compuesta de pedido_material.
 *
 * El Diccionario define la tabla intermedia con PK (id_pedido, id_material) en
 * lugar de un id propio. Eso no es un detalle de forma: impide por construccion
 * que el mismo material aparezca dos veces en un pedido, sin necesidad de un
 * control aparte que alguien pueda olvidarse de escribir.
 *
 * JPA exige que una clave compuesta sea una clase aparte, con equals y hashCode,
 * porque los usa para saber si dos filas son la misma.
 */
@Embeddable
public class PedidoMaterialId implements Serializable {

    private static final long serialVersionUID = 1L;

    @Column(name = "id_pedido")
    private Long idPedido;

    @Column(name = "id_material")
    private Long idMaterial;

    /**
     * Constructor vacío que exige la base de datos (JPA). No se usa desde el
     * código.
     */
    protected PedidoMaterialId() {
    }

    /**
     * Arma el identificador de una línea de pedido, que es la combinación del
     * número de pedido y el número de material.
     */
    public PedidoMaterialId(Long idPedido, Long idMaterial) {
        this.idPedido = idPedido;
        this.idMaterial = idMaterial;
    }

    /**
     * Métodos de lectura: devuelven las dos partes del identificador.
     */
    public Long getIdPedido() {
        return idPedido;
    }

    public Long getIdMaterial() {
        return idMaterial;
    }

    /**
     * Dos identificadores son iguales si coinciden el pedido y el material.
     * Java lo necesita para comparar líneas de pedido correctamente.
     */
    @Override
    public boolean equals(Object otro) {
        if (this == otro) {
            return true;
        }
        if (!(otro instanceof PedidoMaterialId id)) {
            return false;
        }
        return Objects.equals(idPedido, id.idPedido)
                && Objects.equals(idMaterial, id.idMaterial);
    }

    /**
     * Calcula un número que resume el identificador. Java lo usa junto con
     * equals para guardar las líneas en colecciones.
     */
    @Override
    public int hashCode() {
        return Objects.hash(idPedido, idMaterial);
    }
}
