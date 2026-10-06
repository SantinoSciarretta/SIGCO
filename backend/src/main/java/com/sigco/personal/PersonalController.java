package com.sigco.personal;

import com.sigco.personal.dto.PersonalDtos.Asignacion;
import com.sigco.personal.dto.PersonalDtos.CambioEstadoOperario;
import com.sigco.personal.dto.PersonalDtos.InasistenciaRespuesta;
import com.sigco.personal.dto.PersonalDtos.InasistenciaSolicitud;
import com.sigco.personal.dto.PersonalDtos.MotivoSolicitud;
import com.sigco.personal.dto.PersonalDtos.OperarioRespuesta;
import com.sigco.personal.dto.PersonalDtos.OperarioSolicitud;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
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
 * API REST del modulo Personal.
 *
 * No hay DELETE de operarios: se desactivan. El informe pide conservar el
 * historial de obras en las que participo cada uno.
 *
 * El DELETE de /asignaciones/{idObra} tampoco borra: cierra la asignacion con
 * la fecha de hoy. Se usa DELETE porque desde afuera la accion es "sacalo de
 * esta obra"; lo que pasa adentro es que la fila queda con fecha de baja.
 */
@RestController
@RequestMapping("/api/operarios")
/*
 * Modulo 14 (Accesos): la clase exige el permiso de lectura del modulo y cada
 * metodo que escribe lo sobreescribe con el de edicion. El texto del permiso es
 * el mismo que figura en la tabla permiso de la base (migracion V13), asi que
 * la matriz del informe se puede verificar buscando esa cadena en el codigo.
 */
@PreAuthorize("hasAuthority('personal.ver')")
public class PersonalController {

    private final PersonalService servicio;

    /**
     * Constructor: Spring le entrega automáticamente el servicio que contiene
     * la lógica de Personal.
     */
    public PersonalController(PersonalService servicio) {
        this.servicio = servicio;
    }

    /**
     * GET /api/operarios: devuelve los operarios, filtrando opcionalmente por
     * nombre, estado y obra, con la cantidad de faltas de cada uno.
     */
    @GetMapping
    public List<OperarioRespuesta> listar(
            @RequestParam(required = false) String busqueda,
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) Long obra) {
        return servicio.listar(busqueda, estado, obra);
    }

    /**
     * GET /api/operarios/{id}: devuelve la ficha de un operario con las obras
     * en las que trabajó y sus faltas.
     */
    @GetMapping("/{id}")
    public OperarioRespuesta obtener(@PathVariable Long id) {
        return servicio.obtener(id);
    }

    /**
     * POST /api/operarios: da de alta un operario nuevo.
     */
    @PreAuthorize("hasAuthority('personal.editar')")
    @PostMapping
    public ResponseEntity<OperarioRespuesta> crear(
            @Valid @RequestBody OperarioSolicitud solicitud) {
        OperarioRespuesta creado = servicio.crear(solicitud);
        return ResponseEntity
                .created(URI.create("/api/operarios/" + creado.idOperario()))
                .body(creado);
    }

    /**
     * PUT /api/operarios/{id}: corrige el nombre o el teléfono de un operario.
     */
    @PreAuthorize("hasAuthority('personal.editar')")
    @PutMapping("/{id}")
    public OperarioRespuesta actualizar(@PathVariable Long id,
                                        @Valid @RequestBody OperarioSolicitud solicitud) {
        return servicio.actualizar(id, solicitud);
    }

    /**
     * PATCH /api/operarios/{id}/estado: activa o desactiva un operario. Al
     * desactivarlo se cierran sus asignaciones vigentes.
     */
    @PreAuthorize("hasAuthority('personal.editar')")
    @PatchMapping("/{id}/estado")
    public OperarioRespuesta cambiarEstado(@PathVariable Long id,
                                           @Valid @RequestBody CambioEstadoOperario cambio) {
        return servicio.cambiarEstado(id, cambio);
    }

    /**
     * POST /api/operarios/{id}/asignaciones: asigna el operario a una obra.
     */
    @PreAuthorize("hasAuthority('personal.editar')")
    @PostMapping("/{id}/asignaciones")
    public OperarioRespuesta asignar(@PathVariable Long id,
                                     @Valid @RequestBody Asignacion asignacion) {
        return servicio.asignar(id, asignacion);
    }

    /**
     * DELETE /api/operarios/{id}/asignaciones/{idObra}: saca al operario de esa
     * obra. La asignación queda cerrada en el historial, no se borra.
     */
    @PreAuthorize("hasAuthority('personal.editar')")
    @DeleteMapping("/{id}/asignaciones/{idObra}")
    public OperarioRespuesta desasignar(@PathVariable Long id, @PathVariable Long idObra) {
        return servicio.desasignar(id, idObra);
    }
}
