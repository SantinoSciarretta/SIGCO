package com.sigco.materiales;

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
import com.sigco.materiales.dto.MaterialDtos.MaterialRespuesta;
import com.sigco.materiales.dto.MaterialDtos.MaterialSolicitud;
import com.sigco.presupuestacion.Rubro;
import com.sigco.presupuestacion.RubroRepository;
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
 * Tests de las reglas del catalogo de materiales.
 */
@ExtendWith(MockitoExtension.class)
class MaterialServiceTest {

    @Mock private MaterialRepository repositorio;
    @Mock private RubroRepository rubroRepositorio;

    @InjectMocks private MaterialService servicio;

    private static void asignarId(Object entidad, String campo, Long valor) {
        try {
            Field f = entidad.getClass().getDeclaredField(campo);
            f.setAccessible(true);
            f.set(entidad, valor);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private Rubro rubro(String nombre, Long id) {
        Rubro r = new Rubro(nombre);
        asignarId(r, "idRubro", id);
        return r;
    }

    private Material material(String nombre, Rubro rubro, Long id) {
        Material m = new Material(nombre, rubro, "bolsa");
        asignarId(m, "idMaterial", id);
        return m;
    }

    private void devolverLoQueSeGuarda() {
        when(repositorio.save(any(Material.class))).thenAnswer(i -> i.getArgument(0));
    }

    // ==================================================================

    @Nested
    @DisplayName("Alta")
    class Alta {

        @Test
        @DisplayName("Un material nuevo nace Activo, con su rubro y su unidad")
        void naceActivo() {
            Rubro albanileria = rubro("Albanileria", 1L);
            when(rubroRepositorio.findById(1L)).thenReturn(Optional.of(albanileria));
            when(repositorio.findByRubroIdRubroAndNombreMaterialIgnoreCase(anyLong(), anyString()))
                    .thenReturn(Optional.empty());
            devolverLoQueSeGuarda();

            MaterialRespuesta r = servicio.crear(new MaterialSolicitud(
                    "  Cemento CP40  ", 1L, " bolsa 50 kg "));

            assertThat(r.estado()).isEqualTo(Material.ESTADO_ACTIVO);
            assertThat(r.nombreMaterial()).isEqualTo("Cemento CP40");
            assertThat(r.unidadMedida()).isEqualTo("bolsa 50 kg");
            assertThat(r.nombreRubro()).isEqualTo("Albanileria");
            assertThat(r.fechaAlta()).isNotNull();
        }

        @Test
        @DisplayName("No se repite el nombre dentro del mismo rubro")
        void nombreRepetidoEnElMismoRubro() {
            Rubro albanileria = rubro("Albanileria", 1L);
            when(rubroRepositorio.findById(1L)).thenReturn(Optional.of(albanileria));
            when(repositorio.findByRubroIdRubroAndNombreMaterialIgnoreCase(1L, "Cemento CP40"))
                    .thenReturn(Optional.of(material("Cemento CP40", albanileria, 7L)));

            assertThatThrownBy(() -> servicio.crear(new MaterialSolicitud(
                    "Cemento CP40", 1L, "bolsa")))
                    .isInstanceOf(ReglaDeNegocioException.class)
                    .hasMessageContaining("ya tiene un material llamado");

            verify(repositorio, never()).save(any());
        }

        @Test
        @DisplayName("No se puede cargar un material en un rubro inactivo")
        void rubroInactivo() {
            Rubro inactivo = rubro("Plomeria", 2L);
            inactivo.desactivar();
            when(rubroRepositorio.findById(2L)).thenReturn(Optional.of(inactivo));

            // Quedaria cargado pero el sistema no lo ofreceria en ningun pedido.
            assertThatThrownBy(() -> servicio.crear(new MaterialSolicitud(
                    "Caño PVC", 2L, "unidad")))
                    .isInstanceOf(ReglaDeNegocioException.class)
                    .hasMessageContaining("esta inactivo");
        }

        @Test
        @DisplayName("No se puede cargar un material en un rubro que no existe")
        void rubroInexistente() {
            when(rubroRepositorio.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> servicio.crear(new MaterialSolicitud(
                    "Algo", 99L, "unidad")))
                    .isInstanceOf(RecursoNoEncontradoException.class)
                    .hasMessageContaining("Rubro");
        }
    }

    // ==================================================================

    @Nested
    @DisplayName("Edición")
    class Edicion {

        @Test
        @DisplayName("Un material sí puede cambiar de rubro, a diferencia de un subrubro")
        void cambiaDeRubro() {
            Rubro albanileria = rubro("Albanileria", 1L);
            Rubro impermeabilizaciones = rubro("Impermeabilizaciones", 3L);
            Material membrana = material("Membrana", albanileria, 5L);

            when(repositorio.buscarConRubro(5L)).thenReturn(Optional.of(membrana));
            when(rubroRepositorio.findById(3L)).thenReturn(Optional.of(impermeabilizaciones));
            when(repositorio.findByRubroIdRubroAndNombreMaterialIgnoreCase(3L, "Membrana"))
                    .thenReturn(Optional.empty());

            MaterialRespuesta r = servicio.actualizar(5L, new MaterialSolicitud(
                    "Membrana", 3L, "rollo 10 m"));

            assertThat(r.nombreRubro()).isEqualTo("Impermeabilizaciones");
            assertThat(r.unidadMedida()).isEqualTo("rollo 10 m");
        }

        @Test
        @DisplayName("Al mover de rubro se controla el duplicado en el rubro DESTINO")
        void duplicadoEnElRubroDestino() {
            Rubro albanileria = rubro("Albanileria", 1L);
            Rubro techos = rubro("Techos", 4L);
            Material membrana = material("Membrana", albanileria, 5L);

            when(repositorio.buscarConRubro(5L)).thenReturn(Optional.of(membrana));
            when(rubroRepositorio.findById(4L)).thenReturn(Optional.of(techos));
            // En Techos ya hay una "Membrana".
            when(repositorio.findByRubroIdRubroAndNombreMaterialIgnoreCase(4L, "Membrana"))
                    .thenReturn(Optional.of(material("Membrana", techos, 9L)));

            assertThatThrownBy(() -> servicio.actualizar(5L, new MaterialSolicitud(
                    "Membrana", 4L, "rollo")))
                    .isInstanceOf(ReglaDeNegocioException.class);
        }

        @Test
        @DisplayName("Renombrar sin mover de rubro no se toma como duplicado de sí mismo")
        void renombrarASiMismo() {
            Rubro albanileria = rubro("Albanileria", 1L);
            Material cemento = material("Cemento", albanileria, 5L);

            when(repositorio.buscarConRubro(5L)).thenReturn(Optional.of(cemento));
            when(rubroRepositorio.findById(1L)).thenReturn(Optional.of(albanileria));
            when(repositorio.findByRubroIdRubroAndNombreMaterialIgnoreCase(1L, "Cemento CP40"))
                    .thenReturn(Optional.empty());

            assertThat(servicio.actualizar(5L, new MaterialSolicitud(
                    "Cemento CP40", 1L, "bolsa")).nombreMaterial())
                    .isEqualTo("Cemento CP40");
        }

        @Test
        @DisplayName("Se puede editar un material cuyo rubro quedó inactivo, si no se lo mueve")
        void editarBajoRubroInactivo() {
            Rubro inactivo = rubro("Plomeria", 2L);
            inactivo.desactivar();
            Material cano = material("Caño PVC", inactivo, 5L);

            when(repositorio.buscarConRubro(5L)).thenReturn(Optional.of(cano));
            when(rubroRepositorio.findById(2L)).thenReturn(Optional.of(inactivo));
            when(repositorio.findByRubroIdRubroAndNombreMaterialIgnoreCase(2L, "Caño PVC 110"))
                    .thenReturn(Optional.empty());

            // Corregir el nombre de un material que quedó bajo un rubro inactivo
            // tiene que seguir siendo posible: la restricción es para moverlo
            // hacia un rubro inactivo, no para dejarlo donde ya está.
            assertThat(servicio.actualizar(5L, new MaterialSolicitud(
                    "Caño PVC 110", 2L, "unidad")).nombreMaterial())
                    .isEqualTo("Caño PVC 110");
        }
    }

    // ==================================================================

    @Nested
    @DisplayName("Estado")
    class Estado {

        @Test
        @DisplayName("Desactivar conserva el material")
        void desactivarConserva() {
            Rubro albanileria = rubro("Albanileria", 1L);
            when(repositorio.buscarConRubro(5L))
                    .thenReturn(Optional.of(material("Cemento", albanileria, 5L)));

            MaterialRespuesta r = servicio.cambiarEstado(5L, Material.ESTADO_INACTIVO);

            assertThat(r.estado()).isEqualTo(Material.ESTADO_INACTIVO);
            assertThat(r.nombreMaterial()).isEqualTo("Cemento");
            verify(repositorio, never()).delete(any());
        }

        @Test
        @DisplayName("No se puede activar un material cuyo rubro está inactivo")
        void noSeActivaBajoRubroInactivo() {
            Rubro inactivo = rubro("Plomeria", 2L);
            inactivo.desactivar();
            Material cano = material("Caño PVC", inactivo, 5L);
            cano.desactivar();
            when(repositorio.buscarConRubro(5L)).thenReturn(Optional.of(cano));

            assertThatThrownBy(() -> servicio.cambiarEstado(5L, Material.ESTADO_ACTIVO))
                    .isInstanceOf(ReglaDeNegocioException.class)
                    .hasMessageContaining("Active primero el rubro");
        }

        @Test
        @DisplayName("Pedir un material inexistente devuelve el error que se traduce a 404")
        void inexistente() {
            when(repositorio.buscarConRubro(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> servicio.obtener(999L))
                    .isInstanceOf(RecursoNoEncontradoException.class)
                    .hasMessageContaining("Material");
        }
    }
}
