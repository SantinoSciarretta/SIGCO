package com.sigco.accesos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.sigco.accesos.dto.AccesosDtos.PermisosDelRol;
import com.sigco.common.exception.ReglaDeNegocioException;
import com.sigco.usuarios.UsuarioRepository;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Tests del modulo Accesos.
 *
 * Se concentran en las dos reglas que protegen al sistema de si mismo. Las dos
 * existen porque una casilla mal marcada en la pantalla de permisos puede dejar
 * el sistema inutilizable o romper una regla del informe sin que nadie lo note:
 *
 *   - al rol Dueño no se le puede quitar la administracion de accesos ni de
 *     usuarios: nadie podria volver a entrar a corregirlo
 *   - a un capataz no se le puede dar el permiso de aprobar pedidos: el informe
 *     define esa aprobacion como indelegable
 */
@ExtendWith(MockitoExtension.class)
class AccesosServiceTest {

    @Mock private RolRepository rolRepositorio;
    @Mock private PermisoRepository permisoRepositorio;
    @Mock private RegistroAuditoriaRepository auditoriaRepositorio;
    @Mock private UsuarioRepository usuarioRepositorio;
    @Mock private ServicioAuditoria auditoria;

    @InjectMocks private AccesosService servicio;

    // ------------------------------------------------------------------
    //  Ayudantes
    // ------------------------------------------------------------------

    private static void asignar(Object objeto, String campo, Object valor) {
        try {
            Field f = objeto.getClass().getDeclaredField(campo);
            f.setAccessible(true);
            f.set(objeto, valor);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Rol y Permiso no tienen constructor publico: los crea la migracion V13. */
    private static <T> T crear(Class<T> clase) {
        try {
            var c = clase.getDeclaredConstructor();
            c.setAccessible(true);
            return c.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Permiso permiso(Long id, String nombre) {
        Permiso p = crear(Permiso.class);
        asignar(p, "idPermiso", id);
        asignar(p, "nombrePermiso", nombre);
        asignar(p, "modulo", "Prueba");
        return p;
    }

    private static Rol rol(Long id, String nombre) {
        Rol r = crear(Rol.class);
        asignar(r, "idRol", id);
        asignar(r, "nombreRol", nombre);
        return r;
    }

    private static final Permiso ACCESOS_EDITAR = permiso(1L, Permiso.ACCESOS_EDITAR);
    private static final Permiso USUARIOS_EDITAR = permiso(2L, Permiso.USUARIOS_EDITAR);
    private static final Permiso COMPRAS_APROBAR = permiso(3L, Permiso.COMPRAS_APROBAR);
    private static final Permiso OBRAS_VER = permiso(4L, "obras.ver");

    private void conRol(Rol rol) {
        lenient().when(rolRepositorio.conPermisos(rol.getIdRol())).thenReturn(Optional.of(rol));
        lenient().when(usuarioRepositorio.countByRolNombreRolAndEstado(
                rol.getNombreRol(), "Activo")).thenReturn(1L);
    }

    private void conPermisos(List<Long> ids, List<Permiso> permisos) {
        when(permisoRepositorio.findAllById(ids)).thenReturn(permisos);
    }

    // ------------------------------------------------------------------
    //  El Dueño no se puede dejar afuera
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Al Dueño no se le puede quitar la administración de accesos")
    void elDuenoConservaAccesos() {
        Rol dueno = rol(1L, Rol.DUENO);
        conRol(dueno);
        conPermisos(List.of(2L, 4L), List.of(USUARIOS_EDITAR, OBRAS_VER));

        assertThatThrownBy(() -> servicio.definirPermisos(1L, new PermisosDelRol(List.of(2L, 4L))))
                .isInstanceOf(ReglaDeNegocioException.class)
                .hasMessageContaining("administrar los permisos");
    }

    @Test
    @DisplayName("Al Dueño tampoco se le puede quitar la administración de usuarios")
    void elDuenoConservaUsuarios() {
        Rol dueno = rol(1L, Rol.DUENO);
        conRol(dueno);
        conPermisos(List.of(1L, 4L), List.of(ACCESOS_EDITAR, OBRAS_VER));

        assertThatThrownBy(() -> servicio.definirPermisos(1L, new PermisosDelRol(List.of(1L, 4L))))
                .isInstanceOf(ReglaDeNegocioException.class)
                .hasMessageContaining("administrar las cuentas");
    }

    @Test
    @DisplayName("Con los dos permisos críticos, el cambio se aplica")
    void elDuenoPuedeCambiarElResto() {
        Rol dueno = rol(1L, Rol.DUENO);
        conRol(dueno);
        conPermisos(List.of(1L, 2L), List.of(ACCESOS_EDITAR, USUARIOS_EDITAR));

        servicio.definirPermisos(1L, new PermisosDelRol(List.of(1L, 2L)));

        assertThat(dueno.tienePermiso(Permiso.ACCESOS_EDITAR)).isTrue();
        assertThat(dueno.tienePermiso("obras.ver")).isFalse();
    }

    // ------------------------------------------------------------------
    //  La aprobación de pedidos es indelegable
    // ------------------------------------------------------------------

    /**
     * Regla textual del informe. Se sostiene aca y no solo en la carga inicial:
     * sin esto, marcar una casilla en la pantalla de Accesos la anularia en
     * silencio.
     */
    @Test
    @DisplayName("Un capataz no puede recibir el permiso de aprobar pedidos")
    void aprobarPedidosEsIndelegable() {
        Rol capataz = rol(2L, Rol.CAPATAZ_GENERAL);
        conRol(capataz);
        conPermisos(List.of(3L, 4L), List.of(COMPRAS_APROBAR, OBRAS_VER));

        assertThatThrownBy(() -> servicio.definirPermisos(2L, new PermisosDelRol(List.of(3L, 4L))))
                .isInstanceOf(ReglaDeNegocioException.class)
                .hasMessageContaining("indelegable");
    }

    @Test
    @DisplayName("Un capataz sí puede recibir el resto de los permisos de compras")
    void elCapatazPuedeElResto() {
        Rol capataz = rol(2L, Rol.CAPATAZ_GENERAL);
        conRol(capataz);
        conPermisos(List.of(4L), List.of(OBRAS_VER));

        servicio.definirPermisos(2L, new PermisosDelRol(List.of(4L)));

        assertThat(capataz.tienePermiso("obras.ver")).isTrue();
        assertThat(capataz.tienePermiso(Permiso.COMPRAS_APROBAR)).isFalse();
    }

    // ------------------------------------------------------------------
    //  Validación de la entrada
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Se rechaza la lista si alguno de los permisos no existe")
    void rechazaPermisoInexistente() {
        Rol capataz = rol(2L, Rol.CAPATAZ_GENERAL);
        conRol(capataz);
        // Se piden dos, la base devuelve uno: el otro no existe.
        conPermisos(List.of(4L, 99L), List.of(OBRAS_VER));

        assertThatThrownBy(() -> servicio.definirPermisos(2L, new PermisosDelRol(List.of(4L, 99L))))
                .isInstanceOf(ReglaDeNegocioException.class)
                .hasMessageContaining("no existe");
    }
}
