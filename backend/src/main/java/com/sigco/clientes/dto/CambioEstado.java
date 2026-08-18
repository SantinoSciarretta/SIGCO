package com.sigco.clientes.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Cuerpo del pedido para activar o desactivar un cliente.
 *
 * Va como objeto y no como un valor suelto en la direccion para que el conjunto
 * de estados validos quede declarado en un solo lugar y validado por el
 * servidor, igual que el resto de los campos.
 */
public record CambioEstado(

        @NotBlank(message = "El estado es obligatorio")
        @Pattern(regexp = "Activo|Inactivo",
                 message = "El estado debe ser 'Activo' o 'Inactivo'")
        String estado) {
}
