package com.sigco.seguimiento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.sigco.clientes.Cliente;
import com.sigco.common.exception.ReglaDeNegocioException;
import com.sigco.gastos.GastoService;
import com.sigco.obras.Obra;
import com.sigco.obras.ObraRepository;
import com.sigco.seguimiento.dto.SeguimientoDtos.AvanceObra;
import com.sigco.presupuestacion.Rubro;
import com.sigco.seguimiento.dto.SeguimientoDtos.ConfiguracionEtapas;
import com.sigco.seguimiento.dto.SeguimientoDtos.ConfiguracionHitos;
import com.sigco.seguimiento.dto.SeguimientoDtos.EtapaDeObra;
import com.sigco.seguimiento.dto.SeguimientoDtos.Cumplimiento;
import com.sigco.seguimiento.dto.SeguimientoDtos.HitoSolicitud;
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
 * Tests del modulo Seguimiento de Obras.
 *
 * El foco esta en las tres reglas que hacen que el avance signifique algo: que
 * las ponderaciones cierren en 100, que el orden se respete salvo excepcion
 * explicita, y que completar el ultimo hito finalice la obra.
 */
@ExtendWith(MockitoExtension.class)
class SeguimientoServiceTest {

    @Mock private HitoRepository repositorio;
    @Mock private PlantillaHitoRepository plantillaRepositorio;
    @Mock private ObraRepository obraRepositorio;
    @Mock private GastoService gastoService;
    @Mock private com.sigco.presupuestacion.RubroRepository rubroRepositorio;
    @Mock private com.sigco.seguridad.SesionActual sesion;
    // El alcance por obra (modulo 14) se prueba aparte: aca se le dice
    // que alcanza todo, para que estos tests midan lo que vinieron a medir.
    @Mock private com.sigco.seguridad.AlcanceDeObras alcance;

    @InjectMocks private SeguimientoService servicio;

    private static void asignarId(Object entidad, String campo, Long valor) {
        try {
            Field f = entidad.getClass().getDeclaredField(campo);
            f.setAccessible(true);
            f.set(entidad, valor);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private Obra obraEnEjecucion() {
        Cliente c = new Cliente("Mario Lopez", null, null, null, null);
        asignarId(c, "idCliente", 1L);
        Obra o = new Obra(c, "Ituzaingo 231, Pilar", "Casa", Obra.TIPO_REFORMA,
                          LocalDate.of(2027, 2, 1), null);
        asignarId(o, "idObra", 5L);
        o.pasarAEjecucion();
        lenient().when(obraRepositorio.findById(5L)).thenReturn(Optional.of(o));
        return o;
    }

    /** Sin presupuesto aprobado el avance financiero es cero, no una excepcion. */
    private void sinAvanceFinanciero() {
        lenient().when(gastoService.porcentajeConsumidoOCero(5L)).thenReturn(BigDecimal.ZERO);
    }

    private void conAvanceFinanciero(String porcentaje) {
        lenient().when(gastoService.porcentajeConsumidoOCero(5L))
                .thenReturn(new BigDecimal(porcentaje));
    }

    /** Tres hitos de 30 / 40 / 30, que suman 100. */
    private List<Hito> tresHitos(Obra obra) {
        List<Hito> hitos = new ArrayList<>();
        String[] nombres = {"Demolición", "Estructura", "Terminaciones"};
        String[] pesos = {"30", "40", "30"};
        for (int i = 0; i < 3; i++) {
            Hito h = new Hito(obra, nombres[i], new BigDecimal(pesos[i]), i + 1);
            asignarId(h, "idHito", (long) (i + 1));
            lenient().when(repositorio.findById((long) (i + 1))).thenReturn(Optional.of(h));
            hitos.add(h);
        }
        lenient().when(repositorio.deLaObra(5L)).thenReturn(hitos);
        return hitos;
    }

    // ==================================================================

    @Nested
    @DisplayName("Configuración de hitos")
    class Configurar {

        @Test
        @DisplayName("Se guardan los hitos si las ponderaciones suman 100")
        void guardaSiSumaCien() {
            Obra obra = obraEnEjecucion();
            when(repositorio.deLaObra(5L)).thenReturn(List.of());
            when(repositorio.saveAll(any())).thenAnswer(i -> i.getArgument(0));

            var r = servicio.configurar(5L, new ConfiguracionHitos(List.of(
                    new HitoSolicitud("Demolición", new BigDecimal("30"), 1),
                    new HitoSolicitud("Estructura", new BigDecimal("40"), 2),
                    new HitoSolicitud("Terminaciones", new BigDecimal("30"), 3))));

            assertThat(r).hasSize(3);
            assertThat(obra.estaEnEjecucion()).isTrue();
        }

        @Test
        @DisplayName("No se guardan si las ponderaciones no suman 100")
        void rechazaSiNoSumaCien() {
            obraEnEjecucion();

            assertThatThrownBy(() -> servicio.configurar(5L, new ConfiguracionHitos(List.of(
                    new HitoSolicitud("Demolición", new BigDecimal("30"), 1),
                    new HitoSolicitud("Estructura", new BigDecimal("40"), 2)))))
                    .isInstanceOf(ReglaDeNegocioException.class)
                    .hasMessageContaining("suman 70% y tienen que sumar exactamente 100%");
        }

        @Test
        @DisplayName("Una obra en presupuestación no admite hitos")
        void obraEnPresupuestacionNoAdmiteHitos() {
            Cliente c = new Cliente("Mario Lopez", null, null, null, null);
            Obra enPresupuestacion = new Obra(c, "Azcuenaga 243", "Departamento",
                    Obra.TIPO_REFORMA, null, null);
            asignarId(enPresupuestacion, "idObra", 5L);
            when(obraRepositorio.findById(5L)).thenReturn(Optional.of(enPresupuestacion));

            assertThatThrownBy(() -> servicio.configurar(5L, new ConfiguracionHitos(List.of(
                    new HitoSolicitud("Demolición", new BigDecimal("100"), 1)))))
                    .isInstanceOf(ReglaDeNegocioException.class)
                    .hasMessageContaining("solo se definen hitos de obras en ejecución");
        }

        @Test
        @DisplayName("No se redefine el plan si ya hay hitos completados")
        void noRedefineConHitosCompletados() {
            Obra obra = obraEnEjecucion();
            List<Hito> hitos = tresHitos(obra);
            hitos.get(0).completar(LocalDate.now(), null, null);

            assertThatThrownBy(() -> servicio.configurar(5L, new ConfiguracionHitos(List.of(
                    new HitoSolicitud("Otro plan", new BigDecimal("100"), 1)))))
                    .isInstanceOf(ReglaDeNegocioException.class)
                    .hasMessageContaining("borraría");
        }

        @Test
        @DisplayName("Dos hitos con el mismo nombre se rechazan")
        void nombreRepetidoSeRechaza() {
            obraEnEjecucion();

            assertThatThrownBy(() -> servicio.configurar(5L, new ConfiguracionHitos(List.of(
                    new HitoSolicitud("Demolición", new BigDecimal("50"), 1),
                    new HitoSolicitud("demolición", new BigDecimal("50"), 2)))))
                    .isInstanceOf(ReglaDeNegocioException.class)
                    .hasMessageContaining("mismo nombre");
        }
    }

    // ==================================================================

    @Nested
    @DisplayName("Cumplimiento y avance")
    class Cumplir {

        @Test
        @DisplayName("Completar el primer hito da 30% de avance físico")
        void avanceFisicoPorPonderacion() {
            Obra obra = obraEnEjecucion();
            tresHitos(obra);
            sinAvanceFinanciero();

            AvanceObra a = servicio.completar(1L, new Cumplimiento(
                    LocalDate.of(2026, 9, 1), null, false));

            // El avance sale de la ponderación, no de "1 de 3 hitos".
            assertThat(a.avanceFisico()).isEqualByComparingTo("30");
            assertThat(a.hitosCompletados()).isEqualTo(1);
            assertThat(a.hitosTotales()).isEqualTo(3);
        }

        @Test
        @DisplayName("Omitir el flag forzar equivale a no forzar, no a un error")
        void forzarOmitidoEsFalso() {
            Obra obra = obraEnEjecucion();
            tresHitos(obra);

            // Este caso rompia en la API real: con boolean primitivo, omitir el
            // campo daba 400 en lugar de tomarlo como false.
            assertThatThrownBy(() -> servicio.completar(3L, new Cumplimiento(
                    LocalDate.now(), null, null)))
                    .isInstanceOf(ReglaDeNegocioException.class)
                    .hasMessageContaining("Falta completar");
        }

        @Test
        @DisplayName("No se completa un hito si hay anteriores pendientes")
        void respetaElOrden() {
            Obra obra = obraEnEjecucion();
            tresHitos(obra);

            assertThatThrownBy(() -> servicio.completar(3L, new Cumplimiento(
                    LocalDate.now(), null, false)))
                    .isInstanceOf(ReglaDeNegocioException.class)
                    .hasMessageContaining("Falta completar");
        }

        @Test
        @DisplayName("Con forzar se puede adelantar una etapa")
        void forzarSalteaElOrden() {
            Obra obra = obraEnEjecucion();
            tresHitos(obra);
            sinAvanceFinanciero();

            // El informe lo prevé: en la práctica algunas tareas se adelantan.
            AvanceObra a = servicio.completar(3L, new Cumplimiento(
                    LocalDate.now(), "Se adelantó la pintura", true));

            assertThat(a.avanceFisico()).isEqualByComparingTo("30");
        }

        @Test
        @DisplayName("Completar el último hito finaliza la obra")
        void ultimoHitoFinalizaLaObra() {
            Obra obra = obraEnEjecucion();
            List<Hito> hitos = tresHitos(obra);
            hitos.get(0).completar(LocalDate.now(), null, null);
            hitos.get(1).completar(LocalDate.now(), null, null);
            sinAvanceFinanciero();

            AvanceObra a = servicio.completar(3L, new Cumplimiento(LocalDate.now(), null, false));

            assertThat(a.avanceFisico()).isEqualByComparingTo("100");
            // Cierra el ciclo de vida que arrancó al aprobarse el definitivo.
            assertThat(obra.estaFinalizada()).isTrue();
            assertThat(a.estadoObra()).isEqualTo(Obra.ESTADO_FINALIZADA);
        }

        @Test
        @DisplayName("Una obra finalizada tiene los hitos bloqueados")
        void obraFinalizadaBloqueaHitos() {
            Obra obra = obraEnEjecucion();
            tresHitos(obra);
            obra.finalizar();

            assertThatThrownBy(() -> servicio.completar(1L, new Cumplimiento(
                    LocalDate.now(), null, false)))
                    .isInstanceOf(ReglaDeNegocioException.class)
                    .hasMessageContaining("bloqueados");
        }
    }

    // ==================================================================

    @Nested
    @DisplayName("Cruce con el avance financiero")
    class Desfasaje {

        @Test
        @DisplayName("Sin desfasaje significativo no hay alerta")
        void sinAlerta() {
            Obra obra = obraEnEjecucion();
            tresHitos(obra);
            conAvanceFinanciero("35");

            AvanceObra a = servicio.completar(1L, new Cumplimiento(LocalDate.now(), null, false));

            // 35% gastado contra 30% avanzado: 5 puntos, dentro del margen.
            assertThat(a.desfasaje()).isEqualByComparingTo("5");
            assertThat(a.alertaDesfasaje()).isFalse();
        }

        @Test
        @DisplayName("Gastar mucho más rápido de lo que se avanza dispara la alerta")
        void alertaPorDesfasaje() {
            Obra obra = obraEnEjecucion();
            tresHitos(obra);
            conAvanceFinanciero("70");

            AvanceObra a = servicio.completar(1L, new Cumplimiento(LocalDate.now(), null, false));

            // 70% del presupuesto gastado con 30% de obra hecha: 40 puntos de
            // diferencia. Es exactamente la obra que el módulo viene a detectar.
            assertThat(a.desfasaje()).isEqualByComparingTo("40");
            assertThat(a.alertaDesfasaje()).isTrue();
        }

        @Test
        @DisplayName("Sin presupuesto aprobado el panel igual funciona")
        void sinPresupuestoElPanelFunciona() {
            Obra obra = obraEnEjecucion();
            tresHitos(obra);
            sinAvanceFinanciero();

            AvanceObra a = servicio.avance(5L);

            // El avance físico vale por sí solo: no se rompe el panel porque
            // falte el otro lado de la comparación.
            assertThat(a.avanceFinanciero()).isEqualByComparingTo("0");
            assertThat(a.alertaDesfasaje()).isFalse();
        }
    }

    // ==================================================================
    //  Las etapas cargadas por duracion
    // ==================================================================

    /**
     * Pedido de Ricardo: cargar que hay que hacer, de que rubro es y cuanto
     * lleva, y que el sistema saque el porcentaje. Lo que se prueba aca es el
     * reparto: que sume exactamente 100 y que una etapa larga pese mas.
     */
    @Nested
    @DisplayName("Etapas por duración")
    class Etapas {

        private void devolverLoQueSeGuarda() {
            when(repositorio.saveAll(anyList())).thenAnswer(i -> i.getArgument(0));
        }

        @Test
        @DisplayName("La ponderación sale de la duración: la etapa larga pesa más")
        void reparteSegunLaDuracion() {
            obraEnEjecucion();
            devolverLoQueSeGuarda();

            // 10 + 30 + 10 = 50 días.
            var etapas = servicio.configurarEtapas(5L, new ConfiguracionEtapas(List.of(
                    new EtapaDeObra("Demolición", null, 10, 1),
                    new EtapaDeObra("Estructura", null, 30, 2),
                    new EtapaDeObra("Pintura", null, 10, 3))));

            assertThat(etapas).extracting(h -> h.ponderacion().stripTrailingZeros().toPlainString())
                    .containsExactly("20", "60", "20");
            assertThat(etapas).extracting(h -> h.duracionDias()).containsExactly(10, 30, 10);
        }

        /**
         * El caso que obliga a repartir el sobrante: tres etapas iguales dan
         * 33,33 cada una y suman 99,99. Con eso el avance nunca llegaria a
         * completo, asi que el centesimo que falta tiene que ir a algun lado.
         */
        @Test
        @DisplayName("Con duraciones que no dividen exacto, igual suma 100")
        void cierraEnCienAunqueNoDivida() {
            obraEnEjecucion();
            devolverLoQueSeGuarda();

            var etapas = servicio.configurarEtapas(5L, new ConfiguracionEtapas(List.of(
                    new EtapaDeObra("Una", null, 1, 1),
                    new EtapaDeObra("Otra", null, 1, 2),
                    new EtapaDeObra("Tercera", null, 1, 3))));

            BigDecimal suma = etapas.stream().map(h -> h.ponderacion())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            assertThat(suma).isEqualByComparingTo("100");
        }

        @Test
        @DisplayName("El sobrante del redondeo va a la etapa más larga")
        void elSobranteVaALaMasLarga() {
            obraEnEjecucion();
            devolverLoQueSeGuarda();

            // 1 + 100 + 1 = 102 días. Las cortas dan 0,98 cada una y la
            // larga 98,04: suman 100,00 justo por casualidad o no, el reparto
            // se encarga de que cierre.
            var etapas = servicio.configurarEtapas(5L, new ConfiguracionEtapas(List.of(
                    new EtapaDeObra("Corta", null, 1, 1),
                    new EtapaDeObra("Larga", null, 100, 2),
                    new EtapaDeObra("Media", null, 1, 3))));

            // La larga absorbe la diferencia; las cortas quedan con su valor
            // redondeado tal cual.
            BigDecimal suma = etapas.stream().map(h -> h.ponderacion())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            assertThat(suma).isEqualByComparingTo("100");
            assertThat(etapas.get(1).ponderacion()).isGreaterThan(new BigDecimal("98"));
        }

        @Test
        @DisplayName("Guarda el rubro de cada etapa")
        void guardaElRubro() {
            obraEnEjecucion();
            devolverLoQueSeGuarda();

            Rubro albanileria = new Rubro("Albañilería");
            asignarId(albanileria, "idRubro", 3L);
            when(rubroRepositorio.findById(3L)).thenReturn(Optional.of(albanileria));

            var etapas = servicio.configurarEtapas(5L, new ConfiguracionEtapas(List.of(
                    new EtapaDeObra("Demolición de una pared", 3L, 5, 1))));

            assertThat(etapas.get(0).nombreRubro()).isEqualTo("Albañilería");
            assertThat(etapas.get(0).ponderacion()).isEqualByComparingTo("100");
        }

        @Test
        @DisplayName("Rechaza un rubro inactivo")
        void rechazaRubroInactivo() {
            obraEnEjecucion();
            Rubro dadoDeBaja = new Rubro("Viejo");
            asignarId(dadoDeBaja, "idRubro", 9L);
            dadoDeBaja.desactivar();
            when(rubroRepositorio.findById(9L)).thenReturn(Optional.of(dadoDeBaja));

            assertThatThrownBy(() -> servicio.configurarEtapas(5L, new ConfiguracionEtapas(
                    List.of(new EtapaDeObra("Algo", 9L, 5, 1)))))
                    .isInstanceOf(ReglaDeNegocioException.class)
                    .hasMessageContaining("inactivo");
        }

        @Test
        @DisplayName("No redefine las etapas si ya hay hitos completados")
        void noPisaLoYaCumplido() {
            Obra obra = obraEnEjecucion();
            List<Hito> hitos = tresHitos(obra);
            hitos.get(0).completar(LocalDate.of(2027, 3, 1), null, null);

            assertThatThrownBy(() -> servicio.configurarEtapas(5L, new ConfiguracionEtapas(
                    List.of(new EtapaDeObra("Otra cosa", null, 5, 1)))))
                    .isInstanceOf(ReglaDeNegocioException.class)
                    .hasMessageContaining("hitos completados");
        }

        @Test
        @DisplayName("No admite dos etapas con el mismo nombre")
        void rechazaNombresRepetidos() {
            obraEnEjecucion();

            assertThatThrownBy(() -> servicio.configurarEtapas(5L, new ConfiguracionEtapas(List.of(
                    new EtapaDeObra("Pintura", null, 5, 1),
                    new EtapaDeObra("pintura", null, 3, 2)))))
                    .isInstanceOf(ReglaDeNegocioException.class)
                    .hasMessageContaining("mismo nombre");
        }
    }
}
