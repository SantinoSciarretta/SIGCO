import client from '../../api/client';

/** Cliente del módulo Personal. */

/**
 * Pide al servidor los operarios, filtrando opcionalmente por nombre, estado y
 * obra.
 */
export async function listarOperarios({ busqueda, estado, obra } = {}) {
  const respuesta = await client.get('/operarios', {
    params: {
      busqueda: busqueda || undefined,
      estado: estado || undefined,
      obra: obra || undefined,
    },
  });
  return respuesta.data;
}

/**
 * Pide al servidor la ficha completa de un operario.
 */
export async function obtenerOperario(id) {
  const respuesta = await client.get(`/operarios/${id}`);
  return respuesta.data;
}

/**
 * Da de alta un operario nuevo.
 */
export async function crearOperario(datos) {
  const respuesta = await client.post('/operarios', datos);
  return respuesta.data;
}

/**
 * Corrige el nombre o el teléfono de un operario.
 */
export async function actualizarOperario(id, datos) {
  const respuesta = await client.put(`/operarios/${id}`, datos);
  return respuesta.data;
}

/** No existe eliminar: se desactiva y se conserva el historial de obras. */
export async function cambiarEstadoOperario(id, estado) {
  const respuesta = await client.patch(`/operarios/${id}/estado`, { estado });
  return respuesta.data;
}

/**
 * Asigna un operario a una obra.
 */
export async function asignarAObra(id, idObra) {
  const respuesta = await client.post(`/operarios/${id}/asignaciones`, { idObra });
  return respuesta.data;
}

/**
 * Saca al operario de una obra.
 *
 * Es DELETE porque desde afuera la acción es "sacalo de esta obra". Lo que pasa
 * adentro es que la fila queda con fecha de baja: la obra sigue en su historial.
 */
export async function desasignarDeObra(id, idObra) {
  const respuesta = await client.delete(`/operarios/${id}/asignaciones/${idObra}`);
  return respuesta.data;
}

/**
 * Pide al servidor las faltas registradas, filtrando opcionalmente por
 * operario, obra y fechas.
 */
export async function listarInasistencias({ operario, obra, desde, hasta } = {}) {
  const respuesta = await client.get('/inasistencias', {
    params: {
      operario: operario || undefined,
      obra: obra || undefined,
      desde: desde || undefined,
      hasta: hasta || undefined,
    },
  });
  return respuesta.data;
}

/**
 * Registra la falta de un operario.
 */
export async function registrarInasistencia(datos) {
  const respuesta = await client.post('/inasistencias', datos);
  return respuesta.data;
}

/** El motivo se completa después: es el caso normal, no la excepción. */
export async function registrarMotivo(id, motivo) {
  const respuesta = await client.patch(`/inasistencias/${id}/motivo`, { motivo });
  return respuesta.data;
}

/**
 * Escribe una fecha en formato día/mes/año.
 */
export function fecha(valor) {
  if (!valor) return '—';
  const [anio, mes, dia] = String(valor).slice(0, 10).split('-');
  return `${dia}/${mes}/${anio}`;
}
