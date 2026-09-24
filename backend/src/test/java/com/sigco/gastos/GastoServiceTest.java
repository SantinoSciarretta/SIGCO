package com.sigco.gastos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sigco.clientes.Cliente;
import com.sigco.common.exception.ReglaDeNegocioException;
import com.sigco.gastos.dto.EstadoFinanciero;
import com.sigco.gastos.dto.GastoDtos.AnulacionGasto;
import com.sigco.gastos.dto.GastoDtos.GastoSolicitud;
import com.sigco.gastos.dto.GastoRespuesta;
import com.sigco.obras.Obra;
import com.sigco.obras.ObraRepository;
import com.sigco.presupuestacion.ItemPresupuesto;
import com.sigco.presupuestacion.Presupuesto;
import com.sigco.presupuestacion.PresupuestoRepository;
import com.sigco.presupuestacion.Rubro;
import com.sigco.presupuestacion.RubroRepository;
import com.sigco.presupuestacion.Subrubro;
import com.sigco.presupuestacion.SubrubroRepository;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Tests del modulo Gastos.
 *
 * El foco esta en el semaforo y la ganancia estimada: son el motivo por el que
 * el modulo existe. Registrar un gasto es lo facil; compararlo contra lo
 * presupuestado en el mismo rubro es lo que hoy la empresa no puede hacer.
 */
@ExtendWith(MockitoExtension.class)
class GastoServiceTest {

    @Mock private GastoRepository repositorio;
    @Mock private ObraRepository obraRepositorio;
    @Mock private RubroRepository rubroRepositorio;
    @Mock private SubrubroRepository subrubroRepositorio;
    @Mock private PresupuestoRepository presupuestoRepositorio;
    // Desde el modulo 14 los servicios registran quien hizo cada cosa.
    @Mock private com.sigco.seguridad.SesionActual sesion;

    // La auditoria de las acciones sensibles se simula: lo que se verifica aca
    // es la regla de negocio, no que se escriba la traza.
    @Mock private com.sigco.accesos.ServicioAuditoria auditoria;

    @InjectMocks private GastoService servicio;

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

    private Obra obraEnEjecucion() {
        Cliente cliente = new Cliente("Mario Lopez", null, null, null, null);
        asignarId(cliente, "idCliente", 1L);
        Obra o = new Obra(cliente, "Ituzaingo 231, Pilar", "Casa", Obra.TIPO_REFORMA, null, null);
        asignarId(o, "idObra", 5L);
        o.pasarAEjecucion();
        return o;
    }

    /**
     * Definitivo aprobado con dos rubros: Albañilería 1.000.000 y
     * Electricidad 500.000. Es el lado "presupuestado" de la comparacion.
     */
    private Presupuesto definitivoAprobado(Obra obra) {
        Presupuesto p = new Presupuesto(obra, Presupuesto.TIPO_DEFINITIVO, 1, null, null);
        asignarId(p, "idPresupuesto", 20L);
        p.agregarItem(new ItemPresupuesto(p, rubro("Albañilería", 1L), null, null,
                "Contrapisos", "m2", new BigDecimal("100"), new BigDecimal("10000")));
        p.agregarItem(new ItemPresupuesto(p, rubro("Electricidad", 3L), null, null,
                "Instalación", "global", BigDecimal.ONE, new BigDecimal("500000")));
        p.enviar();
        p.aprobar();
        return p;
    }

    private void conPresupuestoAprobado(Obra obra) {
        Presupuesto definitivo = definitivoAprobado(obra);
        lenient().when(presupuestoRepositorio.findByObraIdObraAndTipoPresupuestoAndEstado(
                        5L, Presupuesto.TIPO_DEFINITIVO, Presupuesto.ESTADO_APROBADO))
                .thenReturn(List.of(definitivo));
        lenient().when(presupuestoRepositorio.buscarCompleto(20L))
                .thenReturn(Optional.of(definitivo));
    }

    private GastoSolicitud solicitud(Long idRubro, String monto) {
        return new GastoSolicitud(5L, idRubro, null, Gasto.TIPO_MATERIAL,
                new BigDecimal(monto), LocalDate.of(2026, 9, 3), null, null, null);
    }

    // ==================================================================

    @Nested
    @DisplayName("Alta")
    class Alta {

        @Test
        @DisplayName("Se registra un gasto de una obra en ejecución con definitivo aprobado")
        void registraGasto() {
            Obra obra = obraEnEjecucion();
            when(obraRepositorio.findById(5L)).thenReturn(Optional.of(obra));
            conPresupuestoAprobado(obra);
            when(rubroRepositorio.findById(1L)).thenReturn(Optional.of(rubro("Albañilería", 1L)));
            when(repositorio.save(any(Gasto.class))).thenAnswer(i -> i.getArgument(0));

            GastoRespuesta r = servicio.crear(solicitud(1L, "250000"));

            assertThat(r.estado()).isEqualTo(Gasto.ESTADO_CONFIRMADO);
            assertThat(r.monto()).isEqualByComparingTo("250000");
            // El informe pide separar cuando ocurrio de cuando se cargo.
            assertThat(r.fechaGasto()).isEqualTo(LocalDate.of(2026, 9, 3));
            assertThat(r.fechaCarga()).isNotNull();
        }

        @Test
        @DisplayName("Una obra en presupuestación no admite gastos")
        void obraEnPresupuestacionNoAdmiteGastos() {
            Cliente c = new Cliente("Mario Lopez", null, null, null, null);
            Obra enPresupuestacion = new Obra(c, "Azcuenaga 243", "Departamento",
                    Obra.TIPO_REFORMA, null, null);
            asignarId(enPresupuestacion, "idObra", 5L);
            when(obraRepositorio.findById(5L)).thenReturn(Optional.of(enPresupuestacion));

            assertThatThrownBy(() -> servicio.crear(solicitud(1L, "1000")))
                    .isInstanceOf(ReglaDeNegocioException.class)
                    .hasMessageContaining("solo se cargan gastos de obras en ejecución");
        }

        @Test
        @DisplayName("Sin presupuesto definitivo aprobado no hay contra qué comparar")
        void sinDefinitivoNoSeCarganGastos() {
            Obra obra = obraEnEjecucion();
            when(obraRepositorio.findById(5L)).thenReturn(Optional.of(obra));
            when(presupuestoRepositorio.findByObraIdObraAndTipoPresupuestoAndEstado(
                    5L, Presupuesto.TIPO_DEFINITIVO, Presupuesto.ESTADO_APROBADO))
                    .thenReturn(List.of());

            assertThatThrownBy(() -> servicio.crear(solicitud(1L, "1000")))
                    .isInstanceOf(ReglaDeNegocioException.class)
                    .hasMessageContaining("no tiene un presupuesto definitivo aprobado");
        }

        @Test
        @DisplayName("El subrubro tiene que pertenecer al rubro del gasto")
        void subrubroDebeCorresponderAlRubro() {
            Obra obra = obraEnEjecucion();
            when(obraRepositorio.findById(5L)).thenReturn(Optional.of(obra));
            conPresupuestoAprobado(obra);
            Rubro albanileria = rubro("Albañilería", 1L);
            when(rubroRepositorio.findById(1L)).thenReturn(Optional.of(albanileria));
            Subrubro deOtroRubro = new Subrubro(rubro("Plomería", 2L), "Desagües");
            asignarId(deOtroRubro, "idSubrubro", 9L);
            when(subrubroRepositorio.findById(9L)).thenReturn(Optional.of(deOtroRubro));

            assertThatThrownBy(() -> servicio.crear(new GastoSolicitud(5L, 1L, 9L,
                    Gasto.TIPO_MATERIAL, BigDecimal.TEN, LocalDate.now(), null, null, null)))
                    .isInstanceOf(ReglaDeNegocioException.class)
                    .hasMessageContaining("pertenece al rubro");
        }
    }

    // ==================================================================

    @Nested
    @DisplayName("Semáforo y estado financiero")
    class Semaforo {

        private EstadoFinanciero conGastoEnAlbanileria(String gastado) {
            Obra obra = obraEnEjecucion();
            when(obraRepositorio.findById(5L)).thenReturn(Optional.of(obra));
            conPresupuestoAprobado(obra);
            when(repositorio.totalPorRubro(5L)).thenReturn(List.<Object[]>of(
                    new Object[]{1L, "Albañilería", new BigDecimal(gastado)}));
            when(repositorio.totalGastado(5L)).thenReturn(new BigDecimal(gastado));
            when(repositorio.totalHormiga(5L)).thenReturn(BigDecimal.ZERO);
            return servicio.estadoFinanciero(5L);
        }

        private EstadoFinanciero.RubroFinanciero albanileria(EstadoFinanciero e) {
            return e.rubros().stream()
                    .filter(r -> r.idRubro().equals(1L)).findFirst().orElseThrow();
        }

        @Test
        @DisplayName("Verde mientras no se supera el 90% de lo presupuestado")
        void verdeHastaEl90() {
            // 800.000 sobre 1.000.000 = 80%
            var r = albanileria(conGastoEnAlbanileria("800000"));
            assertThat(r.porcentaje()).isEqualByComparingTo("80.00");
            assertThat(r.semaforo()).isEqualTo(GastoService.SEMAFORO_VERDE);
            assertThat(r.diferencia()).isEqualByComparingTo("200000");
        }

        @Test
        @DisplayName("Amarillo entre el 90% y el 100%")
        void amarilloEntre90y100() {
            var r = albanileria(conGastoEnAlbanileria("950000"));
            assertThat(r.porcentaje()).isEqualByComparingTo("95.00");
            assertThat(r.semaforo()).isEqualTo(GastoService.SEMAFORO_AMARILLO);
        }

        @Test
        @DisplayName("El 90% exacto ya es amarillo, no verde")
        void noventaExactoEsAmarillo() {
            var r = albanileria(conGastoEnAlbanileria("900000"));
            assertThat(r.semaforo()).isEqualTo(GastoService.SEMAFORO_AMARILLO);
        }

        @Test
        @DisplayName("El 100% exacto todavía es amarillo: rojo es al SUPERAR")
        void cienExactoEsAmarillo() {
            // El informe dice "rojo al superar el monto presupuestado", no al
            // alcanzarlo. Gastar exactamente lo presupuestado no es un desvio.
            var r = albanileria(conGastoEnAlbanileria("1000000"));
            assertThat(r.porcentaje()).isEqualByComparingTo("100.00");
            assertThat(r.semaforo()).isEqualTo(GastoService.SEMAFORO_AMARILLO);
        }

        @Test
        @DisplayName("Rojo al superar lo presupuestado, con diferencia negativa")
        void rojoAlSuperar() {
            var r = albanileria(conGastoEnAlbanileria("1100000"));
            assertThat(r.semaforo()).isEqualTo(GastoService.SEMAFORO_ROJO);
            assertThat(r.diferencia()).isEqualByComparingTo("-100000");
        }

        @Test
        @DisplayName("La ganancia estimada es el presupuesto menos lo gastado")
        void gananciaEstimada() {
            EstadoFinanciero e = conGastoEnAlbanileria("800000");
            // Total presupuestado: 1.000.000 + 500.000 = 1.500.000
            assertThat(e.totalPresupuestado()).isEqualByComparingTo("1500000.00");
            assertThat(e.totalGastado()).isEqualByComparingTo("800000");
            assertThat(e.gananciaEstimada()).isEqualByComparingTo("700000.00");
        }

        @Test
        @DisplayName("Un rubro presupuestado sin gasto aparece con gasto cero")
        void rubroSinGastoAparece() {
            EstadoFinanciero e = conGastoEnAlbanileria("800000");
            var electricidad = e.rubros().stream()
                    .filter(r -> r.idRubro().equals(3L)).findFirst().orElseThrow();
            assertThat(electricidad.gastado()).isEqualByComparingTo("0");
            assertThat(electricidad.semaforo()).isEqualTo(GastoService.SEMAFORO_VERDE);
        }

        @Test
        @DisplayName("Un rubro con gasto que nadie presupuestó se marca aparte")
        void gastoEnRubroNoPresupuestado() {
            Obra obra = obraEnEjecucion();
            when(obraRepositorio.findById(5L)).thenReturn(Optional.of(obra));
            conPresupuestoAprobado(obra);
            // Rubro 9 no esta en el presupuesto: se esta gastando en algo que
            // nadie previo, que es mas grave que un desvio.
            when(repositorio.totalPorRubro(5L)).thenReturn(List.<Object[]>of(
                    new Object[]{9L, "Pintura", new BigDecimal("50000")}));
            when(repositorio.totalGastado(5L)).thenReturn(new BigDecimal("50000"));
            when(repositorio.totalHormiga(5L)).thenReturn(BigDecimal.ZERO);

            EstadoFinanciero e = servicio.estadoFinanciero(5L);
            var pintura = e.rubros().stream()
                    .filter(r -> r.idRubro().equals(9L)).findFirst().orElseThrow();

            assertThat(pintura.presupuestado()).isEqualByComparingTo("0");
            assertThat(pintura.semaforo()).isEqualTo(GastoService.SEMAFORO_SIN_PRESUPUESTO);
        }

        /**
         * El balance de cierre se consulta de cualquier obra, incluso de una
         * que se ejecutó sin definitivo aprobado. Ahí la ausencia de
         * presupuesto no es un error: es un cero, con los gastos a la vista.
         *
         * Lo encontró la verificación contra el backend real: seis de las siete
         * obras cargadas devolvían 409 al pedir su balance.
         */
        @Test
        @DisplayName("Sin presupuesto aprobado, el estado financiero vacío no falla")
        void sinPresupuestoNoFalla() {
            Obra obra = obraEnEjecucion();
            when(obraRepositorio.findById(5L)).thenReturn(Optional.of(obra));
            when(presupuestoRepositorio.findByObraIdObraAndTipoPresupuestoAndEstado(
                    5L, Presupuesto.TIPO_DEFINITIVO, Presupuesto.ESTADO_APROBADO))
                    .thenReturn(List.of());
            when(repositorio.totalPorRubro(5L)).thenReturn(List.<Object[]>of(
                    new Object[]{1L, "Albañilería", new BigDecimal("150000")}));
            when(repositorio.totalGastado(5L)).thenReturn(new BigDecimal("150000"));
            when(repositorio.totalHormiga(5L)).thenReturn(BigDecimal.ZERO);

            EstadoFinanciero e = servicio.estadoFinancieroOVacio(5L);

            assertThat(e.idPresupuesto()).isNull();
            assertThat(e.totalPresupuestado()).isEqualByComparingTo("0");
            // Los gastos siguen ahí: es justamente lo que hay que ver.
            assertThat(e.totalGastado()).isEqualByComparingTo("150000");
            assertThat(e.rubros()).hasSize(1);
        }

        @Test
        @DisplayName("Con presupuesto aprobado, la versión que no falla da lo mismo")
        void conPresupuestoDaLoMismo() {
            Obra obra = obraEnEjecucion();
            when(obraRepositorio.findById(5L)).thenReturn(Optional.of(obra));
            conPresupuestoAprobado(obra);
            when(repositorio.totalPorRubro(5L)).thenReturn(List.<Object[]>of(
                    new Object[]{1L, "Albañilería", new BigDecimal("800000")}));
            when(repositorio.totalGastado(5L)).thenReturn(new BigDecimal("800000"));
            when(repositorio.totalHormiga(5L)).thenReturn(BigDecimal.ZERO);

            EstadoFinanciero e = servicio.estadoFinancieroOVacio(5L);

            assertThat(e.idPresupuesto()).isNotNull();
            assertThat(e.totalGastado()).isEqualByComparingTo("800000");
        }
    }

    // ==================================================================

    @Nested
    @DisplayName("Integración con Compras")
    class DesdeCompras {

        @Test
        @DisplayName("Una recepción genera un gasto por cada rubro del pedido")
        void unGastoPorRubro() {
            Obra obra = obraEnEjecucion();
            conPresupuestoAprobado(obra);
            when(repositorio.countByIdPedido(30L)).thenReturn(0L);
            when(rubroRepositorio.findById(1L)).thenReturn(Optional.of(rubro("Albañilería", 1L)));
            when(rubroRepositorio.findById(3L)).thenReturn(Optional.of(rubro("Electricidad", 3L)));

            int generados = servicio.generarDesdeRecepcion(obra, 30L, Map.of(
                    1L, new BigDecimal("520000"),
                    3L, new BigDecimal("180000")), LocalDate.now());

            // Dos rubros en el pedido, dos gastos: un gasto unico habria que
            // imputarlo a un solo rubro y rompe la comparacion.
            assertThat(generados).isEqualTo(2);
            verify(repositorio, org.mockito.Mockito.times(2)).save(any(Gasto.class));
        }

        @Test
        @DisplayName("No se duplican los gastos si la recepción se reprocesa")
        void noDuplicaGastos() {
            Obra obra = obraEnEjecucion();
            when(repositorio.countByIdPedido(30L)).thenReturn(2L);

            int generados = servicio.generarDesdeRecepcion(obra, 30L,
                    Map.of(1L, new BigDecimal("520000")), LocalDate.now());

            assertThat(generados).isZero();
            verify(repositorio, never()).save(any(Gasto.class));
        }

        @Test
        @DisplayName("Si la obra no admite gastos no se genera nada, pero la recepción no falla")
        void obraSinPresupuestoNoGeneraGasto() {
            Obra obra = obraEnEjecucion();
            when(repositorio.countByIdPedido(30L)).thenReturn(0L);
            when(presupuestoRepositorio.findByObraIdObraAndTipoPresupuestoAndEstado(
                    5L, Presupuesto.TIPO_DEFINITIVO, Presupuesto.ESTADO_APROBADO))
                    .thenReturn(List.of());

            // Que el material haya llegado a la obra es un hecho fisico: no
            // puede depender del estado de la presupuestacion.
            int generados = servicio.generarDesdeRecepcion(obra, 30L,
                    Map.of(1L, new BigDecimal("520000")), LocalDate.now());

            assertThat(generados).isZero();
            verify(repositorio, never()).save(any(Gasto.class));
        }
    }

    // ==================================================================

    @Test
    @DisplayName("Anular deja el motivo y el gasto deja de sumar")
    void anular() {
        Obra obra = obraEnEjecucion();
        Gasto g = new Gasto(obra, rubro("Albañilería", 1L), null, Gasto.TIPO_MATERIAL,
                new BigDecimal("50000"), LocalDate.now(), null, null, null, null);
        asignarId(g, "idGasto", 40L);
        when(repositorio.buscarCompleto(40L)).thenReturn(Optional.of(g));

        GastoRespuesta r = servicio.anular(40L, new AnulacionGasto("Se cargó por duplicado"));

        assertThat(r.estado()).isEqualTo(Gasto.ESTADO_ANULADO);
        assertThat(r.motivoAnulacion()).isEqualTo("Se cargó por duplicado");
    }

    @Test
    @DisplayName("Un gasto anulado no se puede editar")
    void anuladoNoSeEdita() {
        Obra obra = obraEnEjecucion();
        Gasto g = new Gasto(obra, rubro("Albañilería", 1L), null, Gasto.TIPO_MATERIAL,
                new BigDecimal("50000"), LocalDate.now(), null, null, null, null);
        g.anular("Duplicado");
        when(repositorio.buscarCompleto(40L)).thenReturn(Optional.of(g));

        assertThatThrownBy(() -> servicio.actualizar(40L, solicitud(1L, "999")))
                .isInstanceOf(ReglaDeNegocioException.class)
                .hasMessageContaining("no se edita");
    }
}
