import client from '../../api/client';

/**
 * Llamadas al módulo Proveedores.
 *
 * Cubre las tres cosas que hoy se pierden: a quién pedirle en cada zona, a
 * cuánto cotizó cada uno, y cómo se comportó en los pedidos anteriores.
 */

export async function listarProveedores({ busqueda, zona, estado } = {}) {
  const respuesta = await client.get('/proveedores', {
    params: {
      busqueda: busqueda || undefined,
      zona: zona || undefined,
      estado: estado || undefined,
    },
  });
  return respuesta.data;
}

/** Zonas ya cargadas, para ofrecerlas como filtro sin tener que escribirlas. */
export async function listarZonas() {
  const respuesta = await client.get('/proveedores/zonas');
  return respuesta.data;
}

export async function crearProveedor(datos) {
  const respuesta = await client.post('/proveedores', datos);
  return respuesta.data;
}

export async function actualizarProveedor(id, datos) {
  const respuesta = await client.put(`/proveedores/${id}`, datos);
  return respuesta.data;
}

/** No hay baja: borrarlo eliminaría su historial de precios y comportamiento. */
export async function cambiarEstadoProveedor(id, estado) {
  const respuesta = await client.patch(`/proveedores/${id}/estado`, { estado });
  return respuesta.data;
}

/**
 * Registra un precio informado por un proveedor.
 *
 * No reemplaza la cotización anterior: se agrega una nueva. Así queda la
 * evolución del precio, que con inflación es información útil por sí misma.
 */
export async function registrarCotizacion(idProveedor, datos) {
  const respuesta = await client.post(`/proveedores/${idProveedor}/cotizaciones`, datos);
  return respuesta.data;
}

/** Historial completo de cotizaciones de un proveedor. */
export async function historialDeCotizaciones(idProveedor) {
  const respuesta = await client.get(`/proveedores/${idProveedor}/cotizaciones`);
  return respuesta.data;
}

/**
 * COMPARADOR: para un material, la última cotización de cada proveedor activo,
 * de menor a mayor precio.
 *
 * Cuelga de la dirección del material porque así es como se lo consulta: se
 * parte de "necesito cemento" y se pregunta quién lo ofrece.
 */
export async function compararCotizaciones(idMaterial) {
  const respuesta = await client.get(`/materiales/${idMaterial}/cotizaciones`);
  return respuesta.data;
}

export async function registrarObservacion(idProveedor, datos) {
  const respuesta = await client.post(`/proveedores/${idProveedor}/observaciones`, datos);
  return respuesta.data;
}

export async function observacionesDe(idProveedor) {
  const respuesta = await client.get(`/proveedores/${idProveedor}/observaciones`);
  return respuesta.data;
}

/** Importe con formato argentino. */
export function pesos(monto) {
  if (monto === null || monto === undefined) return '—';
  return '$ ' + Number(monto).toLocaleString('es-AR', {
    minimumFractionDigits: 2, maximumFractionDigits: 2,
  });
}

/** "2026-08-19T10:30:00" → "19/08/26" */
export function fechaCorta(iso) {
  if (!iso) return '—';
  const f = new Date(iso);
  return f.toLocaleDateString('es-AR', { day: '2-digit', month: '2-digit', year: '2-digit' });
}

/**
 * Antigüedad de una cotización en días.
 *
 * El informe pide poder distinguir las recientes de las que pueden estar
 * desactualizadas: con inflación, un precio de hace ocho meses no sirve para
 * decidir.
 */
export function diasDesde(iso) {
  if (!iso) return null;
  return Math.floor((Date.now() - new Date(iso).getTime()) / 86400000);
}
