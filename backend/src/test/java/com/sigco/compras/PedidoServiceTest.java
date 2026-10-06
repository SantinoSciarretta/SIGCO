package com.sigco.compras;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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
import com.sigco.proveedores.Cotizacion;
import com.sigco.proveedores.CotizacionRepository;
import com.sigco.proveedores.Proveedor;
import com.sigco.proveedores.ProveedorRepository;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDateTime;
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
    // El alcance por obra (modulo 14) se prueba aparte: aca se le dice
    // que alcanza todo, para que estos tests midan lo que vinieron a medir.
    @Mock private com.sigco.seguridad.AlcanceDeObras alcance;

    // La auditoria de las acciones sensibles se simula: lo que se verifica aca
    // es la regla de negocio, no que se escriba la traza.
    @Mock private com.sigco.accesos.ServicioAuditoria auditoria;

    // El PDF en si se prueba aparte; aca solo hace falta que el generador
    // exista y devuelva algo, para verificar que el link publico lo entrega.
    @Mock private GeneradorDeOrdenDePedido generadorDeOrden;

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

    /**
     * Una obra EN EJECUCION, que es la unica que admite pedidos.
     *
     * Desde el pedido de Ricardo del 23/09, una obra en presupuestacion ya no
     * acepta pedidos de materiales: mientras se esta cotizando no se compra
     * nada, porque el presupuesto todavia puede no aprobarse. La obra de prueba
     * arranca en ejecucion para que estos tests midan lo suyo.
     */
    private Obra obra(Long id) {
        Cliente cliente = new Cliente("Marcela Ferrari", null, null, null, null);
        asignarId(cliente, "idCliente", 1L);
        Obra o = new Obra(cliente, "Av. Cabildo 2340", "Departamento",
                          Obra.TIPO_REFORMA, null, null);
        asignarId(o, "idObra", id);
        o.pasarAEjecucion();
        return o;
    }

    @org.junit.jupiter.api.Test
    @org.junit.jupiter.api.DisplayName("No se piden materiales para una obra en presupuestación")
    void noSePideEnPresupuestacion() {
        // Mientras se está cotizando no se compra nada: el presupuesto todavía
        // puede no aprobarse, y ese pedido generaría un gasto contra una obra
        // que quizás nunca arranca. Lo detectó Ricardo al probar el sistema.
        when(obraRepositorio.findById(5L))
                .thenReturn(java.util.Optional.of(obraEnPresupuestacion(5L)));

        assertThatThrownBy(() -> servicio.crear(new NuevoPedido(5L, 7L,
                java.util.List.of(new LineaSolicitud(1L, new java.math.BigDecimal("10"))))))
                .isInstanceOf(ReglaDeNegocioException.class)
                .hasMessageContaining("presupuestación");
    }

    /** Una obra que todavia se esta presupuestando: no admite pedidos. */
    private Obra obraEnPresupuestacion(Long id) {
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
        Proveedor p = new Proveedor("Corralón San Martín", "Zona Norte", null, null, null);
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
        @DisplayName("Se crea con sus materiales y el corralón, pendiente de aprobación")
        void creaPendienteConCorralon() {
            when(obraRepositorio.findById(1L)).thenReturn(Optional.of(obra(1L)));
            when(proveedorRepositorio.findById(7L)).thenReturn(Optional.of(proveedor(7L)));
            when(materialRepositorio.findById(1L)).thenReturn(Optional.of(material("Cemento CP40", 1L)));
            when(materialRepositorio.findById(2L)).thenReturn(Optional.of(material("Arena gruesa", 2L)));
            devolverLoQueSeGuarda();

            PedidoRespuesta r = servicio.crear(new NuevoPedido(1L, 7L, List.of(
                    new LineaSolicitud(1L, new BigDecimal("20")),
                    new LineaSolicitud(2L, new BigDecimal("5")))));

            assertThat(r.estado()).isEqualTo(Pedido.ESTADO_PENDIENTE);
            // El corralón se elige al cargar: es a quien se le pide cotización.
            assertThat(r.idProveedor()).isEqualTo(7L);
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

            assertThatThrownBy(() -> servicio.crear(new NuevoPedido(1L, 7L, List.of(
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
            when(proveedorRepositorio.findById(7L)).thenReturn(Optional.of(proveedor(7L)));
            when(materialRepositorio.findById(3L)).thenReturn(Optional.of(viejo));
            devolverLoQueSeGuarda();

            assertThatThrownBy(() -> servicio.crear(new NuevoPedido(1L, 7L, List.of(
                    new LineaSolicitud(3L, BigDecimal.ONE)))))
                    .isInstanceOf(ReglaDeNegocioException.class)
                    .hasMessageContaining("inactivo");
        }

        @Test
        @DisplayName("El mismo material no puede ir dos veces en el pedido")
        void materialRepetidoSeRechaza() {
            when(obraRepositorio.findById(1L)).thenReturn(Optional.of(obra(1L)));
            when(proveedorRepositorio.findById(7L)).thenReturn(Optional.of(proveedor(7L)));
            when(materialRepositorio.findById(1L)).thenReturn(Optional.of(material("Cemento CP40", 1L)));
            devolverLoQueSeGuarda();

            assertThatThrownBy(() -> servicio.crear(new NuevoPedido(1L, 7L, List.of(
                    new LineaSolicitud(1L, new BigDecimal("10")),
                    new LineaSolicitud(1L, new BigDecimal("5"))))))
                    .isInstanceOf(ReglaDeNegocioException.class)
                    .hasMessageContaining("dos veces");
        }

        @Test
        @DisplayName("Un capataz carga el pedido sin corralón: se elige al aprobar")
        void capatazSinCorralon() {
            when(obraRepositorio.findById(1L)).thenReturn(Optional.of(obra(1L)));
            when(materialRepositorio.findById(1L)).thenReturn(Optional.of(material("Cemento CP40", 1L)));
            devolverLoQueSeGuarda();

            PedidoRespuesta r = servicio.crear(new NuevoPedido(1L, null, List.of(
                    new LineaSolicitud(1L, new BigDecimal("20")))));

            assertThat(r.idProveedor()).isNull();
            verify(proveedorRepositorio, never()).findById(anyLong());
        }

        @Test
        @DisplayName("No se le pide a un corralón inactivo")
        void corralonInactivo() {
            Proveedor inactivo = proveedor(7L);
            inactivo.desactivar();
            when(obraRepositorio.findById(1L)).thenReturn(Optional.of(obra(1L)));
            when(proveedorRepositorio.findById(7L)).thenReturn(Optional.of(inactivo));

            assertThatThrownBy(() -> servicio.crear(new NuevoPedido(1L, 7L, List.of(
                    new LineaSolicitud(1L, BigDecimal.ONE)))))
                    .isInstanceOf(ReglaDeNegocioException.class)
                    .hasMessageContaining("inactivo");
        }
    }

    // ==================================================================

    @Nested
    @DisplayName("Aprobación")
    class Aprobar {

        @Test
        @DisplayName("Aprobar pone los precios sin IVA y el total suma el 21%")
        void apruebaYEnvia() {
            pedidoPendiente().asignarProveedor(proveedor(7L));

            PedidoRespuesta r = servicio.aprobar(10L, new Aprobacion(null, List.of(
                    new PrecioLinea(1L, new BigDecimal("26000")),
                    new PrecioLinea(2L, new BigDecimal("140000")))));

            assertThat(r.estado()).isEqualTo(Pedido.ESTADO_ENVIADO);
            // Sin elegir proveedor al aprobar, queda el que se eligió al cargar.
            assertThat(r.nombreProveedor()).isEqualTo("Corralón San Martín");
            assertThat(r.fechaAprobacion()).isNotNull();
            // 20 x 26.000 = 520.000 ; 5 x 140.000 = 700.000
            assertThat(r.subtotal()).isEqualByComparingTo("1220000.00");
            assertThat(r.iva()).isEqualByComparingTo("256200.00");
            assertThat(r.total()).isEqualByComparingTo("1476200.00");
            assertThat(r.tieneTodosLosPrecios()).isTrue();
        }

        @Test
        @DisplayName("Los precios aprobados quedan como cotización del corralón")
        void guardaLasCotizaciones() {
            pedidoPendiente().asignarProveedor(proveedor(7L));

            servicio.aprobar(10L, new Aprobacion(null, List.of(
                    new PrecioLinea(1L, new BigDecimal("26000")),
                    new PrecioLinea(2L, new BigDecimal("140000")))));

            // Una por material: son el precio de referencia la próxima vez.
            verify(cotizacionRepositorio, times(2)).save(any(Cotizacion.class));
        }

        @Test
        @DisplayName("Al aprobar, la compra se carga como gasto de la obra")
        void generaElGasto() {
            pedidoPendiente().asignarProveedor(proveedor(7L));

            servicio.aprobar(10L, new Aprobacion(null, List.of(
                    new PrecioLinea(1L, new BigDecimal("26000")),
                    new PrecioLinea(2L, new BigDecimal("140000")))));

            verify(gastoService).generarDesdeRecepcion(any(Obra.class), eq(10L), anyMap(), any());
        }

        @Test
        @DisplayName("Se puede aprobar con otro corralón que el elegido al cargar")
        void cambiaDeCorralon() {
            pedidoPendiente().asignarProveedor(proveedor(7L));
            Proveedor otro = new Proveedor("Corralón Norte", "Zona Norte", null, null, null);
            asignarId(otro, "idProveedor", 8L);
            when(proveedorRepositorio.findById(8L)).thenReturn(Optional.of(otro));

            PedidoRespuesta r = servicio.aprobar(10L, new Aprobacion(8L, List.of(
                    new PrecioLinea(1L, BigDecimal.TEN),
                    new PrecioLinea(2L, BigDecimal.TEN))));

            assertThat(r.nombreProveedor()).isEqualTo("Corralón Norte");
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
        @DisplayName("Anular un pedido aprobado anula también su gasto")
        void anulaElGasto() {
            Pedido p = pedidoPendiente();
            p.aprobar(proveedor(7L));

            servicio.anular(10L, new Anulacion("El corralón no entregó"));

            verify(gastoService).anularDePedido(10L, "El corralón no entregó");
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

    // ==================================================================
    //  Pedirle cotización al corralón por WhatsApp
    // ==================================================================

    /**
     * Desde el 05/10/2026 el WhatsApp sirve para PEDIR COTIZACIÓN: el mensaje
     * lleva los materiales y las cantidades, sin precios, sin links y sin PDF.
     *
     * SIGCO no manda el mensaje: arma el link y lo abre. Lo que se prueba acá
     * es que ese link quede bien armado y que cuando el teléfono no se entiende
     * NO se invente uno.
     */
    @Nested
    @DisplayName("Pedido de cotización por WhatsApp")
    class PedidoDeCotizacion {

        private Pedido pedidoConCorralon(String telefonoDelProveedor) {
            Pedido pedido = pedidoPendiente();
            Proveedor corralon = new Proveedor("Corralón San Martín", "Zona Norte",
                                               telefonoDelProveedor, null, null);
            asignarId(corralon, "idProveedor", 3L);
            pedido.asignarProveedor(corralon);
            return pedido;
        }

        @Test
        @DisplayName("Arma el link de WhatsApp al número del corralón")
        void armaElLink() {
            pedidoConCorralon("11 4567-8900");

            var envio = servicio.prepararEnvioPorWhatsApp(10L);

            assertThat(envio.telefonoParaWhatsApp()).isEqualTo("5491145678900");
            assertThat(envio.urlWhatsApp()).startsWith("https://wa.me/5491145678900?text=");
        }

        @Test
        @DisplayName("El mensaje pide cotización con los materiales y sus cantidades")
        void elMensajePideCotizacion() {
            pedidoConCorralon("11 4567-8900");

            String mensaje = servicio.prepararEnvioPorWhatsApp(10L).mensaje();

            assertThat(mensaje)
                    .contains("cotización")
                    .contains("Entregar en: Av. Cabildo 2340")
                    .contains("- Cemento CP40: 20 bolsa")
                    .contains("- Arena gruesa: 5 bolsa");
        }

        @Test
        @DisplayName("El mensaje no lleva precios, ni links, ni PDF")
        void sinPreciosNiLinks() {
            pedidoConCorralon("11 4567-8900");

            var envio = servicio.prepararEnvioPorWhatsApp(10L);

            assertThat(envio.mensaje()).doesNotContain("$").doesNotContain("http")
                    .doesNotContain("PDF");
            assertThat(envio.urlOrden()).isNull();
        }

        @Test
        @DisplayName("Un pedido aprobado ya no pide cotización")
        void aprobadoNoPideCotizacion() {
            pedidoConCorralon("11 4567-8900").aprobar(proveedor(7L));

            assertThatThrownBy(() -> servicio.prepararEnvioPorWhatsApp(10L))
                    .isInstanceOf(ReglaDeNegocioException.class)
                    .hasMessageContaining("antes de aprobar");
        }

        /**
         * El caso que el normalizador viene a cubrir: si el teléfono no se
         * puede interpretar, NO se abre una conversación con un número
         * inventado.
         */
        @Test
        @DisplayName("Con un teléfono que no se entiende, no arma el link y avisa")
        void noInventaUnNumero() {
            pedidoConCorralon("preguntar por Jorge");

            var envio = servicio.prepararEnvioPorWhatsApp(10L);

            assertThat(envio.telefonoParaWhatsApp()).isNull();
            assertThat(envio.urlWhatsApp()).isNull();
            assertThat(envio.aviso()).contains("No se pudo interpretar el teléfono");
            // El mensaje sí se arma: se puede copiar y mandar a mano.
            assertThat(envio.mensaje()).contains("cotización");
        }

        @Test
        @DisplayName("Sin teléfono cargado, dice dónde cargarlo")
        void sinTelefono() {
            pedidoConCorralon(null);

            var envio = servicio.prepararEnvioPorWhatsApp(10L);

            assertThat(envio.urlWhatsApp()).isNull();
            assertThat(envio.aviso()).contains("no tiene teléfono cargado");
        }

        @Test
        @DisplayName("Un pedido viejo sin corralón no puede pedir cotización")
        void sinProveedor() {
            pedidoPendiente();

            assertThatThrownBy(() -> servicio.prepararEnvioPorWhatsApp(10L))
                    .isInstanceOf(ReglaDeNegocioException.class)
                    .hasMessageContaining("no tiene corralón");
        }

        // ---------- Los links públicos que ya se habían mandado ----------

        @Test
        @DisplayName("Un link público ya mandado sigue devolviendo el PDF sin pedir sesión")
        void elLinkPublicoEntregaElPdf() {
            Pedido pedido = pedidoConCorralon("11 4567-8900");
            pedido.compartirOrden("token-de-prueba", LocalDateTime.now().plusDays(30));

            when(repositorio.buscarPorTokenDeOrden("token-de-prueba"))
                    .thenReturn(Optional.of(pedido));
            when(generadorDeOrden.generar(pedido)).thenReturn(new byte[]{1, 2, 3});

            assertThat(servicio.ordenPorToken("token-de-prueba")).hasSize(3);
            verify(alcance, never()).exigirAlcance(any());
        }

        @Test
        @DisplayName("Un token inventado no devuelve nada")
        void tokenInexistente() {
            when(repositorio.buscarPorTokenDeOrden("inventado")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> servicio.ordenPorToken("inventado"))
                    .isInstanceOf(RecursoNoEncontradoException.class);
        }

        @Test
        @DisplayName("Un link vencido deja de servir")
        void tokenVencido() {
            Pedido pedido = pedidoConCorralon("11 4567-8900");
            pedido.compartirOrden("viejo", LocalDateTime.now().minusDays(1));
            when(repositorio.buscarPorTokenDeOrden("viejo")).thenReturn(Optional.of(pedido));

            assertThatThrownBy(() -> servicio.ordenPorToken("viejo"))
                    .isInstanceOf(RecursoNoEncontradoException.class);
        }

        @Test
        @DisplayName("Cortar un link ya mandado lo deja inservible al instante")
        void cortarElLink() {
            Pedido pedido = pedidoConCorralon("11 4567-8900");
            pedido.compartirOrden("token", LocalDateTime.now().plusDays(30));
            devolverLoQueSeGuarda();

            servicio.dejarDeCompartirOrden(10L);

            assertThat(pedido.getTokenOrden()).isNull();
            assertThat(pedido.tieneOrdenCompartida()).isFalse();
        }
    }
}
