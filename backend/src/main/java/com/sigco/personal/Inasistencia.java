package com.sigco.personal;

import com.sigco.obras.Obra;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;

/**
 * Una falta de un operario en una obra.
 *
 * El objetivo del registro, segun el relevamiento, no es descontar del sueldo:
 * es que el dueño pueda detectar patrones de faltas reiteradas y decidir si
 * corresponde hablar con alguien. Por eso el motivo NO es obligatorio: muchas
 * faltas no tienen justificacion conocida en el momento de registrarlas, y el
 * campo queda disponible para completarse despues si el operario avisa.
 */
@Entity
@Table(name = "inasistencia")
public class Inasistencia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_inasistencia")
    private Long idInasistencia;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_operario", nullable = false)
    private Operario operario;

    /** Obligatoria: el informe no admite una falta sin decir en que obra. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_obra", nullable = false)
    private Obra obra;

    @Column(name = "fecha_falta", nullable = false)
    private LocalDate fechaFalta;

    @Column(name = "motivo", length = 200)
    private String motivo;

    // TODO: vincular a usuario real al integrar el modulo Accesos.
    @Column(name = "id_usuario_registro")
    private Long idUsuarioRegistro;

    protected Inasistencia() {
    }

    public Inasistencia(Operario operario, Obra obra, LocalDate fechaFalta, String motivo) {
        this.operario = operario;
        this.obra = obra;
        this.fechaFalta = fechaFalta;
        this.motivo = motivo;
    }

    /** Completar el motivo despues es el caso normal, no la excepcion. */
    public void registrarMotivo(String motivo) {
        this.motivo = motivo;
    }

    public Long getIdInasistencia() {
        return idInasistencia;
    }

    public Operario getOperario() {
        return operario;
    }

    public Obra getObra() {
        return obra;
    }

    public LocalDate getFechaFalta() {
        return fechaFalta;
    }

    public String getMotivo() {
        return motivo;
    }

    public Long getIdUsuarioRegistro() {
        return idUsuarioRegistro;
    }
}
