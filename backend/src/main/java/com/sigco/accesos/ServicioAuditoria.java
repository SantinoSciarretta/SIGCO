package com.sigco.accesos;

import com.sigco.seguridad.SesionActual;
import com.sigco.usuarios.Usuario;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Escribe la traza de las acciones sensibles.
 *
 * ------------------------------------------------------------------
 *  Que se audita y que no
 * ------------------------------------------------------------------
 *
 * Solo lo sensible, que es lo que el informe pide y ademas lo unico util: si se
 * registrara cada consulta, la tabla creceria sin limite y encontrar lo que
 * importa seria imposible.
 *
 * El criterio para decidir si una accion se audita: si el dia de mañana alguien
 * pregunta "quien hizo esto", ¿la respuesta importa? Aprobar un presupuesto,
 * aprobar un pedido, anular un gasto, registrar un pago, cambiar permisos y dar
 * de baja una cuenta entran. Listar clientes, no.
 *
 * ------------------------------------------------------------------
 *  Por que va en la misma transaccion
 * ------------------------------------------------------------------
 *
 * El registro se escribe dentro de la transaccion de la accion que lo genera.
 * Si la accion falla y se deshace, su registro de auditoria se deshace tambien:
 * de lo contrario la auditoria afirmaria que pasaron cosas que no pasaron, que
 * es peor que no tener auditoria.
 */
@Service
public class ServicioAuditoria {

    private final RegistroAuditoriaRepository repositorio;
    private final SesionActual sesion;

    public ServicioAuditoria(RegistroAuditoriaRepository repositorio, SesionActual sesion) {
        this.repositorio = repositorio;
        this.sesion = sesion;
    }

    /**
     * Registra una accion del usuario de la peticion actual.
     *
     * Si no hay sesion, no escribe nada y no falla. Es a proposito: la
     * auditoria no puede ser el motivo por el que una operacion de negocio se
     * cae. Que una accion llegue sin usuario es un problema de la seguridad
     * —y con la cadena de filtros activa no deberia pasar—, no del modulo que
     * la ejecuto.
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void registrar(String accion, String modulo) {
        sesion.usuario().ifPresent(usuario -> registrar(usuario, accion, modulo));
    }

    /**
     * Registra una accion de un usuario concreto.
     *
     * Hace falta para el ingreso al sistema: en ese momento la peticion todavia
     * no esta autenticada —justamente se esta autenticando—, asi que no se
     * puede tomar el usuario de la sesion.
     */
    @Transactional
    public void registrar(Usuario usuario, String accion, String modulo) {
        repositorio.save(new RegistroAuditoria(usuario, recortar(accion), modulo));
    }

    /**
     * La columna admite 150 caracteres. Se recorta en lugar de fallar: perder
     * el final de una descripcion es mejor que perder el registro entero por
     * una descripcion larga.
     */
    private String recortar(String accion) {
        return accion.length() <= 150 ? accion : accion.substring(0, 147) + "...";
    }
}
