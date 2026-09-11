package com.sigco.accesos;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Rol del sistema: Dueño, Capataz General o Capataz de Obra.
 *
 * Un rol es un conjunto de permisos con nombre. La relacion muchos a muchos con
 * permiso se mapea con @ManyToMany sobre la tabla intermedia rol_permiso, que
 * no necesita entidad propia porque no tiene atributos: solo une las dos
 * claves.
 *
 * Los tres roles vienen creados en la migracion V13 y no hay pantalla para
 * agregar uno: un rol nuevo implica decidir que puede hacer, y eso es una
 * decision de diseño del sistema. Lo que si se puede cambiar desde la pantalla
 * de Accesos es que permisos tiene cada rol.
 */
@Entity
@Table(name = "rol")
public class Rol {

    /** Nombres de los tres roles del informe. */
    public static final String DUENO = "Dueño";
    public static final String CAPATAZ_GENERAL = "Capataz General";
    public static final String CAPATAZ_DE_OBRA = "Capataz de Obra";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_rol")
    private Long idRol;

    @Column(name = "nombre_rol", nullable = false, length = 50)
    private String nombreRol;

    @Column(name = "descripcion", length = 200)
    private String descripcion;

    /**
     * LAZY porque el rol se carga en cada peticion autenticada y casi siempre
     * alcanza con su nombre. Cuando hacen falta los permisos se piden con un
     * JOIN FETCH explicito desde el repositorio.
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "rol_permiso",
            joinColumns = @JoinColumn(name = "id_rol"),
            inverseJoinColumns = @JoinColumn(name = "id_permiso"))
    private Set<Permiso> permisos = new LinkedHashSet<>();

    protected Rol() {
    }

    // ------------------------------------------------------------------
    //  Operaciones
    // ------------------------------------------------------------------

    /**
     * Reemplaza los permisos del rol por el conjunto indicado.
     *
     * Se reemplaza entero en lugar de ofrecer agregar/quitar de a uno porque la
     * pantalla de Accesos es una grilla de casillas: el dueño marca y desmarca
     * varias y guarda una vez. Aplicar cada cambio por separado dejaria el rol
     * en estados intermedios que nadie pidio.
     */
    public void definirPermisos(Set<Permiso> nuevos) {
        this.permisos.clear();
        this.permisos.addAll(nuevos);
    }

    public boolean tienePermiso(String nombrePermiso) {
        return permisos.stream()
                .anyMatch(p -> p.getNombrePermiso().equals(nombrePermiso));
    }

    public boolean esDueno() {
        return DUENO.equals(nombreRol);
    }

    // ------------------------------------------------------------------
    //  Acceso
    // ------------------------------------------------------------------

    public Long getIdRol() {
        return idRol;
    }

    public String getNombreRol() {
        return nombreRol;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public Set<Permiso> getPermisos() {
        return permisos;
    }
}
