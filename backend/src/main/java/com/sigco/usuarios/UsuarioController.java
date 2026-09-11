package com.sigco.usuarios;

import com.sigco.common.exception.ReglaDeNegocioException;
import com.sigco.seguridad.SesionActual;
import com.sigco.usuarios.dto.UsuarioDtos.BajaUsuario;
import com.sigco.usuarios.dto.UsuarioDtos.CambioContrasena;
import com.sigco.usuarios.dto.UsuarioDtos.CambioRol;
import com.sigco.usuarios.dto.UsuarioDtos.NuevoUsuario;
import com.sigco.usuarios.dto.UsuarioDtos.UsuarioRespuesta;
import com.sigco.usuarios.dto.UsuarioDtos.VinculoOperario;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * API REST del modulo Usuarios.
 *
 * Cada metodo declara el permiso que exige con @PreAuthorize. La anotacion va
 * aca, junto al metodo que protege, y no en una lista central de rutas: una
 * lista aparte queda lejos del codigo y se desactualiza sin que nadie lo note.
 *
 * El texto del permiso es literalmente el mismo que figura en la tabla permiso
 * de la base (ver migracion V13), asi que la matriz del informe se puede
 * verificar buscando esa cadena en el codigo.
 *
 * NO hay DELETE. Una cuenta se da de baja, no se elimina: la auditoria guarda
 * quien hizo cada cosa, y borrar el usuario dejaria esos registros apuntando a
 * nadie.
 */
@RestController
@RequestMapping("/api/usuarios")
public class UsuarioController {

    private final UsuarioService servicio;
    private final SesionActual sesion;

    public UsuarioController(UsuarioService servicio, SesionActual sesion) {
        this.servicio = servicio;
        this.sesion = sesion;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('usuarios.ver')")
    public List<UsuarioRespuesta> listar() {
        return servicio.listar();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('usuarios.ver')")
    public UsuarioRespuesta obtener(@PathVariable Long id) {
        return servicio.obtener(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('usuarios.editar')")
    public ResponseEntity<UsuarioRespuesta> crear(@Valid @RequestBody NuevoUsuario solicitud) {
        UsuarioRespuesta creado = servicio.crear(solicitud);
        return ResponseEntity
                .created(URI.create("/api/usuarios/" + creado.idUsuario()))
                .body(creado);
    }

    /**
     * Cambia la contrasena de una cuenta.
     *
     * Es el unico endpoint del modulo que NO exige 'usuarios.editar', y el
     * motivo es que cualquiera tiene que poder cambiar la propia: un capataz no
     * administra usuarios pero tiene que poder cambiar su contrasena.
     *
     * La condicion esta escrita en la anotacion para que la regla se lea junto
     * al metodo: o sos vos mismo, o tenes permiso de administrar cuentas.
     */
    @PatchMapping("/{id}/contrasena")
    @PreAuthorize("hasAuthority('usuarios.editar') or #id == principal.idUsuario")
    public ResponseEntity<Void> cambiarContrasena(@PathVariable Long id,
                                                  @Valid @RequestBody CambioContrasena cambio) {
        boolean esLaPropia = sesion.idUsuario().map(id::equals).orElse(false);
        servicio.cambiarContrasena(id, cambio, esLaPropia);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/rol")
    @PreAuthorize("hasAuthority('usuarios.editar')")
    public UsuarioRespuesta cambiarRol(@PathVariable Long id,
                                       @Valid @RequestBody CambioRol cambio) {
        return servicio.cambiarRol(id, cambio);
    }

    @PatchMapping("/{id}/operario")
    @PreAuthorize("hasAuthority('usuarios.editar')")
    public UsuarioRespuesta vincularOperario(@PathVariable Long id,
                                             @RequestBody VinculoOperario vinculo) {
        return servicio.vincularOperario(id, vinculo);
    }

    /** Da de baja la cuenta. Conserva el usuario para no romper la auditoria. */
    @PatchMapping("/{id}/baja")
    @PreAuthorize("hasAuthority('usuarios.editar')")
    public UsuarioRespuesta desactivar(@PathVariable Long id,
                                       @Valid @RequestBody BajaUsuario baja) {
        Long quienPide = sesion.idUsuario().orElseThrow(
                () -> new ReglaDeNegocioException("No hay sesión activa."));
        return servicio.desactivar(id, baja, quienPide);
    }

    @PatchMapping("/{id}/reactivacion")
    @PreAuthorize("hasAuthority('usuarios.editar')")
    public UsuarioRespuesta reactivar(@PathVariable Long id) {
        return servicio.reactivar(id);
    }
}
