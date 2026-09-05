package com.sigco.personal;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OperarioRepository extends JpaRepository<Operario, Long> {

    /**
     * Listado con buscador, filtro por estado y por obra asignada.
     *
     * NINGUN PARAMETRO PUEDE LLEGAR NULO: el patron "(:param IS NULL OR ...)"
     * impide que PostgreSQL deduzca el tipo, le asigna bytea y falla. El
     * servicio traduce "sin filtro" a cadena vacia o 0. Misma correccion que en
     * Clientes, Obras, Compras y Gastos.
     *
     * El filtro por obra mira solo las asignaciones VIGENTES: la pregunta que
     * responde es "quien esta trabajando hoy en esta obra".
     */
    @Query("""
            SELECT DISTINCT o FROM Operario o
            LEFT JOIN o.asignaciones a
            WHERE (:busqueda = '' OR LOWER(o.nombreApellido) LIKE LOWER(CONCAT('%', :busqueda, '%')))
              AND (:estado = '' OR o.estado = :estado)
              AND (:idObra = 0L
                   OR (a.obra.idObra = :idObra AND a.fechaDesasignacion IS NULL))
            ORDER BY o.nombreApellido
            """)
    List<Operario> buscar(@Param("busqueda") String busqueda,
                          @Param("estado") String estado,
                          @Param("idObra") Long idObra);

    /** Un operario con sus asignaciones y las obras, para la ficha. */
    @Query("""
            SELECT DISTINCT o FROM Operario o
            LEFT JOIN FETCH o.asignaciones a
            LEFT JOIN FETCH a.obra
            WHERE o.idOperario = :id
            """)
    Optional<Operario> buscarCompleto(@Param("id") Long id);
}
