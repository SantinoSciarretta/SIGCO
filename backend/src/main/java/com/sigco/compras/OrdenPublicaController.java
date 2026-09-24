package com.sigco.compras;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * La orden de pedido, abierta para el corralon.
 *
 * ------------------------------------------------------------------
 *  Por que existe un endpoint sin login
 * ------------------------------------------------------------------
 *
 * El pedido se le manda al corralon por WhatsApp, y un link wa.me no puede
 * adjuntar archivos: solo lleva texto. Asi que el PDF viaja como enlace, y ese
 * enlace lo abre alguien que no tiene —ni va a tener— cuenta en SIGCO.
 *
 * ------------------------------------------------------------------
 *  Como esta protegido
 * ------------------------------------------------------------------
 *
 * Por el token, que son 32 bytes de azar: no se adivina ni se enumera. Y por el
 * vencimiento, porque un link que vive para siempre en un chat de WhatsApp que
 * se reenvia es un link que tarde o temprano termina en cualquier lado.
 *
 * Ademas, lo que se expone es acotado a proposito desde el primer dia: la orden
 * lleva materiales, cantidades, precios acordados y donde entregar. NO lleva
 * presupuesto, gasto, ganancia ni el nombre del cliente. Se diseño asi
 * justamente porque es un documento que sale de la empresa hacia afuera.
 *
 * ------------------------------------------------------------------
 *  Por que vive en su propia ruta
 * ------------------------------------------------------------------
 *
 * Mismo criterio que /api/vidriera y /api/archivos/publico: lo publico tiene su
 * propio prefijo y no se mezcla con /api/pedidos, que exige permisos. Asi, leer
 * la configuracion de seguridad alcanza para saber que queda abierto, sin tener
 * que revisar endpoint por endpoint.
 */
@RestController
@RequestMapping("/api/ordenes-publicas")
public class OrdenPublicaController {

    private final PedidoService servicio;

    public OrdenPublicaController(PedidoService servicio) {
        this.servicio = servicio;
    }

    /** GET /api/ordenes-publicas/{token} — el PDF de la orden. */
    @GetMapping("/{token}")
    public ResponseEntity<byte[]> orden(@PathVariable String token) {
        byte[] pdf = servicio.ordenPorToken(token);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                // inline para que se abra en el navegador del telefono en lugar
                // de descargarse: el corralon quiere leerlo, no archivarlo.
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"orden.pdf\"")
                .body(pdf);
    }
}
