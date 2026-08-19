package com.sigco.clientes;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
// OJO: en Spring Boot 4 estas anotaciones cambiaron de paquete respecto de
// la 3.x. Los ejemplos que se encuentran en internet suelen traer los viejos
// (org.springframework.boot.test.autoconfigure.*), que ya no existen.
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.TestPropertySource;

/**
 * Tests de la consulta del listado contra una base PostgreSQL real.
 *
 * Se usa una base aparte (sigco_test) y no la de desarrollo, para que las
 * pruebas no ensucien los datos con los que se trabaja. Flyway crea el esquema
 * en esa base ejecutando las mismas migraciones que en desarrollo y produccion.
 *
 * Por que contra PostgreSQL de verdad y no contra una base en memoria: la
 * consulta usa LOWER y LIKE, la migracion usa BIGSERIAL y restricciones CHECK.
 * Una base distinta podria comportarse distinto y dar una prueba que pasa
 * mientras el sistema real falla.
 *
 * Ademas, este test verifica algo que ninguna asercion escribe explicitamente:
 * como la aplicacion corre con ddl-auto=validate, si la entidad Cliente dejara
 * de coincidir con V1__cliente.sql el contexto no levantaria y todos los tests
 * de esta clase fallarian. Es un control automatico de que el codigo sigue
 * fiel al Diccionario de Datos.
 *
 * Cada test corre dentro de una transaccion que se deshace al terminar, asi que
 * los datos que se cargan aca no quedan en la base.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:5432/sigco_test",
        "spring.jpa.show-sql=false"
})
class ClienteRepositoryTest {

    @Autowired
    private ClienteRepository repositorio;

    /**
     * Traduce "sin filtro" a la cadena vacia que espera la consulta. Ningun
     * parametro puede ir en null (ver el comentario del repositorio).
     */
    private List<Cliente> buscar(String busqueda, String origen, String estado) {
        return repositorio.buscar(
                busqueda != null ? busqueda : "",
                origen != null ? origen : "",
                estado != null ? estado : "");
    }

    @BeforeEach
    void cargarClientes() {
        repositorio.save(new Cliente("Marcela Ferrari", "11 4023-7788",
                "marcela@ejemplo.com", Cliente.ORIGEN_CLIENTE_ANTERIOR, "Hernan Dominguez"));

        repositorio.save(new Cliente("Estudio Rossi Arquitectura", "11 5567-2140",
                "contacto@rossi.com", Cliente.ORIGEN_ARQUITECTO, null));

        Cliente inactivo = new Cliente("Lucia Peralta", "11 6612-8095",
                null, Cliente.ORIGEN_OTRO, null);
        inactivo.desactivar();
        repositorio.save(inactivo);
    }

    @Test
    @DisplayName("Sin filtros devuelve todos los clientes ordenados por nombre")
    void sinFiltrosDevuelveTodo() {
        List<Cliente> resultado = buscar(null, null, null);

        assertThat(resultado).hasSize(3);
        assertThat(resultado).extracting(Cliente::getNombreApellido)
                .containsExactly("Estudio Rossi Arquitectura", "Lucia Peralta", "Marcela Ferrari");
    }

    @Test
    @DisplayName("La busqueda por nombre no distingue mayusculas ni pide el nombre completo")
    void busquedaParcialSinDistinguirMayusculas() {
        assertThat(buscar("ROSSI", null, null))
                .extracting(Cliente::getNombreApellido)
                .containsExactly("Estudio Rossi Arquitectura");

        assertThat(buscar("ferrari", null, null))
                .extracting(Cliente::getNombreApellido)
                .containsExactly("Marcela Ferrari");
    }

    @Test
    @DisplayName("Se puede filtrar por origen de la recomendacion")
    void filtraPorOrigen() {
        List<Cliente> resultado = buscar(null, Cliente.ORIGEN_ARQUITECTO, null);

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).getNombreApellido()).isEqualTo("Estudio Rossi Arquitectura");
    }

    @Test
    @DisplayName("Se puede filtrar por estado")
    void filtraPorEstado() {
        assertThat(buscar(null, null, Cliente.ESTADO_ACTIVO)).hasSize(2);
        assertThat(buscar(null, null, Cliente.ESTADO_INACTIVO)).hasSize(1);
    }

    @Test
    @DisplayName("Los filtros se combinan entre si")
    void filtrosCombinados() {
        List<Cliente> resultado = buscar(
                "a", Cliente.ORIGEN_CLIENTE_ANTERIOR, Cliente.ESTADO_ACTIVO);

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).getNombreApellido()).isEqualTo("Marcela Ferrari");
    }

    @Test
    @DisplayName("Una busqueda sin coincidencias devuelve la lista vacia, no un error")
    void busquedaSinResultados() {
        assertThat(buscar("no existe ningun cliente asi", null, null)).isEmpty();
    }
}
