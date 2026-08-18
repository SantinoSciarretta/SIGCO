package com.sigco.clientes;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sigco.clientes.dto.ClienteRespuesta;
import com.sigco.clientes.dto.ClienteSolicitud;
import com.sigco.common.exception.RecursoNoEncontradoException;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
// En Spring Boot 4 esta anotacion vive en otro paquete que en la 3.x.
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Tests de la capa HTTP del modulo Clientes.
 *
 * Levantan solo el controlador, con el servicio reemplazado por un doble. Lo
 * que se prueba aca no es la logica de negocio (eso ya lo cubre
 * ClienteServiceTest) sino el contrato de la API: que codigo HTTP devuelve cada
 * situacion y con que forma sale el cuerpo de la respuesta.
 *
 * Verifica en particular dos cosas que son faciles de romper sin darse cuenta:
 * que la validacion del servidor efectivamente se ejecuta, y que los errores
 * salen con el formato unico del sistema.
 */
@WebMvcTest(ClienteController.class)
@TestPropertySource(properties = "sigco.cors.origenes-permitidos=http://localhost:5173")
class ClienteControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ClienteService servicio;

    private ClienteRespuesta clienteDeEjemplo() {
        return new ClienteRespuesta(1L, "Marcela Ferrari", "11 4023-7788",
                "marcela@ejemplo.com", "Cliente anterior", "Hernan Dominguez",
                "Activo", LocalDateTime.now());
    }

    @Test
    @DisplayName("El alta devuelve 201 con la direccion del cliente creado")
    void altaDevuelve201() throws Exception {
        when(servicio.crear(any())).thenReturn(clienteDeEjemplo());

        mockMvc.perform(post("/api/clientes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombreApellido": "Marcela Ferrari",
                                 "telefonoContacto": "11 4023-7788",
                                 "emailContacto": "marcela@ejemplo.com",
                                 "origenRecomendacion": "Cliente anterior",
                                 "recomendadoPor": "Hernan Dominguez"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/clientes/1"))
                .andExpect(jsonPath("$.nombreApellido").value("Marcela Ferrari"))
                .andExpect(jsonPath("$.estado").value("Activo"));
    }

    @Test
    @DisplayName("Un alta sin nombre se rechaza con 400 y el detalle del campo")
    void altaSinNombreEsRechazada() throws Exception {
        mockMvc.perform(post("/api/clientes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombreApellido": "  "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.estado").value(400))
                .andExpect(jsonPath("$.camposInvalidos.nombreApellido")
                        .value("El nombre del cliente es obligatorio"));

        // La validacion corta antes de llegar al servicio: nunca se intenta
        // guardar un cliente sin nombre.
        verify(servicio, never()).crear(any(ClienteSolicitud.class));
    }

    @Test
    @DisplayName("Un correo con formato invalido se rechaza con 400")
    void correoInvalidoEsRechazado() throws Exception {
        mockMvc.perform(post("/api/clientes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombreApellido": "Marcela Ferrari",
                                 "emailContacto": "esto-no-es-un-correo"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.camposInvalidos.emailContacto").exists());
    }

    @Test
    @DisplayName("Un origen fuera del conjunto permitido se rechaza con 400")
    void origenInvalidoEsRechazado() throws Exception {
        mockMvc.perform(post("/api/clientes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombreApellido": "Marcela Ferrari",
                                 "origenRecomendacion": "Instagram"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.camposInvalidos.origenRecomendacion").exists());
    }

    @Test
    @DisplayName("Pedir un cliente inexistente devuelve 404 con el formato de error del sistema")
    void inexistenteDevuelve404() throws Exception {
        when(servicio.obtener(999L))
                .thenThrow(new RecursoNoEncontradoException("Cliente", 999L));

        mockMvc.perform(get("/api/clientes/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.estado").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.ruta").value("/api/clientes/999"));
    }

    @Test
    @DisplayName("Los filtros del listado llegan al servicio tal como vienen en la direccion")
    void filtrosLleganAlServicio() throws Exception {
        when(servicio.listar("rossi", "Arquitecto", "Activo")).thenReturn(java.util.List.of());

        mockMvc.perform(get("/api/clientes")
                        .param("busqueda", "rossi")
                        .param("origen", "Arquitecto")
                        .param("estado", "Activo"))
                .andExpect(status().isOk());

        verify(servicio).listar("rossi", "Arquitecto", "Activo");
    }

    /** Import estatico local, para no cargar toda la clase de matchers. */
    private static org.springframework.test.web.servlet.result.HeaderResultMatchers header() {
        return org.springframework.test.web.servlet.result.MockMvcResultMatchers.header();
    }
}
