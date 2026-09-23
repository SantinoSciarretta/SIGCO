package com.sigco.portfolio;

import com.sigco.common.exception.RecursoNoEncontradoException;
import com.sigco.common.exception.ReglaDeNegocioException;
import com.sigco.obras.Obra;
import com.sigco.obras.ObraRepository;
import com.sigco.portfolio.dto.PortfolioDtos.NuevaImagen;
import com.sigco.portfolio.dto.PortfolioDtos.NuevaPublicacion;
import com.sigco.portfolio.dto.PortfolioDtos.PublicacionRespuesta;
import com.sigco.portfolio.dto.PortfolioDtos.VidrieraRespuesta;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reglas del modulo Portfolio Web.
 *
 * Reemplaza las fotos dispersas y sin ordenar en el telefono del dueño, que hoy
 * son dificiles de encontrar cuando un cliente las pide.
 *
 * La administracion esta restringida al rol dueño: exige portfolio.ver y
 * portfolio.editar, que ningun capataz tiene. La vidriera publica NO lleva
 * autenticacion, y esa es su razon de ser: vive en /api/vidriera, fuera de
 * /api/portfolio, justamente para poder abrirse sin desarmar las rutas.
 */
@Service
public class PortfolioService {

    private final PublicacionPortfolioRepository repositorio;
    private final ObraRepository obraRepositorio;

    public PortfolioService(PublicacionPortfolioRepository repositorio,
                            ObraRepository obraRepositorio) {
        this.repositorio = repositorio;
        this.obraRepositorio = obraRepositorio;
    }

    // ------------------------------------------------------------------
    //  Vista publica
    // ------------------------------------------------------------------

    /**
     * La vidriera.
     *
     * Devuelve un DTO REDUCIDO a proposito: solo tipo de trabajo e imagenes.
     * Sin cliente, sin direccion, sin id de obra. Es regla del informe: "No se
     * muestran en la vidriera datos del cliente ni de la ubicacion exacta de la
     * obra".
     *
     * Que la restriccion viva en el TIPO y no en una validacion es lo que la
     * hace confiable: no hay forma de filtrar esos datos por accidente, porque
     * el DTO publico no los tiene.
     */
    @Transactional(readOnly = true)
    public List<VidrieraRespuesta> vidriera(String tipoTrabajo) {
        List<PublicacionPortfolio> publicadas = (tipoTrabajo == null || tipoTrabajo.isBlank())
                ? repositorio.publicadas()
                : repositorio.publicadasDeTipo(tipoTrabajo);

        return publicadas.stream().map(VidrieraRespuesta::desde).toList();
    }

    /** Tipos de trabajo con obras publicadas, para el filtro de la vidriera. */
    @Transactional(readOnly = true)
    public List<String> tiposPublicados() {
        return repositorio.tiposConPublicaciones();
    }

    // ------------------------------------------------------------------
    //  Administracion
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<PublicacionRespuesta> listar() {
        return repositorio.todasConImagenes().stream()
                .map(PublicacionRespuesta::desde)
                .toList();
    }

    @Transactional(readOnly = true)
    public PublicacionRespuesta obtener(Long id) {
        return PublicacionRespuesta.desde(buscarOFallar(id));
    }

    /**
     * Crea la publicacion de una obra terminada.
     *
     * Solo obras FINALIZADAS: es la regla del informe. El portfolio muestra
     * trabajos terminados, no obras a medias, y ese estado ahora lo pone
     * Seguimiento al completarse el ultimo hito.
     */
    @Transactional
    public PublicacionRespuesta crear(NuevaPublicacion solicitud) {
        Obra obra = obraRepositorio.findById(solicitud.idObra())
                .orElseThrow(() -> new RecursoNoEncontradoException("Obra", solicitud.idObra()));

        if (!obra.estaFinalizada()) {
            throw new ReglaDeNegocioException(
                    "La obra está " + obra.getEstado().toLowerCase()
                    + ". Solo se publican en el portfolio obras finalizadas.");
        }
        if (repositorio.existsByObraIdObra(solicitud.idObra())) {
            throw new ReglaDeNegocioException(
                    "Esta obra ya tiene una publicación en el portfolio.");
        }

        PublicacionPortfolio publicacion = new PublicacionPortfolio(
                obra, solicitud.tipoTrabajo().trim());

        return PublicacionRespuesta.desde(repositorio.save(publicacion));
    }

    @Transactional
    public PublicacionRespuesta cambiarTipo(Long id, NuevaPublicacion solicitud) {
        PublicacionPortfolio publicacion = buscarOFallar(id);
        publicacion.cambiarTipoTrabajo(solicitud.tipoTrabajo().trim());
        return PublicacionRespuesta.desde(publicacion);
    }

    /**
     * Publica la obra en la vidriera.
     *
     * Exige al menos una imagen: publicar una galeria vacia mostraria una
     * tarjeta sin nada, que es peor que no mostrarla.
     */
    @Transactional
    public PublicacionRespuesta publicar(Long id) {
        PublicacionPortfolio publicacion = buscarOFallar(id);

        if (publicacion.getImagenes().isEmpty()) {
            throw new ReglaDeNegocioException(
                    "La publicación no tiene ninguna imagen cargada. "
                    + "Subí al menos una antes de publicarla.");
        }

        publicacion.publicar();
        return PublicacionRespuesta.desde(publicacion);
    }

    /** Saca la obra de la vidriera conservando sus imagenes. */
    @Transactional
    public PublicacionRespuesta despublicar(Long id) {
        PublicacionPortfolio publicacion = buscarOFallar(id);
        publicacion.despublicar();
        return PublicacionRespuesta.desde(publicacion);
    }

    // ------------------------------------------------------------------
    //  Imagenes
    // ------------------------------------------------------------------

    @Transactional
    public PublicacionRespuesta agregarImagen(Long id, NuevaImagen imagen) {
        PublicacionPortfolio publicacion = buscarOFallar(id);
        publicacion.agregarImagen(new ImagenPortfolio(
                publicacion, imagen.urlImagen().trim(), publicacion.siguienteOrden()));
        return PublicacionRespuesta.desde(publicacion);
    }

    /**
     * Quita una imagen de la galeria.
     *
     * Aca SI se elimina, a diferencia del resto del sistema. Una foto no es un
     * registro de algo que paso: es material de difusion, y si el dueño no
     * quiere mostrarla no tiene sentido conservarla. La obra y su historial no
     * se tocan.
     */
    @Transactional
    public PublicacionRespuesta quitarImagen(Long id, Long idImagen) {
        PublicacionPortfolio publicacion = buscarOFallar(id);

        ImagenPortfolio imagen = publicacion.getImagenes().stream()
                .filter(i -> i.getIdImagen().equals(idImagen))
                .findFirst()
                .orElseThrow(() -> new RecursoNoEncontradoException("Imagen", idImagen));

        if (publicacion.estaPublicada() && publicacion.getImagenes().size() == 1) {
            throw new ReglaDeNegocioException(
                    "Es la única imagen de una publicación activa. "
                    + "Despublicá la obra antes de quitarla.");
        }

        publicacion.quitarImagen(imagen);
        return PublicacionRespuesta.desde(publicacion);
    }

    /**
     * Reordena la galeria segun la lista de ids que llega.
     *
     * La primera imagen es la portada de la obra en la vidriera, asi que el
     * orden no es un detalle: es con que foto se presenta el trabajo al cliente
     * que entra a mirarlo. Hasta ahora las imagenes quedaban en el orden en que
     * se subieron, y cambiarlo obligaba a borrarlas y volver a subirlas todas.
     *
     * Llega la lista COMPLETA de ids en el orden deseado, y no un par
     * "imagen, posicion nueva". Es a proposito: mover una sola imagen obliga a
     * correr a todas las que estan entre su posicion vieja y la nueva, y esa
     * cuenta hecha de a una deja huecos y posiciones repetidas en cuanto dos
     * operaciones se pisan. Con la lista entera, el orden que se guarda es
     * exactamente el que el usuario ve en pantalla.
     *
     * Se exige que la lista tenga las mismas imagenes que la publicacion, ni una
     * mas ni una menos: una lista incompleta dejaria imagenes con el orden
     * viejo, mezcladas con las nuevas, y el resultado no seria el que nadie
     * pidio.
     */
    @Transactional
    public PublicacionRespuesta reordenarImagenes(Long id, List<Long> idsEnOrden) {
        PublicacionPortfolio publicacion = buscarOFallar(id);

        java.util.Map<Long, ImagenPortfolio> porId = new java.util.LinkedHashMap<>();
        for (ImagenPortfolio imagen : publicacion.getImagenes()) {
            porId.put(imagen.getIdImagen(), imagen);
        }

        // Un id repetido haria que la lista pareciera del largo correcto sin
        // serlo, y dejaria una imagen sin reordenar.
        java.util.Set<Long> sinRepetir = new java.util.HashSet<>(idsEnOrden);

        if (sinRepetir.size() != idsEnOrden.size()
                || !sinRepetir.equals(porId.keySet())) {
            throw new ReglaDeNegocioException(
                    "La lista de imágenes no coincide con las de esta publicación. "
                    + "Actualizá la pantalla y volvé a intentarlo.");
        }

        int posicion = 1;
        for (Long idImagen : idsEnOrden) {
            porId.get(idImagen).reordenar(posicion++);
        }

        return PublicacionRespuesta.desde(publicacion);
    }

    private PublicacionPortfolio buscarOFallar(Long id) {
        return repositorio.buscarCompleta(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Publicación", id));
    }
}
