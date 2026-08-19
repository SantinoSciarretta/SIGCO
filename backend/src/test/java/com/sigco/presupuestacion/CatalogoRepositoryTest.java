package com.sigco.presupuestacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.TestPropertySource;

/**
 * Tests del catalogo contra PostgreSQL real.
 *
 * Verifican tres cosas que solo se ven contra una base de verdad: que los
 * indices unicos hacen cumplir la unicidad aunque la aplicacion fallara en
 * comprobarla, que la consulta con LEFT JOIN FETCH devuelve cada rubro una sola
 * vez, y que los subrubros vienen en el orden correcto.
 *
 * IMPORTANTE — por que se limpia el contexto de persistencia:
 *
 * Hibernate mantiene en memoria las entidades de la transaccion en curso. Si
 * los datos de prueba se cargan y despues se consulta sin limpiar, la consulta
 * devuelve ESAS MISMAS instancias, con sus colecciones tal como las dejo el
 * codigo Java, y no lo que realmente hay en la base. El test pasaria o fallaria
 * por el orden en que se escribieron los datos de prueba, no por lo que hace la
 * consulta.
 *
 * Llamando a flush() y clear() al terminar de preparar los datos, se fuerza a
 * que todo se escriba y a que las entidades se lean de nuevo desde la base, que
 * es lo que ocurre en produccion, donde cada peticion abre su propia
 * transaccion.
 *
 * Como consecuencia, los objetos cargados en la preparacion quedan
 * desvinculados: los tests guardan los identificadores y vuelven a buscar la
 * entidad cuando necesitan modificarla.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:5432/sigco_test",
        "spring.jpa.show-sql=false"
})
class CatalogoRepositoryTest {

    @Autowired
    private RubroRepository rubroRepositorio;

    @Autowired
    private SubrubroRepository subrubroRepositorio;

    @Autowired
    private EntityManager entityManager;

    private Long idAlbanileria;
    private Long idPlomeria;

    /** Busqueda sin filtros: cadena vacia, nunca null. */
    private List<Rubro> buscar(String busqueda, String estado) {
        return rubroRepositorio.buscarConSubrubros(
                busqueda != null ? busqueda : "",
                estado != null ? estado : "");
    }

    @BeforeEach
    void cargarCatalogo() {
        Rubro albanileria = rubroRepositorio.save(new Rubro("Albanileria"));
        Rubro plomeria = rubroRepositorio.save(new Rubro("Plomeria"));

        // A proposito NO en orden alfabetico: si la consulta no ordenara, el
        // test lo detecta.
        subrubroRepositorio.save(new Subrubro(albanileria, "Demolicion"));
        subrubroRepositorio.save(new Subrubro(albanileria, "Contrapisos"));
        subrubroRepositorio.save(new Subrubro(albanileria, "Revoques"));
        subrubroRepositorio.save(new Subrubro(plomeria, "Desagues"));

        // Un rubro sin subrubros, para comprobar que el LEFT JOIN no lo pierde.
        rubroRepositorio.save(new Rubro("Pintura"));

        idAlbanileria = albanileria.getIdRubro();
        idPlomeria = plomeria.getIdRubro();

        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("Cada rubro aparece una sola vez, aunque tenga varios subrubros")
    void sinFilasRepetidas() {
        List<Rubro> resultado = buscar(null, null);

        // Sin DISTINCT, Albanileria apareceria tres veces: una por subrubro.
        assertThat(resultado).hasSize(3);
        assertThat(resultado).extracting(Rubro::getNombreRubro)
                .containsExactly("Albanileria", "Pintura", "Plomeria");
    }

    @Test
    @DisplayName("Un rubro sin subrubros igual aparece en el catalogo")
    void rubroSinSubrubrosNoSePierde() {
        List<Rubro> resultado = buscar("pintura", null);

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).getSubrubros()).isEmpty();
    }

    @Test
    @DisplayName("Los subrubros vienen cargados y ordenados por nombre")
    void subrubrosCargadosYOrdenados() {
        List<Rubro> resultado = buscar("albanileria", null);

        // El orden lo pone el ORDER BY de la consulta. La anotacion @OrderBy de
        // la entidad no alcanza: no se aplica cuando la coleccion se trae con
        // JOIN FETCH.
        assertThat(resultado.get(0).getSubrubros())
                .extracting(Subrubro::getNombreSubrubro)
                .containsExactly("Contrapisos", "Demolicion", "Revoques");
    }

    @Test
    @DisplayName("La base impide dos rubros con el mismo nombre sin distinguir mayusculas")
    void indiceUnicoDeRubro() {
        // La aplicacion lo comprueba antes, pero el indice es la garantia que
        // cubre tambien una carga manual o un script externo.
        assertThatThrownBy(() -> {
            rubroRepositorio.save(new Rubro("ALBANILERIA"));
            entityManager.flush();
        }).isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("El mismo nombre de subrubro puede existir en rubros distintos")
    void mismoSubrubroEnRubrosDistintos() {
        Rubro plomeria = rubroRepositorio.findById(idPlomeria).orElseThrow();

        // "Demolicion" en Albanileria y "Demolicion" en Plomeria son trabajos
        // diferentes: la unicidad es por rubro, no global.
        subrubroRepositorio.save(new Subrubro(plomeria, "Demolicion"));
        entityManager.flush();

        assertThat(subrubroRepositorio.buscarDisponibles(idPlomeria)).hasSize(2);
    }

    @Test
    @DisplayName("Los subrubros disponibles excluyen los de un rubro inactivo")
    void disponiblesExcluyenRubroInactivo() {
        assertThat(subrubroRepositorio.buscarDisponibles(0L)).hasSize(4);

        rubroRepositorio.findById(idPlomeria).orElseThrow().desactivar();
        entityManager.flush();

        // Desactivar el rubro saca de circulacion a sus subrubros sin haberles
        // tocado el estado: es la consulta la que exige que el rubro este
        // activo.
        assertThat(subrubroRepositorio.buscarDisponibles(0L)).hasSize(3);
    }

    @Test
    @DisplayName("Se puede filtrar el catalogo por estado")
    void filtraPorEstado() {
        rubroRepositorio.findById(idPlomeria).orElseThrow().desactivar();
        entityManager.flush();

        assertThat(buscar(null, Rubro.ESTADO_ACTIVO)).hasSize(2);
        assertThat(buscar(null, Rubro.ESTADO_INACTIVO)).hasSize(1);
    }

    @Test
    @DisplayName("El catalogo trae los subrubros de cada rubro, no mezclados")
    void cadaRubroConLosSuyos() {
        List<Rubro> resultado = buscar(null, null);

        assertThat(resultado.get(0).getSubrubros()).hasSize(3);  // Albanileria
        assertThat(resultado.get(1).getSubrubros()).isEmpty();   // Pintura
        assertThat(resultado.get(2).getSubrubros())              // Plomeria
                .extracting(Subrubro::getNombreSubrubro)
                .containsExactly("Desagues");

        assertThat(idAlbanileria).isNotNull();
    }
}
