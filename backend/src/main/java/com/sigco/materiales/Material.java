package com.sigco.materiales;

import com.sigco.presupuestacion.Rubro;
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
 * Material del catalogo.
 *
 * Ejemplos reales: cemento CP40, arena gruesa, cano de PVC.
 *
 * Reemplaza los nombres escritos a mano que hoy cambian de una obra a otra.
 * Presupuestacion y Compras eligen de esta lista, no de una propia.
 */
@Entity
@Table(name = "material")
public class Material {

    public static final String ESTADO_ACTIVO = "Activo";
    public static final String ESTADO_INACTIVO = "Inactivo";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_material")
    private Long idMaterial;

    @Column(name = "nombre_material", nullable = false, length = 150)
    private String nombreMaterial;

    /**
     * Rubro al que pertenece, del catalogo de Presupuestacion.
     *
     * A diferencia del subrubro, que no se puede mover de rubro, un material SI
     * puede cambiar: el informe lo permite explicitamente en la accion "Editar
     * Material". Tiene sentido, porque clasificar un material es una decision
     * que se puede corregir, mientras que un subrubro ES parte de su rubro.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_rubro", nullable = false)
    private Rubro rubro;

    @Column(name = "unidad_medida", nullable = false, length = 20)
    private String unidadMedida;

    @Column(name = "estado", nullable = false, length = 10)
    private String estado;

    @Column(name = "fecha_alta", nullable = false)
    private LocalDateTime fechaAlta;

    protected Material() {
    }

    /** Un material nuevo nace Activo: se carga para usarlo. */
    public Material(String nombreMaterial, Rubro rubro, String unidadMedida) {
        this.nombreMaterial = nombreMaterial;
        this.rubro = rubro;
        this.unidadMedida = unidadMedida;
        this.estado = ESTADO_ACTIVO;
        this.fechaAlta = LocalDateTime.now();
    }

    /** Nombre, rubro y unidad son editables. El estado y la fecha de alta no. */
    public void actualizarDatos(String nombreMaterial, Rubro rubro, String unidadMedida) {
        this.nombreMaterial = nombreMaterial;
        this.rubro = rubro;
        this.unidadMedida = unidadMedida;
    }

    public void activar() {
        this.estado = ESTADO_ACTIVO;
    }

    /**
     * Saca el material de la lista disponible para nuevos presupuestos y
     * pedidos, sin afectar los que ya lo usan.
     */
    public void desactivar() {
        this.estado = ESTADO_INACTIVO;
    }

    public boolean estaActivo() {
        return ESTADO_ACTIVO.equals(this.estado);
    }

    // ---------- Metodos de acceso ----------

    public Long getIdMaterial() {
        return idMaterial;
    }

    public String getNombreMaterial() {
        return nombreMaterial;
    }

    public Rubro getRubro() {
        return rubro;
    }

    public String getUnidadMedida() {
        return unidadMedida;
    }

    public String getEstado() {
        return estado;
    }

    public LocalDateTime getFechaAlta() {
        return fechaAlta;
    }
}
