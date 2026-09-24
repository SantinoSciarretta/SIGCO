package com.sigco.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.sigco.clientes.Cliente;
import com.sigco.cobros.CobrosService;
import com.sigco.cobros.dto.CobrosDtos.ResumenCobro;
import com.sigco.gastos.GastoService;
import com.sigco.gastos.dto.EstadoFinanciero;
import com.sigco.gastos.dto.EstadoFinanciero.RubroFinanciero;
import com.sigco.obras.Obra;
import com.sigco.obras.ObraRepository;
import com.sigco.seguimiento.SeguimientoService;
import com.sigco.seguimiento.dto.SeguimientoDtos.AvanceObra;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * El balance de cierre de una obra.
 *
 * Lo que se prueba aca no es la suma de gastos ni el saldo de cobros —de eso ya
 * responden Gastos y Cobros, con sus propios tests— sino las tres restas que el
 * balance agrega y que no son de ningun modulo: la ganancia estimada, el
 * resultado de caja y el margen. Y la lista de lo que queda abierto, que es la
 * parte util del boton de cerrar.
 */
@ExtendWith(MockitoExtension.class)
class BalanceServiceTest {

    @Mock
    private ObraRepository obraRepositorio;

    @Mock
    private GastoService gastoService;

    @Mock
    private CobrosService cobrosService;

    @Mock
    private SeguimientoService seguimientoService;

    @InjectMocks
    private BalanceService servicio;

    // ------------------------------------------------------------------

    @Test
    @DisplayName("La ganancia estimada es lo presupuestado menos lo gastado")
    void gananciaEstimada() {
        preparar(
                economia("1000000", "600000", List.of()),
                cobros("1000000", "400000", "600000", 0),
                avance("50", 1, 2));

        var balance = servicio.de(1L);

        assertThat(balance.gananciaEstimada()).isEqualByComparingTo("400000");
        assertThat(balance.margenPorcentaje()).isEqualByComparingTo("40.00");
    }

    /**
     * La distincion que justifica que el balance tenga tres numeros y no uno.
     * Una obra puede tener buena ganancia estimada y caja negativa: se compro
     * material que todavia no se cobro. Mostrar un solo numero esconderia eso.
     */
    @Test
    @DisplayName("El resultado de caja es lo cobrado menos lo gastado, y puede ser negativo")
    void resultadoDeCajaNegativo() {
        preparar(
                economia("1000000", "600000", List.of()),
                cobros("1000000", "400000", "600000", 0),
                avance("50", 1, 2));

        var balance = servicio.de(1L);

        // Entraron 400.000 y salieron 600.000, aunque la obra proyecte ganancia.
        assertThat(balance.resultadoDeCaja()).isEqualByComparingTo("-200000");
        assertThat(balance.gananciaEstimada()).isEqualByComparingTo("400000");
    }

    @Test
    @DisplayName("Sin presupuesto aprobado el margen da cero, no rompe")
    void sinPresupuesto() {
        preparar(
                economia("0", "150000", List.of()),
                cobros("0", "0", "0", 0),
                avance("0", 0, 0));

        var balance = servicio.de(1L);

        assertThat(balance.margenPorcentaje()).isEqualByComparingTo("0");
        assertThat(balance.gananciaEstimada()).isEqualByComparingTo("-150000");
    }

    // ------------------------------------------------------------------
    //  Lo que queda abierto
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Avisa de los hitos sin completar, el saldo y las cuotas vencidas")
    void enumeraLosPendientes() {
        preparar(
                economia("1000000", "600000", List.of()),
                cobros("1000000", "400000", "600000", 2),
                avance("40", 2, 5));

        var balance = servicio.de(1L);

        assertThat(balance.pendientes())
                .anyMatch(p -> p.contains("3 hitos sin completar"))
                .anyMatch(p -> p.contains("Falta cobrar"))
                .anyMatch(p -> p.contains("2 cuotas vencidas"));
    }

    @Test
    @DisplayName("Marca los rubros que se pasaron del presupuesto")
    void avisaDeLosRubrosExcedidos() {
        preparar(
                economia("1000000", "600000", List.of(
                        rubro("Albañilería", "500000", "560000"),
                        rubro("Pintura", "500000", "40000"))),
                cobros("1000000", "1000000", "0", 0),
                avance("100", 3, 3));

        var balance = servicio.de(1L);

        assertThat(balance.pendientes()).anyMatch(p -> p.contains("1 rubro se pasó"));
    }

    @Test
    @DisplayName("Una obra cerrada y cobrada no tiene nada abierto")
    void sinPendientes() {
        preparar(
                economia("1000000", "600000", List.of(rubro("Pintura", "500000", "400000"))),
                cobros("1000000", "1000000", "0", 0),
                avance("100", 3, 3));

        assertThat(servicio.de(1L).pendientes()).isEmpty();
    }

    // ------------------------------------------------------------------
    //  Armado
    // ------------------------------------------------------------------

    private void preparar(EstadoFinanciero economia, ResumenCobro cobros, AvanceObra avance) {
        when(obraRepositorio.findById(1L)).thenReturn(Optional.of(unaObra()));
        when(gastoService.estadoFinancieroOVacio(1L)).thenReturn(economia);
        when(cobrosService.resumenOVacio(1L)).thenReturn(cobros);
        when(seguimientoService.avance(1L)).thenReturn(avance);
    }

    private Obra unaObra() {
        Cliente cliente = new Cliente("Marcela Ferrari", null, null, null, null);
        Obra obra = new Obra(cliente, "Av. Cabildo 2340", Obra.INMUEBLE_DEPARTAMENTO,
                Obra.TIPO_REFORMA, null, null);
        try {
            Field campo = Obra.class.getDeclaredField("idObra");
            campo.setAccessible(true);
            campo.set(obra, 1L);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        return obra;
    }

    private EstadoFinanciero economia(String presupuestado, String gastado,
                                      List<RubroFinanciero> rubros) {
        return new EstadoFinanciero(1L, "Av. Cabildo 2340", Obra.ESTADO_EN_EJECUCION, 7L,
                new BigDecimal(presupuestado), new BigDecimal(gastado),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, "Verde", rubros);
    }

    private RubroFinanciero rubro(String nombre, String presupuestado, String gastado) {
        BigDecimal p = new BigDecimal(presupuestado);
        BigDecimal g = new BigDecimal(gastado);
        return new RubroFinanciero(1L, nombre, p, g, p.subtract(g), BigDecimal.ZERO, "Verde");
    }

    private ResumenCobro cobros(String plan, String cobrado, String saldo, int vencidas) {
        return new ResumenCobro(1L, "Av. Cabildo 2340", "Marcela Ferrari",
                Obra.ESTADO_EN_EJECUCION, new BigDecimal(plan), new BigDecimal(cobrado),
                new BigDecimal(saldo), vencidas, LocalDate.of(2026, 10, 1));
    }

    private AvanceObra avance(String fisico, int completados, int totales) {
        return new AvanceObra(1L, "Av. Cabildo 2340", Obra.ESTADO_EN_EJECUCION,
                new BigDecimal(fisico), BigDecimal.ZERO, BigDecimal.ZERO, false,
                null, null, false, completados, totales, List.of());
    }
}
