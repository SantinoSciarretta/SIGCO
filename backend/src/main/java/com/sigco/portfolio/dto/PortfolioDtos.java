package com.sigco.portfolio.dto;

import com.sigco.portfolio.ImagenPortfolio;
import com.sigco.portfolio.PublicacionPortfolio;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

/** Datos que entran y salen del modulo Portfolio Web. */
public final class PortfolioDtos {

    /**
     * Constructor privado: esta clase solo agrupa los formatos de datos del
     * Portfolio, no se crean objetos de ella.
     */
    private PortfolioDtos() {
    }

    // ------------------------------------------------------------------
    //  Entrada
    // ------------------------------------------------------------------

    public record NuevaPublicacion(

            /** Solo se usa al crear; al cambiar el tipo se ignora. */
            Long idObra,

            @NotBlank(message = "El tipo de trabajo es obligatorio")
            @Size(max = 30, message = "El tipo de trabajo no puede superar los 30 caracteres")
            String tipoTrabajo) {
    }

    public record NuevaImagen(

            @NotNull(message = "La imagen es obligatoria")
            @Size(max = 255, message = "La referencia no puede superar los 255 caracteres")
            String urlImagen) {
    }

    // ------------------------------------------------------------------
    //  Salida — administracion (interna)
    // ------------------------------------------------------------------

    /** Vista del dueño: incluye los datos de la obra, que él sí puede ver. */
    public record PublicacionRespuesta(
            Long idPublicacion,
            Long idObra,
            String direccionObra,
            String nombreCliente,
            String tipoTrabajo,
            String estado,
            LocalDateTime fechaPublicacion,
            Integer cantidadImagenes,
            List<ImagenRespuesta> imagenes) {

        /**
         * Convierte una publicación en el formato que ve el dueño en el panel
         * de administración, con los datos de la obra y del cliente.
         */
        public static PublicacionRespuesta desde(PublicacionPortfolio p) {
            return new PublicacionRespuesta(
                    p.getIdPublicacion(),
                    p.getObra().getIdObra(),
                    p.getObra().getDireccionObra(),
                    p.getObra().getCliente().getNombreApellido(),
                    p.getTipoTrabajo(), p.getEstado(), p.getFechaPublicacion(),
                    p.getImagenes().size(),
                    p.getImagenes().stream()
                            .sorted(Comparator.comparing(ImagenPortfolio::getOrden))
                            .map(ImagenRespuesta::desde).toList());
        }
    }

    public record ImagenRespuesta(Long idImagen, String urlImagen, Integer orden) {

        /**
         * Convierte una imagen en el formato que se le envía a la pantalla.
         */
        static ImagenRespuesta desde(ImagenPortfolio i) {
            return new ImagenRespuesta(i.getIdImagen(), i.getUrlImagen(), i.getOrden());
        }
    }

    // ------------------------------------------------------------------
    //  Salida — vidriera (publica)
    // ------------------------------------------------------------------

    /**
     * Lo que ve un visitante de la vidriera.
     *
     * NO TIENE cliente, ni dirección, ni id de obra. Es regla del informe: la
     * vidriera muestra únicamente las imágenes y el tipo de trabajo.
     *
     * Que la restricción viva en el TIPO y no en una validación es lo que la
     * hace confiable: no hay forma de filtrar esos datos por accidente, porque
     * este record no los tiene. Una validación se puede olvidar; un campo que
     * no existe, no.
     */
    public record VidrieraRespuesta(
            Long idPublicacion,
            String tipoTrabajo,
            LocalDateTime fechaPublicacion,
            List<String> imagenes) {

        /**
         * Convierte una publicación en el formato de la vidriera pública: solo
         * el tipo de trabajo, la fecha y las fotos en orden. No incluye datos
         * del cliente ni la dirección.
         */
        public static VidrieraRespuesta desde(PublicacionPortfolio p) {
            return new VidrieraRespuesta(
                    p.getIdPublicacion(), p.getTipoTrabajo(), p.getFechaPublicacion(),
                    p.getImagenes().stream()
                            .sorted(Comparator.comparing(ImagenPortfolio::getOrden))
                            .map(ImagenPortfolio::getUrlImagen).toList());
        }
    }
}
