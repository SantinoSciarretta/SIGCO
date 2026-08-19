package com.sigco.proveedores;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * Observacion sobre el comportamiento de un proveedor.
 *
 * Registra lo que hoy queda solo en la memoria del dueño: que un corralon
 * demoro una semana, que mando material de menos, que entrego roto. Con la
 * fecha y el pedido que la origino, para poder ponerla en contexto mas
 * adelante.
 *
 * Igual que la cotizacion, es un registro de un hecho: no se edita ni se borra.
 */
@Entity
@Table(name = "observacion_proveedor")
public class ObservacionProveedor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_observacion")
    private Long idObservacion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_proveedor", nullable = false)
    private Proveedor proveedor;

    /**
     * Pedido que origino la observacion.
     *
     * Se guarda como identificador suelto y no como relacion: la entidad Pedido
     * todavia no existe, porque el modulo Compras va despues. Al desarrollarlo
     * se reemplaza por un @ManyToOne y se agrega la clave foranea en la base.
     * TODO: vincular con la entidad Pedido al desarrollar el modulo Compras.
     */
    @Column(name = "id_pedido")
    private Long idPedido;

    @Column(name = "descripcion", nullable = false, length = 300)
    private String descripcion;

    @Column(name = "fecha", nullable = false)
    private LocalDateTime fecha;

    protected ObservacionProveedor() {
    }

    public ObservacionProveedor(Proveedor proveedor, Long idPedido, String descripcion) {
        this.proveedor = proveedor;
        this.idPedido = idPedido;
        this.descripcion = descripcion;
        this.fecha = LocalDateTime.now();
    }

    // ---------- Metodos de acceso ----------

    public Long getIdObservacion() {
        return idObservacion;
    }

    public Proveedor getProveedor() {
        return proveedor;
    }

    public Long getIdPedido() {
        return idPedido;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public LocalDateTime getFecha() {
        return fecha;
    }
}
