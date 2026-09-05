package com.sigco.cobros;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.sigco.clientes.Cliente;
import com.sigco.cobros.dto.CobrosDtos.AnularPago;
import com.sigco.cobros.dto.CobrosDtos.GenerarPlan;
import com.sigco.cobros.dto.CobrosDtos.PlanDeCobro;
import com.sigco.cobros.dto.CobrosDtos.PreviaCac;
import com.sigco.cobros.dto.CobrosDtos.RegistrarPago;
import com.sigco.common.exception.ReglaDeNegocioException;
import com.sigco.obras.Obra;
import com.sigco.obras.ObraRepository;
import com.sigco.presupuestacion.Presupuesto;
import com.sigco.presupuestacion.PresupuestoRepository;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Tests del modulo Cobros.
 *
 * El foco esta en las dos reglas que hacen confiable el estado de cuenta: que
 * el plan cierre exactamente en el total aprobado, y que el ajuste por CAC no
 * toque las cuotas ya cobradas.
 */
@ExtendWith(MockitoExtension.class)
class CobrosServiceTest {

    @Mock private CuotaRepository repositorio;
    @Mock private RegistroCacRepository cacRepositorio;
    @Mock private ObraRepository obraRepositorio;
    @Mock private PresupuestoRepository presupuestoRepositorio;

    @InjectMocks private CobrosService servicio;

    private static void asignarId(Object entidad, String campo, Long valor) {
        try {
            Field f = entidad.getClass().getDeclaredField(campo);
            f.setAccessible(true);
            f.set(entidad, valor);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void asignarTotal(Presupuesto p, BigDecimal total) {
        try {
            Field f = Presupuesto.class.getDeclaredField("totalPresupuesto");
            f.setAccessible(true);
            f.set(p, total);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private Obra obra() {
        Cliente c = new Cliente("Mario Lopez", null, null, null, null);
        asignarId(c, "idCliente", 1L);
        Obra o = new Obra(c, "Ituzaingo 231, Pilar", "Casa", Obra.TIPO_REFORMA, null, null);
        asignarId(o, "idObra", 5L);
        o.pasarAEjecucion();
        lenient().when(obraRepositorio.findById(5L)).thenReturn(Optional.of(o));
        return o;
    }

    /** Definitivo aprobado de $1.000.000 con 30% de anticipo y 4 cuotas. */
    private void conDefinitivoAprobado(Obra obra, String total, String anticipo, int cuotas) {
        Presupuesto p = new Presupuesto(obra, Presupuesto.TIPO_DEFINITIVO, 1, null, null);
        asignarId(p, "idPresupuesto", 20L);
        // El total lo pone la carga de items o la cotizacion; aca se asigna
        // directo porque lo que se prueba es Cobros, no Presupuestacion.
        asignarTotal(p, new BigDecimal(total));
        p.definirPlanDePago(new BigDecimal(anticipo), cuotas, null);
        lenient().when(presupuestoRepositorio.findByObraIdObraAndTipoPresupuestoAndEstado(
                        5L, Presupuesto.TIPO_DEFINITIVO, Presupuesto.ESTADO_APROBADO))
                .thenReturn(List.of(p));
    }

    private void devolverLoQueSeGuarda() {
        lenient().when(repositorio.saveAll(any())).thenAnswer(i -> i.getArgument(0));
    }

    private void conCac(String anterior, String actual) {
        RegistroCac ant = new RegistroCac(LocalDate.of(2026, 8, 1), new BigDecimal(anterior));
        RegistroCac act = new RegistroCac(LocalDate.of(2026, 9, 1), new BigDecimal(actual));
        lenient().when(cacRepositorio.ultimosDos()).thenReturn(List.of(act, ant));
    }

    /** Plan ya generado en memoria, para probar pagos y ajustes. */
    private List<Cuota> planDe(Obra obra, String anticipo, String... cuotas) {
        List<Cuota> plan = new ArrayList<>();
        // Vencimientos FUTUROS a proposito: el estado Vencida se deriva del
        // calendario, asi que con fechas fijas el test cambiaria de resultado
        // segun el dia en que se corra.
        LocalDate primerVencimiento = LocalDate.now().plusDays(10);
        Cuota c0 = new Cuota(obra, 0, new BigDecimal(anticipo), primerVencimiento);
        asignarId(c0, "idCuota", 100L);
        plan.add(c0);
        for (int i = 0; i < cuotas.length; i++) {
            Cuota c = new Cuota(obra, i + 1, new BigDecimal(cuotas[i]),
                    primerVencimiento.plusDays(15L * (i + 1)));
            asignarId(c, "idCuota", 101L + i);
            lenient().when(repositorio.buscarCompleta(101L + i)).thenReturn(Optional.of(c));
            plan.add(c);
        }
        lenient().when(repositorio.buscarCompleta(100L)).thenReturn(Optional.of(c0));
        lenient().when(repositorio.delPlan(5L)).thenReturn(plan);
        return plan;
    }

    // ==================================================================

    @Nested
    @DisplayName("Generación del plan")
    class Generar {

        @Test
        @DisplayName("El anticipo es la cuota cero y el plan cierra en el total aprobado")
        void planCierraEnElTotal() {
            Obra obra = obra();
            conDefinitivoAprobado(obra, "1000000", "30", 4);
            devolverLoQueSeGuarda();

            PlanDeCobro plan = servicio.generar(5L, new GenerarPlan(LocalDate.of(2026, 9, 1)));

            assertThat(plan.cuotas()).hasSize(5);
            assertThat(plan.cuotas().get(0).esAnticipo()).isTrue();
            assertThat(plan.cuotas().get(0).montoCuota()).isEqualByComparingTo("300000.00");
            // 700.000 / 4 = 175.000 cada una
            assertThat(plan.cuotas().get(1).montoCuota()).isEqualByComparingTo("175000.00");
            // La regla del informe: anticipo + cuotas = total del presupuesto.
            assertThat(plan.totalPlan()).isEqualByComparingTo("1000000.00");
        }

        @Test
        @DisplayName("Los centavos del redondeo se ajustan en la última cuota")
        void elRedondeoNoRompeElTotal() {
            Obra obra = obra();
            // 1.000 con 0% de anticipo entre 3: 333,33 x 3 = 999,99. Falta 1 centavo.
            conDefinitivoAprobado(obra, "1000", "0", 3);
            devolverLoQueSeGuarda();

            PlanDeCobro plan = servicio.generar(5L, new GenerarPlan(LocalDate.of(2026, 9, 1)));

            assertThat(plan.cuotas().get(1).montoCuota()).isEqualByComparingTo("333.33");
            assertThat(plan.cuotas().get(3).montoCuota()).isEqualByComparingTo("333.34");
            // Unos centavos de diferencia incumplen la regla igual que muchos.
            assertThat(plan.totalPlan()).isEqualByComparingTo("1000.00");
        }

        @Test
        @DisplayName("Las cuotas son quincenales")
        void cuotasQuincenales() {
            Obra obra = obra();
            conDefinitivoAprobado(obra, "1000000", "30", 2);
            devolverLoQueSeGuarda();

            PlanDeCobro plan = servicio.generar(5L, new GenerarPlan(LocalDate.of(2026, 9, 1)));

            assertThat(plan.cuotas().get(1).fechaVencimiento()).isEqualTo(LocalDate.of(2026, 9, 16));
            assertThat(plan.cuotas().get(2).fechaVencimiento()).isEqualTo(LocalDate.of(2026, 10, 1));
        }

        @Test
        @DisplayName("Sin definitivo aprobado no se genera plan de cobro")
        void sinDefinitivoNoHayPlan() {
            obra();
            when(presupuestoRepositorio.findByObraIdObraAndTipoPresupuestoAndEstado(
                    5L, Presupuesto.TIPO_DEFINITIVO, Presupuesto.ESTADO_APROBADO))
                    .thenReturn(List.of());

            // Cobrarle a un cliente por un precio que no aceptó sería el peor
            // error posible del sistema.
            assertThatThrownBy(() -> servicio.generar(5L, new GenerarPlan(LocalDate.now())))
                    .isInstanceOf(ReglaDeNegocioException.class)
                    .hasMessageContaining("no tiene un presupuesto definitivo aprobado");
        }

        @Test
        @DisplayName("No se genera dos veces el plan de la misma obra")
        void noSeGeneraDosVeces() {
            Obra obra = obra();
            conDefinitivoAprobado(obra, "1000000", "30", 4);
            when(repositorio.existsByObraIdObra(5L)).thenReturn(true);

            assertThatThrownBy(() -> servicio.generar(5L, new GenerarPlan(LocalDate.now())))
                    .isInstanceOf(ReglaDeNegocioException.class)
                    .hasMessageContaining("ya tiene un plan de cobro");
        }
    }

    // ==================================================================

    @Nested
    @DisplayName("Pagos")
    class Pagos {

        @Test
        @DisplayName("Registrar un pago baja el saldo pendiente")
        void pagoBajaElSaldo() {
            Obra obra = obra();
            planDe(obra, "300000", "175000", "175000");

            PlanDeCobro plan = servicio.registrarPago(100L, new RegistrarPago(
                    LocalDate.of(2026, 9, 1), "Transferencia", "Recibo"));

            assertThat(plan.totalCobrado()).isEqualByComparingTo("300000");
            assertThat(plan.saldoPendiente()).isEqualByComparingTo("350000");
            assertThat(plan.cuotasAbonadas()).isEqualTo(1);
        }

        @Test
        @DisplayName("Una cuota abonada no se paga dos veces")
        void noSePagaDosVeces() {
            Obra obra = obra();
            List<Cuota> plan = planDe(obra, "300000", "175000");
            plan.get(0).abonar(LocalDate.now(), "Efectivo", null);

            assertThatThrownBy(() -> servicio.registrarPago(100L, new RegistrarPago(
                    LocalDate.now(), "Efectivo", null)))
                    .isInstanceOf(ReglaDeNegocioException.class)
                    .hasMessageContaining("ya está abonada");
        }

        @Test
        @DisplayName("Anular un pago devuelve la cuota a pendiente con su motivo")
        void anularPago() {
            Obra obra = obra();
            List<Cuota> plan = planDe(obra, "300000", "175000");
            plan.get(0).abonar(LocalDate.now(), "Cheque", null);

            PlanDeCobro r = servicio.anularPago(100L, new AnularPago("El cheque rebotó"));

            // Se anula EL PAGO, no la cuota: sigue debiéndose.
            assertThat(r.cuotas().get(0).estado()).isEqualTo(Cuota.ESTADO_PENDIENTE);
            assertThat(r.cuotas().get(0).motivoAnulacion()).isEqualTo("El cheque rebotó");
            assertThat(r.saldoPendiente()).isEqualByComparingTo("475000");
        }
    }

    // ==================================================================

    @Nested
    @DisplayName("Actualización por índice CAC")
    class Cac {

        @Test
        @DisplayName("El coeficiente sale de comparar el mes con el anterior")
        void coeficienteEntreMeses() {
            Obra obra = obra();
            planDe(obra, "300000", "175000", "175000");
            conCac("1000", "1100");

            PreviaCac previa = servicio.previaCac(5L);

            // 1100 / 1000 = 1,10: los costos subieron 10%.
            assertThat(previa.coeficiente()).isEqualByComparingTo("1.100000");
            assertThat(previa.saldoActual()).isEqualByComparingTo("650000");
            assertThat(previa.saldoActualizado()).isEqualByComparingTo("715000.00");
        }

        @Test
        @DisplayName("El ajuste NO toca las cuotas ya cobradas")
        void noTocaLoYaCobrado() {
            Obra obra = obra();
            List<Cuota> plan = planDe(obra, "300000", "175000", "175000");
            plan.get(0).abonar(LocalDate.now(), "Transferencia", null);
            conCac("1000", "1100");

            PlanDeCobro r = servicio.aplicarCac(5L);

            // El anticipo ya cobrado queda intacto: reajustarlo sería cobrar
            // dos veces por lo mismo.
            assertThat(r.cuotas().get(0).montoCuota()).isEqualByComparingTo("300000");
            // Las pendientes suben 10%.
            assertThat(r.cuotas().get(1).montoCuota()).isEqualByComparingTo("192500.00");
            assertThat(r.cuotas().get(2).montoCuota()).isEqualByComparingTo("192500.00");
        }

        @Test
        @DisplayName("Con un solo mes cargado no se puede calcular el ajuste")
        void hacenFaltaDosMeses() {
            obra();
            when(cacRepositorio.ultimosDos()).thenReturn(List.of(
                    new RegistroCac(LocalDate.of(2026, 9, 1), new BigDecimal("1100"))));

            // El CAC es un número absoluto: por sí solo no dice cuánto subió nada.
            assertThatThrownBy(() -> servicio.previaCac(5L))
                    .isInstanceOf(ReglaDeNegocioException.class)
                    .hasMessageContaining("al menos dos meses");
        }

        @Test
        @DisplayName("Sin cuotas pendientes no hay saldo que actualizar")
        void sinPendientesNoSeActualiza() {
            Obra obra = obra();
            List<Cuota> plan = planDe(obra, "300000", "175000");
            plan.forEach(c -> c.abonar(LocalDate.now(), "Efectivo", null));
            conCac("1000", "1100");

            assertThatThrownBy(() -> servicio.aplicarCac(5L))
                    .isInstanceOf(ReglaDeNegocioException.class)
                    .hasMessageContaining("No quedan cuotas pendientes");
        }
    }

    // ==================================================================

    @Test
    @DisplayName("Una cuota impaga cuyo vencimiento pasó se marca Vencida sola")
    void vencimientoSeDerivaDelCalendario() {
        Obra obra = obra();
        Cuota vieja = new Cuota(obra, 1, new BigDecimal("175000"),
                LocalDate.now().minusDays(10));
        asignarId(vieja, "idCuota", 200L);
        when(repositorio.delPlan(5L)).thenReturn(List.of(vieja));

        PlanDeCobro plan = servicio.plan(5L);

        // El estado sale del calendario: si dependiera de que alguien lo marque,
        // volvería a depender de la memoria del dueño.
        assertThat(plan.cuotas().get(0).estado()).isEqualTo(Cuota.ESTADO_VENCIDA);
        assertThat(plan.cuotasVencidas()).isEqualTo(1);
    }
}
