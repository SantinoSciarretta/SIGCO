/**
 * ============================================================================
 *  DATOS DE MUESTRA
 * ============================================================================
 *
 * Todo lo que las pantallas muestran hoy sale de este archivo. Está aislado a
 * propósito: es el único punto que hay que reemplazar cuando cada módulo se
 * conecte al backend.
 *
 * Plan de reemplazo, módulo por módulo:
 *   OBRAS   -> GET /api/obras          (módulo Obras)
 *   RUBROS  -> GET /api/rubros         (módulo Presupuestación)
 *   gasto   -> GET /api/gastos?obra=   (módulo Gastos)
 *   HITOS   -> GET /api/hitos?obra=    (módulo Seguimiento de Obras)
 *   CATALOGO-> GET /api/materiales     (módulo Materiales)
 *   cobrar  -> GET /api/cuotas?obra=   (módulo Cobros)
 *
 * Los importes son números planos porque son datos de muestra. En el backend
 * viajan como BigDecimal y llegan al frontend como texto, para no perder
 * precisión en el camino.
 *
 * NOTA: las direcciones de muestra son de Rosario y alrededores, tal como
 * vinieron del diseño. El relevamiento sitúa a Granica SRL en CABA y Gran
 * Buenos Aires; cuando se carguen los datos reales, esto queda corregido solo.
 */

/** Colores del gráfico de torta: pasos de la rampa de acento, de oscuro a claro. */
export const COLORES_RUBRO = [
  'var(--color-accent-800)',
  'var(--color-accent-600)',
  'var(--color-accent-500)',
  'var(--color-accent-400)',
  'var(--color-accent-300)',
];

/** Rubros con los que Granica arma sus presupuestos. */
export const RUBROS = [
  'Materiales',
  'Mano de obra',
  'Subcontratos',
  'Equipos y alquileres',
  'Permisos y varios',
];

/** Cómo se reparte el PRESUPUESTO entre rubros (suma 1). */
export const REPARTO_PRESUPUESTO = [0.42, 0.31, 0.14, 0.08, 0.05];

/** Cómo se repartió el GASTO real entre rubros, por obra (una fila por obra). */
export const REPARTO_GASTO = [
  [0.4547, 0.3159, 0.1526, 0.0555, 0.0213],
  [0.47, 0.29, 0.13, 0.07, 0.04],
  [0.52, 0.24, 0.12, 0.08, 0.04],
  [0.50, 0.28, 0.12, 0.06, 0.04],
  [0.44, 0.32, 0.13, 0.07, 0.04],
];

/**
 * Obras activas.
 * `avance` en null significa que el porcentaje se calcula a partir de los
 * hitos completados. Es la obra que tiene asignada el capataz, y es lo que
 * permite ver el efecto en vivo: si el capataz tilda un hito desde el celular,
 * el avance cambia también en el tablero del dueño.
 */
export const OBRAS = [
  {
    codigo: 'GR-0140', nombre: 'Casa Aguirre — Ampliación', corto: 'Casa Aguirre',
    dir: 'Belgrano 1240, Rosario', pres: 18400000, gasto: 12980000,
    capataz: 'R. Duarte', cobrar: 2350000, avance: null,
  },
  {
    codigo: 'GR-0141', nombre: 'Local Mitre — Refacción', corto: 'Local Mitre',
    dir: 'Mitre 780, Rosario', pres: 7250000, gasto: 6890000,
    capataz: 'J. Sosa', cobrar: 1180000, avance: 78,
  },
  {
    codigo: 'GR-0143', nombre: 'Dúplex Los Álamos', corto: 'Los Álamos',
    dir: 'Los Álamos 55, Funes', pres: 31200000, gasto: 9120000,
    capataz: 'M. Ferreyra', cobrar: 4900000, avance: 24,
  },
  {
    codigo: 'GR-0144', nombre: 'Depósito Ruta 9', corto: 'Depósito R9',
    dir: 'Km 312, Roldán', pres: 12800000, gasto: 13940000,
    capataz: 'R. Duarte', cobrar: 780000, avance: 91,
  },
  {
    codigo: 'GR-0146', nombre: 'Quincho Vinuesa', corto: 'Quincho',
    dir: 'Vinuesa 344, Rosario', pres: 5600000, gasto: 3010000,
    capataz: 'J. Sosa', cobrar: 620000, avance: 45,
  },
];

/**
 * Hitos de la obra del capataz, en orden de ejecución.
 * En el módulo Seguimiento cada hito lleva además su ponderación; acá todos
 * pesan igual, que es el caso más simple.
 */
export const HITOS = [
  { nombre: 'Replanteo y excavación', fecha: '06/03' },
  { nombre: 'Cimientos', fecha: '19/03' },
  { nombre: 'Estructura y losa', fecha: '11/04' },
  { nombre: 'Mampostería', fecha: '02/05' },
  { nombre: 'Sanitaria', fecha: '14/08' },
  { nombre: 'Eléctrica', fecha: '29/08' },
  { nombre: 'Revoques', fecha: '12/09' },
  { nombre: 'Terminaciones', fecha: '30/09' },
];

/**
 * Catálogo de materiales que el capataz puede pedir.
 * `paso` es de cuánto en cuánto sube la cantidad al tocar el "+": nadie pide
 * una bolsa de cemento de a una, se piden de a diez.
 */
export const CATALOGO = [
  { nombre: 'Cemento CP40', unidad: 'bolsa 50 kg', paso: 10 },
  { nombre: 'Arena gruesa', unidad: 'm³ a granel', paso: 2 },
  { nombre: 'Ladrillo hueco 12', unidad: 'unidad', paso: 100 },
  { nombre: 'Hierro nervurado ⌀8', unidad: 'barra 12 m', paso: 5 },
  { nombre: 'Cal hidráulica', unidad: 'bolsa 25 kg', paso: 5 },
  { nombre: 'Membrana asfáltica 4 mm', unidad: 'rollo 10 m', paso: 1 },
  { nombre: 'Alambre de atar', unidad: 'kg', paso: 5 },
];

/** Índice de la obra asignada al capataz. */
export const OBRA_DEL_CAPATAZ = 0;

// ---------------------------------------------------------------------------
//  Cálculos derivados
// ---------------------------------------------------------------------------

/** Porcentaje de avance a partir de los hitos completados. */
export function avancePorHitos(hitosCompletados) {
  const cantidad = HITOS.filter((_, i) => hitosCompletados[i]).length;
  return Math.round((cantidad / HITOS.length) * 100);
}

/**
 * Avance de una obra. La del capataz se calcula con los hitos en vivo; el
 * resto trae su valor cargado.
 */
export function avanceDeObra(indice, hitosCompletados) {
  if (indice === OBRA_DEL_CAPATAZ) return avancePorHitos(hitosCompletados);
  return OBRAS[indice].avance;
}

/**
 * Presupuestado y gastado de cada rubro de una obra, con su desvío.
 * Es el cálculo que alimenta el gráfico de barras del detalle de obra.
 */
export function rubrosDeObra(indice) {
  const obra = OBRAS[indice];
  const repartoGasto = REPARTO_GASTO[indice];

  return RUBROS.map((nombre, i) => {
    const presupuestado = obra.pres * REPARTO_PRESUPUESTO[i];
    const gastado = obra.gasto * repartoGasto[i];

    // La escala de la barra se estira un 6% por encima del mayor de los dos
    // valores, para que la marca del tope nunca quede pegada al borde.
    const escala = Math.max(presupuestado, gastado) * 1.06;

    return {
      nombre,
      color: COLORES_RUBRO[i],
      presupuestado,
      gastado,
      razon: gastado / presupuestado,
      participacion: repartoGasto[i],
      anchoGasto: (gastado / escala) * 100,
      anchoTope: (presupuestado / escala) * 100,
    };
  });
}

/**
 * Segmentos del gráfico de torta del desglose de gasto.
 * Se dibuja con un solo <circle> por rubro y el truco de stroke-dasharray:
 * cada anillo muestra solo su porción de la circunferencia y se corre con
 * stroke-dashoffset para arrancar donde terminó el anterior.
 */
export function segmentosDeTorta(indice, radio = 86) {
  const circunferencia = 2 * Math.PI * radio;
  const repartoGasto = REPARTO_GASTO[indice];
  let acumulado = 0;

  return repartoGasto.map((porcion, i) => {
    // Se descuentan 2px al largo para dejar una ranura entre segmentos.
    const largo = porcion * circunferencia - 2;
    const segmento = {
      color: COLORES_RUBRO[i],
      dasharray: `${largo} ${circunferencia - largo}`,
      dashoffset: -acumulado * circunferencia,
    };
    acumulado += porcion;
    return segmento;
  });
}

/** Total pendiente de cobro sumando todas las obras. */
export function totalPorCobrar() {
  return OBRAS.reduce((suma, obra) => suma + obra.cobrar, 0);
}
