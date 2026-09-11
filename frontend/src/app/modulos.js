/**
 * Módulos que aparecen en la navegación.
 *
 * Se declaran en un solo archivo para que el menú y las rutas salgan de la
 * misma fuente: agregar un módulo es agregar una línea, no tocar tres archivos.
 *
 * Cada módulo declara el PERMISO que hace falta para verlo. Es el mismo texto
 * que usa el backend en @PreAuthorize y el mismo que está cargado en la tabla
 * permiso (migración V13). Que las tres cosas usen literalmente la misma
 * cadena es lo que hace que la matriz del informe sea verificable de punta a
 * punta.
 *
 * OJO con qué significa esto: filtrar el menú es PRESENTACIÓN. Un capataz que
 * escriba la dirección a mano tampoco entra —de eso se encarga RutaProtegida—,
 * y si insistiera contra la API, el backend le contesta 403. Esconder el enlace
 * no protege nada por sí solo; evita ofrecer algo que va a ser rechazado.
 */

/** Navegación superior (pantallas de escritorio). */
export const MODULOS = [
  { ruta: '/tablero', nombre: 'Tablero', permiso: 'tablero.ver' },
  { ruta: '/obras', nombre: 'Obras', permiso: 'obras.ver' },
  // Clientes no estaba en la navegación del diseño. Se agregó porque fue el
  // primer módulo conectado al backend y necesitaba desde dónde entrar.
  { ruta: '/clientes', nombre: 'Clientes', permiso: 'clientes.ver' },
  // Materiales entra al menú porque es un módulo propio del informe y lo
  // consultan tanto Presupuestación como Compras.
  { ruta: '/materiales', nombre: 'Materiales', permiso: 'materiales.ver' },
  { ruta: '/proveedores', nombre: 'Proveedores', permiso: 'proveedores.ver' },
  { ruta: '/pedidos', nombre: 'Pedidos', permiso: 'compras.ver' },
  // Gastos entra al menú con entrada propia: el informe le da vistas de
  // listado y de reporte que exceden la ficha de una obra.
  { ruta: '/gastos', nombre: 'Gastos', permiso: 'gastos.ver' },
  // Seguimiento entra con entrada propia: el informe le da una vista de
  // todas las obras activas, que excede la ficha de una sola.
  { ruta: '/seguimiento', nombre: 'Avance', permiso: 'seguimiento.ver' },
  { ruta: '/personal', nombre: 'Personal', permiso: 'personal.ver' },
  { ruta: '/presupuestos', nombre: 'Presupuestos', permiso: 'presupuestos.ver' },
  { ruta: '/cobranzas', nombre: 'Cobranzas', permiso: 'cobros.ver' },
  { ruta: '/portfolio', nombre: 'Portfolio', permiso: 'portfolio.ver' },
];

/**
 * Administración: Usuarios y Accesos.
 *
 * Van en un grupo aparte y no en la barra principal por dos motivos. Uno
 * visual: con catorce entradas la barra queda impracticable, y el diseño
 * original ya preveía un menú secundario de administración. Y otro de uso: son
 * pantallas que el dueño abre cada tanto —dar de alta un capataz, revisar la
 * auditoría—, no todos los días como Obras o Gastos.
 */
export const MODULOS_ADMINISTRACION = [
  { ruta: '/usuarios', nombre: 'Usuarios', permiso: 'usuarios.ver' },
  { ruta: '/accesos', nombre: 'Accesos', permiso: 'accesos.ver' },
];

/** Los módulos que el usuario puede ver, según sus permisos. */
export function modulosVisibles(lista, puede) {
  return lista.filter((modulo) => puede(modulo.permiso));
}
