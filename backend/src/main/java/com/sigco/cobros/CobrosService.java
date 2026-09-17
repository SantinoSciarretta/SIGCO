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

    public CobrosService(CuotaRepository repositorio,
                         RegistroCacRepository cacRepositorio,
                         ObraRepository obraRepositorio,
                         PresupuestoRepository presupuestoRepositorio) {
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
                    sumar(cuotas, Cuota::estaAbonada),
                    sumar(cuotas, Cuota::estaPendiente),
                    (int) cuotas.stream().filter(Cuota::estaVencida).count(),
                    proximoVencimiento(cuotas)));
        }
        // Primero las que tienen cuotas vencidas: son las que hay que reclamar.
        resumen.sort(Comparator.comparing(ResumenCobro::cuotasVencidas).reversed());
        return resumen;
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
     * El monto no se recibe: es el de la cuota. El informe establece que si el
     * cliente paga fuera de termino se mantiene el valor sin recargos, tal como
     * lo maneja hoy la empresa, asi que no hay nada que recalcular al cobrar.
     */
    @Transactional
    public PlanDeCobro registrarPago(Long idCuota, RegistrarPago pago) {
        Cuota cuota = buscarCuotaOFallar(idCuota);

        if (cuota.estaAbonada()) {
            throw new ReglaDeNegocioException("La cuota ya está abonada.");
        }

        cuota.abonar(pago.fechaPago(), pago.medioPago(), pago.comprobanteEmitido());
        return plan(cuota.getObra().getIdObra());
    }

    @Transactional
    public PlanDeCobro anularPago(Long idCuota, AnularPago anulacion) {
        Cuota cuota = buscarCuotaOFallar(idCuota);

        if (!cuota.estaAbonada()) {
            throw new ReglaDeNegocioException("La cuota no está abonada.");
        }

        cuota.anularPago(anulacion.motivo().trim());
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
                        new RegistroCac(mes, solicitud.valorIndice())));
        registro.corregirValor(solicitud.valorIndice());

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
        List<RegistroCac> ultimos = cacRepositorio.ultimosDos();
        if (ultimos.size() < 2) {
            throw new ReglaDeNegocioException(
                    "Hacen falta al menos dos meses de índice cargados para poder "
                    + "actualizar: el ajuste sale de comparar un mes con el anterior.");
        }

        RegistroCac actual = ultimos.get(0);
        RegistroCac anterior = ultimos.get(1);
        BigDecimal coeficiente = coeficienteEntre(anterior, actual);

        List<Cuota> pendientes = repositorio.delPlan(idObra).stream()
                .filter(Cuota::estaPendiente)
                .toList();

        BigDecimal saldo = pendientes.stream()
                .map(Cuota::getMontoCuota)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new PreviaCac(
                anterior.getMesCorrespondiente(), anterior.getValorIndice(),
                actual.getMesCorrespondiente(), actual.getValorIndice(),
                coeficiente, saldo,
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

    /**
     * Coeficiente de actualizacion: indice del mes sobre el del mes anterior.
     *
     * Se usa la RELACION entre dos meses y no el indice suelto: el CAC es un
     * numero absoluto (por ejemplo 1234,56) que por si solo no dice cuanto
     * subieron los costos. Lo que importa es cuanto crecio respecto del mes
     * pasado.
     */
    private BigDecimal coeficienteEntre(RegistroCac anterior, RegistroCac actual) {
        if (anterior.getValorIndice().signum() == 0) {
            throw new ReglaDeNegocioException(
                    "El índice del mes anterior es cero: no se puede calcular el ajuste.");
        }
        return actual.getValorIndice()
                .divide(anterior.getValorIndice(), 6, RoundingMode.HALF_UP);
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
                sumar(cuotas, Cuota::estaAbonada),
                sumar(cuotas, Cuota::estaPendiente),
                (int) cuotas.stream().filter(Cuota::estaAbonada).count(),
                cuotas.size(),
                (int) cuotas.stream().filter(Cuota::estaVencida).count(),
                proximoVencimiento(cuotas),
                cuotas.stream()
                        .sorted(Comparator.comparing(Cuota::getNumeroCuota))
                        .map(CuotaRespuesta::desde).toList());
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
