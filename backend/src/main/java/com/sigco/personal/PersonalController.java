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
public class PersonalController {

    private final PersonalService servicio;

    public PersonalController(PersonalService servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    public List<OperarioRespuesta> listar(
            @RequestParam(required = false) String busqueda,
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) Long obra) {
        return servicio.listar(busqueda, estado, obra);
    }

    @GetMapping("/{id}")
    public OperarioRespuesta obtener(@PathVariable Long id) {
        return servicio.obtener(id);
    }

    @PostMapping
    public ResponseEntity<OperarioRespuesta> crear(
            @Valid @RequestBody OperarioSolicitud solicitud) {
        OperarioRespuesta creado = servicio.crear(solicitud);
        return ResponseEntity
                .created(URI.create("/api/operarios/" + creado.idOperario()))
                .body(creado);
    }

    @PutMapping("/{id}")
    public OperarioRespuesta actualizar(@PathVariable Long id,
                                        @Valid @RequestBody OperarioSolicitud solicitud) {
        return servicio.actualizar(id, solicitud);
    }

    @PatchMapping("/{id}/estado")
    public OperarioRespuesta cambiarEstado(@PathVariable Long id,
                                           @Valid @RequestBody CambioEstadoOperario cambio) {
        return servicio.cambiarEstado(id, cambio);
    }

    @PostMapping("/{id}/asignaciones")
    public OperarioRespuesta asignar(@PathVariable Long id,
                                     @Valid @RequestBody Asignacion asignacion) {
        return servicio.asignar(id, asignacion);
    }

    @DeleteMapping("/{id}/asignaciones/{idObra}")
    public OperarioRespuesta desasignar(@PathVariable Long id, @PathVariable Long idObra) {
        return servicio.desasignar(id, idObra);
    }
}
