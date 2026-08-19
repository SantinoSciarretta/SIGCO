package com.sigco.presupuestacion;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Subrubro: subdivision de un rubro.
 *
 * Ejemplos: dentro de Albanileria, "Demolicion" y "Contrapisos".
 *
 * El informe es explicito en que un subrubro pertenece a un UNICO rubro, y en
 * que al cargar un item de presupuesto no se puede elegir un subrubro que no
 * corresponda al rubro elegido. Esa segunda regla se implementa en la Parte B
 * del modulo, cuando exista item_presupuesto; el vinculo que la hace posible
 * es esta relacion.
 */
@Entity
@Table(name = "subrubro")
public class Subrubro {

    public static final String ESTADO_ACTIVO = "Activo";
    public static final String ESTADO_INACTIVO = "Inactivo";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_subrubro")
    private Long idSubrubro;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_rubro", nullable = false)
    private Rubro rubro;

    @Column(name = "nombre_subrubro", nullable = false, length = 100)
    private String nombreSubrubro;

    @Column(name = "estado", nullable = false, length = 10)
    private String estado;

    protected Subrubro() {
    }

    /**
     * Un subrubro se crea siempre dentro de un rubro: no existe suelto.
     *
     * El constructor lo agrega ademas a la coleccion del rubro, para que el
     * objeto en memoria quede consistente con lo que despues se lee de la base.
     */
    public Subrubro(Rubro rubro, String nombreSubrubro) {
        this.rubro = rubro;
        this.nombreSubrubro = nombreSubrubro;
        this.estado = ESTADO_ACTIVO;
        rubro.agregarSubrubro(this);
    }

    /** Lo unico editable es el nombre. El rubro al que pertenece no cambia. */
    public void renombrar(String nombreSubrubro) {
        this.nombreSubrubro = nombreSubrubro;
    }

    public void activar() {
        this.estado = ESTADO_ACTIVO;
    }

    public void desactivar() {
        this.estado = ESTADO_INACTIVO;
    }

    public boolean estaActivo() {
        return ESTADO_ACTIVO.equals(this.estado);
    }

    // ---------- Metodos de acceso ----------

    public Long getIdSubrubro() {
        return idSubrubro;
    }

    public Rubro getRubro() {
        return rubro;
    }

    public String getNombreSubrubro() {
        return nombreSubrubro;
    }

    public String getEstado() {
        return estado;
    }
}
