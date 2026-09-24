package com.sigco.obras.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Cambio de estado de una obra.
 *
 * No incluye "En presupuestacion" entre los destinos posibles: es el estado
 * con el que la obra nace y no se vuelve a el.
 */
public record CambioEstadoObra(

        @NotBlank(message = "El estado es obligatorio")
        @Pattern(regexp = "En ejecución|Finalizada|Cancelada",
                 message = "El estado debe ser 'En ejecución', 'Finalizada' o 'Cancelada'")
        String estado,

        /** Obligatorio cuando el estado es Cancelada. Lo controla el servicio. */
        @Size(max = 200, message = "El motivo no puede superar los 200 caracteres")
        String motivoCancelacion,

        /** Se carga al pasar a "En ejecución", cuando arrancan los trabajos. */
        LocalDate fechaInicioReal,

        /**
         * Confirmación para cancelar una obra que ya tiene presupuesto
         * definitivo aprobado.
         *
         * El informe dice que esa obra no se cancela "salvo autorización
         * explícita del dueño". No alcanza con que el rol sea Dueño —solo el
         * dueño llega a esta pantalla de todos modos—: lo que hace falta es que
         * la decisión sea deliberada y no un clic de más. Este campo es esa
         * confirmación: el servicio rechaza la cancelación si no viene en true.
         */
        boolean confirmaObraEnEjecucion,

        /**
         * Confirmación para dar por terminada una obra que todavía tiene hitos
         * sin completar.
         *
         * Ricardo pidió un botón de "obra terminada" para ver el balance. El
         * riesgo de ese botón es cerrar una obra que en realidad sigue: al
         * quedar Finalizada, sus hitos se bloquean y no se puede seguir
         * cargando el avance. Cuando hay hitos pendientes el servicio pide esta
         * confirmación, porque o bien la obra terminó y esos hitos quedaron sin
         * marcar, o bien no terminó.
         *
         * Cuando no hay hitos pendientes —el caso normal— no hace falta.
         */
        boolean confirmaHitosPendientes) {
}
