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
 * Dirección del PDF del presupuesto.
 *
 * Se abre en una pestaña nueva en lugar de descargarse con Axios: el documento
 * lo arma el servidor y el navegador ya sabe mostrarlo. Traerlo por Axios
 * obligaría a manejar el binario y crear una URL temporal, sin ninguna ventaja.
 *
 * OJO: cuando exista la autenticación del módulo 14, esta dirección va a
 * necesitar el token, que un enlace directo no envía. En ese momento habrá que
 * pedirlo con Axios y abrir el resultado, o usar una URL firmada.
 */
export function urlDelPdf(id) {
  return `${import.meta.env.VITE_API_URL}/presupuestos/${id}/pdf`;
}

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
