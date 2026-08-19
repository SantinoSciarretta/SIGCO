package com.sigco.materiales;

import com.sigco.common.exception.RecursoNoEncontradoException;
import com.sigco.common.exception.ReglaDeNegocioException;
import com.sigco.materiales.dto.MaterialDtos.MaterialRespuesta;
import com.sigco.materiales.dto.MaterialDtos.MaterialSolicitud;
import com.sigco.presupuestacion.Rubro;
import com.sigco.presupuestacion.RubroRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Logica del catalogo de materiales.
 *
 * Depende del catalogo de rubros de Presupuestacion, y eso es a proposito: el
 * informe pide que el rubro de un material salga del mismo catalogo que usan
 * los presupuestos, para que las dos clasificaciones no se desincronicen.
 *
 * Como en el resto del sistema, nada se elimina. Un material usado en un
 * presupuesto o en un pedido no puede borrarse sin romperlos, y el informe
 * resuelve el caso desactivandolo.
 */
@Service
public class MaterialService {

    private static final long TODOS_LOS_RUBROS = 0L;

    private final MaterialRepository repositorio;
    private final RubroRepository rubroRepositorio;

    public MaterialService(MaterialRepository repositorio, RubroRepository rubroRepositorio) {
        this.repositorio = repositorio;
        this.rubroRepositorio = rubroRepositorio;
    }

    // ------------------------------------------------------------------
    //  Consulta
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<MaterialRespuesta> listar(String busqueda, Long idRubro, String estado) {
        return repositorio.buscar(
                        sinFiltro(busqueda),
                        idRubro != null ? idRubro : TODOS_LOS_RUBROS,
                        sinFiltro(estado))
                .stream()
                .map(MaterialRespuesta::desde)
                .toList();
    }

    /**
     * Materiales que se pueden elegir en un presupuesto o un pedido nuevo.
     *
     * Excluye los inactivos y los que pertenecen a un rubro inactivo.
     */
    @Transactional(readOnly = true)
    public List<MaterialRespuesta> listarDisponibles(Long idRubro) {
        return repositorio.buscarDisponibles(idRubro != null ? idRubro : TODOS_LOS_RUBROS)
                .stream()
                .map(MaterialRespuesta::desde)
                .toList();
    }

    @Transactional(readOnly = true)
    public MaterialRespuesta obtener(Long id) {
        return MaterialRespuesta.desde(buscarOFallar(id));
    }

    // ------------------------------------------------------------------
    //  Alta y edicion
    // ------------------------------------------------------------------

    @Transactional
    public MaterialRespuesta crear(MaterialSolicitud solicitud) {
        Rubro rubro = buscarRubroOFallar(solicitud.idRubro());
        exigirRubroActivo(rubro);

        String nombre = solicitud.nombreMaterial().trim();
        verificarNombreLibre(rubro.getIdRubro(), nombre, null);

        Material material = new Material(nombre, rubro, solicitud.unidadMedida().trim());

        return MaterialRespuesta.desde(repositorio.save(material));
    }

    /**
     * Edita nombre, rubro y unidad.
     *
     * A diferencia del subrubro, un material SI puede cambiar de rubro: el
     * informe lo permite en la accion "Editar Material". Clasificar un material
     * es una decision que se puede corregir.
     *
     * Al cambiar de rubro hay que revisar la unicidad del nombre en el rubro
     * DESTINO, no en el de origen: puede haber ahi otro material con ese mismo
     * nombre.
     */
    @Transactional
    public MaterialRespuesta actualizar(Long id, MaterialSolicitud solicitud) {
        Material material = buscarOFallar(id);
        Rubro rubro = buscarRubroOFallar(solicitud.idRubro());

        // Solo se exige rubro activo si efectivamente se esta cambiando de
        // rubro: editar el nombre de un material cuyo rubro quedo inactivo
        // tiene que seguir siendo posible.
        if (!rubro.getIdRubro().equals(material.getRubro().getIdRubro())) {
            exigirRubroActivo(rubro);
        }

        String nombre = solicitud.nombreMaterial().trim();
        verificarNombreLibre(rubro.getIdRubro(), nombre, id);

        material.actualizarDatos(nombre, rubro, solicitud.unidadMedida().trim());

        return MaterialRespuesta.desde(material);
    }

    @Transactional
    public MaterialRespuesta cambiarEstado(Long id, String nuevoEstado) {
        Material material = buscarOFallar(id);

        if (Material.ESTADO_ACTIVO.equals(nuevoEstado)) {
            // Activarlo bajo un rubro inactivo dejaria un dato incoherente:
            // marcado como disponible pero nunca ofrecido, porque la consulta
            // exige que el rubro tambien lo este.
            if (!material.getRubro().estaActivo()) {
                throw new ReglaDeNegocioException(
                        "No se puede activar el material porque el rubro "
                        + material.getRubro().getNombreRubro()
                        + " esta inactivo. Active primero el rubro.");
            }
            material.activar();
        } else {
            material.desactivar();
        }

        return MaterialRespuesta.desde(material);
    }

    // ------------------------------------------------------------------
    //  Reglas
    // ------------------------------------------------------------------

    /**
     * No se permiten dos materiales con el mismo nombre dentro del mismo rubro.
     *
     * Es la regla del informe, y apunta al problema concreto que se busca
     * resolver: hoy el mismo material aparece escrito de varias formas y al
     * armar un pedido no se sabe cual elegir. Entre rubros distintos si puede
     * repetirse, porque son materiales diferentes.
     *
     * @param idAExcluir al editar, el propio material no cuenta como duplicado
     */
    private void verificarNombreLibre(Long idRubro, String nombre, Long idAExcluir) {
        Optional<Material> existente = repositorio
                .findByRubroIdRubroAndNombreMaterialIgnoreCase(idRubro, nombre);

        if (existente.isPresent() && !existente.get().getIdMaterial().equals(idAExcluir)) {
            throw new ReglaDeNegocioException(
                    "El rubro ya tiene un material llamado "
                    + existente.get().getNombreMaterial() + ".");
        }
    }

    private void exigirRubroActivo(Rubro rubro) {
        if (!rubro.estaActivo()) {
            throw new ReglaDeNegocioException(
                    "El rubro " + rubro.getNombreRubro() + " esta inactivo: "
                    + "un material cargado ahi no se ofreceria en ningun pedido.");
        }
    }

    private Material buscarOFallar(Long id) {
        return repositorio.buscarConRubro(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Material", id));
    }

    private Rubro buscarRubroOFallar(Long id) {
        return rubroRepositorio.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Rubro", id));
    }

    private String sinFiltro(String texto) {
        if (texto == null || texto.isBlank()) {
            return "";
        }
        return texto.trim();
    }
}
