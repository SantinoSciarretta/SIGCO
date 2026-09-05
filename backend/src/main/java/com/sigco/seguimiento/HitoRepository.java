package com.sigco.seguimiento;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HitoRepository extends JpaRepository<Hito, Long> {

    /**
     * Hitos de una obra, en el orden previsto.
     *
     * El ORDER BY va en la consulta y no en un @OrderBy de la entidad: esa
     * anotacion no se aplica cuando la coleccion se trae con JOIN FETCH, y ya
     * hizo perder el orden una vez en el catalogo de Presupuestacion.
     */
    @Query("""
            SELECT h FROM Hito h
            JOIN FETCH h.obra
            WHERE h.obra.idObra = :idObra
            ORDER BY h.orden ASC
            """)
    List<Hito> deLaObra(@Param("idObra") Long idObra);

    long countByObraIdObra(Long idObra);

    /**
     * Borra los hitos de una obra.
     *
     * flushAutomatically es imprescindible: sin eso Hibernate acomoda las
     * sentencias a su criterio y manda los INSERT de los hitos nuevos ANTES del
     * DELETE, chocando contra el indice unico (id_obra, LOWER(nombre_hito)).
     * Redefinir un plan que repite algun nombre fallaba con un 500.
     *
     * clearAutomatically deja limpio el contexto de persistencia, para que lo
     * que se lea despues venga de la base y no de entidades ya borradas.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM Hito h WHERE h.obra.idObra = :idObra")
    void borrarDeLaObra(@Param("idObra") Long idObra);
}
