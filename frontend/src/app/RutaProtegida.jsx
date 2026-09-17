import { Navigate, useLocation } from 'react-router-dom';
import { useSesion } from '../modules/sesion/useSesion';

/**
 * Envuelve las rutas que requieren sesión, y opcionalmente un permiso.
 *
 * Es PRESENTACIÓN, no seguridad, y hay que tenerlo claro para defenderlo: esto
 * evita que alguien llegue a una pantalla que no le sirve, pero no protege los
 * datos. Quien quiera saltearlo pide los datos directo a la API y no pasa por
 * acá. La seguridad real está en el backend, que revalida el permiso en cada
 * petición y contesta 403 aunque la pantalla se haya abierto igual.
 *
 * Lo que sí resuelve: que un capataz no se encuentre con una pantalla de cobros
 * vacía y llena de errores en rojo porque la API le rechazó cada llamada.
 *
 * @param {string} [permiso] permiso necesario; sin él alcanza con estar dentro
 */
export default function RutaProtegida({ permiso, children }) {
  const { sesion, cargando, puede } = useSesion();
  const ubicacion = useLocation();

  // Mientras se verifica el token guardado no se decide nada: mostrar el login
  // un instante para después saltar adentro se ve como un parpadeo.
  if (cargando) {
    return null;
  }

  if (!sesion) {
    // `state` recuerda a dónde quería ir, para volver ahí después de entrar.
    // `replace` evita que el botón Atrás del navegador vuelva a una pantalla
    // que ya no puede ver.
    return <Navigate to="/" replace state={{ destino: ubicacion.pathname }} />;
  }

  // Cuenta obligada a cambiar la contraseña: no entra a ninguna otra pantalla.
  //
  // Esto es COMODIDAD, no la defensa. El backend rechaza toda petición de una
  // cuenta en esta situación (FiltroCambioDeContrasena), así que sin esta
  // redirección el usuario vería el sistema entero fallando en 403 sin
  // entender por qué. Acá se lo lleva directo al único lugar donde puede
  // resolverlo.
  if (sesion.debeCambiarContrasena && ubicacion.pathname !== '/mi-cuenta') {
    return <Navigate to="/mi-cuenta" replace />;
  }

  if (permiso && !puede(permiso)) {
    return <SinPermiso />;
  }

  return children;
}

/**
 * Se explica qué pasó en lugar de redirigir en silencio.
 *
 * Una redirección sin aviso deja al usuario pensando que el sistema falló o
 * que se equivocó de enlace. Decirle que la pantalla existe pero no le
 * corresponde es información útil: si la necesita, sabe que tiene que pedirla.
 */
function SinPermiso() {
  return (
    <div style={{ padding: '40px 4px', maxWidth: 560 }}>
      <span className="kicker kicker-acento">Acceso restringido</span>
      <h2 style={{ margin: '10px 0 12px', fontSize: 26 }}>
        Esta sección no está habilitada para tu rol
      </h2>
      <p style={{ margin: 0, fontSize: 14, lineHeight: 1.6 }}>
        Tu usuario no tiene permiso para ver esta pantalla. Si necesitás
        acceder, pedíselo al dueño: los permisos se administran desde el módulo
        Accesos.
      </p>
    </div>
  );
}
