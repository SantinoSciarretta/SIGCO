package com.sigco.proveedores;

import com.sigco.proveedores.dto.ProveedorDtos.CotizacionRespuesta;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Comparador de cotizaciones.
 *
 * Cuelga de la direccion del material y no de la del proveedor porque asi es
 * como se lo consulta: se parte de "necesito cemento" y se pregunta quien lo
 * ofrece, no al reves.
 *
 * Va en un controlador propio, aparte de ProveedorController, para que la ruta
 * quede donde corresponde sin mezclar dos jerarquias de recursos en una misma
 * clase.
 */
@RestController
public class ComparadorController {

    private final ProveedorService servicio;

    public ComparadorController(ProveedorService servicio) {
        this.servicio = servicio;
    }

    /**
     * GET /api/materiales/{id}/cotizaciones
     *
     * Ultima cotizacion de cada proveedor activo para ese material, de menor a
     * mayor precio. Es la respuesta a "quien me lo hace mas barato hoy".
     *
     * Devuelve 404 si el material no existe, para no confundir ese caso con
     * "todavia nadie lo cotizo", que devuelve una lista vacia.
     */
    @GetMapping("/api/materiales/{id}/cotizaciones")
    public List<CotizacionRespuesta> compararPorMaterial(@PathVariable Long id) {
        return servicio.compararPorMaterial(id);
    }
}
