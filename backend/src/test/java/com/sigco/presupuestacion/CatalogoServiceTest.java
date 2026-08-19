package com.sigco.presupuestacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sigco.common.exception.RecursoNoEncontradoException;
import com.sigco.common.exception.ReglaDeNegocioException;
import com.sigco.presupuestacion.dto.RubroRespuesta;
import com.sigco.presupuestacion.dto.RubroSolicitud;
import com.sigco.presupuestacion.dto.SubrubroRespuesta;
import com.sigco.presupuestacion.dto.SubrubroSolicitud;
import java.lang.reflect.Field;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Tests de las reglas del catalogo de rubros y subrubros.
 */
@ExtendWith(MockitoExtension.class)
class CatalogoServiceTest {

    @Mock
    private RubroRepository rubroRepositorio;

    @Mock
    private SubrubroRepository subrubroRepositorio;

    @InjectMocks
    private CatalogoService servicio;

    /**
     * Asigna el identificador de una entidad recien creada.
     *
     * En produccion lo pone la base al guardar; en un test con dobles, la
     * entidad nunca pasa por ahi. Se usa reflexion en lugar de agregar un
     * setter que el codigo de produccion no necesita: un setId publico seria
     * una puerta abierta a pisar la clave primaria desde cualquier lado.
     */
    private static void asignarId(Object entidad, String campo, Long valor) {
        try {
            Field f = entidad.getClass().getDeclaredField(campo);
            f.setAccessible(true);
            f.set(entidad, valor);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private Rubro rubroConId(String nombre, Long id) {
        Rubro rubro = new Rubro(nombre);
        asignarId(rubro, "idRubro", id);
        return rubro;
    }

    private Subrubro subrubroConId(Rubro rubro, String nombre, Long id) {
        Subrubro subrubro = new Subrubro(rubro, nombre);
        asignarId(subrubro, "idSubrubro", id);
        return subrubro;
    }

    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Rubros")
    class Rubros {

        @Test
        @DisplayName("Un rubro nuevo nace Activo")
        void naceActivo() {
            when(rubroRepositorio.findByNombreRubroIgnoreCase(anyString()))
                    .thenReturn(Optional.empty());
            when(rubroRepositorio.save(any(Rubro.class)))
                    .thenAnswer(i -> i.getArgument(0));

            RubroRespuesta respuesta = servicio.crearRubro(new RubroSolicitud("  Albanileria  "));

            assertThat(respuesta.estado()).isEqualTo(Rubro.ESTADO_ACTIVO);
            assertThat(respuesta.nombreRubro()).isEqualTo("Albanileria");
        }

        @Test
        @DisplayName("No se pueden crear dos rubros con el mismo nombre")
        void nombreDuplicadoSeRechaza() {
            when(rubroRepositorio.findByNombreRubroIgnoreCase("Plomeria"))
                    .thenReturn(Optional.of(rubroConId("Plomeria", 5L)));

            assertThatThrownBy(() -> servicio.crearRubro(new RubroSolicitud("Plomeria")))
                    .isInstanceOf(ReglaDeNegocioException.class)
                    .hasMessageContaining("Ya existe un rubro");

            verify(rubroRepositorio, never()).save(any());
        }

        @Test
        @DisplayName("Al renombrar, el propio rubro no cuenta como duplicado")
        void renombrarASiMismoEsValido() {
            Rubro rubro = rubroConId("Plomeria", 5L);
            when(rubroRepositorio.findById(5L)).thenReturn(Optional.of(rubro));
            when(rubroRepositorio.findByNombreRubroIgnoreCase("PLOMERIA"))
                    .thenReturn(Optional.of(rubro));

            RubroRespuesta respuesta = servicio.renombrarRubro(5L, new RubroSolicitud("PLOMERIA"));

            assertThat(respuesta.nombreRubro()).isEqualTo("PLOMERIA");
        }

        @Test
        @DisplayName("Renombrar con el nombre de otro rubro se rechaza")
        void renombrarPisandoOtroSeRechaza() {
            when(rubroRepositorio.findById(5L)).thenReturn(Optional.of(rubroConId("Plomeria", 5L)));
            when(rubroRepositorio.findByNombreRubroIgnoreCase("Albanileria"))
                    .thenReturn(Optional.of(rubroConId("Albanileria", 9L)));

            assertThatThrownBy(() -> servicio.renombrarRubro(5L, new RubroSolicitud("Albanileria")))
                    .isInstanceOf(ReglaDeNegocioException.class);
        }

        @Test
        @DisplayName("Desactivar conserva el rubro y sus subrubros")
        void desactivarConserva() {
            Rubro rubro = rubroConId("Plomeria", 5L);
            subrubroConId(rubro, "Desagues", 1L);
            when(rubroRepositorio.findById(5L)).thenReturn(Optional.of(rubro));

            RubroRespuesta respuesta = servicio.cambiarEstadoRubro(5L, Rubro.ESTADO_INACTIVO);

            assertThat(respuesta.estado()).isEqualTo(Rubro.ESTADO_INACTIVO);
            // Nada se elimina: el subrubro sigue ahi y con su estado original.
            assertThat(rubro.getSubrubros()).hasSize(1);
            assertThat(rubro.getSubrubros().get(0).estaActivo()).isTrue();
            verify(rubroRepositorio, never()).delete(any());
        }

        @Test
        @DisplayName("Pedir un rubro inexistente devuelve el error que se traduce a 404")
        void inexistenteFalla() {
            when(rubroRepositorio.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> servicio.cambiarEstadoRubro(999L, Rubro.ESTADO_ACTIVO))
                    .isInstanceOf(RecursoNoEncontradoException.class)
                    .hasMessageContaining("Rubro");
        }
    }

    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Subrubros")
    class Subrubros {

        @Test
        @DisplayName("Un subrubro se crea dentro de su rubro y queda vinculado")
        void seCreaDentroDelRubro() {
            Rubro rubro = rubroConId("Albanileria", 1L);
            when(rubroRepositorio.findById(1L)).thenReturn(Optional.of(rubro));
            when(subrubroRepositorio.findByRubroIdRubroAndNombreSubrubroIgnoreCase(anyLong(), anyString()))
                    .thenReturn(Optional.empty());
            when(subrubroRepositorio.save(any(Subrubro.class)))
                    .thenAnswer(i -> i.getArgument(0));

            SubrubroRespuesta respuesta = servicio.crearSubrubro(
                    1L, new SubrubroSolicitud("Contrapisos"));

            assertThat(respuesta.nombreSubrubro()).isEqualTo("Contrapisos");
            assertThat(respuesta.nombreRubro()).isEqualTo("Albanileria");
            assertThat(respuesta.estado()).isEqualTo(Subrubro.ESTADO_ACTIVO);
            // El subrubro queda dentro de la coleccion de su rubro.
            assertThat(rubro.getSubrubros()).hasSize(1);
        }

        @Test
        @DisplayName("No se puede crear un subrubro en un rubro que no existe")
        void rubroInexistenteFalla() {
            when(rubroRepositorio.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> servicio.crearSubrubro(999L, new SubrubroSolicitud("Algo")))
                    .isInstanceOf(RecursoNoEncontradoException.class)
                    .hasMessageContaining("Rubro");
        }

        @Test
        @DisplayName("No se repite el nombre de un subrubro dentro del mismo rubro")
        void nombreRepetidoEnElMismoRubroSeRechaza() {
            Rubro rubro = rubroConId("Albanileria", 1L);
            when(rubroRepositorio.findById(1L)).thenReturn(Optional.of(rubro));
            when(subrubroRepositorio.findByRubroIdRubroAndNombreSubrubroIgnoreCase(1L, "Demolicion"))
                    .thenReturn(Optional.of(subrubroConId(rubro, "Demolicion", 7L)));

            assertThatThrownBy(() -> servicio.crearSubrubro(1L, new SubrubroSolicitud("Demolicion")))
                    .isInstanceOf(ReglaDeNegocioException.class)
                    .hasMessageContaining("ya tiene un subrubro");

            verify(subrubroRepositorio, never()).save(any());
        }

        @Test
        @DisplayName("No se puede activar un subrubro cuyo rubro esta inactivo")
        void noSeActivaBajoRubroInactivo() {
            Rubro rubro = rubroConId("Plomeria", 5L);
            rubro.desactivar();
            Subrubro subrubro = subrubroConId(rubro, "Desagues", 3L);
            subrubro.desactivar();
            when(subrubroRepositorio.findById(3L)).thenReturn(Optional.of(subrubro));

            // Quedaria marcado como disponible pero el sistema nunca lo
            // ofreceria, porque la consulta exige que el rubro este activo.
            assertThatThrownBy(() -> servicio.cambiarEstadoSubrubro(3L, Subrubro.ESTADO_ACTIVO))
                    .isInstanceOf(ReglaDeNegocioException.class)
                    .hasMessageContaining("Active primero el rubro");
        }

        @Test
        @DisplayName("Desactivar un subrubro siempre se permite")
        void desactivarSiemprePermitido() {
            Rubro rubro = rubroConId("Plomeria", 5L);
            rubro.desactivar();
            Subrubro subrubro = subrubroConId(rubro, "Desagues", 3L);
            when(subrubroRepositorio.findById(3L)).thenReturn(Optional.of(subrubro));

            SubrubroRespuesta respuesta = servicio.cambiarEstadoSubrubro(3L, Subrubro.ESTADO_INACTIVO);

            assertThat(respuesta.estado()).isEqualTo(Subrubro.ESTADO_INACTIVO);
        }

        @Test
        @DisplayName("Renombrar un subrubro no lo mueve de rubro")
        void renombrarNoMueveDeRubro() {
            Rubro rubro = rubroConId("Albanileria", 1L);
            Subrubro subrubro = subrubroConId(rubro, "Contrapiso", 3L);
            when(subrubroRepositorio.findById(3L)).thenReturn(Optional.of(subrubro));
            when(subrubroRepositorio.findByRubroIdRubroAndNombreSubrubroIgnoreCase(1L, "Contrapisos"))
                    .thenReturn(Optional.empty());

            SubrubroRespuesta respuesta = servicio.renombrarSubrubro(
                    3L, new SubrubroSolicitud("Contrapisos"));

            assertThat(respuesta.nombreSubrubro()).isEqualTo("Contrapisos");
            assertThat(respuesta.idRubro()).isEqualTo(1L);
        }
    }
}
