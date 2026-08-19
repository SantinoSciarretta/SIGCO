package com.sigco.materiales;

import com.sigco.materiales.dto.MaterialDtos.CambioEstadoMaterial;
import com.sigco.materiales.dto.MaterialDtos.MaterialRespuesta;
import com.sigco.materiales.dto.MaterialDtos.MaterialSolicitud;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * API REST del catalogo de materiales.
 *
 * Sin DELETE, como en el resto del sistema: un material usado en un presupuesto
 * o en un pedido no puede borrarse sin romperlos. El informe resuelve el caso
 * desactivandolo, de modo que no aparezca en las cargas nuevas sin afectar las
 * existentes.
 */
@RestController
@RequestMapping("/api/materiales")
public class MaterialController {

    private final MaterialService servicio;

    public MaterialController(MaterialService servicio) {
        this.servicio = servicio;
    }

    /**
     * GET /api/materiales?busqueda=cemento&rubro=1&estado=Activo
     *
     * Con disponibles=true devuelve solo los que se pueden elegir en un
     * presupuesto o pedido nuevo: activos y con su rubro activo. Es la vista
     * que consumen Presupuestacion y Compras; la otra es la de mantenimiento
     * del catalogo, que necesita ver tambien los inactivos.
     */
    @GetMapping
    public List<MaterialRespuesta> listar(
            @RequestParam(required = false) String busqueda,
            @RequestParam(required = false) Long rubro,
            @RequestParam(required = false) String estado,
            @RequestParam(required = false, defaultValue = "false") boolean disponibles) {

        if (disponibles) {
            return servicio.listarDisponibles(rubro);
        }
        return servicio.listar(busqueda, rubro, estado);
    }

    /** GET /api/materiales/{id} */
    @GetMapping("/{id}")
    public MaterialRespuesta obtener(@PathVariable Long id) {
        return servicio.obtener(id);
    }

    /** POST /api/materiales */
    @PostMapping
    public ResponseEntity<MaterialRespuesta> crear(
            @Valid @RequestBody MaterialSolicitud solicitud) {

        MaterialRespuesta creado = servicio.crear(solicitud);
        return ResponseEntity
                .created(URI.create("/api/materiales/" + creado.idMaterial()))
                .body(creado);
    }

    /** PUT /api/materiales/{id} — nombre, rubro y unidad son editables. */
    @PutMapping("/{id}")
    public MaterialRespuesta actualizar(@PathVariable Long id,
                                        @Valid @RequestBody MaterialSolicitud solicitud) {
        return servicio.actualizar(id, solicitud);
    }

    /** PATCH /api/materiales/{id}/estado */
    @PatchMapping("/{id}/estado")
    public MaterialRespuesta cambiarEstado(@PathVariable Long id,
                                           @Valid @RequestBody CambioEstadoMaterial cambio) {
        return servicio.cambiarEstado(id, cambio.estado());
    }
}
