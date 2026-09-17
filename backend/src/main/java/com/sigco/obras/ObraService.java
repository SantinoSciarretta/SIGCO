package com.sigco.obras;

import com.sigco.clientes.Cliente;
import com.sigco.clientes.ClienteRepository;
import com.sigco.seguridad.AlcanceDeObras;
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

    /**
     * A que obras alcanza quien esta pidiendo.
     *
     * Un Capataz de Obra solo ve las que tiene asignadas: es el "(su obra)" de
     * la matriz del informe, que un permiso por modulo no puede expresar.
     */
    private final AlcanceDeObras alcance;

    /**
     * Para saber si la obra tiene un definitivo aprobado antes de cancelarla.
     *
     * Se inyecta el REPOSITORIO y no PresupuestoService: ese servicio ya depende
     * de ObraRepository, y pedirle el servicio entero cerraria un ciclo entre
     * los dos modulos. Con el repositorio, la dependencia va en un solo sentido.
     */
    private final com.sigco.presupuestacion.PresupuestoRepository presupuestoRepositorio;

    private final com.sigco.accesos.ServicioAuditoria auditoria;

    public ObraService(ObraRepository repositorio, ClienteRepository clienteRepositorio,
                       AlcanceDeObras alcance,
                       com.sigco.presupuestacion.PresupuestoRepository presupuestoRepositorio,
                       com.sigco.accesos.ServicioAuditoria auditoria) {
        this.repositorio = repositorio;
        this.clienteRepositorio = clienteRepositorio;
        this.alcance = alcance;
        this.presupuestoRepositorio = presupuestoRepositorio;
        this.auditoria = auditoria;
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
                // El filtro por alcance va DESPUES de la consulta y no dentro,
                // porque para el dueño y el capataz general no hay filtro: meter
                // una condicion en la consulta obligaria a pasarle una lista de
                // ids en el caso mas frecuente, que es el que no la necesita.
                .filter(o -> alcance.alcanza(o.getIdObra()))
                .map(ObraRespuesta::desde)
                .toList();
    }

    /**
     * Las obras del usuario que esta pidiendo.
     *
     * Es lo que usa la pantalla del capataz para saber en que obra esta
     * trabajando. Para el dueño y el capataz general devuelve las obras en
     * ejecucion, que es la lectura natural de "mis obras" para quien las ve
     * todas.
     */
    @Transactional(readOnly = true)
    public List<ObraRespuesta> mias() {
        return repositorio.porEstado(Obra.ESTADO_EN_EJECUCION).stream()
                .filter(o -> alcance.alcanza(o.getIdObra()))
                .map(ObraRespuesta::desde)
                .toList();
    }

    @Transactional(readOnly = true)
    public ObraRespuesta obtener(Long id) {
        alcance.exigirAlcance(id);
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
     * Ese cambio ya se dispara solo: PresupuestoService, al aprobar un
     * definitivo, pone la obra en ejecucion. Esta transicion manual se conserva
     * para los casos que no pasan por ahi —una obra cargada con el presupuesto
     * ya aprobado de antes, por ejemplo— y porque el cambio automatico no puede
     * ser la unica forma de llegar a un estado.
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
     * Tambien se dispara solo: SeguimientoService finaliza la obra al
     * completarse el ultimo hito. Esta via manual queda para las obras que no
     * llevan hitos cargados.
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
     * Y si la obra ya tiene un presupuesto definitivo aprobado, hace falta algo
     * mas que el motivo: una confirmacion explicita.
     *
     * Es la regla del informe —"una obra con presupuesto definitivo aprobado no
     * puede cancelarse salvo autorizacion explicita del dueño"— y la diferencia
     * es de fondo: cancelar una obra que todavia se estaba presupuestando es
     * descartar una propuesta que no prospero; cancelar una con el definitivo
     * aprobado es interrumpir una obra en marcha, con material comprado, gente
     * asignada y cuotas emitidas. Lo segundo no puede pasar por un clic de mas.
     */
    private void cancelar(Obra obra, CambioEstadoObra cambio) {
        String motivo = normalizar(cambio.motivoCancelacion());
        if (motivo == null) {
            throw new ReglaDeNegocioException(
                    "Para cancelar una obra hay que indicar el motivo.");
        }

        if (tieneDefinitivoAprobado(obra) && !cambio.confirmaObraEnEjecucion()) {
            throw new ReglaDeNegocioException(
                    "Esta obra tiene un presupuesto definitivo aprobado: cancelarla "
                    + "interrumpe una obra en marcha. Si estás seguro, confirmá la "
                    + "cancelación.");
        }

        obra.cancelar(motivo);

        auditoria.registrar(
                "Cancelación de la obra #" + obra.getIdObra() + ": " + motivo,
                "Obras");
    }

    /** Si la obra llego a tener un definitivo aprobado, esta o estuvo en marcha. */
    private boolean tieneDefinitivoAprobado(Obra obra) {
        return !presupuestoRepositorio.findByObraIdObraAndTipoPresupuestoAndEstado(
                obra.getIdObra(),
                com.sigco.presupuestacion.Presupuesto.TIPO_DEFINITIVO,
                com.sigco.presupuestacion.Presupuesto.ESTADO_APROBADO).isEmpty();
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
