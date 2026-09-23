package com.sigco.proveedores;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sigco.common.exception.RecursoNoEncontradoException;
import com.sigco.common.exception.ReglaDeNegocioException;
import com.sigco.materiales.Material;
import com.sigco.materiales.MaterialRepository;
import com.sigco.presupuestacion.Rubro;
import com.sigco.proveedores.dto.ProveedorDtos.CotizacionRespuesta;
import com.sigco.proveedores.dto.ProveedorDtos.CotizacionSolicitud;
import com.sigco.proveedores.dto.ProveedorDtos.ObservacionSolicitud;
import com.sigco.proveedores.dto.ProveedorDtos.ProveedorRespuesta;
import com.sigco.proveedores.dto.ProveedorDtos.ProveedorSolicitud;
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
 * Tests de las reglas del modulo Proveedores.
 */
@ExtendWith(MockitoExtension.class)
class ProveedorServiceTest {

    @Mock private ProveedorRepository repositorio;
    @Mock private CotizacionRepository cotizacionRepositorio;
    @Mock private ObservacionRepository observacionRepositorio;
    @Mock private MaterialRepository materialRepositorio;

    // Desde la auditoria del 22/09, una observacion vinculada a un pedido exige
    // que ese pedido exista y sea de este proveedor.
    @Mock private com.sigco.compras.PedidoRepository pedidoRepositorio;

    @InjectMocks private ProveedorService servicio;

    private static void asignarId(Object entidad, String campo, Long valor) {
        try {
            Field f = entidad.getClass().getDeclaredField(campo);
            f.setAccessible(true);
            f.set(entidad, valor);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private Proveedor proveedor(String nombre, String zona, Long id) {
        Proveedor p = new Proveedor(nombre, zona, null, null);
        asignarId(p, "idProveedor", id);
        return p;
    }

    private Material material(String nombre, Long id) {
        Material m = new Material(nombre, new Rubro("Albanileria"), "bolsa");
        asignarId(m, "idMaterial", id);
        return m;
    }

    // ==================================================================

    @Nested
    @DisplayName("Alta y duplicados")
    class Alta {

        @Test
        @DisplayName("Un proveedor nuevo nace Activo y sin cotizaciones")
        void naceActivo() {
            when(repositorio.buscarPorNombreYZona(anyString(), anyString()))
                    .thenReturn(Optional.empty());
            when(repositorio.save(any(Proveedor.class))).thenAnswer(i -> i.getArgument(0));

            ProveedorRespuesta r = servicio.crear(new ProveedorSolicitud(
                    "  Corralón San Martín  ", "  Zona Norte  ", "11 4444-5555", null));

            assertThat(r.estado()).isEqualTo(Proveedor.ESTADO_ACTIVO);
            assertThat(r.nombreProveedor()).isEqualTo("Corralón San Martín");
            assertThat(r.zonaCobertura()).isEqualTo("Zona Norte");
            assertThat(r.cantidadCotizaciones()).isZero();
        }

        @Test
        @DisplayName("No se repite el mismo nombre en la misma zona")
        void duplicadoEnLaMismaZona() {
            when(repositorio.buscarPorNombreYZona("Corralón San Martín", "Zona Norte"))
                    .thenReturn(Optional.of(proveedor("Corralón San Martín", "Zona Norte", 1L)));

            assertThatThrownBy(() -> servicio.crear(new ProveedorSolicitud(
                    "Corralón San Martín", "Zona Norte", null, null)))
                    .isInstanceOf(ReglaDeNegocioException.class)
                    .hasMessageContaining("Ya existe un proveedor");

            verify(repositorio, never()).save(any());
        }

        @Test
        @DisplayName("El mismo nombre en OTRA zona sí se permite: son sucursales distintas")
        void mismoNombreEnOtraZona() {
            // Una cadena de corralones puede tener sucursales homónimas, y a
            // efectos de a quién pedirle son proveedores diferentes.
            when(repositorio.buscarPorNombreYZona("Corralón San Martín", "Zona Sur"))
                    .thenReturn(Optional.empty());
            when(repositorio.save(any(Proveedor.class))).thenAnswer(i -> i.getArgument(0));

            assertThat(servicio.crear(new ProveedorSolicitud(
                    "Corralón San Martín", "Zona Sur", null, null)).zonaCobertura())
                    .isEqualTo("Zona Sur");
        }

        @Test
        @DisplayName("Al editar, el propio proveedor no cuenta como duplicado")
        void editarASiMismo() {
            Proveedor existente = proveedor("Corralón San Martín", "Zona Norte", 1L);
            when(repositorio.findById(1L)).thenReturn(Optional.of(existente));
            when(repositorio.buscarPorNombreYZona("Corralón San Martín", "Zona Norte"))
                    .thenReturn(Optional.of(existente));

            assertThat(servicio.actualizar(1L, new ProveedorSolicitud(
                    "Corralón San Martín", "Zona Norte", "11 9999-0000", null))
                    .telefonoContacto())
                    .isEqualTo("11 9999-0000");
        }
    }

    // ==================================================================

    @Nested
    @DisplayName("Cotizaciones")
    class Cotizaciones {

        @Test
        @DisplayName("Registrar una cotización guarda el precio con su fecha")
        void registra() {
            Proveedor p = proveedor("Corralón San Martín", "Zona Norte", 1L);
            when(repositorio.findById(1L)).thenReturn(Optional.of(p));
            when(materialRepositorio.findById(5L))
                    .thenReturn(Optional.of(material("Cemento CP40", 5L)));
            when(cotizacionRepositorio.save(any(Cotizacion.class)))
                    .thenAnswer(i -> i.getArgument(0));

            CotizacionRespuesta r = servicio.registrarCotizacion(
                    1L, new CotizacionSolicitud(5L, new BigDecimal("18500.00")));

            assertThat(r.precioCotizado()).isEqualByComparingTo("18500.00");
            assertThat(r.nombreMaterial()).isEqualTo("Cemento CP40");
            assertThat(r.fechaCotizacion()).isNotNull();
        }

        @Test
        @DisplayName("Una cotización nueva NO reemplaza a la anterior: se agrega")
        void noReemplazaLaAnterior() {
            Proveedor p = proveedor("Corralón San Martín", "Zona Norte", 1L);
            when(repositorio.findById(1L)).thenReturn(Optional.of(p));
            when(materialRepositorio.findById(5L))
                    .thenReturn(Optional.of(material("Cemento CP40", 5L)));
            when(cotizacionRepositorio.save(any(Cotizacion.class)))
                    .thenAnswer(i -> i.getArgument(0));

            servicio.registrarCotizacion(1L, new CotizacionSolicitud(5L, new BigDecimal("18500")));
            servicio.registrarCotizacion(1L, new CotizacionSolicitud(5L, new BigDecimal("21000")));

            // Dos altas, ningún borrado: queda la evolución del precio, que en
            // contexto inflacionario es información útil por sí misma.
            verify(cotizacionRepositorio, org.mockito.Mockito.times(2)).save(any(Cotizacion.class));
            verify(cotizacionRepositorio, never()).delete(any());
        }

        @Test
        @DisplayName("No se le registran cotizaciones a un proveedor inactivo")
        void proveedorInactivo() {
            Proveedor p = proveedor("Corralón Viejo", "Zona Oeste", 1L);
            p.desactivar();
            when(repositorio.findById(1L)).thenReturn(Optional.of(p));

            assertThatThrownBy(() -> servicio.registrarCotizacion(
                    1L, new CotizacionSolicitud(5L, new BigDecimal("100"))))
                    .isInstanceOf(ReglaDeNegocioException.class)
                    .hasMessageContaining("esta inactivo");
        }

        @Test
        @DisplayName("No se cotiza un material que no existe")
        void materialInexistente() {
            when(repositorio.findById(1L))
                    .thenReturn(Optional.of(proveedor("Corralón", "Norte", 1L)));
            when(materialRepositorio.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> servicio.registrarCotizacion(
                    1L, new CotizacionSolicitud(99L, new BigDecimal("100"))))
                    .isInstanceOf(RecursoNoEncontradoException.class)
                    .hasMessageContaining("Material");
        }
    }

    // ==================================================================

    @Nested
    @DisplayName("Comparador")
    class Comparador {

        @Test
        @DisplayName("Devuelve las cotizaciones ordenadas de menor a mayor precio")
        void ordenadasPorPrecio() {
            Material cemento = material("Cemento CP40", 5L);
            when(materialRepositorio.existsById(5L)).thenReturn(true);
            when(cotizacionRepositorio.comparar(5L)).thenReturn(List.of(
                    new Cotizacion(proveedor("Corralón Sur", "Zona Sur", 2L), cemento,
                            new BigDecimal("17800")),
                    new Cotizacion(proveedor("Corralón San Martín", "Zona Norte", 1L), cemento,
                            new BigDecimal("18500"))));

            List<CotizacionRespuesta> r = servicio.compararPorMaterial(5L);

            assertThat(r).hasSize(2);
            assertThat(r.get(0).precioCotizado()).isEqualByComparingTo("17800");
            assertThat(r.get(0).nombreProveedor()).isEqualTo("Corralón Sur");
        }

        @Test
        @DisplayName("Un material sin cotizaciones devuelve lista vacía, no error")
        void sinCotizaciones() {
            when(materialRepositorio.existsById(5L)).thenReturn(true);
            when(cotizacionRepositorio.comparar(5L)).thenReturn(List.of());

            assertThat(servicio.compararPorMaterial(5L)).isEmpty();
        }

        @Test
        @DisplayName("Un material inexistente devuelve 404, no lista vacía")
        void materialInexistente() {
            when(materialRepositorio.existsById(99L)).thenReturn(false);

            // Distinguir los dos casos importa: "nadie lo cotizó" y "ese
            // material no existe" son problemas distintos.
            assertThatThrownBy(() -> servicio.compararPorMaterial(99L))
                    .isInstanceOf(RecursoNoEncontradoException.class);
        }
    }

    // ==================================================================

    @Nested
    @DisplayName("Observaciones y estado")
    class Observaciones {

        /**
         * Sin esta comprobación se podía colgar una queja de un pedido
         * inexistente, o peor, del pedido de OTRO proveedor: el historial de
         * comportamiento que el dueño usa para decidir a quién comprarle
         * quedaría contaminado.
         */
        @Test
        @DisplayName("Una observación sobre un pedido que no existe se rechaza")
        void pedidoInexistente() {
            when(repositorio.findById(1L))
                    .thenReturn(Optional.of(proveedor("Corralón", "Norte", 1L)));
            when(pedidoRepositorio.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> servicio.registrarObservacion(1L,
                    new ObservacionSolicitud("Cualquier cosa", 99L)))
                    .isInstanceOf(com.sigco.common.exception.RecursoNoEncontradoException.class);
        }

        @Test
        @DisplayName("Una observación sobre el pedido de OTRO proveedor se rechaza")
        void pedidoDeOtroProveedor() {
            when(repositorio.findById(1L))
                    .thenReturn(Optional.of(proveedor("Corralón", "Norte", 1L)));
            conPedidoDelProveedor(42L, 7L);   // el pedido es del proveedor 7

            assertThatThrownBy(() -> servicio.registrarObservacion(1L,
                    new ObservacionSolicitud("Demoró la entrega", 42L)))
                    .isInstanceOf(ReglaDeNegocioException.class)
                    .hasMessageContaining("no corresponde a este proveedor");
        }

        @Test
        @DisplayName("Sin pedido asociado la observación se guarda igual")
        void sinPedidoTambienSeGuarda() {
            when(repositorio.findById(1L))
                    .thenReturn(Optional.of(proveedor("Corralón", "Norte", 1L)));
            when(observacionRepositorio.save(any(ObservacionProveedor.class)))
                    .thenAnswer(i -> i.getArgument(0));

            // No toda queja nace de un pedido: puede ser "me atendió mal por
            // teléfono". Por eso el vínculo es opcional.
            var r = servicio.registrarObservacion(1L,
                    new ObservacionSolicitud("Atendió de mala manera", null));

            assertThat(r.idPedido()).isNull();
        }

        /** Declara un pedido existente y de quién es. */
        private void conPedidoDelProveedor(Long idPedido, Long idProveedor) {
            var pedido = org.mockito.Mockito.mock(com.sigco.compras.Pedido.class);
            var prov = proveedor("Quien sea", "Norte", idProveedor);
            org.mockito.Mockito.when(pedido.getProveedor()).thenReturn(prov);
            when(pedidoRepositorio.findById(idPedido)).thenReturn(Optional.of(pedido));
        }

        @Test
        @DisplayName("Una observación se guarda con su fecha y el pedido que la originó")
        void registraObservacion() {
            when(repositorio.findById(1L))
                    .thenReturn(Optional.of(proveedor("Corralón", "Norte", 1L)));
            when(observacionRepositorio.save(any(ObservacionProveedor.class)))
                    .thenAnswer(i -> i.getArgument(0));
            conPedidoDelProveedor(42L, 1L);

            var r = servicio.registrarObservacion(1L, new ObservacionSolicitud(
                    "Demoró una semana la entrega de hierro", 42L));

            assertThat(r.descripcion()).isEqualTo("Demoró una semana la entrega de hierro");
            assertThat(r.idPedido()).isEqualTo(42L);
            assertThat(r.fecha()).isNotNull();
        }

        @Test
        @DisplayName("Desactivar conserva el proveedor y su historial")
        void desactivarConserva() {
            Proveedor p = proveedor("Corralón San Martín", "Zona Norte", 1L);
            when(repositorio.findById(1L)).thenReturn(Optional.of(p));
            lenient().when(cotizacionRepositorio.countByProveedorIdProveedor(1L)).thenReturn(12L);

            ProveedorRespuesta r = servicio.cambiarEstado(1L, Proveedor.ESTADO_INACTIVO);

            assertThat(r.estado()).isEqualTo(Proveedor.ESTADO_INACTIVO);
            // El historial de precios y comportamiento es justamente lo que el
            // módulo viene a conservar: nada se borra.
            assertThat(r.cantidadCotizaciones()).isEqualTo(12L);
            verify(repositorio, never()).delete(any());
        }

        @Test
        @DisplayName("Pedir un proveedor inexistente devuelve el error que se traduce a 404")
        void inexistente() {
            when(repositorio.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> servicio.obtener(999L))
                    .isInstanceOf(RecursoNoEncontradoException.class)
                    .hasMessageContaining("Proveedor");
        }
    }
}
