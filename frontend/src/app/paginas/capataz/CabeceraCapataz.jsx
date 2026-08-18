import { Link } from 'react-router-dom';
import estilos from './CabeceraCapataz.module.css';

/**
 * Cabecera oscura de las pantallas internas del capataz.
 *
 * Lleva el botón de volver y el nombre de la sección. El botón mide 40 px y no
 * menos: se toca en obra, muchas veces sin mirar la pantalla con detenimiento.
 *
 * @param {string} titulo   texto de la sección, en versalitas
 * @param {node}   children contenido extra de la cabecera (avance, pestañas)
 */
export default function CabeceraCapataz({ titulo, children }) {
  return (
    <header className={estilos.cabecera}>
      <div className={estilos.fila}>
        <Link to="/obra" className={estilos.volver} aria-label="Volver a la obra">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor"
               strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
            <path d="M19 12H5" />
            <path d="m11 18-6-6 6-6" />
          </svg>
        </Link>
        <span className={estilos.titulo}>{titulo}</span>
      </div>
      {children}
    </header>
  );
}
