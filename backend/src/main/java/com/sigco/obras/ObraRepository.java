package com.sigco.obras;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Acceso a datos de obras.
 */
public interface ObraRepository extends JpaRepository<Obra, Long> {

    /**
     * Listado con los filtros combinables que pide el informe: cliente, tipo de
     * obra, estado y rango de fechas de creacion.
     *
     * JOIN FETCH sobre cliente: trae la obra y su cliente en UNA sola consulta.
     * Sin eso, listar cincuenta obras dispararia cincuenta consultas extra para
     * resolver el nombre de cada cliente (el problema N+1), porque la relacion
     * esta declarada como LAZY.
     *
     * Las obras se ordenan dejando primero las que estan en ejecucion: es lo
     * que el dueño necesita ver al entrar, y responde al pedido del informe de
     * que las obras activas se destaquen de las finalizadas o canceladas.
     *
     * NINGUN PARAMETRO PUEDE LLEGAR EN NULL. La ausencia de filtro se expresa
     * con valores neutros: cadena vacia para los textos, cero para el
     * identificador de cliente y un rango de fechas amplio.
     *
     * El motivo es un problema real de PostgreSQL: cuando un parametro aparece
     * unicamente en "? IS NULL", la base no tiene de donde deducir su tipo, le
     * asigna uno por defecto (bytea) y despues falla al usarlo, con errores del
     * estilo "no existe la funcion lower(bytea)" o "no se pudo determinar el
     * tipo del parametro". Lo peligroso es que no falla siempre: depende de
     * como resuelva la inferencia en cada conexion, asi que puede pasar los
     * tests y romper despues.
     *
     * Con valores neutros, todos los parametros viajan tipados y la consulta es
     * estable. La busqueda por texto no necesita condicion aparte: LIKE '%%'
     * coincide con todo.
     *
     * (Si mas adelante los filtros opcionales crecen, la alternativa es armar
     * la consulta con Specifications, que agrega solo las condiciones que
     * aplican en lugar de neutralizarlas.)
     */
    @Query("""
            SELECT o FROM Obra o
            JOIN FETCH o.cliente c
            WHERE (:idCliente = 0 OR c.idCliente = :idCliente)
              AND (:tipoObra = '' OR o.tipoObra = :tipoObra)
              AND (:estado = '' OR o.estado = :estado)
              AND o.fechaCreacion BETWEEN :desde AND :hasta
              AND (LOWER(o.direccionObra) LIKE LOWER(CONCAT('%', :busqueda, '%'))
                   OR LOWER(c.nombreApellido) LIKE LOWER(CONCAT('%', :busqueda, '%')))
            ORDER BY
              CASE o.estado
                WHEN 'En ejecución' THEN 0
                WHEN 'En presupuestación' THEN 1
                WHEN 'Finalizada' THEN 2
                ELSE 3
              END,
              o.fechaCreacion DESC
            """)
    List<Obra> buscar(@Param("idCliente") Long idCliente,
                      @Param("tipoObra") String tipoObra,
                      @Param("estado") String estado,
                      @Param("desde") LocalDateTime desde,
                      @Param("hasta") LocalDateTime hasta,
                      @Param("busqueda") String busqueda);

    /** Una obra con su cliente ya cargado, para la ficha de detalle. */
    @Query("SELECT o FROM Obra o JOIN FETCH o.cliente WHERE o.idObra = :id")
    java.util.Optional<Obra> buscarConCliente(@Param("id") Long id);

    /**
     * Cantidad de obras por cliente, en una sola consulta.
     *
     * La usa el listado de clientes para mostrar cuantos proyectos tiene cada
     * uno. Se resuelve agrupando en la base y no preguntando cliente por
     * cliente, que serian tantas consultas como clientes haya en la lista.
     */
    @Query("SELECT o.cliente.idCliente, COUNT(o) FROM Obra o GROUP BY o.cliente.idCliente")
    List<Object[]> contarPorCliente();

    /** Cuantas obras tiene un cliente. */
    /**
     * Obras en un estado determinado, con su cliente ya cargado.
     *
     * La usa el Tablero, que recorre las obras en ejecucion. Sin el JOIN FETCH
     * cada fila dispararia una consulta mas para traer el nombre del cliente
     * (con open-in-view desactivado ni siquiera funcionaria).
     */
    @Query("""
            SELECT o FROM Obra o
            JOIN FETCH o.cliente
            WHERE o.estado = :estado
            ORDER BY o.fechaCreacion DESC
            """)
    List<Obra> porEstado(@Param("estado") String estado);

    /** Cuantas obras hay en un estado. Para los contadores del Tablero. */
    long countByEstado(String estado);

    long countByClienteIdCliente(Long idCliente);
}
