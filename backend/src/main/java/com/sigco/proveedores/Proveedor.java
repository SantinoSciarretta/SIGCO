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

    @Column(name = "estado", nullable = false, length = 10)
    private String estado;

    @Column(name = "fecha_alta", nullable = false)
    private LocalDateTime fechaAlta;

    protected Proveedor() {
    }

    public Proveedor(String nombreProveedor, String zonaCobertura,
                     String telefonoContacto, String emailContacto) {
        this.nombreProveedor = nombreProveedor;
        this.zonaCobertura = zonaCobertura;
        this.telefonoContacto = telefonoContacto;
        this.emailContacto = emailContacto;
        this.estado = ESTADO_ACTIVO;
        this.fechaAlta = LocalDateTime.now();
    }

    public void actualizarDatos(String nombreProveedor, String zonaCobertura,
                                String telefonoContacto, String emailContacto) {
        this.nombreProveedor = nombreProveedor;
        this.zonaCobertura = zonaCobertura;
        this.telefonoContacto = telefonoContacto;
        this.emailContacto = emailContacto;
    }

    public void activar() {
        this.estado = ESTADO_ACTIVO;
    }

    /** Lo saca de la lista disponible sin afectar su historial. */
    public void desactivar() {
        this.estado = ESTADO_INACTIVO;
    }

    public boolean estaActivo() {
        return ESTADO_ACTIVO.equals(this.estado);
    }

    // ---------- Metodos de acceso ----------

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

    public String getEstado() {
        return estado;
    }

    public LocalDateTime getFechaAlta() {
        return fechaAlta;
    }
}
