package com.sigco.cobros;

import com.sigco.cobros.dto.CobrosDtos.AnularPago;
import com.sigco.cobros.dto.CobrosDtos.CuotaRespuesta;
import com.sigco.cobros.dto.CobrosDtos.GenerarPlan;
import com.sigco.cobros.dto.CobrosDtos.IndiceCacRespuesta;
import com.sigco.cobros.dto.CobrosDtos.NuevoIndiceCac;
import com.sigco.cobros.dto.CobrosDtos.PlanDeCobro;
import com.sigco.cobros.dto.CobrosDtos.PreviaCac;
import com.sigco.cobros.dto.CobrosDtos.RegistrarPago;
import com.sigco.cobros.dto.CobrosDtos.ResumenCobro;
import com.sigco.common.exception.RecursoNoEncontradoException;
import com.sigco.common.exception.ReglaDeNegocioException;
import com.sigco.obras.Obra;
import com.sigco.obras.ObraRepository;
import com.sigco.presupuestacion.Presupuesto;
import com.sigco.presupuestacion.PresupuestoRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reglas del modulo Cobros.
 *
 * Reemplaza la planilla por obra mas la planilla resumen que hoy se actualiza a
 * mano, y sobre todo elimina la dependencia de la memoria del dueño para saber
 * que cuota esta por vencer.
 */
@Service
public class CobrosService {

    private static final BigDecimal CIEN = new BigDecimal("100");
    private static final int DECIMALES = 2;

    /** Las cuotas son quincenales, tal como las maneja hoy la empresa. */
    private static final int DIAS_ENTRE_CUOTAS = 15;

    /** Ventana de la alerta: una cuota que vence dentro de una semana. */
    private static final int DIAS_DE_AVISO = 7;

    private final CuotaRepository repositorio;
    private final RegistroCacRepository cacRepositorio;
    private final ObraRepository obraRepositorio;
    private final PresupuestoRepository presupuestoRepositorio;

    /** Arma el PDF de la planilla que se le entrega al cliente. */
    private final GeneradorDePlanillaDePagos generador = new GeneradorDePlanillaDePagos();

    private final com.sigco.accesos.ServicioAuditoria auditoria;

    /** Quien registra cada pago: queda guardado en pago.id_usuario_registro. */
    private final com.sigco.seguridad.SesionActual sesion;

    public CobrosService(CuotaRepository repositorio,
                         RegistroCacRepository cacRepositorio,
                         ObraRepository obraRepositorio,
                         PresupuestoRepository presupuestoRepositorio,
                         com.sigco.accesos.ServicioAuditoria auditoria,
                         com.sigco.seguridad.SesionActual sesion) {
        this.auditoria = auditoria;
        this.sesion = sesion;
        this.repositorio = repositorio;
        this.cacRepositorio = cacRepositorio;
        this.obraRepositorio = obraRepositorio;
        this.presupuestoRepositorio = presupuestoRepositorio;
    }

    // ------------------------------------------------------------------
    //  Generacion del plan
    // ------------------------------------------------------------------

    /**
     * Genera el plan de cobro de una obra a partir de su definitivo aprobado.
     *
     * Todos los datos salen del presupuesto: el porcentaje de anticipo, la
     * cantidad de cuotas y el total. Lo unico que se pide es la fecha del primer
     * vencimiento, porque eso se acuerda con el cliente y no esta en el
     * presupuesto.
     *
     * Pedir de nuevo el anticipo o el total abriria la puerta a que el plan no
     * coincida con lo que el cliente acepto, que es exactamente lo que hoy pasa
     * al copiar numeros entre planillas.
     */
    @Transactional
    public PlanDeCobro generar(Long idObra, GenerarPlan solicitud) {
        Obra obra = buscarObraOFallar(idObra);
        Presupuesto definitivo = buscarDefinitivoAprobado(obra);

        if (repositorio.existsByObraIdObra(idObra)) {
            throw new ReglaDeNegocioException(
                    "La obra ya tiene un plan de cobro. Para rehacerlo hay que "
                    + "eliminar el actual, y eso borraría los pagos registrados.");
        }
        if (definitivo.getAnticipoPorcentaje() == null
                || definitivo.getCantidadCuotas() == null) {
            throw new ReglaDeNegocioException(
                    "El presupuesto aprobado no tiene definido el plan de pago "
                    + "(anticipo y cuotas). Cargalo antes de generar el cobro.");
        }

        BigDecimal total = definitivo.getTotalPresupuesto();
        BigDecimal anticipo = total.multiply(definitivo.getAnticipoPorcentaje())
                .divide(CIEN, DECIMALES, RoundingMode.HALF_UP);
        int cantidadCuotas = definitivo.getCantidadCuotas();

        List<Cuota> plan = new ArrayList<>();
        plan.add(new Cuota(obra, 0, anticipo, solicitud.primerVencimiento()));

        if (cantidadCuotas > 0) {
            BigDecimal resto = total.subtract(anticipo);
            BigDecimal montoCuota = resto.divide(
                    new BigDecimal(cantidadCuotas), DECIMALES, RoundingMode.HALF_UP);

            // El redondeo de las cuotas puede dejar centavos afuera. Se ajustan
            // en la ULTIMA, para que el plan cierre exactamente en el total del
            // presupuesto: la regla del informe es que anticipo + cuotas den el
            // total aprobado, y unos centavos de diferencia la incumplen igual.
            BigDecimal acumulado = montoCuota.multiply(new BigDecimal(cantidadCuotas));
            BigDecimal ajuste = resto.subtract(acumulado);

            LocalDate vencimiento = solicitud.primerVencimiento();
            for (int i = 1; i <= cantidadCuotas; i++) {
                vencimiento = vencimiento.plusDays(DIAS_ENTRE_CUOTAS);
                BigDecimal monto = (i == cantidadCuotas) ? montoCuota.add(ajuste) : montoCuota;
                plan.add(new Cuota(obra, i, monto, vencimiento));
            }
        }

        repositorio.saveAll(plan);
        return armarPlan(obra, plan);
    }

    // ------------------------------------------------------------------
    //  Consulta
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public PlanDeCobro plan(Long idObra) {
        Obra obra = buscarObraOFallar(idObra);
        List<Cuota> cuotas = repositorio.delPlan(idObra);

        if (cuotas.isEmpty()) {
            throw new ReglaDeNegocioException(
                    "La obra todavía no tiene plan de cobro generado.");
        }
        return armarPlan(obra, cuotas);
    }

    /**
     * Vista consolidada: cuanto resta cobrar de cada obra.
     *
     * Reemplaza la planilla resumen que hoy se actualiza a mano y que, por eso
     * mismo, casi siempre esta desactualizada.
     */
    /**
     * La planilla de pagos en PDF, para entregarle al cliente.
     *
     * Es un pendiente que el informe pide para este modulo: reemplaza a la
     * planilla que hoy la empresa arma a mano y actualiza cada vez que el
     * cliente pregunta cuanto debe.
     */
    @Transactional(readOnly = true)
    public byte[] generarPlanilla(Long idObra) {
        Obra obra = buscarObraOFallar(idObra);
        List<Cuota> cuotas = repositorio.delPlan(idObra);

        if (cuotas.isEmpty()) {
            throw new ReglaDeNegocioException(
                    "Esta obra todavía no tiene plan de cobro.");
        }
        return generador.generar(obra, cuotas);
    }

    /** Nombre del archivo, armado con la direccion de la obra. */
    @Transactional(readOnly = true)
    public String nombreDePlanilla(Long idObra) {
        return generador.nombreDeArchivo(buscarObraOFallar(idObra));
    }

    @Transactional(readOnly = true)
    public List<ResumenCobro> consolidado() {
        Map<Long, List<Cuota>> porObra = new LinkedHashMap<>();
        for (Cuota c : repositorio.deObrasConCobros()) {
            c.revisarVencimiento(LocalDate.now());
            porObra.computeIfAbsent(c.getObra().getIdObra(), k -> new ArrayList<>()).add(c);
        }

        List<ResumenCobro> resumen = new ArrayList<>();
        for (List<Cuota> cuotas : porObra.values()) {
            Obra obra = cuotas.get(0).getObra();
            resumen.add(new ResumenCobro(
                    obra.getIdObra(), obra.getDireccionObra(),
                    obra.getCliente().getNombreApellido(), obra.getEstado(),
                    sumar(cuotas, c -> true),
                    // Lo cobrado sale de los pagos y el saldo de lo que falta:
                    // con pagos parciales, una misma cuota aporta a los dos.
                    sumarPagado(cuotas),
                    sumarSaldo(cuotas),
                    (int) cuotas.stream().filter(Cuota::estaVencida).count(),
                    proximoVencimiento(cuotas)));
        }
        // Primero las que tienen cuotas vencidas: son las que hay que reclamar.
        resumen.sort(Comparator.comparing(ResumenCobro::cuotasVencidas).reversed());
        return resumen;
    }

    /**
     * Lo cobrado de una obra, sin fallar si no tiene plan.
     *
     * `plan` lanza cuando la obra todavia no tiene cuotas generadas, y esta
     * bien que lo haga: quien entra a la pantalla de cobros de una obra sin
     * plan tiene que enterarse. Pero el balance de obra consulta a varios
     * modulos y una obra sin plan de cobro es un caso normal —se genera recien
     * cuando se aprueba el definitivo—, asi que ahi la ausencia es un cero y
     * no un error.
     */
    @Transactional(readOnly = true)
    public ResumenCobro resumenOVacio(Long idObra) {
        Obra obra = buscarObraOFallar(idObra);
        List<Cuota> cuotas = repositorio.delPlan(idObra);
        cuotas.forEach(c -> c.revisarVencimiento(LocalDate.now()));

        return new ResumenCobro(
                obra.getIdObra(), obra.getDireccionObra(),
                obra.getCliente().getNombreApellido(), obra.getEstado(),
                sumar(cuotas, c -> true),
                sumarPagado(cuotas),
                sumarSaldo(cuotas),
                (int) cuotas.stream().filter(Cuota::estaVencida).count(),
                proximoVencimiento(cuotas));
    }

    /** Cuotas que vencen en los proximos dias o que ya vencieron. */
    @Transactional(readOnly = true)
    public List<CuotaRespuesta> alertas() {
        List<Cuota> cuotas = repositorio.porVencerHasta(
                LocalDate.now().plusDays(DIAS_DE_AVISO));
        cuotas.forEach(c -> c.revisarVencimiento(LocalDate.now()));
        return cuotas.stream().map(CuotaRespuesta::desde).toList();
    }

    // ------------------------------------------------------------------
    //  Pagos
    // ------------------------------------------------------------------

    /**
     * Registra el pago de una cuota.
     *
     *
     * El monto SI se recibe, a diferencia de antes: puede ser la cuota entera o
     * una parte. Lo que no cambia es el valor de la cuota — el informe establece
     * que si el cliente paga fuera de termino se mantiene sin recargos, tal como
     * lo maneja hoy la empresa.
     */
    @Transactional
    public PlanDeCobro registrarPago(Long idCuota, RegistrarPago pago) {
        Cuota cuota = buscarCuotaOFallar(idCuota);

        if (cuota.estaAbonada()) {
            throw new ReglaDeNegocioException("La cuota ya está abonada.");
        }

        // No se puede cobrar mas de lo que se debe. Se verifica aca y no en una
        // restriccion de la tabla porque depende de los otros pagos de la misma
        // cuota, y una restriccion no puede mirar otras filas.
        if (pago.monto().compareTo(cuota.saldo()) > 0) {
            throw new ReglaDeNegocioException(
                    "El pago (" + pago.monto() + ") supera el saldo de la cuota ("
                    + cuota.saldo() + "). Si el cliente pagó de más, registrá el "
                    + "saldo exacto y el excedente a cuenta de la cuota siguiente.");
        }

        cuota.registrarPago(new Pago(
                cuota,
                pago.monto(),
                pago.fechaPago(),
                pago.medioPago(),
                pago.comprobanteEmitido(),
                sesion.idUsuario().orElse(null)));

        // Dinero que entra. Es de las pocas acciones del sistema que afirman un
        // hecho del mundo real —"el cliente pagó"— y no se puede verificar
        // mirando otra pantalla: si no queda registrado quien la cargo, no hay
        // forma de reconstruirlo despues.
        //
        // Se anota el monto de ESTE pago y el saldo que queda, no el total de la
        // cuota: con pagos parciales son cosas distintas.
        auditoria.registrar(
                "Cobro de " + pago.monto()
                + " en la cuota " + cuota.getNumeroCuota()
                + " de la obra #" + cuota.getObra().getIdObra()
                + " (" + pago.medioPago() + "). Saldo: " + cuota.saldo(),
                "Cobros");

        return plan(cuota.getObra().getIdObra());
    }

    /**
     * Anula la cobranza de la cuota: vuelve a deberse entera.
     *
     * Se anulan TODOS los pagos y no uno suelto. Anular una parte dejaria un
     * estado de cuenta que nadie puede reconstruir sin mirar el historial
     * entero; si hubo un error, se anula todo y se vuelven a cargar los pagos
     * que si entraron, que ademas es como se corrige en una planilla.
     */
    @Transactional
    public PlanDeCobro anularPago(Long idCuota, AnularPago anulacion) {
        Cuota cuota = buscarCuotaOFallar(idCuota);

        if (!cuota.tienePagos()) {
            throw new ReglaDeNegocioException("La cuota no tiene ningún pago registrado.");
        }

        java.math.BigDecimal anulado = cuota.totalPagado();
        cuota.anularPago(anulacion.motivo().trim());

        auditoria.registrar(
                "Anulación del cobro de la cuota " + cuota.getNumeroCuota()
                + " de la obra #" + cuota.getObra().getIdObra()
                + " por " + anulado + ": " + anulacion.motivo().trim(),
                "Cobros");

        return plan(cuota.getObra().getIdObra());
    }

    // ------------------------------------------------------------------
    //  Indice CAC
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<IndiceCacRespuesta> indices() {
        return cacRepositorio.findAllByOrderByMesCorrespondienteDesc().stream()
                .map(IndiceCacRespuesta::desde)
                .toList();
    }

    /**
     * Carga el indice del mes. Si ya existe, lo corrige.
     *
     * El valor se ingresa a mano: la importacion automatica desde la CAC esta
     * explicitamente fuera del alcance de esta version.
     */
    @Transactional
    public IndiceCacRespuesta registrarIndice(NuevoIndiceCac solicitud) {
        LocalDate mes = solicitud.mesCorrespondiente().withDayOfMonth(1);

        RegistroCac registro = cacRepositorio.findByMesCorrespondiente(mes)
                .orElseGet(() -> cacRepositorio.save(
                        new RegistroCac(mes, solicitud.coeficiente())));
        registro.corregirValor(solicitud.coeficiente());

        return IndiceCacRespuesta.desde(registro);
    }

    /**
     * Muestra como quedarian las cuotas antes de aplicar el CAC.
     *
     * El informe pide esta previa explicitamente: el dueño ve el efecto antes de
     * confirmar. Una actualizacion de saldo no deberia ser una sorpresa.
     */
    @Transactional(readOnly = true)
    public PreviaCac previaCac(Long idObra) {
        RegistroCac ultimo = cacRepositorio.ultimo()
                .orElseThrow(() -> new ReglaDeNegocioException(
                        "Todavía no hay ninguna actualización cargada. "
                        + "Cargá el coeficiente del mes y volvé."));

        BigDecimal coeficiente = ultimo.getCoeficiente();

        List<Cuota> pendientes = repositorio.delPlan(idObra).stream()
                .filter(Cuota::estaPendiente)
                .toList();

        BigDecimal saldo = pendientes.stream()
                .map(Cuota::getMontoCuota)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new PreviaCac(
                ultimo.getMesCorrespondiente(),
                coeficiente,
                saldo,
                saldo.multiply(coeficiente).setScale(DECIMALES, RoundingMode.HALF_UP),
                pendientes.size());
    }

    /**
     * Aplica el ultimo ajuste del CAC a las cuotas pendientes de la obra.
     *
     * SOLO LAS PENDIENTES. El informe es explicito: "recalcula unicamente las
     * cuotas que todavia no fueron abonadas, sin modificar las ya cobradas".
     * Reajustar una cuota ya pagada seria cobrar dos veces por lo mismo.
     */
    @Transactional
    public PlanDeCobro aplicarCac(Long idObra) {
        PreviaCac previa = previaCac(idObra);

        if (previa.cuotasAfectadas() == 0) {
            throw new ReglaDeNegocioException(
                    "No quedan cuotas pendientes: no hay saldo que actualizar.");
        }

        Obra obra = buscarObraOFallar(idObra);
        List<Cuota> cuotas = repositorio.delPlan(idObra);
        cuotas.forEach(c -> c.actualizarPorCac(previa.coeficiente()));

        return armarPlan(obra, cuotas);
    }

    // ------------------------------------------------------------------
    //  Auxiliares
    // ------------------------------------------------------------------

    private PlanDeCobro armarPlan(Obra obra, List<Cuota> cuotas) {
        LocalDate hoy = LocalDate.now();
        cuotas.forEach(c -> c.revisarVencimiento(hoy));

        return new PlanDeCobro(
                obra.getIdObra(), obra.getDireccionObra(),
                obra.getCliente().getNombreApellido(),
                sumar(cuotas, c -> true),
                sumarPagado(cuotas),
                sumarSaldo(cuotas),
                (int) cuotas.stream().filter(Cuota::estaAbonada).count(),
                cuotas.size(),
                (int) cuotas.stream().filter(Cuota::estaVencida).count(),
                proximoVencimiento(cuotas),
                cuotas.stream()
                        .sorted(Comparator.comparing(Cuota::getNumeroCuota))
                        .map(CuotaRespuesta::desde).toList());
    }

    /**
     * Lo efectivamente cobrado: la suma de los pagos recibidos.
     *
     * Antes era la suma de las cuotas abonadas, y con pagos parciales eso
     * dejaria afuera toda la plata que entro en cuotas a medio pagar.
     */
    private BigDecimal sumarPagado(List<Cuota> cuotas) {
        return cuotas.stream()
                .map(Cuota::totalPagado)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Lo que falta cobrar: la suma de los saldos.
     *
     * Antes era la suma de las cuotas pendientes enteras, que ahora contaria de
     * mas: una cuota pagada a medias no se debe entera.
     */
    private BigDecimal sumarSaldo(List<Cuota> cuotas) {
        return cuotas.stream()
                .map(Cuota::saldo)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal sumar(List<Cuota> cuotas, java.util.function.Predicate<Cuota> filtro) {
        return cuotas.stream()
                .filter(filtro)
                .map(Cuota::getMontoCuota)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private LocalDate proximoVencimiento(List<Cuota> cuotas) {
        return cuotas.stream()
                .filter(Cuota::estaPendiente)
                .map(Cuota::getFechaVencimiento)
                .min(LocalDate::compareTo)
                .orElse(null);
    }

    private Obra buscarObraOFallar(Long idObra) {
        return obraRepositorio.findById(idObra)
                .orElseThrow(() -> new RecursoNoEncontradoException("Obra", idObra));
    }

    private Cuota buscarCuotaOFallar(Long idCuota) {
        return repositorio.buscarCompleta(idCuota)
                .orElseThrow(() -> new RecursoNoEncontradoException("Cuota", idCuota));
    }

    /**
     * El plan sale del definitivo APROBADO, no de cualquier presupuesto.
     *
     * Regla del informe: no se generan cobros sobre presupuestos en borrador o
     * rechazados. Cobrarle a un cliente por un precio que no acepto seria el
     * peor error posible del sistema.
     */
    private Presupuesto buscarDefinitivoAprobado(Obra obra) {
        List<Presupuesto> aprobados = presupuestoRepositorio
                .findByObraIdObraAndTipoPresupuestoAndEstado(
                        obra.getIdObra(), Presupuesto.TIPO_DEFINITIVO,
                        Presupuesto.ESTADO_APROBADO);

        if (aprobados.isEmpty()) {
            throw new ReglaDeNegocioException(
                    "La obra no tiene un presupuesto definitivo aprobado: "
                    + "no hay sobre qué generar el plan de cobro.");
        }
        return aprobados.get(0);
    }
}
