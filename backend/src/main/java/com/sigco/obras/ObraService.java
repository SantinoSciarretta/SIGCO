package com.sigco.obras;

import com.sigco.clientes.Cliente;
import com.sigco.clientes.ClienteRepository;
import com.sigco.common.exception.RecursoNoEncontradoException;
import com.sigco.common.exception.ReglaDeNegocioException;
import com.sigco.obras.dto.CambioEstadoObra;
import com.sigco.obras.dto.ObraEdicion;
import com.sigco.obras.dto.ObraRespuesta;
import com.sigco.obras.dto.ObraSolicitud;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Logica de negocio del modulo Obras.
 *
 * Concentra el ciclo de vida de la obra, que es donde estan casi todas las
 * reglas de este modulo:
 *
 *      En presupuestacion  ──►  En ejecucion  ──►  Finalizada
 *              │                      │
 *              └──────────────────────┴──►  Cancelada
 *
 * Finalizada y Cancelada son estados terminales.
 */
@Service
public class ObraService {

    /**
     * Limites que se usan cuando no se indica rango de fechas. Cubren cualquier
     * fecha real del sistema: Granica no tiene obras anteriores al 2000 ni las
     * va a cargar con fecha posterior al 2999.
     *
     * Existen porque la consulta no puede recibir fechas nulas (ver el
     * comentario en ObraRepository.buscar).
     */
    private static final LocalDateTime SIN_LIMITE_INFERIOR = LocalDateTime.of(2000, 1, 1, 0, 0);
    private static final LocalDateTime SIN_LIMITE_SUPERIOR = LocalDateTime.of(2999, 12, 31, 23, 59);

    /** Ningun cliente tiene identificador cero: los BIGSERIAL arrancan en uno. */
    private static final long SIN_FILTRO_CLIENTE = 0L;

    private final ObraRepository repositorio;
    private final ClienteRepository clienteRepositorio;

    public ObraService(ObraRepository repositorio, ClienteRepository clienteRepositorio) {
        this.repositorio = repositorio;
        this.clienteRepositorio = clienteRepositorio;
    }

    /**
     * Listado con filtros combinables.
     *
     * El rango de fechas llega como dias sueltos y se convierte a momentos
     * exactos, porque la columna guarda fecha y hora: "hasta el 31 de marzo"
     * tiene que incluir todo el 31 de marzo, no cortar a las 00:00.
     *
     * La conversion tambien resuelve un problema tecnico concreto: comparar un
     * parametro nulo sin tipo contra una columna TIMESTAMP hace que PostgreSQL
     * no pueda inferir el tipo y rechace la consulta. Al declarar el parametro
     * como LocalDateTime, Hibernate lo envia con el tipo correcto aunque sea
     * nulo.
     */
    @Transactional(readOnly = true)
    public List<ObraRespuesta> listar(Long idCliente, String tipoObra, String estado,
                                      LocalDate desde, LocalDate hasta, String busqueda) {
        return repositorio.buscar(
                        idCliente != null ? idCliente : SIN_FILTRO_CLIENTE,
                        sinFiltro(tipoObra),
                        sinFiltro(estado),
                        desde != null ? desde.atStartOfDay() : SIN_LIMITE_INFERIOR,
                        hasta != null ? hasta.atTime(LocalTime.MAX) : SIN_LIMITE_SUPERIOR,
                        sinFiltro(busqueda))
                .stream()
                .map(ObraRespuesta::desde)
                .toList();
    }

    @Transactional(readOnly = true)
    public ObraRespuesta obtener(Long id) {
        return ObraRespuesta.desde(buscarOFallar(id));
    }

    /**
     * Alta de una obra.
     *
     * La obra no puede existir sin cliente: si el identificador que llega no
     * corresponde a ninguno, el alta falla. Es la primera regla del informe
     * para este modulo.
     */
    @Transactional
    public ObraRespuesta crear(ObraSolicitud solicitud) {
        Cliente cliente = clienteRepositorio.findById(solicitud.idCliente())
                .orElseThrow(() -> new RecursoNoEncontradoException("Cliente", solicitud.idCliente()));

        Obra obra = new Obra(
                cliente,
                solicitud.direccionObra().trim(),
                solicitud.tipoInmueble(),
                solicitud.tipoObra(),
                solicitud.fechaFinEstimada(),
                normalizar(solicitud.notas()));

        return ObraRespuesta.desde(repositorio.save(obra));
    }

    /**
     * Edicion de los datos maestros.
     *
     * Ni el cliente ni el tipo de obra se pueden modificar: no estan en
     * ObraEdicion, asi que no hay forma de enviarlos.
     */
    @Transactional
    public ObraRespuesta actualizar(Long id, ObraEdicion edicion) {
        Obra obra = buscarOFallar(id);

        // Regla del informe: la fecha de inicio real no puede cargarse hasta
        // que el presupuesto definitivo este aprobado. Mientras la obra sigue
        // "En presupuestacion", ese presupuesto todavia no se aprobo.
        // TODO: al desarrollar Presupuestacion, comprobar directamente contra
        //       el estado del presupuesto definitivo.
        if (edicion.fechaInicioReal() != null && obra.estaEnPresupuestacion()) {
            throw new ReglaDeNegocioException(
                    "No se puede cargar la fecha de inicio mientras la obra este en presupuestacion. "
                    + "Se habilita al aprobarse el presupuesto definitivo.");
        }

        validarOrdenDeFechas(edicion.fechaInicioReal() != null
                ? edicion.fechaInicioReal() : obra.getFechaInicioReal(),
                edicion.fechaFinEstimada());

        obra.actualizarDatos(
                edicion.direccionObra().trim(),
                edicion.tipoInmueble(),
                edicion.fechaFinEstimada(),
                normalizar(edicion.notas()));

        if (edicion.fechaInicioReal() != null) {
            obra.registrarInicioReal(edicion.fechaInicioReal());
        }

        return ObraRespuesta.desde(obra);
    }

    /**
     * Cambio de estado, con las transiciones validas del ciclo de vida.
     *
     * Las reglas se comprueban en este orden: primero que la obra admita
     * cambios, despues que la transicion pedida tenga sentido, y por ultimo lo
     * que exige cada destino en particular.
     */
    @Transactional
    public ObraRespuesta cambiarEstado(Long id, CambioEstadoObra cambio) {
        Obra obra = buscarOFallar(id);

        if (obra.estaFinalizada() || obra.estaCancelada()) {
            throw new ReglaDeNegocioException(
                    "La obra esta " + obra.getEstado().toLowerCase()
                    + " y ya no admite cambios de estado.");
        }

        switch (cambio.estado()) {
            case Obra.ESTADO_EN_EJECUCION -> pasarAEjecucion(obra, cambio);
            case Obra.ESTADO_FINALIZADA -> finalizar(obra);
            case Obra.ESTADO_CANCELADA -> cancelar(obra, cambio);
            default -> throw new ReglaDeNegocioException("Estado no reconocido: " + cambio.estado());
        }

        return ObraRespuesta.desde(obra);
    }

    // ------------------------------------------------------------------
    //  Transiciones
    // ------------------------------------------------------------------

    /**
     * A "En ejecucion" se llega unicamente desde "En presupuestacion", y ocurre
     * cuando el cliente aprueba el presupuesto definitivo.
     *
     * TODO: al desarrollar Presupuestacion, este cambio deberia dispararse solo
     *       al aprobarse el presupuesto definitivo, sin que el dueño tenga que
     *       actualizarlo a mano.
     */
    private void pasarAEjecucion(Obra obra, CambioEstadoObra cambio) {
        if (!obra.estaEnPresupuestacion()) {
            throw new ReglaDeNegocioException(
                    "Solo una obra en presupuestacion puede pasar a ejecucion.");
        }

        obra.pasarAEjecucion();

        // Recien ahora se habilita cargar la fecha de inicio: la obra arranco.
        if (cambio.fechaInicioReal() != null) {
            validarOrdenDeFechas(cambio.fechaInicioReal(), obra.getFechaFinEstimada());
            obra.registrarInicioReal(cambio.fechaInicioReal());
        }
    }

    /**
     * Una obra se finaliza cuando termina de ejecutarse, nunca antes de
     * arrancar.
     *
     * TODO: al desarrollar Seguimiento de Obras, este cambio deberia dispararse
     *       automaticamente al completarse el ultimo hito.
     */
    private void finalizar(Obra obra) {
        if (obra.estaEnPresupuestacion()) {
            throw new ReglaDeNegocioException(
                    "Una obra en presupuestacion no puede finalizarse: primero tiene que ejecutarse.");
        }
        obra.finalizar();
    }

    /**
     * Cancelar exige dejar el motivo. El informe lo pide para poder entender
     * mas adelante por que un proyecto no se concreto.
     *
     * TODO: al desarrollar Presupuestacion, impedir la cancelacion si la obra
     *       ya tiene un presupuesto definitivo aprobado, salvo autorizacion
     *       explicita del dueño: eso no es un presupuesto rechazado sino una
     *       obra interrumpida en plena ejecucion.
     */
    private void cancelar(Obra obra, CambioEstadoObra cambio) {
        String motivo = normalizar(cambio.motivoCancelacion());
        if (motivo == null) {
            throw new ReglaDeNegocioException(
                    "Para cancelar una obra hay que indicar el motivo.");
        }
        obra.cancelar(motivo);
    }

    // ------------------------------------------------------------------

    private Obra buscarOFallar(Long id) {
        return repositorio.buscarConCliente(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Obra", id));
    }

    /** La obra no puede terminar antes de empezar. */
    private void validarOrdenDeFechas(LocalDate inicio, LocalDate fin) {
        if (inicio != null && fin != null && fin.isBefore(inicio)) {
            throw new ReglaDeNegocioException(
                    "La fecha estimada de finalizacion no puede ser anterior a la de inicio.");
        }
    }

    private String normalizar(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        return texto.trim();
    }

    /**
     * Valor de un filtro para la consulta: el texto recortado, o cadena vacia
     * cuando no hay filtro. Nunca null, por lo explicado en el repositorio.
     */
    private String sinFiltro(String texto) {
        if (texto == null || texto.isBlank()) {
            return "";
        }
        return texto.trim();
    }
}
