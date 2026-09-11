package com.sigco.accesos;

import com.sigco.accesos.dto.AccesosDtos.AuditoriaRespuesta;
import com.sigco.accesos.dto.AccesosDtos.PermisoRespuesta;
import com.sigco.accesos.dto.AccesosDtos.PermisosDelRol;
import com.sigco.accesos.dto.AccesosDtos.RolRespuesta;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * API REST del modulo Accesos.
 *
 * No hay POST ni DELETE de roles ni de permisos: los tres roles y los permisos
 * vienen definidos con el sistema (migracion V13). Un permiso nuevo no es un
 * dato que se carga, es codigo nuevo que hay que proteger; y un rol nuevo
 * implica decidir que puede hacer, que es una decision de diseño.
 *
 * Lo unico que se modifica es QUE permisos tiene cada rol, con un PUT que
 * reemplaza la lista completa.
 */
@RestController
public class AccesosController {

    private final AccesosService servicio;

    public AccesosController(AccesosService servicio) {
        this.servicio = servicio;
    }

    @GetMapping("/api/permisos")
    @PreAuthorize("hasAuthority('accesos.ver')")
    public List<PermisoRespuesta> permisos() {
        return servicio.permisos();
    }

    @GetMapping("/api/roles")
    @PreAuthorize("hasAuthority('accesos.ver')")
    public List<RolRespuesta> roles() {
        return servicio.roles();
    }

    /** Reemplaza la lista completa de permisos del rol. */
    @PutMapping("/api/roles/{id}/permisos")
    @PreAuthorize("hasAuthority('accesos.editar')")
    public RolRespuesta definirPermisos(@PathVariable Long id,
                                        @Valid @RequestBody PermisosDelRol solicitud) {
        return servicio.definirPermisos(id, solicitud);
    }

    /**
     * GET /api/auditoria — ultimas acciones sensibles.
     *
     * Solo lectura, y no hay forma de borrar un registro desde ninguna parte del
     * sistema: una auditoria que se puede editar no sirve para lo que existe.
     */
    @GetMapping("/api/auditoria")
    @PreAuthorize("hasAuthority('accesos.ver')")
    public List<AuditoriaRespuesta> auditoria(
            @RequestParam(required = false) Long usuario,
            @RequestParam(required = false) String modulo) {
        return servicio.auditoria(usuario, modulo);
    }
}
