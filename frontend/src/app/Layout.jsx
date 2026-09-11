import { useState } from 'react';
import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { MODULOS, MODULOS_ADMINISTRACION, modulosVisibles } from './modulos';
import { useSesion } from '../modules/sesion/useSesion';
import estilos from './Layout.module.css';

/**
 * Marco de las pantallas de escritorio.
 *
 * Barra de navegación horizontal sobre el azul más profundo de la rampa, con
 * la marca a la izquierda y el usuario a la derecha. Reemplaza a la barra
 * lateral: el dueño trabaja en pantallas de información densa, y la navegación
 * arriba devuelve todo el ancho al contenido.
 *
 * Desde el módulo 14, el menú se arma con los permisos del usuario: cada uno ve
 * las entradas a las que puede entrar y ninguna más. Un capataz general no ve
 * "Cobranzas" ni "Presupuestos" — no porque estén deshabilitadas, sino porque
 * no existen para él.
 *
 * El <Outlet /> de React Router es el hueco donde se dibuja la pantalla activa:
 * la barra se arma una sola vez y al navegar solo cambia el contenido.
 */
export default function Layout() {
  const { sesion, salir, puede } = useSesion();
  const navegar = useNavigate();
  const [menuAbierto, setMenuAbierto] = useState(false);

  const visibles = modulosVisibles(MODULOS, puede);
  const administracion = modulosVisibles(MODULOS_ADMINISTRACION, puede);

  const cerrarSesion = () => {
    salir();
    navegar('/');
  };

  const clase = ({ isActive }) =>
    (isActive ? `${estilos.enlace} ${estilos.enlaceActivo}` : estilos.enlace);

  return (
    <div className={estilos.contenedor}>

      <header className={estilos.barra}>
        <span className={estilos.marca}>SIGCO</span>
        <span className={estilos.separador} />

        <nav className={estilos.navegacion}>
          {visibles.map((modulo) => (
            <NavLink key={modulo.ruta} to={modulo.ruta} className={clase}>
              {modulo.nombre}
            </NavLink>
          ))}
        </nav>

        <div className={estilos.usuario}>
          <button type="button" className={estilos.usuarioBoton}
                  onClick={() => setMenuAbierto((abierto) => !abierto)}
                  aria-expanded={menuAbierto}>
            <span className={estilos.usuarioRol}>{sesion?.nombreRol}</span>
            <span className={estilos.usuarioNombre}>{sesion?.nombreUsuario}</span>
            <span className={estilos.usuarioAvatar}>{iniciales(sesion?.nombreUsuario)}</span>
          </button>

          {menuAbierto && (
            <div className={estilos.menuUsuario}>
              {administracion.map((modulo) => (
                <NavLink key={modulo.ruta} to={modulo.ruta}
                         className={estilos.menuEnlace}
                         onClick={() => setMenuAbierto(false)}>
                  {modulo.nombre}
                </NavLink>
              ))}

              <NavLink to="/mi-cuenta" className={estilos.menuEnlace}
                       onClick={() => setMenuAbierto(false)}>
                Mi contraseña
              </NavLink>

              <button type="button" className={estilos.menuSalir} onClick={cerrarSesion}>
                Cerrar sesión
              </button>
            </div>
          )}
        </div>
      </header>

      <main className={estilos.contenido}>
        <Outlet />
      </main>

    </div>
  );
}

/** "ricardo" → "RI". Dos letras alcanzan para reconocerse en la barra. */
function iniciales(nombre) {
  return (nombre ?? '??').slice(0, 2).toUpperCase();
}
