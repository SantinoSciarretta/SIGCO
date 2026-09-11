package com.sigco.personal;

import com.sigco.common.exception.RecursoNoEncontradoException;
import com.sigco.common.exception.ReglaDeNegocioException;
import com.sigco.obras.Obra;
import com.sigco.obras.ObraRepository;
import com.sigco.seguridad.SesionActual;
import com.sigco.personal.dto.PersonalDtos.Asignacion;
import com.sigco.personal.dto.PersonalDtos.CambioEstadoOperario;
import com.sigco.personal.dto.PersonalDtos.InasistenciaRespuesta;
import com.sigco.personal.dto.PersonalDtos.InasistenciaSolicitud;
import com.sigco.personal.dto.PersonalDtos.MotivoSolicitud;
import com.sigco.personal.dto.PersonalDtos.OperarioRespuesta;
import com.sigco.personal.dto.PersonalDtos.OperarioSolicitud;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reglas del modulo Personal.
 *
 * El objetivo del relevamiento no es controlar horarios: es que el dueño pueda
 * detectar patrones de faltas reiteradas y decidir si corresponde hablar con
 * alguien. Todo el modulo esta ordenado alrededor de eso.
 */
@Service
public class PersonalService {

    private static final long TODOS = 0L;
    private static final String SIN_FILTRO = "";
    private static final LocalDate DESDE_SIEMPRE = LocalDate.of(2000, 1, 1);
    private static final LocalDate HASTA_SIEMPRE = LocalDate.of(2999, 12, 31);

    private final OperarioRepository repositorio;
    private final InasistenciaRepository inasistenciaRepositorio;
    private final ObraRepository obraRepositorio;

    /** Quien esta usando el sistema, para la columna "quien lo registro". */
    private final SesionActual sesion;

    public PersonalService(OperarioRepository repositorio,
                           InasistenciaRepository inasistenciaRepositorio,
                           ObraRepository obraRepositorio,
                           SesionActual sesion) {
        this.repositorio = repositorio;
        this.inasistenciaRepositorio = inasistenciaRepositorio;
        this.obraRepositorio = obraRepositorio;
        this.sesion = sesion;
    }

    // ------------------------------------------------------------------
    //  Operarios
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<OperarioRespuesta> listar(String busqueda, String estado, Long idObra) {
        return repositorio.buscar(
                        busqueda != null ? busqueda : SIN_FILTRO,
                        estado != null ? estado : SIN_FILTRO,
                        idObra != null ? idObra : TODOS)
                .stream()
                .map(o -> OperarioRespuesta.resumen(
                        o, inasistenciaRepositorio.countByOperarioIdOperario(o.getIdOperario())))
                .toList();
    }

    @Transactional(readOnly = true)
    public OperarioRespuesta obtener(Long id) {
        Operario operario = buscarCompletoOFallar(id);
        return OperarioRespuesta.completa(
                operario, inasistenciaRepositorio.countByOperarioIdOperario(id));
    }

    @Transactional
    public OperarioRespuesta crear(OperarioSolicitud solicitud) {
        Operario operario = new Operario(
                solicitud.nombreApellido().trim(), limpiar(solicitud.telefonoContacto()));
        return OperarioRespuesta.resumen(repositorio.save(operario), 0);
    }

    @Transactional
    public OperarioRespuesta actualizar(Long id, OperarioSolicitud solicitud) {
        Operario operario = buscarCompletoOFallar(id);
        operario.actualizarDatos(
                solicitud.nombreApellido().trim(), limpiar(solicitud.telefonoContacto()));
        return OperarioRespuesta.completa(
                operario, inasistenciaRepositorio.countByOperarioIdOperario(id));
    }

    /**
     * Activa o desactiva un operario.
     *
     * No existe eliminar. Al desactivarlo se cierran sus asignaciones vigentes
     * con la fecha de hoy, pero las filas quedan: el informe pide conservar el
     * historial de obras anteriores, y borrarlas lo destruiria.
     */
    @Transactional
    public OperarioRespuesta cambiarEstado(Long id, CambioEstadoOperario cambio) {
        Operario operario = buscarCompletoOFallar(id);

        if (Operario.ESTADO_ACTIVO.equals(cambio.estado())) {
            operario.activar();
        } else {
            operario.desactivar();
        }

        return OperarioRespuesta.completa(
                operario, inasistenciaRepositorio.countByOperarioIdOperario(id));
    }

    // ------------------------------------------------------------------
    //  Asignacion a obras
    // ------------------------------------------------------------------

    /**
     * Asigna el operario a una obra.
     *
     * Si ya estuvo asignado y la asignacion se cerro, se REABRE en lugar de
     * crear una fila nueva: la clave primaria es (operario, obra), asi que no
     * puede haber dos. Reabrir tambien es lo correcto conceptualmente: es la
     * misma relacion, que se retoma.
     */
    @Transactional
    public OperarioRespuesta asignar(Long id, Asignacion asignacion) {
        Operario operario = buscarCompletoOFallar(id);

        if (!operario.estaActivo()) {
            throw new ReglaDeNegocioException(
                    "El operario está inactivo. Activalo antes de asignarlo a una obra.");
        }

        Obra obra = obraRepositorio.findById(asignacion.idObra())
                .orElseThrow(() -> new RecursoNoEncontradoException("Obra", asignacion.idObra()));

        if (obra.estaCancelada() || obra.estaFinalizada()) {
            throw new ReglaDeNegocioException(
                    "La obra está " + obra.getEstado().toLowerCase()
                    + " y no admite asignación de personal.");
        }

        Optional<OperarioObra> previa = operario.getAsignaciones().stream()
                .filter(a -> a.getObra().getIdObra().equals(asignacion.idObra()))
                .findFirst();

        if (previa.isPresent()) {
            if (previa.get().estaVigente()) {
                throw new ReglaDeNegocioException(
                        "El operario ya está asignado a esta obra.");
            }
            previa.get().reasignar();
        } else {
            operario.asignar(new OperarioObra(operario, obra));
        }

        return OperarioRespuesta.completa(
                operario, inasistenciaRepositorio.countByOperarioIdOperario(id));
    }

    /** Cierra la asignacion sin borrarla: la obra queda en el historial. */
    @Transactional
    public OperarioRespuesta desasignar(Long id, Long idObra) {
        Operario operario = buscarCompletoOFallar(id);

        OperarioObra asignacion = operario.getAsignaciones().stream()
                .filter(a -> a.getObra().getIdObra().equals(idObra) && a.estaVigente())
                .findFirst()
                .orElseThrow(() -> new ReglaDeNegocioException(
                        "El operario no está asignado a esa obra."));

        asignacion.desasignar(LocalDate.now());

        return OperarioRespuesta.completa(
                operario, inasistenciaRepositorio.countByOperarioIdOperario(id));
    }

    // ------------------------------------------------------------------
    //  Inasistencias
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<InasistenciaRespuesta> listarInasistencias(Long idOperario, Long idObra,
                                                           LocalDate desde, LocalDate hasta) {
        return inasistenciaRepositorio.buscar(
                        idOperario != null ? idOperario : TODOS,
                        idObra != null ? idObra : TODOS,
                        desde != null ? desde : DESDE_SIEMPRE,
                        hasta != null ? hasta : HASTA_SIEMPRE)
                .stream()
                .map(InasistenciaRespuesta::desde)
                .toList();
    }

    /**
     * Registra una falta.
     *
     * Dos reglas del informe se cruzan aca:
     *
     *   - No hay inasistencia sin obra: la falta ocurre en algun lado, y sin ese
     *     dato no sirve para cruzar contra el avance de esa obra.
     *   - El operario tiene que estar o haber estado asignado a esa obra. Mira
     *     el historial completo y no solo lo vigente, porque una falta se puede
     *     cargar despues de que el operario cambio de obra.
     */
    @Transactional
    public InasistenciaRespuesta registrarInasistencia(InasistenciaSolicitud solicitud) {
        Operario operario = buscarCompletoOFallar(solicitud.idOperario());

        Obra obra = obraRepositorio.findById(solicitud.idObra())
                .orElseThrow(() -> new RecursoNoEncontradoException("Obra", solicitud.idObra()));

        if (!operario.estuvoAsignadoA(solicitud.idObra())) {
            throw new ReglaDeNegocioException(
                    operario.getNombreApellido() + " no está ni estuvo asignado a "
                    + obra.getDireccionObra() + ". Asignalo antes de registrarle una falta ahí.");
        }

        if (inasistenciaRepositorio.existsByOperarioIdOperarioAndObraIdObraAndFechaFalta(
                solicitud.idOperario(), solicitud.idObra(), solicitud.fechaFalta())) {
            throw new ReglaDeNegocioException(
                    "Ya hay una inasistencia registrada para ese operario, esa obra y esa fecha.");
        }

        Inasistencia inasistencia = new Inasistencia(
                operario, obra, solicitud.fechaFalta(), limpiar(solicitud.motivo()));

        sesion.idUsuario().ifPresent(inasistencia::registradaPor);

        return InasistenciaRespuesta.desde(inasistenciaRepositorio.save(inasistencia));
    }

    /**
     * Completa el motivo de una falta ya registrada.
     *
     * El informe lo prevee explicitamente: el motivo "queda disponible para
     * completarse despues si el operario avisa el motivo mas tarde".
     */
    @Transactional
    public InasistenciaRespuesta registrarMotivo(Long id, MotivoSolicitud solicitud) {
        Inasistencia inasistencia = inasistenciaRepositorio.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Inasistencia", id));

        inasistencia.registrarMotivo(solicitud.motivo().trim());
        return InasistenciaRespuesta.desde(inasistencia);
    }

    // ------------------------------------------------------------------

    private Operario buscarCompletoOFallar(Long id) {
        return repositorio.buscarCompleto(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Operario", id));
    }

    private String limpiar(String texto) {
        return (texto == null || texto.isBlank()) ? null : texto.trim();
    }
}
