package com.sigco.gastos;

import com.sigco.common.exception.RecursoNoEncontradoException;
import com.sigco.common.exception.ReglaDeNegocioException;
import com.sigco.gastos.dto.EstadoFinanciero;
import com.sigco.gastos.dto.EstadoFinanciero.RubroFinanciero;
import com.sigco.gastos.dto.GastoDtos.AnulacionGasto;
import com.sigco.gastos.dto.GastoDtos.GastoSolicitud;
import com.sigco.gastos.dto.GastoRespuesta;
import com.sigco.obras.Obra;
import com.sigco.obras.ObraRepository;
import com.sigco.presupuestacion.ItemPresupuesto;
import com.sigco.presupuestacion.Presupuesto;
import com.sigco.seguridad.SesionActual;
import com.sigco.presupuestacion.PresupuestoRepository;
import com.sigco.presupuestacion.Rubro;
import com.sigco.presupuestacion.RubroRepository;
import com.sigco.presupuestacion.Subrubro;
import com.sigco.presupuestacion.SubrubroRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reglas del modulo Gastos.
 *
 * Lo central del modulo no es guardar un gasto: es compararlo contra lo
 * presupuestado en el mismo rubro, en el momento. Ese cruce es posible porque
 * gasto e item_presupuesto comparten la clasificacion por rubro; si cada modulo
 * tuviera su propia lista, la comparacion no existiria.
 */
@Service
public class GastoService {

    private static final long TODOS = 0L;
    private static final String SIN_FILTRO = "";
    private static final LocalDate DESDE_SIEMPRE = LocalDate.of(2000, 1, 1);
    private static final LocalDate HASTA_SIEMPRE = LocalDate.of(2999, 12, 31);

    /** Umbrales del semaforo, tal como los define el informe. */
    private static final BigDecimal UMBRAL_AMARILLO = new BigDecimal("90");
    private static final BigDecimal UMBRAL_ROJO = new BigDecimal("100");

    public static final String SEMAFORO_VERDE = "Verde";
    public static final String SEMAFORO_AMARILLO = "Amarillo";
    public static final String SEMAFORO_ROJO = "Rojo";
    /** Gasto en un rubro que nadie presupuesto: no es un desvio, es un faltante. */
    public static final String SEMAFORO_SIN_PRESUPUESTO = "Sin presupuesto";

    private final GastoRepository repositorio;
    private final ObraRepository obraRepositorio;
    private final RubroRepository rubroRepositorio;
    private final SubrubroRepository subrubroRepositorio;
    private final PresupuestoRepository presupuestoRepositorio;

    /**
     * Quien esta usando el sistema. Es lo que permite completar la columna
     * "quien lo hizo", que hasta el modulo Accesos quedaba en null.
     */
    private final SesionActual sesion;

    private final com.sigco.accesos.ServicioAuditoria auditoria;

    public GastoService(GastoRepository repositorio,
                        ObraRepository obraRepositorio,
                        RubroRepository rubroRepositorio,
                        SubrubroRepository subrubroRepositorio,
                        PresupuestoRepository presupuestoRepositorio,
                        SesionActual sesion,
                        com.sigco.accesos.ServicioAuditoria auditoria) {
        this.auditoria = auditoria;
        this.repositorio = repositorio;
        this.obraRepositorio = obraRepositorio;
        this.rubroRepositorio = rubroRepositorio;
        this.subrubroRepositorio = subrubroRepositorio;
        this.presupuestoRepositorio = presupuestoRepositorio;
        this.sesion = sesion;
    }

    // ------------------------------------------------------------------
    //  Consulta
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<GastoRespuesta> listar(Long idObra, Long idRubro, String tipoGasto,
                                       String estado, LocalDate desde, LocalDate hasta) {
        return repositorio.buscar(
                        idObra != null ? idObra : TODOS,
                        idRubro != null ? idRubro : TODOS,
                        tipoGasto != null ? tipoGasto : SIN_FILTRO,
                        estado != null ? estado : SIN_FILTRO,
                        desde != null ? desde : DESDE_SIEMPRE,
                        hasta != null ? hasta : HASTA_SIEMPRE)
                .stream()
                .map(GastoRespuesta::desde)
                .toList();
    }

    @Transactional(readOnly = true)
    public GastoRespuesta obtener(Long id) {
        return GastoRespuesta.desde(buscarOFallar(id));
    }

    // ------------------------------------------------------------------
    //  Alta, edicion y anulacion
    // ------------------------------------------------------------------

    @Transactional
    public GastoRespuesta crear(GastoSolicitud solicitud) {
        Obra obra = obraRepositorio.findById(solicitud.idObra())
                .orElseThrow(() -> new RecursoNoEncontradoException("Obra", solicitud.idObra()));

        exigirObraEnEjecucionConPresupuesto(obra);

        Rubro rubro = buscarRubroOFallar(solicitud.idRubro());
        Subrubro subrubro = resolverSubrubro(solicitud.idSubrubro(), rubro);

        Gasto gasto = new Gasto(obra, rubro, subrubro, solicitud.tipoGasto(),
                solicitud.monto(), solicitud.fechaGasto(),
                limpiar(solicitud.descripcion()), limpiar(solicitud.comprobanteAdjunto()),
                null, solicitud.idOperario());

        // Quien lo cargo. Hasta el modulo Accesos esta columna quedaba en null.
        sesion.idUsuario().ifPresent(gasto::registradoPor);

        return GastoRespuesta.desde(repositorio.save(gasto));
    }

    @Transactional
    public GastoRespuesta actualizar(Long id, GastoSolicitud solicitud) {
        Gasto gasto = buscarOFallar(id);

        if (gasto.estaAnulado()) {
            throw new ReglaDeNegocioException(
                    "Un gasto anulado no se edita. Si el dato era otro, cargá un gasto nuevo.");
        }

        Rubro rubro = buscarRubroOFallar(solicitud.idRubro());
        Subrubro subrubro = resolverSubrubro(solicitud.idSubrubro(), rubro);

        // La obra no se cambia: mover un gasto de obra alteraria el resultado
        // economico de las dos. Por eso solicitud.idObra() se ignora aca.
        gasto.actualizar(rubro, subrubro, solicitud.tipoGasto(), solicitud.monto(),
                solicitud.fechaGasto(), limpiar(solicitud.descripcion()),
                limpiar(solicitud.comprobanteAdjunto()), solicitud.idOperario());

        return GastoRespuesta.desde(gasto);
    }

    /**
     * Anula el gasto. No existe eliminar: lo pide el informe explicitamente,
     * para conservar la trazabilidad completa de la obra.
     */
    @Transactional
    public GastoRespuesta anular(Long id, AnulacionGasto anulacion) {
        Gasto gasto = buscarOFallar(id);

        if (gasto.estaAnulado()) {
            throw new ReglaDeNegocioException("El gasto ya está anulado.");
        }

        gasto.anular(anulacion.motivo().trim());

        // Un gasto anulado deja de contar contra el presupuesto del rubro y
        // cambia la ganancia de la obra. Que quede quien lo anulo y por que es
        // lo que impide que un desvio se "arregle" sin dejar rastro.
        auditoria.registrar(
                "Anulación del gasto #" + gasto.getIdGasto()
                + " por " + gasto.getMonto()
                + " en la obra #" + gasto.getObra().getIdObra()
                + ": " + anulacion.motivo().trim(),
                "Gastos");

        return GastoRespuesta.desde(gasto);
    }

    // ------------------------------------------------------------------
    //  Integracion con Compras
    // ------------------------------------------------------------------

    /**
     * Genera los gastos de un pedido recien recibido.
     *
     * Es la funcionalidad que el informe describe como "vinculacion automatica
     * de cada compra confirmada con el modulo Gastos, generando el gasto
     * correspondiente sin necesidad de cargarlo dos veces". Elimina la doble
     * carga entre Compras y Gastos.
     *
     * UN GASTO POR RUBRO, no uno por pedido. Un pedido puede mezclar cemento
     * (Albañileria) con cable (Electricidad), y un gasto unico habria que
     * imputarlo a un solo rubro, rompiendo la comparacion contra el presupuesto
     * que es justamente lo que el modulo viene a resolver.
     *
     * Recibe datos primitivos y no la entidad Pedido a proposito: asi Gastos no
     * depende de Compras y la relacion queda en una sola direccion.
     *
     * Devuelve cuantos gastos genero. Cero significa que la obra todavia no
     * admite gastos (no esta en ejecucion o no tiene definitivo aprobado). En
     * ese caso NO se falla: que el material haya llegado a la obra es un hecho
     * fisico y no puede depender del estado de la presupuestacion.
     */
    @Transactional
    public int generarDesdeRecepcion(Obra obra, Long idPedido,
                                     Map<Long, BigDecimal> montoPorRubro,
                                     LocalDate fechaRecepcion) {
        if (repositorio.countByIdPedido(idPedido) > 0) {
            // Ya se generaron: no se duplican si la recepcion se reprocesa.
            return 0;
        }
        if (!obra.estaEnEjecucion() || !tieneDefinitivoAprobado(obra)) {
            return 0;
        }

        int generados = 0;
        for (Map.Entry<Long, BigDecimal> entrada : montoPorRubro.entrySet()) {
            if (entrada.getValue().signum() <= 0) {
                continue;
            }
            Rubro rubro = buscarRubroOFallar(entrada.getKey());
            Gasto automatico = new Gasto(obra, rubro, null, Gasto.TIPO_MATERIAL,
                    entrada.getValue(), fechaRecepcion,
                    "Generado por la recepción del pedido #" + idPedido,
                    null, idPedido, null);
            // Queda a nombre de quien confirmo la recepcion: el gasto lo genera
            // el sistema, pero lo dispara una persona.
            sesion.idUsuario().ifPresent(automatico::registradoPor);
            repositorio.save(automatico);
            generados++;
        }
        return generados;
    }

    private boolean tieneDefinitivoAprobado(Obra obra) {
        return !presupuestoRepositorio.findByObraIdObraAndTipoPresupuestoAndEstado(
                obra.getIdObra(), Presupuesto.TIPO_DEFINITIVO, Presupuesto.ESTADO_APROBADO)
                .isEmpty();
    }

    // ------------------------------------------------------------------
    //  Estado financiero: la razon de ser del modulo
    // ------------------------------------------------------------------

    /**
     * Compara lo presupuestado contra lo gastado, rubro por rubro.
     *
     * El presupuesto sale del definitivo APROBADO de la obra: es el que el
     * cliente acepto y contra el que tiene sentido medirse. El gasto sale de una
     * consulta agregada sobre los gastos confirmados.
     */
    @Transactional(readOnly = true)
    public EstadoFinanciero estadoFinanciero(Long idObra) {
        Obra obra = obraRepositorio.findById(idObra)
                .orElseThrow(() -> new RecursoNoEncontradoException("Obra", idObra));

        return armar(obra, buscarDefinitivoAprobado(obra));
    }

    /**
     * El mismo estado financiero, pero sin fallar cuando no hay presupuesto.
     *
     * `estadoFinanciero` lanza si la obra no tiene un definitivo aprobado, y
     * esta bien que lo haga: quien entra a la pantalla de gastos de esa obra
     * tiene que enterarse de que no hay contra que comparar. Pero el balance de
     * cierre se consulta de cualquier obra, incluso una que se ejecuto sin
     * definitivo aprobado, y ahi la ausencia de presupuesto no es un error: es
     * un cero, con los gastos igual de visibles.
     *
     * Se resuelve con un metodo propio y NO atrapando la excepcion del otro. El
     * motivo es el mismo que ya esta explicado en porcentajeConsumidoOCero:
     * `estadoFinanciero` es @Transactional, y al lanzar deja la transaccion
     * marcada como rollback-only. Atraparla desde afuera no deshace esa marca y
     * la transaccion del llamador explota al confirmar.
     */
    @Transactional(readOnly = true)
    public EstadoFinanciero estadoFinancieroOVacio(Long idObra) {
        Obra obra = obraRepositorio.findById(idObra)
                .orElseThrow(() -> new RecursoNoEncontradoException("Obra", idObra));

        List<Presupuesto> aprobados = presupuestoRepositorio
                .findByObraIdObraAndTipoPresupuestoAndEstado(
                        idObra, Presupuesto.TIPO_DEFINITIVO, Presupuesto.ESTADO_APROBADO);

        if (aprobados.isEmpty()) {
            return armar(obra, null);
        }
        return armar(obra, recargarConItems(aprobados.get(0)));
    }

    /**
     * Arma la comparacion. El presupuesto puede faltar: en ese caso todo lo
     * presupuestado vale cero y quedan a la vista los gastos solos.
     */
    private EstadoFinanciero armar(Obra obra, Presupuesto definitivo) {
        Long idObra = obra.getIdObra();

        Map<Long, BigDecimal> presupuestadoPorRubro = new LinkedHashMap<>();
        Map<Long, String> nombreDeRubro = new LinkedHashMap<>();
        for (ItemPresupuesto item : definitivo != null
                                    ? definitivo.getItems() : List.<ItemPresupuesto>of()) {
            Long id = item.getRubro().getIdRubro();
            nombreDeRubro.put(id, item.getRubro().getNombreRubro());
            presupuestadoPorRubro.merge(id, item.getSubtotal(), BigDecimal::add);
        }

        Map<Long, BigDecimal> gastadoPorRubro = new LinkedHashMap<>();
        for (Object[] fila : repositorio.totalPorRubro(idObra)) {
            Long id = (Long) fila[0];
            nombreDeRubro.putIfAbsent(id, (String) fila[1]);
            gastadoPorRubro.put(id, (BigDecimal) fila[2]);
        }

        // La union de los dos conjuntos: interesa tanto un rubro presupuestado
        // sin gasto (todavia no se ejecuto) como uno con gasto sin presupuesto
        // (se esta gastando en algo que nadie previo).
        List<RubroFinanciero> rubros = new ArrayList<>();
        for (Long id : new TreeSet<>(nombreDeRubro.keySet())) {
            BigDecimal presupuestado = presupuestadoPorRubro.getOrDefault(id, BigDecimal.ZERO);
            BigDecimal gastado = gastadoPorRubro.getOrDefault(id, BigDecimal.ZERO);
            BigDecimal porcentaje = calcularPorcentaje(gastado, presupuestado);

            rubros.add(new RubroFinanciero(
                    id, nombreDeRubro.get(id), presupuestado, gastado,
                    presupuestado.subtract(gastado), porcentaje,
                    calcularSemaforo(porcentaje, presupuestado)));
        }
        rubros.sort((a, b) -> a.nombreRubro().compareToIgnoreCase(b.nombreRubro()));

        BigDecimal totalPresupuestado = definitivo != null
                ? definitivo.getTotalPresupuesto() : BigDecimal.ZERO;
        BigDecimal totalGastado = repositorio.totalGastado(idObra);
        BigDecimal porcentajeGeneral = calcularPorcentaje(totalGastado, totalPresupuestado);

        return new EstadoFinanciero(
                obra.getIdObra(), obra.getDireccionObra(), obra.getEstado(),
                definitivo != null ? definitivo.getIdPresupuesto() : null,
                totalPresupuestado,
                totalGastado,
                // Ganancia estimada: lo presupuestado menos lo gastado. El
                // informe pide recalcularla con cada gasto nuevo, y sale sola
                // porque no se guarda en ningun lado.
                totalPresupuestado.subtract(totalGastado),
                porcentajeGeneral,
                repositorio.totalHormiga(idObra),
                calcularSemaforo(porcentajeGeneral, totalPresupuestado),
                rubros);
    }

    /**
     * Porcentaje del presupuesto ya consumido, o cero si no se puede calcular.
     *
     * Existe para que Seguimiento pueda cruzar el avance financiero contra el
     * fisico sin depender de una excepcion.
     *
     * NO se resuelve llamando a estadoFinanciero() y atrapando la excepcion:
     * ese metodo es @Transactional, y cuando lanza una RuntimeException Spring
     * marca la transaccion como rollback-only. Atraparla afuera no deshace esa
     * marca, y la transaccion del llamador explota al confirmar con un
     * UnexpectedRollbackException. Una excepcion no sirve como control de flujo
     * cruzando un limite transaccional.
     */
    @Transactional(readOnly = true)
    public BigDecimal porcentajeConsumidoOCero(Long idObra) {
        List<Presupuesto> aprobados = presupuestoRepositorio
                .findByObraIdObraAndTipoPresupuestoAndEstado(
                        idObra, Presupuesto.TIPO_DEFINITIVO, Presupuesto.ESTADO_APROBADO);

        if (aprobados.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return calcularPorcentaje(repositorio.totalGastado(idObra),
                                  aprobados.get(0).getTotalPresupuesto());
    }

    /**
     * Resumen financiero de una obra que NO falla si no hay presupuesto.
     *
     * El Tablero recorre todas las obras en ejecucion, y alguna puede no tener
     * definitivo aprobado todavia. Con estadoFinanciero() esa sola obra haria
     * fallar el tablero entero.
     *
     * Igual que porcentajeConsumidoOCero(), NO se implementa llamando a
     * estadoFinanciero() y atrapando la excepcion: al lanzar dentro de un
     * metodo @Transactional, Spring marca la transaccion como rollback-only y
     * atraparla afuera no deshace esa marca.
     */
    @Transactional(readOnly = true)
    public ResumenFinanciero resumenOVacio(Long idObra) {
        List<Presupuesto> aprobados = presupuestoRepositorio
                .findByObraIdObraAndTipoPresupuestoAndEstado(
                        idObra, Presupuesto.TIPO_DEFINITIVO, Presupuesto.ESTADO_APROBADO);

        BigDecimal gastado = repositorio.totalGastado(idObra);

        if (aprobados.isEmpty()) {
            // Sin presupuesto no hay ganancia estimada posible: informar el
            // gastado como perdida seria inventar un dato.
            return new ResumenFinanciero(false, BigDecimal.ZERO, gastado,
                    BigDecimal.ZERO, BigDecimal.ZERO, SEMAFORO_SIN_PRESUPUESTO);
        }

        BigDecimal presupuestado = aprobados.get(0).getTotalPresupuesto();
        BigDecimal porcentaje = calcularPorcentaje(gastado, presupuestado);

        return new ResumenFinanciero(true, presupuestado, gastado,
                presupuestado.subtract(gastado), porcentaje,
                calcularSemaforo(porcentaje, presupuestado));
    }

    /**
     * Las cifras de una obra, sin el detalle por rubro.
     *
     * Es lo que necesita el Tablero, que muestra una fila por obra. El desglose
     * por rubro vive en EstadoFinanciero y se consulta al entrar a la obra.
     */
    public record ResumenFinanciero(
            boolean tienePresupuestoAprobado,
            BigDecimal presupuestado,
            BigDecimal gastado,
            BigDecimal gananciaEstimada,
            BigDecimal porcentajeConsumido,
            String semaforo) {
    }

    /**
     * Umbrales del semaforo, textuales del informe: "verde mientras el gasto
     * acumulado no supere el noventa por ciento del monto presupuestado,
     * amarillo entre el noventa y el cien por ciento, y rojo al superar el
     * monto presupuestado".
     */
    private String calcularSemaforo(BigDecimal porcentaje, BigDecimal presupuestado) {
        if (presupuestado.signum() == 0) {
            return SEMAFORO_SIN_PRESUPUESTO;
        }
        if (porcentaje.compareTo(UMBRAL_ROJO) > 0) {
            return SEMAFORO_ROJO;
        }
        if (porcentaje.compareTo(UMBRAL_AMARILLO) >= 0) {
            return SEMAFORO_AMARILLO;
        }
        return SEMAFORO_VERDE;
    }

    private BigDecimal calcularPorcentaje(BigDecimal gastado, BigDecimal presupuestado) {
        if (presupuestado == null || presupuestado.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return gastado.multiply(new BigDecimal("100"))
                .divide(presupuestado, 2, RoundingMode.HALF_UP);
    }

    // ------------------------------------------------------------------
    //  Auxiliares
    // ------------------------------------------------------------------

    private Gasto buscarOFallar(Long id) {
        return repositorio.buscarCompleto(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Gasto", id));
    }

    /**
     * Una obra solo admite gastos si tiene definitivo aprobado y esta en
     * ejecucion.
     *
     * Es regla del informe, y tiene sentido operativo: sin presupuesto aprobado
     * no hay contra que comparar, y cargar gastos de una obra que todavia se
     * esta presupuestando significa que se empezo a gastar antes de cerrar el
     * precio con el cliente.
     */
    private void exigirObraEnEjecucionConPresupuesto(Obra obra) {
        if (!obra.estaEnEjecucion()) {
            throw new ReglaDeNegocioException(
                    "La obra está " + obra.getEstado().toLowerCase()
                    + " y solo se cargan gastos de obras en ejecución.");
        }
        buscarDefinitivoAprobado(obra);
    }

    private Presupuesto buscarDefinitivoAprobado(Obra obra) {
        List<Presupuesto> aprobados = presupuestoRepositorio
                .findByObraIdObraAndTipoPresupuestoAndEstado(
                        obra.getIdObra(), Presupuesto.TIPO_DEFINITIVO, Presupuesto.ESTADO_APROBADO);

        if (aprobados.isEmpty()) {
            throw new ReglaDeNegocioException(
                    "La obra no tiene un presupuesto definitivo aprobado, "
                    + "así que no hay contra qué comparar los gastos.");
        }
        return recargarConItems(aprobados.get(0));
    }

    /**
     * Recarga el presupuesto con sus items.
     *
     * El estado financiero los necesita para repartir el presupuestado por
     * rubro, y con open-in-view desactivado no se pueden leer despues de que la
     * consulta original cerro.
     */
    private Presupuesto recargarConItems(Presupuesto presupuesto) {
        return presupuestoRepositorio.buscarCompleto(presupuesto.getIdPresupuesto())
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "Presupuesto", presupuesto.getIdPresupuesto()));
    }

    private Rubro buscarRubroOFallar(Long idRubro) {
        return rubroRepositorio.findById(idRubro)
                .orElseThrow(() -> new RecursoNoEncontradoException("Rubro", idRubro));
    }

    /**
     * El subrubro tiene que pertenecer al rubro del gasto.
     *
     * Misma regla que en item_presupuesto y por el mismo motivo: si un gasto de
     * Albañileria pudiera llevar un subrubro de Plomeria, el agrupamiento por
     * rubro dejaria de significar algo y el semaforo compararia mal.
     */
    private Subrubro resolverSubrubro(Long idSubrubro, Rubro rubro) {
        if (idSubrubro == null) {
            return null;
        }

        Subrubro subrubro = subrubroRepositorio.findById(idSubrubro)
                .orElseThrow(() -> new RecursoNoEncontradoException("Subrubro", idSubrubro));

        if (!subrubro.getRubro().getIdRubro().equals(rubro.getIdRubro())) {
            throw new ReglaDeNegocioException(
                    "El subrubro \"" + subrubro.getNombreSubrubro() + "\" pertenece al rubro "
                    + subrubro.getRubro().getNombreRubro() + ", no a " + rubro.getNombreRubro() + ".");
        }
        return subrubro;
    }

    private String limpiar(String texto) {
        return (texto == null || texto.isBlank()) ? null : texto.trim();
    }
}
