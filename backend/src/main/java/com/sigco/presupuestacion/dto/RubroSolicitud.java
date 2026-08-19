package com.sigco.presupuestacion.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Alta o cambio de nombre de un rubro.
 *
 * El estado no esta aca: un rubro nuevo nace Activo y despues se activa o
 * desactiva con su propia operacion.
 */
public record RubroSolicitud(

        @NotBlank(message = "El nombre del rubro es obligatorio")
        @Size(max = 100, message = "El nombre no puede superar los 100 caracteres")
        String nombreRubro) {
}
