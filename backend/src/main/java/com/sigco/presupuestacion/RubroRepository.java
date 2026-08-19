package com.sigco.presupuestacion;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Acceso a datos del catalogo de rubros.
 */
public interface RubroRepository extends JpaRepository<Rubro, Long> {

    /**
     * Catalogo completo, con los subrubros de cada rubro ya cargados.
     *
     * LEFT JOIN FETCH: el LEFT es necesario porque un rubro recien creado
     * todavia no tiene subrubros, y con un JOIN comun desapareceria del
     * listado. El FETCH trae todo en una sola consulta en lugar de una por
     * rubro.
     *
     * DISTINCT: al unir rubro con sus subrubros, la base devuelve una fila por
     * subrubro, es decir el mismo rubro repetido. Sin DISTINCT, un rubro con
     * cuatro subrubros aparece cuatro veces en la lista.
     *
     * El orden de los subrubros se declara ACA y no alcanza con el @OrderBy de
     * la entidad: cuando una coleccion se trae con JOIN FETCH, Hibernate la
     * llena en el orden en que vienen las filas de esta consulta, y el @OrderBy
     * queda sin efecto. Solo se aplica cuando la coleccion se carga por
     * separado. Por eso el ORDER BY incluye tambien el nombre del subrubro.
     *
     * Como en el resto del sistema, ningun parametro puede llegar en null: la
     * ausencia de filtro se expresa con cadena vacia (ver el comentario en
     * ClienteRepository.buscar).
     */
    @Query("""
            SELECT DISTINCT r FROM Rubro r
            LEFT JOIN FETCH r.subrubros s
            WHERE LOWER(r.nombreRubro) LIKE LOWER(CONCAT('%', :busqueda, '%'))
              AND (:estado = '' OR r.estado = :estado)
            ORDER BY r.nombreRubro ASC, s.nombreSubrubro ASC
            """)
    List<Rubro> buscarConSubrubros(@Param("busqueda") String busqueda,
                                   @Param("estado") String estado);

    /**
     * Busca un rubro por nombre sin distinguir mayusculas.
     *
     * Se usa para avisar antes de crear un duplicado. La base tambien lo impide
     * con un indice unico, pero comprobarlo antes permite devolver un mensaje
     * entendible en lugar de un error de restriccion.
     */
    Optional<Rubro> findByNombreRubroIgnoreCase(String nombreRubro);
}
