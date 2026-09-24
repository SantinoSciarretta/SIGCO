package com.sigco.presupuestacion;

import com.sigco.common.exception.RecursoNoEncontradoException;
import com.sigco.common.exception.ReglaDeNegocioException;
import com.sigco.presupuestacion.dto.RubroRespuesta;
import com.sigco.presupuestacion.dto.RubroSolicitud;
import com.sigco.presupuestacion.dto.SubrubroRespuesta;
import com.sigco.presupuestacion.dto.SubrubroSolicitud;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Logica del catalogo de rubros y subrubros.
 *
 * Rubros y subrubros se manejan juntos en un mismo servicio porque son un solo
 * catalogo de dos niveles, no dos cosas independientes: un subrubro no existe
 * fuera de su rubro, y las reglas de uno hablan del otro.
 *
 * Regla transversal del modulo: nada se elimina. Un rubro o subrubro que ya se
 * uso en un presupuesto no puede borrarse sin romper ese presupuesto, y el
 * informe resuelve el caso desactivandolo, para que no aparezca en las cargas
 * nuevas sin afectar las existentes. Como el borrado nunca es la respuesta
 * correcta, el servicio directamente no lo ofrece.
 */
@Service
public class CatalogoService {

    private static final long TODOS_LOS_RUBROS = 0L;

    private final RubroRepository rubroRepositorio;
    private final SubrubroRepository subrubroRepositorio;

    public CatalogoService(RubroRepository rubroRepositorio,
                           SubrubroRepository subrubroRepositorio) {
        this.rubroRepositorio = rubroRepositorio;
        this.subrubroRepositorio = subrubroRepositorio;
    }

    // ------------------------------------------------------------------
    //  Consulta
    // ------------------------------------------------------------------

    /** Catalogo completo: cada rubro con sus subrubros anidados. */
    @Transactional(readOnly = true)
    public List<RubroRespuesta> listar(String busqueda, String estado) {
        return rubroRepositorio.buscarConSubrubros(sinFiltro(busqueda), sinFiltro(estado))
                .stream()
                .map(RubroRespuesta::desde)
                .toList();
    }

    /**
     * Subrubros que se pueden ofrecer al armar un presupuesto nuevo.
     *
     * Excluye los inactivos y tambien los que pertenecen a un rubro inactivo.
     */
    @Transactional(readOnly = true)
    public List<SubrubroRespuesta> listarSubrubrosDisponibles(Long idRubro) {
        return subrubroRepositorio
                .buscarDisponibles(idRubro != null ? idRubro : TODOS_LOS_RUBROS)
                .stream()
                .map(SubrubroRespuesta::desde)
                .toList();
    }

    // ------------------------------------------------------------------
    //  Rubros
    // ------------------------------------------------------------------

    @Transactional
    public RubroRespuesta crearRubro(RubroSolicitud solicitud) {
        String nombre = solicitud.nombreRubro().trim();
        verificarNombreDeRubroLibre(nombre, null);

        Rubro rubro = new Rubro(nombre);
        aplicarManoDeObra(rubro, solicitud.esManoDeObra());

        return RubroRespuesta.soloRubro(rubroRepositorio.save(rubro));
    }

    @Transactional
    public RubroRespuesta renombrarRubro(Long id, RubroSolicitud solicitud) {
        Rubro rubro = buscarRubroOFallar(id);
        String nombre = solicitud.nombreRubro().trim();

        verificarNombreDeRubroLibre(nombre, id);
        rubro.renombrar(nombre);
        aplicarManoDeObra(rubro, solicitud.esManoDeObra());

        return RubroRespuesta.soloRubro(rubro);
    }

    /**
     * Marca este rubro como el de mano de obra, desmarcando al anterior.
     *
     * Hay UNO SOLO en todo el catalogo: dos marcados dejarian a la planilla sin
     * saber cual es, y elegiria uno de los dos sin criterio. La base lo impide
     * con un indice unico parcial (V18), pero si el servicio no desmarcara al
     * anterior, marcar un rubro nuevo fallaria con un error de base de datos en
     * lugar de hacer lo que el usuario pidio.
     */
    private void aplicarManoDeObra(Rubro rubro, boolean esManoDeObra) {
        if (rubro.esManoDeObra() == esManoDeObra) {
            return;
        }

        if (esManoDeObra) {
            rubroRepositorio.findByEsManoDeObraTrue()
                    .filter(anterior -> !anterior.getIdRubro().equals(rubro.getIdRubro()))
                    .ifPresent(anterior -> anterior.marcarComoManoDeObra(false));

            // El desmarcado del anterior tiene que llegar a la base ANTES de
            // marcar el nuevo: si los dos cambios viajaran juntos al confirmar
            // la transaccion, el indice unico podria ver los dos marcados a la
            // vez y rechazar la operacion.
            rubroRepositorio.flush();
        }

        rubro.marcarComoManoDeObra(esManoDeObra);
    }

    @Transactional
    public RubroRespuesta cambiarEstadoRubro(Long id, String nuevoEstado) {
        Rubro rubro = buscarRubroOFallar(id);

        if (Rubro.ESTADO_ACTIVO.equals(nuevoEstado)) {
            rubro.activar();
        } else {
            rubro.desactivar();
        }

        return RubroRespuesta.soloRubro(rubro);
    }

    // ------------------------------------------------------------------
    //  Subrubros
    // ------------------------------------------------------------------

    @Transactional
    public SubrubroRespuesta crearSubrubro(Long idRubro, SubrubroSolicitud solicitud) {
        Rubro rubro = buscarRubroOFallar(idRubro);
        String nombre = solicitud.nombreSubrubro().trim();

        verificarNombreDeSubrubroLibre(idRubro, nombre, null);

        return SubrubroRespuesta.desde(
                subrubroRepositorio.save(new Subrubro(rubro, nombre)));
    }

    @Transactional
    public SubrubroRespuesta renombrarSubrubro(Long id, SubrubroSolicitud solicitud) {
        Subrubro subrubro = buscarSubrubroOFallar(id);
        String nombre = solicitud.nombreSubrubro().trim();

        verificarNombreDeSubrubroLibre(subrubro.getRubro().getIdRubro(), nombre, id);
        subrubro.renombrar(nombre);

        return SubrubroRespuesta.desde(subrubro);
    }

    @Transactional
    public SubrubroRespuesta cambiarEstadoSubrubro(Long id, String nuevoEstado) {
        Subrubro subrubro = buscarSubrubroOFallar(id);

        if (Subrubro.ESTADO_ACTIVO.equals(nuevoEstado)) {
            // Reactivar un subrubro cuyo rubro esta inactivo dejaria un dato
            // incoherente: quedaria marcado como disponible pero el sistema no
            // lo ofreceria nunca. Conviene avisar en lugar de aceptarlo.
            if (!subrubro.getRubro().estaActivo()) {
                throw new ReglaDeNegocioException(
                        "No se puede activar el subrubro porque el rubro "
                        + subrubro.getRubro().getNombreRubro()
                        + " esta inactivo. Active primero el rubro.");
            }
            subrubro.activar();
        } else {
            subrubro.desactivar();
        }

        return SubrubroRespuesta.desde(subrubro);
    }

    // ------------------------------------------------------------------
    //  Reglas compartidas
    // ------------------------------------------------------------------

    /**
     * El informe prohibe dos rubros con el mismo nombre, para no terminar con
     * el gasto de un mismo trabajo repartido entre dos etiquetas distintas.
     *
     * La base lo impide con un indice unico; esta comprobacion existe para
     * devolver un mensaje entendible en lugar de un error de restriccion.
     *
     * @param idAExcluir al renombrar, el propio rubro no cuenta como duplicado
     */
    private void verificarNombreDeRubroLibre(String nombre, Long idAExcluir) {
        Optional<Rubro> existente = rubroRepositorio.findByNombreRubroIgnoreCase(nombre);

        if (existente.isPresent() && !existente.get().getIdRubro().equals(idAExcluir)) {
            throw new ReglaDeNegocioException(
                    "Ya existe un rubro llamado " + existente.get().getNombreRubro() + ".");
        }
    }

    /**
     * El nombre del subrubro es unico dentro de su rubro, no en todo el sistema.
     *
     * El informe pide la unicidad solo para los rubros. Se extendio a los
     * subrubros dentro de su rubro por el mismo motivo que da para los rubros:
     * dos "Demolicion" dentro de Albanileria son un error de carga y dividirian
     * el gasto de un mismo trabajo. Entre rubros distintos si pueden repetirse,
     * porque son trabajos diferentes.
     */
    private void verificarNombreDeSubrubroLibre(Long idRubro, String nombre, Long idAExcluir) {
        Optional<Subrubro> existente = subrubroRepositorio
                .findByRubroIdRubroAndNombreSubrubroIgnoreCase(idRubro, nombre);

        if (existente.isPresent() && !existente.get().getIdSubrubro().equals(idAExcluir)) {
            throw new ReglaDeNegocioException(
                    "El rubro ya tiene un subrubro llamado "
                    + existente.get().getNombreSubrubro() + ".");
        }
    }

    private Rubro buscarRubroOFallar(Long id) {
        return rubroRepositorio.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Rubro", id));
    }

    private Subrubro buscarSubrubroOFallar(Long id) {
        return subrubroRepositorio.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Subrubro", id));
    }

    /** Cadena vacia para los filtros ausentes: ningun parametro va en null. */
    private String sinFiltro(String texto) {
        if (texto == null || texto.isBlank()) {
            return "";
        }
        return texto.trim();
    }
}
