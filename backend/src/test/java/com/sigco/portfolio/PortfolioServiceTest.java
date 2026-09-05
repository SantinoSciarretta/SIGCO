package com.sigco.portfolio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.sigco.clientes.Cliente;
import com.sigco.common.exception.ReglaDeNegocioException;
import com.sigco.obras.Obra;
import com.sigco.obras.ObraRepository;
import com.sigco.portfolio.dto.PortfolioDtos.NuevaImagen;
import com.sigco.portfolio.dto.PortfolioDtos.NuevaPublicacion;
import com.sigco.portfolio.dto.PortfolioDtos.PublicacionRespuesta;
import com.sigco.portfolio.dto.PortfolioDtos.VidrieraRespuesta;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Tests del modulo Portfolio Web.
 *
 * El foco esta en las dos reglas que definen el modulo: solo se publican obras
 * finalizadas, y la vidriera no expone datos del cliente.
 */
@ExtendWith(MockitoExtension.class)
class PortfolioServiceTest {

    @Mock private PublicacionPortfolioRepository repositorio;
    @Mock private ObraRepository obraRepositorio;

    @InjectMocks private PortfolioService servicio;

    private static void asignarId(Object entidad, String campo, Long valor) {
        try {
            Field f = entidad.getClass().getDeclaredField(campo);
            f.setAccessible(true);
            f.set(entidad, valor);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private Obra obra(boolean finalizada) {
        Cliente c = new Cliente("Marcela Ferrari", "11-4444-5555", null, null, null);
        asignarId(c, "idCliente", 1L);
        Obra o = new Obra(c, "Av. Cabildo 2340, Belgrano", "Departamento",
                Obra.TIPO_REFORMA, null, null);
        asignarId(o, "idObra", 1L);
        o.pasarAEjecucion();
        if (finalizada) {
            o.finalizar();
        }
        lenient().when(obraRepositorio.findById(1L)).thenReturn(Optional.of(o));
        return o;
    }

    private PublicacionPortfolio publicacionCon(int cantidadImagenes) {
        PublicacionPortfolio p = new PublicacionPortfolio(obra(true), "Refacción");
        asignarId(p, "idPublicacion", 50L);
        for (int i = 0; i < cantidadImagenes; i++) {
            ImagenPortfolio img = new ImagenPortfolio(p, "portfolio/foto-" + i + ".jpg", i);
            asignarId(img, "idImagen", 500L + i);
            p.agregarImagen(img);
        }
        lenient().when(repositorio.buscarCompleta(50L)).thenReturn(Optional.of(p));
        return p;
    }

    // ==================================================================

    @Test
    @DisplayName("Solo se publican obras finalizadas")
    void soloObrasFinalizadas() {
        obra(false);

        assertThatThrownBy(() -> servicio.crear(new NuevaPublicacion(1L, "Refacción")))
                .isInstanceOf(ReglaDeNegocioException.class)
                .hasMessageContaining("Solo se publican en el portfolio obras finalizadas");
    }

    @Test
    @DisplayName("La publicación nace despublicada: primero las fotos")
    void naceDespublicada() {
        obra(true);
        when(repositorio.existsByObraIdObra(1L)).thenReturn(false);
        when(repositorio.save(any(PublicacionPortfolio.class)))
                .thenAnswer(i -> i.getArgument(0));

        PublicacionRespuesta r = servicio.crear(new NuevaPublicacion(1L, "Refacción"));

        // Publicarla al crearla mostraría una obra sin fotos en la vidriera.
        assertThat(r.estado()).isEqualTo(PublicacionPortfolio.ESTADO_DESPUBLICADA);
        assertThat(r.cantidadImagenes()).isZero();
    }

    @Test
    @DisplayName("No se publica una galería vacía")
    void noSePublicaSinImagenes() {
        publicacionCon(0);

        assertThatThrownBy(() -> servicio.publicar(50L))
                .isInstanceOf(ReglaDeNegocioException.class)
                .hasMessageContaining("no tiene ninguna imagen");
    }

    @Test
    @DisplayName("Publicar con imágenes deja la obra en la vidriera")
    void publicaConImagenes() {
        publicacionCon(2);

        PublicacionRespuesta r = servicio.publicar(50L);

        assertThat(r.estado()).isEqualTo(PublicacionPortfolio.ESTADO_PUBLICADA);
        assertThat(r.cantidadImagenes()).isEqualTo(2);
    }

    @Test
    @DisplayName("Despublicar conserva las imágenes")
    void despublicarConservaImagenes() {
        PublicacionPortfolio p = publicacionCon(3);
        p.publicar();

        PublicacionRespuesta r = servicio.despublicar(50L);

        // Regla del informe: se puede volver a publicar sin recargar las fotos.
        assertThat(r.estado()).isEqualTo(PublicacionPortfolio.ESTADO_DESPUBLICADA);
        assertThat(r.cantidadImagenes()).isEqualTo(3);
    }

    @Test
    @DisplayName("Las imágenes se agregan al final de la galería")
    void imagenesEnOrden() {
        publicacionCon(2);

        PublicacionRespuesta r = servicio.agregarImagen(
                50L, new NuevaImagen("portfolio/nueva.jpg"));

        assertThat(r.imagenes()).hasSize(3);
        assertThat(r.imagenes().get(2).orden()).isEqualTo(2);
    }

    @Test
    @DisplayName("No se quita la única imagen de una publicación activa")
    void noSeVaciaUnaPublicacionActiva() {
        PublicacionPortfolio p = publicacionCon(1);
        p.publicar();

        assertThatThrownBy(() -> servicio.quitarImagen(50L, 500L))
                .isInstanceOf(ReglaDeNegocioException.class)
                .hasMessageContaining("Despublicá la obra antes");
    }

    @Test
    @DisplayName("La vidriera NO expone datos del cliente ni la dirección")
    void vidrieraNoExponeDatosDelCliente() {
        PublicacionPortfolio p = publicacionCon(2);
        p.publicar();
        when(repositorio.publicadas()).thenReturn(List.of(p));

        List<VidrieraRespuesta> vidriera = servicio.vidriera(null);

        // La restricción vive en el TIPO: VidrieraRespuesta no tiene campos de
        // cliente ni de dirección, así que no hay forma de filtrarlos por
        // accidente. Una validación se puede olvidar; un campo que no existe, no.
        assertThat(vidriera).hasSize(1);
        assertThat(vidriera.get(0).tipoTrabajo()).isEqualTo("Refacción");
        assertThat(vidriera.get(0).imagenes()).hasSize(2);

        List<String> campos = List.of(VidrieraRespuesta.class.getRecordComponents())
                .stream().map(java.lang.reflect.RecordComponent::getName).toList();
        assertThat(campos).doesNotContain("nombreCliente", "direccionObra", "idObra");
    }
}
