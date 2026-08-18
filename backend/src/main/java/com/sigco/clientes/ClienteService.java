package com.sigco.clientes;

import com.sigco.clientes.dto.ClienteRespuesta;
import com.sigco.clientes.dto.ClienteSolicitud;
import com.sigco.common.exception.RecursoNoEncontradoException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Logica de negocio del modulo Clientes.
 *
 * Es la capa donde viven las reglas. El controlador solo traduce HTTP y el
 * repositorio solo habla con la base; toda decision sobre que se puede y que no
 * se puede hacer esta aca, del lado del servidor.
 */
@Service
public class ClienteService {

    private final ClienteRepository repositorio;

    public ClienteService(ClienteRepository repositorio) {
        this.repositorio = repositorio;
    }

    /**
     * Listado con filtros. Los tres parametros son opcionales y combinables.
     *
     * Un texto vacio se trata igual que uno ausente: si el usuario borra lo que
     * escribio en el buscador, espera ver todos los clientes, no ninguno.
     */
    @Transactional(readOnly = true)
    public List<ClienteRespuesta> listar(String busqueda, String origen, String estado) {
        return repositorio.buscar(normalizar(busqueda), normalizar(origen), normalizar(estado))
                .stream()
                .map(ClienteRespuesta::desde)
                .toList();
    }

    @Transactional(readOnly = true)
    public ClienteRespuesta obtener(Long id) {
        return ClienteRespuesta.desde(buscarOFallar(id));
    }

    /**
     * Alta de un cliente.
     *
     * Es el mismo metodo que usa el alta rapida desde el modulo Obras. El
     * informe lo pide explicitamente: el formulario reducido de Obras manda
     * menos campos, pero pasa por esta misma logica, para no terminar con dos
     * reglas de negocio distintas segun desde donde se cargue el cliente.
     */
    @Transactional
    public ClienteRespuesta crear(ClienteSolicitud solicitud) {
        Cliente cliente = new Cliente(
                solicitud.nombreApellido().trim(),
                normalizar(solicitud.telefonoContacto()),
                normalizar(solicitud.emailContacto()),
                normalizar(solicitud.origenRecomendacion()),
                normalizar(solicitud.recomendadoPor()));

        return ClienteRespuesta.desde(repositorio.save(cliente));
    }

    /**
     * Edicion de los datos de contacto.
     *
     * No toca el estado ni la fecha de alta: el estado tiene su propia
     * operacion y la fecha de alta no se modifica nunca.
     */
    @Transactional
    public ClienteRespuesta actualizar(Long id, ClienteSolicitud solicitud) {
        Cliente cliente = buscarOFallar(id);

        cliente.actualizarDatos(
                solicitud.nombreApellido().trim(),
                normalizar(solicitud.telefonoContacto()),
                normalizar(solicitud.emailContacto()),
                normalizar(solicitud.origenRecomendacion()),
                normalizar(solicitud.recomendadoPor()));

        // No hace falta llamar a save(): dentro de una transaccion, Hibernate
        // detecta el cambio sobre una entidad que ya esta en su contexto y
        // escribe el UPDATE al cerrar. Se lo llama "dirty checking".
        return ClienteRespuesta.desde(cliente);
    }

    /**
     * Marca el cliente como activo o inactivo.
     *
     * El informe es explicito: un cliente NO se elimina, se desactiva. Aunque
     * hoy nada impediria borrarlo (todavia no existe el modulo Obras), la baja
     * fisica romperia la trazabilidad de los proyectos anteriores, que es
     * justamente lo que este modulo viene a resolver. Por eso el servicio no
     * expone ninguna operacion de borrado.
     */
    @Transactional
    public ClienteRespuesta cambiarEstado(Long id, String nuevoEstado) {
        Cliente cliente = buscarOFallar(id);

        if (Cliente.ESTADO_ACTIVO.equals(nuevoEstado)) {
            cliente.activar();
        } else {
            cliente.desactivar();
        }

        return ClienteRespuesta.desde(cliente);
    }

    // ------------------------------------------------------------------

    /** Busca el cliente o lanza el error que el manejador global traduce a 404. */
    private Cliente buscarOFallar(Long id) {
        return repositorio.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Cliente", id));
    }

    /**
     * Convierte a null los textos vacios o en blanco.
     *
     * Importa para la busqueda (un filtro vacio no debe filtrar nada) y para el
     * guardado: es preferible una columna en null antes que una cadena vacia,
     * porque "sin dato" y "dato vacio" son la misma cosa y conviene que se
     * guarden de una sola manera.
     */
    private String normalizar(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        return texto.trim();
    }
}
