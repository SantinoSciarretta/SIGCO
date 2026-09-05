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
  // Materiales entra al menú porque es un módulo propio del informe y lo
  // consultan tanto Presupuestación como Compras.
  { ruta: '/materiales', nombre: 'Materiales', listo: true },
  { ruta: '/proveedores', nombre: 'Proveedores', listo: true },
  { ruta: '/pedidos', nombre: 'Pedidos', listo: true },
  // Gastos entra al menú con entrada propia: el informe le da vistas de
  // listado y de reporte que exceden la ficha de una obra.
  { ruta: '/gastos', nombre: 'Gastos', listo: true },
  // Seguimiento entra con entrada propia: el informe le da una vista de
  // todas las obras activas, que excede la ficha de una sola.
  { ruta: '/seguimiento', nombre: 'Avance', listo: true },
  { ruta: '/personal', nombre: 'Personal', listo: true },
  { ruta: '/presupuestos', nombre: 'Presupuestos', listo: true },
  { ruta: '/cobranzas', nombre: 'Cobranzas', listo: true },
  { ruta: '/portfolio', nombre: 'Portfolio', listo: true },
];

/**
 * Módulos que todavía no tienen entrada en la navegación.
 *
 * El diseño resolvió el menú de los cinco módulos que el dueño usa a diario,
 * pero el sistema tiene catorce. Cada módulo se suma al menú cuando se
 * desarrolla, no antes: así la navegación nunca ofrece algo que no existe.
 *
 * Cuando queden cuatro o cinco módulos más, la barra va a empezar a quedar
 * cargada y probablemente convenga un menú secundario de administración.
 */
export const MODULOS_SIN_UBICAR = [
  'Usuarios', 'Accesos',
];
