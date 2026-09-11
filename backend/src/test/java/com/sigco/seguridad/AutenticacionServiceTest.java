package com.sigco.seguridad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sigco.accesos.Rol;
import com.sigco.accesos.ServicioAuditoria;
import com.sigco.common.exception.ReglaDeNegocioException;
import com.sigco.usuarios.Usuario;
import com.sigco.usuarios.UsuarioRepository;
import com.sigco.usuarios.dto.UsuarioDtos.Credenciales;
import com.sigco.usuarios.dto.UsuarioDtos.Sesion;
import java.lang.reflect.Field;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Tests del ingreso al sistema.
 *
 * Lo que se verifica es lo que hace que el login sea seguro y no solo
 * funcional: que la contrasena se compare contra el hash de verdad, que el
 * mensaje de error no revele si el usuario existe, y que una cuenta dada de
 * baja no entre aunque la contrasena sea correcta.
 */
@ExtendWith(MockitoExtension.class)
class AutenticacionServiceTest {

    @Mock private UsuarioRepository repositorio;
    @Mock private ServicioAuditoria auditoria;

    // BCrypt y JWT de verdad: son el objeto de la prueba, no dependencias a
    // simular. Con un codificador simulado, "la contrasena se verifica bien"
    // no probaria nada.
    private final PasswordEncoder codificador = new BCryptPasswordEncoder();
    private final ServicioJwt servicioJwt = new ServicioJwt(
            "clave-de-prueba-suficientemente-larga-para-hmac-sha256", 28800);

    private AutenticacionService servicio() {
        return new AutenticacionService(repositorio, codificador, servicioJwt, auditoria);
    }

    private static void asignar(Object objeto, String campo, Object valor) {
        try {
            Field f = objeto.getClass().getDeclaredField(campo);
            f.setAccessible(true);
            f.set(objeto, valor);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Rol rolDueno() {
        try {
            var c = Rol.class.getDeclaredConstructor();
            c.setAccessible(true);
            Rol r = c.newInstance();
            asignar(r, "idRol", 1L);
            asignar(r, "nombreRol", Rol.DUENO);
            return r;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private Usuario usuario(String contrasena, boolean activo) {
        Usuario u = new Usuario("ricardo", codificador.encode(contrasena), rolDueno(), null);
        asignar(u, "idUsuario", 1L);
        if (!activo) {
            u.desactivar("Dejó la empresa");
        }
        lenient().when(repositorio.porNombre("ricardo")).thenReturn(Optional.of(u));
        return u;
    }

    // ------------------------------------------------------------------

    @Test
    @DisplayName("Con las credenciales correctas devuelve token, rol y permisos")
    void ingresoCorrecto() {
        Usuario u = usuario("granica2026", true);

        Sesion sesion = servicio().ingresar(new Credenciales("ricardo", "granica2026"));

        assertThat(sesion.token()).isNotBlank();
        assertThat(sesion.nombreUsuario()).isEqualTo("ricardo");
        assertThat(sesion.nombreRol()).isEqualTo(Rol.DUENO);
        assertThat(sesion.duracionEnSegundos()).isEqualTo(28800);

        // El token tiene que poder verificarse y apuntar a este usuario.
        assertThat(servicioJwt.idDelUsuario(sesion.token())).isEqualTo(1L);

        // Queda registrado el ingreso y la fecha de último acceso.
        assertThat(u.getUltimaFechaAcceso()).isNotNull();
        verify(auditoria).registrar(u, "Ingreso al sistema", "Accesos");
    }

    @Test
    @DisplayName("Con la contraseña incorrecta no entra")
    void contrasenaIncorrecta() {
        usuario("granica2026", true);

        assertThatThrownBy(() -> servicio().ingresar(
                new Credenciales("ricardo", "otracosa")))
                .isInstanceOf(ReglaDeNegocioException.class);
    }

    /**
     * Si el mensaje distinguiera un caso del otro, cualquiera podria probar
     * nombres hasta descubrir cuales son cuentas reales, y despues concentrarse
     * en adivinar la contrasena de una que sabe que existe.
     */
    @Test
    @DisplayName("Usuario inexistente y contraseña incorrecta dan el MISMO mensaje")
    void elMensajeNoRevelaSiElUsuarioExiste() {
        usuario("granica2026", true);
        when(repositorio.porNombre("nadie")).thenReturn(Optional.empty());

        String conUsuarioReal = mensajeDe(() -> servicio()
                .ingresar(new Credenciales("ricardo", "incorrecta")));
        String conUsuarioInventado = mensajeDe(() -> servicio()
                .ingresar(new Credenciales("nadie", "incorrecta")));

        assertThat(conUsuarioReal).isEqualTo(conUsuarioInventado);
    }

    @Test
    @DisplayName("Una cuenta dada de baja no entra, aunque la contraseña sea correcta")
    void cuentaDadaDeBajaNoEntra() {
        usuario("granica2026", false);

        assertThatThrownBy(() -> servicio().ingresar(
                new Credenciales("ricardo", "granica2026")))
                .isInstanceOf(ReglaDeNegocioException.class)
                .hasMessageContaining("dada de baja");

        verify(auditoria, org.mockito.Mockito.never())
                .registrar(anyString(), anyString());
    }

    // ------------------------------------------------------------------
    //  El token
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Un token firmado con otra clave se rechaza")
    void tokenConFirmaAjena() {
        ServicioJwt otroServidor = new ServicioJwt(
                "otra-clave-distinta-igual-de-larga-para-hmac-sha", 28800);
        String tokenAjeno = otroServidor.emitir(1L, "ricardo", Rol.DUENO);

        // Devuelve null en lugar de lanzar: un token invalido es una situacion
        // esperable, no un error del sistema. El filtro lo trata como
        // "no autenticado" y sigue.
        assertThat(servicioJwt.idDelUsuario(tokenAjeno)).isNull();
    }

    @Test
    @DisplayName("Un token vencido se rechaza")
    void tokenVencido() {
        ServicioJwt yaVencido = new ServicioJwt(
                "clave-de-prueba-suficientemente-larga-para-hmac-sha256", -1);
        String token = yaVencido.emitir(1L, "ricardo", Rol.DUENO);

        assertThat(servicioJwt.idDelUsuario(token)).isNull();
    }

    @Test
    @DisplayName("Cualquier texto que no sea un token se rechaza sin fallar")
    void textoCualquieraNoRompe() {
        assertThat(servicioJwt.idDelUsuario("esto-no-es-un-token")).isNull();
        assertThat(servicioJwt.idDelUsuario("")).isNull();
    }

    // ------------------------------------------------------------------

    private String mensajeDe(Runnable accion) {
        try {
            accion.run();
            return "(no falló)";
        } catch (ReglaDeNegocioException e) {
            return e.getMessage();
        }
    }
}
