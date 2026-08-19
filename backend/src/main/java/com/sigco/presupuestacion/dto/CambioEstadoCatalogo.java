package com.sigco.presupuestacion.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Activa o desactiva un rubro o un subrubro. */
public record CambioEstadoCatalogo(

        @NotBlank(message = "El estado es obligatorio")
        @Pattern(regexp = "Activo|Inactivo",
                 message = "El estado debe ser 'Activo' o 'Inactivo'")
        String estado) {
}
