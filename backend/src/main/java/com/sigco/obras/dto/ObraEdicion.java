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

        LocalDate fechaFinEstimada,

        String notas) {
}
