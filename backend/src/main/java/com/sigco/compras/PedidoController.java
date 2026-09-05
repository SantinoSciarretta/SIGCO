package com.sigco.compras;

import com.sigco.compras.dto.PedidoDtos.Anulacion;
import com.sigco.compras.dto.PedidoDtos.Aprobacion;
import com.sigco.compras.dto.PedidoDtos.NuevoPedido;
import com.sigco.compras.dto.PedidoDtos.Recepcion;
import com.sigco.compras.dto.PedidoRespuesta;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * API REST del modulo Compras.
 *
 * No hay DELETE: un pedido no se elimina, se anula con un motivo. Es la misma
 * regla que rige en Obras y Gastos, y por el mismo motivo: el historial de lo
 * que se pidio a cada proveedor es informacion que hoy la empresa pierde.
 *
 * Las tres transiciones del circuito son PATCH sobre un sub-recurso y no un
 * PUT del pedido completo, porque cada una es una accion distinta con reglas y
 * responsables distintos, no una edicion generica.
 */
@RestController
@RequestMapping("/api/pedidos")
public class PedidoController {

    private final PedidoService servicio;

    public PedidoController(PedidoService servicio) {
        this.servicio = servicio;
    }

    /** GET /api/pedidos?obra=&proveedor=&estado= */
    @GetMapping
    public List<PedidoRespuesta> listar(
            @RequestParam(required = false) Long obra,
            @RequestParam(required = false) Long proveedor,
            @RequestParam(required = false) String estado) {
        return servicio.listar(obra, proveedor, estado);
    }

    /** GET /api/pedidos/pendientes — panel de aprobacion del dueño. */
    @GetMapping("/pendientes")
    public List<PedidoRespuesta> pendientes() {
        return servicio.pendientesDeAprobacion();
    }

    @GetMapping("/{id}")
    public PedidoRespuesta obtener(@PathVariable Long id) {
        return servicio.obtener(id);
    }

    /**
     * GET /api/pedidos/{id}/precios-sugeridos?proveedor=
     *
     * Ultima cotizacion de ese proveedor para cada material del pedido. La
     * pantalla de aprobacion la usa para precargar los precios en lugar de que
     * el dueño los escriba de memoria.
     */
    @GetMapping("/{id}/precios-sugeridos")
    public Map<Long, BigDecimal> preciosSugeridos(@PathVariable Long id,
                                                  @RequestParam Long proveedor) {
        return servicio.sugerirPrecios(id, proveedor);
    }

    @PostMapping
    public ResponseEntity<PedidoRespuesta> crear(@Valid @RequestBody NuevoPedido solicitud) {
        PedidoRespuesta creado = servicio.crear(solicitud);
        return ResponseEntity
                .created(URI.create("/api/pedidos/" + creado.idPedido()))
                .body(creado);
    }

    /**
     * PATCH /api/pedidos/{id}/aprobacion
     *
     * Aprueba, asigna proveedor y confirma precios en un solo paso, porque el
     * dueño toma las tres decisiones juntas. Accion no delegable.
     */
    @PatchMapping("/{id}/aprobacion")
    public PedidoRespuesta aprobar(@PathVariable Long id,
                                   @Valid @RequestBody Aprobacion aprobacion) {
        return servicio.aprobar(id, aprobacion);
    }

    /**
     * PATCH /api/pedidos/{id}/recepcion
     *
     * Se completa desde el celular en la obra. La foto del remito es
     * obligatoria y la nota define si quedo Recibido Completo o con Diferencias.
     */
    @PatchMapping("/{id}/recepcion")
    public PedidoRespuesta recibir(@PathVariable Long id,
                                   @Valid @RequestBody Recepcion recepcion) {
        return servicio.recibir(id, recepcion);
    }

    @PatchMapping("/{id}/anulacion")
    public PedidoRespuesta anular(@PathVariable Long id,
                                  @Valid @RequestBody Anulacion anulacion) {
        return servicio.anular(id, anulacion);
    }
}
