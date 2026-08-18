package com.sigco.clientes.dto;

import com.sigco.clientes.Cliente;
import java.time.LocalDateTime;

/**
 * Datos de un cliente tal como salen de la API.
 *
 * El controlador nunca devuelve la entidad JPA directa. Aca eso todavia no se
 * nota, porque el cliente no tiene relaciones, pero el patron se sostiene
 * desde el primer modulo por dos razones que si van a pesar despues: evita
 * exponer campos internos (como el valor unitario de un item de presupuesto,
 * que segun el informe no debe llegar al cliente) y evita los ciclos infinitos
 * al convertir a JSON una relacion de ida y vuelta como cliente-obras.
 *
 * PENDIENTE: el informe pide mostrar en el listado la cantidad de obras de
 * cada cliente. Ese campo se agrega al desarrollar el modulo Obras; no se
 * incluye ahora devolviendo un cero fijo, porque seria un dato falso.
 */
public record ClienteRespuesta(
        Long idCliente,
        String nombreApellido,
        String telefonoContacto,
        String emailContacto,
        String origenRecomendacion,
        String recomendadoPor,
        String estado,
        LocalDateTime fechaAlta) {

    /** Convierte la entidad en su representacion de salida. */
    public static ClienteRespuesta desde(Cliente cliente) {
        return new ClienteRespuesta(
                cliente.getIdCliente(),
                cliente.getNombreApellido(),
                cliente.getTelefonoContacto(),
                cliente.getEmailContacto(),
                cliente.getOrigenRecomendacion(),
                cliente.getRecomendadoPor(),
                cliente.getEstado(),
                cliente.getFechaAlta());
    }
}
