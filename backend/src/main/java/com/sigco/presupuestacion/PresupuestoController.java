package com.sigco.presupuestacion;

import com.sigco.presupuestacion.dto.PresupuestoDtos.CambioEstadoPresupuesto;
import com.sigco.presupuestacion.dto.PresupuestoDtos.Duplicacion;
import com.sigco.presupuestacion.dto.PresupuestoDtos.ItemSolicitud;
import com.sigco.presupuestacion.dto.PresupuestoDtos.NuevoPresupuesto;
import com.sigco.presupuestacion.dto.PresupuestoDtos.PlanDePago;
import com.sigco.presupuestacion.dto.PresupuestoRespuesta;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
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
 * API REST del modulo Presupuestacion.
 *
 * El informe establece que un presupuesto no se elimina, solo se marca
 * Rechazado, para conservar la trazabilidad de la negociacion con el cliente.
 * El DELETE de presupuestos es una extension pedida para poder probar sin
 * arrastrar registros, e incluye los aprobados. El unico caso que rechaza es
 * el presupuesto que sea base de otro, y eso es integridad referencial, no una
 * regla de negocio. Ver PresupuestoService.eliminar.
 *
 * El DELETE de items es mas antiguo y no contradice nada: un item se puede
 * quitar unicamente mientras el presupuesto esta en Borrador, es decir antes de
 * que el cliente lo haya visto.
 */
@RestController
@RequestMapping("/api/presupuestos")
public class PresupuestoController {

    private final PresupuestoService servicio;

    public PresupuestoController(PresupuestoService servicio) {
        this.servicio = servicio;
    }

    /** GET /api/presupuestos?obra=3&tipo=Definitivo&estado=Enviado */
    @GetMapping
    public List<PresupuestoRespuesta> listar(
            @RequestParam(required = false) Long obra,
            @RequestParam(required = false) String tipo,
            @RequestParam(required = false) String estado) {

        return servicio.listar(obra, tipo, estado);
    }

    /** GET /api/presupuestos/{id} — con items y subtotales por rubro. */
    @GetMapping("/{id}")
    public PresupuestoRespuesta obtener(@PathVariable Long id) {
        return servicio.obtener(id);
    }

    /** POST /api/presupuestos — nace en Borrador. */
    @PostMapping
    public ResponseEntity<PresupuestoRespuesta> crear(
            @Valid @RequestBody NuevoPresupuesto solicitud) {

        PresupuestoRespuesta creado = servicio.crear(solicitud);
        return ResponseEntity
                .created(URI.create("/api/presupuestos/" + creado.idPresupuesto()))
                .body(creado);
    }

    /**
     * POST /api/presupuestos/{id}/duplicar
     *
     * Crea un presupuesto nuevo con una copia de los items del indicado, y los
     * deja vinculados. Es lo que permite armar el definitivo a partir del
     * anteproyecto sin sobrescribirlo, y reutilizar un presupuesto de otra obra
     * como plantilla.
     */
    @PostMapping("/{id}/duplicar")
    public ResponseEntity<PresupuestoRespuesta> duplicar(
            @PathVariable Long id, @Valid @RequestBody Duplicacion solicitud) {

        PresupuestoRespuesta creado = servicio.duplicar(id, solicitud);
        return ResponseEntity
                .created(URI.create("/api/presupuestos/" + creado.idPresupuesto()))
                .body(creado);
    }

    /**
     * GET /api/presupuestos/{id}/pdf
     *
     * Devuelve el presupuesto como PDF con el membrete de la empresa, listo
     * para enviarle al cliente. Los precios unitarios NO aparecen: solo el
     * subtotal de cada rubro, como pide el informe.
     *
     * Content-Disposition inline hace que el navegador lo abra en una pestaña
     * en lugar de descargarlo directamente, que es lo comodo para revisarlo
     * antes de mandarlo.
     */
    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> generarPdf(@PathVariable Long id) {
        byte[] pdf = servicio.generarPdf(id);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"presupuesto-" + id + ".pdf\"")
                .body(pdf);
    }

    // ---------- Items ----------

    /** POST /api/presupuestos/{id}/items */
    @PostMapping("/{id}/items")
    public PresupuestoRespuesta agregarItem(@PathVariable Long id,
                                            @Valid @RequestBody ItemSolicitud solicitud) {
        return servicio.agregarItem(id, solicitud);
    }

    /** PUT /api/presupuestos/{id}/items/{idItem} */
    @PutMapping("/{id}/items/{idItem}")
    public PresupuestoRespuesta actualizarItem(@PathVariable Long id,
                                               @PathVariable Long idItem,
                                               @Valid @RequestBody ItemSolicitud solicitud) {
        return servicio.actualizarItem(id, idItem, solicitud);
    }

    /** DELETE /api/presupuestos/{id}/items/{idItem} — solo en Borrador. */
    @DeleteMapping("/{id}/items/{idItem}")
    public PresupuestoRespuesta quitarItem(@PathVariable Long id, @PathVariable Long idItem) {
        return servicio.quitarItem(id, idItem);
    }

    // ---------- Plan de pago y estado ----------

    /** PUT /api/presupuestos/{id}/plan-de-pago */
    @PutMapping("/{id}/plan-de-pago")
    public PresupuestoRespuesta definirPlanDePago(@PathVariable Long id,
                                                  @Valid @RequestBody PlanDePago plan) {
        return servicio.definirPlanDePago(id, plan);
    }

    /**
     * PATCH /api/presupuestos/{id}/estado
     *
     * Aprobar el definitivo pone ademas la obra en ejecucion.
     */
    @PatchMapping("/{id}/estado")
    public PresupuestoRespuesta cambiarEstado(
            @PathVariable Long id, @Valid @RequestBody CambioEstadoPresupuesto cambio) {
        return servicio.cambiarEstado(id, cambio);
    }

    /**
     * DELETE /api/presupuestos/{id}
     *
     * Devuelve 204 sin cuerpo si se elimino, 404 si no existe y 409 si otro
     * presupuesto lo tiene como base.
     *
     * Si el eliminado era el definitivo aprobado, la obra vuelve a
     * "En presupuestacion" y pierde su fecha de inicio real.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        servicio.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
