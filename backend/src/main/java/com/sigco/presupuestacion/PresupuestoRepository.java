package com.sigco.presupuestacion;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Acceso a datos de presupuestos.
 */
public interface PresupuestoRepository extends JpaRepository<Presupuesto, Long> {

    /**
     * Listado de presupuestos, con la obra y su cliente ya cargados.
     *
     * Como en el resto del sistema, ningun parametro puede llegar en null: la
     * ausencia de filtro se expresa con cero o cadena vacia.
     */
    @Query("""
            SELECT p FROM Presupuesto p
            JOIN FETCH p.obra o
            JOIN FETCH o.cliente
            WHERE (:idObra = 0 OR o.idObra = :idObra)
              AND (:tipo = '' OR p.tipoPresupuesto = :tipo)
              AND (:estado = '' OR p.estado = :estado)
            ORDER BY p.fechaCreacion DESC
            """)
    List<Presupuesto> buscar(@Param("idObra") Long idObra,
                             @Param("tipo") String tipo,
                             @Param("estado") String estado);

    /**
     * Un presupuesto con todo lo necesario para mostrarlo o generar su PDF.
     *
     * Trae la obra, su cliente y los items en una sola consulta. Sin esto, cada
     * item dispararia consultas extra al resolver su rubro y su subrubro.
     */
    @Query("""
            SELECT DISTINCT p FROM Presupuesto p
            JOIN FETCH p.obra o
            JOIN FETCH o.cliente
            LEFT JOIN FETCH p.items i
            LEFT JOIN FETCH i.rubro
            LEFT JOIN FETCH i.subrubro
            WHERE p.idPresupuesto = :id
            """)
    Optional<Presupuesto> buscarCompleto(@Param("id") Long id);

    /**
     * Ultimo numero de version usado para un tipo de presupuesto en una obra.
     *
     * El informe define la version como el numero dentro del mismo tipo: una
     * obra puede tener el Anteproyecto v1 y los Definitivos v1 y v2.
     */
    @Query("""
            SELECT COALESCE(MAX(p.version), 0) FROM Presupuesto p
            WHERE p.obra.idObra = :idObra AND p.tipoPresupuesto = :tipo
            """)
    Integer ultimaVersion(@Param("idObra") Long idObra, @Param("tipo") String tipo);

    /** Cuantos presupuestos de un tipo tiene una obra. Se usa para las reglas de circuito. */
    long countByObraIdObraAndTipoPresupuesto(Long idObra, String tipoPresupuesto);

    /** Presupuestos de una obra en un estado dado y de un tipo dado. */
    List<Presupuesto> findByObraIdObraAndTipoPresupuestoAndEstado(
            Long idObra, String tipoPresupuesto, String estado);

    /** Si algun presupuesto usa a este como base, no se lo puede dar por perdido. */
    boolean existsByPresupuestoBaseIdPresupuesto(Long idPresupuesto);
}
