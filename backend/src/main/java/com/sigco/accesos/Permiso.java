package com.sigco.accesos;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Un permiso: una accion concreta que un rol puede tener habilitada.
 *
 * Los permisos NO se crean desde una pantalla. Vienen definidos con el sistema
 * en la migracion V13, porque un permiso nuevo no es un dato: es codigo nuevo
 * que hay que proteger. Inventar un permiso desde un formulario crearia una
 * fila que ningun endpoint consulta.
 *
 * Lo que si se administra es a que rol se le asigna cada uno.
 */
@Entity
@Table(name = "permiso")
public class Permiso {

    /**
     * Nombres de los permisos que el codigo referencia explicitamente.
     *
     * Solo estan aca los que se nombran en una anotacion o en una validacion.
     * El resto se resuelve por convencion (<modulo>.ver / <modulo>.editar) y no
     * necesita una constante.
     */
    public static final String COMPRAS_APROBAR = "compras.aprobar";
    public static final String USUARIOS_EDITAR = "usuarios.editar";
    public static final String ACCESOS_EDITAR = "accesos.editar";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_permiso")
    private Long idPermiso;

    @Column(name = "nombre_permiso", nullable = false, length = 100)
    private String nombrePermiso;

    @Column(name = "modulo", nullable = false, length = 50)
    private String modulo;

    @Column(name = "descripcion", length = 200)
    private String descripcion;

    protected Permiso() {
    }

    public Long getIdPermiso() {
        return idPermiso;
    }

    public String getNombrePermiso() {
        return nombrePermiso;
    }

    public String getModulo() {
        return modulo;
    }

    public String getDescripcion() {
        return descripcion;
    }
}
