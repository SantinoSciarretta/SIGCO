package com.sigco.proveedores;

import com.sigco.proveedores.dto.ProveedorDtos.CambioEstadoProveedor;
import com.sigco.proveedores.dto.ProveedorDtos.CotizacionRespuesta;
import com.sigco.proveedores.dto.ProveedorDtos.CotizacionSolicitud;
import com.sigco.proveedores.dto.ProveedorDtos.ObservacionRespuesta;
import com.sigco.proveedores.dto.ProveedorDtos.ObservacionSolicitud;
import com.sigco.proveedores.dto.ProveedorDtos.ProveedorRespuesta;
import com.sigco.proveedores.dto.ProveedorDtos.ProveedorSolicitud;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
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
 * API REST del modulo Proveedores.
 *
 * Sin DELETE en ninguno de los tres recursos, y por motivos distintos:
 *
 *   proveedor   · borrarlo eliminaria su historial de precios y comportamiento
 *   cotizacion  · es el registro de un precio informado en una fecha; si el
 *                 precio cambia se agrega otra, no se corrige la anterior
 *   observacion · es el registro de un hecho ocurrido en un pedido
 */
@RestController
@RequestMapping("/api/proveedores")
/*
 * Modulo 14 (Accesos): la clase exige el permiso de lectura del modulo y cada
 * metodo que escribe lo sobreescribe con el de edicion. El texto del permiso es
 * el mismo que figura en la tabla permiso de la base (migracion V13), asi que
 * la matriz del informe se puede verificar buscando esa cadena en el codigo.
 */
@PreAuthorize("hasAuthority('proveedores.ver')")
public class ProveedorController {

    private final ProveedorService servicio;

    public ProveedorController(ProveedorService servicio) {
        this.servicio = servicio;
    }

    // ---------- Proveedores ----------

    /** GET /api/proveedores?busqueda=corralon&zona=norte&estado=Activo */
    @GetMapping
    public List<ProveedorRespuesta> listar(
            @RequestParam(required = false) String busqueda,
            @RequestParam(required = false) String zona,
            @RequestParam(required = false) String estado) {

        return servicio.listar(busqueda, zona, estado);
    }

    /** GET /api/proveedores/zonas — las zonas ya cargadas, para el filtro. */
    @GetMapping("/zonas")
    public List<String> zonas() {
        return servicio.zonas();
    }

    /** GET /api/proveedores/{id} */
    @GetMapping("/{id}")
    public ProveedorRespuesta obtener(@PathVariable Long id) {
        return servicio.obtener(id);
    }

    /** POST /api/proveedores */
    @PreAuthorize("hasAuthority('proveedores.editar')")
    @PostMapping
    public ResponseEntity<ProveedorRespuesta> crear(
            @Valid @RequestBody ProveedorSolicitud solicitud) {

        ProveedorRespuesta creado = servicio.crear(solicitud);
        return ResponseEntity
                .created(URI.create("/api/proveedores/" + creado.idProveedor()))
                .body(creado);
    }

    /** PUT /api/proveedores/{id} */
    @PreAuthorize("hasAuthority('proveedores.editar')")
    @PutMapping("/{id}")
    public ProveedorRespuesta actualizar(@PathVariable Long id,
                                         @Valid @RequestBody ProveedorSolicitud solicitud) {
        return servicio.actualizar(id, solicitud);
    }

    /** PATCH /api/proveedores/{id}/estado */
    @PreAuthorize("hasAuthority('proveedores.editar')")
    @PatchMapping("/{id}/estado")
    public ProveedorRespuesta cambiarEstado(@PathVariable Long id,
                                            @Valid @RequestBody CambioEstadoProveedor cambio) {
        return servicio.cambiarEstado(id, cambio.estado());
    }

    // ---------- Cotizaciones ----------

    /** POST /api/proveedores/{id}/cotizaciones — registra un precio informado. */
    @PreAuthorize("hasAuthority('proveedores.editar')")
    @PostMapping("/{id}/cotizaciones")
    public ResponseEntity<CotizacionRespuesta> registrarCotizacion(
            @PathVariable Long id, @Valid @RequestBody CotizacionSolicitud solicitud) {

        CotizacionRespuesta creada = servicio.registrarCotizacion(id, solicitud);
        return ResponseEntity
                .created(URI.create("/api/proveedores/" + id + "/cotizaciones"))
                .body(creada);
    }

    /** GET /api/proveedores/{id}/cotizaciones — historial del proveedor. */
    @GetMapping("/{id}/cotizaciones")
    public List<CotizacionRespuesta> historialDeCotizaciones(@PathVariable Long id) {
        return servicio.historialDeCotizaciones(id);
    }

    // ---------- Observaciones ----------

    /** POST /api/proveedores/{id}/observaciones */
    @PreAuthorize("hasAuthority('proveedores.editar')")
    @PostMapping("/{id}/observaciones")
    public ResponseEntity<ObservacionRespuesta> registrarObservacion(
            @PathVariable Long id, @Valid @RequestBody ObservacionSolicitud solicitud) {

        ObservacionRespuesta creada = servicio.registrarObservacion(id, solicitud);
        return ResponseEntity
                .created(URI.create("/api/proveedores/" + id + "/observaciones"))
                .body(creada);
    }

    /** GET /api/proveedores/{id}/observaciones */
    @GetMapping("/{id}/observaciones")
    public List<ObservacionRespuesta> observaciones(@PathVariable Long id) {
        return servicio.observacionesDe(id);
    }
}
