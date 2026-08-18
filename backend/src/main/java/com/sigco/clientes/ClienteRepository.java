package com.sigco.clientes;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Acceso a datos de clientes.
 *
 * Spring Data genera la implementacion de esta interfaz en tiempo de ejecucion:
 * no hay que escribir el CRUD. De JpaRepository ya vienen guardar, buscar por
 * id y listar.
 */
public interface ClienteRepository extends JpaRepository<Cliente, Long> {

    /**
     * Busqueda del listado, con los tres filtros del informe combinables entre
     * si: texto del nombre, origen de la recomendacion y estado.
     *
     * Cada condicion se anula sola cuando su parametro llega en null
     * (:parametro IS NULL OR ...), de modo que una sola consulta cubre las ocho
     * combinaciones posibles de filtros sin armar SQL a mano.
     *
     * Usa parametros con nombre, nunca concatenacion de texto: es lo que
     * previene la inyeccion de SQL que menciona el informe.
     */
    @Query("""
            SELECT c FROM Cliente c
            WHERE (:busqueda IS NULL
                   OR LOWER(c.nombreApellido) LIKE LOWER(CONCAT('%', :busqueda, '%')))
              AND (:origen IS NULL OR c.origenRecomendacion = :origen)
              AND (:estado IS NULL OR c.estado = :estado)
            ORDER BY c.nombreApellido ASC
            """)
    List<Cliente> buscar(@Param("busqueda") String busqueda,
                         @Param("origen") String origen,
                         @Param("estado") String estado);
}
