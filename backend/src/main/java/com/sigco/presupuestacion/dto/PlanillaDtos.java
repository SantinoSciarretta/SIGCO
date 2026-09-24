package com.sigco.presupuestacion.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

/**
 * La planilla de carga de un rubro del presupuesto.
 *
 * ------------------------------------------------------------------
 *  Que problema resuelve
 * ------------------------------------------------------------------
 *
 * Hasta ahora, presupuestar un rubro era agregar los items de a uno: abrir un
 * formulario, elegir el material, escribir cantidad y precio, guardar, y volver
 * a empezar. Con veinte materiales, son veinte vueltas por el mismo formulario.
 *
 * Ricardo lo pidio de otra forma al probar el sistema: que al elegir un rubro
 * aparezcan TODOS los materiales del catalogo de ese rubro en una planilla,
 * como si fuera una hoja de calculo, y el vaya completando cantidad y precio en
 * las filas que correspondan. Las que deje vacias no se cargan.
 *
 * Es como se presupuesta en papel, y es mas rapido: se recorre la lista una
 * sola vez en lugar de recordar que materiales hay que ir a buscar.
 */
public final class PlanillaDtos {

    private PlanillaDtos() {
    }

    // ------------------------------------------------------------------
    //  Salida: la planilla para completar
    // ------------------------------------------------------------------

    /**
     * Un rubro listo para presupuestar, con todas sus filas.
     *
     * @param idRubro       el rubro que se esta presupuestando
     * @param nombreRubro   para el titulo de la pantalla
     * @param esManoDeObra  si es true, las filas son los OTROS rubros y no
     *                      materiales: cambia lo que la pantalla escribe en el
     *                      encabezado de la primera columna
     * @param filas         una por material (o por rubro, en mano de obra)
     * @param subtotal      lo ya cargado para este rubro, para verlo sin sumar
     */
    public record PlanillaDeRubro(
            Long idRubro,
            String nombreRubro,
            boolean esManoDeObra,
            List<FilaPlanilla> filas,
            BigDecimal subtotal) {
    }

    /**
     * Una fila de la planilla.
     *
     * Viene con la cantidad y el precio YA CARGADOS si ese material se
     * presupuestó antes, y en null si todavía no. Así la pantalla es la misma
     * para cargar por primera vez y para corregir lo cargado: el usuario ve la
     * planilla completa con lo que ya puso.
     *
     * @param idMaterial    el material del catálogo, o null en la fila de un
     *                      rubro dentro de la planilla de mano de obra
     * @param idSubrubro    el subrubro sugerido, si el material tiene uno
     * @param descripcion   lo que se lee en la primera columna
     * @param unidadMedida  "bolsa", "m2", "jornal"; se muestra al lado de la cantidad
     * @param cantidad      lo ya cargado, o null
     * @param valorUnitario lo ya cargado, o null
     */
    public record FilaPlanilla(
            Long idMaterial,
            Long idSubrubro,
            String descripcion,
            String unidadMedida,
            BigDecimal cantidad,
            BigDecimal valorUnitario) {
    }

    // ------------------------------------------------------------------
    //  Entrada: la planilla completada
    // ------------------------------------------------------------------

    /**
     * Lo que la pantalla manda al guardar: TODAS las filas, completas o no.
     *
     * Se manda la planilla entera y no solo lo que cambió, y el servicio
     * reemplaza con esto los ítems que el presupuesto tenía de ese rubro. Es
     * deliberado: mandar solo los cambios obligaría a llevar la cuenta de qué
     * fila se editó, cuál se vació y cuál se dejó igual, y un despiste ahí deja
     * ítems fantasma que nadie ve pero que suman al total.
     *
     * Las filas sin cantidad o sin precio se descartan: es el "los que deja en
     * vacío que no le tome precio" del pedido.
     */
    public record PlanillaCompletada(
            @NotNull(message = "La planilla no puede venir vacía")
            List<FilaCompletada> filas) {
    }

    public record FilaCompletada(
            /** Null en la planilla de mano de obra, donde las filas son rubros. */
            Long idMaterial,

            Long idSubrubro,

            @Size(max = 250, message = "La descripción no puede superar los 250 caracteres")
            String descripcion,

            @Size(max = 20, message = "La unidad no puede superar los 20 caracteres")
            String unidadMedida,

            /**
             * Null o cero significa "esta fila no va al presupuesto".
             *
             * No se valida como obligatoria justamente por eso: la planilla
             * llega con todas sus filas, y la mayoría vienen vacías.
             */
            @DecimalMin(value = "0", message = "La cantidad no puede ser negativa")
            BigDecimal cantidad,

            @PositiveOrZero(message = "El valor unitario no puede ser negativo")
            BigDecimal valorUnitario) {

        /**
         * Si esta fila se carga al presupuesto.
         *
         * Hacen falta las dos cosas: una cantidad sin precio no suma nada al
         * total, y un precio sin cantidad tampoco. Cualquiera de las dos sola es
         * una fila a medio completar, y guardarla dejaría un ítem que aporta
         * cero al presupuesto y ensucia el detalle.
         */
        public boolean tieneCarga() {
            return cantidad != null && cantidad.signum() > 0
                    && valorUnitario != null;
        }
    }
}
