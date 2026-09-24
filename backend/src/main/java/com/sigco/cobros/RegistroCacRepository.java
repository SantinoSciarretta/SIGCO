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
     * El ultimo coeficiente cargado.
     *
     * Con uno alcanza: desde V21 la tabla guarda directamente por cuanto se
     * multiplican las cuotas, y no el nivel del indice publicado. Antes hacian
     * falta dos meses porque el coeficiente salia de dividir uno por el otro.
     */
    @Query("""
            SELECT r FROM RegistroCac r
            ORDER BY r.mesCorrespondiente DESC
            LIMIT 1
            """)
    Optional<RegistroCac> ultimo();
}
