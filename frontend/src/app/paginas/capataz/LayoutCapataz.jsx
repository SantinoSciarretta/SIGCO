import { Outlet } from 'react-router-dom';
import estilos from './LayoutCapataz.module.css';

/**
 * Marco de las pantallas del capataz.
 *
 * Estas pantallas se usan en obra, desde el celular, con una mano y a veces con
 * guantes. Por eso no tienen barra de navegación ni menús: cada pantalla se
 * ocupa de una sola cosa y se vuelve con el botón de atrás.
 *
 * En una computadora el contenido se limita al ancho de un teléfono y se
 * centra, en lugar de estirarse: son pantallas diseñadas para ese ancho, y
 * estiradas quedarían con bloques de dos metros de largo.
 */
export default function LayoutCapataz() {
  return (
    <div className={estilos.fondo}>
      <div className={estilos.marco}>
        <Outlet />
      </div>
    </div>
  );
}
