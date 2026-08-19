package com.sigco.clientes;

import com.sigco.clientes.dto.ClienteRespuesta;
import com.sigco.clientes.dto.ClienteSolicitud;
import com.sigco.common.exception.RecursoNoEncontradoException;
import com.sigco.obras.ObraRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Logica de negocio del modulo Clientes.
 *
 * Es la capa donde viven las reglas. El controlador solo traduce HTTP y el
 * repositorio solo habla con la base; toda decision sobre que se puede y que no
 * se puede hacer esta aca, del lado del servidor.
 *
 * Consulta ademas al repositorio de Obras para contar cuantos proyectos tiene
 * cada cliente, que es un dato que el informe pide mostrar en el listado. Es la
 * unica dependencia de este modulo hacia otro, y es de solo lectura.
 */
@Service
public class ClienteService {

    private final ClienteRepository repositorio;
    private final ObraRepository obraRepositorio;

    public ClienteService(ClienteRepository repositorio, ObraRepository obraRepositorio) {
        this.repositorio = repositorio;
        this.obraRepositorio = obraRepositorio;
    }

    /**
     * Listado con filtros. Los tres parametros son opcionales y combinables.
     *
     * Un texto vacio se trata igual que uno ausente: si el usuario borra lo que
     * escribio en el buscador, espera ver todos los clientes, no ninguno.
     */
    @Transactional(readOnly = true)
    public List<ClienteRespuesta> listar(String busqueda, String origen, String estado) {
        // Los filtros ausentes viajan como cadena vacia, nunca como null
        // (ver el comentario de ClienteRepository.buscar).
        List<Cliente> clientes = repositorio.buscar(
                sinFiltro(busqueda), sinFiltro(origen), sinFiltro(estado));

        // Una sola consulta agrupada para todos los clientes de la lista, en
        // lugar de preguntar uno por uno: con cien clientes serian cien
        // consultas extra (el problema N+1).
        Map<Long, Long> obrasPorCliente = contarObrasPorCliente();

        return clientes.stream()
                .map(cliente -> ClienteRespuesta.desde(
                        cliente,
                        obrasPorCliente.getOrDefault(cliente.getIdCliente(), 0L)))
                .toList();
    }

    @Transactional(readOnly = true)
    public ClienteRespuesta obtener(Long id) {
        Cliente cliente = buscarOFallar(id);
        return ClienteRespuesta.desde(cliente, obraRepositorio.countByClienteIdCliente(id));
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

        // Un cliente recien creado no tiene obras todavia.
        return ClienteRespuesta.desde(repositorio.save(cliente), 0L);
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
        return ClienteRespuesta.desde(cliente, obraRepositorio.countByClienteIdCliente(id));
    }

    /**
     * Marca el cliente como activo o inactivo.
     *
     * El informe es explicito: un cliente NO se elimina, se desactiva. La baja
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

        return ClienteRespuesta.desde(cliente, obraRepositorio.countByClienteIdCliente(id));
    }

    // ------------------------------------------------------------------

    /** Arma el mapa cliente -> cantidad de obras a partir de la consulta agrupada. */
    private Map<Long, Long> contarObrasPorCliente() {
        Map<Long, Long> conteo = new HashMap<>();
        for (Object[] fila : obraRepositorio.contarPorCliente()) {
            conteo.put((Long) fila[0], (Long) fila[1]);
        }
        return conteo;
    }

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

    /**
     * Valor de un filtro para la consulta: el texto recortado, o cadena vacia
     * cuando no hay filtro. Nunca null, por lo explicado en el repositorio.
     */
    private String sinFiltro(String texto) {
        if (texto == null || texto.isBlank()) {
            return "";
        }
        return texto.trim();
    }
}
