package com.sigco.clientes.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Datos que llegan del formulario al dar de alta o editar un cliente.
 *
 * Las anotaciones de validacion son la version autoritativa de las reglas: se
 * ejecutan en el servidor antes de tocar la base, asi que no se pueden saltear
 * modificando el navegador. El formulario del frontend repite estas mismas
 * comprobaciones, pero solo para avisar al usuario en el momento.
 *
 * El estado y la fecha de alta no estan aca a proposito: no los elige quien
 * carga el formulario. Un cliente nace Activo con la fecha del momento, y el
 * estado despues se cambia con su propia operacion.
 *
 * Es un "record" de Java 17: clase inmutable cuyo constructor y metodos de
 * acceso genera el compilador.
 */
public record ClienteSolicitud(

        @NotBlank(message = "El nombre del cliente es obligatorio")
        @Size(max = 150, message = "El nombre no puede superar los 150 caracteres")
        String nombreApellido,

        @Size(max = 30, message = "El telefono no puede superar los 30 caracteres")
        String telefonoContacto,

        // @Email acepta el valor nulo: el correo es opcional segun el informe,
        // pero si viene cargado tiene que tener forma de correo.
        @Email(message = "El correo electronico no tiene un formato valido")
        @Size(max = 100, message = "El correo no puede superar los 100 caracteres")
        String emailContacto,

        // Conjunto cerrado de valores, el mismo que valida la restriccion
        // ck_cliente_origen de la base. Al ser opcional, un valor nulo pasa.
        @Pattern(regexp = "Cliente anterior|Arquitecto|Otro",
                 message = "El origen debe ser 'Cliente anterior', 'Arquitecto' u 'Otro'")
        String origenRecomendacion,

        @Size(max = 150, message = "El nombre de quien recomendo no puede superar los 150 caracteres")
        String recomendadoPor) {
}
