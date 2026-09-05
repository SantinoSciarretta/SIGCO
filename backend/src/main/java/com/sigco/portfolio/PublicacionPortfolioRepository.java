package com.sigco.portfolio;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PublicacionPortfolioRepository
        extends JpaRepository<PublicacionPortfolio, Long> {

    /**
     * Publicaciones visibles en la vidriera.
     *
     * El filtro por estado va en la CONSULTA, no en el código que la usa: así
     * no hay forma de que una obra despublicada llegue a la vista pública por
     * un descuido al armar la respuesta.
     */
    @Query("""
            SELECT DISTINCT p FROM PublicacionPortfolio p
            LEFT JOIN FETCH p.imagenes
            WHERE p.estado = 'Publicada'
            ORDER BY p.fechaPublicacion DESC
            """)
    List<PublicacionPortfolio> publicadas();

    @Query("""
            SELECT DISTINCT p FROM PublicacionPortfolio p
            LEFT JOIN FETCH p.imagenes
            WHERE p.estado = 'Publicada' AND p.tipoTrabajo = :tipoTrabajo
            ORDER BY p.fechaPublicacion DESC
            """)
    List<PublicacionPortfolio> publicadasDeTipo(@Param("tipoTrabajo") String tipoTrabajo);

    /** Tipos con al menos una obra publicada, para el filtro de la vidriera. */
    @Query("""
            SELECT DISTINCT p.tipoTrabajo FROM PublicacionPortfolio p
            WHERE p.estado = 'Publicada'
            ORDER BY p.tipoTrabajo ASC
            """)
    List<String> tiposConPublicaciones();

    /** Panel del dueño: todas, publicadas o no. */
    @Query("""
            SELECT DISTINCT p FROM PublicacionPortfolio p
            JOIN FETCH p.obra o
            JOIN FETCH o.cliente
            LEFT JOIN FETCH p.imagenes
            ORDER BY p.fechaPublicacion DESC
            """)
    List<PublicacionPortfolio> todasConImagenes();

    @Query("""
            SELECT DISTINCT p FROM PublicacionPortfolio p
            JOIN FETCH p.obra o
            JOIN FETCH o.cliente
            LEFT JOIN FETCH p.imagenes
            WHERE p.idPublicacion = :id
            """)
    Optional<PublicacionPortfolio> buscarCompleta(@Param("id") Long id);

    boolean existsByObraIdObra(Long idObra);
}
