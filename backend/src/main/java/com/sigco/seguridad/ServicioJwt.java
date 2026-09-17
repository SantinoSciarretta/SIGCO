package com.sigco.seguridad;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Emision y verificacion de los tokens JWT.
 *
 * ------------------------------------------------------------------
 *  Por que un token y no una sesion en el servidor
 * ------------------------------------------------------------------
 *
 * El backend de SIGCO no guarda sesiones: cada peticion llega con toda la
 * informacion necesaria para autorizarla. Es lo que permite que el backend sea
 * "stateless" y que Railway pueda reiniciarlo o correr dos copias sin que nadie
 * pierda la sesion.
 *
 * ------------------------------------------------------------------
 *  Que lleva el token y que NO lleva
 * ------------------------------------------------------------------
 *
 * Lleva el id del usuario, su nombre y su rol. NO lleva los permisos.
 *
 * Es una decision deliberada: un token vive ocho horas, y si los permisos
 * viajaran adentro, quitarle un permiso a un rol no tendria efecto hasta que
 * todos volvieran a entrar. Los permisos se leen de la base en cada peticion,
 * asi un cambio en la pantalla de Accesos se aplica en la peticion siguiente.
 *
 * El token esta FIRMADO, no cifrado: cualquiera que lo tenga puede leer su
 * contenido, pero nadie puede modificarlo sin la clave. Por eso adentro no va
 * nada secreto.
 */
@Service
public class ServicioJwt {

    private final SecretKey clave;
    private final long duracionEnSegundos;

    public ServicioJwt(@Value("${sigco.jwt.secreto}") String secreto,
                       @Value("${sigco.jwt.duracion-segundos}") long duracionEnSegundos) {
        // HMAC-SHA256 exige una clave de al menos 256 bits (32 caracteres). Si
        // el secreto configurado es mas corto, la libreria lanza al arrancar en
        // lugar de firmar con una clave debil, que es lo correcto: una firma
        // debil es peor que no arrancar.
        this.clave = Keys.hmacShaKeyFor(secreto.getBytes(StandardCharsets.UTF_8));
        this.duracionEnSegundos = duracionEnSegundos;
    }

    /** Emite el token que el frontend va a adjuntar en cada peticion. */
    public String emitir(Long idUsuario, String nombreUsuario, String nombreRol) {
        Instant ahora = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(idUsuario))
                .claim("usuario", nombreUsuario)
                .claim("rol", nombreRol)
                .issuedAt(Date.from(ahora))
                .expiration(Date.from(ahora.plusSeconds(duracionEnSegundos)))
                .signWith(clave)
                .compact();
    }

    /**
     * Devuelve el id del usuario del token, o null si el token no sirve.
     *
     * Devuelve null en lugar de lanzar porque un token invalido o vencido es
     * una situacion esperable —una pestaña abierta desde ayer, por ejemplo—, no
     * un error del sistema. El filtro lo trata como "no autenticado" y sigue.
     */
    public Long idDelUsuario(String token) {
        try {
            Claims contenido = Jwts.parser()
                    .verifyWith(clave)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Long.valueOf(contenido.getSubject());
        } catch (JwtException | IllegalArgumentException ex) {
            return null;
        }
    }

    /**
     * Si a este token le queda poco y conviene reemplazarlo por uno nuevo.
     *
     * ------------------------------------------------------------------
     *  Renovacion deslizante: por que asi y no con un refresh token
     * ------------------------------------------------------------------
     *
     * El problema real: el token dura ocho horas contadas desde el ingreso, asi
     * que a media tarde el dueño se queda afuera en medio del trabajo, aunque
     * haya estado usando el sistema todo el dia.
     *
     * La solucion clasica es un segundo token de refresco, pero eso obliga a
     * guardarlo en una tabla y a poder revocarlo: le agrega estado al servidor,
     * que es justo lo que este diseño evita.
     *
     * Aca el token se renueva mientras se usa. Si a una peticion valida le queda
     * menos del umbral, el backend emite uno nuevo y se lo devuelve al frontend
     * en una cabecera. El efecto: quien trabaja nunca se cae, y quien deja la
     * pestaña abierta y se va vence igual, porque nadie esta renovando nada.
     *
     * No alarga la ventana de un token robado mas alla de su duracion: el
     * ladron tendria que estar usandolo activamente, y en ese caso el problema
     * no es la renovacion.
     */
    public boolean convieneRenovar(String token, double umbral) {
        try {
            Claims contenido = Jwts.parser()
                    .verifyWith(clave)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            long segundosRestantes = java.time.Duration
                    .between(Instant.now(), contenido.getExpiration().toInstant())
                    .getSeconds();

            return segundosRestantes < duracionEnSegundos * umbral;
        } catch (JwtException | IllegalArgumentException ex) {
            // Un token que no se puede leer no se renueva. No es un error: el
            // filtro ya lo trato como "no autenticado" antes de llegar aca.
            return false;
        }
    }

    public long getDuracionEnSegundos() {
        return duracionEnSegundos;
    }
}
