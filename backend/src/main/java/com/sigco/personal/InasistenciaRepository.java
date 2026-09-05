package com.sigco.personal;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InasistenciaRepository extends JpaRepository<Inasistencia, Long> {

    /** Listado con filtros. Ningun parametro viaja nulo (ver OperarioRepository). */
    @Query("""
            SELECT i FROM Inasistencia i
            JOIN FETCH i.operario op
            JOIN FETCH i.obra ob
            WHERE (:idOperario = 0L OR op.idOperario = :idOperario)
              AND (:idObra = 0L OR ob.idObra = :idObra)
              AND i.fechaFalta BETWEEN :desde AND :hasta
            ORDER BY i.fechaFalta DESC
            """)
    List<Inasistencia> buscar(@Param("idOperario") Long idOperario,
                              @Param("idObra") Long idObra,
                              @Param("desde") LocalDate desde,
                              @Param("hasta") LocalDate hasta);

    /**
     * La regla del informe: no dos inasistencias del mismo operario, misma
     * fecha y obra. La base tiene el indice unico; esto existe para devolver un
     * 409 con un mensaje entendible en lugar de un error de restriccion.
     */
    boolean existsByOperarioIdOperarioAndObraIdObraAndFechaFalta(
            Long idOperario, Long idObra, LocalDate fechaFalta);

    /** Cantidad de faltas de un operario, para el listado. */
    long countByOperarioIdOperario(Long idOperario);
}
