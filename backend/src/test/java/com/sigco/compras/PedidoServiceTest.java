package com.sigco.compras;

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

        assertThatThrownBy(() -> servicio.crear(new NuevoPedido(5L,
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

    // ==================================================================
    //  Mandarle la orden al corralon por WhatsApp
    // ==================================================================

    /**
     * Pedido de Ricardo: que al enviar el pedido se le pueda mandar al corralón
     * por WhatsApp con el PDF.
     *
     * SIGCO no manda el mensaje: arma el link y lo abre. Lo que se prueba acá
     * es que ese link quede bien armado, que el PDF se pueda abrir sin login, y
     * que cuando el teléfono no se entiende NO se invente uno.
     */
    @Nested
    @DisplayName("Envío de la orden por WhatsApp")
    class EnvioPorWhatsApp {

        /**
         * El servicio se arma a mano acá porque necesita la dirección pública,
         * que es un String y @InjectMocks le pasa null: Mockito no puede
         * inventar un valor para un tipo que no se puede simular.
         */
        private PedidoService conUrl(String url) {
            return new PedidoService(repositorio, obraRepositorio, materialRepositorio,
                    proveedorRepositorio, cotizacionRepositorio, gastoService, sesion,
                    alcance, auditoria, generadorDeOrden, url);
        }

        private Pedido pedidoAprobado(String telefonoDelProveedor) {
            Pedido pedido = pedidoPendiente();
            Proveedor corralon = new Proveedor("Corralón San Martín", "Zona Norte",
                                               telefonoDelProveedor, null);
            asignarId(corralon, "idProveedor", 3L);
            pedido.aprobar(corralon);
            devolverLoQueSeGuarda();
            return pedido;
        }

        @Test
        @DisplayName("Arma el link de WhatsApp con el número y el mensaje")
        void armaElLink() {
            pedidoAprobado("11 4567-8900");

            var envio = conUrl("https://sigco.app").prepararEnvioPorWhatsApp(10L);

            assertThat(envio.telefonoParaWhatsApp()).isEqualTo("5491145678900");
            assertThat(envio.urlWhatsApp()).startsWith("https://wa.me/5491145678900?text=");
            assertThat(envio.aviso()).isNull();
        }

        @Test
        @DisplayName("El mensaje dice de qué obra es y qué materiales lleva")
        void elMensajeTieneLoQueElCorralonNecesita() {
            pedidoAprobado("11 4567-8900");

            var envio = conUrl("https://sigco.app").prepararEnvioPorWhatsApp(10L);

            assertThat(envio.mensaje())
                    .contains("Granica SRL")
                    .contains("Pedido #10")
                    .contains("Av. Cabildo 2340")
                    .contains("Cemento CP40")
                    .contains("Arena gruesa")
                    .contains(envio.urlOrden());
        }

        @Test
        @DisplayName("Genera un link público con token y vencimiento")
        void generaElLinkPublico() {
            Pedido pedido = pedidoAprobado("11 4567-8900");

            var envio = conUrl("https://sigco.app").prepararEnvioPorWhatsApp(10L);

            assertThat(pedido.getTokenOrden()).isNotNull().hasSizeGreaterThan(30);
            assertThat(pedido.tieneOrdenCompartida()).isTrue();
            assertThat(envio.urlOrden())
                    .isEqualTo("https://sigco.app/api/ordenes-publicas/" + pedido.getTokenOrden());
            assertThat(envio.vence()).isAfter(LocalDateTime.now().plusDays(29));
        }

        @Test
        @DisplayName("Una barra final en la dirección no duplica la del link")
        void noDuplicaLaBarra() {
            pedidoAprobado("11 4567-8900");

            var envio = conUrl("https://sigco.app/").prepararEnvioPorWhatsApp(10L);

            assertThat(envio.urlOrden()).contains("sigco.app/api/ordenes-publicas/");
            assertThat(envio.urlOrden()).doesNotContain("//api");
        }

        /**
         * Volver a preparar el envío REUSA el link. Si el corralón ya lo tiene
         * en el chat, mandárselo de nuevo no debería romperle el anterior.
         *
         * Y sobre todo: generar uno nuevo cada vez hacía que dos llamadas
         * cruzadas dejaran la pantalla mostrando un token ya invalidado. Lo
         * encontró la prueba en el navegador, donde React llama al efecto dos
         * veces en desarrollo.
         */
        @Test
        @DisplayName("Preparar de nuevo reusa el link vigente")
        void elTokenSeReusa() {
            Pedido pedido = pedidoAprobado("11 4567-8900");
            PedidoService servicioConUrl = conUrl("https://sigco.app");

            servicioConUrl.prepararEnvioPorWhatsApp(10L);
            String primero = pedido.getTokenOrden();

            var segundo = servicioConUrl.prepararEnvioPorWhatsApp(10L);

            assertThat(pedido.getTokenOrden()).isEqualTo(primero);
            assertThat(segundo.urlOrden()).endsWith(primero);
        }

        @Test
        @DisplayName("Si el link venció, prepara uno nuevo")
        void elTokenVencidoSeReemplaza() {
            Pedido pedido = pedidoAprobado("11 4567-8900");
            pedido.compartirOrden("vencido", LocalDateTime.now().minusDays(1));

            conUrl("https://sigco.app").prepararEnvioPorWhatsApp(10L);

            assertThat(pedido.getTokenOrden()).isNotEqualTo("vencido");
            assertThat(pedido.tieneOrdenCompartida()).isTrue();
        }

        /**
         * La regla más importante de todo esto: si el teléfono no se entiende,
         * NO se abre una conversación con un número inventado.
         */
        @Test
        @DisplayName("Con un teléfono que no se entiende, no arma el link y avisa")
        void noInventaUnNumero() {
            pedidoAprobado("preguntar por Jorge");

            var envio = conUrl("https://sigco.app").prepararEnvioPorWhatsApp(10L);

            assertThat(envio.telefonoParaWhatsApp()).isNull();
            assertThat(envio.urlWhatsApp()).isNull();
            assertThat(envio.aviso()).contains("No se pudo interpretar el teléfono");
            // El link de la orden SÍ se genera: se puede copiar y mandar a mano.
            assertThat(envio.urlOrden()).isNotNull();
        }

        @Test
        @DisplayName("Sin teléfono cargado, dice dónde cargarlo")
        void sinTelefono() {
            pedidoAprobado(null);

            var envio = conUrl("https://sigco.app").prepararEnvioPorWhatsApp(10L);

            assertThat(envio.urlWhatsApp()).isNull();
            assertThat(envio.aviso()).contains("no tiene teléfono cargado");
        }

        @Test
        @DisplayName("No se prepara el envío de un pedido sin proveedor")
        void sinProveedor() {
            pedidoPendiente();

            assertThatThrownBy(() -> conUrl("https://sigco.app").prepararEnvioPorWhatsApp(10L))
                    .isInstanceOf(ReglaDeNegocioException.class)
                    .hasMessageContaining("no tiene proveedor");
        }

        @Test
        @DisplayName("No se prepara el envío de un pedido anulado")
        void anulado() {
            Pedido pedido = pedidoAprobado("11 4567-8900");
            pedido.anular("Se consiguió en otro lado");

            assertThatThrownBy(() -> conUrl("https://sigco.app").prepararEnvioPorWhatsApp(10L))
                    .isInstanceOf(ReglaDeNegocioException.class)
                    .hasMessageContaining("anulado");
        }

        // ---------- El link público ----------

        @Test
        @DisplayName("El link público devuelve el PDF sin pedir sesión")
        void elLinkPublicoEntregaElPdf() {
            Pedido pedido = pedidoAprobado("11 4567-8900");
            // El token se pone a mano en vez de llamar a prepararEnvio: ese
            // método sí comprueba alcance, y acá se mide justamente que
            // ordenPorToken NO lo haga.
            pedido.compartirOrden("token-de-prueba", LocalDateTime.now().plusDays(30));

            when(repositorio.buscarPorTokenDeOrden("token-de-prueba"))
                    .thenReturn(Optional.of(pedido));
            when(generadorDeOrden.generar(pedido)).thenReturn(new byte[]{1, 2, 3});

            assertThat(servicio.ordenPorToken("token-de-prueba")).hasSize(3);
            // Y no se comprobó alcance: el corralón no tiene sesión.
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
            Pedido pedido = pedidoAprobado("11 4567-8900");
            pedido.compartirOrden("viejo", LocalDateTime.now().minusDays(1));
            when(repositorio.buscarPorTokenDeOrden("viejo")).thenReturn(Optional.of(pedido));

            assertThatThrownBy(() -> servicio.ordenPorToken("viejo"))
                    .isInstanceOf(RecursoNoEncontradoException.class);
        }

        @Test
        @DisplayName("Cortar el link lo deja inservible al instante")
        void cortarElLink() {
            Pedido pedido = pedidoAprobado("11 4567-8900");
            conUrl("https://sigco.app").prepararEnvioPorWhatsApp(10L);
            assertThat(pedido.tieneOrdenCompartida()).isTrue();

            servicio.dejarDeCompartirOrden(10L);

            assertThat(pedido.getTokenOrden()).isNull();
            assertThat(pedido.tieneOrdenCompartida()).isFalse();
        }
    }
}
