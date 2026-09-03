package com.sigco.presupuestacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sigco.clientes.Cliente;
import com.sigco.common.exception.RecursoNoEncontradoException;
import com.sigco.common.exception.ReglaDeNegocioException;
import com.sigco.materiales.Material;
import com.sigco.materiales.MaterialRepository;
import com.sigco.obras.Obra;
import com.sigco.obras.ObraRepository;
import com.sigco.presupuestacion.dto.PresupuestoDtos.CambioEstadoPresupuesto;
import com.sigco.presupuestacion.dto.PresupuestoDtos.Duplicacion;
import com.sigco.presupuestacion.dto.PresupuestoDtos.ItemSolicitud;
import com.sigco.presupuestacion.dto.PresupuestoDtos.NuevoPresupuesto;
import com.sigco.presupuestacion.dto.PresupuestoDtos.PlanDePago;
import com.sigco.presupuestacion.dto.PresupuestoRespuesta;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
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
 * Tests del circuito de presupuestacion.
 *
 * Cubren las reglas que el informe define para el modulo mas complejo del
 * sistema: que instancia corresponde a cada tipo de obra, el versionado sin
 * sobrescribir, el ciclo de negociacion y el plan de pago.
 */
@ExtendWith(MockitoExtension.class)
class PresupuestoServiceTest {

    @Mock private PresupuestoRepository repositorio;
    @Mock private ItemPresupuestoRepository itemRepositorio;
    @Mock private ObraRepository obraRepositorio;
    @Mock private MaterialRepository materialRepositorio;
    @Mock private RubroRepository rubroRepositorio;
    @Mock private SubrubroRepository subrubroRepositorio;

    @InjectMocks private PresupuestoService servicio;

    /** Asigna el id que en produccion pone la base al guardar. */
    private static void asignarId(Object entidad, String campo, Long valor) {
        try {
            Field f = entidad.getClass().getDeclaredField(campo);
            f.setAccessible(true);
            f.set(entidad, valor);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private Obra obra(String tipoObra, Long id) {
        Cliente cliente = new Cliente("Marcela Ferrari", null, null, null, null);
        Obra obra = new Obra(cliente, "Av. Cabildo 2340", Obra.INMUEBLE_DEPARTAMENTO,
                tipoObra, null, null);
        asignarId(obra, "idObra", id);
        return obra;
    }

    private Presupuesto presupuesto(Obra obra, String tipo, Long id) {
        Presupuesto p = new Presupuesto(obra, tipo, 1, null, null);
        asignarId(p, "idPresupuesto", id);
        return p;
    }

    private Material material(String nombre, Rubro rubro, Long id) {
        Material m = new Material(nombre, rubro, "bolsa");
        asignarId(m, "idMaterial", id);
        return m;
    }

    private Rubro rubro(String nombre, Long id) {
        Rubro r = new Rubro(nombre);
        asignarId(r, "idRubro", id);
        return r;
    }

    private Subrubro subrubro(Rubro rubro, String nombre, Long id) {
        Subrubro s = new Subrubro(rubro, nombre);
        asignarId(s, "idSubrubro", id);
        return s;
    }

    private void devolverLoQueSeGuarda() {
        when(repositorio.save(any(Presupuesto.class))).thenAnswer(i -> i.getArgument(0));
    }

    // ==================================================================

    @Nested
    @DisplayName("Circuito según el tipo de obra")
    class Circuito {

        @Test
        @DisplayName("Una construcción no habilita anteproyecto")
        void construccionSinAnteproyecto() {
            when(obraRepositorio.findById(1L))
                    .thenReturn(Optional.of(obra(Obra.TIPO_CONSTRUCCION, 1L)));

            assertThatThrownBy(() -> servicio.crear(new NuevoPresupuesto(
                    1L, Presupuesto.TIPO_ANTEPROYECTO, null, null, null, null)))
                    .isInstanceOf(ReglaDeNegocioException.class)
                    .hasMessageContaining("solo a las reformas");
        }

        @Test
        @DisplayName("Una reforma no puede tener definitivo sin anteproyecto previo")
        void reformaExigeAnteproyecto() {
            when(obraRepositorio.findById(1L))
                    .thenReturn(Optional.of(obra(Obra.TIPO_REFORMA, 1L)));
            when(repositorio.countByObraIdObraAndTipoPresupuesto(1L, Presupuesto.TIPO_ANTEPROYECTO))
                    .thenReturn(0L);

            assertThatThrownBy(() -> servicio.crear(new NuevoPresupuesto(
                    1L, Presupuesto.TIPO_DEFINITIVO, null, null, null, null)))
                    .isInstanceOf(ReglaDeNegocioException.class)
                    .hasMessageContaining("sin un anteproyecto previo");
        }

        @Test
        @DisplayName("Con anteproyecto previo, la reforma sí puede tener definitivo")
        void reformaConAnteproyecto() {
            when(obraRepositorio.findById(1L))
                    .thenReturn(Optional.of(obra(Obra.TIPO_REFORMA, 1L)));
            when(repositorio.countByObraIdObraAndTipoPresupuesto(1L, Presupuesto.TIPO_ANTEPROYECTO))
                    .thenReturn(1L);
            when(repositorio.ultimaVersion(1L, Presupuesto.TIPO_DEFINITIVO)).thenReturn(0);
            devolverLoQueSeGuarda();

            PresupuestoRespuesta r = servicio.crear(new NuevoPresupuesto(
                    1L, Presupuesto.TIPO_DEFINITIVO, null, null, null, null));

            assertThat(r.estado()).isEqualTo(Presupuesto.ESTADO_BORRADOR);
            assertThat(r.version()).isEqualTo(1);
        }

        @Test
        @DisplayName("Una construcción va directo al definitivo, sin anteproyecto")
        void construccionVaDirectoAlDefinitivo() {
            when(obraRepositorio.findById(1L))
                    .thenReturn(Optional.of(obra(Obra.TIPO_CONSTRUCCION, 1L)));
            when(repositorio.ultimaVersion(1L, Presupuesto.TIPO_DEFINITIVO)).thenReturn(0);
            devolverLoQueSeGuarda();

            assertThat(servicio.crear(new NuevoPresupuesto(
                    1L, Presupuesto.TIPO_DEFINITIVO, null, null, null, null)).version())
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("Un adicional necesita un definitivo aprobado")
        void adicionalExigeDefinitivoAprobado() {
            when(obraRepositorio.findById(1L))
                    .thenReturn(Optional.of(obra(Obra.TIPO_CONSTRUCCION, 1L)));
            when(repositorio.findByObraIdObraAndTipoPresupuestoAndEstado(
                    1L, Presupuesto.TIPO_DEFINITIVO, Presupuesto.ESTADO_APROBADO))
                    .thenReturn(List.of());

            assertThatThrownBy(() -> servicio.crear(new NuevoPresupuesto(
                    1L, Presupuesto.TIPO_ADICIONAL, null, null, null, null)))
                    .isInstanceOf(ReglaDeNegocioException.class)
                    .hasMessageContaining("todavía no tiene ninguno");
        }

        @Test
        @DisplayName("No se puede presupuestar una obra que no existe")
        void obraInexistente() {
            when(obraRepositorio.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> servicio.crear(new NuevoPresupuesto(
                    99L, Presupuesto.TIPO_DEFINITIVO, null, null, null, null)))
                    .isInstanceOf(RecursoNoEncontradoException.class)
                    .hasMessageContaining("Obra");
        }
    }

    // ==================================================================

    @Nested
    @DisplayName("Cotización inicial")
    class Cotizacion {

        @Test
        @DisplayName("El total sale de metros por valor de referencia")
        void calculaElPrecioEstimativo() {
            when(obraRepositorio.findById(1L))
                    .thenReturn(Optional.of(obra(Obra.TIPO_REFORMA, 1L)));
            when(repositorio.ultimaVersion(1L, Presupuesto.TIPO_COTIZACION_INICIAL)).thenReturn(0);
            devolverLoQueSeGuarda();

            PresupuestoRespuesta r = servicio.crear(new NuevoPresupuesto(
                    1L, Presupuesto.TIPO_COTIZACION_INICIAL, null,
                    new BigDecimal("85.50"), new BigDecimal("420000.00"), "6 meses"));

            // 85,50 m2 x 420.000 = 35.910.000
            assertThat(r.totalPresupuesto()).isEqualByComparingTo("35910000.00");
        }

        @Test
        @DisplayName("Sin metros ni valor por metro, la cotización se rechaza")
        void exigeSusDatos() {
            when(obraRepositorio.findById(1L))
                    .thenReturn(Optional.of(obra(Obra.TIPO_REFORMA, 1L)));
            when(repositorio.ultimaVersion(1L, Presupuesto.TIPO_COTIZACION_INICIAL)).thenReturn(0);

            assertThatThrownBy(() -> servicio.crear(new NuevoPresupuesto(
                    1L, Presupuesto.TIPO_COTIZACION_INICIAL, null, null, null, null)))
                    .isInstanceOf(ReglaDeNegocioException.class)
                    .hasMessageContaining("metros cuadrados");
        }

        @Test
        @DisplayName("La cotización inicial no admite ítems")
        void noAdmiteItems() {
            Presupuesto cotizacion = presupuesto(
                    obra(Obra.TIPO_REFORMA, 1L), Presupuesto.TIPO_COTIZACION_INICIAL, 10L);
            when(repositorio.buscarCompleto(10L)).thenReturn(Optional.of(cotizacion));

            assertThatThrownBy(() -> servicio.agregarItem(10L, new ItemSolicitud(
                    1L, null, null, "Algo", "m2", BigDecimal.ONE, BigDecimal.TEN)))
                    .isInstanceOf(ReglaDeNegocioException.class)
                    .hasMessageContaining("no lleva ítems");
        }
    }

    // ==================================================================

    @Nested
    @DisplayName("Ítems y cálculo del total")
    class Items {

        private Presupuesto definitivoEnBorrador() {
            Presupuesto p = presupuesto(
                    obra(Obra.TIPO_CONSTRUCCION, 1L), Presupuesto.TIPO_DEFINITIVO, 10L);
            lenient().when(repositorio.buscarCompleto(10L)).thenReturn(Optional.of(p));
            return p;
        }

        @Test
        @DisplayName("El subtotal es cantidad por valor unitario, y el total la suma")
        void calculaSubtotalesYTotal() {
            definitivoEnBorrador();
            Rubro albanileria = rubro("Albañilería", 1L);
            when(rubroRepositorio.findById(1L)).thenReturn(Optional.of(albanileria));

            servicio.agregarItem(10L, new ItemSolicitud(
                    1L, null, null, "Contrapiso", "m2", new BigDecimal("40.00"), new BigDecimal("12500.00")));

            PresupuestoRespuesta r = servicio.agregarItem(10L, new ItemSolicitud(
                    1L, null, null, "Revoque", "m2", new BigDecimal("60.00"), new BigDecimal("8000.00")));

            // 40 x 12.500 = 500.000 ; 60 x 8.000 = 480.000
            assertThat(r.items()).hasSize(2);
            assertThat(r.items().get(0).subtotal()).isEqualByComparingTo("500000.00");
            assertThat(r.totalPresupuesto()).isEqualByComparingTo("980000.00");
        }

        @Test
        @DisplayName("Los subtotales se agrupan por rubro")
        void agrupaPorRubro() {
            definitivoEnBorrador();
            Rubro albanileria = rubro("Albañilería", 1L);
            Rubro plomeria = rubro("Plomería", 2L);
            when(rubroRepositorio.findById(1L)).thenReturn(Optional.of(albanileria));
            when(rubroRepositorio.findById(2L)).thenReturn(Optional.of(plomeria));

            servicio.agregarItem(10L, new ItemSolicitud(
                    1L, null, null, "Contrapiso", "m2", new BigDecimal("10"), new BigDecimal("1000")));
            servicio.agregarItem(10L, new ItemSolicitud(
                    1L, null, null, "Revoque", "m2", new BigDecimal("10"), new BigDecimal("2000")));
            PresupuestoRespuesta r = servicio.agregarItem(10L, new ItemSolicitud(
                    2L, null, null, "Desagüe", "ml", new BigDecimal("5"), new BigDecimal("3000")));

            assertThat(r.subtotalesPorRubro()).hasSize(2);
            assertThat(r.subtotalesPorRubro().get(0).nombreRubro()).isEqualTo("Albañilería");
            assertThat(r.subtotalesPorRubro().get(0).subtotal()).isEqualByComparingTo("30000");
            assertThat(r.subtotalesPorRubro().get(1).subtotal()).isEqualByComparingTo("15000");
        }

        @Test
        @DisplayName("Se puede vincular el ítem a un material del catálogo")
        void vinculaMaterialDelCatalogo() {
            definitivoEnBorrador();
            Rubro albanileria = rubro("Albañilería", 1L);
            when(rubroRepositorio.findById(1L)).thenReturn(Optional.of(albanileria));
            when(materialRepositorio.findById(3L))
                    .thenReturn(Optional.of(material("Cemento CP40", albanileria, 3L)));

            PresupuestoRespuesta r = servicio.agregarItem(10L, new ItemSolicitud(
                    1L, null, 3L, "Cemento CP40 para la carpeta del baño", "bolsa",
                    new BigDecimal("20"), new BigDecimal("15000")));

            assertThat(r.items().get(0).idMaterial()).isEqualTo(3L);
            assertThat(r.items().get(0).nombreMaterial()).isEqualTo("Cemento CP40");
            // La descripcion no se pisa con el nombre del catalogo: es lo que
            // se imprime en el PDF y puede llevar mas detalle.
            assertThat(r.items().get(0).descripcion())
                    .isEqualTo("Cemento CP40 para la carpeta del baño");
        }

        @Test
        @DisplayName("El material tiene que pertenecer al rubro del ítem")
        void materialDebeCorresponderAlRubro() {
            definitivoEnBorrador();
            Rubro albanileria = rubro("Albañilería", 1L);
            Rubro plomeria = rubro("Plomería", 2L);
            when(rubroRepositorio.findById(1L)).thenReturn(Optional.of(albanileria));
            when(materialRepositorio.findById(4L))
                    .thenReturn(Optional.of(material("Caño PVC 110", plomeria, 4L)));

            assertThatThrownBy(() -> servicio.agregarItem(10L, new ItemSolicitud(
                    1L, null, 4L, "Algo", "unidad", BigDecimal.ONE, BigDecimal.TEN)))
                    .isInstanceOf(ReglaDeNegocioException.class)
                    .hasMessageContaining("pertenece al rubro");
        }

        @Test
        @DisplayName("Un material inactivo no entra en un presupuesto nuevo")
        void materialInactivoNoSePuedeUsar() {
            definitivoEnBorrador();
            Rubro albanileria = rubro("Albañilería", 1L);
            Material viejo = material("Cal hidratada", albanileria, 5L);
            viejo.desactivar();
            when(rubroRepositorio.findById(1L)).thenReturn(Optional.of(albanileria));
            when(materialRepositorio.findById(5L)).thenReturn(Optional.of(viejo));

            assertThatThrownBy(() -> servicio.agregarItem(10L, new ItemSolicitud(
                    1L, null, 5L, "Algo", "bolsa", BigDecimal.ONE, BigDecimal.TEN)))
                    .isInstanceOf(ReglaDeNegocioException.class)
                    .hasMessageContaining("inactivo");
        }

        @Test
        @DisplayName("El ítem sin material sigue siendo válido: no todo ítem es un material")
        void itemSinMaterialEsValido() {
            definitivoEnBorrador();
            when(rubroRepositorio.findById(1L))
                    .thenReturn(Optional.of(rubro("Albañilería", 1L)));

            PresupuestoRespuesta r = servicio.agregarItem(10L, new ItemSolicitud(
                    1L, null, null, "Mano de obra de albañilería", "jornal",
                    new BigDecimal("15"), new BigDecimal("45000")));

            assertThat(r.items().get(0).idMaterial()).isNull();
        }

        @Test
        @DisplayName("No se puede usar un subrubro de otro rubro")
        void subrubroDebeCorresponderAlRubro() {
            definitivoEnBorrador();
            Rubro albanileria = rubro("Albañilería", 1L);
            Rubro plomeria = rubro("Plomería", 2L);
            when(rubroRepositorio.findById(1L)).thenReturn(Optional.of(albanileria));
            when(subrubroRepositorio.findById(7L))
                    .thenReturn(Optional.of(subrubro(plomeria, "Desagües", 7L)));

            assertThatThrownBy(() -> servicio.agregarItem(10L, new ItemSolicitud(
                    1L, 7L, null, "Algo", "m2", BigDecimal.ONE, BigDecimal.TEN)))
                    .isInstanceOf(ReglaDeNegocioException.class)
                    .hasMessageContaining("pertenece al rubro");
        }

        @Test
        @DisplayName("Un presupuesto enviado ya no se puede modificar")
        void enviadoNoSeModifica() {
            Presupuesto p = definitivoEnBorrador();
            p.enviar();

            assertThatThrownBy(() -> servicio.agregarItem(10L, new ItemSolicitud(
                    1L, null, null, "Algo", "m2", BigDecimal.ONE, BigDecimal.TEN)))
                    .isInstanceOf(ReglaDeNegocioException.class)
                    .hasMessageContaining("Generá una versión nueva");
        }
    }

    // ==================================================================

    @Nested
    @DisplayName("Versionado")
    class Versionado {

        @Test
        @DisplayName("Duplicar copia los ítems SIN sobrescribir el original")
        void duplicarNoSobrescribe() {
            Obra obra = obra(Obra.TIPO_REFORMA, 1L);
            Presupuesto anteproyecto = presupuesto(obra, Presupuesto.TIPO_ANTEPROYECTO, 10L);
            Rubro albanileria = rubro("Albañilería", 1L);
            anteproyecto.agregarItem(new ItemPresupuesto(anteproyecto, albanileria, null, null,
                    "Contrapiso", "m2", new BigDecimal("40"), new BigDecimal("12500")));

            when(repositorio.buscarCompleto(10L)).thenReturn(Optional.of(anteproyecto));
            when(repositorio.countByObraIdObraAndTipoPresupuesto(1L, Presupuesto.TIPO_ANTEPROYECTO))
                    .thenReturn(1L);
            when(repositorio.ultimaVersion(1L, Presupuesto.TIPO_DEFINITIVO)).thenReturn(0);
            devolverLoQueSeGuarda();

            PresupuestoRespuesta definitivo = servicio.duplicar(
                    10L, new Duplicacion(Presupuesto.TIPO_DEFINITIVO, null));

            // El definitivo arranca con una copia de los ítems...
            assertThat(definitivo.items()).hasSize(1);
            assertThat(definitivo.totalPresupuesto()).isEqualByComparingTo("500000.00");
            // ...y queda vinculado al presupuesto que le sirvió de base.
            assertThat(definitivo.idPresupuestoBase()).isEqualTo(10L);
            // El anteproyecto sigue intacto: son dos registros independientes.
            assertThat(anteproyecto.getItems()).hasSize(1);
            assertThat(anteproyecto.getTipoPresupuesto()).isEqualTo(Presupuesto.TIPO_ANTEPROYECTO);
        }

        @Test
        @DisplayName("La versión se numera dentro del mismo tipo")
        void versionPorTipo() {
            when(obraRepositorio.findById(1L))
                    .thenReturn(Optional.of(obra(Obra.TIPO_CONSTRUCCION, 1L)));
            when(repositorio.ultimaVersion(1L, Presupuesto.TIPO_DEFINITIVO)).thenReturn(2);
            devolverLoQueSeGuarda();

            assertThat(servicio.crear(new NuevoPresupuesto(
                    1L, Presupuesto.TIPO_DEFINITIVO, null, null, null, null)).version())
                    .isEqualTo(3);
        }
    }

    // ==================================================================

    @Nested
    @DisplayName("Ciclo de negociación")
    class Estados {

        private Presupuesto definitivoConItem() {
            Obra obra = obra(Obra.TIPO_CONSTRUCCION, 1L);
            Presupuesto p = presupuesto(obra, Presupuesto.TIPO_DEFINITIVO, 10L);
            p.agregarItem(new ItemPresupuesto(p, rubro("Albañilería", 1L), null, null,
                    "Contrapiso", "m2", new BigDecimal("10"), new BigDecimal("1000")));
            lenient().when(repositorio.buscarCompleto(10L)).thenReturn(Optional.of(p));
            return p;
        }

        @Test
        @DisplayName("No se envía un presupuesto sin ítems")
        void noSeEnviaVacio() {
            Presupuesto p = presupuesto(
                    obra(Obra.TIPO_CONSTRUCCION, 1L), Presupuesto.TIPO_DEFINITIVO, 10L);
            when(repositorio.buscarCompleto(10L)).thenReturn(Optional.of(p));

            assertThatThrownBy(() -> servicio.cambiarEstado(10L,
                    new CambioEstadoPresupuesto(Presupuesto.ESTADO_ENVIADO)))
                    .isInstanceOf(ReglaDeNegocioException.class)
                    .hasMessageContaining("sin ningún ítem");
        }

        @Test
        @DisplayName("Aprobar el definitivo pone la obra en ejecución")
        void aprobarPoneLaObraEnEjecucion() {
            Presupuesto p = definitivoConItem();
            p.enviar();
            when(repositorio.findByObraIdObraAndTipoPresupuestoAndEstado(
                    1L, Presupuesto.TIPO_DEFINITIVO, Presupuesto.ESTADO_APROBADO))
                    .thenReturn(List.of());

            PresupuestoRespuesta r = servicio.cambiarEstado(10L,
                    new CambioEstadoPresupuesto(Presupuesto.ESTADO_APROBADO));

            assertThat(r.estado()).isEqualTo(Presupuesto.ESTADO_APROBADO);
            // El cambio encadenado que pide el informe: aprobar el definitivo
            // es lo que da inicio a la obra.
            assertThat(p.getObra().getEstado()).isEqualTo(Obra.ESTADO_EN_EJECUCION);
        }

        @Test
        @DisplayName("Una obra no puede tener dos definitivos aprobados")
        void unSoloDefinitivoAprobado() {
            Presupuesto p = definitivoConItem();
            p.enviar();
            Presupuesto yaAprobado = presupuesto(p.getObra(), Presupuesto.TIPO_DEFINITIVO, 5L);
            yaAprobado.aprobar();
            when(repositorio.findByObraIdObraAndTipoPresupuestoAndEstado(
                    1L, Presupuesto.TIPO_DEFINITIVO, Presupuesto.ESTADO_APROBADO))
                    .thenReturn(List.of(yaAprobado));

            assertThatThrownBy(() -> servicio.cambiarEstado(10L,
                    new CambioEstadoPresupuesto(Presupuesto.ESTADO_APROBADO)))
                    .isInstanceOf(ReglaDeNegocioException.class)
                    .hasMessageContaining("presupuesto adicional");
        }

        @Test
        @DisplayName("Un presupuesto rechazado ya no cambia de estado")
        void rechazadoEsTerminal() {
            Presupuesto p = definitivoConItem();
            p.rechazar();

            assertThatThrownBy(() -> servicio.cambiarEstado(10L,
                    new CambioEstadoPresupuesto(Presupuesto.ESTADO_APROBADO)))
                    .isInstanceOf(ReglaDeNegocioException.class)
                    .hasMessageContaining("no admite cambios");
        }

        @Test
        @DisplayName("Un presupuesto se rechaza, nunca se elimina")
        void rechazarConservaElRegistro() {
            Presupuesto p = definitivoConItem();
            p.enviar();

            PresupuestoRespuesta r = servicio.cambiarEstado(10L,
                    new CambioEstadoPresupuesto(Presupuesto.ESTADO_RECHAZADO));

            assertThat(r.estado()).isEqualTo(Presupuesto.ESTADO_RECHAZADO);
            // Sigue estando, con sus ítems y su total: la negociación queda
            // documentada.
            assertThat(r.items()).hasSize(1);
            assertThat(r.totalPresupuesto()).isEqualByComparingTo("10000");
        }
    }

    // ==================================================================

    @Nested
    @DisplayName("Plan de pago")
    class Plan {

        private Presupuesto conTotal(String total) {
            Obra obra = obra(Obra.TIPO_CONSTRUCCION, 1L);
            Presupuesto p = presupuesto(obra, Presupuesto.TIPO_DEFINITIVO, 10L);
            p.agregarItem(new ItemPresupuesto(p, rubro("Albañilería", 1L), null, null,
                    "Obra", "gl", BigDecimal.ONE, new BigDecimal(total)));
            lenient().when(repositorio.buscarCompleto(10L)).thenReturn(Optional.of(p));
            return p;
        }

        @Test
        @DisplayName("El anticipo y las cuotas se calculan sobre el total")
        void calculaAnticipoYCuotas() {
            conTotal("10000000");

            PresupuestoRespuesta r = servicio.definirPlanDePago(
                    10L, new PlanDePago(new BigDecimal("30"), 7, "7 meses"));

            // 30% de 10.000.000 = 3.000.000 ; saldo 7.000.000 en 7 cuotas
            assertThat(r.montoAnticipo()).isEqualByComparingTo("3000000.00");
            assertThat(r.montoCuota()).isEqualByComparingTo("1000000.00");
        }

        @Test
        @DisplayName("Un anticipo parcial sin cuotas no cierra")
        void anticipoParcialSinCuotas() {
            conTotal("10000000");

            assertThatThrownBy(() -> servicio.definirPlanDePago(
                    10L, new PlanDePago(new BigDecimal("30"), 0, null)))
                    .isInstanceOf(ReglaDeNegocioException.class)
                    .hasMessageContaining("al menos una cuota");
        }

        @Test
        @DisplayName("Un anticipo del 100% con cuotas tampoco cierra")
        void anticipoTotalConCuotas() {
            conTotal("10000000");

            assertThatThrownBy(() -> servicio.definirPlanDePago(
                    10L, new PlanDePago(new BigDecimal("100"), 3, null)))
                    .isInstanceOf(ReglaDeNegocioException.class)
                    .hasMessageContaining("no queda");
        }

        @Test
        @DisplayName("Un anticipo del 100% sin cuotas es válido: se paga todo junto")
        void pagoContado() {
            conTotal("10000000");

            PresupuestoRespuesta r = servicio.definirPlanDePago(
                    10L, new PlanDePago(new BigDecimal("100"), 0, null));

            assertThat(r.montoAnticipo()).isEqualByComparingTo("10000000.00");
            assertThat(r.montoCuota()).isNull();
        }
    }

    @Test
    @DisplayName("Pedir un presupuesto inexistente devuelve el error que se traduce a 404")
    void inexistente() {
        when(repositorio.buscarCompleto(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.obtener(999L))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessageContaining("Presupuesto");
    }

    /**
     * Baja definitiva de un presupuesto.
     *
     * Es una extension sobre lo que pide el informe (que dice marcar Rechazado
     * y no eliminar), asi que lo que mas importa probar son los dos bloqueos:
     * sin ellos, borrar dejaria un derivado sin origen o una obra en ejecucion
     * sin el presupuesto que la justifica.
     */
    @Nested
    @DisplayName("Eliminar")
    class Eliminar {

        @Test
        @DisplayName("Un borrador sin derivados se elimina")
        void borradorSeElimina() {
            Presupuesto p = presupuesto(obra(Obra.TIPO_REFORMA, 1L),
                    Presupuesto.TIPO_ANTEPROYECTO, 5L);
            when(repositorio.findById(5L)).thenReturn(Optional.of(p));
            when(repositorio.existsByPresupuestoBaseIdPresupuesto(5L)).thenReturn(false);

            servicio.eliminar(5L);

            verify(repositorio).delete(p);
        }

        @Test
        @DisplayName("Un rechazado también se elimina: ya no sostiene nada")
        void rechazadoSeElimina() {
            Presupuesto p = presupuesto(obra(Obra.TIPO_REFORMA, 1L),
                    Presupuesto.TIPO_DEFINITIVO, 6L);
            p.enviar();
            p.rechazar();
            when(repositorio.findById(6L)).thenReturn(Optional.of(p));
            when(repositorio.existsByPresupuestoBaseIdPresupuesto(6L)).thenReturn(false);

            servicio.eliminar(6L);

            verify(repositorio).delete(p);
        }

        @Test
        @DisplayName("No se elimina si otro presupuesto lo tiene como base")
        void conDerivadoNoSeElimina() {
            Presupuesto p = presupuesto(obra(Obra.TIPO_REFORMA, 1L),
                    Presupuesto.TIPO_ANTEPROYECTO, 7L);
            when(repositorio.findById(7L)).thenReturn(Optional.of(p));
            when(repositorio.existsByPresupuestoBaseIdPresupuesto(7L)).thenReturn(true);

            assertThatThrownBy(() -> servicio.eliminar(7L))
                    .isInstanceOf(ReglaDeNegocioException.class)
                    .hasMessageContaining("otro presupuesto se generó a partir de este");

            verify(repositorio, never()).delete(any());
        }

        @Test
        @DisplayName("Un definitivo aprobado se elimina y devuelve la obra a presupuestación")
        void aprobadoSeEliminaYRevierteLaObra() {
            Obra obra = obra(Obra.TIPO_REFORMA, 1L);
            Presupuesto p = presupuesto(obra, Presupuesto.TIPO_DEFINITIVO, 8L);
            p.enviar();
            p.aprobar();
            obra.pasarAEjecucion();
            obra.registrarInicioReal(LocalDate.of(2026, 5, 4));

            when(repositorio.findById(8L)).thenReturn(Optional.of(p));
            when(repositorio.existsByPresupuestoBaseIdPresupuesto(8L)).thenReturn(false);
            when(repositorio.findByObraIdObraAndTipoPresupuestoAndEstado(
                    1L, Presupuesto.TIPO_DEFINITIVO, Presupuesto.ESTADO_APROBADO))
                    .thenReturn(List.of(p));

            servicio.eliminar(8L);

            verify(repositorio).delete(p);
            assertThat(obra.estaEnPresupuestacion()).isTrue();
            // La fecha de inicio dependia de esa aprobacion: sin ella no se sostiene.
            assertThat(obra.getFechaInicioReal()).isNull();
        }

        @Test
        @DisplayName("Si queda otro definitivo aprobado, la obra sigue en ejecución")
        void conOtroAprobadoLaObraNoSeRevierte() {
            Obra obra = obra(Obra.TIPO_REFORMA, 1L);
            Presupuesto aEliminar = presupuesto(obra, Presupuesto.TIPO_DEFINITIVO, 8L);
            aEliminar.enviar();
            aEliminar.aprobar();
            Presupuesto otro = presupuesto(obra, Presupuesto.TIPO_DEFINITIVO, 9L);
            otro.enviar();
            otro.aprobar();
            obra.pasarAEjecucion();

            when(repositorio.findById(8L)).thenReturn(Optional.of(aEliminar));
            when(repositorio.existsByPresupuestoBaseIdPresupuesto(8L)).thenReturn(false);
            when(repositorio.findByObraIdObraAndTipoPresupuestoAndEstado(
                    1L, Presupuesto.TIPO_DEFINITIVO, Presupuesto.ESTADO_APROBADO))
                    .thenReturn(List.of(aEliminar, otro));

            servicio.eliminar(8L);

            verify(repositorio).delete(aEliminar);
            assertThat(obra.estaEnEjecucion()).isTrue();
        }

        @Test
        @DisplayName("Eliminar un anteproyecto aprobado no toca el estado de la obra")
        void anteproyectoAprobadoNoTocaLaObra() {
            Obra obra = obra(Obra.TIPO_REFORMA, 1L);
            Presupuesto p = presupuesto(obra, Presupuesto.TIPO_ANTEPROYECTO, 8L);
            p.enviar();
            p.aprobar();
            obra.pasarAEjecucion();

            when(repositorio.findById(8L)).thenReturn(Optional.of(p));
            when(repositorio.existsByPresupuestoBaseIdPresupuesto(8L)).thenReturn(false);

            servicio.eliminar(8L);

            // Solo el definitivo pone la obra en ejecucion, asi que solo el
            // definitivo puede deshacerlo.
            assertThat(obra.estaEnEjecucion()).isTrue();
        }

        @Test
        @DisplayName("Eliminar uno inexistente devuelve el error que se traduce a 404")
        void inexistenteNoSeElimina() {
            when(repositorio.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> servicio.eliminar(999L))
                    .isInstanceOf(RecursoNoEncontradoException.class)
                    .hasMessageContaining("Presupuesto");

            verify(repositorio, never()).delete(any());
        }
    }
}
