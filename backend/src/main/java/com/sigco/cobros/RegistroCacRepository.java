package com.sigco.cobros;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Repositorio del indice CAC. Ver la nota en CuotaRepository. */
public interface RegistroCacRepository extends JpaRepository<RegistroCac, Long> {

    List<RegistroCac> findAllByOrderByMesCorrespondienteDesc();

    Optional<RegistroCac> findByMesCorrespondiente(LocalDate mesCorrespondiente);

    /**
     * Los dos ultimos indices cargados.
     *
     * La actualizacion por CAC no usa el indice suelto sino la RELACION
     * entre el del mes y el del mes anterior: el coeficiente es
     * nuevo/anterior. Un indice solo no dice cuanto subio nada.
     */
    @Query("""
            SELECT r FROM RegistroCac r
            ORDER BY r.mesCorrespondiente DESC
            LIMIT 2
            """)
    List<RegistroCac> ultimosDos();
}
