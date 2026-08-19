package com.sigco.obras;

import com.sigco.obras.dto.CambioEstadoObra;
import com.sigco.obras.dto.ObraEdicion;
import com.sigco.obras.dto.ObraRespuesta;
import com.sigco.obras.dto.ObraSolicitud;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
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
 * API REST del modulo Obras.
 *
 * Igual que en Clientes, no existe DELETE: el informe establece que una obra no
 * se elimina, solo se cancela, para no perder la trazabilidad de los
 * presupuestos, gastos y cobros que ya pueda tener asociados.
 */
@RestController
@RequestMapping("/api/obras")
public class ObraController {

    private final ObraService servicio;

    public ObraController(ObraService servicio) {
        this.servicio = servicio;
    }

    /**
     * GET /api/obras
     *
     * Filtros combinables, todos opcionales:
     *   ?cliente=3&tipoObra=Reforma&estado=En ejecución&desde=2026-01-01&hasta=2026-12-31&busqueda=cabildo
     *
     * El filtro por cliente es el que usa la ficha del cliente para mostrar su
     * historial de obras.
     */
    @GetMapping
    public List<ObraRespuesta> listar(
            @RequestParam(required = false) Long cliente,
            @RequestParam(required = false) String tipoObra,
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) String busqueda) {

        return servicio.listar(cliente, tipoObra, estado, desde, hasta, busqueda);
    }

    /** GET /api/obras/{id} */
    @GetMapping("/{id}")
    public ObraRespuesta obtener(@PathVariable Long id) {
        return servicio.obtener(id);
    }

    /** POST /api/obras — la obra nace "En presupuestación". */
    @PostMapping
    public ResponseEntity<ObraRespuesta> crear(@Valid @RequestBody ObraSolicitud solicitud) {
        ObraRespuesta creada = servicio.crear(solicitud);
        return ResponseEntity
                .created(URI.create("/api/obras/" + creada.idObra()))
                .body(creada);
    }

    /** PUT /api/obras/{id} — datos maestros. No admite cambiar cliente ni tipo de obra. */
    @PutMapping("/{id}")
    public ObraRespuesta actualizar(@PathVariable Long id,
                                    @Valid @RequestBody ObraEdicion edicion) {
        return servicio.actualizar(id, edicion);
    }

    /**
     * PATCH /api/obras/{id}/estado — avanza el ciclo de vida de la obra.
     *
     * Devuelve 409 cuando la transicion pedida no es valida (por ejemplo,
     * finalizar una obra que todavia esta en presupuestacion) o cuando falta
     * el motivo al cancelar.
     */
    @PatchMapping("/{id}/estado")
    public ObraRespuesta cambiarEstado(@PathVariable Long id,
                                       @Valid @RequestBody CambioEstadoObra cambio) {
        return servicio.cambiarEstado(id, cambio);
    }
}
