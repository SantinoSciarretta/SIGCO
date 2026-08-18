package com.sigco.clientes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sigco.clientes.dto.ClienteRespuesta;
import com.sigco.clientes.dto.ClienteSolicitud;
import com.sigco.common.exception.RecursoNoEncontradoException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Tests de las reglas de negocio del modulo Clientes.
 *
 * No tocan la base de datos: el repositorio esta reemplazado por un doble
 * (@Mock) al que se le indica que devolver. Eso permite probar solo las
 * decisiones del servicio, sin arrastrar consultas SQL ni tiempos de conexion.
 */
@ExtendWith(MockitoExtension.class)
class ClienteServiceTest {

    @Mock
    private ClienteRepository repositorio;

    @InjectMocks
    private ClienteService servicio;

    /** Devuelve la entidad tal cual se la paso, imitando a save(). */
    private void devolverLoQueSeGuarda() {
        when(repositorio.save(any(Cliente.class)))
                .thenAnswer(invocacion -> invocacion.getArgument(0));
    }

    // ------------------------------------------------------------------
    //  Alta
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Un cliente nuevo nace Activo y con la fecha de alta del momento")
    void altaNaceActivaConFecha() {
        devolverLoQueSeGuarda();
        LocalDateTime antes = LocalDateTime.now();

        ClienteRespuesta respuesta = servicio.crear(new ClienteSolicitud(
                "Marcela Ferrari", "11 4023-7788", "marcela@ejemplo.com",
                "Cliente anterior", "Hernan Dominguez"));

        assertThat(respuesta.estado()).isEqualTo(Cliente.ESTADO_ACTIVO);
        assertThat(respuesta.fechaAlta()).isNotNull();
        assertThat(respuesta.fechaAlta()).isAfterOrEqualTo(antes);
    }

    @Test
    @DisplayName("Los espacios sobrantes se recortan y los campos vacios se guardan como nulos")
    void altaNormalizaLosTextos() {
        devolverLoQueSeGuarda();

        servicio.crear(new ClienteSolicitud(
                "  Estudio Rossi  ", "   ", "", null, "  Arq. Rossi  "));

        ArgumentCaptor<Cliente> capturado = ArgumentCaptor.forClass(Cliente.class);
        verify(repositorio).save(capturado.capture());
        Cliente guardado = capturado.getValue();

        assertThat(guardado.getNombreApellido()).isEqualTo("Estudio Rossi");
        // "sin dato" y "dato vacio" son lo mismo: se guarda de una sola manera.
        assertThat(guardado.getTelefonoContacto()).isNull();
        assertThat(guardado.getEmailContacto()).isNull();
        assertThat(guardado.getRecomendadoPor()).isEqualTo("Arq. Rossi");
    }

    // ------------------------------------------------------------------
    //  Consulta
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Pedir un cliente que no existe devuelve el error que se traduce a 404")
    void consultaInexistenteFalla() {
        when(repositorio.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.obtener(999L))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessageContaining("Cliente")
                .hasMessageContaining("999");
    }

    @Test
    @DisplayName("Un filtro vacio se trata igual que un filtro ausente")
    void filtroVacioNoFiltra() {
        when(repositorio.buscar(null, null, null)).thenReturn(List.of());

        servicio.listar("   ", "", null);

        // Si el usuario borra lo que escribio en el buscador espera ver todo,
        // no una lista vacia.
        verify(repositorio).buscar(null, null, null);
    }

    // ------------------------------------------------------------------
    //  Edicion
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Editar los datos de contacto no cambia el estado ni la fecha de alta")
    void edicionNoTocaEstadoNiFechaAlta() {
        Cliente existente = new Cliente("Lucia Peralta", "11 6612-8095",
                null, "Otro", null);
        existente.desactivar();
        LocalDateTime fechaOriginal = existente.getFechaAlta();
        when(repositorio.findById(1L)).thenReturn(Optional.of(existente));

        ClienteRespuesta respuesta = servicio.actualizar(1L, new ClienteSolicitud(
                "Lucia Peralta", "11 0000-0000", "lucia@ejemplo.com", "Arquitecto", "Estudio Rossi"));

        assertThat(respuesta.telefonoContacto()).isEqualTo("11 0000-0000");
        assertThat(respuesta.origenRecomendacion()).isEqualTo("Arquitecto");
        assertThat(respuesta.estado()).isEqualTo(Cliente.ESTADO_INACTIVO);
        assertThat(respuesta.fechaAlta()).isEqualTo(fechaOriginal);
    }

    @Test
    @DisplayName("Editar un cliente inexistente falla en lugar de crear uno nuevo")
    void edicionDeInexistenteFalla() {
        when(repositorio.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.actualizar(404L, new ClienteSolicitud(
                "Alguien", null, null, null, null)))
                .isInstanceOf(RecursoNoEncontradoException.class);

        verify(repositorio, never()).save(any());
    }

    // ------------------------------------------------------------------
    //  Estado
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Marcar como inactivo conserva el registro y su historial")
    void desactivarConservaElRegistro() {
        Cliente existente = new Cliente("Hernan Dominguez", null, null, null, null);
        when(repositorio.findById(1L)).thenReturn(Optional.of(existente));

        ClienteRespuesta respuesta = servicio.cambiarEstado(1L, Cliente.ESTADO_INACTIVO);

        assertThat(respuesta.estado()).isEqualTo(Cliente.ESTADO_INACTIVO);
        assertThat(respuesta.nombreApellido()).isEqualTo("Hernan Dominguez");
        // El informe es explicito: el cliente no se borra. El servicio no
        // expone ninguna operacion de eliminacion.
        verify(repositorio, never()).delete(any());
    }

    @Test
    @DisplayName("Un cliente inactivo se puede volver a activar")
    void reactivar() {
        Cliente existente = new Cliente("Marcela Ferrari", null, null, null, null);
        existente.desactivar();
        when(repositorio.findById(1L)).thenReturn(Optional.of(existente));

        ClienteRespuesta respuesta = servicio.cambiarEstado(1L, Cliente.ESTADO_ACTIVO);

        assertThat(respuesta.estado()).isEqualTo(Cliente.ESTADO_ACTIVO);
    }
}
