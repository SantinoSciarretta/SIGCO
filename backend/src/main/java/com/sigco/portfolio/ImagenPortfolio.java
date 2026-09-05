package com.sigco.portfolio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Una foto de la galeria de una obra.
 *
 * La base guarda solo la URL de Supabase Storage, nunca el binario. Es el mismo
 * criterio que para las fotos de remito y los comprobantes de gastos.
 */
@Entity
@Table(name = "imagen_portfolio")
public class ImagenPortfolio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_imagen")
    private Long idImagen;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_publicacion", nullable = false)
    private PublicacionPortfolio publicacion;

    @Column(name = "url_imagen", nullable = false, length = 255)
    private String urlImagen;

    /** Posicion en la galeria: el dueño decide con cual arranca. */
    @Column(name = "orden", nullable = false)
    private Integer orden;

    protected ImagenPortfolio() {
    }

    public ImagenPortfolio(PublicacionPortfolio publicacion, String urlImagen, Integer orden) {
        this.publicacion = publicacion;
        this.urlImagen = urlImagen;
        this.orden = orden;
    }

    public void reordenar(Integer orden) {
        this.orden = orden;
    }

    public Long getIdImagen() {
        return idImagen;
    }

    public PublicacionPortfolio getPublicacion() {
        return publicacion;
    }

    public String getUrlImagen() {
        return urlImagen;
    }

    public Integer getOrden() {
        return orden;
    }
}
