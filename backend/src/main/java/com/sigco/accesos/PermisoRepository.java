package com.sigco.accesos;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Catalogo de permisos. Solo lectura: los permisos los define la migracion. */
public interface PermisoRepository extends JpaRepository<Permiso, Long> {

    /** Ordenados por modulo para que la grilla de Accesos salga agrupada. */
    List<Permiso> findAllByOrderByModuloAscNombrePermisoAsc();
}
