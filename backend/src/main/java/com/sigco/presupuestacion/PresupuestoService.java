package com.sigco.presupuestacion;

import com.sigco.common.exception.RecursoNoEncontradoException;
import com.sigco.common.exception.ReglaDeNegocioException;
import com.sigco.materiales.Material;
import com.sigco.materiales.MaterialRepository;
import com.sigco.obras.Obra;
import com.sigco.obras.ObraRepository;
import com.sigco.presupuestacion.dto.PresupuestoDtos.CambioEstadoPresupuesto;
import com.sigco.presupuestacion.dto.PresupuestoDtos.Duplicacion;
import com.sigco.presupuestacion.dto.PresupuestoDtos.ItemSolicitud;
import com.sigco.presupuestacion.dto.PresupuestoDtos.NuevoPresupuesto;
import com.sigco.presupuestacion.dto.PresupuestoDtos.PlanDePago;
import com.sigco.presupuestacion.dto.PresupuestoRespuesta;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Logica del modulo Presupuestacion.
 *
 * Concentra el circuito que releva el informe:
 *
 *   Cotizacion inicial ──► Anteproyecto (solo reformas) ──► Definitivo
 *                                                              │
 *                                                              └──► Adicional
 *
 * y el ciclo de negociacion de cada version:
 *
 *   Borrador ──► Enviado ──► Aprobado / Rechazado
 *      └────────────────────► Rechazado
 *
 * Al aprobarse un presupuesto definitivo, la obra pasa a "En ejecucion".
 */
@Service
public class PresupuestoService {

    private static final long TODAS_LAS_OBRAS = 0L;

    private final GeneradorDePdf generadorDePdf;
    private final PresupuestoRepository repositorio;
    private final ItemPresupuestoRepository itemRepositorio;
    private final ObraRepository obraRepositorio;
    private final MaterialRepository materialRepositorio;
    private final RubroRepository rubroRepositorio;
    private final SubrubroRepository subrubroRepositorio;

    public PresupuestoService(PresupuestoRepository repositorio,
                              ItemPresupuestoRepository itemRepositorio,
                              ObraRepository obraRepositorio,
                              MaterialRepository materialRepositorio,
                              RubroRepository rubroRepositorio,
                              SubrubroRepository subrubroRepositorio,
                              GeneradorDePdf generadorDePdf) {
        this.generadorDePdf = generadorDePdf;
        this.repositorio = repositorio;
        this.itemRepositorio = itemRepositorio;
        this.obraRepositorio = obraRepositorio;
        this.materialRepositorio = materialRepositorio;
        this.rubroRepositorio = rubroRepositorio;
        this.subrubroRepositorio = subrubroRepositorio;
    }

    // ------------------------------------------------------------------
    //  Consulta
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<PresupuestoRespuesta> listar(Long idObra, String tipo, String estado) {
        return repositorio.buscar(
                        idObra != null ? idObra : TODAS_LAS_OBRAS,
                        sinFiltro(tipo),
                        sinFiltro(estado))
                .stream()
                .map(PresupuestoRespuesta::resumida)
                .toList();
    }

    @Transactional(readOnly = true)
    public PresupuestoRespuesta obtener(Long id) {
        return PresupuestoRespuesta.completa(buscarCompletoOFallar(id));
    }

    /**
     * Arma el PDF del presupuesto para enviarle al cliente.
     *
     * La consulta trae la obra, el cliente y los items en una sola vez, porque
     * el generador los recorre todos. Sin eso, armar el documento dispararia
     * una consulta por item.
     *
     * El PDF no se guarda en ningun lado: se regenera a partir de los datos
     * cada vez que se lo pide. Guardarlo obligaria a regenerarlo ante cualquier
     * cambio y a resolver donde almacenarlo, sin ninguna ventaja: el documento
     * pesa unos pocos kilobytes y se arma en milisegundos.
     */
    @Transactional(readOnly = true)
    public byte[] generarPdf(Long id) {
        return generadorDePdf.generar(buscarCompletoOFallar(id));
    }

    // ------------------------------------------------------------------
    //  Alta
    // ------------------------------------------------------------------

    /**
     * Crea una version de presupuesto para una obra.
     *
     * Acá se aplican las tres reglas de circuito del informe:
     *
     *  1. No hay presupuesto sin obra.
     *  2. En construccion nueva no se habilita el anteproyecto: el tipo de obra
     *     ya determina que ese paso no corresponde.
     *  3. No hay definitivo de una reforma sin anteproyecto previo.
     */
    @Transactional
    public PresupuestoRespuesta crear(NuevoPresupuesto solicitud) {
        Obra obra = obraRepositorio.findById(solicitud.idObra())
                .orElseThrow(() -> new RecursoNoEncontradoException("Obra", solicitud.idObra()));

        String tipo = solicitud.tipoPresupuesto();
        validarCircuito(obra, tipo);

        Presupuesto base = null;
        if (solicitud.idPresupuestoBase() != null) {
            base = buscarOFallar(solicitud.idPresupuestoBase());
        }

        int version = repositorio.ultimaVersion(obra.getIdObra(), tipo) + 1;

        Presupuesto presupuesto = new Presupuesto(
                obra, tipo, version, base, normalizar(solicitud.plazoEstimadoObra()));

        // La cotizacion inicial es la unica instancia sin items: su precio sale
        // de multiplicar la superficie por un valor de referencia.
        if (presupuesto.esCotizacionInicial()) {
            exigirDatosDeCotizacion(solicitud);
            presupuesto.calcularCotizacionInicial(
                    solicitud.metrosCuadrados(), solicitud.valorPorM2());
        }

        return PresupuestoRespuesta.completa(repositorio.save(presupuesto));
    }

    /**
     * Duplica un presupuesto como punto de partida de otro.
     *
     * Es la operacion que resuelve el versionado real del informe: al armar el
     * definitivo a partir del anteproyecto, el anteproyecto NO se sobrescribe.
     * Se crea un presupuesto nuevo con una COPIA de sus items, y los dos quedan
     * como registros independientes vinculados por presupuestoBase.
     *
     * Sirve tambien para reutilizar un presupuesto de otra obra como plantilla,
     * indicando la obra destino.
     */
    @Transactional
    public PresupuestoRespuesta duplicar(Long idOrigen, Duplicacion solicitud) {
        Presupuesto origen = buscarCompletoOFallar(idOrigen);

        Obra obraDestino = solicitud.idObraDestino() != null
                ? obraRepositorio.findById(solicitud.idObraDestino())
                        .orElseThrow(() -> new RecursoNoEncontradoException(
                                "Obra", solicitud.idObraDestino()))
                : origen.getObra();

        String tipo = solicitud.tipoPresupuesto();
        validarCircuito(obraDestino, tipo);

        int version = repositorio.ultimaVersion(obraDestino.getIdObra(), tipo) + 1;

        Presupuesto copia = new Presupuesto(
                obraDestino, tipo, version, origen, origen.getPlazoEstimadoObra());

        // Se copian los items uno por uno. No se reutilizan los del origen:
        // son registros nuevos, que despues se pueden modificar sin tocar el
        // presupuesto del que salieron.
        for (ItemPresupuesto item : origen.getItems()) {
            copia.agregarItem(new ItemPresupuesto(
                    copia, item.getRubro(), item.getSubrubro(), item.getMaterial(),
                    item.getDescripcion(), item.getUnidadMedida(),
                    item.getCantidad(), item.getValorUnitario()));
        }

        return PresupuestoRespuesta.completa(repositorio.save(copia));
    }

    // ------------------------------------------------------------------
    //  Items
    // ------------------------------------------------------------------

    @Transactional
    public PresupuestoRespuesta agregarItem(Long idPresupuesto, ItemSolicitud solicitud) {
        Presupuesto presupuesto = buscarCompletoOFallar(idPresupuesto);
        exigirBorrador(presupuesto);
        exigirQueLleveItems(presupuesto);

        Rubro rubro = buscarRubroOFallar(solicitud.idRubro());
        Subrubro subrubro = resolverSubrubro(solicitud.idSubrubro(), rubro);
        Material material = resolverMaterial(solicitud.idMaterial(), rubro);

        presupuesto.agregarItem(new ItemPresupuesto(
                presupuesto, rubro, subrubro, material, solicitud.descripcion().trim(),
                solicitud.unidadMedida().trim(), solicitud.cantidad(), solicitud.valorUnitario()));

        return PresupuestoRespuesta.completa(presupuesto);
    }

    @Transactional
    public PresupuestoRespuesta actualizarItem(Long idPresupuesto, Long idItem,
                                               ItemSolicitud solicitud) {
        Presupuesto presupuesto = buscarCompletoOFallar(idPresupuesto);
        exigirBorrador(presupuesto);

        ItemPresupuesto item = buscarItemOFallar(presupuesto, idItem);
        Rubro rubro = buscarRubroOFallar(solicitud.idRubro());
        Subrubro subrubro = resolverSubrubro(solicitud.idSubrubro(), rubro);
        Material material = resolverMaterial(solicitud.idMaterial(), rubro);

        item.actualizar(rubro, subrubro, material, solicitud.descripcion().trim(),
                solicitud.unidadMedida().trim(), solicitud.cantidad(), solicitud.valorUnitario());
        presupuesto.recalcularTotal();

        return PresupuestoRespuesta.completa(presupuesto);
    }

    /**
     * Quita un item del presupuesto.
     *
     * No contradice la regla de que un presupuesto no se elimina: esa regla
     * habla del presupuesto completo. Un item solo se puede quitar mientras el
     * presupuesto esta en Borrador, es decir antes de mostrarselo al cliente.
     */
    @Transactional
    public PresupuestoRespuesta quitarItem(Long idPresupuesto, Long idItem) {
        Presupuesto presupuesto = buscarCompletoOFallar(idPresupuesto);
        exigirBorrador(presupuesto);

        presupuesto.quitarItem(buscarItemOFallar(presupuesto, idItem));

        return PresupuestoRespuesta.completa(presupuesto);
    }

    // ------------------------------------------------------------------
    //  Plan de pago
    // ------------------------------------------------------------------

    /**
     * Define el anticipo y las cuotas.
     *
     * El informe pide que anticipo mas cuotas cierren al 100% del total. Con
     * este modelo eso se cumple por construccion: el anticipo es un porcentaje
     * del total y las cuotas reparten exactamente el saldo restante.
     *
     * Lo que si hay que impedir es el caso que NO cierra: un anticipo menor al
     * 100% sin ninguna cuota, que dejaria una parte del total sin forma de
     * cobrarse.
     */
    @Transactional
    public PresupuestoRespuesta definirPlanDePago(Long id, PlanDePago plan) {
        Presupuesto presupuesto = buscarCompletoOFallar(id);
        exigirBorrador(presupuesto);

        boolean anticipoCubreTodo = plan.anticipoPorcentaje().compareTo(
                java.math.BigDecimal.valueOf(100)) == 0;

        if (!anticipoCubreTodo && plan.cantidadCuotas() == 0) {
            throw new ReglaDeNegocioException(
                    "El plan de pago no cierra: con un anticipo del "
                    + plan.anticipoPorcentaje() + "% hace falta al menos una cuota "
                    + "para cobrar el saldo restante.");
        }

        if (anticipoCubreTodo && plan.cantidadCuotas() > 0) {
            throw new ReglaDeNegocioException(
                    "El plan de pago no cierra: si el anticipo es del 100% no queda "
                    + "saldo para dividir en cuotas.");
        }

        presupuesto.definirPlanDePago(
                plan.anticipoPorcentaje(), plan.cantidadCuotas(),
                normalizar(plan.plazoEstimadoObra()));

        return PresupuestoRespuesta.completa(presupuesto);
    }

    // ------------------------------------------------------------------
    //  Estados
    // ------------------------------------------------------------------

    /**
     * Avanza el estado de la negociacion con el cliente.
     *
     * TODO: al integrar el modulo Accesos, restringir la aprobacion al rol
     *       dueño. El informe la define como una decision no delegable.
     */
    @Transactional
    public PresupuestoRespuesta cambiarEstado(Long id, CambioEstadoPresupuesto cambio) {
        Presupuesto presupuesto = buscarCompletoOFallar(id);

        if (presupuesto.estaCerrado()) {
            throw new ReglaDeNegocioException(
                    "El presupuesto ya esta " + presupuesto.getEstado().toLowerCase()
                    + " y no admite cambios de estado.");
        }

        switch (cambio.estado()) {
            case Presupuesto.ESTADO_ENVIADO -> enviar(presupuesto);
            case Presupuesto.ESTADO_APROBADO -> aprobar(presupuesto);
            case Presupuesto.ESTADO_RECHAZADO -> presupuesto.rechazar();
            default -> throw new ReglaDeNegocioException(
                    "Estado no reconocido: " + cambio.estado());
        }

        return PresupuestoRespuesta.completa(presupuesto);
    }

    private void enviar(Presupuesto presupuesto) {
        if (!presupuesto.esBorrador()) {
            throw new ReglaDeNegocioException("Solo un presupuesto en borrador se puede enviar.");
        }
        if (presupuesto.llevaItems() && presupuesto.getItems().isEmpty()) {
            throw new ReglaDeNegocioException(
                    "No se puede enviar un presupuesto sin ningún ítem cargado.");
        }
        presupuesto.enviar();
    }

    /**
     * Aprueba el presupuesto y, si es el definitivo, pone la obra en ejecucion.
     *
     * Ese cambio automatico es el que pide el informe: aprobar el definitivo es
     * lo que da inicio a la obra, y encadenarlo evita que el dueño tenga que
     * acordarse de actualizar el estado a mano.
     */
    private void aprobar(Presupuesto presupuesto) {
        // Que la obra tenga cliente lo garantiza el modelo: id_cliente es NOT
        // NULL en la tabla obra, asi que no existe obra sin cliente valido.

        if (presupuesto.esDefinitivo()) {
            exigirUnSoloDefinitivoAprobado(presupuesto);
        }

        presupuesto.aprobar();

        if (presupuesto.esDefinitivo() && presupuesto.getObra().estaEnPresupuestacion()) {
            presupuesto.getObra().pasarAEjecucion();
        }
    }

    // ------------------------------------------------------------------
    //  Baja
    // ------------------------------------------------------------------

    /**
     * Elimina un presupuesto de forma definitiva.
     *
     * ATENCION: el informe establece que un presupuesto no se elimina, solo se
     * marca Rechazado. Esta operacion es una extension pedida expresamente para
     * poder hacer pruebas sin arrastrar registros, e incluye los aprobados.
     *
     * Queda un unico bloqueo, y no es una regla de negocio sino integridad
     * referencial: si otro presupuesto lo tiene como base, borrarlo cortaria la
     * cadena de versionado y el derivado quedaria sin origen. La clave foranea
     * de la base lo rechazaria igual; el control existe para devolver un 409
     * con un mensaje util en lugar de un error de restriccion.
     *
     * Los items se van con el presupuesto: la relacion es cascade = ALL con
     * orphanRemoval, porque un item no existe fuera de su presupuesto.
     */
    @Transactional
    public void eliminar(Long id) {
        Presupuesto presupuesto = repositorio.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Presupuesto", id));

        if (repositorio.existsByPresupuestoBaseIdPresupuesto(id)) {
            throw new ReglaDeNegocioException(
                    "No se puede eliminar: otro presupuesto se generó a partir de este. "
                    + "Eliminá primero el que deriva de él.");
        }

        deshacerEfectoSobreLaObra(presupuesto);

        repositorio.delete(presupuesto);
    }

    /**
     * Devuelve la obra a presupuestacion si el que se elimina era el definitivo
     * aprobado que la habia puesto en ejecucion.
     *
     * Aprobar un definitivo arrastra un cambio de estado de la obra. Eliminarlo
     * sin deshacer ese cambio dejaria una obra "En ejecucion" sin ningun
     * presupuesto aprobado detras: Gastos no tendria contra que comparar y el
     * listado de obras mostraria un estado que ningun registro justifica.
     *
     * Solo se revierte si no queda otro definitivo aprobado en la obra, y solo
     * desde "En ejecucion". Una obra Finalizada o Cancelada se deja como esta:
     * llego a ese estado por otro camino (el ultimo hito, o una decision del
     * dueño) y no le corresponde a esta operacion deshacerlo.
     */
    private void deshacerEfectoSobreLaObra(Presupuesto presupuesto) {
        if (!presupuesto.esDefinitivo() || !presupuesto.estaAprobado()) {
            return;
        }

        Obra obra = presupuesto.getObra();

        if (!obra.estaEnEjecucion()) {
            return;
        }

        boolean quedaOtroAprobado = repositorio
                .findByObraIdObraAndTipoPresupuestoAndEstado(
                        obra.getIdObra(),
                        Presupuesto.TIPO_DEFINITIVO,
                        Presupuesto.ESTADO_APROBADO)
                .stream()
                .anyMatch(otro -> !otro.getIdPresupuesto().equals(presupuesto.getIdPresupuesto()));

        if (!quedaOtroAprobado) {
            obra.volverAPresupuestacion();
        }
    }

    // ------------------------------------------------------------------
    //  Reglas del circuito
    // ------------------------------------------------------------------

    private void validarCircuito(Obra obra, String tipo) {
        if (Presupuesto.TIPO_ANTEPROYECTO.equals(tipo)) {
            // El tipo de obra determina el circuito: en construccion nueva no
            // hay planos de arquitecto previos que presupuestar por rubro.
            if (!Obra.TIPO_REFORMA.equals(obra.getTipoObra())) {
                throw new ReglaDeNegocioException(
                        "El anteproyecto corresponde solo a las reformas. "
                        + "Esta obra es una construcción.");
            }
        }

        if (Presupuesto.TIPO_DEFINITIVO.equals(tipo)
                && Obra.TIPO_REFORMA.equals(obra.getTipoObra())) {

            long anteproyectos = repositorio.countByObraIdObraAndTipoPresupuesto(
                    obra.getIdObra(), Presupuesto.TIPO_ANTEPROYECTO);

            if (anteproyectos == 0) {
                throw new ReglaDeNegocioException(
                        "No se puede generar el presupuesto definitivo de una reforma "
                        + "sin un anteproyecto previo para esta obra.");
            }
        }

        if (Presupuesto.TIPO_ADICIONAL.equals(tipo)) {
            List<Presupuesto> definitivosAprobados =
                    repositorio.findByObraIdObraAndTipoPresupuestoAndEstado(
                            obra.getIdObra(), Presupuesto.TIPO_DEFINITIVO,
                            Presupuesto.ESTADO_APROBADO);

            if (definitivosAprobados.isEmpty()) {
                throw new ReglaDeNegocioException(
                        "Un adicional se genera sobre un presupuesto definitivo ya "
                        + "aprobado, y esta obra todavía no tiene ninguno.");
            }
        }
    }

    /**
     * Una obra no puede tener dos presupuestos definitivos aprobados.
     *
     * El informe no lo dice con estas palabras, pero si insiste en que tiene
     * que quedar claro cual version aprobo el cliente. Con dos aprobados esa
     * pregunta no tendria respuesta, y ademas Gastos no sabria contra cual
     * comparar. Los cambios posteriores se cargan como Adicional.
     */
    private void exigirUnSoloDefinitivoAprobado(Presupuesto presupuesto) {
        List<Presupuesto> yaAprobados = repositorio.findByObraIdObraAndTipoPresupuestoAndEstado(
                presupuesto.getObra().getIdObra(),
                Presupuesto.TIPO_DEFINITIVO,
                Presupuesto.ESTADO_APROBADO);

        if (!yaAprobados.isEmpty()) {
            throw new ReglaDeNegocioException(
                    "La obra ya tiene un presupuesto definitivo aprobado (versión "
                    + yaAprobados.get(0).getVersion() + "). "
                    + "Para un cambio, generá un presupuesto adicional.");
        }
    }

    /**
     * El subrubro elegido tiene que pertenecer al rubro del item.
     *
     * Es una de las reglas explicitas del informe. Sin ella se podria cargar un
     * item de rubro Albanileria con subrubro Desagues, y el agrupamiento por
     * rubro dejaria de tener sentido.
     */
    /**
     * El material elegido tiene que pertenecer al rubro del item.
     *
     * Es la misma regla que ya se aplica al subrubro y por el mismo motivo: si
     * un item de rubro Albanileria pudiera referir a un material de Plomeria,
     * el agrupamiento por rubro dejaria de significar algo, y Gastos compararia
     * contra un presupuesto mal clasificado.
     *
     * Tambien se rechaza un material inactivo: si se dio de baja del catalogo,
     * no deberia poder entrar en un presupuesto nuevo. Los items ya cargados
     * que lo referencian no se tocan, porque son historia.
     */
    private Material resolverMaterial(Long idMaterial, Rubro rubro) {
        if (idMaterial == null) {
            return null;
        }

        Material material = materialRepositorio.findById(idMaterial)
                .orElseThrow(() -> new RecursoNoEncontradoException("Material", idMaterial));

        if (!material.getRubro().getIdRubro().equals(rubro.getIdRubro())) {
            throw new ReglaDeNegocioException(
                    "El material \"" + material.getNombreMaterial() + "\" pertenece al rubro "
                    + material.getRubro().getNombreRubro() + ", no a "
                    + rubro.getNombreRubro() + ".");
        }

        if (!material.estaActivo()) {
            throw new ReglaDeNegocioException(
                    "El material \"" + material.getNombreMaterial()
                    + "\" está inactivo en el catálogo y no se puede usar en un presupuesto.");
        }

        return material;
    }

    private Subrubro resolverSubrubro(Long idSubrubro, Rubro rubro) {
        if (idSubrubro == null) {
            return null;
        }

        Subrubro subrubro = subrubroRepositorio.findById(idSubrubro)
                .orElseThrow(() -> new RecursoNoEncontradoException("Subrubro", idSubrubro));

        if (!subrubro.getRubro().getIdRubro().equals(rubro.getIdRubro())) {
            throw new ReglaDeNegocioException(
                    "El subrubro " + subrubro.getNombreSubrubro() + " pertenece al rubro "
                    + subrubro.getRubro().getNombreRubro() + ", no a " + rubro.getNombreRubro() + ".");
        }

        return subrubro;
    }

    // ------------------------------------------------------------------

    /**
     * Un presupuesto solo se modifica mientras esta en Borrador.
     *
     * Una vez enviado al cliente, su contenido queda congelado: si despues hay
     * cambios, se genera una version nueva. Es lo que evita el problema del
     * relevamiento, donde las versiones se pisan y no queda registro de que se
     * le mostro al cliente.
     */
    private void exigirBorrador(Presupuesto presupuesto) {
        if (!presupuesto.esBorrador()) {
            throw new ReglaDeNegocioException(
                    "El presupuesto esta " + presupuesto.getEstado().toLowerCase()
                    + " y ya no se puede modificar. Generá una versión nueva.");
        }
    }

    private void exigirQueLleveItems(Presupuesto presupuesto) {
        if (!presupuesto.llevaItems()) {
            throw new ReglaDeNegocioException(
                    "La cotización inicial no lleva ítems: su precio sale de los metros "
                    + "cuadrados por el valor de referencia.");
        }
    }

    private void exigirDatosDeCotizacion(NuevoPresupuesto solicitud) {
        if (solicitud.metrosCuadrados() == null || solicitud.valorPorM2() == null) {
            throw new ReglaDeNegocioException(
                    "La cotización inicial necesita los metros cuadrados y el valor por metro.");
        }
    }

    private ItemPresupuesto buscarItemOFallar(Presupuesto presupuesto, Long idItem) {
        ItemPresupuesto item = itemRepositorio.findById(idItem)
                .orElseThrow(() -> new RecursoNoEncontradoException("Ítem", idItem));

        // Sin esta comprobacion se podria editar el item de otro presupuesto
        // pasando su identificador en la direccion.
        if (!item.getPresupuesto().getIdPresupuesto().equals(presupuesto.getIdPresupuesto())) {
            throw new RecursoNoEncontradoException("Ítem", idItem);
        }

        return item;
    }

    private Presupuesto buscarOFallar(Long id) {
        return repositorio.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Presupuesto", id));
    }

    private Presupuesto buscarCompletoOFallar(Long id) {
        return repositorio.buscarCompleto(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Presupuesto", id));
    }

    private Rubro buscarRubroOFallar(Long id) {
        return rubroRepositorio.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Rubro", id));
    }

    private String normalizar(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        return texto.trim();
    }

    private String sinFiltro(String texto) {
        if (texto == null || texto.isBlank()) {
            return "";
        }
        return texto.trim();
    }
}
