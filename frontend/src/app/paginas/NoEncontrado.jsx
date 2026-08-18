import { Link } from 'react-router-dom';

/**
 * Pantalla para una dirección que no corresponde a ninguna ruta del sistema.
 */
export default function NoEncontrado() {
  return (
    <div style={{ padding: '64px 28px', maxWidth: '46ch' }}>
      <h2>Pantalla no encontrada</h2>
      <p className="text-muted">
        La dirección ingresada no corresponde a ninguna pantalla del sistema.
      </p>
      <p><Link to="/">Volver al ingreso</Link></p>
    </div>
  );
}
