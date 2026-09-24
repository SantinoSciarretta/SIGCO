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

    // La auditoria de las acciones sensibles se simula: lo que se verifica aca
    // es la regla de negocio, no que se escriba la traza.
    @Mock private com.sigco.accesos.ServicioAuditoria auditoria;

    // Quien registra el pago. Se simula porque estos tests miden los montos y
    // los estados, no de quien queda el registro.
    @Mock private com.sigco.seguridad.SesionActual sesion;

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

    /**
     * Carga el coeficiente del mes.
     *
     * Desde V21 es un solo numero y es por cuanto se multiplican las cuotas:
     * conCac("1.1") sube un 10%. Antes se cargaban dos niveles de indice y el
     * coeficiente salia de dividirlos.
     */
    private void conCac(String coeficiente) {
        RegistroCac registro = new RegistroCac(
                LocalDate.of(2026, 9, 1), new BigDecimal(coeficiente));
        lenient().when(cacRepositorio.ultimo()).thenReturn(Optional.of(registro));
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
                    new BigDecimal("300000"), LocalDate.of(2026, 9, 1),
                    "Transferencia", "Recibo"));

            assertThat(plan.totalCobrado()).isEqualByComparingTo("300000");
            assertThat(plan.saldoPendiente()).isEqualByComparingTo("350000");
            assertThat(plan.cuotasAbonadas()).isEqualTo(1);
        }

        @Test
        @DisplayName("Una cuota abonada no se paga dos veces")
        void noSePagaDosVeces() {
            Obra obra = obra();
            List<Cuota> plan = planDe(obra, "300000", "175000");
            pagarEntera(plan.get(0), LocalDate.now(), "Efectivo", null);

            assertThatThrownBy(() -> servicio.registrarPago(100L, new RegistrarPago(
                    new BigDecimal("300000"), LocalDate.now(), "Efectivo", null)))
                    .isInstanceOf(ReglaDeNegocioException.class)
                    .hasMessageContaining("ya está abonada");
        }

        @Test
        @DisplayName("Anular un pago devuelve la cuota a pendiente con su motivo")
        void anularPago() {
            Obra obra = obra();
            List<Cuota> plan = planDe(obra, "300000", "175000");
            pagarEntera(plan.get(0), LocalDate.now(), "Cheque", null);

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
            conCac("1.1");

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
            pagarEntera(plan.get(0), LocalDate.now(), "Transferencia", null);
            conCac("1.1");

            PlanDeCobro r = servicio.aplicarCac(5L);

            // El anticipo ya cobrado queda intacto: reajustarlo sería cobrar
            // dos veces por lo mismo.
            assertThat(r.cuotas().get(0).montoCuota()).isEqualByComparingTo("300000");
            // Las pendientes suben 10%.
            assertThat(r.cuotas().get(1).montoCuota()).isEqualByComparingTo("192500.00");
            assertThat(r.cuotas().get(2).montoCuota()).isEqualByComparingTo("192500.00");
        }

        @Test
        @DisplayName("Sin ninguna actualización cargada, no hay nada que aplicar")
        void sinCoeficienteCargado() {
            obra();
            when(cacRepositorio.ultimo()).thenReturn(Optional.empty());

            assertThatThrownBy(() -> servicio.previaCac(5L))
                    .isInstanceOf(ReglaDeNegocioException.class)
                    .hasMessageContaining("Todavía no hay ninguna actualización");
        }

        /**
         * El caso que reportó Ricardo. Antes la tabla guardaba el NIVEL del
         * índice y el coeficiente salía de dividir un mes por el anterior; con
         * 0,1 y 1,6 cargados eso daba 16, y las cuotas se multiplicaban por
         * dieciseis. Ahora el número es directamente el multiplicador.
         */
        @Test
        @DisplayName("El número cargado ES el multiplicador: 1,4 sobre $1.000 da $1.400")
        void elCoeficienteSeAplicaTalCual() {
            Obra obra = obra();
            planDe(obra, "1000", "1000", "1000");
            conCac("1.4");
            devolverLoQueSeGuarda();

            var previa = servicio.previaCac(5L);
            assertThat(previa.coeficiente()).isEqualByComparingTo("1.4");
            // Tres cuotas de 1.000 pendientes: 3.000 pasan a 4.200.
            assertThat(previa.saldoActual()).isEqualByComparingTo("3000");
            assertThat(previa.saldoActualizado()).isEqualByComparingTo("4200");

            var r = servicio.aplicarCac(5L);
            assertThat(r.cuotas().get(1).montoCuota()).isEqualByComparingTo("1400.00");
        }

        @Test
        @DisplayName("Un coeficiente de 1 deja las cuotas como estaban")
        void coeficienteNeutro() {
            Obra obra = obra();
            planDe(obra, "1000", "1000");
            conCac("1");
            devolverLoQueSeGuarda();

            var r = servicio.aplicarCac(5L);
            assertThat(r.cuotas().get(1).montoCuota()).isEqualByComparingTo("1000.00");
        }

        @Test
        @DisplayName("Sin cuotas pendientes no hay saldo que actualizar")
        void sinPendientesNoSeActualiza() {
            Obra obra = obra();
            List<Cuota> plan = planDe(obra, "300000", "175000");
            plan.forEach(c -> pagarEntera(c, LocalDate.now(), "Efectivo", null));
            conCac("1.1");

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

    // ------------------------------------------------------------------
    //  Pagos parciales
    // ------------------------------------------------------------------

    /**
     * Alcance NUEVO: el informe no pide pagos parciales. Define la cuota con
     * estados Pendiente / Abonada / Vencida, o sea que se cobra entera o no se
     * cobra. Se agregó a pedido de Santino en la auditoría previa a la entrega.
     */
    @org.junit.jupiter.api.Nested
    @DisplayName("Pagos parciales")
    class Parciales {

        @Test
        @DisplayName("Un pago parcial deja la cuota en Parcial, con su saldo")
        void pagoParcial() {
            Obra obra = obra();
            planDe(obra, "300000", "175000", "175000");

            PlanDeCobro plan = servicio.registrarPago(100L, new RegistrarPago(
                    new BigDecimal("120000"), LocalDate.of(2026, 9, 1),
                    "Transferencia", "Recibo"));

            var anticipo = plan.cuotas().get(0);
            assertThat(anticipo.estado()).isEqualTo(Cuota.ESTADO_PARCIAL);
            assertThat(anticipo.totalPagado()).isEqualByComparingTo("120000");
            assertThat(anticipo.saldo()).isEqualByComparingTo("180000");

            // El total cobrado de la obra incluye lo que entró a cuenta.
            assertThat(plan.totalCobrado()).isEqualByComparingTo("120000");
            assertThat(plan.saldoPendiente()).isEqualByComparingTo("530000");

            // Todavía no cuenta como cuota abonada: falta plata.
            assertThat(plan.cuotasAbonadas()).isZero();
        }

        @Test
        @DisplayName("Dos pagos que completan la cuota la dejan Abonada")
        void dosPagosCompletan() {
            Obra obra = obra();
            planDe(obra, "300000", "175000");

            servicio.registrarPago(100L, new RegistrarPago(
                    new BigDecimal("120000"), LocalDate.of(2026, 9, 1), "Efectivo", null));
            PlanDeCobro plan = servicio.registrarPago(100L, new RegistrarPago(
                    new BigDecimal("180000"), LocalDate.of(2026, 9, 15),
                    "Transferencia", "Recibo"));

            var anticipo = plan.cuotas().get(0);
            assertThat(anticipo.estado()).isEqualTo(Cuota.ESTADO_ABONADA);
            assertThat(anticipo.saldo()).isEqualByComparingTo("0");
            assertThat(anticipo.pagos()).hasSize(2);
            assertThat(plan.cuotasAbonadas()).isEqualTo(1);
        }

        @Test
        @DisplayName("No se puede cobrar más que el saldo")
        void noSeCobraDeMas() {
            Obra obra = obra();
            planDe(obra, "300000", "175000");
            servicio.registrarPago(100L, new RegistrarPago(
                    new BigDecimal("250000"), LocalDate.now(), "Efectivo", null));

            // Quedan 50000 de saldo y se intentan cobrar 80000.
            assertThatThrownBy(() -> servicio.registrarPago(100L, new RegistrarPago(
                    new BigDecimal("80000"), LocalDate.now(), "Efectivo", null)))
                    .isInstanceOf(ReglaDeNegocioException.class)
                    .hasMessageContaining("supera el saldo");
        }

        @Test
        @DisplayName("Anular devuelve la cuota a deberse entera, con todos sus pagos")
        void anularBorraTodosLosPagos() {
            Obra obra = obra();
            planDe(obra, "300000", "175000");
            servicio.registrarPago(100L, new RegistrarPago(
                    new BigDecimal("120000"), LocalDate.now(), "Efectivo", null));
            servicio.registrarPago(100L, new RegistrarPago(
                    new BigDecimal("50000"), LocalDate.now(), "Efectivo", null));

            PlanDeCobro plan = servicio.anularPago(100L, new AnularPago("Rebotó"));

            var anticipo = plan.cuotas().get(0);
            assertThat(anticipo.totalPagado()).isEqualByComparingTo("0");
            assertThat(anticipo.saldo()).isEqualByComparingTo("300000");
            assertThat(anticipo.pagos()).isEmpty();
            assertThat(anticipo.motivoAnulacion()).isEqualTo("Rebotó");
        }

        @Test
        @DisplayName("Una cuota sin pagos no se puede anular")
        void sinPagosNoSeAnula() {
            Obra obra = obra();
            planDe(obra, "300000", "175000");

            assertThatThrownBy(() -> servicio.anularPago(100L, new AnularPago("Error")))
                    .isInstanceOf(ReglaDeNegocioException.class)
                    .hasMessageContaining("no tiene ningún pago");
        }

        /**
         * El punto más delicado del cambio.
         *
         * Aplicar el coeficiente sobre el monto entero encarecería
         * retroactivamente la parte ya pagada, y el cliente terminaría debiendo
         * plata por algo que ya pagó.
         */
        @Test
        @DisplayName("El CAC ajusta solo el saldo impago, no lo ya pagado")
        void cacSoloSobreElSaldo() {
            Obra obra = obra();
            List<Cuota> plan = planDe(obra, "300000", "175000");

            // Se pagan 100000 de los 300000 del anticipo: quedan 200000.
            pagarParcial(plan.get(0), new BigDecimal("100000"));
            conCac("1.1");   // +10%

            PlanDeCobro r = servicio.aplicarCac(5L);

            // 100000 ya pagados + 200000 x 1,10 = 100000 + 220000 = 320000
            assertThat(r.cuotas().get(0).montoCuota()).isEqualByComparingTo("320000.00");
            // Lo pagado no se movió.
            assertThat(r.cuotas().get(0).totalPagado()).isEqualByComparingTo("100000");
            assertThat(r.cuotas().get(0).saldo()).isEqualByComparingTo("220000.00");
        }

        @Test
        @DisplayName("Una cuota parcial y vencida SIGUE vencida: se sigue debiendo")
        void parcialVencidaSigueVencida() {
            Obra obra = obra();
            Cuota vencida = new Cuota(obra, 0, new BigDecimal("300000"),
                    LocalDate.now().minusDays(10));
            asignarId(vencida, "idCuota", 100L);

            pagarParcial(vencida, new BigDecimal("50000"));
            vencida.revisarVencimiento(LocalDate.now());

            // Que haya entrado algo no cambia que el resto está impago y fuera
            // de término: es justo lo que hay que reclamar.
            assertThat(vencida.getEstado()).isEqualTo(Cuota.ESTADO_VENCIDA);
            assertThat(vencida.saldo()).isEqualByComparingTo("250000");
        }

        private void pagarParcial(Cuota cuota, BigDecimal monto) {
            cuota.registrarPago(new Pago(cuota, monto, LocalDate.now(),
                    "Transferencia", null, null));
        }
    }

    /**
     * Paga una cuota entera.
     *
     * Reemplaza al viejo cuota.abonar(): ahora el estado no se marca, se deriva
     * de los pagos registrados. Para los tests que solo necesitan "esta cuota ya
     * se cobro", esto es el equivalente.
     */
    private static void pagarEntera(Cuota cuota, LocalDate fecha,
                                    String medio, String comprobante) {
        cuota.registrarPago(new Pago(cuota, cuota.getMontoCuota(), fecha,
                medio, comprobante, null));
    }
}
