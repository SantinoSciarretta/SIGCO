package com.sigco.obras.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Datos del alta de una obra.
 *
 * Los tres campos obligatorios son los que exige el informe: cliente,
 * direccion y tipo de obra. Se suma tipo de inmueble, que el formulario del
 * informe tambien marca como obligatorio.
 *
 * El estado y la fecha de creacion no estan aca: toda obra nace
 * "En presupuestacion" con la fecha del momento. La fecha de inicio real
 * tampoco, porque no puede cargarse hasta que el presupuesto definitivo este
 * aprobado.
 */
public record ObraSolicitud(

        @NotNull(message = "La obra tiene que estar asociada a un cliente")
        Long idCliente,

        @NotBlank(message = "La direccion de la obra es obligatoria")
        @Size(max = 200, message = "La direccion no puede superar los 200 caracteres")
        String direccionObra,

        @NotBlank(message = "El tipo de inmueble es obligatorio")
        @Pattern(regexp = "Casa|Departamento|Local",
                 message = "El tipo de inmueble debe ser 'Casa', 'Departamento' o 'Local'")
        String tipoInmueble,

        // Define si el circuito de Presupuestacion incluye la etapa de
        // anteproyecto, que corresponde solo a las reformas.
        @NotBlank(message = "El tipo de obra es obligatorio")
        @Pattern(regexp = "Construcción|Reforma",
                 message = "El tipo de obra debe ser 'Construcción' o 'Reforma'")
        String tipoObra,

        /**
         * Cuándo se estima que arranca la obra.
         *
         * Distinta de la fecha de inicio real, que no se puede cargar hasta que
         * el presupuesto definitivo esté aprobado. Esta es del momento del alta
         * y no compromete nada.
         */
        LocalDate fechaInicioEstimada,

        /**
         * Cuántos meses se estima que dura.
         *
         * Con esto y la fecha de inicio, el sistema calcula la fecha tentativa
         * de finalización: no hay que escribirla a mano.
         */
        @jakarta.validation.constraints.Min(value = 1, message = "El plazo tiene que ser de al menos un mes")
        @jakarta.validation.constraints.Max(value = 120, message = "El plazo no puede superar los 120 meses")
        Integer mesesEstimados,

        /**
         * Solo si NO se carga el plazo en meses.
         *
         * Con plazo cargado, esta fecha la calcula el sistema y lo que venga
         * acá se ignora: si se aceptaran las dos, podrían contradecirse.
         */
        LocalDate fechaFinEstimada,

        String notas) {
}
