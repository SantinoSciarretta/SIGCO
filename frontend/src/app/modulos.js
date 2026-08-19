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
  { ruta: '/pedidos', nombre: 'Pedidos', listo: false },
  { ruta: '/presupuestos', nombre: 'Presupuestos', listo: true },
  { ruta: '/cobranzas', nombre: 'Cobranzas', listo: false },
];

/**
 * Módulos que todavía no tienen entrada en la navegación.
 *
 * El diseño resolvió el menú de los cinco módulos que el dueño usa a diario,
 * pero el sistema tiene catorce. Cada módulo se suma al menú cuando se
 * desarrolla, no antes: así la navegación nunca ofrece algo que no existe.
 *
 * Gastos y Seguimiento son un caso aparte: no van a tener entrada propia
 * porque viven dentro de la ficha de obra, que es donde tienen sentido.
 *
 * Cuando queden cuatro o cinco módulos más, la barra va a empezar a quedar
 * cargada y probablemente convenga un menú secundario de administración.
 */
export const MODULOS_SIN_UBICAR = [
  'Personal', 'Portfolio Web',
  'Usuarios', 'Accesos',
];
