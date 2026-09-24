package com.sigco.dashboard;

import com.sigco.dashboard.dto.BalanceDeObra;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * El balance economico de una obra.
 *
 * ------------------------------------------------------------------
 *  Por que pide dos permisos y no uno
 * ------------------------------------------------------------------
 *
 * El balance junta lo gastado con lo cobrado y muestra la ganancia. Segun la
 * matriz del informe, el Capataz General tiene consulta sobre Gastos pero NO
 * accede a Cobros, y el Capataz de Obra no accede a ninguno de los dos. Si este
 * endpoint exigiera solo `gastos.ver`, el capataz general veria por aca la
 * informacion financiera que la matriz le niega.
 *
 * Exigir los dos permisos deja el balance donde corresponde: hoy, solo el
 * Dueño. Y si mañana se crea un rol que administre cobros, alcanza con darle
 * los dos permisos, sin tocar este archivo.
 *
 * No va bajo /api/obras aunque hable de una obra: ese prefijo es de
 * ObraController y el balance no es un dato de la obra sino el cruce de tres
 * modulos sobre ella.
 */
@RestController
@RequestMapping("/api/balance")
@PreAuthorize("hasAuthority('gastos.ver') and hasAuthority('cobros.ver')")
public class BalanceController {

    private final BalanceService servicio;

    public BalanceController(BalanceService servicio) {
        this.servicio = servicio;
    }

    /** GET /api/balance/{idObra} */
    @GetMapping("/{idObra}")
    public BalanceDeObra de(@PathVariable Long idObra) {
        return servicio.de(idObra);
    }
}
