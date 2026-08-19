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
 *  - El cliente no se cambia: la obra pertenece a quien la encargo.
 *  - El tipo de obra queda bloqueado apenas existe un presupuesto, porque
 *    determina el circuito de Presupuestacion. Como ese modulo todavia no
 *    existe, se toma la posicion mas segura y no se permite cambiarlo.
 *
 * Al no estar los campos en el objeto, la regla no depende de que alguien se
 * acuerde de validarla: no hay forma de enviarlos.
 */
public record ObraEdicion(

        @NotBlank(message = "La direccion de la obra es obligatoria")
        @Size(max = 200, message = "La direccion no puede superar los 200 caracteres")
        String direccionObra,

        @NotBlank(message = "El tipo de inmueble es obligatorio")
        @Pattern(regexp = "Casa|Departamento|Local",
                 message = "El tipo de inmueble debe ser 'Casa', 'Departamento' o 'Local'")
        String tipoInmueble,

        // Solo se admite si la obra ya salio de "En presupuestacion".
        // La comprobacion la hace el servicio.
        LocalDate fechaInicioReal,

        LocalDate fechaFinEstimada,

        String notas) {
}
