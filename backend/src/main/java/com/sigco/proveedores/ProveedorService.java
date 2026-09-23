package com.sigco.proveedores;

import com.sigco.common.exception.RecursoNoEncontradoException;
import com.sigco.common.exception.ReglaDeNegocioException;
import com.sigco.materiales.Material;
import com.sigco.materiales.MaterialRepository;
import com.sigco.proveedores.dto.ProveedorDtos.CotizacionRespuesta;
import com.sigco.proveedores.dto.ProveedorDtos.CotizacionSolicitud;
import com.sigco.proveedores.dto.ProveedorDtos.ObservacionRespuesta;
import com.sigco.proveedores.dto.ProveedorDtos.ObservacionSolicitud;
import com.sigco.proveedores.dto.ProveedorDtos.ProveedorRespuesta;
import com.sigco.proveedores.dto.ProveedorDtos.ProveedorSolicitud;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Logica del modulo Proveedores.
 *
 * Cubre las tres cosas que hoy se pierden: a quien se le puede pedir en cada
 * zona, a cuanto cotizo cada uno cada material, y como se comporto en los
 * pedidos anteriores.
 *
 * Cotizaciones y observaciones son registros de hechos: se crean y no se
 * modifican. Si el proveedor informa un precio nuevo, se agrega otra
 * cotizacion; la anterior queda como historia.
 */
@Service
public class ProveedorService {

    private final ProveedorRepository repositorio;
    private final CotizacionRepository cotizacionRepositorio;
    private final ObservacionRepository observacionRepositorio;
    private final MaterialRepository materialRepositorio;

    /**
     * Para verificar que el pedido de una observacion exista y sea de este
     * proveedor.
     *
     * Se inyecta el REPOSITORIO y no PedidoService: ese servicio ya depende de
     * ProveedorRepository, y pedirle el servicio entero cerraria un ciclo entre
     * los dos modulos.
     */
    private final com.sigco.compras.PedidoRepository pedidoRepositorio;

    public ProveedorService(ProveedorRepository repositorio,
                            CotizacionRepository cotizacionRepositorio,
                            ObservacionRepository observacionRepositorio,
                            MaterialRepository materialRepositorio,
                            com.sigco.compras.PedidoRepository pedidoRepositorio) {
        this.pedidoRepositorio = pedidoRepositorio;
        this.repositorio = repositorio;
        this.cotizacionRepositorio = cotizacionRepositorio;
        this.observacionRepositorio = observacionRepositorio;
        this.materialRepositorio = materialRepositorio;
    }

    // ------------------------------------------------------------------
    //  Proveedores
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<ProveedorRespuesta> listar(String busqueda, String zona, String estado) {
        return repositorio.buscar(sinFiltro(busqueda), sinFiltro(zona), sinFiltro(estado))
                .stream()
                .map(this::conConteos)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<String> zonas() {
        return repositorio.zonas();
    }

    @Transactional(readOnly = true)
    public ProveedorRespuesta obtener(Long id) {
        return conConteos(buscarOFallar(id));
    }

    @Transactional
    public ProveedorRespuesta crear(ProveedorSolicitud solicitud) {
        String nombre = solicitud.nombreProveedor().trim();
        String zona = solicitud.zonaCobertura().trim();

        verificarQueNoEsteRepetido(nombre, zona, null);

        Proveedor proveedor = new Proveedor(nombre, zona,
                normalizar(solicitud.telefonoContacto()),
                normalizar(solicitud.emailContacto()));

        return conConteos(repositorio.save(proveedor));
    }

    @Transactional
    public ProveedorRespuesta actualizar(Long id, ProveedorSolicitud solicitud) {
        Proveedor proveedor = buscarOFallar(id);
        String nombre = solicitud.nombreProveedor().trim();
        String zona = solicitud.zonaCobertura().trim();

        verificarQueNoEsteRepetido(nombre, zona, id);

        proveedor.actualizarDatos(nombre, zona,
                normalizar(solicitud.telefonoContacto()),
                normalizar(solicitud.emailContacto()));

        return conConteos(proveedor);
    }

    /**
     * Activa o desactiva un proveedor.
     *
     * El informe pide no poder eliminar un proveedor con pedidos o cotizaciones
     * asociadas. En este sistema directamente no se elimina ninguno: la baja
     * fisica borraria el historial de precios y de comportamiento, que es
     * justamente lo que el modulo viene a conservar.
     */
    @Transactional
    public ProveedorRespuesta cambiarEstado(Long id, String nuevoEstado) {
        Proveedor proveedor = buscarOFallar(id);

        if (Proveedor.ESTADO_ACTIVO.equals(nuevoEstado)) {
            proveedor.activar();
        } else {
            proveedor.desactivar();
        }

        return conConteos(proveedor);
    }

    // ------------------------------------------------------------------
    //  Cotizaciones
    // ------------------------------------------------------------------

    /**
     * Registra un precio informado por un proveedor.
     *
     * No reemplaza la cotizacion anterior de ese material: se agrega una nueva.
     * Asi queda la evolucion del precio, que en un contexto de inflacion es
     * informacion util por si misma.
     */
    @Transactional
    public CotizacionRespuesta registrarCotizacion(Long idProveedor, CotizacionSolicitud solicitud) {
        Proveedor proveedor = buscarOFallar(idProveedor);

        if (!proveedor.estaActivo()) {
            throw new ReglaDeNegocioException(
                    "El proveedor " + proveedor.getNombreProveedor() + " esta inactivo: "
                    + "no tiene sentido registrarle cotizaciones nuevas.");
        }

        Material material = materialRepositorio.findById(solicitud.idMaterial())
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "Material", solicitud.idMaterial()));

        Cotizacion cotizacion = new Cotizacion(proveedor, material, solicitud.precioCotizado());

        return CotizacionRespuesta.desde(cotizacionRepositorio.save(cotizacion));
    }

    /**
     * Comparador: para un material, la ultima cotizacion de cada proveedor
     * activo, de menor a mayor precio.
     *
     * Es la consulta que responde la pregunta concreta del dueño al aprobar un
     * pedido: "necesito cemento, quien me lo hace mas barato hoy".
     */
    @Transactional(readOnly = true)
    public List<CotizacionRespuesta> compararPorMaterial(Long idMaterial) {
        // Se comprueba que el material exista para poder devolver un 404
        // entendible en lugar de una lista vacia, que se confundiria con
        // "nadie lo cotizo todavia".
        if (!materialRepositorio.existsById(idMaterial)) {
            throw new RecursoNoEncontradoException("Material", idMaterial);
        }

        return cotizacionRepositorio.comparar(idMaterial).stream()
                .map(CotizacionRespuesta::desde)
                .toList();
    }

    /** Historial completo de cotizaciones de un proveedor. */
    @Transactional(readOnly = true)
    public List<CotizacionRespuesta> historialDeCotizaciones(Long idProveedor) {
        buscarOFallar(idProveedor);

        return cotizacionRepositorio.historialDeProveedor(idProveedor).stream()
                .map(CotizacionRespuesta::desde)
                .toList();
    }

    // ------------------------------------------------------------------
    //  Observaciones
    // ------------------------------------------------------------------

    /**
     * Registra una observacion sobre el comportamiento del proveedor.
     *
     * Es lo que hoy queda en la memoria del dueño: que demoro, que mando de
     * menos, que entrego roto. Con fecha, para poder ponerla en contexto.
     */
    @Transactional
    public ObservacionRespuesta registrarObservacion(Long idProveedor,
                                                     ObservacionSolicitud solicitud) {
        Proveedor proveedor = buscarOFallar(idProveedor);

        // El pedido es opcional —una observacion puede no venir de ninguno— pero
        // si viene tiene que existir y ser de ESTE proveedor. Sin esta
        // comprobacion se podia colgar una queja de un pedido inexistente, o
        // peor, del pedido de otro proveedor: el historial de comportamiento
        // que el dueño usa para decidir a quien comprarle quedaria contaminado.
        if (solicitud.idPedido() != null) {
            var pedido = pedidoRepositorio.findById(solicitud.idPedido())
                    .orElseThrow(() -> new RecursoNoEncontradoException(
                            "Pedido", solicitud.idPedido()));

            boolean esDeEsteProveedor = pedido.getProveedor() != null
                    && pedido.getProveedor().getIdProveedor().equals(idProveedor);

            if (!esDeEsteProveedor) {
                throw new ReglaDeNegocioException(
                        "Ese pedido no corresponde a este proveedor.");
            }
        }

        ObservacionProveedor observacion = new ObservacionProveedor(
                proveedor, solicitud.idPedido(), solicitud.descripcion().trim());

        return ObservacionRespuesta.desde(observacionRepositorio.save(observacion));
    }

    @Transactional(readOnly = true)
    public List<ObservacionRespuesta> observacionesDe(Long idProveedor) {
        buscarOFallar(idProveedor);

        return observacionRepositorio.findByProveedorIdProveedorOrderByFechaDesc(idProveedor)
                .stream()
                .map(ObservacionRespuesta::desde)
                .toList();
    }

    // ------------------------------------------------------------------

    /**
     * El informe prohibe dos proveedores con el mismo nombre Y la misma zona.
     *
     * La combinacion importa: una cadena de corralones puede tener sucursales
     * con el mismo nombre en zonas distintas, y a efectos de a quien pedirle
     * son proveedores diferentes.
     *
     * @param idAExcluir al editar, el propio proveedor no cuenta como duplicado
     */
    private void verificarQueNoEsteRepetido(String nombre, String zona, Long idAExcluir) {
        Optional<Proveedor> existente = repositorio.buscarPorNombreYZona(nombre, zona);

        if (existente.isPresent() && !existente.get().getIdProveedor().equals(idAExcluir)) {
            throw new ReglaDeNegocioException(
                    "Ya existe un proveedor " + existente.get().getNombreProveedor()
                    + " en la zona " + existente.get().getZonaCobertura() + ".");
        }
    }

    /**
     * Agrega al proveedor la cantidad de cotizaciones y observaciones que tiene.
     *
     * Se consulta por proveedor y no en una sola consulta agrupada como en
     * Clientes, porque el listado de proveedores es corto: la empresa trabaja
     * con un puñado de corralones, no con cientos. Si creciera, conviene pasar
     * al agrupado.
     */
    private ProveedorRespuesta conConteos(Proveedor proveedor) {
        Long id = proveedor.getIdProveedor();
        return ProveedorRespuesta.desde(
                proveedor,
                id != null ? cotizacionRepositorio.countByProveedorIdProveedor(id) : 0,
                id != null ? observacionRepositorio.countByProveedorIdProveedor(id) : 0);
    }

    private Proveedor buscarOFallar(Long id) {
        return repositorio.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Proveedor", id));
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
