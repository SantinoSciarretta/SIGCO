package com.sigco.presupuestacion;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;

/**
 * Rubro: la clasificacion de trabajos con la que se arman los presupuestos.
 *
 * Ejemplos reales de Granica: Albanileria, Plomeria, Electricidad.
 *
 * Es una entidad compartida. Presupuestacion agrupa por rubro, Gastos clasifica
 * cada gasto por rubro, y Materiales asigna cada material a uno. Que exista una
 * sola lista es justamente lo que permite comparar lo gastado contra lo
 * presupuestado.
 */
@Entity
@Table(name = "rubro")
public class Rubro {

    public static final String ESTADO_ACTIVO = "Activo";
    public static final String ESTADO_INACTIVO = "Inactivo";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_rubro")
    private Long idRubro;

    @Column(name = "nombre_rubro", nullable = false, length = 100)
    private String nombreRubro;

    @Column(name = "estado", nullable = false, length = 10)
    private String estado;

    /**
     * Si este es EL rubro de mano de obra. Hay uno solo en todo el catalogo.
     *
     * Cambia como se presupuesta: al abrir su planilla, en lugar de listar
     * materiales lista los OTROS rubros, para cargar de una sola vez cuanto sale
     * la mano de obra de cada especialidad.
     *
     * Es una marca y no el nombre del rubro a proposito: reconocerlo por el
     * nombre se romperia el dia que alguien lo renombre a "Mano de obra y
     * jornales", y la planilla dejaria de comportarse distinto sin que nadie
     * entienda por que.
     */
    @Column(name = "es_mano_de_obra", nullable = false)
    private boolean esManoDeObra;

    /**
     * Subrubros del rubro.
     *
     * Aca SI se declara la coleccion, a diferencia de lo que se hizo en Cliente
     * con sus obras. El motivo es que son casos distintos: la lista de
     * subrubros de un rubro es corta y acotada, y la pantalla del catalogo los
     * necesita todos para mostrarlos anidados. Las obras de un cliente, en
     * cambio, crecen sin limite y solo hacia falta contarlas.
     *
     * Es LAZY igual: la consulta del catalogo los trae con JOIN FETCH cuando
     * los necesita, y el resto de las operaciones no paga ese costo.
     *
     * Sin cascade de borrado a proposito: en este sistema nada se elimina.
     */
    @OneToMany(mappedBy = "rubro", fetch = FetchType.LAZY, cascade = CascadeType.PERSIST)
    @OrderBy("nombreSubrubro ASC")
    private List<Subrubro> subrubros = new ArrayList<>();

    protected Rubro() {
    }

    /** Un rubro nuevo nace Activo: se crea para usarlo. */
    public Rubro(String nombreRubro) {
        this.nombreRubro = nombreRubro;
        this.estado = ESTADO_ACTIVO;
    }

    /**
     * Marca o desmarca este rubro como el de mano de obra.
     *
     * Que no haya dos marcados lo garantiza un indice unico parcial en la base
     * (V18), no este metodo: una comprobacion en Java se puede saltear con dos
     * peticiones simultaneas, y la base no.
     */
    public void marcarComoManoDeObra(boolean esManoDeObra) {
        this.esManoDeObra = esManoDeObra;
    }

    public boolean esManoDeObra() {
        return esManoDeObra;
    }

    /** Lo unico editable de un rubro es su nombre. */
    public void renombrar(String nombreRubro) {
        this.nombreRubro = nombreRubro;
    }

    public void activar() {
        this.estado = ESTADO_ACTIVO;
    }

    /**
     * Saca el rubro de la lista disponible para nuevos presupuestos.
     *
     * No toca el estado de sus subrubros a proposito: si el rubro se vuelve a
     * activar, cada subrubro conserva el estado que tenia. Lo que si hace el
     * sistema es no ofrecer subrubros cuyo rubro este inactivo (ver
     * SubrubroRepository.buscarDisponibles).
     */
    public void desactivar() {
        this.estado = ESTADO_INACTIVO;
    }

    public boolean estaActivo() {
        return ESTADO_ACTIVO.equals(this.estado);
    }

    void agregarSubrubro(Subrubro subrubro) {
        this.subrubros.add(subrubro);
    }

    // ---------- Metodos de acceso ----------

    public Long getIdRubro() {
        return idRubro;
    }

    public String getNombreRubro() {
        return nombreRubro;
    }

    public String getEstado() {
        return estado;
    }

    public List<Subrubro> getSubrubros() {
        return subrubros;
    }
}
