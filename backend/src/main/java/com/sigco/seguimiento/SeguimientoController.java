package com.sigco.seguimiento;

import com.sigco.seguimiento.dto.SeguimientoDtos.AvanceObra;
import com.sigco.seguimiento.dto.SeguimientoDtos.ConfiguracionHitos;
import com.sigco.seguimiento.dto.SeguimientoDtos.Cumplimiento;
import com.sigco.seguimiento.dto.SeguimientoDtos.HitoRespuesta;
import com.sigco.seguimiento.dto.SeguimientoDtos.NuevaPlantilla;
import com.sigco.seguimiento.dto.SeguimientoDtos.ObservacionHito;
import com.sigco.seguimiento.dto.SeguimientoDtos.PlantillaRespuesta;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * API REST del modulo Seguimiento de Obras.
 *
 * Las rutas del avance y de los hitos cuelgan de la obra porque asi se
 * consultan: se parte de "como viene esta obra". Las de cumplimiento cuelgan
 * del hito, que es lo que se esta modificando.
 */
@RestController
/*
 * Modulo 14 (Accesos): la clase exige el permiso de lectura del modulo y cada
 * metodo que escribe lo sobreescribe con el de edicion. El texto del permiso es
 * el mismo que figura en la tabla permiso de la base (migracion V13), asi que
 * la matriz del informe se puede verificar buscando esa cadena en el codigo.
 */
@PreAuthorize("hasAuthority('seguimiento.ver')")
public class SeguimientoController {

    private final SeguimientoService servicio;

    public SeguimientoController(SeguimientoService servicio) {
        this.servicio = servicio;
    }

    // ---------- Avance y configuracion, por obra ----------

    /** GET /api/obras/{id}/avance — panel principal del modulo. */
    @GetMapping("/api/obras/{id}/avance")
    public AvanceObra avance(@PathVariable Long id) {
        return servicio.avance(id);
    }

    /**
     * PUT /api/obras/{id}/hitos
     *
     * Define el conjunto COMPLETO de hitos. Es PUT y no POST porque reemplaza
     * la configuración entera: la regla de que las ponderaciones sumen 100
     * aplica al conjunto, no a cada hito por separado.
     */
    @PreAuthorize("hasAuthority('seguimiento.editar')")
    @PutMapping("/api/obras/{id}/hitos")
    public List<HitoRespuesta> configurar(@PathVariable Long id,
                                          @Valid @RequestBody ConfiguracionHitos configuracion) {
        return servicio.configurar(id, configuracion);
    }

    /** POST /api/obras/{id}/hitos/desde-plantilla/{idPlantilla} */
    @PreAuthorize("hasAuthority('seguimiento.editar')")
    @PostMapping("/api/obras/{id}/hitos/desde-plantilla/{idPlantilla}")
    public List<HitoRespuesta> aplicarPlantilla(@PathVariable Long id,
                                                @PathVariable Long idPlantilla) {
        return servicio.aplicarPlantilla(id, idPlantilla);
    }

    // ---------- Cumplimiento, por hito ----------

    /**
     * PATCH /api/hitos/{id}/cumplimiento
     *
     * Al completar el último hito pendiente, la obra pasa a Finalizada.
     */
    @PreAuthorize("hasAuthority('seguimiento.editar')")
    @PatchMapping("/api/hitos/{id}/cumplimiento")
    public AvanceObra completar(@PathVariable Long id,
                                @Valid @RequestBody Cumplimiento cumplimiento) {
        return servicio.completar(id, cumplimiento);
    }

    @PreAuthorize("hasAuthority('seguimiento.editar')")
    @PatchMapping("/api/hitos/{id}/reapertura")
    public AvanceObra reabrir(@PathVariable Long id) {
        return servicio.reabrir(id);
    }

    @PreAuthorize("hasAuthority('seguimiento.editar')")
    @PatchMapping("/api/hitos/{id}/observacion")
    public AvanceObra registrarObservacion(@PathVariable Long id,
                                           @Valid @RequestBody ObservacionHito observacion) {
        return servicio.registrarObservacion(id, observacion);
    }

    // ---------- Plantillas ----------

    @GetMapping("/api/plantillas-hito")
    public List<PlantillaRespuesta> listarPlantillas() {
        return servicio.listarPlantillas();
    }

    @PreAuthorize("hasAuthority('seguimiento.editar')")
    @PostMapping("/api/plantillas-hito")
    public PlantillaRespuesta crearPlantilla(@Valid @RequestBody NuevaPlantilla solicitud) {
        return servicio.crearPlantilla(solicitud);
    }
}
