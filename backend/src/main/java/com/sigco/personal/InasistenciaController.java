package com.sigco.personal;

import com.sigco.personal.dto.PersonalDtos.InasistenciaRespuesta;
import com.sigco.personal.dto.PersonalDtos.InasistenciaSolicitud;
import com.sigco.personal.dto.PersonalDtos.MotivoSolicitud;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
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
 * Inasistencias.
 *
 * Van en un controlador propio y no dentro de /api/operarios porque se
 * consultan de las dos puntas: por operario (para ver reiteraciones) y por obra
 * (para cruzar contra el avance en Seguimiento). Colgarlas de una sola de las
 * dos jerarquias obligaria a inventar rutas raras para la otra.
 */
@RestController
@RequestMapping("/api/inasistencias")
public class InasistenciaController {

    private final PersonalService servicio;

    public InasistenciaController(PersonalService servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    public List<InasistenciaRespuesta> listar(
            @RequestParam(required = false) Long operario,
            @RequestParam(required = false) Long obra,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return servicio.listarInasistencias(operario, obra, desde, hasta);
    }

    @PostMapping
    public ResponseEntity<InasistenciaRespuesta> registrar(
            @Valid @RequestBody InasistenciaSolicitud solicitud) {
        InasistenciaRespuesta creada = servicio.registrarInasistencia(solicitud);
        return ResponseEntity
                .created(URI.create("/api/inasistencias/" + creada.idInasistencia()))
                .body(creada);
    }

    /** El motivo se completa despues, que es el caso normal. */
    @PatchMapping("/{id}/motivo")
    public InasistenciaRespuesta registrarMotivo(@PathVariable Long id,
                                                 @Valid @RequestBody MotivoSolicitud solicitud) {
        return servicio.registrarMotivo(id, solicitud);
    }
}
