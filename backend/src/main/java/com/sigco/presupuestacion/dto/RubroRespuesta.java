package com.sigco.presupuestacion.dto;

import com.sigco.presupuestacion.Rubro;
import java.util.List;

/**
 * Un rubro con sus subrubros anidados.
 *
 * El catalogo se muestra siempre asi, como un arbol de dos niveles, porque es
 * como se lo piensa: "Albanileria, y adentro Demolicion y Contrapisos". Anidar
 * los subrubros en la respuesta evita que la pantalla tenga que hacer una
 * segunda llamada y despues cruzarlos por identificador.
 */
public record RubroRespuesta(
        Long idRubro,
        String nombreRubro,
        String estado,
        /** Si es EL rubro de mano de obra: su planilla lista rubros, no materiales. */
        boolean esManoDeObra,
        List<SubrubroRespuesta> subrubros) {

    /** Requiere que los subrubros esten cargados (consulta con JOIN FETCH). */
    public static RubroRespuesta desde(Rubro rubro) {
        return new RubroRespuesta(
                rubro.getIdRubro(),
                rubro.getNombreRubro(),
                rubro.getEstado(),
                rubro.esManoDeObra(),
                rubro.getSubrubros().stream().map(SubrubroRespuesta::desde).toList());
    }

    /** Version sin subrubros, para cuando la coleccion no esta cargada. */
    public static RubroRespuesta soloRubro(Rubro rubro) {
        return new RubroRespuesta(
                rubro.getIdRubro(),
                rubro.getNombreRubro(),
                rubro.getEstado(),
                rubro.esManoDeObra(),
                List.of());
    }
}
