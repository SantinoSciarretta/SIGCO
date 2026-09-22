package com.sigco.usuarios.dto;

import com.sigco.usuarios.Usuario;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Entradas y salidas del modulo Usuarios.
 *
 * La regla que gobierna todo este archivo: NINGUN record de salida tiene un
 * campo para la contrasena ni para su hash. No es que se omita al completarlo:
 * el campo no existe, asi que no puede filtrarse por descuido. Es el mismo
 * criterio que se uso en la vidriera del Portfolio con los datos del cliente.
 */
public final class UsuarioDtos {

    private UsuarioDtos() {
    }

    // ------------------------------------------------------------------
    //  Entradas
    // ------------------------------------------------------------------

    /**
     * Alta de una cuenta.
     *
     * El largo minimo de la contrasena se valida en el servidor y no solo en el
     * formulario: una validacion que vive en el navegador se saltea mandando la
     * peticion directo a la API.
     */
    public record NuevoUsuario(
            @NotBlank(message = "El nombre de usuario es obligatorio")
            @Size(max = 50, message = "El nombre de usuario no puede superar los 50 caracteres")
            // Sin espacios ni acentos: es lo que se escribe para entrar, y un
            // espacio invisible al final vuelve imposible iniciar sesion sin que
            // se entienda por que.
            @Pattern(regexp = "^[a-zA-Z0-9._-]+$",
                     message = "El nombre de usuario solo admite letras, números, punto, guion y guion bajo")
            String nombreUsuario,

            @NotBlank(message = "La contraseña es obligatoria")
            @Size(min = 10, max = 72,
                  message = "La contraseña debe tener entre 10 y 72 caracteres")
            String contrasena,

            @NotNull(message = "Hay que elegir un rol")
            Long idRol,

            /** Opcional: vincula la cuenta con alguien de Personal. */
            Long idOperario) {
    }

    /** Cambio de contrasena. */
    public record CambioContrasena(
            /**
             * La contrasena actual.
             *
             * Se exige cuando uno cambia la propia, para que alguien que
             * encuentre la sesion abierta no pueda quedarse con la cuenta. El
             * dueño reseteando la de otro no la necesita: si la supiera, no
             * haria falta resetearla.
             */
            String contrasenaActual,

            @NotBlank(message = "La contraseña nueva es obligatoria")
            @Size(min = 10, max = 72,
                  message = "La contraseña debe tener entre 10 y 72 caracteres")
            String contrasenaNueva) {
    }

    public record CambioRol(
            @NotNull(message = "Hay que elegir un rol")
            Long idRol) {
    }

    public record VinculoOperario(
            /** null desvincula la cuenta del registro de Personal. */
            Long idOperario) {
    }

    public record BajaUsuario(
            @NotBlank(message = "Hay que indicar el motivo de la baja")
            @Size(max = 200, message = "El motivo no puede superar los 200 caracteres")
            String motivo) {
    }

    /** Credenciales del login. */
    public record Credenciales(
            @NotBlank(message = "Ingresá tu usuario")
            String nombreUsuario,

            @NotBlank(message = "Ingresá tu contraseña")
            String contrasena) {
    }

    // ------------------------------------------------------------------
    //  Salidas
    // ------------------------------------------------------------------

    public record UsuarioRespuesta(
            Long idUsuario,
            String nombreUsuario,
            Long idRol,
            String nombreRol,
            Long idOperario,
            String nombreOperario,
            String estado,
            String motivoBaja,
            LocalDateTime ultimaFechaAcceso,
            LocalDateTime fechaAlta) {

        public static UsuarioRespuesta desde(Usuario u) {
            return new UsuarioRespuesta(
                    u.getIdUsuario(),
                    u.getNombreUsuario(),
                    u.getRol().getIdRol(),
                    u.getRol().getNombreRol(),
                    u.getOperario() != null ? u.getOperario().getIdOperario() : null,
                    u.getOperario() != null ? u.getOperario().getNombreApellido() : null,
                    u.getEstado(),
                    u.getMotivoBaja(),
                    u.getUltimaFechaAcceso(),
                    u.getFechaAlta());
        }
    }

    /**
     * Lo que devuelve el login.
     *
     * Incluye la lista de permisos porque el frontend la necesita para decidir
     * que mostrar: un boton que el usuario no puede usar es peor que no
     * mostrarlo. Eso es PRESENTACION, no seguridad — el backend vuelve a
     * verificar el permiso en cada peticion, y esconder el boton no protege
     * nada por si solo.
     */
    public record Sesion(
            String token,
            long duracionEnSegundos,
            Long idUsuario,
            String nombreUsuario,
            String nombreRol,
            Long idOperario,
            List<String> permisos,

            /**
             * Si la cuenta tiene que cambiar la contrasena antes de operar.
             *
             * El frontend lo usa para mandar al usuario directo a la pantalla de
             * cambio. Eso es COMODIDAD: quien ignore este campo y llame a la API
             * igual se choca contra FiltroCambioDeContrasena, que es lo que de
             * verdad lo impide.
             */
            boolean debeCambiarContrasena) {
    }
}
