import client from '../../api/client';

/** Cliente del módulo Seguimiento de Obras. */

/** Panel de avance: hitos, avance físico y cruce con el financiero. */
export async function avanceDeObra(idObra) {
  const respuesta = await client.get(`/obras/${idObra}/avance`);
  return respuesta.data;
}

/**
 * Define el conjunto COMPLETO de hitos de la obra.
 *
 * Es PUT y no POST porque reemplaza la configuración entera: la regla de que
 * las ponderaciones sumen 100 aplica al conjunto, no a cada hito por separado.
 */
export async function configurarHitos(idObra, hitos) {
  const respuesta = await client.put(`/obras/${idObra}/hitos`, { hitos });
  return respuesta.data;
}

export async function aplicarPlantilla(idObra, idPlantilla) {
  const respuesta = await client.post(
    `/obras/${idObra}/hitos/desde-plantilla/${idPlantilla}`);
  return respuesta.data;
}

/** Al completar el último hito, la obra pasa sola a Finalizada. */
export async function completarHito(idHito, datos) {
  const respuesta = await client.patch(`/hitos/${idHito}/cumplimiento`, datos);
  return respuesta.data;
}

export async function reabrirHito(idHito) {
  const respuesta = await client.patch(`/hitos/${idHito}/reapertura`);
  return respuesta.data;
}

export async function listarPlantillas() {
  const respuesta = await client.get('/plantillas-hito');
  return respuesta.data;
}

export async function crearPlantilla(datos) {
  const respuesta = await client.post('/plantillas-hito', datos);
  return respuesta.data;
}

export function fecha(valor) {
  if (!valor) return '—';
  const [anio, mes, dia] = String(valor).slice(0, 10).split('-');
  return `${dia}/${mes}/${anio}`;
}

/**
 * Cómo se lee el plazo de la obra.
 *
 * Se separa "atrasada" de "quedan pocos días": lo primero ya pasó, lo segundo
 * todavía se puede corregir.
 */
export function textoDePlazo(avance) {
  if (avance.fechaFinEstimada === null) return 'Sin fecha estimada';
  if (avance.atrasada) return `Atrasada ${Math.abs(avance.diasParaElPlazo)} días`;
  if (avance.diasParaElPlazo < 0) return 'Terminada fuera de plazo';
  return `Faltan ${avance.diasParaElPlazo} días`;
}
