/**
 * Módulos que aparecen en la navegación, tal como los define el diseño.
 *
 * Se declaran en un solo archivo para que el menú y las rutas salgan de la
 * misma fuente: agregar un módulo es agregar una línea, no tocar tres archivos.
 *
 * `listo` indica si el módulo ya está desarrollado contra el backend. Los que
 * no lo están muestran una pantalla que lo aclara, así la navegación funciona
 * completa desde el primer día.
 */

/** Navegación superior del rol Dueño (pantallas de escritorio). */
export const MODULOS_DUENO = [
  { ruta: '/tablero', nombre: 'Tablero', listo: true },
  { ruta: '/obras', nombre: 'Obras', listo: true },
  // Clientes no estaba en la navegación del diseño. Se agrega porque es el
  // primer módulo conectado al backend y necesita desde dónde entrar. Los
  // demás módulos que faltan se suman cuando le toque a cada uno, no antes.
  { ruta: '/clientes', nombre: 'Clientes', listo: true },
  { ruta: '/pedidos', nombre: 'Pedidos', listo: false },
  { ruta: '/presupuestos', nombre: 'Presupuestos', listo: false },
  { ruta: '/cobranzas', nombre: 'Cobranzas', listo: false },
];

/**
 * PENDIENTE DE DEFINICIÓN
 *
 * El diseño resuelve la navegación de los cinco módulos que el dueño usa a
 * diario, pero el sistema tiene catorce. Estos nueve todavía no tienen desde
 * dónde entrar:
 *
 *   Clientes · Materiales · Proveedores · Personal · Portfolio Web ·
 *   Usuarios · Accesos · Gastos (hoy vive dentro del detalle de obra) ·
 *   Seguimiento (hoy vive dentro del detalle de obra)
 *
 * Hay que resolverlo antes de desarrollar el módulo Clientes, que es el
 * primero de la lista y hoy no tendría dónde ubicarse. La salida más probable
 * es un menú secundario de administración y catálogos.
 */
export const MODULOS_SIN_UBICAR = [
  'Clientes', 'Materiales', 'Proveedores', 'Personal', 'Portfolio Web',
  'Usuarios', 'Accesos',
];
