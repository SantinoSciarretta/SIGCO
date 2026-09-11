package com.sigco.accesos;

import com.sigco.usuarios.Usuario;
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
 * Traza de una accion sensible: quien, que, sobre que modulo y cuando.
 *
 * Se auditan SOLO las acciones sensibles, no todo lo que pasa. El informe lo
 * dice y ademas tiene sentido practico: si se registrara cada consulta, la
 * tabla creceria sin limite y encontrar lo que importa seria imposible. Lo que
 * se audita es lo que cambia plata, permisos o estados irreversibles.
 *
 * Esta entidad no tiene operaciones: un registro de auditoria se escribe una
 * vez y nunca se modifica ni se borra. Si se pudiera editar, no serviria para
 * lo que existe.
 */
@Entity
@Table(name = "registro_auditoria")
public class RegistroAuditoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_auditoria")
    private Long idAuditoria;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_usuario", nullable = false)
    private Usuario usuario;

    @Column(name = "accion_realizada", nullable = false, length = 150)
    private String accionRealizada;

    @Column(name = "modulo_afectado", nullable = false, length = 50)
    private String moduloAfectado;

    @Column(name = "fecha_hora", nullable = false)
    private LocalDateTime fechaHora;

    protected RegistroAuditoria() {
    }

    public RegistroAuditoria(Usuario usuario, String accionRealizada, String moduloAfectado) {
        this.usuario = usuario;
        this.accionRealizada = accionRealizada;
        this.moduloAfectado = moduloAfectado;
        this.fechaHora = LocalDateTime.now();
    }

    public Long getIdAuditoria() {
        return idAuditoria;
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public String getAccionRealizada() {
        return accionRealizada;
    }

    public String getModuloAfectado() {
        return moduloAfectado;
    }

    public LocalDateTime getFechaHora() {
        return fechaHora;
    }
}
