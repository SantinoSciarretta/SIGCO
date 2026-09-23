package com.sigco.compras;

import com.sigco.common.exception.RecursoNoEncontradoException;
import com.sigco.common.exception.ReglaDeNegocioException;
import com.sigco.compras.dto.PedidoDtos.Anulacion;
import com.sigco.compras.dto.PedidoDtos.Aprobacion;
import com.sigco.compras.dto.PedidoDtos.LineaSolicitud;
import com.sigco.compras.dto.PedidoDtos.NuevoPedido;
import com.sigco.compras.dto.PedidoDtos.PrecioLinea;
import com.sigco.compras.dto.PedidoDtos.Recepcion;
import com.sigco.compras.dto.PedidoRespuesta;
import com.sigco.gastos.GastoService;
import com.sigco.materiales.Material;
import com.sigco.materiales.MaterialRepository;
import com.sigco.obras.Obra;
import com.sigco.obras.ObraRepository;
import com.sigco.proveedores.CotizacionRepository;
import com.sigco.proveedores.Proveedor;
import com.sigco.proveedores.ProveedorRepository;
import com.sigco.seguridad.SesionActual;
import com.sigco.seguridad.AlcanceDeObras;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.time.LocalDate;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reglas del circuito de compras.
 *
 * El informe describe un circuito de tres pasos con responsables distintos, y
 * la separacion importa: el capataz pide, el dueño aprueba y elige proveedor,
 * quien recibe confirma con la foto del remito. Aprobar es explicitamente no
 * delegable.
 *
 * Esa separacion ya esta hecha cumplir por el sistema: aprobar() exige el
 * permiso compras.aprobar, que solo tiene el Dueño (V13), y recibir() pasa por
 * AlcanceDeObras, que limita al capataz a las obras que tiene asignadas.
 */
@Service
public class PedidoService {

    private static final long TODAS = 0L;
    private static final String SIN_FILTRO = "";

    private final PedidoRepository repositorio;
    private final ObraRepository obraRepositorio;
    private final MaterialRepository materialRepositorio;
    private final ProveedorRepository proveedorRepositorio;
    private final CotizacionRepository cotizacionRepositorio;
    private final GastoService gastoService;

    /** Quien pide y quien recibe: los dos salen de la sesion. */
    private final SesionActual sesion;

    /** Un capataz de obra solo opera sobre los pedidos de su obra. */
    private final AlcanceDeObras alcance;

    private final com.sigco.accesos.ServicioAuditoria auditoria;

    public PedidoService(PedidoRepository repositorio,
                         ObraRepository obraRepositorio,
                         MaterialRepository materialRepositorio,
                         ProveedorRepository proveedorRepositorio,
                         CotizacionRepository cotizacionRepositorio,
                         GastoService gastoService,
                         SesionActual sesion,
                         AlcanceDeObras alcance,
                         com.sigco.accesos.ServicioAuditoria auditoria) {
        this.auditoria = auditoria;
        this.repositorio = repositorio;
        this.obraRepositorio = obraRepositorio;
        this.materialRepositorio = materialRepositorio;
        this.proveedorRepositorio = proveedorRepositorio;
        this.cotizacionRepositorio = cotizacionRepositorio;
        this.gastoService = gastoService;
        this.sesion = sesion;
        this.alcance = alcance;
    }

    // ------------------------------------------------------------------
    //  Consulta
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<PedidoRespuesta> listar(Long idObra, Long idProveedor, String estado) {
        // Ningun parametro viaja nulo: ver el comentario en PedidoRepository.
        return repositorio.buscar(
                        idObra != null ? idObra : TODAS,
                        idProveedor != null ? idProveedor : TODAS,
                        estado != null ? estado : SIN_FILTRO)
                .stream()
                // "(su obra)" de la matriz del informe: el capataz de obra ve
                // solo los pedidos de las obras que tiene asignadas.
                .filter(p -> alcance.alcanza(p.getObra().getIdObra()))
                .map(PedidoRespuesta::resumen)
                .toList();
    }

    @Transactional(readOnly = true)
    public PedidoRespuesta obtener(Long id) {
        Pedido pedido = buscarCompletoOFallar(id);
        alcance.exigirAlcance(pedido.getObra().getIdObra());
        return PedidoRespuesta.completa(pedido);
    }

    /** Pedidos esperando la aprobacion del dueño. Alimenta su panel y el Dashboard. */
    @Transactional(readOnly = true)
    public List<PedidoRespuesta> pendientesDeAprobacion() {
        return repositorio.findByEstadoOrderByFechaSolicitudAsc(Pedido.ESTADO_PENDIENTE)
                .stream()
                .map(PedidoRespuesta::resumen)
                .toList();
    }

    /**
     * Precios sugeridos para aprobar un pedido con un proveedor dado.
     *
     * Devuelve la ultima cotizacion de ese proveedor para cada material del
     * pedido. Los materiales que nunca cotizo quedan afuera del mapa y el dueño
     * los completa a mano: es mejor que proponerle un cero que podria confirmar
     * sin mirar.
     */
    @Transactional(readOnly = true)
    public Map<Long, BigDecimal> sugerirPrecios(Long idPedido, Long idProveedor) {
        Pedido pedido = buscarCompletoOFallar(idPedido);
        Map<Long, BigDecimal> sugeridos = new HashMap<>();

        for (PedidoMaterial linea : pedido.getMateriales()) {
            Long idMaterial = linea.getMaterial().getIdMaterial();
            cotizacionRepositorio.ultimaDe(idProveedor, idMaterial)
                    .ifPresent(c -> sugeridos.put(idMaterial, c.getPrecioCotizado()));
        }
        return sugeridos;
    }

    // ------------------------------------------------------------------
    //  Paso 1 — alguien en la obra arma el pedido
    // ------------------------------------------------------------------

    @Transactional
    public PedidoRespuesta crear(NuevoPedido solicitud) {
        Obra obra = obraRepositorio.findById(solicitud.idObra())
                .orElseThrow(() -> new RecursoNoEncontradoException("Obra", solicitud.idObra()));

        exigirObraOperativa(obra);
        alcance.exigirAlcance(obra.getIdObra());

        // Quien genera el pedido queda registrado: es lo que convierte el
        // pedido informal por WhatsApp en un pedido con responsable.
        Pedido pedido = new Pedido(obra, sesion.idUsuario().orElse(null));
        repositorio.save(pedido);

        Set<Long> yaAgregados = new HashSet<>();
        for (LineaSolicitud linea : solicitud.materiales()) {
            if (!yaAgregados.add(linea.idMaterial())) {
                throw new ReglaDeNegocioException(
                        "El material aparece dos veces en el pedido. "
                        + "Sumá las cantidades en una sola línea.");
            }
            pedido.agregarMaterial(
                    new PedidoMaterial(pedido, buscarMaterialUsableOFallar(linea.idMaterial()),
                                       linea.cantidad()));
        }

        return PedidoRespuesta.completa(repositorio.save(pedido));
    }

    // ------------------------------------------------------------------
    //  Paso 2 — el dueño aprueba y elige el proveedor
    // ------------------------------------------------------------------

    /**
     * Aprueba el pedido, le asigna proveedor y confirma los precios.
     *
     * Es una sola operacion porque el circuito real lo es. Exige el precio de
     * TODOS los materiales, no de algunos: el total del pedido es lo que
     * despues se convierte en el gasto, y un total al que le falta una linea no
     * sirve para comparar contra el presupuesto.
     */
    @Transactional
    public PedidoRespuesta aprobar(Long id, Aprobacion aprobacion) {
        Pedido pedido = buscarCompletoOFallar(id);

        if (!pedido.estaPendiente()) {
            throw new ReglaDeNegocioException(
                    "Solo se aprueba un pedido pendiente de aprobación. "
                    + "Este está " + pedido.getEstado().toLowerCase() + ".");
        }

        Proveedor proveedor = proveedorRepositorio.findById(aprobacion.idProveedor())
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "Proveedor", aprobacion.idProveedor()));

        if (!proveedor.estaActivo()) {
            throw new ReglaDeNegocioException(
                    "El proveedor \"" + proveedor.getNombreProveedor()
                    + "\" está inactivo y no se le pueden enviar pedidos.");
        }

        aplicarPrecios(pedido, aprobacion.precios());
        pedido.aprobar(proveedor);

        // El informe define la aprobacion del pedido como indelegable del dueño,
        // y el permiso compras.aprobar es lo que lo hace cumplir. Queda anotado
        // quien la ejercio: es lo que vuelve verificable que no se delego.
        auditoria.registrar(
                "Aprobación del pedido #" + pedido.getIdPedido()
                + " a " + proveedor.getNombreProveedor()
                + " para la obra #" + pedido.getObra().getIdObra(),
                "Compras");

        return PedidoRespuesta.completa(pedido);
    }

    private void aplicarPrecios(Pedido pedido, List<PrecioLinea> precios) {
        Map<Long, BigDecimal> porMaterial = new HashMap<>();
        for (PrecioLinea p : precios) {
            porMaterial.put(p.idMaterial(), p.precioUnitario());
        }

        for (PedidoMaterial linea : pedido.getMateriales()) {
            BigDecimal precio = porMaterial.get(linea.getMaterial().getIdMaterial());
            if (precio == null) {
                throw new ReglaDeNegocioException(
                        "Falta el precio de \"" + linea.getMaterial().getNombreMaterial()
                        + "\". Hay que confirmar el precio de todos los materiales del pedido.");
            }
            linea.ponerPrecio(precio);
        }
    }

    // ------------------------------------------------------------------
    //  Paso 3 — se confirma la recepcion en obra
    // ------------------------------------------------------------------

    /**
     * Confirma que el material llego.
     *
     * Solo se puede recibir lo que se envio: el informe lo dice explicitamente
     * ("No se puede confirmar la recepcion de un pedido que todavia no fue
     * enviado al proveedor"). Sin esa regla se podria marcar como recibido un
     * pedido que el dueño ni siquiera aprobo.
     *
     * El estado final lo determina la nota, no el usuario. Ver Pedido.recibir().
     */
    @Transactional
    public PedidoRespuesta recibir(Long id, Recepcion recepcion) {
        Pedido pedido = buscarCompletoOFallar(id);
        alcance.exigirAlcance(pedido.getObra().getIdObra());

        if (!pedido.fueEnviado()) {
            throw new ReglaDeNegocioException(
                    pedido.estaPendiente()
                            ? "Este pedido todavía no fue aprobado ni enviado al proveedor."
                            : "Este pedido ya está " + pedido.getEstado().toLowerCase() + ".");
        }

        // Quien confirma la recepcion queda registrado en pedido.id_usuario_recibe.
        pedido.recibir(sesion.idUsuario().orElse(null), recepcion.fotoRemito().trim(),
                       recepcion.notaDiferencia() != null && !recepcion.notaDiferencia().isBlank()
                               ? recepcion.notaDiferencia().trim()
                               : null);

        generarGastoDeLaCompra(pedido);

        return PedidoRespuesta.completa(pedido);
    }

    /**
     * Convierte la compra recibida en gasto de la obra.
     *
     * Es lo que elimina la doble carga que hoy hace la empresa: el material que
     * llego ya quedo pedido en el sistema, no hay razon para volver a tipearlo
     * como gasto.
     *
     * Se agrupa POR RUBRO porque un pedido puede mezclar cemento (Albañileria)
     * con cable (Electricidad), y el semaforo de Gastos compara rubro por rubro.
     * Un gasto unico habria que imputarlo a uno solo y ensuciaria la comparacion.
     *
     * Si el pedido no tiene todos los precios cargados no se genera nada: un
     * gasto con monto incompleto es peor que ninguno, porque el semaforo diria
     * que la obra viene mejor de lo que viene.
     */
    private void generarGastoDeLaCompra(Pedido pedido) {
        if (!pedido.tieneTodosLosPrecios()) {
            return;
        }

        Map<Long, BigDecimal> porRubro = new HashMap<>();
        for (PedidoMaterial linea : pedido.getMateriales()) {
            porRubro.merge(linea.getMaterial().getRubro().getIdRubro(),
                           linea.calcularSubtotal(), BigDecimal::add);
        }

        gastoService.generarDesdeRecepcion(pedido.getObra(), pedido.getIdPedido(),
                                           porRubro, LocalDate.now());
    }

    // ------------------------------------------------------------------
    //  Baja
    // ------------------------------------------------------------------

    /**
     * Anula un pedido que finalmente no se concreta.
     *
     * No se elimina: el informe pide dejar el registro con un motivo. Un pedido
     * ya recibido no se anula, porque el material llego a la obra y anularlo
     * borraria del historial algo que efectivamente paso.
     */
    @Transactional
    public PedidoRespuesta anular(Long id, Anulacion anulacion) {
        Pedido pedido = buscarCompletoOFallar(id);

        if (pedido.fueRecibido()) {
            throw new ReglaDeNegocioException(
                    "No se anula un pedido ya recibido: el material llegó a la obra. "
                    + "Si hubo un problema, registralo como observación del proveedor.");
        }
        if (pedido.estaAnulado()) {
            throw new ReglaDeNegocioException("El pedido ya está anulado.");
        }

        pedido.anular(anulacion.motivo().trim());

        auditoria.registrar(
                "Anulación del pedido #" + pedido.getIdPedido()
                + ": " + anulacion.motivo().trim(),
                "Compras");

        return PedidoRespuesta.completa(pedido);
    }

    // ------------------------------------------------------------------
    //  Auxiliares
    // ------------------------------------------------------------------

    private Pedido buscarCompletoOFallar(Long id) {
        return repositorio.buscarCompleto(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Pedido", id));
    }

    /**
     * Una obra cancelada o finalizada no recibe material nuevo.
     *
     * El informe no lo dice con estas palabras, pero pedir materiales para una
     * obra que ya termino o se cancelo no tiene sentido y ensuciaria el gasto
     * de una obra cerrada.
     */
    private void exigirObraOperativa(Obra obra) {
        if (obra.estaCancelada() || obra.estaFinalizada()) {
            throw new ReglaDeNegocioException(
                    "La obra está " + obra.getEstado().toLowerCase()
                    + " y no admite pedidos de materiales.");
        }
    }

    /**
     * El material tiene que existir y estar activo.
     *
     * Un material dado de baja del catalogo no deberia entrar en un pedido
     * nuevo. Los pedidos viejos que lo referencian no se tocan: son historia.
     */
    private Material buscarMaterialUsableOFallar(Long idMaterial) {
        Material material = materialRepositorio.findById(idMaterial)
                .orElseThrow(() -> new RecursoNoEncontradoException("Material", idMaterial));

        if (!material.estaActivo()) {
            throw new ReglaDeNegocioException(
                    "El material \"" + material.getNombreMaterial()
                    + "\" está inactivo en el catálogo y no se puede pedir.");
        }
        return material;
    }
}
