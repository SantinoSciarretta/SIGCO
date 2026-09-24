package com.sigco.compras;

import com.sigco.compras.dto.PedidoDtos.Anulacion;
import com.sigco.compras.dto.PedidoDtos.Aprobacion;
import com.sigco.compras.dto.PedidoDtos.NuevoPedido;
import com.sigco.compras.dto.PedidoDtos.Recepcion;
import com.sigco.compras.dto.EnvioPorWhatsApp;
import com.sigco.compras.dto.PedidoRespuesta;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
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
/*
 * Modulo 14 (Accesos): la clase exige el permiso de lectura del modulo y cada
 * metodo que escribe lo sobreescribe con el de edicion. El texto del permiso es
 * el mismo que figura en la tabla permiso de la base (migracion V13), asi que
 * la matriz del informe se puede verificar buscando esa cadena en el codigo.
 */
@PreAuthorize("hasAuthority('compras.ver')")
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

    @PreAuthorize("hasAuthority('compras.editar')")
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
    // Permiso propio y distinto del resto del modulo: la aprobacion del pedido
    // es indelegable segun el informe, y ningun capataz lo tiene. Que sea un
    // permiso separado es lo que hace cumplir esa regla, en lugar de confiar en
    // que nadie se la asigne.
    @PreAuthorize("hasAuthority('compras.aprobar')")
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
    @PreAuthorize("hasAuthority('compras.editar')")
    @PatchMapping("/{id}/recepcion")
    public PedidoRespuesta recibir(@PathVariable Long id,
                                   @Valid @RequestBody Recepcion recepcion) {
        return servicio.recibir(id, recepcion);
    }

    /**
     * GET /api/pedidos/{id}/orden — el PDF para mandarle al proveedor.
     *
     * Solo compras.ver, como el resto de las lecturas del modulo: generar el
     * documento no cambia nada del pedido. Quien puede verlo puede mandarlo.
     */
    @GetMapping("/{id}/orden")
    public ResponseEntity<byte[]> orden(@PathVariable Long id) {
        byte[] pdf = servicio.generarOrden(id);

        return ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.APPLICATION_PDF)
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + servicio.nombreDeOrden(id) + "\"")
                .body(pdf);
    }

    /**
     * POST /api/pedidos/{id}/envio-whatsapp
     *
     * Prepara el envio de la orden al corralon: genera el link publico del PDF
     * y devuelve el link de WhatsApp con el mensaje listo. NO manda nada — el
     * envio lo hace el dueño desde su propio WhatsApp.
     *
     * Exige compras.aprobar y no compras.editar, que es mas fuerte de lo que
     * parece. El informe dice que enviarle el pedido al proveedor es del dueño
     * y no se delega; los capataces tienen compras.editar para armar pedidos y
     * confirmar recepciones. Si este endpoint pidiera compras.editar, un
     * capataz podria mandarle una orden a un proveedor por su cuenta, y ademas
     * generar un link publico de un documento de la empresa.
     *
     * Es POST y no GET porque cambia algo: genera un token nuevo y deja sin
     * efecto el anterior.
     */
    @PreAuthorize("hasAuthority('compras.aprobar')")
    @PostMapping("/{id}/envio-whatsapp")
    public EnvioPorWhatsApp prepararEnvio(@PathVariable Long id) {
        return servicio.prepararEnvioPorWhatsApp(id);
    }

    /**
     * DELETE /api/pedidos/{id}/envio-whatsapp
     *
     * Corta el link publico. Si la orden se le mando al corralon equivocado,
     * esperar a que venza no sirve de nada.
     */
    @PreAuthorize("hasAuthority('compras.aprobar')")
    @DeleteMapping("/{id}/envio-whatsapp")
    public PedidoRespuesta dejarDeCompartir(@PathVariable Long id) {
        return servicio.dejarDeCompartirOrden(id);
    }

    @PreAuthorize("hasAuthority('compras.editar')")
    @PatchMapping("/{id}/anulacion")
    public PedidoRespuesta anular(@PathVariable Long id,
                                  @Valid @RequestBody Anulacion anulacion) {
        return servicio.anular(id, anulacion);
    }
}
