package com.sigco.proveedores;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Acceso a datos de las observaciones de comportamiento.
 */
public interface ObservacionRepository extends JpaRepository<ObservacionProveedor, Long> {

    /** Observaciones de un proveedor, de la mas reciente a la mas vieja. */
    List<ObservacionProveedor> findByProveedorIdProveedorOrderByFechaDesc(Long idProveedor);

    /** Cuantas observaciones tiene un proveedor. Se muestra en el listado. */
    long countByProveedorIdProveedor(Long idProveedor);
}
