import { NavLink, Outlet } from 'react-router-dom';
import { MODULOS_DUENO } from './modulos';
import estilos from './Layout.module.css';

/**
 * Marco de las pantallas de escritorio (rol Dueño).
 *
 * Barra de navegación horizontal sobre el azul más profundo de la rampa, con
 * la marca a la izquierda y el usuario a la derecha. Reemplaza a la barra
 * lateral: el dueño trabaja en pantallas de información densa, y la navegación
 * arriba devuelve todo el ancho al contenido.
 *
 * El <Outlet /> de React Router es el hueco donde se dibuja la pantalla activa:
 * la barra se arma una sola vez y al navegar solo cambia el contenido.
 */
export default function Layout() {
  return (
    <div className={estilos.contenedor}>

      <header className={estilos.barra}>
        <span className={estilos.marca}>SIGCO</span>
        <span className={estilos.separador} />

        <nav className={estilos.navegacion}>
          {MODULOS_DUENO.map((modulo) => (
            <NavLink
              key={modulo.ruta}
              to={modulo.ruta}
              className={({ isActive }) =>
                isActive ? `${estilos.enlace} ${estilos.enlaceActivo}` : estilos.enlace
              }
            >
              {modulo.nombre}
            </NavLink>
          ))}
        </nav>

        <div className={estilos.usuario}>
          {/* TODO: reemplazar por el usuario autenticado al integrar módulo Accesos */}
          <span className={estilos.usuarioRol}>Dueño</span>
          <span className={estilos.usuarioNombre}>M. Granica</span>
          <span className={estilos.usuarioAvatar}>MG</span>
        </div>
      </header>

      <main className={estilos.contenido}>
        <Outlet />
      </main>

    </div>
  );
}
