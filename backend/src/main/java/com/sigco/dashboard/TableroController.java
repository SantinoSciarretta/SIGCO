package com.sigco.dashboard;

import com.sigco.dashboard.dto.TableroDtos.Tablero;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * API REST del Tablero.
 *
 * Un solo endpoint de lectura, y no hay mas. El Tablero no crea, no modifica y
 * no borra nada: todo lo que se puede hacer desde la pantalla lleva al modulo
 * que corresponde y se hace alli, con sus propias validaciones.
 *
 * Si el tablero tuviera endpoints de escritura, una regla de negocio terminaria
 * viviendo en dos lugares.
 */
@RestController
@RequestMapping("/api/tablero")
/*
 * Modulo 14 (Accesos): la clase exige el permiso de lectura del modulo y cada
 * metodo que escribe lo sobreescribe con el de edicion. El texto del permiso es
 * el mismo que figura en la tabla permiso de la base (migracion V13), asi que
 * la matriz del informe se puede verificar buscando esa cadena en el codigo.
 */
@PreAuthorize("hasAuthority('tablero.ver')")
public class TableroController {

    private final TableroService servicio;

    public TableroController(TableroService servicio) {
        this.servicio = servicio;
    }

    /**
     * GET /api/tablero — todo el panel en una sola respuesta.
     *
     * Una sola llamada y no cinco: la pantalla no sirve de a partes, y ademas
     * asi todos los numeros corresponden al mismo instante.
     */
    @GetMapping
    public Tablero tablero() {
        return servicio.armar();
    }
}
