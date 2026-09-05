package com.sigco.seguimiento;

import com.sigco.common.exception.RecursoNoEncontradoException;
import com.sigco.common.exception.ReglaDeNegocioException;
import com.sigco.gastos.GastoService;
import com.sigco.obras.Obra;
import com.sigco.obras.ObraRepository;
import com.sigco.seguimiento.dto.SeguimientoDtos.AvanceObra;
import com.sigco.seguimiento.dto.SeguimientoDtos.ConfiguracionHitos;
import com.sigco.seguimiento.dto.SeguimientoDtos.Cumplimiento;
import com.sigco.seguimiento.dto.SeguimientoDtos.HitoRespuesta;
import com.sigco.seguimiento.dto.SeguimientoDtos.HitoSolicitud;
import com.sigco.seguimiento.dto.SeguimientoDtos.NuevaPlantilla;
import com.sigco.seguimiento.dto.SeguimientoDtos.ObservacionHito;
import com.sigco.seguimiento.dto.SeguimientoDtos.PlantillaRespuesta;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reglas del modulo Seguimiento de Obras.
 *
 * Reemplaza el cronograma que hoy se arma al inicio y no se actualiza, y el
 * avance que se evalua de memoria durante las visitas del dueño. Convierte eso
 * en un porcentaje objetivo, y lo cruza contra el avance financiero para
 * detectar obras que consumieron presupuesto sin avanzar en la misma medida.
 */
@Service
public class SeguimientoService {

    private static final BigDecimal CIEN = new BigDecimal("100");

    /**
     * Margen a partir del cual el desfasaje entre avance financiero y fisico se
     * marca como alerta. El informe lo sugiere: "por ejemplo, mas de quince
     * puntos porcentuales de diferencia".
     */
    private static final BigDecimal MARGEN_DESFASAJE = new BigDecimal("15");

    private final HitoRepository repositorio;
    private final PlantillaHitoRepository plantillaRepositorio;
    private final ObraRepository obraRepositorio;
    private final GastoService gastoService;

    public SeguimientoService(HitoRepository repositorio,
                              PlantillaHitoRepository plantillaRepositorio,
                              ObraRepository obraRepositorio,
                              GastoService gastoService) {
        this.repositorio = repositorio;
        this.plantillaRepositorio = plantillaRepositorio;
        this.obraRepositorio = obraRepositorio;
        this.gastoService = gastoService;
    }

    // ------------------------------------------------------------------
    //  Configuracion de hitos
    // ------------------------------------------------------------------

    /**
     * Define el conjunto completo de hitos de una obra.
     *
     * Reemplaza los que hubiera. Se hace de una vez y no de a uno porque la
     * regla de las ponderaciones aplica al conjunto: la suma tiene que dar 100.
     * Agregandolos de a uno, la obra quedaria en un estado invalido entre altas
     * y el avance calculado en el medio no significaria nada.
     */
    @Transactional
    public List<HitoRespuesta> configurar(Long idObra, ConfiguracionHitos configuracion) {
        Obra obra = buscarObraOFallar(idObra);

        if (!obra.estaEnEjecucion()) {
            throw new ReglaDeNegocioException(
                    "La obra está " + obra.getEstado().toLowerCase()
                    + " y solo se definen hitos de obras en ejecución.");
        }

        exigirQueSumeCien(configuracion.hitos());
        exigirNombresYOrdenesUnicos(configuracion.hitos());

        // Si ya habia hitos completados, redefinir el conjunto borraria ese
        // registro historico. El informe no lo contempla, pero es la misma
        // logica que en el resto del sistema: lo que ya paso no se pisa.
        List<Hito> existentes = repositorio.deLaObra(idObra);
        if (existentes.stream().anyMatch(Hito::estaCompletado)) {
            throw new ReglaDeNegocioException(
                    "La obra ya tiene hitos completados. Redefinir el plan borraría "
                    + "ese registro. Si hay que cambiarlo, hacelo antes de completar el primero.");
        }

        repositorio.borrarDeLaObra(idObra);

        List<Hito> nuevos = configuracion.hitos().stream()
                .map(h -> new Hito(obra, h.nombreHito().trim(), h.ponderacion(), h.orden()))
                .toList();

        return repositorio.saveAll(nuevos).stream()
                .sorted((a, b) -> a.getOrden().compareTo(b.getOrden()))
                .map(HitoRespuesta::desde)
                .toList();
    }

    /**
     * La suma de las ponderaciones tiene que ser exactamente 100.
     *
     * Es la regla que hace que el porcentaje de avance signifique algo. Con 90
     * el avance nunca llegaria a completo; con 110 una obra a medias podria
     * mostrar 100%.
     */
    private void exigirQueSumeCien(List<HitoSolicitud> hitos) {
        BigDecimal suma = hitos.stream()
                .map(HitoSolicitud::ponderacion)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (suma.compareTo(CIEN) != 0) {
            throw new ReglaDeNegocioException(
                    "Las ponderaciones suman " + suma.stripTrailingZeros().toPlainString()
                    + "% y tienen que sumar exactamente 100%.");
        }
    }

    private void exigirNombresYOrdenesUnicos(List<HitoSolicitud> hitos) {
        long nombres = hitos.stream()
                .map(h -> h.nombreHito().trim().toLowerCase()).distinct().count();
        if (nombres != hitos.size()) {
            throw new ReglaDeNegocioException("Hay dos hitos con el mismo nombre.");
        }

        long ordenes = hitos.stream().map(HitoSolicitud::orden).distinct().count();
        if (ordenes != hitos.size()) {
            throw new ReglaDeNegocioException("Hay dos hitos con el mismo número de orden.");
        }
    }

    // ------------------------------------------------------------------
    //  Cumplimiento
    // ------------------------------------------------------------------

    /**
     * Marca un hito como completado.
     *
     * Al completar el ULTIMO pendiente, la obra pasa a Finalizada. Es el cierre
     * del ciclo de vida que arranco cuando se aprobo el presupuesto definitivo
     * y la obra paso a En ejecucion.
     */
    @Transactional
    public AvanceObra completar(Long idHito, Cumplimiento cumplimiento) {
        Hito hito = repositorio.findById(idHito)
                .orElseThrow(() -> new RecursoNoEncontradoException("Hito", idHito));

        Obra obra = hito.getObra();

        if (obra.estaFinalizada()) {
            throw new ReglaDeNegocioException(
                    "La obra ya está finalizada y sus hitos quedaron bloqueados.");
        }
        if (hito.estaCompletado()) {
            throw new ReglaDeNegocioException("El hito ya está completado.");
        }

        List<Hito> hitos = repositorio.deLaObra(obra.getIdObra());

        // El informe: "No se puede completar un hito posterior si hay hitos
        // anteriores sin completar, SALVO que el dueño lo habilite de forma
        // explicita, ya que en la practica algunas tareas pueden adelantarse".
        if (!cumplimiento.forzarOFalso()) {
            hitos.stream()
                    .filter(h -> h.getOrden() < hito.getOrden() && !h.estaCompletado())
                    .findFirst()
                    .ifPresent(anterior -> {
                        throw new ReglaDeNegocioException(
                                "Falta completar \"" + anterior.getNombreHito()
                                + "\", que va antes. Si esta etapa se adelantó, "
                                + "confirmá que querés saltearlo.");
                    });
        }

        hito.completar(cumplimiento.fechaCumplimiento(), limpiar(cumplimiento.observacion()));

        // Se relee del objeto en memoria: hitos trae la misma instancia.
        boolean quedaAlgunoPendiente = hitos.stream().anyMatch(h -> !h.estaCompletado());
        if (!quedaAlgunoPendiente) {
            obra.finalizar();
        }

        return armarAvance(obra, hitos);
    }

    /** Deshace un cumplimiento marcado por error. */
    @Transactional
    public AvanceObra reabrir(Long idHito) {
        Hito hito = repositorio.findById(idHito)
                .orElseThrow(() -> new RecursoNoEncontradoException("Hito", idHito));

        if (!hito.estaCompletado()) {
            throw new ReglaDeNegocioException("El hito no está completado.");
        }
        if (hito.getObra().estaFinalizada()) {
            throw new ReglaDeNegocioException(
                    "La obra está finalizada. Reabrir un hito exige antes reabrir la obra.");
        }

        hito.reabrir();
        return armarAvance(hito.getObra(), repositorio.deLaObra(hito.getObra().getIdObra()));
    }

    @Transactional
    public AvanceObra registrarObservacion(Long idHito, ObservacionHito observacion) {
        Hito hito = repositorio.findById(idHito)
                .orElseThrow(() -> new RecursoNoEncontradoException("Hito", idHito));

        hito.registrarObservacion(observacion.observacion().trim());
        return armarAvance(hito.getObra(), repositorio.deLaObra(hito.getObra().getIdObra()));
    }

    // ------------------------------------------------------------------
    //  Avance
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public AvanceObra avance(Long idObra) {
        Obra obra = buscarObraOFallar(idObra);
        return armarAvance(obra, repositorio.deLaObra(idObra));
    }

    /**
     * Arma el panel de avance.
     *
     * El avance FISICO sale de sumar las ponderaciones de los hitos completados
     * y nada mas: sin estimaciones intermedias, para que el numero sea siempre
     * objetivo.
     *
     * El FINANCIERO viene de Gastos. Si la obra no tiene presupuesto aprobado no
     * se puede calcular, y en ese caso se informa cero en lugar de fallar: el
     * avance fisico sigue siendo valido por si solo.
     */
    private AvanceObra armarAvance(Obra obra, List<Hito> hitos) {
        BigDecimal avanceFisico = hitos.stream()
                .filter(Hito::estaCompletado)
                .map(Hito::getPonderacion)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Devuelve cero si la obra no tiene definitivo aprobado. NO se usa
        // estadoFinanciero() atrapando su excepcion: ese metodo es
        // @Transactional y al lanzar marca la transaccion como rollback-only,
        // asi que el panel entero fallaba al confirmar.
        BigDecimal avanceFinanciero = gastoService.porcentajeConsumidoOCero(obra.getIdObra());

        BigDecimal desfasaje = avanceFinanciero.subtract(avanceFisico);

        LocalDate fin = obra.getFechaFinEstimada();
        Long diasParaElPlazo = fin != null
                ? ChronoUnit.DAYS.between(LocalDate.now(), fin) : null;

        // Atrasada: se paso la fecha estimada y todavia queda obra por hacer.
        boolean atrasada = diasParaElPlazo != null
                && diasParaElPlazo < 0
                && avanceFisico.compareTo(CIEN) < 0;

        return new AvanceObra(
                obra.getIdObra(), obra.getDireccionObra(), obra.getEstado(),
                avanceFisico, avanceFinanciero, desfasaje,
                desfasaje.compareTo(MARGEN_DESFASAJE) > 0,
                fin, diasParaElPlazo, atrasada,
                (int) hitos.stream().filter(Hito::estaCompletado).count(),
                hitos.size(),
                hitos.stream().map(HitoRespuesta::desde).toList());
    }

    // ------------------------------------------------------------------
    //  Plantillas
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<PlantillaRespuesta> listarPlantillas() {
        return plantillaRepositorio.todasConDetalles().stream()
                .map(PlantillaRespuesta::desde)
                .toList();
    }

    @Transactional
    public PlantillaRespuesta crearPlantilla(NuevaPlantilla solicitud) {
        exigirQueSumeCien(solicitud.etapas());
        exigirNombresYOrdenesUnicos(solicitud.etapas());

        PlantillaHito plantilla = new PlantillaHito(solicitud.nombrePlantilla().trim());
        solicitud.etapas().forEach(e -> plantilla.agregarDetalle(
                new PlantillaHitoDetalle(plantilla, e.nombreHito().trim(),
                                         e.ponderacion(), e.orden())));

        return PlantillaRespuesta.desde(plantillaRepositorio.save(plantilla));
    }

    /**
     * Aplica una plantilla a una obra: crea sus hitos a partir de las etapas.
     *
     * Pasa por la misma validacion que la configuracion manual. Una plantilla
     * guardada ya cierra en 100, pero validarla igual evita que un cambio futuro
     * en los datos se cuele sin control.
     */
    @Transactional
    public List<HitoRespuesta> aplicarPlantilla(Long idObra, Long idPlantilla) {
        PlantillaHito plantilla = plantillaRepositorio.buscarCompleta(idPlantilla)
                .orElseThrow(() -> new RecursoNoEncontradoException("Plantilla", idPlantilla));

        List<HitoSolicitud> hitos = plantilla.getDetalles().stream()
                .map(d -> new HitoSolicitud(d.getNombreHito(), d.getPonderacion(), d.getOrden()))
                .toList();

        return configurar(idObra, new ConfiguracionHitos(hitos));
    }

    // ------------------------------------------------------------------

    private Obra buscarObraOFallar(Long idObra) {
        return obraRepositorio.findById(idObra)
                .orElseThrow(() -> new RecursoNoEncontradoException("Obra", idObra));
    }

    private String limpiar(String texto) {
        return (texto == null || texto.isBlank()) ? null : texto.trim();
    }
}
