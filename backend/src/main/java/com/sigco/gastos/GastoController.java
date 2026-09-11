package com.sigco.gastos;

import com.sigco.gastos.dto.GastoDtos.AnulacionGasto;
import com.sigco.gastos.dto.GastoDtos.GastoSolicitud;
import com.sigco.gastos.dto.GastoRespuesta;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
 * API REST del modulo Gastos.
 *
 * No hay DELETE: un gasto no se elimina, se anula con un motivo. Es la regla
 * explicita del informe, para conservar la trazabilidad completa de la obra.
 */
@RestController
@RequestMapping("/api/gastos")
/*
 * Modulo 14 (Accesos): la clase exige el permiso de lectura del modulo y cada
 * metodo que escribe lo sobreescribe con el de edicion. El texto del permiso es
 * el mismo que figura en la tabla permiso de la base (migracion V13), asi que
 * la matriz del informe se puede verificar buscando esa cadena en el codigo.
 */
@PreAuthorize("hasAuthority('gastos.ver')")
public class GastoController {

    private final GastoService servicio;

    public GastoController(GastoService servicio) {
        this.servicio = servicio;
    }

    /** GET /api/gastos?obra=&rubro=&tipo=&estado=&desde=&hasta= */
    @GetMapping
    public List<GastoRespuesta> listar(
            @RequestParam(required = false) Long obra,
            @RequestParam(required = false) Long rubro,
            @RequestParam(required = false) String tipo,
            @RequestParam(required = false) String estado,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return servicio.listar(obra, rubro, tipo, estado, desde, hasta);
    }

    @GetMapping("/{id}")
    public GastoRespuesta obtener(@PathVariable Long id) {
        return servicio.obtener(id);
    }

    @PreAuthorize("hasAuthority('gastos.editar')")
    @PostMapping
    public ResponseEntity<GastoRespuesta> crear(@Valid @RequestBody GastoSolicitud solicitud) {
        GastoRespuesta creado = servicio.crear(solicitud);
        return ResponseEntity
                .created(URI.create("/api/gastos/" + creado.idGasto()))
                .body(creado);
    }

    @PreAuthorize("hasAuthority('gastos.editar')")
    @PutMapping("/{id}")
    public GastoRespuesta actualizar(@PathVariable Long id,
                                     @Valid @RequestBody GastoSolicitud solicitud) {
        return servicio.actualizar(id, solicitud);
    }

    @PreAuthorize("hasAuthority('gastos.editar')")
    @PatchMapping("/{id}/anulacion")
    public GastoRespuesta anular(@PathVariable Long id,
                                 @Valid @RequestBody AnulacionGasto anulacion) {
        return servicio.anular(id, anulacion);
    }
}
