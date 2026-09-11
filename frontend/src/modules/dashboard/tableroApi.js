import client from '../../api/client';

/**
 * Cliente del Tablero.
 *
 * Una sola llamada, porque el backend devuelve el panel entero de una vez: la
 * pantalla no sirve de a partes y así todos los números corresponden al mismo
 * instante.
 */
export function cargarTablero() {
  return client.get('/tablero').then((r) => r.data);
}

/* --------------------------------------------------------------------------
   Presentación

   El backend manda los números; acá solo se decide cómo se escriben. Ninguna
   de estas funciones toma decisiones de negocio: el semáforo, por ejemplo,
   llega resuelto desde Gastos y acá únicamente se traduce a un color.
   -------------------------------------------------------------------------- */

/** Color del semáforo que decidió el backend. Nunca se recalcula acá. */
export function colorDeSemaforo(semaforo) {
  if (semaforo === 'Rojo') return 'var(--color-excedido)';
  if (semaforo === 'Amarillo') return 'var(--color-alerta)';
  if (semaforo === 'Sin presupuesto') return 'var(--color-neutral-500)';
  return 'var(--color-ok)';
}

/** Texto que acompaña al color: quien no distingue verde de rojo tiene que
 *  poder leer el estado igual. */
export function textoDeSemaforo(semaforo) {
  if (semaforo === 'Rojo') return 'Excedido';
  if (semaforo === 'Amarillo') return 'Al límite';
  if (semaforo === 'Sin presupuesto') return 'Sin presupuesto';
  return 'En presupuesto';
}

/** "$ 12.980.000" */
export function pesos(monto) {
  return '$ ' + Math.round(Number(monto ?? 0)).toLocaleString('es-AR');
}

/** Abreviado para las cifras grandes del encabezado: 12980000 → "12,98" */
export function millones(monto) {
  const valor = Number(monto ?? 0) / 1000000;
  return (valor >= 10 ? valor.toFixed(1) : valor.toFixed(2)).replace('.', ',');
}

/** El backend manda 78.50; en pantalla va "79%". */
export function porcentaje(valor) {
  return Math.round(Number(valor ?? 0)) + '%';
}
