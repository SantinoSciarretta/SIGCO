import { useEffect, useState } from 'react';
import { consultarEstado } from '../../api/estadoApi';

/**
 * Pantalla de inicio provisoria.
 *
 * Su unica funcion hoy es comprobar que el frontend logra comunicarse con el
 * backend: si esta tarjeta muestra la conexion en verde, quiere decir que la
 * cadena completa funciona (navegador -> API -> base de datos) y que CORS esta
 * bien configurado.
 *
 * En el modulo 12 esta pantalla se reemplaza por el Dashboard consolidado.
 */
export default function Inicio() {
  // Tres estados posibles: consultando, respondio bien, o fallo.
  const [estado, setEstado] = useState(null);
  const [error, setError] = useState(null);
  const [consultando, setConsultando] = useState(true);

  // useEffect con lista de dependencias vacia: se ejecuta una sola vez, cuando
  // el componente se monta.
  useEffect(() => {
    consultarEstado()
      .then((datos) => setEstado(datos))
      .catch((fallo) => setError(fallo.mensaje))
      .finally(() => setConsultando(false));
  }, []);

  return (
    <div>
      <h1>Inicio</h1>
      <p>
        El sistema esta en desarrollo. Los modulos se van habilitando a medida
        que se construyen.
      </p>

      <section>
        <h2>Estado de la conexion</h2>

        {consultando && <p>Consultando al servidor...</p>}

        {error && (
          <div>
            <p><strong>Sin conexion con el backend</strong></p>
            <p>{error}</p>
            <p>
              Verificar que el backend este en ejecucion
              (<code>cd backend</code> y <code>.\mvnw.cmd spring-boot:run</code>).
            </p>
          </div>
        )}

        {estado && (
          <dl>
            <dt>Aplicacion</dt>
            <dd>{estado.aplicacion}</dd>

            <dt>API</dt>
            <dd>{estado.api}</dd>

            <dt>Base de datos</dt>
            <dd>{estado.baseDatos}</dd>

            <dt>Consultado</dt>
            <dd>{new Date(estado.momento).toLocaleString('es-AR')}</dd>
          </dl>
        )}
      </section>
    </div>
  );
}
