package com.sigco.presupuestacion.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Alta o cambio de nombre de un subrubro.
 *
 * El rubro al que pertenece no viaja en el cuerpo: va en la direccion
 * (POST /api/rubros/{idRubro}/subrubros). Es lo que corresponde en REST cuando
 * el recurso existe siempre dentro de otro, y de paso hace imposible mover un
 * subrubro de rubro por accidente al editarlo.
 */
public record SubrubroSolicitud(

        @NotBlank(message = "El nombre del subrubro es obligatorio")
        @Size(max = 100, message = "El nombre no puede superar los 100 caracteres")
        String nombreSubrubro) {
}
