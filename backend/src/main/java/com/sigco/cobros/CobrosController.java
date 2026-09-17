package com.sigco.cobros;

import com.sigco.cobros.dto.CobrosDtos.AnularPago;
import com.sigco.cobros.dto.CobrosDtos.CuotaRespuesta;
import com.sigco.cobros.dto.CobrosDtos.GenerarPlan;
import com.sigco.cobros.dto.CobrosDtos.IndiceCacRespuesta;
import com.sigco.cobros.dto.CobrosDtos.NuevoIndiceCac;
import com.sigco.cobros.dto.CobrosDtos.PlanDeCobro;
import com.sigco.cobros.dto.CobrosDtos.PreviaCac;
import com.sigco.cobros.dto.CobrosDtos.RegistrarPago;
import com.sigco.cobros.dto.CobrosDtos.ResumenCobro;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * API REST del modulo Cobros.
 *
 * No hay DELETE. Un plan de cobro no se borra y un pago tampoco: se anula
 * dejando el motivo, para conservar la trazabilidad del estado de cuenta.
 *
 * TODO: al integrar Accesos, restringir TODO este modulo al rol dueño. El
 *       informe lo define como informacion financiera no delegable.
 */
@RestController
/*
 * Modulo 14 (Accesos): la clase exige el permiso de lectura del modulo y cada
 * metodo que escribe lo sobreescribe con el de edicion. El texto del permiso es
 * el mismo que figura en la tabla permiso de la base (migracion V13), asi que
 * la matriz del informe se puede verificar buscando esa cadena en el codigo.
 */
@PreAuthorize("hasAuthority('cobros.ver')")
public class CobrosController {

    private final CobrosService servicio;

    public CobrosController(CobrosService servicio) {
        this.servicio = servicio;
    }

    // ---------- Plan de cobro, por obra ----------

    /** GET /api/obras/{id}/cobros — plan completo con su saldo. */
    @GetMapping("/api/obras/{id}/cobros")
    public PlanDeCobro plan(@PathVariable Long id) {
        return servicio.plan(id);
    }

    /**
     * POST /api/obras/{id}/cobros
     *
     * Genera el plan a partir del definitivo aprobado. Lo único que se pide es
     * la fecha del primer vencimiento: el anticipo, la cantidad de cuotas y el
     * total salen del presupuesto que el cliente aceptó.
     */
    @PreAuthorize("hasAuthority('cobros.editar')")
    @PostMapping("/api/obras/{id}/cobros")
    public PlanDeCobro generar(@PathVariable Long id,
                               @Valid @RequestBody GenerarPlan solicitud) {
        return servicio.generar(id, solicitud);
    }

    /** GET /api/obras/{id}/cobros/previa-cac — efecto del ajuste antes de aplicarlo. */
    @GetMapping("/api/obras/{id}/cobros/previa-cac")
    public PreviaCac previaCac(@PathVariable Long id) {
        return servicio.previaCac(id);
    }

    /** POST /api/obras/{id}/cobros/actualizacion-cac */
    @PreAuthorize("hasAuthority('cobros.editar')")
    @PostMapping("/api/obras/{id}/cobros/actualizacion-cac")
    public PlanDeCobro aplicarCac(@PathVariable Long id) {
        return servicio.aplicarCac(id);
    }

    // ---------- Vista consolidada y alertas ----------

    /** GET /api/cobros — cuánto resta cobrar de cada obra. */
    /**
     * GET /api/obras/{id}/cobros/planilla — la planilla de pagos en PDF.
     *
     * inline y no attachment: el navegador la abre en una pestaña para
     * revisarla antes de mandarla, que es lo que se hace en la practica.
     */
    @PreAuthorize("hasAuthority('cobros.ver')")
    @GetMapping("/api/obras/{id}/cobros/planilla")
    public ResponseEntity<byte[]> planilla(@PathVariable Long id) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + servicio.nombreDePlanilla(id) + "\"")
                .body(servicio.generarPlanilla(id));
    }

    @GetMapping("/api/cobros")
    public List<ResumenCobro> consolidado() {
        return servicio.consolidado();
    }

    /** GET /api/cobros/alertas — cuotas por vencer o vencidas. */
    @GetMapping("/api/cobros/alertas")
    public List<CuotaRespuesta> alertas() {
        return servicio.alertas();
    }

    // ---------- Pagos, por cuota ----------

    @PreAuthorize("hasAuthority('cobros.editar')")
    @PatchMapping("/api/cuotas/{id}/pago")
    public PlanDeCobro registrarPago(@PathVariable Long id,
                                     @Valid @RequestBody RegistrarPago pago) {
        return servicio.registrarPago(id, pago);
    }

    @PreAuthorize("hasAuthority('cobros.editar')")
    @PatchMapping("/api/cuotas/{id}/anulacion")
    public PlanDeCobro anularPago(@PathVariable Long id,
                                  @Valid @RequestBody AnularPago anulacion) {
        return servicio.anularPago(id, anulacion);
    }

    // ---------- Índice CAC ----------

    @GetMapping("/api/cac")
    public List<IndiceCacRespuesta> indices() {
        return servicio.indices();
    }

    /** El valor se carga a mano: la importación automática está fuera del alcance. */
    @PreAuthorize("hasAuthority('cobros.editar')")
    @PostMapping("/api/cac")
    public IndiceCacRespuesta registrarIndice(@Valid @RequestBody NuevoIndiceCac solicitud) {
        return servicio.registrarIndice(solicitud);
    }
}
