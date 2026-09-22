package com.sigco.seguridad;

import com.sigco.usuarios.Usuario;
import com.sigco.usuarios.UsuarioRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Lee el token de cada peticion y deja identificado al usuario.
 *
 * Se ejecuta una vez por peticion, ANTES de que la peticion llegue a ningun
 * controlador. Si hay un token valido, carga el usuario de la base y lo deja
 * disponible para el resto de la peticion. Si no hay token, o no sirve, la
 * peticion sigue sin usuario y Spring Security decide despues si el destino
 * admitia acceso anonimo.
 *
 * El filtro NO rechaza peticiones: eso lo hace la configuracion de seguridad.
 * Un filtro que decide sobre autorizacion mezcla dos responsabilidades y
 * termina con reglas de acceso repartidas en dos lugares.
 *
 * ------------------------------------------------------------------
 *  Por que consulta la base en cada peticion
 * ------------------------------------------------------------------
 *
 * Seria mas rapido confiar en lo que dice el token y no consultar nada. No se
 * hace, a proposito: los permisos cambian desde la pantalla de Accesos y una
 * cuenta se puede dar de baja en cualquier momento. Si el filtro confiara en el
 * token, un usuario dado de baja seguiria entrando hasta que su token venciera,
 * que puede ser ocho horas despues.
 *
 * Es una consulta indexada por clave primaria; el costo es despreciable frente
 * a lo que cuesta que un permiso revocado siga funcionando.
 */
@Component
public class FiltroJwt extends OncePerRequestFilter {

    private static final String CABECERA = "Authorization";
    private static final String PREFIJO = "Bearer ";

    /**
     * Cabecera por la que viaja el token renovado.
     *
     * Tiene que estar declarada en exposedHeaders de la configuracion de CORS:
     * el navegador no deja leer una cabecera de respuesta que el servidor no
     * autorizo explicitamente, y la renovacion pasaria inadvertida.
     */
    public static final String CABECERA_TOKEN_RENOVADO = "X-Token-Renovado";

    /**
     * Se renueva cuando queda menos de un cuarto de la duracion: con ocho horas
     * de token, en las ultimas dos.
     *
     * Antes seria emitir un token en casi cada peticion sin ganar nada. Mas
     * tarde dejaria sin margen a quien hace una sola consulta por la tarde y
     * vuelve al rato.
     */
    private static final double UMBRAL_DE_RENOVACION = 0.25;

    private final ServicioJwt servicioJwt;
    private final UsuarioRepository usuarioRepositorio;

    /**
     * Cuanto puede vivir una sesion, contando desde el ingreso.
     *
     * Es el tope de la cadena de renovaciones: pasado esto hay que volver a
     * escribir la contrasena, por mucho que se haya estado usando el sistema.
     */
    private final long topeEnSegundos;

    public FiltroJwt(ServicioJwt servicioJwt,
                     UsuarioRepository usuarioRepositorio,
                     @org.springframework.beans.factory.annotation.Value(
                             "${sigco.jwt.tope-sesion-segundos}") long topeEnSegundos) {
        this.servicioJwt = servicioJwt;
        this.usuarioRepositorio = usuarioRepositorio;
        this.topeEnSegundos = topeEnSegundos;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest peticion,
                                    HttpServletResponse respuesta,
                                    FilterChain cadena) throws ServletException, IOException {

        String token = extraerToken(peticion);

        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            ServicioJwt.DatosDeSesion datos = servicioJwt.datosDe(token);

            if (datos != null && !servicioJwt.sesionPasoElTope(datos, topeEnSegundos)) {
                Optional<Usuario> encontrado = usuarioRepositorio.completo(datos.idUsuario());

                // Tres condiciones, y cada una cierra una puerta distinta:
                //
                //   - que la cuenta exista y este ACTIVA, para que una cuenta
                //     dada de baja quede afuera aunque su token siga vigente;
                //   - que la VERSION DE SESION del token coincida con la de la
                //     cuenta, para que un cambio de contrasena deje sin efecto
                //     los tokens anteriores;
                //   - y, mas arriba, que la sesion no haya pasado su tope.
                //
                // Todo esto se puede comprobar sin consultas extra porque el
                // usuario ya se consulta en cada peticion desde el modulo
                // Accesos, para que un permiso revocado se aplique en el acto.
                if (encontrado.isPresent()
                        && encontrado.get().estaActivo()
                        && encontrado.get().getVersionSesion() == datos.versionSesion()) {

                    UsuarioAutenticado autenticado = new UsuarioAutenticado(encontrado.get());

                    UsernamePasswordAuthenticationToken autenticacion =
                            new UsernamePasswordAuthenticationToken(
                                    autenticado, null, autenticado.getAuthorities());

                    SecurityContextHolder.getContext().setAuthentication(autenticacion);

                    renovarSiConviene(token, datos, encontrado.get(), respuesta);
                }
            }
        }

        cadena.doFilter(peticion, respuesta);
    }

    /**
     * Devuelve un token nuevo si al actual le queda poco.
     *
     * Viaja en una cabecera de respuesta y no en el cuerpo porque el cuerpo es
     * de cada modulo: meter ahi un dato de la sesion obligaria a que las
     * respuestas de los catorce modulos tuvieran un campo mas. El frontend lo
     * recoge en su interceptor de Axios, en un solo lugar.
     *
     * Si la renovacion falla por lo que sea, la peticion sigue: el token viejo
     * todavia es valido. Quedarse sin renovar es una molestia dentro de un rato;
     * cortar la peticion es un error ahora.
     */
    private void renovarSiConviene(String token,
                                   ServicioJwt.DatosDeSesion datos,
                                   Usuario usuario,
                                   HttpServletResponse respuesta) {
        try {
            if (servicioJwt.convieneRenovar(token, UMBRAL_DE_RENOVACION, topeEnSegundos)) {
                // Se CONSERVA el inicio de sesion del token anterior. Grabar el
                // instante actual haria que el tope no llegara nunca: la sesion
                // se renovaria a si misma para siempre.
                respuesta.setHeader(CABECERA_TOKEN_RENOVADO, servicioJwt.renovar(
                        usuario.getIdUsuario(),
                        usuario.getNombreUsuario(),
                        usuario.getRol().getNombreRol(),
                        usuario.getVersionSesion(),
                        datos.inicioSesion()));
            }
        } catch (RuntimeException ignorado) {
            // Sin renovar: el token actual sigue sirviendo.
        }
    }

    private String extraerToken(HttpServletRequest peticion) {
        String cabecera = peticion.getHeader(CABECERA);
        if (cabecera == null || !cabecera.startsWith(PREFIJO)) {
            return null;
        }
        return cabecera.substring(PREFIJO.length()).trim();
    }
}
