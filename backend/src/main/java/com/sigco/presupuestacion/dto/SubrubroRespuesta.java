package com.sigco.presupuestacion.dto;

import com.sigco.presupuestacion.Subrubro;

/** Un subrubro tal como sale de la API. */
public record SubrubroRespuesta(
        Long idSubrubro,
        Long idRubro,
        String nombreRubro,
        String nombreSubrubro,
        String estado) {

    /**
     * Requiere que el rubro este cargado. Las consultas del repositorio lo
     * traen con JOIN FETCH; cuando el subrubro se acaba de crear, el rubro ya
     * esta en memoria porque fue el que se uso para crearlo.
     */
    public static SubrubroRespuesta desde(Subrubro subrubro) {
        return new SubrubroRespuesta(
                subrubro.getIdSubrubro(),
                subrubro.getRubro().getIdRubro(),
                subrubro.getRubro().getNombreRubro(),
                subrubro.getNombreSubrubro(),
                subrubro.getEstado());
    }
}
