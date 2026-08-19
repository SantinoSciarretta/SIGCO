import client from '../../api/client';

/**
 * Llamadas al catálogo de materiales.
 *
 * Es el catálogo único que consumen Presupuestación y Compras. Que exista una
 * sola lista es lo que evita los nombres escritos a mano que hoy cambian de una
 * obra a otra.
 */

/** GET /api/materiales — vista de mantenimiento: incluye los inactivos. */
export async function listarMateriales({ busqueda, rubro, estado } = {}) {
  const respuesta = await client.get('/materiales', {
    params: {
      busqueda: busqueda || undefined,
      rubro: rubro || undefined,
      estado: estado || undefined,
    },
  });
  return respuesta.data;
}

/**
 * GET /api/materiales?disponibles=true
 *
 * Solo los que se pueden elegir en un presupuesto o pedido nuevo: activos y con
 * su rubro activo. Es la vista que van a consumir Presupuestación y Compras.
 */
export async function listarMaterialesDisponibles(rubro) {
  const respuesta = await client.get('/materiales', {
    params: { disponibles: true, rubro: rubro || undefined },
  });
  return respuesta.data;
}

export async function crearMaterial(datos) {
  const respuesta = await client.post('/materiales', datos);
  return respuesta.data;
}

export async function actualizarMaterial(id, datos) {
  const respuesta = await client.put(`/materiales/${id}`, datos);
  return respuesta.data;
}

/** No hay baja: un material usado en un pedido no puede borrarse, se desactiva. */
export async function cambiarEstadoMaterial(id, estado) {
  const respuesta = await client.patch(`/materiales/${id}/estado`, { estado });
  return respuesta.data;
}

/**
 * Unidades habituales de la empresa.
 *
 * Es una lista SUGERIDA, no cerrada: el informe la enumera como "Unidad /
 * Bolsa / Metro cuadrado / Metro lineal / Litro, entre otras". Por eso el campo
 * del formulario permite escribir una que no esté acá.
 */
export const UNIDADES_SUGERIDAS = [
  'unidad', 'bolsa', 'bolsa 50 kg', 'bolsa 25 kg', 'm²', 'm³', 'ml',
  'litro', 'kg', 'rollo', 'barra', 'chapa', 'balde',
];
