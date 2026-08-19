package com.sigco.obras;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.sigco.clientes.Cliente;
import com.sigco.clientes.ClienteRepository;
import com.sigco.common.exception.RecursoNoEncontradoException;
import com.sigco.common.exception.ReglaDeNegocioException;
import com.sigco.obras.dto.CambioEstadoObra;
import com.sigco.obras.dto.ObraEdicion;
import com.sigco.obras.dto.ObraRespuesta;
import com.sigco.obras.dto.ObraSolicitud;
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
 * Tests de las reglas del modulo Obras.
 *
 * El grueso esta en el ciclo de vida, que es donde vive casi toda la logica de
 * este modulo:
 *
 *      En presupuestacion  ──►  En ejecucion  ──►  Finalizada
 *              │                      │
 *              └──────────────────────┴──►  Cancelada
 */
@ExtendWith(MockitoExtension.class)
class ObraServiceTest {

    @Mock
    private ObraRepository repositorio;

    @Mock
    private ClienteRepository clienteRepositorio;

    @InjectMocks
    private ObraService servicio;

    private Cliente unCliente() {
        return new Cliente("Marcela Ferrari", null, null, null, null);
    }

    private Obra unaObra() {
        return new Obra(unCliente(), "Av. Cabildo 2340", Obra.INMUEBLE_DEPARTAMENTO,
                Obra.TIPO_REFORMA, LocalDate.of(2026, 12, 1), null);
    }

    private ObraSolicitud unaSolicitud() {
        return new ObraSolicitud(1L, "Av. Cabildo 2340", Obra.INMUEBLE_DEPARTAMENTO,
                Obra.TIPO_REFORMA, LocalDate.of(2026, 12, 1), "Acceso por cochera");
    }

    // ------------------------------------------------------------------
    //  Alta
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Alta de obra")
    class Alta {

        @Test
        @DisplayName("Una obra nueva nace En presupuestación y sin fecha de inicio")
        void naceEnPresupuestacion() {
            when(clienteRepositorio.findById(1L)).thenReturn(Optional.of(unCliente()));
            when(repositorio.save(any(Obra.class)))
                    .thenAnswer(invocacion -> invocacion.getArgument(0));

            ObraRespuesta respuesta = servicio.crear(unaSolicitud());

            assertThat(respuesta.estado()).isEqualTo(Obra.ESTADO_EN_PRESUPUESTACION);
            // La fecha de inicio se carga recien al aprobarse el presupuesto.
            assertThat(respuesta.fechaInicioReal()).isNull();
            assertThat(respuesta.fechaCreacion()).isNotNull();
        }

        @Test
        @DisplayName("No se puede crear una obra para un cliente que no existe")
        void sinClienteFalla() {
            when(clienteRepositorio.findById(1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> servicio.crear(unaSolicitud()))
                    .isInstanceOf(RecursoNoEncontradoException.class)
                    .hasMessageContaining("Cliente");
        }
    }

    // ------------------------------------------------------------------
    //  Ciclo de vida
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Ciclo de vida")
    class CicloDeVida {

        @Test
        @DisplayName("De presupuestación pasa a ejecución y ahí sí acepta la fecha de inicio")
        void pasaAEjecucion() {
            Obra obra = unaObra();
            when(repositorio.buscarConCliente(1L)).thenReturn(Optional.of(obra));

            ObraRespuesta respuesta = servicio.cambiarEstado(1L, new CambioEstadoObra(
                    Obra.ESTADO_EN_EJECUCION, null, LocalDate.of(2026, 3, 4)));

            assertThat(respuesta.estado()).isEqualTo(Obra.ESTADO_EN_EJECUCION);
            assertThat(respuesta.fechaInicioReal()).isEqualTo(LocalDate.of(2026, 3, 4));
        }

        @Test
        @DisplayName("Una obra en presupuestación no puede finalizarse sin haberse ejecutado")
        void noFinalizaSinEjecutarse() {
            when(repositorio.buscarConCliente(1L)).thenReturn(Optional.of(unaObra()));

            assertThatThrownBy(() -> servicio.cambiarEstado(1L,
                    new CambioEstadoObra(Obra.ESTADO_FINALIZADA, null, null)))
                    .isInstanceOf(ReglaDeNegocioException.class)
                    .hasMessageContaining("primero tiene que ejecutarse");
        }

        @Test
        @DisplayName("Una obra ya en ejecución no puede volver a pasar a ejecución")
        void noRepiteTransicion() {
            Obra obra = unaObra();
            obra.pasarAEjecucion();
            when(repositorio.buscarConCliente(1L)).thenReturn(Optional.of(obra));

            assertThatThrownBy(() -> servicio.cambiarEstado(1L,
                    new CambioEstadoObra(Obra.ESTADO_EN_EJECUCION, null, null)))
                    .isInstanceOf(ReglaDeNegocioException.class);
        }

        @Test
        @DisplayName("Una obra finalizada ya no admite cambios de estado")
        void finalizadaEsTerminal() {
            Obra obra = unaObra();
            obra.pasarAEjecucion();
            obra.finalizar();
            when(repositorio.buscarConCliente(1L)).thenReturn(Optional.of(obra));

            assertThatThrownBy(() -> servicio.cambiarEstado(1L,
                    new CambioEstadoObra(Obra.ESTADO_CANCELADA, "Ya no va", null)))
                    .isInstanceOf(ReglaDeNegocioException.class)
                    .hasMessageContaining("ya no admite cambios");
        }

        @Test
        @DisplayName("Una obra cancelada tampoco admite cambios")
        void canceladaEsTerminal() {
            Obra obra = unaObra();
            obra.cancelar("El cliente no aceptó el precio");
            when(repositorio.buscarConCliente(1L)).thenReturn(Optional.of(obra));

            assertThatThrownBy(() -> servicio.cambiarEstado(1L,
                    new CambioEstadoObra(Obra.ESTADO_EN_EJECUCION, null, null)))
                    .isInstanceOf(ReglaDeNegocioException.class);
        }
    }

    // ------------------------------------------------------------------
    //  Cancelación
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Cancelación")
    class Cancelacion {

        @Test
        @DisplayName("Cancelar sin motivo se rechaza")
        void motivoObligatorio() {
            when(repositorio.buscarConCliente(1L)).thenReturn(Optional.of(unaObra()));

            assertThatThrownBy(() -> servicio.cambiarEstado(1L,
                    new CambioEstadoObra(Obra.ESTADO_CANCELADA, "   ", null)))
                    .isInstanceOf(ReglaDeNegocioException.class)
                    .hasMessageContaining("motivo");
        }

        @Test
        @DisplayName("Cancelar con motivo conserva el registro y el motivo")
        void cancelaConMotivo() {
            when(repositorio.buscarConCliente(1L)).thenReturn(Optional.of(unaObra()));

            ObraRespuesta respuesta = servicio.cambiarEstado(1L, new CambioEstadoObra(
                    Obra.ESTADO_CANCELADA, "El cliente no aceptó el precio", null));

            assertThat(respuesta.estado()).isEqualTo(Obra.ESTADO_CANCELADA);
            assertThat(respuesta.motivoCancelacion()).isEqualTo("El cliente no aceptó el precio");
            // La obra no se borra: queda para entender más adelante por qué no
            // se concretó.
            assertThat(respuesta.direccionObra()).isEqualTo("Av. Cabildo 2340");
        }
    }

    // ------------------------------------------------------------------
    //  Edición
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Edición")
    class Edicion {

        @Test
        @DisplayName("No se puede cargar la fecha de inicio mientras la obra está en presupuestación")
        void fechaInicioBloqueadaEnPresupuestacion() {
            when(repositorio.buscarConCliente(1L)).thenReturn(Optional.of(unaObra()));

            assertThatThrownBy(() -> servicio.actualizar(1L, new ObraEdicion(
                    "Av. Cabildo 2340", Obra.INMUEBLE_DEPARTAMENTO,
                    LocalDate.of(2026, 3, 4), null, null)))
                    .isInstanceOf(ReglaDeNegocioException.class)
                    .hasMessageContaining("presupuesto definitivo");
        }

        @Test
        @DisplayName("Con la obra en ejecución sí se puede corregir la fecha de inicio")
        void fechaInicioPermitidaEnEjecucion() {
            Obra obra = unaObra();
            obra.pasarAEjecucion();
            when(repositorio.buscarConCliente(1L)).thenReturn(Optional.of(obra));

            ObraRespuesta respuesta = servicio.actualizar(1L, new ObraEdicion(
                    "Av. Cabildo 2340", Obra.INMUEBLE_DEPARTAMENTO,
                    LocalDate.of(2026, 3, 10), LocalDate.of(2026, 12, 1), null));

            assertThat(respuesta.fechaInicioReal()).isEqualTo(LocalDate.of(2026, 3, 10));
        }

        @Test
        @DisplayName("La fecha estimada de fin no puede ser anterior a la de inicio")
        void fechasCoherentes() {
            Obra obra = unaObra();
            obra.pasarAEjecucion();
            when(repositorio.buscarConCliente(1L)).thenReturn(Optional.of(obra));

            assertThatThrownBy(() -> servicio.actualizar(1L, new ObraEdicion(
                    "Av. Cabildo 2340", Obra.INMUEBLE_DEPARTAMENTO,
                    LocalDate.of(2026, 6, 1), LocalDate.of(2026, 3, 1), null)))
                    .isInstanceOf(ReglaDeNegocioException.class)
                    .hasMessageContaining("anterior");
        }

        @Test
        @DisplayName("La edición no cambia el tipo de obra ni el cliente")
        void noCambiaTipoNiCliente() {
            Obra obra = unaObra();
            when(repositorio.buscarConCliente(1L)).thenReturn(Optional.of(obra));

            ObraRespuesta respuesta = servicio.actualizar(1L, new ObraEdicion(
                    "Otra dirección 100", Obra.INMUEBLE_LOCAL, null, null, "Nota nueva"));

            assertThat(respuesta.direccionObra()).isEqualTo("Otra dirección 100");
            assertThat(respuesta.tipoInmueble()).isEqualTo(Obra.INMUEBLE_LOCAL);
            // El tipo de obra determina el circuito de Presupuestación: no está
            // en ObraEdicion, así que no hay forma de enviarlo.
            assertThat(respuesta.tipoObra()).isEqualTo(Obra.TIPO_REFORMA);
            assertThat(respuesta.nombreCliente()).isEqualTo("Marcela Ferrari");
        }
    }

    @Test
    @DisplayName("Pedir una obra que no existe devuelve el error que se traduce a 404")
    void inexistenteFalla() {
        when(repositorio.buscarConCliente(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.obtener(999L))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessageContaining("Obra");
    }
}
