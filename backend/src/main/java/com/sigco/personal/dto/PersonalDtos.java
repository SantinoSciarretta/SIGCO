package com.sigco.personal.dto;

import com.sigco.personal.Inasistencia;
import com.sigco.personal.Operario;
import com.sigco.personal.OperarioObra;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Datos que entran y salen del modulo Personal. */
public final class PersonalDtos {

    private PersonalDtos() {
    }

    // ------------------------------------------------------------------
    //  Entrada
    // ------------------------------------------------------------------

    public record OperarioSolicitud(

            @NotBlank(message = "El nombre y apellido son obligatorios")
            @Size(max = 150, message = "El nombre no puede superar los 150 caracteres")
            String nombreApellido,

            @Size(max = 30, message = "El teléfono no puede superar los 30 caracteres")
            String telefonoContacto) {
    }

    public record CambioEstadoOperario(

            @NotBlank(message = "El estado es obligatorio")
            @Pattern(regexp = "Activo|Inactivo", message = "Estado no válido")
            String estado) {
    }

    /** Asignacion de un operario a una obra. */
    public record Asignacion(

            @NotNull(message = "La obra es obligatoria")
            Long idObra) {
    }

    /**
     * Registro de una inasistencia.
     *
     * El motivo NO es obligatorio: el informe aclara que muchas faltas no
     * tienen justificacion conocida al momento de registrarlas.
     */
    public record InasistenciaSolicitud(

            @NotNull(message = "El operario es obligatorio")
            Long idOperario,

            @NotNull(message = "La obra es obligatoria")
            Long idObra,

            @NotNull(message = "La fecha de la falta es obligatoria")
            LocalDate fechaFalta,

            @Size(max = 200, message = "El motivo no puede superar los 200 caracteres")
            String motivo) {
    }

    /** Completar el motivo despues, que es el caso normal. */
    public record MotivoSolicitud(

            @NotBlank(message = "El motivo es obligatorio")
            @Size(max = 200, message = "El motivo no puede superar los 200 caracteres")
            String motivo) {
    }

    // ------------------------------------------------------------------
    //  Salida
    // ------------------------------------------------------------------

    public record OperarioRespuesta(
            Long idOperario,
            String nombreApellido,
            String telefonoContacto,
            String estado,
            LocalDateTime fechaAlta,
            Integer cantidadObrasVigentes,
            Long cantidadInasistencias,
            List<AsignacionRespuesta> asignaciones) {

        /** Version del listado: sin las asignaciones, que ahi no se detallan. */
        public static OperarioRespuesta resumen(Operario o, long inasistencias) {
            return new OperarioRespuesta(
                    o.getIdOperario(), o.getNombreApellido(), o.getTelefonoContacto(),
                    o.getEstado(), o.getFechaAlta(),
                    (int) o.getAsignaciones().stream().filter(OperarioObra::estaVigente).count(),
                    inasistencias, null);
        }

        /** Version de la ficha: con el historial completo de obras. */
        public static OperarioRespuesta completa(Operario o, long inasistencias) {
            return new OperarioRespuesta(
                    o.getIdOperario(), o.getNombreApellido(), o.getTelefonoContacto(),
                    o.getEstado(), o.getFechaAlta(),
                    (int) o.getAsignaciones().stream().filter(OperarioObra::estaVigente).count(),
                    inasistencias,
                    o.getAsignaciones().stream().map(AsignacionRespuesta::desde).toList());
        }
    }

    public record AsignacionRespuesta(
            Long idObra,
            String direccionObra,
            String estadoObra,
            LocalDate fechaAsignacion,
            LocalDate fechaDesasignacion,
            boolean vigente) {

        static AsignacionRespuesta desde(OperarioObra a) {
            return new AsignacionRespuesta(
                    a.getObra().getIdObra(), a.getObra().getDireccionObra(),
                    a.getObra().getEstado(), a.getFechaAsignacion(),
                    a.getFechaDesasignacion(), a.estaVigente());
        }
    }

    public record InasistenciaRespuesta(
            Long idInasistencia,
            Long idOperario,
            String nombreOperario,
            Long idObra,
            String direccionObra,
            LocalDate fechaFalta,
            String motivo) {

        public static InasistenciaRespuesta desde(Inasistencia i) {
            return new InasistenciaRespuesta(
                    i.getIdInasistencia(),
                    i.getOperario().getIdOperario(), i.getOperario().getNombreApellido(),
                    i.getObra().getIdObra(), i.getObra().getDireccionObra(),
                    i.getFechaFalta(), i.getMotivo());
        }
    }
}
