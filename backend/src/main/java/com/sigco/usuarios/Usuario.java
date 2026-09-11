package com.sigco.usuarios;

import com.sigco.accesos.Rol;
import com.sigco.personal.Operario;
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
 * Cuenta de acceso al sistema.
 *
 * Es distinta de Operario, y la diferencia es del informe: Personal registra a
 * TODOS los operarios de la empresa, trabajen o no con el sistema; Usuario
 * registra solo a los que tienen con que entrar. La mayoria de los operarios no
 * usa el sistema y no tiene cuenta.
 *
 * El vinculo opcional con un operario existe para un caso concreto: que el
 * capataz que confirma la recepcion de materiales desde el celular quede
 * identificado como la persona de Personal, y no como una cuenta suelta.
 */
@Entity
@Table(name = "usuario")
public class Usuario {

    public static final String ESTADO_ACTIVO = "Activo";
    public static final String ESTADO_INACTIVO = "Inactivo";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_usuario")
    private Long idUsuario;

    @Column(name = "nombre_usuario", nullable = false, length = 50)
    private String nombreUsuario;

    /**
     * Hash BCrypt de la contrasena. NUNCA la contrasena.
     *
     * BCrypt es un hash unidireccional con sal incorporada: del hash no se
     * puede volver a la contrasena, y dos personas con la misma contrasena
     * tienen hashes distintos. Ni el dueño ni quien tenga acceso a la base
     * puede leer la contrasena de nadie.
     *
     * El campo es de lectura restringida a proposito: no hay getter publico que
     * lo exponga (ver verificarContra()), asi no puede filtrarse por descuido al
     * armar una respuesta.
     */
    @Column(name = "contrasena_hash", nullable = false, length = 255)
    private String contrasenaHash;

    /**
     * EAGER, a diferencia del resto del sistema: el rol se necesita en CADA
     * peticion autenticada para decidir si el usuario puede hacer lo que pide.
     * Cargarlo despues obligaria a una consulta extra por peticion.
     */
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "id_rol", nullable = false)
    private Rol rol;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_operario")
    private Operario operario;

    @Column(name = "estado", nullable = false, length = 10)
    private String estado;

    @Column(name = "motivo_baja", length = 200)
    private String motivoBaja;

    @Column(name = "ultima_fecha_acceso")
    private LocalDateTime ultimaFechaAcceso;

    @Column(name = "fecha_alta", nullable = false)
    private LocalDateTime fechaAlta;

    protected Usuario() {
    }

    /**
     * Crea una cuenta.
     *
     * Recibe el hash ya calculado y no la contrasena en claro: cifrar es
     * responsabilidad del servicio, que es quien tiene el codificador. Si la
     * entidad recibiera la contrasena, la contrasena en texto plano existiria
     * en un objeto mas del sistema sin necesidad.
     */
    public Usuario(String nombreUsuario, String contrasenaHash, Rol rol, Operario operario) {
        this.nombreUsuario = nombreUsuario;
        this.contrasenaHash = contrasenaHash;
        this.rol = rol;
        this.operario = operario;
        this.estado = ESTADO_ACTIVO;
        this.fechaAlta = LocalDateTime.now();
    }

    // ------------------------------------------------------------------
    //  Operaciones
    // ------------------------------------------------------------------

    /**
     * Compara una contrasena contra el hash guardado.
     *
     * La comparacion se hace ACA adentro y no afuera para que el hash no tenga
     * que salir de la entidad. Quien pregunta recibe un si o un no, nunca el
     * hash.
     *
     * @param verificador funcion que compara texto plano contra hash; la aporta
     *                    el servicio, que es quien conoce BCrypt
     */
    public boolean verificarContra(String contrasenaEnClaro,
                                   java.util.function.BiPredicate<String, String> verificador) {
        return verificador.test(contrasenaEnClaro, this.contrasenaHash);
    }

    public void cambiarContrasena(String nuevoHash) {
        this.contrasenaHash = nuevoHash;
    }

    public void cambiarRol(Rol nuevoRol) {
        this.rol = nuevoRol;
    }

    public void vincularOperario(Operario nuevoOperario) {
        this.operario = nuevoOperario;
    }

    public void registrarIngreso() {
        this.ultimaFechaAcceso = LocalDateTime.now();
    }

    /**
     * Da de baja la cuenta.
     *
     * No se elimina: la auditoria guarda quien hizo cada cosa, y borrar el
     * usuario dejaria registros apuntando a nadie. Ademas una cuenta eliminada
     * y vuelta a crear con el mismo nombre pareceria la misma persona sin serlo.
     */
    public void desactivar(String motivo) {
        this.estado = ESTADO_INACTIVO;
        this.motivoBaja = motivo;
    }

    public void reactivar() {
        this.estado = ESTADO_ACTIVO;
        this.motivoBaja = null;
    }

    public boolean estaActivo() {
        return ESTADO_ACTIVO.equals(estado);
    }

    // ------------------------------------------------------------------
    //  Acceso
    // ------------------------------------------------------------------

    public Long getIdUsuario() {
        return idUsuario;
    }

    public String getNombreUsuario() {
        return nombreUsuario;
    }

    public Rol getRol() {
        return rol;
    }

    public Operario getOperario() {
        return operario;
    }

    public String getEstado() {
        return estado;
    }

    public String getMotivoBaja() {
        return motivoBaja;
    }

    public LocalDateTime getUltimaFechaAcceso() {
        return ultimaFechaAcceso;
    }

    public LocalDateTime getFechaAlta() {
        return fechaAlta;
    }

    // No hay getContrasenaHash(). Es deliberado: sin getter, el hash no puede
    // terminar en un DTO ni en un log por descuido.
}
