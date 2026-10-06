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
        String nombreRubro,

        /**
         * Si este es el rubro de mano de obra.
         *
         * Cambia cómo se presupuesta: su planilla lista los otros rubros en
         * lugar de materiales, para cargar de una sola vez cuánto sale la mano
         * de obra de cada especialidad. Hay uno solo en todo el catálogo.
         *
         * Boolean y no boolean: el dato es OPCIONAL. Con boolean, un pedido que
         * no lo traía (el formulario de "Nuevo rubro" mandaba solo el nombre)
         * fallaba entero con "Cannot map null into type boolean", y no se podía
         * crear ningún rubro. Vacío significa "no" al crear y "dejarlo como
         * está" al renombrar: ver CatalogoService.
         */
        Boolean esManoDeObra) {
}
