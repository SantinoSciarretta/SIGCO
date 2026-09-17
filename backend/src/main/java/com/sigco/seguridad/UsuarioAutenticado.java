package com.sigco.seguridad;

import com.sigco.accesos.Permiso;
import com.sigco.usuarios.Usuario;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * El usuario que hizo la peticion, tal como lo ve Spring Security.
 *
 * Envuelve la entidad Usuario y traduce sus permisos a "authorities", que es el
 * nombre que Spring Security les da. La traduccion es directa: el permiso
 * "compras.aprobar" se convierte en la authority "compras.aprobar", y por eso
 * en los controladores se puede escribir:
 *
 *     @PreAuthorize("hasAuthority('compras.aprobar')")
 *
 * Que la regla de la base y la anotacion del codigo usen literalmente el mismo
 * texto es lo que hace que la matriz de permisos del informe sea verificable:
 * se puede leer la fila de la tabla y buscar esa misma cadena en el codigo.
 *
 * Se implementa a mano y no con el User de Spring para poder llevar tambien el
 * id del usuario y el operario vinculado, que es lo que necesitan los modulos
 * para completar los campos "quien lo registro".
 */
public class UsuarioAutenticado implements org.springframework.security.core.userdetails.UserDetails {

    private final Long idUsuario;
    private final String nombreUsuario;
    private final String nombreRol;
    private final Long idOperario;
    private final List<GrantedAuthority> permisos;

    /**
     * Se copia aca para que FiltroCambioDeContrasena no tenga que volver a
     * consultar la base: FiltroJwt ya cargo el usuario en esta misma peticion.
     */
    private final boolean debeCambiarContrasena;

    public UsuarioAutenticado(Usuario usuario) {
        this.idUsuario = usuario.getIdUsuario();
        this.debeCambiarContrasena = usuario.debeCambiarContrasena();
        this.nombreUsuario = usuario.getNombreUsuario();
        this.nombreRol = usuario.getRol().getNombreRol();
        this.idOperario = usuario.getOperario() != null
                ? usuario.getOperario().getIdOperario() : null;
        this.permisos = usuario.getRol().getPermisos().stream()
                .map(Permiso::getNombrePermiso)
                .map(SimpleGrantedAuthority::new)
                .map(GrantedAuthority.class::cast)
                .toList();
    }

    public boolean debeCambiarContrasena() {
        return debeCambiarContrasena;
    }

    public Long getIdUsuario() {
        return idUsuario;
    }

    public String getNombreRol() {
        return nombreRol;
    }

    /** El operario vinculado, si la cuenta corresponde a alguien de Personal. */
    public Long getIdOperario() {
        return idOperario;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return permisos;
    }

    /**
     * No devuelve la contrasena: la verificacion no pasa por este objeto.
     *
     * Spring Security ofrece un flujo donde el framework compara la contrasena
     * por su cuenta, y para eso necesitaria el hash aca. SIGCO no lo usa: la
     * comparacion la hace el servicio de autenticacion contra la entidad, asi
     * el hash nunca sale del Usuario. Este objeto se construye DESPUES de que
     * la contrasena ya fue verificada.
     */
    @Override
    public String getPassword() {
        return null;
    }

    @Override
    public String getUsername() {
        return nombreUsuario;
    }

    /**
     * Una cuenta desactivada no llega hasta aca: el servicio la rechaza antes.
     * Se devuelve true para que Spring no vuelva a decidir sobre algo que ya se
     * resolvio, y que esa decision viva en un solo lugar.
     */
    @Override
    public boolean isEnabled() {
        return true;
    }
}
