package com.sigco.seguridad;

import com.sigco.common.exception.RecursoNoEncontradoException;
import com.sigco.usuarios.UsuarioRepository;
import com.sigco.usuarios.dto.UsuarioDtos.Credenciales;
import com.sigco.usuarios.dto.UsuarioDtos.Sesion;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Ingreso al sistema.
 *
 * La ruta es /api/sesion y no /api/login porque el resto de la API nombra
 * recursos en español: una sesion es un recurso, crearla es entrar.
 *
 * NO hay endpoint para salir. Con tokens no hace falta: el servidor no guarda
 * nada que borrar, asi que cerrar sesion es que el navegador olvide el token.
 * Un endpoint de salida daria la impresion de que el servidor invalida algo, y
 * no es asi.
 */
@RestController
@RequestMapping("/api/sesion")
public class AutenticacionController {

    private final AutenticacionService servicio;
    private final SesionActual sesion;
    private final UsuarioRepository usuarioRepositorio;

    public AutenticacionController(AutenticacionService servicio,
                                   SesionActual sesion,
                                   UsuarioRepository usuarioRepositorio) {
        this.servicio = servicio;
        this.sesion = sesion;
        this.usuarioRepositorio = usuarioRepositorio;
    }

    /** POST /api/sesion — ingresar. Es la unica ruta abierta sin autenticacion. */
    @PostMapping
    public Sesion ingresar(@Valid @RequestBody Credenciales credenciales) {
        return servicio.ingresar(credenciales);
    }

    /**
     * GET /api/sesion — quien soy.
     *
     * La usa el frontend al recargarse: el token sobrevive en el navegador pero
     * el estado de la aplicacion no, asi que hay que volver a preguntar quien es
     * el usuario y que permisos tiene. Se pregunta al backend en lugar de
     * guardar los permisos en el navegador, porque lo que el navegador guarda
     * el usuario lo puede editar.
     */
    @GetMapping
    public Sesion actual(HttpServletRequest peticion) {
        Long id = sesion.idUsuario()
                .orElseThrow(() -> new RecursoNoEncontradoException("Sesión", 0L));

        return servicio.sesionDe(
                usuarioRepositorio.completo(id)
                        .orElseThrow(() -> new RecursoNoEncontradoException("Usuario", id)),
                peticion.getHeader("Authorization").substring("Bearer ".length()).trim());
    }
}
