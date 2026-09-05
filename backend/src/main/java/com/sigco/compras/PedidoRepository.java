package com.sigco.compras;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PedidoRepository extends JpaRepository<Pedido, Long> {

    /**
     * Listado con filtros combinables.
     *
     * NINGUN PARAMETRO PUEDE LLEGAR NULO. El patron habitual
     * "(:param IS NULL OR ...)" hace que PostgreSQL no pueda deducir el tipo del
     * parametro, le asigne bytea y falle. El servicio traduce "sin filtro" a
     * cadena vacia o a 0 segun el caso. Es la misma correccion que se aplico en
     * Clientes y Obras, donde el fallo aparecio de forma intermitente.
     */
    @Query("""
            SELECT p FROM Pedido p
            JOIN p.obra o
            LEFT JOIN p.proveedor pr
            WHERE (:idObra = 0L OR o.idObra = :idObra)
              AND (:idProveedor = 0L OR pr.idProveedor = :idProveedor)
              AND (:estado = '' OR p.estado = :estado)
            ORDER BY p.fechaSolicitud DESC
            """)
    List<Pedido> buscar(@Param("idObra") Long idObra,
                        @Param("idProveedor") Long idProveedor,
                        @Param("estado") String estado);

    /**
     * Un pedido con sus lineas, materiales, obra y proveedor en una sola
     * consulta.
     *
     * Con open-in-view desactivado, lo que no se trae aca no se puede leer
     * despues: la sesion ya esta cerrada cuando el DTO arma la respuesta.
     */
    @Query("""
            SELECT DISTINCT p FROM Pedido p
            JOIN FETCH p.obra o
            JOIN FETCH o.cliente
            LEFT JOIN FETCH p.proveedor
            LEFT JOIN FETCH p.materiales m
            LEFT JOIN FETCH m.material mat
            LEFT JOIN FETCH mat.rubro
            WHERE p.idPedido = :id
            """)
    Optional<Pedido> buscarCompleto(@Param("id") Long id);

    /** Historial de compras de un proveedor, para su ficha. */
    long countByProveedorIdProveedor(Long idProveedor);

    /** Pedidos esperando aprobacion, para el panel del dueño y el Dashboard. */
    List<Pedido> findByEstadoOrderByFechaSolicitudAsc(String estado);
}
