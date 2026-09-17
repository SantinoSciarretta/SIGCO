package com.sigco.seguridad;

import com.sigco.accesos.ServicioAuditoria;
import com.sigco.usuarios.Usuario;
import com.sigco.usuarios.UsuarioRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lleva la cuenta de los intentos fallidos de ingreso.
 *
 * ------------------------------------------------------------------
 *  Por que esto es una clase aparte y no un metodo de AutenticacionService
 * ------------------------------------------------------------------
 *
 * Por la transaccion, y es la razon entera de que este archivo exista.
 *
 * Cuando alguien escribe mal la contrasena, el ingreso termina lanzando una
 * excepcion. ReglaDeNegocioException es una RuntimeException, y Spring deshace
 * la transaccion ante una RuntimeException. Si el contador se incrementara
 * dentro de la transaccion del ingreso, ese incremento se deshace junto con
 * todo lo demas: el contador quedaria siempre en cero y la cuenta no se
 * bloquearia nunca. El codigo se veria correcto y no serviria para nada.
 *
 * Llamar a un metodo @Transactional desde otro metodo de la MISMA clase tampoco
 * alcanza: la llamada no pasa por el proxy de Spring y la anotacion se ignora.
 * Por eso es un componente separado, que se inyecta y se llama desde afuera.
 *
 * REQUIRES_NEW suspende la transaccion del ingreso, abre una propia, guarda el
 * intento y la confirma. Cuando despues el ingreso se deshace, esto ya esta
 * escrito en la base.
 *
 * Es el mismo problema que ESTADO.md anota para GastoService: una excepcion no
 * sirve como control de flujo cruzando un limite transaccional.
 */
@Component
public class RegistroDeIntentos {

    private final UsuarioRepository repositorio;
    private final ServicioAuditoria auditoria;
    private final int intentosMaximos;
    private final int minutosDeBloqueo;

    public RegistroDeIntentos(UsuarioRepository repositorio,
                              ServicioAuditoria auditoria,
                              @Value("${sigco.seguridad.intentos-maximos}") int intentosMaximos,
                              @Value("${sigco.seguridad.minutos-bloqueo}") int minutosDeBloqueo) {
        this.repositorio = repositorio;
        this.auditoria = auditoria;
        this.intentosMaximos = intentosMaximos;
        this.minutosDeBloqueo = minutosDeBloqueo;
    }

    /**
     * Anota un intento fallido contra una cuenta que existe.
     *
     * @return true si este intento fue el que bloqueo la cuenta
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean registrarFallo(Long idUsuario) {
        Usuario usuario = repositorio.findById(idUsuario).orElse(null);
        if (usuario == null) {
            return false;
        }

        boolean quedoBloqueada = usuario.registrarIntentoFallido(intentosMaximos, minutosDeBloqueo);

        if (quedoBloqueada) {
            // El bloqueo se audita y el intento suelto no. Un error de tipeo no
            // le interesa a nadie; que una cuenta se haya bloqueado, si: es la
            // señal de que alguien estuvo probando contrasenas.
            auditoria.registrar(usuario,
                    "Cuenta bloqueada por " + intentosMaximos + " intentos fallidos",
                    "Accesos");
        }

        // Hibernate escribe el cambio al confirmar esta transaccion, sin save()
        // explicito: el usuario esta gestionado por el contexto de persistencia.
        return quedoBloqueada;
    }

    public int getIntentosMaximos() {
        return intentosMaximos;
    }

    public int getMinutosDeBloqueo() {
        return minutosDeBloqueo;
    }
}
