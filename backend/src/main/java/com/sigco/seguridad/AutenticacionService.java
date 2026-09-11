package com.sigco.seguridad;

import com.sigco.accesos.Permiso;
import com.sigco.accesos.ServicioAuditoria;
import com.sigco.common.exception.ReglaDeNegocioException;
import com.sigco.usuarios.Usuario;
import com.sigco.usuarios.UsuarioRepository;
import com.sigco.usuarios.dto.UsuarioDtos.Credenciales;
import com.sigco.usuarios.dto.UsuarioDtos.Sesion;
import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ingreso al sistema.
 *
 * ------------------------------------------------------------------
 *  Por que el mensaje de error es siempre el mismo
 * ------------------------------------------------------------------
 *
 * Usuario inexistente y contrasena incorrecta devuelven exactamente el mismo
 * texto. Es deliberado: si el sistema contestara "ese usuario no existe",
 * cualquiera podria probar nombres hasta descubrir cuales son cuentas reales, y
 * despues concentrarse en adivinar la contrasena de una que sabe que existe.
 *
 * Una cuenta dada de baja SI recibe un mensaje distinto: quien la usaba tiene
 * que entender que no es un problema de tipeo, y ya sabia que la cuenta existia.
 */
@Service
public class AutenticacionService {

    private final UsuarioRepository repositorio;
    private final PasswordEncoder codificador;
    private final ServicioJwt servicioJwt;
    private final ServicioAuditoria auditoria;

    public AutenticacionService(UsuarioRepository repositorio,
                                PasswordEncoder codificador,
                                ServicioJwt servicioJwt,
                                ServicioAuditoria auditoria) {
        this.repositorio = repositorio;
        this.codificador = codificador;
        this.servicioJwt = servicioJwt;
        this.auditoria = auditoria;
    }

    @Transactional
    public Sesion ingresar(Credenciales credenciales) {
        Optional<Usuario> encontrado = repositorio.porNombre(credenciales.nombreUsuario().trim());

        // Se verifica la contrasena aunque el usuario no exista, para que el
        // tiempo de respuesta sea parecido en los dos casos: si el sistema
        // contestara instantaneamente ante un usuario inexistente y tardara al
        // comparar el hash, la diferencia de tiempo revelaria cuales existen.
        boolean valida = encontrado
                .map(u -> u.verificarContra(credenciales.contrasena(), codificador::matches))
                .orElseGet(() -> {
                    codificador.matches(credenciales.contrasena(), HASH_DE_DESCARTE);
                    return false;
                });

        if (encontrado.isEmpty() || !valida) {
            throw new ReglaDeNegocioException("Usuario o contraseña incorrectos.");
        }

        Usuario usuario = encontrado.get();

        if (!usuario.estaActivo()) {
            throw new ReglaDeNegocioException(
                    "Esta cuenta está dada de baja. Hablá con el dueño para reactivarla.");
        }

        usuario.registrarIngreso();

        // El ingreso se audita con el usuario explicito: en este momento la
        // peticion todavia no esta autenticada —justamente se esta
        // autenticando—, asi que no se puede tomar de la sesion.
        auditoria.registrar(usuario, "Ingreso al sistema", "Accesos");

        return armarSesion(usuario);
    }

    /**
     * Los datos de la sesion en curso, para cuando el frontend se recarga.
     *
     * El token sigue siendo el que ya tiene el navegador: aca no se emite uno
     * nuevo. Si cada recarga emitiera un token, la sesion no venceria nunca
     * mientras alguien dejara la pestaña abierta.
     */
    public Sesion sesionDe(Usuario usuario, String tokenVigente) {
        return new Sesion(
                tokenVigente,
                servicioJwt.getDuracionEnSegundos(),
                usuario.getIdUsuario(),
                usuario.getNombreUsuario(),
                usuario.getRol().getNombreRol(),
                usuario.getOperario() != null ? usuario.getOperario().getIdOperario() : null,
                usuario.getRol().getPermisos().stream()
                        .map(Permiso::getNombrePermiso)
                        .sorted()
                        .toList());
    }

    private Sesion armarSesion(Usuario usuario) {
        String token = servicioJwt.emitir(
                usuario.getIdUsuario(),
                usuario.getNombreUsuario(),
                usuario.getRol().getNombreRol());
        return sesionDe(usuario, token);
    }

    /**
     * Hash valido pero de una contrasena que nadie usa.
     *
     * Sirve solo para que la comparacion tarde lo mismo cuando el usuario no
     * existe. No corresponde a ninguna cuenta.
     */
    private static final String HASH_DE_DESCARTE =
            "$2a$10$lotlfbgwDv9go7H4KEh1A.qrSnn6MO9AKUUjTM6jR47FPlMsJM2RC";
}
