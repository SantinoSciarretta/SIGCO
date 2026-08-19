package com.sigco.obras.dto;

import com.sigco.obras.Obra;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Datos de una obra tal como salen de la API.
 *
 * Incluye el nombre del cliente ademas de su identificador, para que el listado
 * pueda mostrarlo sin tener que pedir cada cliente por separado.
 *
 * Aca se ve por que el patron DTO importa: la entidad Obra tiene una relacion
 * hacia Cliente, y Cliente va a tener la relacion inversa hacia sus obras.
 * Convertir eso a JSON directamente entraria en un ciclo infinito: la obra
 * incluiria a su cliente, que incluiria sus obras, que incluirian su cliente.
 */
public record ObraRespuesta(
        Long idObra,
        Long idCliente,
        String nombreCliente,
        String direccionObra,
        String tipoInmueble,
        String tipoObra,
        LocalDate fechaInicioReal,
        LocalDate fechaFinEstimada,
        String notas,
        String estado,
        String motivoCancelacion,
        LocalDateTime fechaCreacion) {

    /**
     * Convierte la entidad en su representacion de salida.
     *
     * Requiere que el cliente de la obra este cargado. Las consultas del
     * repositorio lo traen con JOIN FETCH justamente para eso: si la relacion
     * llegara sin cargar, este metodo dispararia una consulta extra por cada
     * obra de la lista.
     */
    public static ObraRespuesta desde(Obra obra) {
        return new ObraRespuesta(
                obra.getIdObra(),
                obra.getCliente().getIdCliente(),
                obra.getCliente().getNombreApellido(),
                obra.getDireccionObra(),
                obra.getTipoInmueble(),
                obra.getTipoObra(),
                obra.getFechaInicioReal(),
                obra.getFechaFinEstimada(),
                obra.getNotas(),
                obra.getEstado(),
                obra.getMotivoCancelacion(),
                obra.getFechaCreacion());
    }
}
