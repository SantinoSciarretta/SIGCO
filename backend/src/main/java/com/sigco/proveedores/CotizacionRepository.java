package com.sigco.proveedores;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Acceso a datos de cotizaciones.
 */
public interface CotizacionRepository extends JpaRepository<Cotizacion, Long> {

    /**
     * COMPARADOR DE COTIZACIONES.
     *
     * Para un material dado, trae la ULTIMA cotizacion de cada proveedor activo,
     * ordenada de menor a mayor precio. Es la consulta que resuelve la pregunta
     * concreta del dueño: "necesito cemento, quien me lo hace mas barato hoy".
     *
     * El problema a resolver es lo que en SQL se llama "el ultimo de cada
     * grupo": la tabla tiene todas las cotizaciones historicas, y hay que
     * quedarse con una sola por proveedor.
     *
     * Se resuelve con una subconsulta que, por cada fila, comprueba que su
     * identificador sea el mayor de ese proveedor para ese material.
     *
     * Por que MAX(id) y no MAX(fecha): la fecha la pone el sistema al insertar,
     * asi que el orden de los identificadores y el de las fechas es siempre el
     * mismo. Pero el identificador es unico y la fecha no: si dos cotizaciones
     * cayeran en el mismo instante, comparar por fecha devolveria las dos y el
     * mismo proveedor apareceria repetido en el comparador.
     */
    @Query("""
            SELECT c FROM Cotizacion c
            JOIN FETCH c.proveedor p
            JOIN FETCH c.material m
            WHERE m.idMaterial = :idMaterial
              AND p.estado = 'Activo'
              AND c.idCotizacion = (
                  SELECT MAX(c2.idCotizacion) FROM Cotizacion c2
                  WHERE c2.proveedor = c.proveedor
                    AND c2.material = c.material)
            ORDER BY c.precioCotizado ASC
            """)
    List<Cotizacion> comparar(@Param("idMaterial") Long idMaterial);

    /**
     * Historial completo de cotizaciones de un proveedor, de la mas reciente a
     * la mas vieja. Lo usa la ficha del proveedor.
     */
    @Query("""
            SELECT c FROM Cotizacion c
            JOIN FETCH c.material m
            JOIN FETCH m.rubro
            WHERE c.proveedor.idProveedor = :idProveedor
            ORDER BY c.fechaCotizacion DESC
            """)
    List<Cotizacion> historialDeProveedor(@Param("idProveedor") Long idProveedor);

    /** Cuantas cotizaciones tiene un proveedor. */
    long countByProveedorIdProveedor(Long idProveedor);
}
