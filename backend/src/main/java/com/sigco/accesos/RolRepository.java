package com.sigco.accesos;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Consultas sobre roles. */
public interface RolRepository extends JpaRepository<Rol, Long> {

    /**
     * Todos los roles con sus permisos.
     *
     * DISTINCT porque el JOIN multiplica la fila del rol por cada permiso: sin
     * el, un rol con veintiocho permisos aparece veintiocho veces.
     */
    @Query("""
            SELECT DISTINCT r FROM Rol r
            LEFT JOIN FETCH r.permisos
            ORDER BY r.idRol
            """)
    List<Rol> todosConPermisos();

    @Query("""
            SELECT r FROM Rol r
            LEFT JOIN FETCH r.permisos
            WHERE r.idRol = :id
            """)
    Optional<Rol> conPermisos(@Param("id") Long id);

    Optional<Rol> findByNombreRol(String nombreRol);
}
