package com.sigco.dashboard;

import com.sigco.cobros.CobrosService;
import com.sigco.cobros.dto.CobrosDtos.ResumenCobro;
import com.sigco.compras.Pedido;
import com.sigco.compras.PedidoRepository;
import com.sigco.dashboard.dto.TableroDtos.ObraEnTablero;
import com.sigco.dashboard.dto.TableroDtos.Pendiente;
import com.sigco.dashboard.dto.TableroDtos.Resumen;
import com.sigco.dashboard.dto.TableroDtos.Tablero;
import com.sigco.gastos.GastoService;
import com.sigco.gastos.GastoService.ResumenFinanciero;
import com.sigco.obras.Obra;
import com.sigco.obras.ObraRepository;
import com.sigco.presupuestacion.Presupuesto;
import com.sigco.presupuestacion.PresupuestoRepository;
import com.sigco.seguimiento.SeguimientoService;
import com.sigco.seguimiento.dto.SeguimientoDtos.AvanceObra;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tablero del dueño: la pantalla de entrada al sistema.
 *
 * Reune en un solo lugar lo que hoy el dueño tiene que ir a buscar obra por
 * obra, en planillas distintas: cuanto se gasto contra lo presupuestado, cuanto
 * hay por cobrar, como viene el avance y que esta esperando una decision suya.
 *
 * ------------------------------------------------------------------
 *  La decision de diseño del modulo
 * ------------------------------------------------------------------
 *
 * Este servicio NO calcula nada propio. Ni un porcentaje, ni un saldo, ni un
 * semaforo. Todo lo pide a los modulos que son dueños de cada numero:
 *
 *   - el semaforo y la ganancia    -> Gastos
 *   - el avance fisico             -> Seguimiento
 *   - el saldo y los vencimientos  -> Cobros
 *   - los pedidos por aprobar      -> Compras
 *   - los presupuestos sin respuesta -> Presupuestacion
 *
 * Podria ser mas rapido resolver todo con unas consultas SQL agregadas
 * directamente contra las tablas. No se hizo, a proposito: si el Tablero
 * recalculara el semaforo por su cuenta, el dia que cambie el umbral habria que
 * acordarse de cambiarlo en dos lugares, y hasta que alguien lo note la ficha
 * de la obra y el tablero van a mostrar colores distintos para la misma obra.
 * Ese es justamente el problema que el sistema viene a resolver.
 *
 * El costo de esa decision es real y conviene tenerlo claro para defenderlo: el
 * tablero hace unas pocas consultas por obra en ejecucion, en lugar de un
 * puñado de consultas totales. Con la cantidad de obras simultaneas que maneja
 * la empresa (menos de diez) es irrelevante. Si algun dia fueran cientos,
 * habria que agregar consultas agregadas, pero recien entonces.
 *
 * Por eso este modulo va anteultimo: no puede existir hasta que existan los
 * once modulos que producen la informacion que consolida.
 */
@Service
public class TableroService {

    /** Una cuota entra en "por cobrar esta semana" si vence dentro de estos dias. */
    private static final int DIAS_DE_LA_SEMANA = 7;

    /** Un presupuesto enviado empieza a ser urgente a partir de estos dias sin respuesta. */
    private static final int DIAS_PRESUPUESTO_SIN_RESPUESTA = 15;

    /** Un pedido esperando aprobacion frena la obra: a los dos dias ya es urgente. */
    private static final int DIAS_PEDIDO_SIN_APROBAR = 2;

    /**
     * Los tipos de pendiente, como constantes y no como texto suelto.
     *
     * Importan porque el tablero reducido del Capataz General filtra por este
     * valor: con el literal escrito en cada lugar, cambiar "Pedido" en un punto
     * y no en el filtro dejaria de mostrarle pedidos sin ningun error a la
     * vista.
     */
    private static final String TIPO_PRESUPUESTO = "Presupuesto";
    private static final String TIPO_PEDIDO = "Pedido";
    private static final String TIPO_COBRO = "Cobro";

    private static final String URGENCIA_ALTA = "alta";
    private static final String URGENCIA_MEDIA = "media";

    private final ObraRepository obraRepositorio;
    private final PresupuestoRepository presupuestoRepositorio;
    private final PedidoRepository pedidoRepositorio;
    private final GastoService gastoService;
    private final SeguimientoService seguimientoService;
    private final CobrosService cobrosService;

    /**
     * Para saber qué puede ver quien pide el tablero.
     *
     * Es el único módulo que lo necesita: en el resto, el permiso decide si se
     * entra o no, y acá decide qué contiene la respuesta.
     */
    private final com.sigco.seguridad.SesionActual sesion;

    public TableroService(ObraRepository obraRepositorio,
                          PresupuestoRepository presupuestoRepositorio,
                          PedidoRepository pedidoRepositorio,
                          GastoService gastoService,
                          SeguimientoService seguimientoService,
                          CobrosService cobrosService,
                          com.sigco.seguridad.SesionActual sesion) {
        this.sesion = sesion;
        this.obraRepositorio = obraRepositorio;
        this.presupuestoRepositorio = presupuestoRepositorio;
        this.pedidoRepositorio = pedidoRepositorio;
        this.gastoService = gastoService;
        this.seguimientoService = seguimientoService;
        this.cobrosService = cobrosService;
    }

    // ------------------------------------------------------------------
    //  El tablero completo
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Tablero armar() {
        List<Obra> enEjecucion = obraRepositorio.porEstado(Obra.ESTADO_EN_EJECUCION);

        // El consolidado de cobros llega de una sola vez y se indexa por obra,
        // en lugar de consultar el plan de cada obra por separado.
        Map<Long, ResumenCobro> cobrosPorObra = new HashMap<>();
        for (ResumenCobro r : cobrosService.consolidado()) {
            cobrosPorObra.put(r.idObra(), r);
        }

        List<ObraEnTablero> obras = new ArrayList<>();
        for (Obra obra : enEjecucion) {
            obras.add(armarFila(obra, cobrosPorObra.get(obra.getIdObra())));
        }

        // Primero las obras que necesitan atencion: excedidas, despues las que
        // se desfasan, y dentro de cada grupo la de mayor consumo. Una lista
        // ordenada por fecha obligaria a leerla entera para encontrar el
        // problema, que es lo que pasa hoy con las planillas.
        obras.sort(Comparator
                .comparing((ObraEnTablero o) -> !GastoService.SEMAFORO_ROJO.equals(o.semaforo()))
                .thenComparing(o -> !o.alertaDesfasaje())
                .thenComparing(ObraEnTablero::avanceFinanciero, Comparator.reverseOrder()));

        List<Pendiente> pendientes = armarPendientes(enEjecucion, cobrosPorObra);

        Tablero completo = new Tablero(
                LocalDateTime.now(),
                armarResumen(obras, pendientes),
                obras,
                pendientes);

        return reducirSiCorresponde(completo);
    }

    // ------------------------------------------------------------------
    //  La version reducida del Capataz General
    // ------------------------------------------------------------------

    /**
     * Quita del tablero lo que el rol no tiene derecho a ver.
     *
     * ------------------------------------------------------------------
     *  Por que se arma completo y despues se recorta
     * ------------------------------------------------------------------
     *
     * Parece mas prolijo no calcular lo que no se va a mostrar, y ademas seria
     * mas rapido. No se hace asi por una razon concreta: si el recorte
     * estuviera repartido en cada paso del armado, alcanzaria con agregar un
     * campo nuevo y olvidarse de una rama para que una cifra financiera se le
     * escape al capataz sin que nadie lo note. Con un unico punto de recorte,
     * lo que se oculta se lee de un vistazo y se prueba en un solo lugar.
     *
     * El costo es calcular de mas para un rol que entra poco al tablero, con
     * menos de diez obras activas. Es despreciable frente a filtrar mal.
     *
     * ------------------------------------------------------------------
     *  Que se oculta y por que
     * ------------------------------------------------------------------
     *
     * La matriz del informe le da al Capataz General acceso de CONSULTA al
     * tablero, pero ningun acceso a Presupuestacion ni a Cobros. Entonces:
     *
     *   - lo presupuestado y la ganancia estimada salen (Presupuestacion)
     *   - el saldo, las cuotas vencidas y los vencimientos salen (Cobros)
     *   - lo gastado y el semaforo QUEDAN: Gastos es Consulta para el rol, y el
     *     semaforo es lo que le sirve para saber donde parar la mano
     *
     * Los campos ocultos viajan en null y no en cero, a proposito: cero es una
     * afirmacion —"no hay nada por cobrar"— y seria mentira. null dice "esto no
     * es asunto tuyo", y el frontend lo muestra como un guion en lugar de un
     * importe.
     */
    private Tablero reducirSiCorresponde(Tablero tablero) {
        boolean veFinanzas = sesion.puede("cobros.ver") || sesion.puede("presupuestos.ver");
        if (veFinanzas) {
            return tablero;
        }

        List<ObraEnTablero> obras = tablero.obras().stream()
                .map(this::sinDatosFinancieros)
                .toList();

        // De los pendientes quedan solo los pedidos. Los presupuestos esperando
        // respuesta son de Presupuestacion y las cuotas vencidas de Cobros: dos
        // modulos a los que este rol no tiene acceso. Ademas son cosas que solo
        // el dueño puede destrabar, asi que al capataz no le sirven de nada.
        List<Pendiente> pendientes = tablero.pendientes().stream()
                .filter(p -> TIPO_PEDIDO.equals(p.tipo()))
                .toList();

        return new Tablero(
                tablero.momento(),
                sinDatosFinancieros(tablero.resumen(), obras, pendientes),
                obras,
                pendientes);
    }

    private ObraEnTablero sinDatosFinancieros(ObraEnTablero o) {
        return new ObraEnTablero(
                o.idObra(), o.direccionObra(), o.nombreCliente(), o.estado(),
                null,                    // totalPresupuestado
                o.totalGastado(),
                null,                    // gananciaEstimada
                o.semaforo(),
                o.avanceFisico(), o.avanceFinanciero(), o.desfasaje(),
                o.alertaDesfasaje(), o.atrasada(), o.diasParaElPlazo(),
                o.hitosCompletados(), o.hitosTotales(),
                null,                    // saldoPendiente
                0,                       // cuotasVencidas
                null);                   // proximoVencimiento
    }

    private Resumen sinDatosFinancieros(Resumen r,
                                        List<ObraEnTablero> obras,
                                        List<Pendiente> pendientes) {
        return new Resumen(
                r.obrasEnEjecucion(),
                r.obrasEnPresupuestacion(),
                null,                    // totalPresupuestado
                r.totalGastado(),
                null,                    // gananciaEstimada
                null,                    // saldoPorCobrar
                null,                    // porCobrarEstaSemana
                r.obrasExcedidas(),
                r.obrasConDesfasaje(),
                // Se recuenta sobre los pendientes que quedaron: si no, el
                // numero incluiria presupuestos que la lista ya no muestra y el
                // capataz veria "3 urgentes" con dos tarjetas en pantalla.
                (int) pendientes.stream()
                        .filter(p -> URGENCIA_ALTA.equals(p.urgencia())).count());
    }

    // ------------------------------------------------------------------
    //  Una obra
    // ------------------------------------------------------------------

    private ObraEnTablero armarFila(Obra obra, ResumenCobro cobro) {
        // resumenOVacio y no estadoFinanciero: una obra sin definitivo aprobado
        // no puede hacer fallar el tablero entero.
        ResumenFinanciero finanzas = gastoService.resumenOVacio(obra.getIdObra());
        AvanceObra avance = seguimientoService.avance(obra.getIdObra());

        return new ObraEnTablero(
                obra.getIdObra(),
                obra.getDireccionObra(),
                obra.getCliente().getNombreApellido(),
                obra.getEstado(),
                finanzas.presupuestado(),
                finanzas.gastado(),
                finanzas.gananciaEstimada(),
                finanzas.semaforo(),
                avance.avanceFisico(),
                avance.avanceFinanciero(),
                avance.desfasaje(),
                avance.alertaDesfasaje(),
                avance.atrasada(),
                avance.diasParaElPlazo(),
                avance.hitosCompletados(),
                avance.hitosTotales(),
                cobro != null ? cobro.saldoPendiente() : BigDecimal.ZERO,
                cobro != null ? cobro.cuotasVencidas() : 0,
                cobro != null ? cobro.proximoVencimiento() : null);
    }

    // ------------------------------------------------------------------
    //  Resumen
    // ------------------------------------------------------------------

    private Resumen armarResumen(List<ObraEnTablero> obras, List<Pendiente> pendientes) {
        BigDecimal presupuestado = sumar(obras, ObraEnTablero::totalPresupuestado);
        BigDecimal gastado = sumar(obras, ObraEnTablero::totalGastado);
        BigDecimal saldo = sumar(obras, ObraEnTablero::saldoPendiente);

        LocalDate limite = LocalDate.now().plusDays(DIAS_DE_LA_SEMANA);
        BigDecimal estaSemana = obras.stream()
                .filter(o -> o.proximoVencimiento() != null
                        && !o.proximoVencimiento().isAfter(limite))
                .map(ObraEnTablero::saldoPendiente)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new Resumen(
                obras.size(),
                (int) obraRepositorio.countByEstado(Obra.ESTADO_EN_PRESUPUESTACION),
                presupuestado,
                gastado,
                // La ganancia se suma de las obras, no se recalcula: asi una
                // obra sin presupuesto aprobado aporta cero y no distorsiona.
                sumar(obras, ObraEnTablero::gananciaEstimada),
                saldo,
                estaSemana,
                (int) obras.stream()
                        .filter(o -> GastoService.SEMAFORO_ROJO.equals(o.semaforo())).count(),
                (int) obras.stream().filter(ObraEnTablero::alertaDesfasaje).count(),
                (int) pendientes.stream()
                        .filter(p -> URGENCIA_ALTA.equals(p.urgencia())).count());
    }

    private BigDecimal sumar(List<ObraEnTablero> obras,
                             java.util.function.Function<ObraEnTablero, BigDecimal> campo) {
        return obras.stream().map(campo).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // ------------------------------------------------------------------
    //  Pendientes
    // ------------------------------------------------------------------

    /**
     * Lo que espera una decision del dueño.
     *
     * Los tres tipos que entran son cosas que solo el puede destrabar: aprobar
     * un pedido, seguir un presupuesto enviado y reclamar una cuota vencida. Un
     * hito sin completar no entra en la lista aunque este atrasado, porque no es
     * el dueño quien lo resuelve, y una lista donde aparece lo que uno no puede
     * hacer se deja de mirar.
     */
    private List<Pendiente> armarPendientes(List<Obra> enEjecucion,
                                            Map<Long, ResumenCobro> cobrosPorObra) {
        List<Pendiente> pendientes = new ArrayList<>();
        LocalDate hoy = LocalDate.now();

        // ---- Obras en ejecución sin plan de cobro ----
        //
        // Una obra llega a "En ejecución" porque se aprobó su presupuesto
        // definitivo, y ese presupuesto ya trae el anticipo y la cantidad de
        // cuotas acordados. Falta un solo dato para armar el plan: la fecha del
        // primer vencimiento, que se pacta con el cliente y no está en ningún
        // presupuesto.
        //
        // Por eso el plan NO se genera solo al aprobar, aunque figure como
        // pendiente del módulo Cobros: el sistema tendría que inventar una
        // fecha de vencimiento, es decir inventar un compromiso de pago que el
        // cliente nunca aceptó. Preferible que el tablero lo reclame: la obra
        // arrancó y todavía no hay de dónde cobrarla, que es exactamente el
        // tipo de cosa que hoy se le traspapela al dueño.
        for (Obra obra : enEjecucion) {
            if (cobrosPorObra.containsKey(obra.getIdObra())) {
                continue;
            }
            long dias = obra.getFechaInicioReal() != null
                    ? ChronoUnit.DAYS.between(obra.getFechaInicioReal(), hoy)
                    : 0L;

            pendientes.add(new Pendiente(
                    TIPO_COBRO,
                    "Sin plan de cobro: " + obra.getDireccionObra(),
                    "La obra está en ejecución y todavía no se generó el plan de pago",
                    URGENCIA_ALTA,
                    Math.max(0, dias),
                    obra.getIdObra(),
                    "/cobranzas"));
        }

        // ---- Presupuestos enviados sin respuesta ----
        for (Presupuesto p : presupuestoRepositorio.buscar(0L, "", Presupuesto.ESTADO_ENVIADO)) {
            long dias = ChronoUnit.DAYS.between(p.getFechaCreacion().toLocalDate(), hoy);
            pendientes.add(new Pendiente(
                    TIPO_PRESUPUESTO,
                    p.getTipoPresupuesto() + " de " + p.getObra().getDireccionObra(),
                    "Enviado hace " + dias + " " + (dias == 1 ? "día" : "días")
                            + " · " + formatear(p.getTotalPresupuesto()),
                    dias >= DIAS_PRESUPUESTO_SIN_RESPUESTA ? URGENCIA_ALTA : URGENCIA_MEDIA,
                    dias,
                    p.getObra().getIdObra(),
                    "/presupuestos/" + p.getIdPresupuesto()));
        }

        // ---- Pedidos esperando aprobación ----
        // La aprobacion es indelegable, asi que un pedido pendiente frena la
        // compra hasta que el dueño entre al sistema.
        for (Pedido pedido : pedidoRepositorio
                .findByEstadoOrderByFechaSolicitudAsc(Pedido.ESTADO_PENDIENTE)) {
            long dias = ChronoUnit.DAYS.between(pedido.getFechaSolicitud().toLocalDate(), hoy);
            pendientes.add(new Pendiente(
                    TIPO_PEDIDO,
                    "Pedido para " + pedido.getObra().getDireccionObra(),
                    "Esperando aprobación hace " + dias + " " + (dias == 1 ? "día" : "días"),
                    dias >= DIAS_PEDIDO_SIN_APROBAR ? URGENCIA_ALTA : URGENCIA_MEDIA,
                    dias,
                    pedido.getObra().getIdObra(),
                    "/pedidos"));
        }

        // ---- Pedidos enviados esperando recepción ----
        for (Pedido pedido : pedidoRepositorio
                .findByEstadoOrderByFechaSolicitudAsc(Pedido.ESTADO_ENVIADO)) {
            long dias = pedido.getFechaAprobacion() == null ? 0
                    : ChronoUnit.DAYS.between(pedido.getFechaAprobacion().toLocalDate(), hoy);
            pendientes.add(new Pendiente(
                    TIPO_PEDIDO,
                    "Material en camino a " + pedido.getObra().getDireccionObra(),
                    "Enviado al proveedor hace " + dias + " " + (dias == 1 ? "día" : "días")
                            + " · falta confirmar la recepción",
                    URGENCIA_MEDIA,
                    dias,
                    pedido.getObra().getIdObra(),
                    "/pedidos"));
        }

        // ---- Cobros vencidos y por vencer ----
        // Salen del consolidado de Cobros, que ya resolvio que cuota esta
        // vencida a partir del calendario.
        for (ResumenCobro cobro : cobrosService.consolidado()) {
            if (cobro.cuotasVencidas() != null && cobro.cuotasVencidas() > 0) {
                pendientes.add(new Pendiente(
                        TIPO_COBRO,
                        cobro.cuotasVencidas() + " "
                                + (cobro.cuotasVencidas() == 1 ? "cuota vencida" : "cuotas vencidas")
                                + " · " + cobro.nombreCliente(),
                        cobro.direccionObra() + " · saldo " + formatear(cobro.saldoPendiente()),
                        URGENCIA_ALTA,
                        null,
                        cobro.idObra(),
                        "/cobranzas"));

            } else if (cobro.proximoVencimiento() != null
                    && !cobro.proximoVencimiento().isAfter(hoy.plusDays(DIAS_DE_LA_SEMANA))) {
                long dias = ChronoUnit.DAYS.between(hoy, cobro.proximoVencimiento());
                pendientes.add(new Pendiente(
                        TIPO_COBRO,
                        "Vence una cuota de " + cobro.nombreCliente(),
                        cobro.direccionObra() + " · "
                                + (dias == 0 ? "vence hoy" : "en " + dias + " días"),
                        URGENCIA_MEDIA,
                        dias,
                        cobro.idObra(),
                        "/cobranzas"));
            }
        }

        // Primero lo urgente, y dentro de eso lo que espera hace mas tiempo.
        pendientes.sort(Comparator
                .comparing((Pendiente p) -> !URGENCIA_ALTA.equals(p.urgencia()))
                .thenComparing(p -> p.diasEsperando() == null ? 0L : -p.diasEsperando()));

        return pendientes;
    }

    /**
     * Importe con separador de miles, para los textos de los pendientes.
     *
     * Se arma aca y no en el frontend porque estos textos viajan ya redactados
     * ("Enviado hace 9 dias · $ 143.977.000"): partirlos en datos sueltos para
     * que la pantalla los vuelva a unir no aporta nada.
     *
     * Locale explicito y no el del servidor: en Railway el sistema corre en
     * ingles y los miles saldrian separados con coma, que en Argentina se lee
     * como el separador decimal.
     */
    private String formatear(BigDecimal monto) {
        if (monto == null) {
            return "$ 0";
        }
        // forLanguageTag y no Locale.of(): ese metodo es de Java 19 y este
        // proyecto compila con Java 17, el que declara la Propuesta Tecnica.
        return String.format(java.util.Locale.forLanguageTag("es-AR"), "$ %,d",
                monto.setScale(0, java.math.RoundingMode.HALF_UP).longValue());
    }
}
