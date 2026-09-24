package com.sigco.dashboard.dto;

import com.sigco.gastos.dto.EstadoFinanciero;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * El cierre economico de una obra.
 *
 * ------------------------------------------------------------------
 *  Que problema resuelve
 * ------------------------------------------------------------------
 *
 * Es el pedido de Ricardo: "que haya un boton para poner obra terminada para
 * ver balance". Hoy el resultado de una obra se arma a mano cuando termina,
 * juntando la planilla de gastos, lo que se acuerda haber cobrado y el
 * presupuesto original. Eso pasa semanas despues de terminarla, y con suerte.
 *
 * ------------------------------------------------------------------
 *  Los tres numeros, y por que son tres y no uno
 * ------------------------------------------------------------------
 *
 * La pregunta "cuanto gane con esta obra" tiene tres respuestas distintas, y
 * confundirlas es lo que hace que una obra parezca rentable cuando no lo es:
 *
 *   gananciaEstimada  = presupuestado - gastado
 *       Lo que la obra deberia dejar si el cliente termina de pagar todo.
 *       Es una proyeccion, no plata.
 *
 *   resultadoDeCaja   = cobrado - gastado
 *       La plata que realmente entro menos la que realmente salio. Puede ser
 *       negativa en una obra sana: se compro material que todavia no se cobro.
 *
 *   saldoPorCobrar    = plan de cobro - cobrado
 *       Lo que falta que entre. Es lo que separa a los otros dos numeros, y
 *       tambien lo que hay que seguir reclamando despues de terminar la obra.
 *
 * Una obra terminada con ganancia estimada buena y saldo por cobrar grande no
 * esta cerrada: esta esperando plata.
 *
 * ------------------------------------------------------------------
 *  De donde sale cada dato
 * ------------------------------------------------------------------
 *
 * De ningun lado nuevo. Igual que el Tablero, este balance no calcula por su
 * cuenta: el presupuestado y el gastado por rubro vienen de Gastos, el cobrado
 * de Cobros, y el avance de Seguimiento. Lo unico que agrega son las restas de
 * arriba, que no son de ningun modulo en particular.
 */
public record BalanceDeObra(
        Long idObra,
        String direccionObra,
        String nombreCliente,
        String estadoObra,
        LocalDate fechaInicioReal,
        LocalDate fechaFinEstimada,

        /** Presupuestado contra gastado, con el desglose por rubro y el semaforo. */
        EstadoFinanciero economia,

        /** Lo que se acordo cobrar, lo que entro y lo que falta. */
        BigDecimal totalPlanDeCobro,
        BigDecimal totalCobrado,
        BigDecimal saldoPorCobrar,
        Integer cuotasVencidas,

        /** Presupuestado menos gastado. Negativo = la obra pierde plata. */
        BigDecimal gananciaEstimada,

        /** Cobrado menos gastado: la plata que de verdad quedo. */
        BigDecimal resultadoDeCaja,

        /** La ganancia estimada como porcentaje de lo presupuestado. */
        BigDecimal margenPorcentaje,

        BigDecimal avanceFisico,
        Integer hitosCompletados,
        Integer hitosTotales,

        /**
         * Lo que queda abierto en esta obra.
         *
         * Es la parte util del boton de cerrar: antes de dar una obra por
         * terminada conviene saber que hay tres hitos sin marcar y cuatro
         * cuotas sin cobrar. No impide cerrarla —una obra puede terminarse y
         * seguir cobrandose— pero deja de ser una sorpresa.
         */
        List<String> pendientes) {
}
