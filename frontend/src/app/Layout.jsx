import { NavLink, Outlet } from 'react-router-dom';
import { gruposDeModulos } from './modulos';
import estilos from './Layout.module.css';

/**
 * Marco visual comun a todas las pantallas del sistema: barra lateral de
 * navegacion, encabezado y area de contenido.
 *
 * Se escribe una sola vez y lo heredan los 14 modulos. El componente <Outlet />
 * de React Router es el hueco donde se dibuja la pantalla de la ruta activa:
 * al navegar, solo cambia esa parte, sin recargar la barra lateral ni el
 * encabezado ni la pagina completa.
 *
 * NOTA: los estilos de este archivo son provisorios y neutros. La identidad
 * visual (paleta, tipografia y componentes base) se define en el paso 3 y se
 * aplica sobre esta misma estructura.
 */
export default function Layout() {
  return (
    <div className={estilos.contenedor}>

      <aside className={estilos.barraLateral}>
        <div className={estilos.marca}>
          <span className={estilos.marcaNombre}>SIGCO</span>
          <span className={estilos.marcaDescripcion}>Granica SRL</span>
        </div>

        <nav className={estilos.navegacion}>
          {gruposDeModulos.map((grupo) => (
            <div key={grupo.titulo} className={estilos.grupo}>
              <p className={estilos.grupoTitulo}>{grupo.titulo}</p>

              {grupo.modulos.map((modulo) => (
                <NavLink
                  key={modulo.ruta}
                  to={modulo.ruta}
                  // React Router entrega "isActive" para marcar el modulo en el
                  // que esta parado el usuario. "end" evita que la ruta raiz "/"
                  // quede marcada como activa en todas las demas pantallas.
                  end={modulo.ruta === '/'}
                  className={({ isActive }) =>
                    isActive ? `${estilos.enlace} ${estilos.enlaceActivo}` : estilos.enlace
                  }
                >
                  {modulo.nombre}
                  {!modulo.listo && <span className={estilos.pendiente}>pendiente</span>}
                </NavLink>
              ))}
            </div>
          ))}
        </nav>
      </aside>

      <div className={estilos.principal}>
        <header className={estilos.encabezado}>
          <span className={estilos.encabezadoTitulo}>
            Sistema Integral de Gestion de Obras
          </span>
          {/* Aca va el usuario conectado y el boton de salir, al integrar el modulo Accesos */}
        </header>

        <main className={estilos.contenido}>
          <Outlet />
        </main>
      </div>

    </div>
  );
}
