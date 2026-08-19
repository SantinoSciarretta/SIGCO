package com.sigco.clientes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * Cliente de Granica SRL.
 *
 * Refleja exactamente la tabla "cliente" del Diccionario de Datos. Como la
 * aplicacion corre con spring.jpa.hibernate.ddl-auto=validate, si esta clase
 * dejara de coincidir con la migracion V1__cliente.sql la aplicacion no
 * arrancaria e indicaria que campo difiere. Esa es la red de seguridad que
 * mantiene el codigo alineado con lo que se entrega a la catedra.
 *
 * No usa Lombok: los metodos de acceso estan escritos para que el codigo que
 * se lee sea exactamente el que se compila.
 */
@Entity
@Table(name = "cliente")
public class Cliente {

    /** Valores validos de estado. El mismo conjunto que valida la base. */
    public static final String ESTADO_ACTIVO = "Activo";
    public static final String ESTADO_INACTIVO = "Inactivo";

    /** Valores validos de origen de la recomendacion. */
    public static final String ORIGEN_CLIENTE_ANTERIOR = "Cliente anterior";
    public static final String ORIGEN_ARQUITECTO = "Arquitecto";
    public static final String ORIGEN_OTRO = "Otro";

    @Id
    // IDENTITY delega la generacion del numero a la base, que es lo que hace
    // el tipo BIGSERIAL de PostgreSQL declarado en la migracion.
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_cliente")
    private Long idCliente;

    @Column(name = "nombre_apellido", nullable = false, length = 150)
    private String nombreApellido;

    @Column(name = "telefono_contacto", length = 30)
    private String telefonoContacto;

    @Column(name = "email_contacto", length = 100)
    private String emailContacto;

    @Column(name = "origen_recomendacion", length = 20)
    private String origenRecomendacion;

    @Column(name = "recomendado_por", length = 150)
    private String recomendadoPor;

    @Column(name = "estado", nullable = false, length = 10)
    private String estado;

    @Column(name = "fecha_alta", nullable = false)
    private LocalDateTime fechaAlta;

    // Deliberadamente NO hay una relacion @OneToMany hacia obras.
    //
    // El listado de clientes muestra cuantas obras tiene cada uno, pero ese
    // numero se obtiene con una consulta agrupada desde ObraRepository, no
    // cargando la coleccion. Traer todas las obras de un cliente solo para
    // contarlas seria traer datos que nadie va a usar, y ademas obligaria a
    // cuidar el ciclo cliente -> obras -> cliente al convertir a JSON.
    //
    // El historial de obras de un cliente se pide a GET /api/obras?cliente={id}.

    /** JPA necesita un constructor sin argumentos para reconstruir la entidad. */
    protected Cliente() {
    }

    /**
     * Constructor de alta. Un cliente nace siempre Activo y con la fecha del
     * momento: ninguno de esos dos datos los elige quien carga el formulario.
     */
    public Cliente(String nombreApellido, String telefonoContacto, String emailContacto,
                   String origenRecomendacion, String recomendadoPor) {
        this.nombreApellido = nombreApellido;
        this.telefonoContacto = telefonoContacto;
        this.emailContacto = emailContacto;
        this.origenRecomendacion = origenRecomendacion;
        this.recomendadoPor = recomendadoPor;
        this.estado = ESTADO_ACTIVO;
        this.fechaAlta = LocalDateTime.now();
    }

    /**
     * Actualiza los datos de contacto.
     *
     * El estado y la fecha de alta quedan deliberadamente afuera: el estado se
     * cambia con su propia operacion (marcar como inactivo) y la fecha de alta
     * no se modifica nunca.
     */
    public void actualizarDatos(String nombreApellido, String telefonoContacto, String emailContacto,
                                String origenRecomendacion, String recomendadoPor) {
        this.nombreApellido = nombreApellido;
        this.telefonoContacto = telefonoContacto;
        this.emailContacto = emailContacto;
        this.origenRecomendacion = origenRecomendacion;
        this.recomendadoPor = recomendadoPor;
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

    public Long getIdCliente() {
        return idCliente;
    }

    public String getNombreApellido() {
        return nombreApellido;
    }

    public String getTelefonoContacto() {
        return telefonoContacto;
    }

    public String getEmailContacto() {
        return emailContacto;
    }

    public String getOrigenRecomendacion() {
        return origenRecomendacion;
    }

    public String getRecomendadoPor() {
        return recomendadoPor;
    }

    public String getEstado() {
        return estado;
    }

    public LocalDateTime getFechaAlta() {
        return fechaAlta;
    }
}
