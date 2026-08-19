package com.sigco.presupuestacion;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Acceso a datos de subrubros.
 */
public interface SubrubroRepository extends JpaRepository<Subrubro, Long> {

    /** Detecta un nombre repetido dentro del mismo rubro. */
    Optional<Subrubro> findByRubroIdRubroAndNombreSubrubroIgnoreCase(Long idRubro, String nombreSubrubro);

    /**
     * Subrubros que se pueden ofrecer al cargar un presupuesto nuevo.
     *
     * Exige que el subrubro este activo Y que su rubro tambien lo este. Ese
     * segundo requisito es el que hace innecesario propagar la baja: al
     * desactivar un rubro, sus subrubros dejan de ofrecerse solos, y si el
     * rubro se reactiva cada subrubro recupera el estado que tenia.
     *
     * Lo consume la Parte B del modulo, al armar los items del presupuesto.
     */
    @Query("""
            SELECT s FROM Subrubro s
            JOIN FETCH s.rubro r
            WHERE s.estado = 'Activo'
              AND r.estado = 'Activo'
              AND (:idRubro = 0 OR r.idRubro = :idRubro)
            ORDER BY r.nombreRubro ASC, s.nombreSubrubro ASC
            """)
    List<Subrubro> buscarDisponibles(@Param("idRubro") Long idRubro);
}
