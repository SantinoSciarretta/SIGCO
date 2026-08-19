package com.sigco.presupuestacion;

import com.sigco.presupuestacion.dto.CambioEstadoCatalogo;
import com.sigco.presupuestacion.dto.RubroRespuesta;
import com.sigco.presupuestacion.dto.RubroSolicitud;
import com.sigco.presupuestacion.dto.SubrubroRespuesta;
import com.sigco.presupuestacion.dto.SubrubroSolicitud;
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
 * API REST del catalogo de rubros y subrubros.
 *
 * Los subrubros cuelgan de la direccion de su rubro al crearlos
 * (POST /api/rubros/{id}/subrubros), porque no existen fuera de el. Para
 * modificarlos despues se los referencia por su propio identificador
 * (/api/subrubros/{id}), que es mas corto y no obliga a recordar a que rubro
 * pertenecen.
 *
 * Sin DELETE, como en el resto del sistema: un rubro usado en un presupuesto no
 * puede borrarse sin romperlo, y el informe resuelve el caso desactivandolo.
 */
@RestController
@RequestMapping("/api")
public class CatalogoController {

    private final CatalogoService servicio;

    public CatalogoController(CatalogoService servicio) {
        this.servicio = servicio;
    }

    // ---------- Rubros ----------

    /** GET /api/rubros?busqueda=alba&estado=Activo — cada rubro con sus subrubros. */
    @GetMapping("/rubros")
    public List<RubroRespuesta> listarRubros(
            @RequestParam(required = false) String busqueda,
            @RequestParam(required = false) String estado) {

        return servicio.listar(busqueda, estado);
    }

    /** POST /api/rubros */
    @PostMapping("/rubros")
    public ResponseEntity<RubroRespuesta> crearRubro(@Valid @RequestBody RubroSolicitud solicitud) {
        RubroRespuesta creado = servicio.crearRubro(solicitud);
        return ResponseEntity
                .created(URI.create("/api/rubros/" + creado.idRubro()))
                .body(creado);
    }

    /** PUT /api/rubros/{id} — lo unico editable es el nombre. */
    @PutMapping("/rubros/{id}")
    public RubroRespuesta renombrarRubro(@PathVariable Long id,
                                         @Valid @RequestBody RubroSolicitud solicitud) {
        return servicio.renombrarRubro(id, solicitud);
    }

    /** PATCH /api/rubros/{id}/estado */
    @PatchMapping("/rubros/{id}/estado")
    public RubroRespuesta cambiarEstadoRubro(@PathVariable Long id,
                                             @Valid @RequestBody CambioEstadoCatalogo cambio) {
        return servicio.cambiarEstadoRubro(id, cambio.estado());
    }

    // ---------- Subrubros ----------

    /**
     * GET /api/subrubros?rubro=3
     *
     * Devuelve solo los que se pueden usar en un presupuesto nuevo: activos y
     * con su rubro activo. Lo consume la carga de items del presupuesto.
     */
    @GetMapping("/subrubros")
    public List<SubrubroRespuesta> listarSubrubrosDisponibles(
            @RequestParam(required = false) Long rubro) {

        return servicio.listarSubrubrosDisponibles(rubro);
    }

    /** POST /api/rubros/{idRubro}/subrubros */
    @PostMapping("/rubros/{idRubro}/subrubros")
    public ResponseEntity<SubrubroRespuesta> crearSubrubro(
            @PathVariable Long idRubro,
            @Valid @RequestBody SubrubroSolicitud solicitud) {

        SubrubroRespuesta creado = servicio.crearSubrubro(idRubro, solicitud);
        return ResponseEntity
                .created(URI.create("/api/subrubros/" + creado.idSubrubro()))
                .body(creado);
    }

    /** PUT /api/subrubros/{id} — solo el nombre; el rubro al que pertenece no cambia. */
    @PutMapping("/subrubros/{id}")
    public SubrubroRespuesta renombrarSubrubro(@PathVariable Long id,
                                               @Valid @RequestBody SubrubroSolicitud solicitud) {
        return servicio.renombrarSubrubro(id, solicitud);
    }

    /** PATCH /api/subrubros/{id}/estado */
    @PatchMapping("/subrubros/{id}/estado")
    public SubrubroRespuesta cambiarEstadoSubrubro(@PathVariable Long id,
                                                   @Valid @RequestBody CambioEstadoCatalogo cambio) {
        return servicio.cambiarEstadoSubrubro(id, cambio.estado());
    }
}
