import client from '../../api/client';

/**
 * Llamadas al backend del modulo Clientes.
 *
 * Los componentes piden datos por el nombre de la operacion y nunca conocen la
 * direccion: si manana cambia un endpoint, se toca solo este archivo.
 */

/** GET /api/clientes con los filtros del listado (todos opcionales). */
export async function listarClientes({ busqueda, origen, estado } = {}) {
  // Axios descarta solo los parametros que llegan en undefined, asi que los
  // filtros vacios no viajan en la direccion.
  const respuesta = await client.get('/clientes', {
    params: {
      busqueda: busqueda || undefined,
      origen: origen || undefined,
      estado: estado || undefined,
    },
  });
  return respuesta.data;
}

/** GET /api/clientes/{id} */
export async function obtenerCliente(id) {
  const respuesta = await client.get(`/clientes/${id}`);
  return respuesta.data;
}

/** POST /api/clientes */
export async function crearCliente(datos) {
  const respuesta = await client.post('/clientes', datos);
  return respuesta.data;
}

/** PUT /api/clientes/{id} */
export async function actualizarCliente(id, datos) {
  const respuesta = await client.put(`/clientes/${id}`, datos);
  return respuesta.data;
}

/**
 * PATCH /api/clientes/{id}/estado
 *
 * No existe una operacion de baja: el informe establece que un cliente no se
 * elimina, se marca como inactivo, para no perder el historial de sus obras.
 */
export async function cambiarEstadoCliente(id, estado) {
  const respuesta = await client.patch(`/clientes/${id}/estado`, { estado });
  return respuesta.data;
}

/** Valores validos de origen, los mismos que valida el backend. */
export const ORIGENES = ['Cliente anterior', 'Arquitecto', 'Otro'];
