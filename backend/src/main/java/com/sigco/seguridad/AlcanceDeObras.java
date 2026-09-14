package com.sigco.seguridad;

import com.sigco.accesos.Rol;
import com.sigco.common.exception.ReglaDeNegocioException;
import com.sigco.personal.OperarioObraRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * A que obras alcanza el usuario de esta peticion.
 *
 * ------------------------------------------------------------------
 *  Que problema resuelve
 * ------------------------------------------------------------------
 *
 * Los permisos del modulo Accesos son por MODULO: `compras.ver` habilita el
 * modulo Compras entero. Pero la matriz del informe dice, para el Capataz de
 * Obra, "Total (su obra)" y "Consulta (su obra)".
 *
 * Esa aclaracion entre parentesis no se puede expresar con un permiso: un
 * permiso responde "¿puede entrar a Compras?", y lo que falta responder es
 * "¿puede ver ESTE pedido?". Son dos preguntas distintas y hacen falta las dos.
 *
 * ------------------------------------------------------------------
 *  Como se resuelve
 * ------------------------------------------------------------------
 *
 * El Capataz de Obra tiene su cuenta vinculada a un registro de Personal (el
 * campo opcional `usuario.id_operario`), y Personal sabe a que obras esta
 * asignado hoy (`operario_obra` con `fecha_desasignacion IS NULL`).
 *
 * Ese vinculo, que existia desde el modulo 8 sin un uso claro, es exactamente
 * lo que hace falta: la cadena usuario -> operario -> obras asignadas.
 *
 * Los otros dos roles no tienen limite de obras: el dueño ve todo por
 * definicion, y el Capataz General "ve el seguimiento de todas las obras
 * activas" segun el informe.
 */
@Component
public class AlcanceDeObras {

    private final SesionActual sesion;
    private final OperarioObraRepository asignaciones;

    public AlcanceDeObras(SesionActual sesion, OperarioObraRepository asignaciones) {
        this.sesion = sesion;
        this.asignaciones = asignaciones;
    }

    /**
     * Las obras que el usuario puede ver, o vacio si puede ver todas.
     *
     * Devolver un Optional vacio para "todas" y no la lista completa de obras
     * es deliberado: la lista completa obligaria a consultarlas todas en cada
     * peticion del dueño, que es el caso mas frecuente, solo para no usarlas.
     */
    public Optional<List<Long>> obrasPermitidas() {
        if (!esCapatazDeObra()) {
            return Optional.empty();
        }

        // Un capataz de obra SIN operario vinculado no alcanza ninguna obra.
        // Es lo correcto: sin el vinculo no hay forma de saber cual es la suya,
        // y suponer que son todas seria darle justo lo que la regla le niega.
        return Optional.of(sesion.idOperario()
                .map(asignaciones::obrasVigentesDe)
                .orElseGet(List::of));
    }

    /** Si el usuario alcanza esa obra. */
    public boolean alcanza(Long idObra) {
        return obrasPermitidas()
                .map(permitidas -> permitidas.contains(idObra))
                .orElse(true);
    }

    /**
     * Falla si la obra esta fuera del alcance del usuario.
     *
     * El mensaje NO dice "esa obra existe pero no es tuya": nombrar una obra
     * ajena ya es informacion. Dice lo mismo para una obra que no le
     * corresponde y para una que no existe.
     */
    public void exigirAlcance(Long idObra) {
        if (!alcanza(idObra)) {
            throw new ReglaDeNegocioException(
                    "Solo podés operar sobre las obras que tenés asignadas.");
        }
    }

    private boolean esCapatazDeObra() {
        return sesion.autenticado()
                .map(u -> Rol.CAPATAZ_DE_OBRA.equals(u.getNombreRol()))
                .orElse(false);
    }
}
