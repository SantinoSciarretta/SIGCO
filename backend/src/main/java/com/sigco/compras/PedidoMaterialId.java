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

    protected PedidoMaterialId() {
    }

    public PedidoMaterialId(Long idPedido, Long idMaterial) {
        this.idPedido = idPedido;
        this.idMaterial = idMaterial;
    }

    public Long getIdPedido() {
        return idPedido;
    }

    public Long getIdMaterial() {
        return idMaterial;
    }

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

    @Override
    public int hashCode() {
        return Objects.hash(idPedido, idMaterial);
    }
}
