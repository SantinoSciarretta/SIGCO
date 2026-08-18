/**
 * Semáforo de desvío presupuestario y formato de importes.
 *
 * Concentra en un solo lugar las dos cosas que aparecen en casi todas las
 * pantallas del sistema: cómo se decide el color de un desvío y cómo se
 * escribe la plata.
 *
 * IMPORTANTE: estos umbrales son de PRESENTACIÓN. Cuando se desarrolle el
 * módulo Gastos, el backend va a calcular y devolver el estado del desvío,
 * porque una regla de negocio que vive solo en el navegador se puede saltear.
 * Acá se replica para dar respuesta inmediata en pantalla.
 */

/** Debajo de este porcentaje del presupuesto, el rubro está en verde. */
export const UMBRAL_ALERTA = 0.9;

/** A partir de acá el rubro superó lo presupuestado. */
export const UMBRAL_EXCEDIDO = 1.0;

export const ESTADO_OK = 'ok';
export const ESTADO_ALERTA = 'alerta';
export const ESTADO_EXCEDIDO = 'excedido';

/**
 * Devuelve el estado del desvío a partir de la relación gastado/presupuestado.
 * @param {number} razon gastado dividido presupuestado (1 = consumió el 100%)
 */
export function estadoDeDesvio(razon) {
  if (razon >= UMBRAL_EXCEDIDO) return ESTADO_EXCEDIDO;
  if (razon >= UMBRAL_ALERTA) return ESTADO_ALERTA;
  return ESTADO_OK;
}

/** Color del semáforo, tomado siempre de los tokens (nunca un hex a mano). */
export function colorDeEstado(estado) {
  if (estado === ESTADO_EXCEDIDO) return 'var(--color-excedido)';
  if (estado === ESTADO_ALERTA) return 'var(--color-alerta)';
  return 'var(--color-ok)';
}

/** Texto que acompaña al color. El color nunca viaja solo: quien no distingue
 *  verde de rojo tiene que poder leer el estado igual. */
export function textoDeEstado(estado) {
  if (estado === ESTADO_EXCEDIDO) return 'Excedido';
  if (estado === ESTADO_ALERTA) return 'Al límite';
  return 'En presupuesto';
}

/** Atajo: de la razón directo al color. */
export function colorDeDesvio(razon) {
  return colorDeEstado(estadoDeDesvio(razon));
}

/**
 * Importe completo, con separador de miles argentino.
 * 12980000 -> "$ 12.980.000"
 */
export function pesos(monto) {
  return '$ ' + Math.round(monto).toLocaleString('es-AR');
}

/**
 * Importe abreviado en millones, para las cifras protagonistas donde no entra
 * el número completo.
 * 12980000 -> "$12,98 M"   ·   31200000 -> "$31,2 M"
 */
export function millones(monto) {
  const valor = monto / 1000000;
  const texto = valor >= 10 ? valor.toFixed(1) : valor.toFixed(2);
  return '$' + texto.replace('.', ',') + ' M';
}

/** Porcentaje entero, listo para mostrar. 0.784 -> "78%" */
export function porcentaje(razon) {
  return Math.round(razon * 100) + '%';
}
