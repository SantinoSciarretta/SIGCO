package com.sigco.proveedores;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * Corralon o proveedor de materiales.
 *
 * Reemplaza la lista que hoy el dueño tiene en la cabeza: a quien pedirle,
 * quien llega a que zona, quien cumple y quien demora.
 */
@Entity
@Table(name = "proveedor")
public class Proveedor {

    public static final String ESTADO_ACTIVO = "Activo";
    public static final String ESTADO_INACTIVO = "Inactivo";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_proveedor")
    private Long idProveedor;

    @Column(name = "nombre_proveedor", nullable = false, length = 150)
    private String nombreProveedor;

    /**
     * Zona en la que opera.
     *
     * Es obligatoria porque es el criterio principal con el que el dueño elige
     * a quien pedirle: un corralon que no llega a la obra no sirve por mas
     * barato que sea.
     */
    @Column(name = "zona_cobertura", nullable = false, length = 100)
    private String zonaCobertura;

    @Column(name = "telefono_contacto", length = 30)
    private String telefonoContacto;

    @Column(name = "email_contacto", length = 100)
    private String emailContacto;

    /**
     * Dónde está el corralón (calle y número, localidad). Es opcional: sirve
     * para ubicarlo y para ir a retirar material, pero el criterio para elegir
     * proveedor sigue siendo la zona de cobertura.
     */
    @Column(name = "direccion", length = 200)
    private String direccion;

    @Column(name = "estado", nullable = false, length = 10)
    private String estado;

    @Column(name = "fecha_alta", nullable = false)
    private LocalDateTime fechaAlta;

    /**
     * Constructor vacío que exige la base de datos (JPA) para poder armar el
     * objeto al leerlo. No se usa desde el código.
     */
    protected Proveedor() {
    }

    /**
     * Da de alta un proveedor con su nombre, zona y datos de contacto. Todo
     * proveedor nace Activo y con la fecha de alta de hoy.
     */
    public Proveedor(String nombreProveedor, String zonaCobertura,
                     String telefonoContacto, String emailContacto, String direccion) {
        this.nombreProveedor = nombreProveedor;
        this.zonaCobertura = zonaCobertura;
        this.telefonoContacto = telefonoContacto;
        this.emailContacto = emailContacto;
        this.direccion = direccion;
        this.estado = ESTADO_ACTIVO;
        this.fechaAlta = LocalDateTime.now();
    }

    /**
     * Corrige el nombre, la zona, la dirección o los datos de contacto del
     * proveedor.
     */
    public void actualizarDatos(String nombreProveedor, String zonaCobertura,
                                String telefonoContacto, String emailContacto,
                                String direccion) {
        this.nombreProveedor = nombreProveedor;
        this.zonaCobertura = zonaCobertura;
        this.telefonoContacto = telefonoContacto;
        this.emailContacto = emailContacto;
        this.direccion = direccion;
    }

    /**
     * Vuelve a poner al proveedor disponible para elegirlo en los pedidos.
     */
    public void activar() {
        this.estado = ESTADO_ACTIVO;
    }

    /** Lo saca de la lista disponible sin afectar su historial. */
    public void desactivar() {
        this.estado = ESTADO_INACTIVO;
    }

    /**
     * Indica si el proveedor está activo.
     */
    public boolean estaActivo() {
        return ESTADO_ACTIVO.equals(this.estado);
    }

    // ---------- Metodos de acceso ----------

    /**
     * Métodos de lectura: devuelven los datos guardados del proveedor. Solo
     * leen, no modifican nada.
     */
    public Long getIdProveedor() {
        return idProveedor;
    }

    public String getNombreProveedor() {
        return nombreProveedor;
    }

    public String getZonaCobertura() {
        return zonaCobertura;
    }

    public String getTelefonoContacto() {
        return telefonoContacto;
    }

    public String getEmailContacto() {
        return emailContacto;
    }

    public String getDireccion() {
        return direccion;
    }

    public String getEstado() {
        return estado;
    }

    public LocalDateTime getFechaAlta() {
        return fechaAlta;
    }
}
