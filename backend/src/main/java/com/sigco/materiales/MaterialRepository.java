package com.sigco.materiales;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Acceso a datos del catalogo de materiales.
 */
public interface MaterialRepository extends JpaRepository<Material, Long> {

    /**
     * Listado con buscador por nombre y filtros por rubro y estado.
     *
     * JOIN FETCH sobre rubro para que el listado muestre el nombre del rubro
     * sin disparar una consulta por material.
     *
     * Ningun parametro puede llegar en null: la ausencia de filtro se expresa
     * con cero o cadena vacia (ver el comentario en ClienteRepository.buscar).
     */
    @Query("""
            SELECT m FROM Material m
            JOIN FETCH m.rubro r
            WHERE LOWER(m.nombreMaterial) LIKE LOWER(CONCAT('%', :busqueda, '%'))
              AND (:idRubro = 0 OR r.idRubro = :idRubro)
              AND (:estado = '' OR m.estado = :estado)
            ORDER BY r.nombreRubro ASC, m.nombreMaterial ASC
            """)
    List<Material> buscar(@Param("busqueda") String busqueda,
                          @Param("idRubro") Long idRubro,
                          @Param("estado") String estado);

    /**
     * Materiales que se pueden ofrecer en un presupuesto o un pedido nuevo.
     *
     * Exige que el material este activo Y que su rubro tambien lo este, igual
     * que con los subrubros: desactivar un rubro saca de circulacion todo lo
     * que cuelga de el, sin tener que propagar la baja.
     */
    @Query("""
            SELECT m FROM Material m
            JOIN FETCH m.rubro r
            WHERE m.estado = 'Activo'
              AND r.estado = 'Activo'
              AND (:idRubro = 0 OR r.idRubro = :idRubro)
            ORDER BY r.nombreRubro ASC, m.nombreMaterial ASC
            """)
    List<Material> buscarDisponibles(@Param("idRubro") Long idRubro);

    /** Detecta un nombre repetido dentro del mismo rubro. */
    Optional<Material> findByRubroIdRubroAndNombreMaterialIgnoreCase(Long idRubro, String nombre);

    /** Un material con su rubro ya cargado. */
    @Query("SELECT m FROM Material m JOIN FETCH m.rubro WHERE m.idMaterial = :id")
    Optional<Material> buscarConRubro(@Param("id") Long id);
}
