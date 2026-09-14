package com.sigco.personal;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Consultas sobre la asignacion de operarios a obras.
 *
 * Interfaz de primer nivel y en su propio archivo: Spring Data JPA solo detecta
 * repositorios declarados asi. Agruparlos como interfaces anidadas compila pero
 * deja a Spring sin crear alguno, y la aplicacion falla al arrancar con
 * "No qualifying bean". Ya paso una vez en Cobros.
 */
public interface OperarioObraRepository extends JpaRepository<OperarioObra, OperarioObraId> {

    /**
     * Ids de las obras que el operario tiene asignadas HOY.
     *
     * Solo las vigentes: `fecha_desasignacion IS NULL`. Es lo que define el
     * alcance de un Capataz de Obra, y una asignacion cerrada ya no le da
     * acceso — si se la sacaron de la obra, deja de verla.
     *
     * Devuelve ids y no entidades a proposito: quien la usa necesita decidir si
     * un id esta o no en la lista, y traer las obras completas para eso serian
     * datos que nadie va a leer.
     */
    @Query("""
            SELECT oo.obra.idObra FROM OperarioObra oo
            WHERE oo.operario.idOperario = :idOperario
              AND oo.fechaDesasignacion IS NULL
            """)
    List<Long> obrasVigentesDe(@Param("idOperario") Long idOperario);
}
