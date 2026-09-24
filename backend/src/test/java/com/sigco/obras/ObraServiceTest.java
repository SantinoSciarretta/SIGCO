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

    // El alcance por obra (modulo 14) se prueba aparte: aca se le dice que
    // alcanza todo, para que estos tests midan lo que vinieron a medir.
    @Mock
    private com.sigco.seguridad.AlcanceDeObras alcance;

    // Para la regla de cancelacion: si la obra tiene un definitivo aprobado,
    // cancelarla exige confirmacion explicita. Por defecto el mock devuelve
    // lista vacia, o sea "no hay definitivo aprobado", que es el caso de la
    // mayoria de estos tests.
    @Mock
    private com.sigco.presupuestacion.PresupuestoRepository presupuestoRepositorio;

    @Mock
    private com.sigco.accesos.ServicioAuditoria auditoria;

    @InjectMocks
    private ObraService servicio;

    private Cliente unCliente() {
        return new Cliente("Marcela Ferrari", null, null, null, null);
    }

    private Obra unaObra() {
        return new Obra(unCliente(), "Av. Cabildo 2340", Obra.INMUEBLE_DEPARTAMENTO,
                Obra.TIPO_REFORMA, LocalDate.of(2026, 12, 1), null);
    }

    /**
     * Hace que el repositorio conteste que la obra tiene un definitivo aprobado.
     *
     * Lo usan dos reglas distintas: la fecha de inicio real (que solo se carga
     * con el definitivo aprobado) y la cancelación de una obra en marcha.
     */
    private void conDefinitivoAprobado() {
        // El id va con un matcher porque la obra de prueba no está persistida y
        // su id todavía es null; lo que importa acá es el tipo y el estado.
        when(presupuestoRepositorio.findByObraIdObraAndTipoPresupuestoAndEstado(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(
                        com.sigco.presupuestacion.Presupuesto.TIPO_DEFINITIVO),
                org.mockito.ArgumentMatchers.eq(
                        com.sigco.presupuestacion.Presupuesto.ESTADO_APROBADO)))
                .thenReturn(java.util.List.of(
                        org.mockito.Mockito.mock(
                                com.sigco.presupuestacion.Presupuesto.class)));
    }

    /** Hace que el repositorio conteste que la obra ya tiene presupuestos. */
    private void conAlgunPresupuesto() {
        when(presupuestoRepositorio.buscar(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(""),
                org.mockito.ArgumentMatchers.eq("")))
                .thenReturn(java.util.List.of(
                        org.mockito.Mockito.mock(
                                com.sigco.presupuestacion.Presupuesto.class)));
    }

    private ObraSolicitud unaSolicitud() {
        // Sin plazo estimado (los dos null): esta obra de prueba carga la fecha
        // de fin a mano, que es como estaban todas antes del plazo calculado.
        return new ObraSolicitud(1L, "Av. Cabildo 2340", Obra.INMUEBLE_DEPARTAMENTO,
                Obra.TIPO_REFORMA, null, null,
                LocalDate.of(2026, 12, 1), "Acceso por cochera");
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
    //  Plazo estimado
    // ------------------------------------------------------------------

    /**
     * Pedido de Ricardo al probar el sistema: quiere cargar cuándo cree que
     * arranca la obra y cuántos meses cree que dura, y que el sistema calcule
     * solo la fecha tentativa de finalización.
     */
    @Nested
    @DisplayName("Plazo estimado")
    class Plazo {

        @Test
        @DisplayName("Con comienzo y duración, la fecha de fin se calcula sola")
        void calculaElFin() {
            Obra obra = unaObra();

            obra.estimarPlazo(LocalDate.of(2026, 3, 1), 4);

            assertThat(obra.getFechaFinEstimada()).isEqualTo(LocalDate.of(2026, 7, 1));
            assertThat(obra.tienePlazoCalculado()).isTrue();
        }

        @Test
        @DisplayName("Sin duración cargada, la fecha de fin queda como estaba")
        void sinPlazoNoToca() {
            Obra obra = unaObra();   // nace con fin estimado 01/12/2026

            obra.estimarPlazo(LocalDate.of(2026, 3, 1), null);

            assertThat(obra.getFechaFinEstimada()).isEqualTo(LocalDate.of(2026, 12, 1));
            assertThat(obra.tienePlazoCalculado()).isFalse();
        }

        /**
         * Una vez que la obra arrancó de verdad, esa es la fecha que vale:
         * seguir contando desde una estimación vieja daría un plazo que nadie
         * reconoce.
         */
        @Test
        @DisplayName("Al arrancar la obra, el fin se recalcula desde el inicio REAL")
        void recalculaDesdeElInicioReal() {
            Obra obra = unaObra();
            obra.estimarPlazo(LocalDate.of(2026, 3, 1), 4);
            assertThat(obra.getFechaFinEstimada()).isEqualTo(LocalDate.of(2026, 7, 1));

            // La obra arrancó un mes más tarde de lo previsto.
            obra.registrarInicioReal(LocalDate.of(2026, 4, 1));

            assertThat(obra.getFechaFinEstimada()).isEqualTo(LocalDate.of(2026, 8, 1));
        }

        @Test
        @DisplayName("Cambiar la duración mueve la fecha de fin")
        void cambiarLaDuracionMueveElFin() {
            Obra obra = unaObra();
            obra.estimarPlazo(LocalDate.of(2026, 3, 1), 4);

            // Sin el cálculo, acá quedarían dos datos contradictorios: "son 6
            // meses" conviviendo con una fecha de fin de 4 meses.
            obra.estimarPlazo(LocalDate.of(2026, 3, 1), 6);

            assertThat(obra.getFechaFinEstimada()).isEqualTo(LocalDate.of(2026, 9, 1));
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
                    Obra.ESTADO_EN_EJECUCION, null, LocalDate.of(2026, 3, 4), false));

            assertThat(respuesta.estado()).isEqualTo(Obra.ESTADO_EN_EJECUCION);
            assertThat(respuesta.fechaInicioReal()).isEqualTo(LocalDate.of(2026, 3, 4));
        }

        @Test
        @DisplayName("Una obra en presupuestación no puede finalizarse sin haberse ejecutado")
        void noFinalizaSinEjecutarse() {
            when(repositorio.buscarConCliente(1L)).thenReturn(Optional.of(unaObra()));

            assertThatThrownBy(() -> servicio.cambiarEstado(1L,
                    new CambioEstadoObra(Obra.ESTADO_FINALIZADA, null, null, false)))
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
                    new CambioEstadoObra(Obra.ESTADO_EN_EJECUCION, null, null, false)))
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
                    new CambioEstadoObra(Obra.ESTADO_CANCELADA, "Ya no va", null, false)))
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
                    new CambioEstadoObra(Obra.ESTADO_EN_EJECUCION, null, null, false)))
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
                    new CambioEstadoObra(Obra.ESTADO_CANCELADA, "   ", null, false)))
                    .isInstanceOf(ReglaDeNegocioException.class)
                    .hasMessageContaining("motivo");
        }

        @Test
        @DisplayName("Cancelar con motivo conserva el registro y el motivo")
        void cancelaConMotivo() {
            when(repositorio.buscarConCliente(1L)).thenReturn(Optional.of(unaObra()));

            ObraRespuesta respuesta = servicio.cambiarEstado(1L, new CambioEstadoObra(
                    Obra.ESTADO_CANCELADA, "El cliente no aceptó el precio", null, false));

            assertThat(respuesta.estado()).isEqualTo(Obra.ESTADO_CANCELADA);
            assertThat(respuesta.motivoCancelacion()).isEqualTo("El cliente no aceptó el precio");
            // La obra no se borra: queda para entender más adelante por qué no
            // se concretó.
            assertThat(respuesta.direccionObra()).isEqualTo("Av. Cabildo 2340");
        }

        /**
         * La regla del informe: una obra con presupuesto definitivo aprobado no
         * se cancela salvo autorizacion explicita del dueño.
         *
         * La diferencia es de fondo: cancelar una obra que todavia se estaba
         * presupuestando descarta una propuesta; cancelar una con el definitivo
         * aprobado interrumpe una obra en marcha, con material comprado y cuotas
         * emitidas.
         */
        @Test
        @DisplayName("Con definitivo aprobado, cancelar sin confirmar se rechaza")
        void noCancelaObraEnMarchaSinConfirmar() {
            when(repositorio.buscarConCliente(1L)).thenReturn(Optional.of(unaObra()));
            conDefinitivoAprobado();

            assertThatThrownBy(() -> servicio.cambiarEstado(1L, new CambioEstadoObra(
                    Obra.ESTADO_CANCELADA, "El cliente se arrepintió", null, false)))
                    .isInstanceOf(ReglaDeNegocioException.class)
                    .hasMessageContaining("definitivo aprobado");
        }

        @Test
        @DisplayName("Con definitivo aprobado y confirmación explícita, sí se cancela")
        void cancelaObraEnMarchaConConfirmacion() {
            when(repositorio.buscarConCliente(1L)).thenReturn(Optional.of(unaObra()));
            conDefinitivoAprobado();

            ObraRespuesta respuesta = servicio.cambiarEstado(1L, new CambioEstadoObra(
                    Obra.ESTADO_CANCELADA, "Se interrumpió la obra", null, true));

            assertThat(respuesta.estado()).isEqualTo(Obra.ESTADO_CANCELADA);
        }

        @Test
        @DisplayName("Sin definitivo aprobado no hace falta confirmar nada")
        void sinDefinitivoAprobadoNoPideConfirmacion() {
            when(repositorio.buscarConCliente(1L)).thenReturn(Optional.of(unaObra()));
            // El mock devuelve lista vacía por defecto: no hay definitivo aprobado.

            ObraRespuesta respuesta = servicio.cambiarEstado(1L, new CambioEstadoObra(
                    Obra.ESTADO_CANCELADA, "No prosperó", null, false));

            assertThat(respuesta.estado()).isEqualTo(Obra.ESTADO_CANCELADA);
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
                    "Av. Cabildo 2340", Obra.INMUEBLE_DEPARTAMENTO, null,
                    LocalDate.of(2026, 3, 4), null, null, null, null)))
                    .isInstanceOf(ReglaDeNegocioException.class)
                    .hasMessageContaining("presupuesto definitivo");
        }

        @Test
        @DisplayName("Con el definitivo aprobado sí se puede cargar la fecha de inicio")
        void fechaInicioPermitidaEnEjecucion() {
            Obra obra = unaObra();
            obra.pasarAEjecucion();
            when(repositorio.buscarConCliente(1L)).thenReturn(Optional.of(obra));
            conDefinitivoAprobado();

            ObraRespuesta respuesta = servicio.actualizar(1L, new ObraEdicion(
                    "Av. Cabildo 2340", Obra.INMUEBLE_DEPARTAMENTO, null,
                    LocalDate.of(2026, 3, 10), null, null,
                    LocalDate.of(2026, 12, 1), null));

            assertThat(respuesta.fechaInicioReal()).isEqualTo(LocalDate.of(2026, 3, 10));
        }

        @Test
        @DisplayName("La fecha estimada de fin no puede ser anterior a la de inicio")
        void fechasCoherentes() {
            Obra obra = unaObra();
            obra.pasarAEjecucion();
            when(repositorio.buscarConCliente(1L)).thenReturn(Optional.of(obra));
            conDefinitivoAprobado();

            assertThatThrownBy(() -> servicio.actualizar(1L, new ObraEdicion(
                    "Av. Cabildo 2340", Obra.INMUEBLE_DEPARTAMENTO, null,
                    LocalDate.of(2026, 6, 1), null, null,
                    LocalDate.of(2026, 3, 1), null)))
                    .isInstanceOf(ReglaDeNegocioException.class)
                    .hasMessageContaining("anterior");
        }

        /**
         * Regla del informe: el tipo de obra se bloquea "apenas existe un
         * presupuesto de anteproyecto o definitivo", porque determina el
         * circuito de Presupuestación. Antes de la auditoría del 22/09 no se
         * podía cambiar NUNCA, y una obra cargada con el tipo equivocado no
         * tenía arreglo: había que cancelarla y rehacerla.
         */
        @Test
        @DisplayName("Sin presupuestos, el tipo de obra se puede corregir")
        void corrigeTipoSinPresupuestos() {
            Obra obra = unaObra();
            when(repositorio.buscarConCliente(1L)).thenReturn(Optional.of(obra));
            // Sin presupuestos: el mock devuelve lista vacía por defecto.

            ObraRespuesta respuesta = servicio.actualizar(1L, new ObraEdicion(
                    "Av. Cabildo 2340", Obra.INMUEBLE_DEPARTAMENTO, Obra.TIPO_CONSTRUCCION, null, null, null, null, null));

            assertThat(respuesta.tipoObra()).isEqualTo(Obra.TIPO_CONSTRUCCION);
        }

        @Test
        @DisplayName("Con presupuestos ya generados, el tipo queda bloqueado")
        void noCorrigeTipoConPresupuestos() {
            Obra obra = unaObra();
            when(repositorio.buscarConCliente(1L)).thenReturn(Optional.of(obra));
            conAlgunPresupuesto();

            assertThatThrownBy(() -> servicio.actualizar(1L, new ObraEdicion(
                    "Av. Cabildo 2340", Obra.INMUEBLE_DEPARTAMENTO, Obra.TIPO_CONSTRUCCION, null, null, null, null, null)))
                    .isInstanceOf(ReglaDeNegocioException.class)
                    .hasMessageContaining("tipo de obra");
        }

        @Test
        @DisplayName("Mandar el mismo tipo no se considera un cambio")
        void mismoTipoNoEsCambio() {
            Obra obra = unaObra();
            when(repositorio.buscarConCliente(1L)).thenReturn(Optional.of(obra));
            // No se declara ningún presupuesto: si el servicio los consultara
            // igual, el stub estricto de Mockito haría fallar este test.

            ObraRespuesta respuesta = servicio.actualizar(1L, new ObraEdicion(
                    "Av. Cabildo 2340", Obra.INMUEBLE_DEPARTAMENTO, Obra.TIPO_REFORMA, null, null, null, null, null));

            assertThat(respuesta.tipoObra()).isEqualTo(Obra.TIPO_REFORMA);
        }

        @Test
        @DisplayName("La edición no cambia el tipo de obra ni el cliente")
        void noCambiaTipoNiCliente() {
            Obra obra = unaObra();
            when(repositorio.buscarConCliente(1L)).thenReturn(Optional.of(obra));

            ObraRespuesta respuesta = servicio.actualizar(1L, new ObraEdicion(
                    "Otra dirección 100", Obra.INMUEBLE_LOCAL, null, null, null, null, null, "Nota nueva"));

            assertThat(respuesta.direccionObra()).isEqualTo("Otra dirección 100");
            assertThat(respuesta.tipoInmueble()).isEqualTo(Obra.INMUEBLE_LOCAL);
            // Se envió null como tipo de obra, que significa "no lo toques".
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
