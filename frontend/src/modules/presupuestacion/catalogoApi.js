import client from '../../api/client';

/**
 * Llamadas al catálogo de rubros y subrubros.
 *
 * Es el paso previo a cualquier presupuesto: el informe aclara que el catálogo
 * se mantiene una vez y se reutiliza, no se rehace por cada obra.
 */

/** GET /api/rubros — cada rubro con sus subrubros anidados. */
export async function listarRubros({ busqueda, estado } = {}) {
  const respuesta = await client.get('/rubros', {
    params: {
      busqueda: busqueda || undefined,
      estado: estado || undefined,
    },
  });
  return respuesta.data;
}

/** POST /api/rubros */
export async function crearRubro(nombreRubro) {
  const respuesta = await client.post('/rubros', { nombreRubro });
  return respuesta.data;
}

/** PUT /api/rubros/{id} — lo único editable es el nombre. */
export async function renombrarRubro(id, nombreRubro) {
  const respuesta = await client.put(`/rubros/${id}`, { nombreRubro });
  return respuesta.data;
}

/** PATCH /api/rubros/{id}/estado */
export async function cambiarEstadoRubro(id, estado) {
  const respuesta = await client.patch(`/rubros/${id}/estado`, { estado });
  return respuesta.data;
}

/** POST /api/rubros/{idRubro}/subrubros */
export async function crearSubrubro(idRubro, nombreSubrubro) {
  const respuesta = await client.post(`/rubros/${idRubro}/subrubros`, { nombreSubrubro });
  return respuesta.data;
}

/** PUT /api/subrubros/{id} */
export async function renombrarSubrubro(id, nombreSubrubro) {
  const respuesta = await client.put(`/subrubros/${id}`, { nombreSubrubro });
  return respuesta.data;
}

/** PATCH /api/subrubros/{id}/estado */
export async function cambiarEstadoSubrubro(id, estado) {
  const respuesta = await client.patch(`/subrubros/${id}/estado`, { estado });
  return respuesta.data;
}

/**
 * GET /api/subrubros?rubro={id}
 *
 * Solo los que se pueden usar en un presupuesto nuevo: activos y con su rubro
 * activo. Lo va a consumir la carga de ítems en la Parte B del módulo.
 */
export async function listarSubrubrosDisponibles(idRubro) {
  const respuesta = await client.get('/subrubros', {
    params: { rubro: idRubro || undefined },
  });
  return respuesta.data;
}
