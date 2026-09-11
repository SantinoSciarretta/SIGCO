package com.sigco.accesos;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Consultas sobre la auditoria. */
public interface RegistroAuditoriaRepository extends JpaRepository<RegistroAuditoria, Long> {

    /**
     * Ultimas acciones registradas, con filtros opcionales.
     *
     * Ningun parametro puede llegar en null: PostgreSQL no puede inferir el tipo
     * de un parametro nulo, lo asume bytea y la consulta falla. Por eso el
     * servicio manda 0 para "cualquier usuario", cadena vacia para "cualquier
     * modulo" y un rango de fechas amplio en lugar de nulls.
     */
    @Query("""
            SELECT a FROM RegistroAuditoria a
            JOIN FETCH a.usuario u
            WHERE (:idUsuario = 0 OR u.idUsuario = :idUsuario)
              AND (:modulo = '' OR a.moduloAfectado = :modulo)
              AND a.fechaHora BETWEEN :desde AND :hasta
            ORDER BY a.fechaHora DESC
            """)
    List<RegistroAuditoria> buscar(@Param("idUsuario") Long idUsuario,
                                   @Param("modulo") String modulo,
                                   @Param("desde") LocalDateTime desde,
                                   @Param("hasta") LocalDateTime hasta,
                                   Pageable pagina);
}
