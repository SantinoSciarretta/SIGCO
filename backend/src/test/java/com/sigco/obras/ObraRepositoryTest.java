package com.sigco.obras;

import static org.assertj.core.api.Assertions.assertThat;

import com.sigco.clientes.Cliente;
import com.sigco.clientes.ClienteRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.TestPropertySource;

/**
 * Tests del listado de obras contra PostgreSQL real.
 *
 * Ademas de los filtros, verifica dos cosas que solo se ven contra una base de
 * verdad: que la clave foranea hacia cliente funciona, y que las restricciones
 * CHECK de la migracion aceptan los valores con acento del Diccionario de Datos
 * ("En presupuestación", "Construcción"), que es donde un problema de
 * codificacion de caracteres se manifestaria.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:5432/sigco_test",
        "spring.jpa.show-sql=false"
})
class ObraRepositoryTest {

    /**
     * Limites que usa el servicio cuando no se indica rango de fechas. La
     * consulta no admite fechas nulas (ver el comentario en el repositorio),
     * asi que los tests las pasan igual que en el uso real.
     */
    private static final LocalDateTime DESDE = LocalDateTime.of(2000, 1, 1, 0, 0);
    private static final LocalDateTime HASTA = LocalDateTime.of(2999, 12, 31, 23, 59);

    @Autowired
    private ObraRepository repositorio;

    @Autowired
    private ClienteRepository clienteRepositorio;

    private Cliente marcela;
    private Cliente rossi;

    /**
     * Busqueda sin acotar por fechas, que es el caso habitual.
     *
     * Traduce "sin filtro" a los valores neutros que espera la consulta: cero
     * para el cliente y cadena vacia para los textos. Ningun parametro puede ir
     * en null (ver el comentario del repositorio).
     */
    private List<Obra> buscar(Long idCliente, String tipoObra, String estado, String busqueda) {
        return repositorio.buscar(
                idCliente != null ? idCliente : 0L,
                tipoObra != null ? tipoObra : "",
                estado != null ? estado : "",
                DESDE, HASTA,
                busqueda != null ? busqueda : "");
    }

    @BeforeEach
    void cargarDatos() {
        marcela = clienteRepositorio.save(
                new Cliente("Marcela Ferrari", null, null, null, null));
        rossi = clienteRepositorio.save(
                new Cliente("Estudio Rossi Arquitectura", null, null, null, null));

        // Reforma en ejecución
        Obra enEjecucion = new Obra(marcela, "Av. Cabildo 2340, Belgrano",
                Obra.INMUEBLE_DEPARTAMENTO, Obra.TIPO_REFORMA, LocalDate.of(2026, 12, 1), null);
        enEjecucion.pasarAEjecucion();
        enEjecucion.registrarInicioReal(LocalDate.of(2026, 3, 4));
        repositorio.save(enEjecucion);

        // Construcción en presupuestación
        repositorio.save(new Obra(rossi, "Gascón 1120, Almagro",
                Obra.INMUEBLE_CASA, Obra.TIPO_CONSTRUCCION, null, null));

        // Reforma cancelada
        Obra cancelada = new Obra(marcela, "Perón 455, Vicente López",
                Obra.INMUEBLE_LOCAL, Obra.TIPO_REFORMA, null, null);
        cancelada.cancelar("El cliente no aceptó el precio");
        repositorio.save(cancelada);
    }

    @Test
    @DisplayName("Sin filtros trae todas las obras, con las activas primero")
    void ordenaLasActivasPrimero() {
        List<Obra> resultado = buscar(null, null, null, null);

        assertThat(resultado).hasSize(3);
        // El informe pide que las obras en ejecución se destaquen de las
        // finalizadas o canceladas: la consulta las ordena primero.
        assertThat(resultado.get(0).getEstado()).isEqualTo(Obra.ESTADO_EN_EJECUCION);
        assertThat(resultado.get(2).getEstado()).isEqualTo(Obra.ESTADO_CANCELADA);
    }

    @Test
    @DisplayName("Los valores con acento del Diccionario se guardan y recuperan bien")
    void valoresConAcento() {
        List<Obra> construcciones = buscar(null, Obra.TIPO_CONSTRUCCION, null, null);

        assertThat(construcciones).hasSize(1);
        assertThat(construcciones.get(0).getTipoObra()).isEqualTo("Construcción");
        assertThat(construcciones.get(0).getEstado()).isEqualTo("En presupuestación");
    }

    @Test
    @DisplayName("Se puede filtrar por cliente, que es lo que usa su ficha")
    void filtraPorCliente() {
        assertThat(buscar(marcela.getIdCliente(), null, null, null))
                .hasSize(2);
        assertThat(buscar(rossi.getIdCliente(), null, null, null))
                .hasSize(1);
    }

    @Test
    @DisplayName("Se puede filtrar por estado")
    void filtraPorEstado() {
        assertThat(buscar(null, null, Obra.ESTADO_EN_EJECUCION, null))
                .hasSize(1);
        assertThat(buscar(null, null, Obra.ESTADO_CANCELADA, null))
                .hasSize(1);
    }

    @Test
    @DisplayName("La búsqueda libre encuentra por dirección y por nombre del cliente")
    void busquedaLibre() {
        assertThat(buscar(null, null, null, "cabildo"))
                .hasSize(1);
        assertThat(buscar(null, null, null, "rossi"))
                .hasSize(1);
    }

    @Test
    @DisplayName("El rango de fechas acota por fecha de creación")
    void filtraPorRangoDeFechas() {
        LocalDateTime hoy = LocalDate.now().atStartOfDay();
        LocalDateTime lejano = LocalDateTime.of(2999, 12, 31, 23, 59);

        assertThat(repositorio.buscar(0L, "", "", hoy, lejano, "")).hasSize(3);
        // Un rango que arranca mañana no puede incluir obras creadas hoy.
        assertThat(repositorio.buscar(0L, "", "", hoy.plusDays(1), lejano, "")).isEmpty();
    }

    @Test
    @DisplayName("El conteo por cliente devuelve una fila por cliente con obras")
    void cuentaObrasPorCliente() {
        assertThat(repositorio.countByClienteIdCliente(marcela.getIdCliente())).isEqualTo(2);
        assertThat(repositorio.countByClienteIdCliente(rossi.getIdCliente())).isEqualTo(1);

        // Una sola consulta para toda la lista de clientes, en lugar de una por
        // cliente.
        assertThat(repositorio.contarPorCliente()).hasSize(2);
    }
}
