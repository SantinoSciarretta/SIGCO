import client from '../../api/client';

/**
 * Llamadas al backend de presupuestos.
 */

export async function listarPresupuestos({ obra, tipo, estado } = {}) {
  const respuesta = await client.get('/presupuestos', {
    params: {
      obra: obra || undefined,
      tipo: tipo || undefined,
      estado: estado || undefined,
    },
  });
  return respuesta.data;
}

/** Trae el presupuesto con sus ítems y los subtotales por rubro. */
export async function obtenerPresupuesto(id) {
  const respuesta = await client.get(`/presupuestos/${id}`);
  return respuesta.data;
}

export async function crearPresupuesto(datos) {
  const respuesta = await client.post('/presupuestos', datos);
  return respuesta.data;
}

/**
 * Crea un presupuesto nuevo con una copia de los ítems del indicado.
 *
 * Es la operación que resuelve el versionado: el presupuesto de origen no se
 * toca, quedan los dos vinculados.
 */
export async function duplicarPresupuesto(id, datos) {
  const respuesta = await client.post(`/presupuestos/${id}/duplicar`, datos);
  return respuesta.data;
}

export async function agregarItem(idPresupuesto, item) {
  const respuesta = await client.post(`/presupuestos/${idPresupuesto}/items`, item);
  return respuesta.data;
}

export async function actualizarItem(idPresupuesto, idItem, item) {
  const respuesta = await client.put(`/presupuestos/${idPresupuesto}/items/${idItem}`, item);
  return respuesta.data;
}

export async function quitarItem(idPresupuesto, idItem) {
  const respuesta = await client.delete(`/presupuestos/${idPresupuesto}/items/${idItem}`);
  return respuesta.data;
}

/**
 * La planilla de carga de un rubro.
 *
 * Trae una fila por cada material del catálogo de ese rubro, con la cantidad y
 * el precio ya cargados si se presupuestó antes. Es lo que reemplaza al
 * "agregar ítem de a uno": se ve la lista entera y se completa lo que va.
 */
export async function cargarPlanilla(idPresupuesto, idRubro) {
  const { data } = await client.get(`/presupuestos/${idPresupuesto}/planilla/${idRubro}`);
  return data;
}

/**
 * Guarda la planilla completa de un rubro.
 *
 * Se manda entera —las filas cargadas y las vacías— y el backend reemplaza con
 * eso los ítems que el presupuesto tenía de ese rubro. Mandar solo lo que
 * cambió obligaría a llevar la cuenta de qué fila se editó y cuál se vació, y
 * un despiste ahí deja ítems que no se ven pero suman al total.
 */
export async function guardarPlanilla(idPresupuesto, idRubro, filas) {
  const { data } = await client.put(
    `/presupuestos/${idPresupuesto}/planilla/${idRubro}`, { filas });
  return data;
}

export async function definirPlanDePago(id, plan) {
  const respuesta = await client.put(`/presupuestos/${id}/plan-de-pago`, plan);
  return respuesta.data;
}

/** No existe baja: un presupuesto se marca Rechazado, nunca se elimina. */
export async function cambiarEstadoPresupuesto(id, estado) {
  const respuesta = await client.patch(`/presupuestos/${id}/estado`, { estado });
  return respuesta.data;
}

/**
 * Baja definitiva de un presupuesto.
 *
 * El informe pide marcar Rechazado en lugar de eliminar. Esta operación existe
 * para depurar los presupuestos de prueba, y el backend la acota: rechaza con
 * 409 si el presupuesto está aprobado o si otro se generó a partir de él.
 */
export async function eliminarPresupuesto(id) {
  await client.delete(`/presupuestos/${id}`);
}

/*
 * urlDelPdf() se retiró al activar la seguridad (módulo 14).
 *
 * Devolvía la dirección para ponerla en un <a href>, y eso dejó de funcionar:
 * un enlace no envía la cabecera Authorization, así que el backend contesta
 * 401 y la pestaña se abre con un error en JSON. El comentario de esa misma
 * función ya lo había anticipado desde el módulo 4.
 *
 * Ahora se usa abrirPdf() de src/api/documentos.js, que lo pide con Axios.
 */

export const TIPOS = ['Cotización inicial', 'Anteproyecto', 'Definitivo', 'Adicional'];
export const ESTADOS = ['Borrador', 'Enviado', 'Aprobado', 'Rechazado'];

/** Unidades de medida que usa la empresa, según el informe. */
export const UNIDADES = ['m²', 'ml', 'unidad', 'global', 'jornal'];

/**
 * Estados a los que se puede pasar desde el actual. Réplica de la regla del
 * backend, para no ofrecer algo que va a fallar.
 */
export function estadosPosiblesDesde(estado) {
  if (estado === 'Borrador') return ['Enviado', 'Rechazado'];
  if (estado === 'Enviado') return ['Aprobado', 'Rechazado'];
  return [];
}

/** Formato de importe argentino. */
export function pesos(monto) {
  if (monto === null || monto === undefined) return '—';
  return '$ ' + Number(monto).toLocaleString('es-AR', {
    minimumFractionDigits: 2, maximumFractionDigits: 2,
  });
}
