package com.sigco.proveedores;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Acceso a datos de proveedores.
 */
public interface ProveedorRepository extends JpaRepository<Proveedor, Long> {

    /**
     * Listado con buscador por nombre y filtros por zona y estado.
     *
     * La zona se compara de forma parcial y sin distinguir mayusculas, igual
     * que el nombre: se escribe "zona norte" o "norte" y encuentra lo mismo.
     * Es texto libre cargado a mano, asi que una comparacion exacta fallaria
     * demasiado seguido.
     *
     * Ningun parametro puede llegar en null: la ausencia de filtro se expresa
     * con cadena vacia (ver el comentario en ClienteRepository.buscar).
     */
    @Query("""
            SELECT p FROM Proveedor p
            WHERE LOWER(p.nombreProveedor) LIKE LOWER(CONCAT('%', :busqueda, '%'))
              AND LOWER(p.zonaCobertura) LIKE LOWER(CONCAT('%', :zona, '%'))
              AND (:estado = '' OR p.estado = :estado)
            ORDER BY p.nombreProveedor ASC
            """)
    List<Proveedor> buscar(@Param("busqueda") String busqueda,
                           @Param("zona") String zona,
                           @Param("estado") String estado);

    /**
     * Busca un proveedor por nombre y zona, sin distinguir mayusculas.
     *
     * Se usa para detectar el duplicado que prohibe el informe. La combinacion
     * importa: una cadena de corralones puede tener sucursales con el mismo
     * nombre en zonas distintas, y son proveedores diferentes.
     */
    @Query("""
            SELECT p FROM Proveedor p
            WHERE LOWER(p.nombreProveedor) = LOWER(:nombre)
              AND LOWER(p.zonaCobertura) = LOWER(:zona)
            """)
    Optional<Proveedor> buscarPorNombreYZona(@Param("nombre") String nombre,
                                             @Param("zona") String zona);

    /** Zonas cargadas, para ofrecerlas como filtro sin tener que escribirlas. */
    @Query("SELECT DISTINCT p.zonaCobertura FROM Proveedor p ORDER BY p.zonaCobertura ASC")
    List<String> zonas();
}
