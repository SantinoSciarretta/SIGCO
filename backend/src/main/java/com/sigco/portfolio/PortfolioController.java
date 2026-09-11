package com.sigco.portfolio;

import com.sigco.portfolio.dto.PortfolioDtos.NuevaImagen;
import com.sigco.portfolio.dto.PortfolioDtos.NuevaPublicacion;
import com.sigco.portfolio.dto.PortfolioDtos.PublicacionRespuesta;
import com.sigco.portfolio.dto.PortfolioDtos.VidrieraRespuesta;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * API REST del modulo Portfolio Web.
 *
 * Se separa en dos grupos de rutas con proposito distinto:
 *
 *   /api/vidriera  — publica, solo lectura, sin datos del cliente.
 *   /api/portfolio — administracion del dueño.
 *
 * La separacion no es cosmetica: cuando se active Spring Security, /api/vidriera
 * queda abierta y /api/portfolio detras del login. Tenerlas separadas desde
 * ahora evita tener que desarmar rutas despues.
 *
 * NO HAY formulario de contacto ni endpoint de consulta comercial. Es una
 * decision explicita del dueño, que trabaja unicamente con clientes referidos:
 * el portfolio es respaldo visual, no capta clientes nuevos.
 *
 * MODULO 14 (Accesos): esta clase es la unica que se anota metodo por metodo en
 * lugar de a nivel de clase. Los dos grupos de rutas tienen reglas opuestas —la
 * vidriera es publica, la administracion requiere permiso— y una anotacion de
 * clase cerraria la vidriera para los visitantes.
 */
@RestController
public class PortfolioController {

    private final PortfolioService servicio;

    public PortfolioController(PortfolioService servicio) {
        this.servicio = servicio;
    }

    // ---------- Vidriera pública ----------

    /** GET /api/vidriera?tipo= — obras publicadas, sin datos del cliente. */
    @GetMapping("/api/vidriera")
    public List<VidrieraRespuesta> vidriera(@RequestParam(required = false) String tipo) {
        return servicio.vidriera(tipo);
    }

    /** GET /api/vidriera/tipos — para el filtro de la galería. */
    @GetMapping("/api/vidriera/tipos")
    public List<String> tipos() {
        return servicio.tiposPublicados();
    }

    // ---------- Administración ----------

    @PreAuthorize("hasAuthority('portfolio.ver')")
    @GetMapping("/api/portfolio")
    public List<PublicacionRespuesta> listar() {
        return servicio.listar();
    }

    @PreAuthorize("hasAuthority('portfolio.ver')")
    @GetMapping("/api/portfolio/{id}")
    public PublicacionRespuesta obtener(@PathVariable Long id) {
        return servicio.obtener(id);
    }

    /** Solo obras finalizadas. Nace despublicada: primero las fotos. */
    @PreAuthorize("hasAuthority('portfolio.editar')")
    @PostMapping("/api/portfolio")
    public ResponseEntity<PublicacionRespuesta> crear(
            @Valid @RequestBody NuevaPublicacion solicitud) {
        PublicacionRespuesta creada = servicio.crear(solicitud);
        return ResponseEntity
                .created(URI.create("/api/portfolio/" + creada.idPublicacion()))
                .body(creada);
    }

    @PreAuthorize("hasAuthority('portfolio.editar')")
    @PutMapping("/api/portfolio/{id}")
    public PublicacionRespuesta cambiarTipo(@PathVariable Long id,
                                            @Valid @RequestBody NuevaPublicacion solicitud) {
        return servicio.cambiarTipo(id, solicitud);
    }

    @PreAuthorize("hasAuthority('portfolio.editar')")
    @PatchMapping("/api/portfolio/{id}/publicacion")
    public PublicacionRespuesta publicar(@PathVariable Long id) {
        return servicio.publicar(id);
    }

    /** Saca la obra de la vidriera conservando sus imágenes. */
    @PreAuthorize("hasAuthority('portfolio.editar')")
    @PatchMapping("/api/portfolio/{id}/despublicacion")
    public PublicacionRespuesta despublicar(@PathVariable Long id) {
        return servicio.despublicar(id);
    }

    @PreAuthorize("hasAuthority('portfolio.editar')")
    @PostMapping("/api/portfolio/{id}/imagenes")
    public PublicacionRespuesta agregarImagen(@PathVariable Long id,
                                              @Valid @RequestBody NuevaImagen imagen) {
        return servicio.agregarImagen(id, imagen);
    }

    /**
     * Acá SÍ se elimina, a diferencia del resto del sistema: una foto no es el
     * registro de algo que pasó, es material de difusión.
     */
    @PreAuthorize("hasAuthority('portfolio.editar')")
    @DeleteMapping("/api/portfolio/{id}/imagenes/{idImagen}")
    public PublicacionRespuesta quitarImagen(@PathVariable Long id,
                                             @PathVariable Long idImagen) {
        return servicio.quitarImagen(id, idImagen);
    }
}
