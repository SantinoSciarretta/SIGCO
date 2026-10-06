package com.sigco.presupuestacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sigco.presupuestacion.dto.RubroRespuesta;
import com.sigco.presupuestacion.dto.RubroSolicitud;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Tests de la capa HTTP del catálogo de rubros.
 *
 * Existen por un error que llegó a producción: el formulario de "Nuevo rubro"
 * mandaba solo el nombre, el servidor declaraba la marca de mano de obra como
 * boolean (que no admite vacío) y respondía "Cannot map null into type
 * boolean". No se podía crear ningún rubro. Los tests del servicio no lo
 * detectaban porque arman la solicitud en código, sin pasar por el JSON que
 * manda la pantalla: estos sí pasan por ahí.
 */
@WebMvcTest(CatalogoController.class)
@TestPropertySource(properties = {
        "sigco.cors.origenes-permitidos=http://localhost:5173",
        "sigco.jwt.secreto=clave-de-prueba-suficientemente-larga-para-firmar",
        "sigco.jwt.duracion-segundos=28800",
})
@WithMockUser(authorities = { "presupuestos.ver", "presupuestos.editar" })
class CatalogoControllerTest {

    @MockitoBean
    private com.sigco.seguridad.ServicioJwt servicioJwt;

    @MockitoBean
    private com.sigco.usuarios.UsuarioRepository usuarioRepositorio;

    @MockitoBean
    private CatalogoService servicio;

    @Autowired
    private MockMvc mockMvc;

    private RubroRespuesta rubroDeEjemplo() {
        return new RubroRespuesta(7L, "ELECTRICIDAD", "Activo", false, List.of());
    }

    @Test
    @DisplayName("Un rubro se crea mandando solo el nombre, sin la marca de mano de obra")
    void altaSoloConNombre() throws Exception {
        when(servicio.crearRubro(any())).thenReturn(rubroDeEjemplo());

        mockMvc.perform(post("/api/rubros")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombreRubro\":\"ELECTRICIDAD\"}"))
                .andExpect(status().isCreated());

        ArgumentCaptor<RubroSolicitud> recibida = ArgumentCaptor.forClass(RubroSolicitud.class);
        verify(servicio).crearRubro(recibida.capture());
        assertThat(recibida.getValue().nombreRubro()).isEqualTo("ELECTRICIDAD");
        assertThat(recibida.getValue().esManoDeObra()).isNull();
    }

    @Test
    @DisplayName("Un rubro se renombra mandando solo el nombre")
    void renombrarSoloConNombre() throws Exception {
        when(servicio.renombrarRubro(eq(7L), any())).thenReturn(rubroDeEjemplo());

        mockMvc.perform(put("/api/rubros/7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombreRubro\":\"ELECTRICIDAD\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("La marca de mano de obra se recibe cuando viene")
    void altaConMarcaDeManoDeObra() throws Exception {
        when(servicio.crearRubro(any())).thenReturn(rubroDeEjemplo());

        mockMvc.perform(post("/api/rubros")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombreRubro\":\"Mano de obra\",\"esManoDeObra\":true}"))
                .andExpect(status().isCreated());

        ArgumentCaptor<RubroSolicitud> recibida = ArgumentCaptor.forClass(RubroSolicitud.class);
        verify(servicio).crearRubro(recibida.capture());
        assertThat(recibida.getValue().esManoDeObra()).isTrue();
    }
}
