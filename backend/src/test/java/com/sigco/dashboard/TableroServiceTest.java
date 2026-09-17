package com.sigco.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.sigco.clientes.Cliente;
import com.sigco.cobros.CobrosService;
import com.sigco.cobros.dto.CobrosDtos.ResumenCobro;
import com.sigco.compras.Pedido;
import com.sigco.compras.PedidoRepository;
import com.sigco.dashboard.dto.TableroDtos.ObraEnTablero;
import com.sigco.dashboard.dto.TableroDtos.Pendiente;
import com.sigco.dashboard.dto.TableroDtos.Tablero;
import com.sigco.gastos.GastoService;
import com.sigco.gastos.GastoService.ResumenFinanciero;
import com.sigco.obras.Obra;
import com.sigco.obras.ObraRepository;
import com.sigco.presupuestacion.Presupuesto;
import com.sigco.presupuestacion.PresupuestoRepository;
import com.sigco.seguimiento.SeguimientoService;
import com.sigco.seguimiento.dto.SeguimientoDtos.AvanceObra;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Tests del Tablero.
 *
 * El Tablero no tiene reglas de negocio propias —esa es justamente su decision
 * de diseño—, asi que no tiene sentido testear calculos: no calcula nada.
 *
 * Lo que si hay que verificar, y es lo unico que puede romperse aca, es que
 * consolide bien lo que le dan los otros modulos:
 *
 *   - que sume los totales de todas las obras
 *   - que NO se caiga cuando una obra todavia no tiene presupuesto aprobado
 *   - que ordene primero lo que necesita atencion
 *   - que arme los pendientes con la urgencia correcta
 */
@ExtendWith(MockitoExtension.class)
class TableroServiceTest {

    @Mock private ObraRepository obraRepositorio;
    @Mock private PresupuestoRepository presupuestoRepositorio;
    @Mock private PedidoRepository pedidoRepositorio;
    @Mock private GastoService gastoService;
    @Mock private SeguimientoService seguimientoService;
    @Mock private CobrosService cobrosService;

    /**
     * Quien pide el tablero. Estos tests miden el tablero COMPLETO, así que la
     * sesión simula al dueño; la versión reducida del Capataz General se prueba
     * aparte, más abajo.
     */
    @Mock private com.sigco.seguridad.SesionActual sesion;

    @InjectMocks private TableroService servicio;

    // Por defecto, el dueño: es quien entra al tablero y lo que miden casi
    // todos estos tests. Los del tablero reducido llaman a comoCapatazGeneral().
    @org.junit.jupiter.api.BeforeEach
    void porDefectoElDueno() {
        comoDueno();
    }

    /** Le da a la sesión los permisos financieros del dueño. */
    private void comoDueno() {
        lenient().when(sesion.puede("cobros.ver")).thenReturn(true);
        lenient().when(sesion.puede("presupuestos.ver")).thenReturn(true);
    }

    /** El Capataz General: sin Presupuestación ni Cobros. */
    private void comoCapatazGeneral() {
        lenient().when(sesion.puede("cobros.ver")).thenReturn(false);
        lenient().when(sesion.puede("presupuestos.ver")).thenReturn(false);
    }

    // ------------------------------------------------------------------
    //  Ayudantes
    // ------------------------------------------------------------------

    private static void asignarId(Object entidad, String campo, Long valor) {
        try {
            Field f = entidad.getClass().getDeclaredField(campo);
            f.setAccessible(true);
            f.set(entidad, valor);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private Obra obra(Long id, String direccion) {
        Cliente c = new Cliente("Marcela Ferrari", "11-4444-5555", null, null, null);
        asignarId(c, "idCliente", id);
        Obra o = new Obra(c, direccion, "Departamento", Obra.TIPO_REFORMA, null, null);
        asignarId(o, "idObra", id);
        o.pasarAEjecucion();
        return o;
    }

    private void conFinanzas(Long idObra, String presupuestado, String gastado, String semaforo) {
        BigDecimal p = new BigDecimal(presupuestado);
        BigDecimal g = new BigDecimal(gastado);
        when(gastoService.resumenOVacio(idObra)).thenReturn(
                new ResumenFinanciero(p.signum() > 0, p, g, p.subtract(g),
                        BigDecimal.ZERO, semaforo));
    }

    private void conAvance(Long idObra, String fisico, String financiero, boolean alerta) {
        when(seguimientoService.avance(idObra)).thenReturn(new AvanceObra(
                idObra, "dir", Obra.ESTADO_EN_EJECUCION,
                new BigDecimal(fisico), new BigDecimal(financiero),
                new BigDecimal(financiero).subtract(new BigDecimal(fisico)),
                alerta, null, null, false, 2, 5, List.of()));
    }

    private void sinPedidos() {
        lenient().when(pedidoRepositorio.findByEstadoOrderByFechaSolicitudAsc(Pedido.ESTADO_PENDIENTE))
                .thenReturn(List.of());
        lenient().when(pedidoRepositorio.findByEstadoOrderByFechaSolicitudAsc(Pedido.ESTADO_ENVIADO))
                .thenReturn(List.of());
    }

    private void sinPresupuestosEnviados() {
        lenient().when(presupuestoRepositorio.buscar(0L, "", Presupuesto.ESTADO_ENVIADO))
                .thenReturn(List.of());
    }

    // ------------------------------------------------------------------
    //  Consolidación
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Suma los totales de todas las obras en ejecución")
    void sumaLosTotales() {
        Obra a = obra(1L, "Av. Cabildo 2340");
        Obra b = obra(2L, "Bulnes 1120");
        when(obraRepositorio.porEstado(Obra.ESTADO_EN_EJECUCION)).thenReturn(List.of(a, b));

        conFinanzas(1L, "10000000", "4000000", GastoService.SEMAFORO_VERDE);
        conFinanzas(2L, "20000000", "5000000", GastoService.SEMAFORO_VERDE);
        conAvance(1L, "40", "40", false);
        conAvance(2L, "25", "25", false);

        when(cobrosService.consolidado()).thenReturn(List.of(
                new ResumenCobro(1L, "Av. Cabildo 2340", "Marcela Ferrari",
                        Obra.ESTADO_EN_EJECUCION, new BigDecimal("10000000"),
                        new BigDecimal("3000000"), new BigDecimal("7000000"), 0, null)));
        sinPedidos();
        sinPresupuestosEnviados();

        Tablero tablero = servicio.armar();

        assertThat(tablero.resumen().obrasEnEjecucion()).isEqualTo(2);
        assertThat(tablero.resumen().totalPresupuestado()).isEqualByComparingTo("30000000");
        assertThat(tablero.resumen().totalGastado()).isEqualByComparingTo("9000000");
        assertThat(tablero.resumen().gananciaEstimada()).isEqualByComparingTo("21000000");
        assertThat(tablero.resumen().saldoPorCobrar()).isEqualByComparingTo("7000000");
    }

    /**
     * El caso que motivo resumenOVacio(): una obra recien pasada a ejecucion
     * puede no tener definitivo aprobado. Con estadoFinanciero() esa sola obra
     * hacia fallar el tablero entero.
     */
    @Test
    @DisplayName("Una obra sin presupuesto aprobado no rompe el tablero")
    void obraSinPresupuestoNoRompe() {
        Obra a = obra(1L, "Av. Cabildo 2340");
        Obra b = obra(2L, "Bulnes 1120");
        when(obraRepositorio.porEstado(Obra.ESTADO_EN_EJECUCION)).thenReturn(List.of(a, b));

        conFinanzas(1L, "10000000", "4000000", GastoService.SEMAFORO_VERDE);
        conFinanzas(2L, "0", "0", GastoService.SEMAFORO_SIN_PRESUPUESTO);
        conAvance(1L, "40", "40", false);
        conAvance(2L, "0", "0", false);

        when(cobrosService.consolidado()).thenReturn(List.of());
        sinPedidos();
        sinPresupuestosEnviados();

        Tablero tablero = servicio.armar();

        assertThat(tablero.obras()).hasSize(2);
        assertThat(tablero.resumen().totalPresupuestado()).isEqualByComparingTo("10000000");
        assertThat(tablero.obras())
                .filteredOn(o -> o.idObra().equals(2L))
                .first()
                .extracting(ObraEnTablero::semaforo)
                .isEqualTo(GastoService.SEMAFORO_SIN_PRESUPUESTO);
    }

    @Test
    @DisplayName("Las obras excedidas van primero, aunque se hayan creado después")
    void ordenaPorAtencion() {
        Obra sana = obra(1L, "Obra sana");
        Obra excedida = obra(2L, "Obra excedida");
        when(obraRepositorio.porEstado(Obra.ESTADO_EN_EJECUCION))
                .thenReturn(List.of(sana, excedida));

        conFinanzas(1L, "10000000", "3000000", GastoService.SEMAFORO_VERDE);
        conFinanzas(2L, "10000000", "12000000", GastoService.SEMAFORO_ROJO);
        conAvance(1L, "30", "30", false);
        conAvance(2L, "50", "120", true);

        when(cobrosService.consolidado()).thenReturn(List.of());
        sinPedidos();
        sinPresupuestosEnviados();

        assertThat(servicio.armar().obras())
                .extracting(ObraEnTablero::direccionObra)
                .containsExactly("Obra excedida", "Obra sana");
    }

    // ------------------------------------------------------------------
    //  Pendientes
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Una cuota vencida es urgente y se ordena antes que una por vencer")
    void cuotaVencidaEsUrgente() {
        when(obraRepositorio.porEstado(Obra.ESTADO_EN_EJECUCION)).thenReturn(List.of());

        when(cobrosService.consolidado()).thenReturn(List.of(
                new ResumenCobro(1L, "Obra con deuda", "Cliente A", Obra.ESTADO_EN_EJECUCION,
                        new BigDecimal("10000000"), BigDecimal.ZERO,
                        new BigDecimal("10000000"), 2, LocalDate.now().minusDays(5)),
                new ResumenCobro(2L, "Obra al día", "Cliente B", Obra.ESTADO_EN_EJECUCION,
                        new BigDecimal("8000000"), new BigDecimal("4000000"),
                        new BigDecimal("4000000"), 0, LocalDate.now().plusDays(3))));
        sinPedidos();
        sinPresupuestosEnviados();

        List<Pendiente> pendientes = servicio.armar().pendientes();

        assertThat(pendientes).hasSize(2);
        assertThat(pendientes.get(0).urgencia()).isEqualTo("alta");
        assertThat(pendientes.get(0).titulo()).contains("2 cuotas vencidas");
        assertThat(pendientes.get(1).urgencia()).isEqualTo("media");
    }

    /**
     * Una cuota que vence dentro de mucho tiempo no es un pendiente: si todo
     * apareciera en la lista, la lista dejaria de mirarse.
     */
    @Test
    @DisplayName("Una cuota que vence dentro de un mes no aparece como pendiente")
    void cuotaLejanaNoEsPendiente() {
        when(obraRepositorio.porEstado(Obra.ESTADO_EN_EJECUCION)).thenReturn(List.of());
        when(cobrosService.consolidado()).thenReturn(List.of(
                new ResumenCobro(1L, "Obra al día", "Cliente B", Obra.ESTADO_EN_EJECUCION,
                        new BigDecimal("8000000"), new BigDecimal("4000000"),
                        new BigDecimal("4000000"), 0, LocalDate.now().plusDays(30))));
        sinPedidos();
        sinPresupuestosEnviados();

        assertThat(servicio.armar().pendientes()).isEmpty();
    }

    @Test
    @DisplayName("Cuenta como urgentes solo los pendientes de urgencia alta")
    void cuentaUrgentes() {
        when(obraRepositorio.porEstado(Obra.ESTADO_EN_EJECUCION)).thenReturn(List.of());
        when(cobrosService.consolidado()).thenReturn(List.of(
                new ResumenCobro(1L, "A", "Cliente A", Obra.ESTADO_EN_EJECUCION,
                        BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.TEN, 1, null),
                new ResumenCobro(2L, "B", "Cliente B", Obra.ESTADO_EN_EJECUCION,
                        BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.TEN, 0,
                        LocalDate.now().plusDays(2))));
        sinPedidos();
        sinPresupuestosEnviados();

        assertThat(servicio.armar().resumen().pendientesUrgentes()).isEqualTo(1);
    }

    /**
     * El plan de cobro no se genera solo al aprobar el definitivo, porque
     * faltaría la fecha del primer vencimiento —se pacta con el cliente y no
     * está en ningún presupuesto— y el sistema tendría que inventar un
     * compromiso de pago. En su lugar, el tablero lo reclama.
     */
    @Test
    @DisplayName("Una obra en ejecución sin plan de cobro aparece como pendiente urgente")
    void obraSinPlanDeCobroEsPendiente() {
        Obra a = obra(1L, "Av. Cabildo 2340");
        when(obraRepositorio.porEstado(Obra.ESTADO_EN_EJECUCION)).thenReturn(List.of(a));
        conFinanzas(1L, "10000000", "4000000", GastoService.SEMAFORO_VERDE);
        conAvance(1L, "40", "40", false);
        // Sin plan: la obra no figura en el consolidado de cobros.
        when(cobrosService.consolidado()).thenReturn(List.of());
        sinPedidos();
        sinPresupuestosEnviados();

        Tablero tablero = servicio.armar();

        assertThat(tablero.pendientes())
                .anyMatch(p -> p.titulo().contains("Sin plan de cobro")
                        && "alta".equals(p.urgencia()));
    }

    @Test
    @DisplayName("Con el plan ya generado, no reclama nada")
    void obraConPlanNoEsPendiente() {
        Obra a = obra(1L, "Av. Cabildo 2340");
        when(obraRepositorio.porEstado(Obra.ESTADO_EN_EJECUCION)).thenReturn(List.of(a));
        conFinanzas(1L, "10000000", "4000000", GastoService.SEMAFORO_VERDE);
        conAvance(1L, "40", "40", false);
        when(cobrosService.consolidado()).thenReturn(List.of(
                new ResumenCobro(1L, "Av. Cabildo 2340", "Marcela Ferrari",
                        Obra.ESTADO_EN_EJECUCION, new BigDecimal("10000000"),
                        new BigDecimal("3000000"), new BigDecimal("7000000"), 0, null)));
        sinPedidos();
        sinPresupuestosEnviados();

        assertThat(servicio.armar().pendientes())
                .noneMatch(p -> p.titulo().contains("Sin plan de cobro"));
    }

    // ------------------------------------------------------------------
    //  El tablero reducido del Capataz General
    // ------------------------------------------------------------------

    /**
     * La matriz del informe le da al Capataz General acceso de CONSULTA al
     * tablero, y ningún acceso a Presupuestación ni a Cobros. Estos tests
     * verifican esa frontera: lo que ve, y sobre todo lo que no.
     */
    @org.junit.jupiter.api.Nested
    @DisplayName("Tablero reducido del Capataz General")
    class TableroReducido {

        @Test
        @DisplayName("No ve lo presupuestado, la ganancia ni el saldo por cobrar")
        void sinInformacionFinanciera() {
            comoCapatazGeneral();

            Obra a = obra(1L, "Av. Cabildo 2340");
            when(obraRepositorio.porEstado(Obra.ESTADO_EN_EJECUCION)).thenReturn(List.of(a));
            conFinanzas(1L, "10000000", "4000000", GastoService.SEMAFORO_VERDE);
            conAvance(1L, "40", "40", false);
            when(cobrosService.consolidado()).thenReturn(List.of(
                    new ResumenCobro(1L, "Av. Cabildo 2340", "Marcela Ferrari",
                            Obra.ESTADO_EN_EJECUCION, new BigDecimal("10000000"),
                            new BigDecimal("3000000"), new BigDecimal("7000000"), 0, null)));
            sinPedidos();
            sinPresupuestosEnviados();

            Tablero tablero = servicio.armar();

            // Van en null y no en cero: cero afirmaría que no hay nada por
            // cobrar, y sería mentira.
            assertThat(tablero.resumen().totalPresupuestado()).isNull();
            assertThat(tablero.resumen().gananciaEstimada()).isNull();
            assertThat(tablero.resumen().saldoPorCobrar()).isNull();
            assertThat(tablero.resumen().porCobrarEstaSemana()).isNull();

            ObraEnTablero fila = tablero.obras().get(0);
            assertThat(fila.totalPresupuestado()).isNull();
            assertThat(fila.gananciaEstimada()).isNull();
            assertThat(fila.saldoPendiente()).isNull();
            assertThat(fila.proximoVencimiento()).isNull();
        }

        @Test
        @DisplayName("Sí ve lo gastado, el semáforo y el avance: son su trabajo")
        void conservaLoOperativo() {
            comoCapatazGeneral();

            Obra a = obra(1L, "Av. Cabildo 2340");
            when(obraRepositorio.porEstado(Obra.ESTADO_EN_EJECUCION)).thenReturn(List.of(a));
            conFinanzas(1L, "10000000", "4000000", GastoService.SEMAFORO_ROJO);
            conAvance(1L, "40", "60", true);
            when(cobrosService.consolidado()).thenReturn(List.of());
            sinPedidos();
            sinPresupuestosEnviados();

            ObraEnTablero fila = servicio.armar().obras().get(0);

            assertThat(fila.direccionObra()).isEqualTo("Av. Cabildo 2340");
            assertThat(fila.totalGastado()).isEqualByComparingTo("4000000");
            assertThat(fila.semaforo()).isEqualTo(GastoService.SEMAFORO_ROJO);
            assertThat(fila.avanceFisico()).isEqualByComparingTo("40");
            assertThat(fila.alertaDesfasaje()).isTrue();
        }

        @Test
        @DisplayName("De los pendientes solo le quedan los pedidos")
        void soloPendientesDeCompras() {
            comoCapatazGeneral();

            when(obraRepositorio.porEstado(Obra.ESTADO_EN_EJECUCION)).thenReturn(List.of());
            // Una cuota vencida y un presupuesto sin respuesta: los dos son de
            // módulos a los que este rol no accede.
            when(cobrosService.consolidado()).thenReturn(List.of(
                    new ResumenCobro(1L, "A", "Cliente A", Obra.ESTADO_EN_EJECUCION,
                            BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.TEN, 1, null)));
            sinPedidos();
            sinPresupuestosEnviados();

            Tablero tablero = servicio.armar();

            assertThat(tablero.pendientes()).isEmpty();
            // Y el contador se recalcula sobre lo que queda: si no, diría
            // "1 urgente" sin ninguna tarjeta en pantalla.
            assertThat(tablero.resumen().pendientesUrgentes()).isZero();
        }

        @Test
        @DisplayName("El dueño sigue viendo todo")
        void elDuenoVeTodo() {
            comoDueno();

            when(obraRepositorio.porEstado(Obra.ESTADO_EN_EJECUCION)).thenReturn(List.of());
            when(cobrosService.consolidado()).thenReturn(List.of(
                    new ResumenCobro(1L, "A", "Cliente A", Obra.ESTADO_EN_EJECUCION,
                            BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.TEN, 1, null)));
            sinPedidos();
            sinPresupuestosEnviados();

            Tablero tablero = servicio.armar();

            assertThat(tablero.resumen().saldoPorCobrar()).isNotNull();
            assertThat(tablero.pendientes()).isNotEmpty();
        }
    }
}
