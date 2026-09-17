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

    // Simulado, a diferencia de BCrypt y JWT. Lo que hace RegistroDeIntentos
    // adentro —abrir su propia transaccion— no se puede ejercitar con mocks:
    // eso se prueba contra el backend real. Aca interesa la DECISION del
    // servicio segun lo que ese componente conteste.
    @Mock private RegistroDeIntentos intentos;

    // BCrypt y JWT de verdad: son el objeto de la prueba, no dependencias a
    // simular. Con un codificador simulado, "la contrasena se verifica bien"
    // no probaria nada.
    private final PasswordEncoder codificador = new BCryptPasswordEncoder();
    private final ServicioJwt servicioJwt = new ServicioJwt(
            "clave-de-prueba-suficientemente-larga-para-hmac-sha256", 28800);

    private AutenticacionService servicio() {
        return new AutenticacionService(
                repositorio, codificador, servicioJwt, auditoria, intentos);
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
    //  Limite de intentos fallidos
    // ------------------------------------------------------------------

    @Test
    @DisplayName("El intento que pasa el límite avisa que la cuenta quedó bloqueada")
    void avisaCuandoBloquea() {
        usuario("granica2026", true);
        when(intentos.registrarFallo(1L)).thenReturn(true);
        when(intentos.getMinutosDeBloqueo()).thenReturn(15);

        assertThatThrownBy(() -> servicio().ingresar(
                new Credenciales("ricardo", "incorrecta")))
                .isInstanceOf(ReglaDeNegocioException.class)
                .hasMessageContaining("15 minutos");
    }

    @Test
    @DisplayName("Cada intento fallido contra una cuenta que existe queda anotado")
    void anotaElIntentoFallido() {
        usuario("granica2026", true);

        assertThatThrownBy(() -> servicio().ingresar(
                new Credenciales("ricardo", "incorrecta")))
                .isInstanceOf(ReglaDeNegocioException.class);

        verify(intentos).registrarFallo(1L);
    }

    @Test
    @DisplayName("Contra un usuario inexistente no se anota nada: no hay dónde")
    void noAnotaContraUsuarioInexistente() {
        when(repositorio.porNombre("nadie")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio().ingresar(
                new Credenciales("nadie", "loquesea")))
                .isInstanceOf(ReglaDeNegocioException.class);

        verify(intentos, org.mockito.Mockito.never())
                .registrarFallo(org.mockito.ArgumentMatchers.anyLong());
    }

    /**
     * El test que justifica el orden del codigo.
     *
     * Si el bloqueo se verificara DESPUES de comparar la contrasena, quien esta
     * probando contrasenas podria seguir probandolas durante el bloqueo: el
     * sistema le diria "incorrecta" hasta que acertara y recien ahi le avisaria
     * del bloqueo, confirmandole cual era. Verificando antes, durante el bloqueo
     * ningun intento se evalua.
     *
     * Por eso el caso de prueba usa la contrasena CORRECTA: si el mensaje
     * hablara de credenciales en lugar del bloqueo, el orden estaria mal.
     */
    @Test
    @DisplayName("Una cuenta bloqueada no entra ni con la contraseña correcta")
    void cuentaBloqueadaNoEntraNiConLaCorrecta() {
        Usuario u = usuario("granica2026", true);
        u.registrarIntentoFallido(1, 15);   // con maximo 1, este intento la bloquea

        assertThatThrownBy(() -> servicio().ingresar(
                new Credenciales("ricardo", "granica2026")))
                .isInstanceOf(ReglaDeNegocioException.class)
                .hasMessageContaining("Demasiados intentos");

        // Y no llega a evaluarse como intento: la peticion se corta antes.
        verify(intentos, org.mockito.Mockito.never())
                .registrarFallo(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    @DisplayName("Un ingreso correcto borra la cuenta de intentos fallidos")
    void ingresoCorrectoLimpiaLosIntentos() {
        Usuario u = usuario("granica2026", true);
        u.registrarIntentoFallido(5, 15);   // un fallo suelto, sin llegar al limite
        assertThat(u.getIntentosFallidos()).isEqualTo(1);

        servicio().ingresar(new Credenciales("ricardo", "granica2026"));

        assertThat(u.getIntentosFallidos()).isZero();
        assertThat(u.estaBloqueada()).isFalse();
    }

    @Test
    @DisplayName("Al bloquear, el contador vuelve a cero para no rebloquear al primer error")
    void alBloquearElContadorVuelveACero() {
        Usuario u = usuario("granica2026", true);

        assertThat(u.registrarIntentoFallido(2, 15)).isFalse();
        assertThat(u.registrarIntentoFallido(2, 15)).isTrue();

        assertThat(u.estaBloqueada()).isTrue();
        // Si quedara en 2, al vencer el bloqueo el primer error siguiente
        // volveria a bloquear la cuenta de inmediato.
        assertThat(u.getIntentosFallidos()).isZero();
    }

    // ------------------------------------------------------------------
    //  Cambio obligatorio de contraseña
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Una cuenta recién creada nace obligada a cambiar la contraseña")
    void cuentaNuevaNaceObligada() {
        Usuario nuevo = new Usuario("martin", codificador.encode("general2026"), rolDueno(), null);

        assertThat(nuevo.debeCambiarContrasena()).isTrue();
    }

    @Test
    @DisplayName("La sesión informa si hay que cambiar la contraseña")
    void laSesionInformaLaObligacion() {
        usuario("granica2026", true);

        Sesion sesion = servicio().ingresar(new Credenciales("ricardo", "granica2026"));

        assertThat(sesion.debeCambiarContrasena()).isTrue();
    }

    @Test
    @DisplayName("Elegir una contraseña propia levanta la obligación; que se la resetee otro, la impone")
    void cambioPropioYReseteoAjeno() {
        Usuario u = usuario("granica2026", true);

        u.cambiarContrasena(codificador.encode("laQueEligioRicardo"));
        assertThat(u.debeCambiarContrasena()).isFalse();

        u.resetearContrasena(codificador.encode("laQuePusoElDueno"));
        assertThat(u.debeCambiarContrasena()).isTrue();
    }

    @Test
    @DisplayName("Cambiar la contraseña libera el bloqueo")
    void cambiarContrasenaLiberaElBloqueo() {
        Usuario u = usuario("granica2026", true);
        u.registrarIntentoFallido(1, 15);
        assertThat(u.estaBloqueada()).isTrue();

        // La contrasena que estaban buscando ya no existe: no hay motivo para
        // que el titular siga esperando.
        u.cambiarContrasena(codificador.encode("unaNueva"));

        assertThat(u.estaBloqueada()).isFalse();
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
    @DisplayName("Un token recién emitido no se renueva")
    void tokenNuevoNoSeRenueva() {
        String token = servicioJwt.emitir(1L, "ricardo", Rol.DUENO);

        // Le queda el 100% de su vida: muy por encima del 25% del umbral.
        assertThat(servicioJwt.convieneRenovar(token, 0.25)).isFalse();
    }

    @Test
    @DisplayName("Un token al que le queda poco sí se renueva")
    void tokenPorVencerSeRenueva() {
        // Duracion de 8 horas, pero emitido por un servicio con vida corta: lo
        // que importa es la proporcion entre lo que queda y la duracion
        // configurada.
        ServicioJwt casiVencido = new ServicioJwt(
                "clave-de-prueba-suficientemente-larga-para-hmac-sha256", 60);
        String token = casiVencido.emitir(1L, "ricardo", Rol.DUENO);

        // Le queda un minuto de sesenta segundos configurados. Con un umbral
        // del 200% (mas de lo que dura), tiene que querer renovarse.
        assertThat(casiVencido.convieneRenovar(token, 2.0)).isTrue();
    }

    @Test
    @DisplayName("Un token inválido no se renueva y no rompe")
    void tokenInvalidoNoSeRenueva() {
        assertThat(servicioJwt.convieneRenovar("no-es-un-token", 0.25)).isFalse();
        assertThat(servicioJwt.convieneRenovar("", 0.25)).isFalse();
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
