package com.sigco.usuarios;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Consultas sobre cuentas de acceso.
 *
 * Cada repositorio va en su PROPIO archivo, como interfaz de primer nivel.
 * Spring Data JPA solo detecta interfaces de primer nivel: agrupar dos
 * repositorios como interfaces anidadas dentro de una misma clase compila sin
 * problemas pero deja a Spring sin crear uno de los dos, y la aplicacion falla
 * al arrancar con "No qualifying bean". Ya paso una vez en Cobros.
 */
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    /**
     * Busca por nombre de usuario para el login.
     *
     * Trae el rol CON sus permisos en la misma consulta: apenas el usuario
     * ingresa hay que saber que puede hacer, y sin el JOIN FETCH eso serian dos
     * consultas mas por cada ingreso (y con open-in-view desactivado, un error).
     */
    @Query("""
            SELECT u FROM Usuario u
            JOIN FETCH u.rol r
            LEFT JOIN FETCH r.permisos
            WHERE LOWER(u.nombreUsuario) = LOWER(:nombreUsuario)
            """)
    Optional<Usuario> porNombre(@Param("nombreUsuario") String nombreUsuario);

    /** Igual que la anterior pero por id: la usa el filtro en cada peticion. */
    @Query("""
            SELECT u FROM Usuario u
            JOIN FETCH u.rol r
            LEFT JOIN FETCH r.permisos
            WHERE u.idUsuario = :id
            """)
    Optional<Usuario> completo(@Param("id") Long id);

    @Query("""
            SELECT u FROM Usuario u
            JOIN FETCH u.rol
            LEFT JOIN FETCH u.operario
            ORDER BY u.estado, LOWER(u.nombreUsuario)
            """)
    List<Usuario> todosConRol();

    boolean existsByNombreUsuarioIgnoreCase(String nombreUsuario);

    /**
     * Cuantas cuentas ACTIVAS tiene un rol.
     *
     * La usa la regla que impide dejar el sistema sin ningun dueño: si se
     * desactiva la ultima cuenta con ese rol, nadie puede volver a entrar a
     * administrar usuarios.
     */
    long countByRolNombreRolAndEstado(String nombreRol, String estado);

    /** Si el operario ya tiene cuenta. El indice unico parcial lo garantiza. */
    boolean existsByOperarioIdOperario(Long idOperario);
}
