/**
 * Definicion de los modulos del sistema para la barra de navegacion.
 *
 * Se declara en un solo archivo para que el menu y las rutas salgan de la misma
 * fuente: agregar un modulo es agregar una linea aca, no tocar tres archivos.
 *
 * El campo "listo" indica si el modulo ya esta desarrollado. Los que todavia no
 * lo estan muestran una pantalla que lo aclara, de modo que la navegacion
 * funcione completa desde el primer dia y cada modulo simplemente reemplace su
 * marcador de posicion cuando le toque.
 *
 * El orden de esta lista es el orden de uso del sistema (como lo recorre el
 * dueño), que no coincide con el orden en que se desarrollan los modulos.
 */
export const gruposDeModulos = [
  {
    titulo: 'Principal',
    modulos: [
      // En el modulo 12 esta pantalla pasa a ser el Dashboard consolidado.
      { ruta: '/', nombre: 'Inicio', listo: true },
    ],
  },
  {
    titulo: 'Gestion',
    modulos: [
      { ruta: '/obras', nombre: 'Obras', listo: false },
      { ruta: '/clientes', nombre: 'Clientes', listo: false },
      { ruta: '/presupuestos', nombre: 'Presupuestacion', listo: false },
      { ruta: '/cobros', nombre: 'Cobros', listo: false },
    ],
  },
  {
    titulo: 'Operacion',
    modulos: [
      { ruta: '/seguimiento', nombre: 'Seguimiento de Obras', listo: false },
      { ruta: '/compras', nombre: 'Compras', listo: false },
      { ruta: '/gastos', nombre: 'Gastos', listo: false },
      { ruta: '/personal', nombre: 'Personal', listo: false },
    ],
  },
  {
    titulo: 'Catalogos',
    modulos: [
      { ruta: '/materiales', nombre: 'Materiales', listo: false },
      { ruta: '/proveedores', nombre: 'Proveedores', listo: false },
    ],
  },
  {
    titulo: 'Administracion',
    modulos: [
      { ruta: '/portfolio', nombre: 'Portfolio Web', listo: false },
      { ruta: '/usuarios', nombre: 'Usuarios', listo: false },
      { ruta: '/accesos', nombre: 'Accesos', listo: false },
    ],
  },
];

/** Lista plana de todos los modulos, util para armar las rutas. */
export const todosLosModulos = gruposDeModulos.flatMap((grupo) => grupo.modulos);
