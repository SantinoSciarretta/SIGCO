package com.sigco.cobros;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repositorio de cuotas.
 *
 * Cada repositorio va en su propio archivo, y no como interfaz anidada:
 * Spring Data JPA solo detecta interfaces de PRIMER NIVEL. Anidadas compilan
 * igual pero no generan bean, y la aplicacion falla al arrancar con un
 * "No qualifying bean" que no dice cual es la causa real.
 */
public interface CuotaRepository extends JpaRepository<Cuota, Long> {

    /** Plan de cobro de una obra, en orden. El anticipo es la cuota cero. */
    @Query("""
            SELECT c FROM Cuota c
            JOIN FETCH c.obra o
            JOIN FETCH o.cliente
            WHERE o.idObra = :idObra
            ORDER BY c.numeroCuota ASC
            """)
    List<Cuota> delPlan(@Param("idObra") Long idObra);

    @Query("""
            SELECT c FROM Cuota c
            JOIN FETCH c.obra o
            JOIN FETCH o.cliente
            WHERE c.idCuota = :id
            """)
    Optional<Cuota> buscarCompleta(@Param("id") Long id);

    boolean existsByObraIdObra(Long idObra);

    void deleteByObraIdObra(Long idObra);

    /**
     * Cuotas que vencen dentro de una ventana y siguen impagas.
     *
     * Es la alerta que reemplaza a la memoria del dueño: hoy nadie avisa
     * cuando una cuota esta por vencer.
     */
    @Query("""
            SELECT c FROM Cuota c
            JOIN FETCH c.obra o
            JOIN FETCH o.cliente
            WHERE c.estado <> 'Abonada'
              AND c.fechaVencimiento <= :hasta
            ORDER BY c.fechaVencimiento ASC
            """)
    List<Cuota> porVencerHasta(@Param("hasta") LocalDate hasta);

    /** Todas las cuotas impagas, para la vista consolidada. */
    @Query("""
            SELECT c FROM Cuota c
            JOIN FETCH c.obra o
            JOIN FETCH o.cliente
            WHERE o.estado IN ('En ejecución', 'Finalizada')
            ORDER BY o.idObra ASC, c.numeroCuota ASC
            """)
    List<Cuota> deObrasConCobros();
}
