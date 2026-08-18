import { Link } from 'react-router-dom';

/**
 * Pantalla para una direccion que no corresponde a ninguna ruta del sistema.
 */
export default function NoEncontrado() {
  return (
    <div>
      <h1>Pagina no encontrada</h1>
      <p>La direccion ingresada no corresponde a ninguna pantalla del sistema.</p>
      <Link to="/">Volver al inicio</Link>
    </div>
  );
}
