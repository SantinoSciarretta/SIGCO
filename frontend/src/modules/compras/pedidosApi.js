import client from '../../api/client';

/**
 * Cliente del módulo Compras.
 *
 * Las tres transiciones del circuito son PATCH sobre un sub-recurso y no un PUT
 * del pedido: cada una es una acción distinta, con reglas y responsables
 * distintos, no una edición genérica.
 */

export async function listarPedidos({ obra, proveedor, estado } = {}) {
  const respuesta = await client.get('/pedidos', {
    params: {
      obra: obra || undefined,
      proveedor: proveedor || undefined,
      estado: estado || undefined,
    },
  });
  return respuesta.data;
}

/** Pedidos esperando la aprobación del dueño. */
export async function listarPendientes() {
  const respuesta = await client.get('/pedidos/pendientes');
  return respuesta.data;
}

export async function obtenerPedido(id) {
  const respuesta = await client.get(`/pedidos/${id}`);
  return respuesta.data;
}

export async function crearPedido(datos) {
  const respuesta = await client.post('/pedidos', datos);
  return respuesta.data;
}

/**
 * Última cotización del proveedor para cada material del pedido.
 *
 * Devuelve un objeto { idMaterial: precio }. Los materiales que ese proveedor
 * nunca cotizó no vienen: el dueño los completa a mano. Es preferible a
 * proponerle un cero que podría confirmar sin mirar.
 */
export async function preciosSugeridos(idPedido, idProveedor) {
  const respuesta = await client.get(`/pedidos/${idPedido}/precios-sugeridos`, {
    params: { proveedor: idProveedor },
  });
  return respuesta.data;
}

/** Aprobar: elegir proveedor y confirmar precios. Acción del dueño. */
export async function aprobarPedido(id, datos) {
  const respuesta = await client.patch(`/pedidos/${id}/aprobacion`, datos);
  return respuesta.data;
}

/** Confirmar recepción en obra. Se completa desde el celular. */
export async function recibirPedido(id, datos) {
  const respuesta = await client.patch(`/pedidos/${id}/recepcion`, datos);
  return respuesta.data;
}

export async function anularPedido(id, motivo) {
  const respuesta = await client.patch(`/pedidos/${id}/anulacion`, { motivo });
  return respuesta.data;
}

export const ESTADOS_PEDIDO = [
  'Pendiente de Aprobación',
  'Enviado al Proveedor',
  'Recibido Completo',
  'Recibido con Diferencias',
  'Anulado',
];

/** Color del badge según el estado, para que se lea de un vistazo. */
export function claseDeEstadoPedido(estado, estilos) {
  switch (estado) {
    case 'Pendiente de Aprobación': return estilos.estadoPendiente;
    case 'Enviado al Proveedor': return estilos.estadoEnviado;
    case 'Recibido Completo': return estilos.estadoRecibido;
    case 'Recibido con Diferencias': return estilos.estadoConDiferencias;
    default: return estilos.estadoAnulado;
  }
}

export function pesos(monto) {
  if (monto === null || monto === undefined) return '—';
  return `$ ${Number(monto).toLocaleString('es-AR', {
    minimumFractionDigits: 2, maximumFractionDigits: 2,
  })}`;
}

export function fechaHora(valor) {
  if (!valor) return '—';
  return new Date(valor).toLocaleDateString('es-AR', {
    day: '2-digit', month: '2-digit', year: 'numeric',
  });
}
