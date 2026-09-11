package com.sigco.clientes;

import com.sigco.clientes.dto.CambioEstado;
import com.sigco.clientes.dto.ClienteRespuesta;
import com.sigco.clientes.dto.ClienteSolicitud;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
 * API REST del modulo Clientes.
 *
 * El controlador es una capa fina: traduce HTTP a llamadas al servicio y nada
 * mas. No tiene logica de negocio ni bloques try/catch, porque los errores los
 * resuelve el manejador global (ManejadorGlobalDeErrores).
 *
 * La anotacion @Valid es la que dispara las validaciones declaradas en el DTO.
 * Sin ella las anotaciones no se ejecutan y los datos entrarian sin control:
 * es un olvido facil y silencioso.
 */
@RestController
@RequestMapping("/api/clientes")
/*
 * Modulo 14 (Accesos): la clase exige el permiso de lectura del modulo y cada
 * metodo que escribe lo sobreescribe con el de edicion. El texto del permiso es
 * el mismo que figura en la tabla permiso de la base (migracion V13), asi que
 * la matriz del informe se puede verificar buscando esa cadena en el codigo.
 */
@PreAuthorize("hasAuthority('clientes.ver')")
public class ClienteController {

    private final ClienteService servicio;

    public ClienteController(ClienteService servicio) {
        this.servicio = servicio;
    }

    /**
     * GET /api/clientes
     * Parametros opcionales: ?busqueda=juan&origen=Arquitecto&estado=Activo
     */
    @GetMapping
    public List<ClienteRespuesta> listar(
            @RequestParam(required = false) String busqueda,
            @RequestParam(required = false) String origen,
            @RequestParam(required = false) String estado) {

        return servicio.listar(busqueda, origen, estado);
    }

    /** GET /api/clientes/{id} — 404 si no existe. */
    @GetMapping("/{id}")
    public ClienteRespuesta obtener(@PathVariable Long id) {
        return servicio.obtener(id);
    }

    /**
     * POST /api/clientes
     *
     * Devuelve 201 Created con la cabecera Location apuntando al recurso recien
     * creado, que es la respuesta que corresponde a un alta en REST.
     */
    @PreAuthorize("hasAuthority('clientes.editar')")
    @PostMapping
    public ResponseEntity<ClienteRespuesta> crear(@Valid @RequestBody ClienteSolicitud solicitud) {
        ClienteRespuesta creado = servicio.crear(solicitud);
        return ResponseEntity
                .created(URI.create("/api/clientes/" + creado.idCliente()))
                .body(creado);
    }

    /** PUT /api/clientes/{id} — edicion de los datos de contacto. */
    @PreAuthorize("hasAuthority('clientes.editar')")
    @PutMapping("/{id}")
    public ClienteRespuesta actualizar(@PathVariable Long id,
                                       @Valid @RequestBody ClienteSolicitud solicitud) {
        return servicio.actualizar(id, solicitud);
    }

    /**
     * PATCH /api/clientes/{id}/estado — activar o marcar como inactivo.
     *
     * Es PATCH y no PUT porque modifica un solo campo del cliente, no el
     * recurso completo.
     *
     * No existe DELETE en este modulo, y es deliberado: el informe establece
     * que un cliente no se elimina, se desactiva, para no perder la
     * trazabilidad de sus obras anteriores.
     */
    @PreAuthorize("hasAuthority('clientes.editar')")
    @PatchMapping("/{id}/estado")
    public ClienteRespuesta cambiarEstado(@PathVariable Long id,
                                          @Valid @RequestBody CambioEstado cambio) {
        return servicio.cambiarEstado(id, cambio.estado());
    }
}
