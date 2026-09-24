package com.sigco.dashboard;

import com.sigco.cobros.CobrosService;
import com.sigco.cobros.dto.CobrosDtos.ResumenCobro;
import com.sigco.common.exception.RecursoNoEncontradoException;
import com.sigco.dashboard.dto.BalanceDeObra;
import com.sigco.gastos.GastoService;
import com.sigco.gastos.dto.EstadoFinanciero;
import com.sigco.obras.Obra;
import com.sigco.obras.ObraRepository;
import com.sigco.seguimiento.SeguimientoService;
import com.sigco.seguimiento.dto.SeguimientoDtos.AvanceObra;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * El balance de una obra: que dejo, que falta cobrar y que quedo abierto.
 *
 * Responde al pedido de Ricardo de tener un boton de obra terminada que muestre
 * el balance. El cierre economico de una obra hoy se arma a mano semanas
 * despues, juntando la planilla de gastos con lo que se acuerda haber cobrado.
 *
 * ------------------------------------------------------------------
 *  La misma decision que el Tablero
 * ------------------------------------------------------------------
 *
 * Este servicio no calcula nada propio salvo tres restas. El presupuestado y el
 * gastado por rubro los pide a Gastos, lo cobrado a Cobros y el avance a
 * Seguimiento. Si recalculara por su cuenta —por ejemplo el semaforo, o el
 * total gastado sumando la tabla directo— el dia que cambie un umbral la ficha
 * de la obra y el balance mostrarian numeros distintos de la misma obra, que es
 * exactamente el problema que el sistema viene a resolver.
 *
 * Vive en el modulo Dashboard y no en Obras por la direccion de las
 * dependencias: Gastos, Cobros y Seguimiento ya dependen de Obras. Si el
 * balance viviera ahi, Obras pasaria a depender de los tres y quedaria un
 * ciclo. Dashboard es el modulo que consolida, y esto es una consolidacion.
 */
@Service
public class BalanceService {

    private static final BigDecimal CIEN = new BigDecimal("100");
    private static final int DECIMALES = 2;

    private final ObraRepository obraRepositorio;
    private final GastoService gastoService;
    private final CobrosService cobrosService;
    private final SeguimientoService seguimientoService;

    public BalanceService(ObraRepository obraRepositorio,
                          GastoService gastoService,
                          CobrosService cobrosService,
                          SeguimientoService seguimientoService) {
        this.obraRepositorio = obraRepositorio;
        this.gastoService = gastoService;
        this.cobrosService = cobrosService;
        this.seguimientoService = seguimientoService;
    }

    @Transactional(readOnly = true)
    public BalanceDeObra de(Long idObra) {
        Obra obra = obraRepositorio.findById(idObra)
                .orElseThrow(() -> new RecursoNoEncontradoException("Obra", idObra));

        // La variante que no falla: el balance se consulta de cualquier obra,
        // incluso de una que se ejecuto sin definitivo aprobado. Ahi el
        // presupuestado es cero y los gastos quedan igual a la vista.
        EstadoFinanciero economia = gastoService.estadoFinancieroOVacio(idObra);
        ResumenCobro cobros = cobrosService.resumenOVacio(idObra);
        AvanceObra avance = seguimientoService.avance(idObra);

        BigDecimal presupuestado = economia.totalPresupuestado();
        BigDecimal gastado = economia.totalGastado();
        BigDecimal cobrado = cobros.totalCobrado();

        return new BalanceDeObra(
                obra.getIdObra(),
                obra.getDireccionObra(),
                obra.getCliente().getNombreApellido(),
                obra.getEstado(),
                obra.getFechaInicioReal(),
                obra.getFechaFinEstimada(),
                economia,
                cobros.totalPlan(),
                cobrado,
                cobros.saldoPendiente(),
                cobros.cuotasVencidas(),
                presupuestado.subtract(gastado),
                // La plata que de verdad quedo. Puede ser negativa en una obra
                // sana: se compro material que todavia no se cobro.
                cobrado.subtract(gastado),
                margen(presupuestado, gastado),
                avance.avanceFisico(),
                avance.hitosCompletados(),
                avance.hitosTotales(),
                pendientes(avance, cobros, economia));
    }

    /**
     * La ganancia estimada como porcentaje de lo presupuestado.
     *
     * Sin presupuesto aprobado da cero en vez de dividir por cero. Podria
     * devolver null, pero un margen ausente y un margen de cero se ven igual en
     * la pantalla y el estado financiero ya informa que no hay presupuesto.
     */
    private BigDecimal margen(BigDecimal presupuestado, BigDecimal gastado) {
        if (presupuestado.signum() == 0) {
            return BigDecimal.ZERO.setScale(DECIMALES);
        }
        return presupuestado.subtract(gastado)
                .multiply(CIEN)
                .divide(presupuestado, DECIMALES, RoundingMode.HALF_UP);
    }

    /**
     * Lo que queda abierto en la obra.
     *
     * Es la parte util del boton de cerrar. Nada de esto impide terminar una
     * obra —se puede terminar de construir y seguir cobrando durante meses—
     * pero conviene verlo antes de darla por cerrada y no despues.
     *
     * Son frases y no codigos porque el unico consumidor es una pantalla que
     * las muestra tal cual. El dia que haya que decidir algo en base a esto,
     * conviene devolver datos y no texto.
     */
    private List<String> pendientes(AvanceObra avance, ResumenCobro cobros,
                                    EstadoFinanciero economia) {
        List<String> abiertos = new ArrayList<>();

        int hitosSinCompletar = avance.hitosTotales() - avance.hitosCompletados();
        if (hitosSinCompletar > 0) {
            abiertos.add(hitosSinCompletar == 1
                    ? "Queda 1 hito sin completar."
                    : "Quedan " + hitosSinCompletar + " hitos sin completar.");
        }

        if (cobros.saldoPendiente().signum() > 0) {
            abiertos.add("Falta cobrar " + pesos(cobros.saldoPendiente()) + ".");
        }
        if (cobros.cuotasVencidas() > 0) {
            abiertos.add(cobros.cuotasVencidas() == 1
                    ? "Hay 1 cuota vencida."
                    : "Hay " + cobros.cuotasVencidas() + " cuotas vencidas.");
        }

        // Un rubro excedido no impide cerrar, pero es lo que hay que mirar al
        // presupuestar la proxima obra parecida.
        long excedidos = economia.rubros().stream()
                .filter(r -> r.diferencia().signum() < 0)
                .count();
        if (excedidos > 0) {
            abiertos.add(excedidos == 1
                    ? "1 rubro se pasó del presupuesto."
                    : excedidos + " rubros se pasaron del presupuesto.");
        }

        return abiertos;
    }

    /**
     * Formato para los textos de pendientes.
     *
     * Con separador de miles: "$ 17.293.854" se lee de un vistazo y
     * "$ 17293854" hay que contarlo con el dedo. Es el unico lugar donde el
     * backend formatea un numero, porque es el unico donde arma una frase; el
     * resto de las cifras viajan crudas y las formatea la pantalla.
     */
    private String pesos(BigDecimal monto) {
        // forLanguageTag y no Locale.of: ese metodo es de Java 19 y el
        // proyecto compila con 17. Tampoco `new Locale(...)`, que esta
        // deprecado.
        return "$ " + String.format(java.util.Locale.forLanguageTag("es-AR"), "%,d",
                monto.setScale(0, RoundingMode.HALF_UP).toBigInteger());
    }
}
