package com.sigco.gastos;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GastoRepository extends JpaRepository<Gasto, Long> {

    /**
     * Listado con filtros combinables.
     *
     * NINGUN PARAMETRO PUEDE LLEGAR NULO: el patron "(:param IS NULL OR ...)"
     * impide que PostgreSQL deduzca el tipo, le asigna bytea y falla. El
     * servicio traduce "sin filtro" a 0, cadena vacia o un rango de fechas
     * amplio. Misma correccion que en Clientes, Obras y Compras.
     */
    @Query("""
            SELECT g FROM Gasto g
            JOIN FETCH g.rubro r
            LEFT JOIN FETCH g.subrubro
            JOIN FETCH g.obra o
            WHERE (:idObra = 0L OR o.idObra = :idObra)
              AND (:idRubro = 0L OR r.idRubro = :idRubro)
              AND (:tipoGasto = '' OR g.tipoGasto = :tipoGasto)
              AND (:estado = '' OR g.estado = :estado)
              AND g.fechaGasto BETWEEN :desde AND :hasta
            ORDER BY g.fechaGasto DESC, g.idGasto DESC
            """)
    List<Gasto> buscar(@Param("idObra") Long idObra,
                       @Param("idRubro") Long idRubro,
                       @Param("tipoGasto") String tipoGasto,
                       @Param("estado") String estado,
                       @Param("desde") LocalDate desde,
                       @Param("hasta") LocalDate hasta);

    @Query("""
            SELECT g FROM Gasto g
            JOIN FETCH g.rubro
            LEFT JOIN FETCH g.subrubro
            JOIN FETCH g.obra o
            JOIN FETCH o.cliente
            WHERE g.idGasto = :id
            """)
    Optional<Gasto> buscarCompleto(@Param("id") Long id);

    /**
     * Total gastado por rubro en una obra.
     *
     * Es la mitad del semaforo: la otra mitad sale del presupuesto definitivo
     * aprobado. Se resuelve con una consulta agregada y NO con un campo
     * acumulado en la tabla, como pide el informe: un contador guardado hay que
     * recalcularlo con cada alta, baja y anulacion, y basta que una de esas
     * ramas se olvide para que el numero quede mal para siempre.
     *
     * Solo suma los Confirmados: un gasto anulado deja de contar, pero sigue
     * existiendo en la tabla.
     */
    @Query("""
            SELECT g.rubro.idRubro, g.rubro.nombreRubro, SUM(g.monto)
            FROM Gasto g
            WHERE g.obra.idObra = :idObra
              AND g.estado = 'Confirmado'
            GROUP BY g.rubro.idRubro, g.rubro.nombreRubro
            ORDER BY g.rubro.nombreRubro
            """)
    List<Object[]> totalPorRubro(@Param("idObra") Long idObra);

    /** Total gastado en la obra. Alimenta la ganancia estimada. */
    @Query("""
            SELECT COALESCE(SUM(g.monto), 0)
            FROM Gasto g
            WHERE g.obra.idObra = :idObra AND g.estado = 'Confirmado'
            """)
    BigDecimal totalGastado(@Param("idObra") Long idObra);

    /**
     * Total de gastos hormiga de la obra.
     *
     * Se mide aparte porque es el problema que el relevamiento identifica como
     * mas invisible: los gastos chicos que hoy se pierden. No se excluyen de la
     * comparacion contra el rubro, pero hay que poder ver su impacto acumulado
     * por separado.
     */
    @Query("""
            SELECT COALESCE(SUM(g.monto), 0)
            FROM Gasto g
            WHERE g.obra.idObra = :idObra
              AND g.estado = 'Confirmado'
              AND g.tipoGasto = 'Gasto Hormiga'
            """)
    BigDecimal totalHormiga(@Param("idObra") Long idObra);

    /** Gastos generados por un pedido, para no duplicarlos si se reprocesa. */
    long countByIdPedido(Long idPedido);
}
