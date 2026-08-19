package com.sigco.proveedores;

import com.sigco.materiales.Material;
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
import java.time.LocalDateTime;

/**
 * Precio de un material informado por un proveedor.
 *
 * Una cotizacion NO se corrige ni se pisa: cada precio informado queda como un
 * registro propio con su fecha. Eso permite ver la evolucion del precio de un
 * material y distinguir una cotizacion de ayer de una de hace ocho meses. Si el
 * proveedor informa un precio nuevo, se registra otra cotizacion.
 *
 * Por eso esta entidad no tiene metodos de modificacion: se crea y no se toca.
 */
@Entity
@Table(name = "cotizacion")
public class Cotizacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_cotizacion")
    private Long idCotizacion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_proveedor", nullable = false)
    private Proveedor proveedor;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_material", nullable = false)
    private Material material;

    @Column(name = "precio_cotizado", nullable = false, precision = 12, scale = 2)
    private BigDecimal precioCotizado;

    @Column(name = "fecha_cotizacion", nullable = false)
    private LocalDateTime fechaCotizacion;

    protected Cotizacion() {
    }

    /** La fecha la pone el sistema: es el dato que da sentido a la comparacion. */
    public Cotizacion(Proveedor proveedor, Material material, BigDecimal precioCotizado) {
        this.proveedor = proveedor;
        this.material = material;
        this.precioCotizado = precioCotizado;
        this.fechaCotizacion = LocalDateTime.now();
    }

    // ---------- Metodos de acceso ----------

    public Long getIdCotizacion() {
        return idCotizacion;
    }

    public Proveedor getProveedor() {
        return proveedor;
    }

    public Material getMaterial() {
        return material;
    }

    public BigDecimal getPrecioCotizado() {
        return precioCotizado;
    }

    public LocalDateTime getFechaCotizacion() {
        return fechaCotizacion;
    }
}
