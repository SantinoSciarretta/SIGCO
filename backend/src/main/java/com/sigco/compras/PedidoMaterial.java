package com.sigco.compras;

import com.sigco.materiales.Material;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Una linea del pedido: que material y cuanto.
 *
 * El material sale del catalogo, nunca escrito a mano. Ese es el motivo de ser
 * del modulo Materiales: que "cemento", "Cemento CP40" y "bolsa cemento" dejen
 * de ser tres cosas distintas entre pedidos.
 */
@Entity
@Table(name = "pedido_material")
public class PedidoMaterial {

    private static final int DECIMALES = 2;

    @EmbeddedId
    private PedidoMaterialId id;

    /**
     * @MapsId indica que esta relacion es la que llena idPedido dentro de la
     * clave compuesta. Sin eso habria que mantener el id a mano y podria quedar
     * apuntando a un pedido distinto del de la relacion.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("idPedido")
    @JoinColumn(name = "id_pedido", nullable = false)
    private Pedido pedido;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("idMaterial")
    @JoinColumn(name = "id_material", nullable = false)
    private Material material;

    @Column(name = "cantidad", nullable = false, precision = 12, scale = 2)
    private BigDecimal cantidad;

    /**
     * Precio unitario acordado con el proveedor.
     *
     * Nulo mientras el pedido esta pendiente: quien arma el pedido en la obra
     * sabe que necesita 20 bolsas de cemento, no a cuanto estan. Se completa al
     * aprobar, cuando el dueño ya eligio el proveedor, y el sistema lo propone
     * a partir de la ultima cotizacion de ese proveedor para ese material.
     */
    @Column(name = "precio_unitario", precision = 12, scale = 2)
    private BigDecimal precioUnitario;

    protected PedidoMaterial() {
    }

    public PedidoMaterial(Pedido pedido, Material material, BigDecimal cantidad) {
        this.id = new PedidoMaterialId(pedido.getIdPedido(), material.getIdMaterial());
        this.pedido = pedido;
        this.material = material;
        this.cantidad = cantidad;
    }

    public void cambiarCantidad(BigDecimal cantidad) {
        this.cantidad = cantidad;
    }

    public void ponerPrecio(BigDecimal precioUnitario) {
        this.precioUnitario = precioUnitario;
    }

    /**
     * cantidad x precio unitario. Cero si todavia no hay precio, para que el
     * total del pedido pueda calcularse sin explotar mientras esta pendiente.
     */
    public BigDecimal calcularSubtotal() {
        if (precioUnitario == null) {
            return BigDecimal.ZERO;
        }
        return cantidad.multiply(precioUnitario).setScale(DECIMALES, RoundingMode.HALF_UP);
    }

    public PedidoMaterialId getId() {
        return id;
    }

    public Pedido getPedido() {
        return pedido;
    }

    public Material getMaterial() {
        return material;
    }

    public BigDecimal getCantidad() {
        return cantidad;
    }

    public BigDecimal getPrecioUnitario() {
        return precioUnitario;
    }
}
