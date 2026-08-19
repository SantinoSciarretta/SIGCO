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
     * NINGUN PARAMETRO PUEDE LLEGAR EN NULL. La ausencia de filtro se expresa
     * con la cadena vacia, y el motivo es un problema real de PostgreSQL:
     * cuando un parametro aparece unicamente en "? IS NULL", la base no tiene
     * de donde deducir su tipo, le asigna uno por defecto (bytea) y despues
     * falla al usarlo, con errores del estilo "no existe la funcion
     * lower(bytea)". Lo peligroso es que no falla siempre: depende de como
     * resuelva la inferencia en cada conexion, asi que puede pasar los tests y
     * romper en produccion.
     *
     * Con la cadena vacia como valor de "sin filtro", todos los parametros
     * viajan tipados como texto y la consulta es estable. La busqueda por
     * nombre no necesita condicion aparte: LIKE '%%' coincide con todo.
     *
     * Usa parametros con nombre, nunca concatenacion de texto: es lo que
     * previene la inyeccion de SQL que menciona el informe.
     */
    @Query("""
            SELECT c FROM Cliente c
            WHERE LOWER(c.nombreApellido) LIKE LOWER(CONCAT('%', :busqueda, '%'))
              AND (:origen = '' OR c.origenRecomendacion = :origen)
              AND (:estado = '' OR c.estado = :estado)
            ORDER BY c.nombreApellido ASC
            """)
    List<Cliente> buscar(@Param("busqueda") String busqueda,
                         @Param("origen") String origen,
                         @Param("estado") String estado);
}
