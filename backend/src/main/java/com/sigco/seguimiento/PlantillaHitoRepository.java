package com.sigco.seguimiento;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlantillaHitoRepository extends JpaRepository<PlantillaHito, Long> {

    @Query("""
            SELECT DISTINCT p FROM PlantillaHito p
            LEFT JOIN FETCH p.detalles d
            ORDER BY p.nombrePlantilla ASC
            """)
    List<PlantillaHito> todasConDetalles();

    @Query("""
            SELECT DISTINCT p FROM PlantillaHito p
            LEFT JOIN FETCH p.detalles
            WHERE p.idPlantilla = :id
            """)
    Optional<PlantillaHito> buscarCompleta(@Param("id") Long id);
}
