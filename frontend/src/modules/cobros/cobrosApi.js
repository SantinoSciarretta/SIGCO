import client from '../../api/client';

/** Cliente del módulo Cobros. */

/**
 * Pide al servidor el plan de cobro de una obra: todas sus cuotas con lo pagado
 * y lo pendiente.
 */
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

/**
 * Pide al servidor el resumen de cobros de todas las obras: cuánto se cobró y
 * cuánto falta en cada una.
 */
export async function resumenDeCobrosPorObra() {
  const respuesta = await client.get('/cobros');
  return respuesta.data;
}

/**
 * Pide al servidor las cuotas que vencen en los próximos días o que ya
 * vencieron.
 */
export async function cuotasPorVencerOVencidas() {
  const respuesta = await client.get('/cobros/alertas');
  return respuesta.data;
}

/**
 * Registra un pago de una cuota, por el total o por una parte.
 */
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
export async function calcularVistaPreviaCac(idObra) {
  const respuesta = await client.get(`/obras/${idObra}/cobros/previa-cac`);
  return respuesta.data;
}

/**
 * Aplica el último coeficiente CAC cargado a las cuotas pendientes de la obra.
 */
export async function aplicarCac(idObra) {
  const respuesta = await client.post(`/obras/${idObra}/cobros/actualizacion-cac`);
  return respuesta.data;
}

/**
 * Pide al servidor los coeficientes CAC cargados, del más reciente al más
 * viejo.
 */
export async function listarCoeficientesCac() {
  const respuesta = await client.get('/cac');
  return respuesta.data;
}

/**
 * Guarda el coeficiente CAC de un mes.
 */
export async function registrarCoeficienteCac(datos) {
  const respuesta = await client.post('/cac', datos);
  return respuesta.data;
}

export const MEDIOS_DE_PAGO = ['Transferencia', 'Efectivo', 'Cheque'];
export const COMPROBANTES = ['Mensaje', 'Recibo', 'Planilla'];

/**
 * Elige el estilo con que se pinta una cuota según su estado: abonada, vencida
 * o pendiente.
 */
export function claseDeEstadoCuota(estado, estilos) {
  switch (estado) {
    case 'Abonada': return estilos.cuotaAbonada;
    case 'Vencida': return estilos.cuotaVencida;
    // Parcial se ve como pendiente y no como abonada: todavia se debe plata,
    // y ese es el dato que importa al mirar la lista.
    case 'Parcial': return estilos.cuotaParcial;
    default: return estilos.cuotaPendiente;
  }
}

/**
 * Escribe un monto en pesos con dos decimales, al estilo argentino (por ejemplo
 * "$ 1.234,50"). Si no hay monto, muestra un guion.
 */
export function pesos(monto) {
  if (monto === null || monto === undefined) return '—';
  return `$ ${Number(monto).toLocaleString('es-AR', {
    minimumFractionDigits: 2, maximumFractionDigits: 2,
  })}`;
}

/**
 * Escribe un monto en pesos sin decimales, para los lugares donde hay poco
 * espacio.
 */
export function pesosCorto(monto) {
  if (monto === null || monto === undefined) return '—';
  return `$ ${Number(monto).toLocaleString('es-AR', { maximumFractionDigits: 0 })}`;
}

/**
 * Escribe una fecha en formato día/mes/año.
 */
export function fecha(valor) {
  if (!valor) return '—';
  const [anio, mes, dia] = String(valor).slice(0, 10).split('-');
  return `${dia}/${mes}/${anio}`;
}

/**
 * Escribe un mes abreviado con su año, por ejemplo "sep 2026".
 */
export function mes(valor) {
  if (!valor) return '—';
  const [anio, m] = String(valor).slice(0, 10).split('-');
  const nombres = ['ene', 'feb', 'mar', 'abr', 'may', 'jun',
    'jul', 'ago', 'sep', 'oct', 'nov', 'dic'];
  return `${nombres[Number(m) - 1]} ${anio}`;
}
