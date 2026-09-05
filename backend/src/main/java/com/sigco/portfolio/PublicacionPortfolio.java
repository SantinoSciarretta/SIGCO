package com.sigco.portfolio;

import com.sigco.obras.Obra;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Una obra terminada mostrada en la vidriera digital.
 *
 * El portfolio es respaldo visual de la trayectoria de la empresa para clientes
 * que llegan por recomendacion y todavia estan evaluando. NO capta clientes
 * nuevos: la vista publica no tiene formularios de contacto, por decision
 * explicita del dueño, que trabaja unicamente con referidos.
 *
 * Tampoco muestra datos del cliente ni la direccion exacta de la obra: solo las
 * imagenes y el tipo de trabajo.
 */
@Entity
@Table(name = "publicacion_portfolio")
public class PublicacionPortfolio {

    public static final String ESTADO_PUBLICADA = "Publicada";
    public static final String ESTADO_DESPUBLICADA = "Despublicada";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_publicacion")
    private Long idPublicacion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_obra", nullable = false)
    private Obra obra;

    /**
     * Lista abierta a proposito: el informe dice "Construccion / Refaccion /
     * Decoracion de local, ENTRE OTROS". Encerrarla obligaria a una migracion
     * de base cada vez que la empresa quiera mostrar un tipo nuevo.
     */
    @Column(name = "tipo_trabajo", nullable = false, length = 30)
    private String tipoTrabajo;

    @Column(name = "estado", nullable = false, length = 15)
    private String estado;

    @Column(name = "fecha_publicacion", nullable = false)
    private LocalDateTime fechaPublicacion;

    @OneToMany(mappedBy = "publicacion", fetch = FetchType.LAZY,
               cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ImagenPortfolio> imagenes = new ArrayList<>();

    protected PublicacionPortfolio() {
    }

    /**
     * Nace DESPUBLICADA.
     *
     * Se cargan las fotos primero y se publica despues, cuando el dueño ve que
     * la galeria quedo bien. Publicarla al crearla mostraria una obra sin fotos
     * en la vidriera.
     */
    public PublicacionPortfolio(Obra obra, String tipoTrabajo) {
        this.obra = obra;
        this.tipoTrabajo = tipoTrabajo;
        this.estado = ESTADO_DESPUBLICADA;
        this.fechaPublicacion = LocalDateTime.now();
    }

    public void cambiarTipoTrabajo(String tipoTrabajo) {
        this.tipoTrabajo = tipoTrabajo;
    }

    public void publicar() {
        this.estado = ESTADO_PUBLICADA;
        this.fechaPublicacion = LocalDateTime.now();
    }

    /**
     * Saca la obra de la vidriera SIN borrar las imagenes.
     *
     * Es regla del informe: "Una obra despublicada conserva sus imagenes
     * cargadas, para poder volver a publicarla mas adelante sin recargarlas".
     */
    public void despublicar() {
        this.estado = ESTADO_DESPUBLICADA;
    }

    public void agregarImagen(ImagenPortfolio imagen) {
        this.imagenes.add(imagen);
    }

    public void quitarImagen(ImagenPortfolio imagen) {
        this.imagenes.remove(imagen);
    }

    public boolean estaPublicada() {
        return ESTADO_PUBLICADA.equals(this.estado);
    }

    /** Siguiente numero de orden, para que las fotos se agreguen al final. */
    public int siguienteOrden() {
        return imagenes.stream()
                .mapToInt(ImagenPortfolio::getOrden)
                .max()
                .orElse(-1) + 1;
    }

    public Long getIdPublicacion() {
        return idPublicacion;
    }

    public Obra getObra() {
        return obra;
    }

    public String getTipoTrabajo() {
        return tipoTrabajo;
    }

    public String getEstado() {
        return estado;
    }

    public LocalDateTime getFechaPublicacion() {
        return fechaPublicacion;
    }

    public List<ImagenPortfolio> getImagenes() {
        return imagenes;
    }
}
