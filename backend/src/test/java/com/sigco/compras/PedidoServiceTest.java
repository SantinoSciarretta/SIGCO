package com.sigco.compras;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.sigco.clientes.Cliente;
import com.sigco.common.exception.RecursoNoEncontradoException;
import com.sigco.common.exception.ReglaDeNegocioException;
import com.sigco.compras.dto.PedidoDtos.Anulacion;
import com.sigco.compras.dto.PedidoDtos.Aprobacion;
import com.sigco.compras.dto.PedidoDtos.LineaSolicitud;
import com.sigco.compras.dto.PedidoDtos.NuevoPedido;
import com.sigco.compras.dto.PedidoDtos.PrecioLinea;
import com.sigco.compras.dto.PedidoDtos.Recepcion;
import com.sigco.compras.dto.PedidoRespuesta;
import com.sigco.gastos.GastoService;
import com.sigco.materiales.Material;
import com.sigco.materiales.MaterialRepository;
import com.sigco.obras.Obra;
import com.sigco.obras.ObraRepository;
import com.sigco.presupuestacion.Rubro;
import com.sigco.proveedores.CotizacionRepository;
import com.sigco.proveedores.Proveedor;
import com.sigco.proveedores.ProveedorRepository;
import java.lang.reflect.Field;
import java.math.BigDecimal;
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
 * Tests del circuito de compras.
 *
 * Cubren las reglas que el informe define para reemplazar el WhatsApp: que no
 * se pueda recibir lo que no se envio, que el precio se confirme completo antes
 * de aprobar, y que el estado de recepcion salga de si hubo diferencias.
 */
@ExtendWith(MockitoExtension.class)
class PedidoServiceTest {

    @Mock private PedidoRepository repositorio;
    @Mock private ObraRepository obraRepositorio;
    @Mock private MaterialRepository materialRepositorio;
    @Mock private ProveedorRepository proveedorRepositorio;
    @Mock private CotizacionRepository cotizacionRepositorio;
    // La generacion del gasto se prueba en GastoServiceTest; aca solo hace
    // falta que la dependencia exista para no romper la inyeccion.
    @Mock private GastoService gastoService;
    // Desde el modulo 14 los servicios registran quien hizo cada cosa.
    @Mock private com.sigco.seguridad.SesionActual sesion;

    @InjectMocks private PedidoService servicio;

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

    private Obra obra(Long id) {
        Cliente cliente = new Cliente("Marcela Ferrari", null, null, null, null);
        asignarId(cliente, "idCliente", 1L);
        Obra o = new Obra(cliente, "Av. Cabildo 2340", "Departamento",
                          Obra.TIPO_REFORMA, null, null);
        asignarId(o, "idObra", id);
        return o;
    }

    private Material material(String nombre, Long id) {
        Rubro rubro = new Rubro("Albañilería");
        asignarId(rubro, "idRubro", 1L);
        Material m = new Material(nombre, rubro, "bolsa");
        asignarId(m, "idMaterial", id);
        return m;
    }

    private Proveedor proveedor(Long id) {
        Proveedor p = new Proveedor("Corralón San Martín", "Zona Norte", null, null);
        asignarId(p, "idProveedor", id);
        return p;
    }

    /** Pedido pendiente con dos materiales, que es el punto de partida real. */
    private Pedido pedidoPendiente() {
        Pedido p = new Pedido(obra(1L), null);
        asignarId(p, "idPedido", 10L);
        p.agregarMaterial(new PedidoMaterial(p, material("Cemento CP40", 1L), new BigDecimal("20")));
        p.agregarMaterial(new PedidoMaterial(p, material("Arena gruesa", 2L), new BigDecimal("5")));
        lenient().when(repositorio.buscarCompleto(10L)).thenReturn(Optional.of(p));
        return p;
    }

    private void devolverLoQueSeGuarda() {
        lenient().when(repositorio.save(any(Pedido.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    // ==================================================================

    @Nested
    @DisplayName("Alta del pedido")
    class Alta {

        @Test
        @DisplayName("Se crea con sus materiales y queda pendiente de aprobación, sin proveedor")
        void creaPendienteSinProveedor() {
            when(obraRepositorio.findById(1L)).thenReturn(Optional.of(obra(1L)));
            when(materialRepositorio.findById(1L)).thenReturn(Optional.of(material("Cemento CP40", 1L)));
            when(materialRepositorio.findById(2L)).thenReturn(Optional.of(material("Arena gruesa", 2L)));
            devolverLoQueSeGuarda();

            PedidoRespuesta r = servicio.crear(new NuevoPedido(1L, List.of(
                    new LineaSolicitud(1L, new BigDecimal("20")),
                    new LineaSolicitud(2L, new BigDecimal("5")))));

            assertThat(r.estado()).isEqualTo(Pedido.ESTADO_PENDIENTE);
            // El capataz no decide a quien comprarle: eso llega con la aprobacion.
            assertThat(r.idProveedor()).isNull();
            assertThat(r.materiales()).hasSize(2);
            // Sin precios todavia, asi que el total no significa nada.
            assertThat(r.tieneTodosLosPrecios()).isFalse();
        }

        @Test
        @DisplayName("No se puede pedir material para una obra cancelada")
        void obraCanceladaNoAdmitePedidos() {
            Obra cancelada = obra(1L);
            cancelada.cancelar("El cliente desistió");
            when(obraRepositorio.findById(1L)).thenReturn(Optional.of(cancelada));

            assertThatThrownBy(() -> servicio.crear(new NuevoPedido(1L, List.of(
                    new LineaSolicitud(1L, BigDecimal.ONE)))))
                    .isInstanceOf(ReglaDeNegocioException.class)
                    .hasMessageContaining("no admite pedidos");
        }

        @Test
        @DisplayName("No se puede pedir un material dado de baja del catálogo")
        void materialInactivoNoSePuedePedir() {
            Material viejo = material("Cal hidratada", 3L);
            viejo.desactivar();
            when(obraRepositorio.findById(1L)).thenReturn(Optional.of(obra(1L)));
            when(materialRepositorio.findById(3L)).thenReturn(Optional.of(viejo));
            devolverLoQueSeGuarda();

            assertThatThrownBy(() -> servicio.crear(new NuevoPedido(1L, List.of(
                    new LineaSolicitud(3L, BigDecimal.ONE)))))
                    .isInstanceOf(ReglaDeNegocioException.class)
                    .hasMessageContaining("inactivo");
        }

        @Test
        @DisplayName("El mismo material no puede ir dos veces en el pedido")
        void materialRepetidoSeRechaza() {
            when(obraRepositorio.findById(1L)).thenReturn(Optional.of(obra(1L)));
            when(materialRepositorio.findById(1L)).thenReturn(Optional.of(material("Cemento CP40", 1L)));
            devolverLoQueSeGuarda();

            assertThatThrownBy(() -> servicio.crear(new NuevoPedido(1L, List.of(
                    new LineaSolicitud(1L, new BigDecimal("10")),
                    new LineaSolicitud(1L, new BigDecimal("5"))))))
                    .isInstanceOf(ReglaDeNegocioException.class)
                    .hasMessageContaining("dos veces");
        }
    }

    // ==================================================================

    @Nested
    @DisplayName("Aprobación")
    class Aprobar {

        @Test
        @DisplayName("Aprobar asigna proveedor, precios y lo envía en un solo paso")
        void apruebaYEnvia() {
            pedidoPendiente();
            when(proveedorRepositorio.findById(7L)).thenReturn(Optional.of(proveedor(7L)));

            PedidoRespuesta r = servicio.aprobar(10L, new Aprobacion(7L, List.of(
                    new PrecioLinea(1L, new BigDecimal("26000")),
                    new PrecioLinea(2L, new BigDecimal("140000")))));

            assertThat(r.estado()).isEqualTo(Pedido.ESTADO_ENVIADO);
            assertThat(r.nombreProveedor()).isEqualTo("Corralón San Martín");
            assertThat(r.fechaAprobacion()).isNotNull();
            // 20 x 26.000 = 520.000 ; 5 x 140.000 = 700.000
            assertThat(r.total()).isEqualByComparingTo("1220000.00");
            assertThat(r.tieneTodosLosPrecios()).isTrue();
        }

        @Test
        @DisplayName("Falta el precio de un material: no se aprueba")
        void precioIncompletoNoAprueba() {
            pedidoPendiente();
            when(proveedorRepositorio.findById(7L)).thenReturn(Optional.of(proveedor(7L)));

            assertThatThrownBy(() -> servicio.aprobar(10L, new Aprobacion(7L, List.of(
                    new PrecioLinea(1L, new BigDecimal("26000"))))))
                    .isInstanceOf(ReglaDeNegocioException.class)
                    .hasMessageContaining("Falta el precio");
        }

        @Test
        @DisplayName("No se le envía un pedido a un proveedor inactivo")
        void proveedorInactivoNoRecibePedidos() {
            pedidoPendiente();
            Proveedor inactivo = proveedor(7L);
            inactivo.desactivar();
            when(proveedorRepositorio.findById(7L)).thenReturn(Optional.of(inactivo));

            assertThatThrownBy(() -> servicio.aprobar(10L, new Aprobacion(7L, List.of(
                    new PrecioLinea(1L, BigDecimal.TEN),
                    new PrecioLinea(2L, BigDecimal.TEN)))))
                    .isInstanceOf(ReglaDeNegocioException.class)
                    .hasMessageContaining("inactivo");
        }

        @Test
        @DisplayName("Un pedido ya enviado no se vuelve a aprobar")
        void noSeApruebaDosVeces() {
            Pedido p = pedidoPendiente();
            p.aprobar(proveedor(7L));

            assertThatThrownBy(() -> servicio.aprobar(10L, new Aprobacion(7L, List.of(
                    new PrecioLinea(1L, BigDecimal.TEN)))))
                    .isInstanceOf(ReglaDeNegocioException.class)
                    .hasMessageContaining("pendiente de aprobación");
        }
    }

    // ==================================================================

    @Nested
    @DisplayName("Recepción en obra")
    class Recibir {

        @Test
        @DisplayName("Sin nota de diferencia queda Recibido Completo")
        void sinDiferenciasQuedaCompleto() {
            Pedido p = pedidoPendiente();
            p.aprobar(proveedor(7L));

            PedidoRespuesta r = servicio.recibir(10L,
                    new Recepcion("remitos/r-2026-09-03.jpg", null));

            assertThat(r.estado()).isEqualTo(Pedido.ESTADO_RECIBIDO_COMPLETO);
            assertThat(r.fotoRemito()).isEqualTo("remitos/r-2026-09-03.jpg");
            assertThat(r.fechaRecepcion()).isNotNull();
        }

        @Test
        @DisplayName("Con nota de diferencia queda Recibido con Diferencias")
        void conDiferenciasQuedaMarcado() {
            Pedido p = pedidoPendiente();
            p.aprobar(proveedor(7L));

            PedidoRespuesta r = servicio.recibir(10L,
                    new Recepcion("remitos/r.jpg", "Faltaron 4 bolsas de cemento"));

            // El estado sale del dato, no de una eleccion del usuario: nadie
            // puede marcar "completo" y a la vez anotar que faltaron bolsas.
            assertThat(r.estado()).isEqualTo(Pedido.ESTADO_RECIBIDO_CON_DIFERENCIAS);
            assertThat(r.notaDiferencia()).isEqualTo("Faltaron 4 bolsas de cemento");
        }

        @Test
        @DisplayName("Una nota en blanco no cuenta como diferencia")
        void notaEnBlancoNoEsDiferencia() {
            Pedido p = pedidoPendiente();
            p.aprobar(proveedor(7L));

            PedidoRespuesta r = servicio.recibir(10L, new Recepcion("remitos/r.jpg", "   "));

            assertThat(r.estado()).isEqualTo(Pedido.ESTADO_RECIBIDO_COMPLETO);
            assertThat(r.notaDiferencia()).isNull();
        }

        @Test
        @DisplayName("No se recibe un pedido que todavía no fue enviado")
        void noSeRecibeLoNoEnviado() {
            pedidoPendiente();

            assertThatThrownBy(() -> servicio.recibir(10L, new Recepcion("remitos/r.jpg", null)))
                    .isInstanceOf(ReglaDeNegocioException.class)
                    .hasMessageContaining("todavía no fue aprobado");
        }

        @Test
        @DisplayName("No se recibe dos veces el mismo pedido")
        void noSeRecibeDosVeces() {
            Pedido p = pedidoPendiente();
            p.aprobar(proveedor(7L));
            p.recibir(null, "remitos/r.jpg", null);

            assertThatThrownBy(() -> servicio.recibir(10L, new Recepcion("remitos/otro.jpg", null)))
                    .isInstanceOf(ReglaDeNegocioException.class)
                    .hasMessageContaining("ya está");
        }
    }

    // ==================================================================

    @Nested
    @DisplayName("Anulación")
    class Anular {

        @Test
        @DisplayName("Un pedido pendiente se anula dejando el motivo")
        void anulaPendiente() {
            pedidoPendiente();

            PedidoRespuesta r = servicio.anular(10L, new Anulacion("El proveedor no tenía stock"));

            assertThat(r.estado()).isEqualTo(Pedido.ESTADO_ANULADO);
            assertThat(r.motivoAnulacion()).isEqualTo("El proveedor no tenía stock");
        }

        @Test
        @DisplayName("No se anula un pedido ya recibido: el material llegó a la obra")
        void noSeAnulaLoRecibido() {
            Pedido p = pedidoPendiente();
            p.aprobar(proveedor(7L));
            p.recibir(null, "remitos/r.jpg", null);

            assertThatThrownBy(() -> servicio.anular(10L, new Anulacion("Me arrepentí")))
                    .isInstanceOf(ReglaDeNegocioException.class)
                    .hasMessageContaining("ya recibido");
        }
    }

    @Test
    @DisplayName("Pedir un pedido inexistente devuelve el error que se traduce a 404")
    void inexistente() {
        when(repositorio.buscarCompleto(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.obtener(999L))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessageContaining("Pedido");
    }
}
