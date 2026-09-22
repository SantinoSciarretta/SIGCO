package com.sigco.usuarios;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sigco.accesos.Rol;
import com.sigco.accesos.RolRepository;
import com.sigco.accesos.ServicioAuditoria;
import com.sigco.common.exception.ReglaDeNegocioException;
import com.sigco.personal.OperarioRepository;
import com.sigco.usuarios.dto.UsuarioDtos.BajaUsuario;
import com.sigco.usuarios.dto.UsuarioDtos.CambioContrasena;
import com.sigco.usuarios.dto.UsuarioDtos.CambioRol;
import com.sigco.usuarios.dto.UsuarioDtos.NuevoUsuario;
import java.lang.reflect.Field;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Tests del modulo Usuarios.
 *
 * El foco esta en las tres cosas que pueden dejar el sistema inutilizable o
 * inseguro:
 *
 *   - que la contrasena NUNCA se guarde en texto plano
 *   - que no se pueda dejar el sistema sin ningun dueño activo
 *   - que cambiar la propia contrasena exija la actual
 */
@ExtendWith(MockitoExtension.class)
class UsuarioServiceTest {

    @Mock private UsuarioRepository repositorio;
    @Mock private RolRepository rolRepositorio;
    @Mock private OperarioRepository operarioRepositorio;
    @Mock private ServicioAuditoria auditoria;

    // BCrypt de verdad y no un mock: la prueba central del modulo es que lo
    // que se guarda NO es la contrasena, y con un codificador simulado esa
    // verificacion no probaria nada.
    private final PasswordEncoder codificador = new BCryptPasswordEncoder();

    private UsuarioService servicio;

    private static void asignarId(Object entidad, String campo, Long valor) {
        try {
            Field f = entidad.getClass().getDeclaredField(campo);
            f.setAccessible(true);
            f.set(entidad, valor);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Arma un Rol para el test.
     *
     * Por reflexion porque Rol no tiene constructor publico: los tres roles del
     * sistema los crea la migracion V13 y no hay forma de inventar uno desde el
     * codigo. Eso esta bien para produccion, pero un test necesita uno.
     */
    private static Rol rol(String nombre, Long id) {
        try {
            var constructor = Rol.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            Rol r = constructor.newInstance();
            asignarId(r, "idRol", id);
            Field f = Rol.class.getDeclaredField("nombreRol");
            f.setAccessible(true);
            f.set(r, nombre);
            return r;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private UsuarioService servicio() {
        if (servicio == null) {
            // La politica de contrasenas va de verdad, no simulada: es una
            // regla de negocio del modulo y con un doble no se probaria nada.
            servicio = new UsuarioService(repositorio, rolRepositorio, operarioRepositorio,
                    codificador, auditoria, new PoliticaDeContrasenas());
        }
        return servicio;
    }

    private Usuario usuarioCon(Rol rol, String contrasena, Long id) {
        Usuario u = new Usuario("ricardo", codificador.encode(contrasena), rol, null);
        asignarId(u, "idUsuario", id);
        lenient().when(repositorio.completo(id)).thenReturn(Optional.of(u));
        return u;
    }

    // ------------------------------------------------------------------
    //  Contraseñas
    // ------------------------------------------------------------------

    @Test
    @DisplayName("La contraseña se guarda cifrada, nunca en texto plano")
    void laContrasenaSeGuardaCifrada() {
        Rol dueno = rol(Rol.DUENO, 1L);
        when(rolRepositorio.findById(1L)).thenReturn(Optional.of(dueno));
        when(repositorio.existsByNombreUsuarioIgnoreCase("ricardo")).thenReturn(false);
        when(repositorio.save(any(Usuario.class))).thenAnswer(i -> {
            Usuario u = i.getArgument(0);
            asignarId(u, "idUsuario", 1L);
            return u;
        });

        servicio().crear(new NuevoUsuario("ricardo", "melonVerde47", 1L, null));

        // La entidad no expone el hash a proposito, asi que se verifica por el
        // unico camino que hay: preguntarle si una contrasena coincide.
        verify(repositorio).save(org.mockito.ArgumentMatchers.argThat(u -> {
            boolean coincideLaCorrecta = u.verificarContra("melonVerde47", codificador::matches);
            boolean rechazaOtra = !u.verificarContra("melonVerde47 ", codificador::matches);
            return coincideLaCorrecta && rechazaOtra;
        }));
    }

    @Test
    @DisplayName("Cambiar la propia contraseña exige escribir la actual")
    void cambiarLaPropiaExigeLaActual() {
        Usuario usuario = usuarioCon(rol(Rol.DUENO, 1L), "actual123", 1L);

        assertThatThrownBy(() -> servicio().cambiarContrasena(
                1L, new CambioContrasena("equivocada", "nueva12345"), true))
                .isInstanceOf(ReglaDeNegocioException.class)
                .hasMessageContaining("actual no es correcta");

        // Y la contrasena sigue siendo la de antes.
        assertThat(usuario.verificarContra("actual123", codificador::matches)).isTrue();
    }

    @Test
    @DisplayName("Con la contraseña actual correcta, el cambio se aplica")
    void cambioCorrecto() {
        Usuario usuario = usuarioCon(rol(Rol.DUENO, 1L), "actual123", 1L);

        servicio().cambiarContrasena(1L, new CambioContrasena("actual123", "nueva12345"), true);

        assertThat(usuario.verificarContra("nueva12345", codificador::matches)).isTrue();
        assertThat(usuario.verificarContra("actual123", codificador::matches)).isFalse();
    }

    /**
     * El dueño reseteando la de otro no necesita la actual: si la supiera, no
     * haria falta resetearla.
     */
    @Test
    @DisplayName("El reseteo de la contraseña de otro no pide la actual")
    void reseteoDeOtroNoPideLaActual() {
        Usuario usuario = usuarioCon(rol(Rol.CAPATAZ_DE_OBRA, 2L), "vieja1234", 2L);

        servicio().cambiarContrasena(2L, new CambioContrasena(null, "nueva12345"), false);

        assertThat(usuario.verificarContra("nueva12345", codificador::matches)).isTrue();
    }

    // ------------------------------------------------------------------
    //  No dejar el sistema sin dueño
    // ------------------------------------------------------------------

    /**
     * La regla que protege al sistema de si mismo: sin ningun dueño activo
     * nadie puede administrar usuarios ni aprobar un presupuesto, y el unico
     * camino de vuelta es tocar la base a mano.
     */
    @Test
    @DisplayName("No se da de baja al último dueño activo")
    void noSeDaDeBajaAlUltimoDueno() {
        usuarioCon(rol(Rol.DUENO, 1L), "clave1234", 1L);
        when(repositorio.countByRolNombreRolAndEstado(Rol.DUENO, Usuario.ESTADO_ACTIVO))
                .thenReturn(1L);

        assertThatThrownBy(() -> servicio().desactivar(
                1L, new BajaUsuario("Se va"), 99L))
                .isInstanceOf(ReglaDeNegocioException.class)
                .hasMessageContaining("única cuenta activa con rol Dueño");

        verify(auditoria, never()).registrar(anyString(), anyString());
    }

    @Test
    @DisplayName("Con dos dueños activos, uno se puede dar de baja")
    void conDosDuenosSePuedeDarDeBaja() {
        Usuario usuario = usuarioCon(rol(Rol.DUENO, 1L), "clave1234", 1L);
        when(repositorio.countByRolNombreRolAndEstado(Rol.DUENO, Usuario.ESTADO_ACTIVO))
                .thenReturn(2L);

        servicio().desactivar(1L, new BajaUsuario("Dejó la empresa"), 99L);

        assertThat(usuario.estaActivo()).isFalse();
        assertThat(usuario.getMotivoBaja()).isEqualTo("Dejó la empresa");
    }

    @Test
    @DisplayName("Tampoco se le puede sacar el rol Dueño al último dueño")
    void noSeLeQuitaElRolAlUltimoDueno() {
        usuarioCon(rol(Rol.DUENO, 1L), "clave1234", 1L);
        when(rolRepositorio.findById(3L)).thenReturn(Optional.of(rol(Rol.CAPATAZ_DE_OBRA, 3L)));
        when(repositorio.countByRolNombreRolAndEstado(Rol.DUENO, Usuario.ESTADO_ACTIVO))
                .thenReturn(1L);

        assertThatThrownBy(() -> servicio().cambiarRol(1L, new CambioRol(3L)))
                .isInstanceOf(ReglaDeNegocioException.class)
                .hasMessageContaining("única cuenta activa con rol Dueño");
    }

    /**
     * Darse de baja uno mismo dejaria al usuario afuera del sistema en el acto,
     * y si ademas era el ultimo dueño, nadie podria reactivarlo.
     */
    @Test
    @DisplayName("Nadie puede darse de baja a sí mismo")
    void nadieSeDaDeBajaASiMismo() {
        usuarioCon(rol(Rol.DUENO, 1L), "clave1234", 1L);

        assertThatThrownBy(() -> servicio().desactivar(
                1L, new BajaUsuario("Me voy"), 1L))
                .isInstanceOf(ReglaDeNegocioException.class)
                .hasMessageContaining("tu propia cuenta");
    }

    // ------------------------------------------------------------------
    //  Nombre de usuario
    // ------------------------------------------------------------------

    @Test
    @DisplayName("No se repite el nombre de usuario, sin distinguir mayúsculas")
    void noSeRepiteElNombre() {
        when(repositorio.existsByNombreUsuarioIgnoreCase("Ricardo")).thenReturn(true);

        assertThatThrownBy(() -> servicio().crear(
                new NuevoUsuario("Ricardo", "granica2026", 1L, null)))
                .isInstanceOf(ReglaDeNegocioException.class)
                .hasMessageContaining("Ya existe una cuenta");
    }

    @Test
    @DisplayName("Un operario no puede tener dos cuentas")
    void unOperarioUnaSolaCuenta() {
        when(repositorio.existsByNombreUsuarioIgnoreCase("jorge")).thenReturn(false);
        when(rolRepositorio.findById(3L)).thenReturn(Optional.of(rol(Rol.CAPATAZ_DE_OBRA, 3L)));
        when(repositorio.existsByOperarioIdOperario(7L)).thenReturn(true);

        assertThatThrownBy(() -> servicio().crear(
                new NuevoUsuario("jorge", "capataz2026", 3L, 7L)))
                .isInstanceOf(ReglaDeNegocioException.class)
                .hasMessageContaining("ya tiene una cuenta");
    }
}
