package com.sigco.gastos;

import com.sigco.gastos.dto.EstadoFinanciero;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Estado financiero de una obra: presupuestado contra gastado, por rubro.
 *
 * La ruta cuelga de la obra y no de /api/gastos porque asi es como se consulta:
 * se parte de "como viene esta obra", no de "listame gastos". Va en un
 * controlador propio, igual que el comparador de cotizaciones en Proveedores,
 * para no mezclar dos jerarquias de recursos en la misma clase.
 *
 * Es la vista que hoy no existe: el dueño conoce el resultado economico de una
 * obra recien cuando termina.
 */
@RestController
/*
 * Modulo 14 (Accesos): la clase exige el permiso de lectura del modulo y cada
 * metodo que escribe lo sobreescribe con el de edicion. El texto del permiso es
 * el mismo que figura en la tabla permiso de la base (migracion V13), asi que
 * la matriz del informe se puede verificar buscando esa cadena en el codigo.
 */
@PreAuthorize("hasAuthority('gastos.ver')")
public class EstadoFinancieroController {

    private final GastoService servicio;

    public EstadoFinancieroController(GastoService servicio) {
        this.servicio = servicio;
    }

    /**
     * GET /api/obras/{id}/estado-financiero
     *
     * Devuelve 409 si la obra no tiene un presupuesto definitivo aprobado: sin
     * eso no hay contra que comparar, y devolver ceros seria mentir.
     */
    @GetMapping("/api/obras/{id}/estado-financiero")
    public EstadoFinanciero de(@PathVariable Long id) {
        return servicio.estadoFinanciero(id);
    }
}
