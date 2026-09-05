import client from '../../api/client';

/** Cliente del módulo Cobros. */

export async function planDeCobro(idObra) {
  const respuesta = await client.get(`/obras/${idObra}/cobros`);
  return respuesta.data;
}

/**
 * Genera el plan a partir del definitivo aprobado.
 *
 * Solo se manda la fecha del primer vencimiento: el anticipo, la cantidad de
 * cuotas y el total salen del presupuesto que el cliente aceptó. Pedirlos de
 * nuevo abriría la puerta a que el plan no coincida con lo acordado.
 */
export async function generarPlan(idObra, primerVencimiento) {
  const respuesta = await client.post(`/obras/${idObra}/cobros`, { primerVencimiento });
  return respuesta.data;
}

export async function consolidado() {
  const respuesta = await client.get('/cobros');
  return respuesta.data;
}

export async function alertas() {
  const respuesta = await client.get('/cobros/alertas');
  return respuesta.data;
}

export async function registrarPago(idCuota, datos) {
  const respuesta = await client.patch(`/cuotas/${idCuota}/pago`, datos);
  return respuesta.data;
}

/** Anula el PAGO, no la cuota: la cuota vuelve a deberse. */
export async function anularPago(idCuota, motivo) {
  const respuesta = await client.patch(`/cuotas/${idCuota}/anulacion`, { motivo });
  return respuesta.data;
}

/** Muestra el efecto del ajuste antes de confirmarlo. */
export async function previaCac(idObra) {
  const respuesta = await client.get(`/obras/${idObra}/cobros/previa-cac`);
  return respuesta.data;
}

export async function aplicarCac(idObra) {
  const respuesta = await client.post(`/obras/${idObra}/cobros/actualizacion-cac`);
  return respuesta.data;
}

export async function listarIndices() {
  const respuesta = await client.get('/cac');
  return respuesta.data;
}

export async function registrarIndice(datos) {
  const respuesta = await client.post('/cac', datos);
  return respuesta.data;
}

export const MEDIOS_DE_PAGO = ['Transferencia', 'Efectivo', 'Cheque'];
export const COMPROBANTES = ['Mensaje', 'Recibo', 'Planilla'];

export function claseDeEstadoCuota(estado, estilos) {
  switch (estado) {
    case 'Abonada': return estilos.cuotaAbonada;
    case 'Vencida': return estilos.cuotaVencida;
    default: return estilos.cuotaPendiente;
  }
}

export function pesos(monto) {
  if (monto === null || monto === undefined) return '—';
  return `$ ${Number(monto).toLocaleString('es-AR', {
    minimumFractionDigits: 2, maximumFractionDigits: 2,
  })}`;
}

export function pesosCorto(monto) {
  if (monto === null || monto === undefined) return '—';
  return `$ ${Number(monto).toLocaleString('es-AR', { maximumFractionDigits: 0 })}`;
}

export function fecha(valor) {
  if (!valor) return '—';
  const [anio, mes, dia] = String(valor).slice(0, 10).split('-');
  return `${dia}/${mes}/${anio}`;
}

export function mes(valor) {
  if (!valor) return '—';
  const [anio, m] = String(valor).slice(0, 10).split('-');
  const nombres = ['ene', 'feb', 'mar', 'abr', 'may', 'jun',
    'jul', 'ago', 'sep', 'oct', 'nov', 'dic'];
  return `${nombres[Number(m) - 1]} ${anio}`;
}
