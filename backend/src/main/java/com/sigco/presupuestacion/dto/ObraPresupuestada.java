package com.sigco.presupuestacion.dto;

import com.sigco.obras.Obra;
import com.sigco.presupuestacion.Presupuesto;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

/**
 * Los presupuestos de una obra, juntos.
 *
 * ------------------------------------------------------------------
 *  Por que existe
 * ------------------------------------------------------------------
 *
 * El listado plano mostraba una fila por presupuesto, mezclando los de todas
 * las obras. Ricardo lo pidio al reves al probar el sistema: primero la obra, y
 * al entrar, sus instancias. Tiene sentido, porque asi es como piensa el
 * circuito — una obra pasa por cotizacion inicial, anteproyecto y definitivo, y
 * lo que le interesa saber es en cual esta cada una.
 *
 * ------------------------------------------------------------------
 *  Que es el "vigente"
 * ------------------------------------------------------------------
 *
 * Una obra puede tener varios presupuestos y solo uno gobierna: el que se
 * cobra, el que Gastos usa de referencia y el que puso la obra en ejecucion.
 * Elegirlo es una regla de negocio y por eso se decide aca y no en la pantalla:
 * si lo eligiera el frontend, el Tablero y esta vista podrian mostrar numeros
 * distintos de la misma obra.
 */
public record ObraPresupuestada(
        Long idObra,
        String direccionObra,
        String nombreCliente,
        String tipoObra,
        String estadoObra,

        /** El presupuesto que manda hoy en esta obra. Nunca es null. */
        Long idPresupuestoVigente,
        String tipoVigente,
        String estadoVigente,
        BigDecimal totalVigente,

        /** Todas las instancias, en el orden del circuito. */
        List<PresupuestoRespuesta> presupuestos) {

    /**
     * El orden en que se recorre el circuito.
     *
     * Se ordena por esto y no por fecha: el anteproyecto siempre va antes que
     * el definitivo aunque se haya cargado despues, porque asi se lee la
     * historia de la negociacion.
     */
    private static final List<String> ORDEN_DEL_CIRCUITO = List.of(
            Presupuesto.TIPO_COTIZACION_INICIAL,
            Presupuesto.TIPO_ANTEPROYECTO,
            Presupuesto.TIPO_DEFINITIVO,
            Presupuesto.TIPO_ADICIONAL);

    public static ObraPresupuestada de(Obra obra, List<Presupuesto> presupuestos) {
        List<Presupuesto> ordenados = presupuestos.stream()
                .sorted(Comparator
                        .comparingInt((Presupuesto p) ->
                                ORDEN_DEL_CIRCUITO.indexOf(p.getTipoPresupuesto()))
                        .thenComparing(Presupuesto::getVersion))
                .toList();

        Presupuesto vigente = elVigente(ordenados);

        return new ObraPresupuestada(
                obra.getIdObra(),
                obra.getDireccionObra(),
                obra.getCliente().getNombreApellido(),
                obra.getTipoObra(),
                obra.getEstado(),
                vigente.getIdPresupuesto(),
                vigente.getTipoPresupuesto(),
                vigente.getEstado(),
                vigente.getTotalPresupuesto(),
                ordenados.stream().map(PresupuestoRespuesta::resumida).toList());
    }

    /**
     * Cual de todos gobierna la obra.
     *
     * 1. El definitivo aprobado, si existe. Es el que el cliente acepto, el que
     *    puso la obra en ejecucion y contra el que Gastos compara.
     * 2. Si no hay, el ultimo del circuito que siga en juego. Un presupuesto
     *    rechazado no representa a la obra: si el definitivo se rechazo, lo
     *    que sigue en pie es el anteproyecto.
     * 3. Si estan todos rechazados, el ultimo igual, para no devolver null.
     *
     * Se toma el de mayor version dentro del tipo porque la lista ya viene
     * ordenada por circuito y despues por version.
     */
    private static Presupuesto elVigente(List<Presupuesto> ordenados) {
        return ultimo(ordenados.stream().filter(p -> p.esDefinitivo() && p.estaAprobado()))
                .or(() -> ultimo(ordenados.stream().filter(p -> !p.estaCerrado() || p.estaAprobado())))
                .orElseGet(() -> ordenados.get(ordenados.size() - 1));
    }

    /** El ultimo de un flujo ya ordenado. `reduce` evita comparar por fecha, que puede faltar. */
    private static java.util.Optional<Presupuesto> ultimo(
            java.util.stream.Stream<Presupuesto> flujo) {
        return flujo.reduce((anterior, siguiente) -> siguiente);
    }
}
