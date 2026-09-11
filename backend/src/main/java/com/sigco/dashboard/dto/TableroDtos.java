package com.sigco.dashboard.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Respuestas del Tablero.
 *
 * El Tablero no tiene entidades propias: no hay tabla `tablero` en el modelo de
 * datos. Todo lo que devuelve sale de los otros modulos, asi que estos records
 * son solo la forma en que esa informacion llega armada al frontend.
 *
 * La decision de fondo del modulo esta aca: el Tablero NO recalcula nada. El
 * porcentaje consumido lo calcula Gastos, el avance fisico lo calcula
 * Seguimiento y el saldo lo calcula Cobros. Si el Tablero los volviera a
 * calcular, tarde o temprano una pantalla mostraria un numero y otra mostraria
 * otro distinto para lo mismo, que es exactamente el problema que el sistema
 * viene a resolver.
 */
public final class TableroDtos {

    private TableroDtos() {
    }

    /**
     * Todo el tablero en una sola respuesta.
     *
     * Es una sola llamada y no cinco porque la pantalla no sirve de a partes:
     * el dueño entra para ver el estado general, y media pantalla cargada no le
     * dice nada. Una sola respuesta ademas garantiza que todos los numeros
     * correspondan al mismo instante.
     */
    public record Tablero(
            LocalDateTime momento,
            Resumen resumen,
            List<ObraEnTablero> obras,
            List<Pendiente> pendientes) {
    }

    /**
     * Las cifras de arriba de todo: el estado del negocio en cuatro numeros.
     *
     * Son totales de las obras EN EJECUCION unicamente. Sumar las finalizadas
     * mezclaria plata ya cerrada con plata en juego y el numero dejaria de
     * servir para decidir.
     */
    public record Resumen(
            int obrasEnEjecucion,
            int obrasEnPresupuestacion,
            BigDecimal totalPresupuestado,
            BigDecimal totalGastado,
            /** Presupuestado menos gastado. Negativa = las obras estan perdiendo plata. */
            BigDecimal gananciaEstimada,
            BigDecimal saldoPorCobrar,
            /** Cuanto de ese saldo vence dentro de los proximos siete dias. */
            BigDecimal porCobrarEstaSemana,
            /** Obras cuyo semaforo esta en rojo: gastaron mas de lo presupuestado. */
            int obrasExcedidas,
            /** Obras que gastan mas rapido de lo que avanzan. */
            int obrasConDesfasaje,
            int pendientesUrgentes) {
    }

    /**
     * Una obra activa, vista desde arriba.
     *
     * Cruza las tres dimensiones que el dueño mira juntas y que hoy tiene que ir
     * a buscar a tres lugares distintos: cuanto lleva gastado, cuanto lleva
     * construido y cuanto le falta cobrar.
     */
    public record ObraEnTablero(
            Long idObra,
            String direccionObra,
            String nombreCliente,
            String estado,
            BigDecimal totalPresupuestado,
            BigDecimal totalGastado,
            BigDecimal gananciaEstimada,
            /** Verde / Amarillo / Rojo / Sin presupuesto. Lo decide Gastos, no el Tablero. */
            String semaforo,
            BigDecimal avanceFisico,
            BigDecimal avanceFinanciero,
            /** avanceFinanciero - avanceFisico. Positivo = se gasta mas rapido de lo que se avanza. */
            BigDecimal desfasaje,
            boolean alertaDesfasaje,
            boolean atrasada,
            Long diasParaElPlazo,
            Integer hitosCompletados,
            Integer hitosTotales,
            BigDecimal saldoPendiente,
            Integer cuotasVencidas,
            LocalDate proximoVencimiento) {
    }

    /**
     * Algo que espera una decision del dueño.
     *
     * Es la parte del tablero que reemplaza la memoria: hoy un presupuesto
     * enviado hace veinte dias o un pedido esperando aprobacion no aparecen en
     * ningun lado hasta que alguien pregunta.
     *
     * Los cuatro tipos son cosas que SOLO el dueño puede destrabar. Un hito sin
     * completar no entra: eso lo resuelve el capataz y no le corresponde a esta
     * lista.
     */
    public record Pendiente(
            /** Presupuesto / Pedido / Cobro. Agrupa la lista en pantalla. */
            String tipo,
            String titulo,
            String detalle,
            /** alta / media. Ordena la lista: primero lo que ya se vencio. */
            String urgencia,
            /** Hace cuantos dias espera. Es lo que convierte "pendiente" en "urgente". */
            Long diasEsperando,
            Long idObra,
            /** A donde lleva el enlace de la pantalla. */
            String ruta) {
    }
}
