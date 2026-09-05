package com.sigco.personal;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Un operario de la empresa.
 *
 * DISTINTO DE usuario. Personal registra a todos los operarios, trabajen o no
 * con el sistema; Usuarios gestiona solo las cuentas de acceso. La mayoria de
 * los operarios no va a tener cuenta: el vinculo existe para los que reciben
 * materiales en obra y necesitan confirmarlo desde el celular.
 */
@Entity
@Table(name = "operario")
public class Operario {

    public static final String ESTADO_ACTIVO = "Activo";
    public static final String ESTADO_INACTIVO = "Inactivo";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_operario")
    private Long idOperario;

    @Column(name = "nombre_apellido", nullable = false, length = 150)
    private String nombreApellido;

    @Column(name = "telefono_contacto", length = 30)
    private String telefonoContacto;

    @Column(name = "estado", nullable = false, length = 10)
    private String estado;

    @Column(name = "fecha_alta", nullable = false)
    private LocalDateTime fechaAlta;

    /**
     * Obras en las que trabaja o trabajo.
     *
     * Incluye las asignaciones cerradas: la ficha del operario tiene que poder
     * responder "en que obras estuvo", no solo "donde esta hoy".
     */
    @OneToMany(mappedBy = "operario", fetch = FetchType.LAZY,
               cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OperarioObra> asignaciones = new ArrayList<>();

    protected Operario() {
    }

    public Operario(String nombreApellido, String telefonoContacto) {
        this.nombreApellido = nombreApellido;
        this.telefonoContacto = telefonoContacto;
        this.estado = ESTADO_ACTIVO;
        this.fechaAlta = LocalDateTime.now();
    }

    public void actualizarDatos(String nombreApellido, String telefonoContacto) {
        this.nombreApellido = nombreApellido;
        this.telefonoContacto = telefonoContacto;
    }

    public void activar() {
        this.estado = ESTADO_ACTIVO;
    }

    /**
     * Marca al operario como inactivo y cierra sus asignaciones vigentes.
     *
     * El informe pide desvincularlo de las obras activas PERO conservar su
     * historial. Por eso se cierran las asignaciones con una fecha de baja en
     * lugar de borrarlas: la fila queda y se sabe hasta cuando estuvo.
     */
    public void desactivar() {
        this.estado = ESTADO_INACTIVO;
        LocalDate hoy = LocalDate.now();
        asignaciones.stream()
                .filter(OperarioObra::estaVigente)
                .forEach(a -> a.desasignar(hoy));
    }

    public void asignar(OperarioObra asignacion) {
        this.asignaciones.add(asignacion);
    }

    public boolean estaActivo() {
        return ESTADO_ACTIVO.equals(this.estado);
    }

    /**
     * Si el operario esta o estuvo asignado a esa obra.
     *
     * Es la regla del informe para las inasistencias: "Un operario solo puede
     * tener inasistencias registradas en obras a las que este (o haya estado)
     * asignado". Por eso mira todas las asignaciones y no solo las vigentes:
     * una falta se puede cargar despues de que el operario cambio de obra.
     */
    public boolean estuvoAsignadoA(Long idObra) {
        return asignaciones.stream()
                .anyMatch(a -> a.getObra().getIdObra().equals(idObra));
    }

    public boolean estaAsignadoHoyA(Long idObra) {
        return asignaciones.stream()
                .anyMatch(a -> a.estaVigente() && a.getObra().getIdObra().equals(idObra));
    }

    // ---------- Metodos de acceso ----------

    public Long getIdOperario() {
        return idOperario;
    }

    public String getNombreApellido() {
        return nombreApellido;
    }

    public String getTelefonoContacto() {
        return telefonoContacto;
    }

    public String getEstado() {
        return estado;
    }

    public LocalDateTime getFechaAlta() {
        return fechaAlta;
    }

    public List<OperarioObra> getAsignaciones() {
        return asignaciones;
    }
}
