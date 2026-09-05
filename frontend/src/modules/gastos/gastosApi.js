import client from '../../api/client';

/** Cliente del módulo Gastos. */

export async function listarGastos({ obra, rubro, tipo, estado, desde, hasta } = {}) {
  const respuesta = await client.get('/gastos', {
    params: {
      obra: obra || undefined,
      rubro: rubro || undefined,
      tipo: tipo || undefined,
      estado: estado || undefined,
      desde: desde || undefined,
      hasta: hasta || undefined,
    },
  });
  return respuesta.data;
}

export async function crearGasto(datos) {
  const respuesta = await client.post('/gastos', datos);
  return respuesta.data;
}

export async function actualizarGasto(id, datos) {
  const respuesta = await client.put(`/gastos/${id}`, datos);
  return respuesta.data;
}

/** No existe eliminar: el informe pide anular dejando el motivo. */
export async function anularGasto(id, motivo) {
  const respuesta = await client.patch(`/gastos/${id}/anulacion`, { motivo });
  return respuesta.data;
}

/**
 * Estado financiero de la obra: presupuestado contra gastado, por rubro.
 *
 * Cuelga de la obra y no de /gastos porque así es como se consulta: se parte de
 * "cómo viene esta obra", no de "listame gastos".
 */
export async function estadoFinanciero(idObra) {
  const respuesta = await client.get(`/obras/${idObra}/estado-financiero`);
  return respuesta.data;
}

export const TIPOS_GASTO = ['Material', 'Mano de Obra', 'Gasto Hormiga', 'Otro'];

/**
 * Clase CSS del semáforo.
 *
 * Los colores son los funcionales del sistema (--color-ok, --color-alerta,
 * --color-excedido), no los de la marca: acá el color es información, no
 * decoración.
 */
export function claseDeSemaforo(semaforo, estilos) {
  switch (semaforo) {
    case 'Verde': return estilos.semaforoVerde;
    case 'Amarillo': return estilos.semaforoAmarillo;
    case 'Rojo': return estilos.semaforoRojo;
    default: return estilos.semaforoSinPresupuesto;
  }
}

export function pesos(monto) {
  if (monto === null || monto === undefined) return '—';
  return `$ ${Number(monto).toLocaleString('es-AR', {
    minimumFractionDigits: 2, maximumFractionDigits: 2,
  })}`;
}

/** Versión compacta para las celdas de la tabla financiera. */
export function pesosCorto(monto) {
  if (monto === null || monto === undefined) return '—';
  return `$ ${Number(monto).toLocaleString('es-AR', { maximumFractionDigits: 0 })}`;
}

export function fecha(valor) {
  if (!valor) return '—';
  const [anio, mes, dia] = String(valor).slice(0, 10).split('-');
  return `${dia}/${mes}/${anio}`;
}
