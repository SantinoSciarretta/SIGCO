package com.sigco.obras.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Datos que se pueden modificar de una obra ya creada.
 *
 * Es un objeto distinto del alta a proposito, y la diferencia es la regla de
 * negocio: aca NO estan ni el cliente ni el tipo de obra.
 *
 *  - El cliente no se cambia: la obra pertenece a quien la encargo. Al no
 *    estar en el objeto, la regla no depende de que alguien se acuerde de
 *    validarla: no hay forma de enviarlo.
 *  - El tipo de obra SI se puede corregir, pero solo mientras la obra no tenga
 *    ningun presupuesto. Es la regla del informe, que lo bloquea "apenas existe
 *    un presupuesto de anteproyecto o definitivo" porque determina el circuito
 *    de Presupuestacion. Hasta la auditoria del 22/09 no se podia cambiar
 *    nunca, y una obra cargada con el tipo equivocado no tenia arreglo: habia
 *    que cancelarla y rehacerla. La comprobacion la hace el servicio, que es
 *    quien puede consultar los presupuestos.
 */
public record ObraEdicion(

        @NotBlank(message = "La direccion de la obra es obligatoria")
        @Size(max = 200, message = "La direccion no puede superar los 200 caracteres")
        String direccionObra,

        @NotBlank(message = "El tipo de inmueble es obligatorio")
        @Pattern(regexp = "Casa|Departamento|Local",
                 message = "El tipo de inmueble debe ser 'Casa', 'Departamento' o 'Local'")
        String tipoInmueble,

        /**
         * Opcional: si no viene, el tipo de obra no se toca.
         *
         * Solo se admite mientras la obra no tenga ningun presupuesto generado.
         */
        @Pattern(regexp = "Construcción|Reforma",
                 message = "El tipo de obra debe ser 'Construcción' o 'Reforma'")
        String tipoObra,

        // Solo se admite si la obra ya salio de "En presupuestacion".
        // La comprobacion la hace el servicio.
        LocalDate fechaInicioReal,

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

        /** Se ignora si hay plazo en meses: ahí la calcula el sistema. */
        LocalDate fechaFinEstimada,

        String notas) {
}
