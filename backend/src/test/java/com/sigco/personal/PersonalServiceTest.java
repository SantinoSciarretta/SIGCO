package com.sigco.personal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sigco.clientes.Cliente;
import com.sigco.common.exception.ReglaDeNegocioException;
import com.sigco.obras.Obra;
import com.sigco.obras.ObraRepository;
import com.sigco.personal.dto.PersonalDtos.Asignacion;
import com.sigco.personal.dto.PersonalDtos.CambioEstadoOperario;
import com.sigco.personal.dto.PersonalDtos.InasistenciaRespuesta;
import com.sigco.personal.dto.PersonalDtos.InasistenciaSolicitud;
import com.sigco.personal.dto.PersonalDtos.OperarioRespuesta;
import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Tests del modulo Personal.
 *
 * El foco esta en las dos reglas que hacen util el registro de faltas: que una
 * inasistencia siempre este atada a una obra donde el operario efectivamente
 * trabajo, y que dar de baja a alguien no borre su historial.
 */
@ExtendWith(MockitoExtension.class)
class PersonalServiceTest {

    @Mock private OperarioRepository repositorio;
    @Mock private InasistenciaRepository inasistenciaRepositorio;
    @Mock private ObraRepository obraRepositorio;

    @InjectMocks private PersonalService servicio;

    private static void asignarId(Object entidad, String campo, Long valor) {
        try {
            Field f = entidad.getClass().getDeclaredField(campo);
            f.setAccessible(true);
            f.set(entidad, valor);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private Obra obra(Long id) {
        Cliente c = new Cliente("Mario Lopez", null, null, null, null);
        asignarId(c, "idCliente", 1L);
        Obra o = new Obra(c, "Ituzaingo 231, Pilar", "Casa", Obra.TIPO_REFORMA, null, null);
        asignarId(o, "idObra", id);
        o.pasarAEjecucion();
        return o;
    }

    private Operario operario(Long id) {
        Operario o = new Operario("Juan Pérez", "11-5555-1234");
        asignarId(o, "idOperario", id);
        lenient().when(repositorio.buscarCompleto(id)).thenReturn(Optional.of(o));
        lenient().when(inasistenciaRepositorio.countByOperarioIdOperario(id)).thenReturn(0L);
        return o;
    }

    // ==================================================================

    @Nested
    @DisplayName("Asignación a obras")
    class Asignar {

        @Test
        @DisplayName("Se asigna a una obra en ejecución")
        void asignaAObra() {
            Operario o = operario(1L);
            when(obraRepositorio.findById(5L)).thenReturn(Optional.of(obra(5L)));

            OperarioRespuesta r = servicio.asignar(1L, new Asignacion(5L));

            assertThat(r.cantidadObrasVigentes()).isEqualTo(1);
            assertThat(o.estaAsignadoHoyA(5L)).isTrue();
        }

        @Test
        @DisplayName("No se asigna dos veces a la misma obra")
        void noSeAsignaDosVeces() {
            Operario o = operario(1L);
            o.asignar(new OperarioObra(o, obra(5L)));
            when(obraRepositorio.findById(5L)).thenReturn(Optional.of(obra(5L)));

            assertThatThrownBy(() -> servicio.asignar(1L, new Asignacion(5L)))
                    .isInstanceOf(ReglaDeNegocioException.class)
                    .hasMessageContaining("ya está asignado");
        }

        @Test
        @DisplayName("Una asignación cerrada se reabre en vez de duplicarse")
        void reabreAsignacionCerrada() {
            Operario o = operario(1L);
            OperarioObra previa = new OperarioObra(o, obra(5L));
            previa.desasignar(LocalDate.of(2026, 6, 1));
            o.asignar(previa);
            when(obraRepositorio.findById(5L)).thenReturn(Optional.of(obra(5L)));

            OperarioRespuesta r = servicio.asignar(1L, new Asignacion(5L));

            // La clave primaria es (operario, obra): no puede haber dos filas.
            assertThat(r.asignaciones()).hasSize(1);
            assertThat(previa.estaVigente()).isTrue();
        }

        @Test
        @DisplayName("Un operario inactivo no se asigna a obras")
        void inactivoNoSeAsigna() {
            Operario o = operario(1L);
            o.desactivar();

            assertThatThrownBy(() -> servicio.asignar(1L, new Asignacion(5L)))
                    .isInstanceOf(ReglaDeNegocioException.class)
                    .hasMessageContaining("está inactivo");
        }

        @Test
        @DisplayName("Desasignar cierra la fila pero conserva el historial")
        void desasignarConservaHistorial() {
            Operario o = operario(1L);
            o.asignar(new OperarioObra(o, obra(5L)));

            OperarioRespuesta r = servicio.desasignar(1L, 5L);

            // La obra sigue en el historial, marcada como no vigente. Borrar la
            // fila habría perdido el registro de que trabajó ahí.
            assertThat(r.asignaciones()).hasSize(1);
            assertThat(r.asignaciones().get(0).vigente()).isFalse();
            assertThat(r.cantidadObrasVigentes()).isZero();
        }
    }

    // ==================================================================

    @Nested
    @DisplayName("Baja del operario")
    class Baja {

        @Test
        @DisplayName("Desactivar cierra las asignaciones vigentes sin borrarlas")
        void desactivarCierraAsignaciones() {
            Operario o = operario(1L);
            o.asignar(new OperarioObra(o, obra(5L)));
            o.asignar(new OperarioObra(o, obra(6L)));

            OperarioRespuesta r = servicio.cambiarEstado(1L, new CambioEstadoOperario("Inactivo"));

            assertThat(r.estado()).isEqualTo(Operario.ESTADO_INACTIVO);
            assertThat(r.cantidadObrasVigentes()).isZero();
            // El informe pide conservar el historial de obras anteriores.
            assertThat(r.asignaciones()).hasSize(2);
            assertThat(r.asignaciones()).allMatch(a -> !a.vigente());
        }
    }

    // ==================================================================

    @Nested
    @DisplayName("Inasistencias")
    class Inasistencias {

        @Test
        @DisplayName("Se registra una falta en una obra donde el operario trabaja")
        void registraFalta() {
            Operario o = operario(1L);
            o.asignar(new OperarioObra(o, obra(5L)));
            when(obraRepositorio.findById(5L)).thenReturn(Optional.of(obra(5L)));
            when(inasistenciaRepositorio.save(any(Inasistencia.class)))
                    .thenAnswer(i -> i.getArgument(0));

            InasistenciaRespuesta r = servicio.registrarInasistencia(new InasistenciaSolicitud(
                    1L, 5L, LocalDate.of(2026, 9, 2), null));

            assertThat(r.fechaFalta()).isEqualTo(LocalDate.of(2026, 9, 2));
            // El motivo NO es obligatorio: muchas faltas no tienen justificación
            // conocida al registrarlas.
            assertThat(r.motivo()).isNull();
        }

        @Test
        @DisplayName("No se registra una falta en una obra donde nunca estuvo asignado")
        void faltaEnObraAjenaSeRechaza() {
            operario(1L);
            when(obraRepositorio.findById(9L)).thenReturn(Optional.of(obra(9L)));

            assertThatThrownBy(() -> servicio.registrarInasistencia(new InasistenciaSolicitud(
                    1L, 9L, LocalDate.now(), null)))
                    .isInstanceOf(ReglaDeNegocioException.class)
                    .hasMessageContaining("no está ni estuvo asignado");
        }

        @Test
        @DisplayName("Vale registrar una falta en una obra donde estuvo, aunque ya no esté")
        void faltaEnObraAnteriorEsValida() {
            Operario o = operario(1L);
            OperarioObra cerrada = new OperarioObra(o, obra(5L));
            cerrada.desasignar(LocalDate.of(2026, 8, 1));
            o.asignar(cerrada);
            when(obraRepositorio.findById(5L)).thenReturn(Optional.of(obra(5L)));
            when(inasistenciaRepositorio.save(any(Inasistencia.class)))
                    .thenAnswer(i -> i.getArgument(0));

            // Una falta se puede cargar después de que el operario cambió de
            // obra: por eso la regla mira el historial, no solo lo vigente.
            InasistenciaRespuesta r = servicio.registrarInasistencia(new InasistenciaSolicitud(
                    1L, 5L, LocalDate.of(2026, 7, 15), "Aviso que estaba enfermo"));

            assertThat(r.motivo()).isEqualTo("Aviso que estaba enfermo");
        }

        @Test
        @DisplayName("No se cargan dos faltas del mismo operario, obra y fecha")
        void faltaDuplicadaSeRechaza() {
            Operario o = operario(1L);
            o.asignar(new OperarioObra(o, obra(5L)));
            when(obraRepositorio.findById(5L)).thenReturn(Optional.of(obra(5L)));
            when(inasistenciaRepositorio.existsByOperarioIdOperarioAndObraIdObraAndFechaFalta(
                    1L, 5L, LocalDate.of(2026, 9, 2))).thenReturn(true);

            assertThatThrownBy(() -> servicio.registrarInasistencia(new InasistenciaSolicitud(
                    1L, 5L, LocalDate.of(2026, 9, 2), null)))
                    .isInstanceOf(ReglaDeNegocioException.class)
                    .hasMessageContaining("Ya hay una inasistencia");

            verify(inasistenciaRepositorio, never()).save(any());
        }
    }
}
