import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import Blueprint from '../../components/ui/Blueprint';
import { useSesion } from '../../modules/sesion/useSesion';
import estilos from './Login.module.css';

/**
 * Ingreso al sistema.
 *
 * Hasta el módulo 14 esta pantalla era una maqueta: se elegía el rol tocando un
 * botón y no había ninguna validación. Ahora autentica de verdad contra
 * POST /api/sesion, que devuelve el token firmado y los permisos del usuario.
 *
 * A dónde entra cada uno lo decide EL ROL QUE DEVUELVE EL BACKEND, no una
 * elección de la pantalla. Es la diferencia de fondo con la maqueta: antes uno
 * decía quién era, ahora lo dice el servidor después de verificar la contraseña.
 *
 * El PIN de cuatro dígitos del diseño original se reemplazó por una contraseña.
 * El motivo es del backend: las contraseñas se guardan con hash BCrypt y un PIN
 * de cuatro dígitos tiene diez mil combinaciones posibles, así que el hash no
 * protege gran cosa — se prueban todas en segundos. El informe pide "credenciales
 * únicas" y no especifica el formato.
 */
export default function Login() {
  const { ingresar } = useSesion();
  const navegar = useNavigate();

  const [nombreUsuario, setNombreUsuario] = useState('');
  const [contrasena, setContrasena] = useState('');
  const [error, setError] = useState(null);
  const [entrando, setEntrando] = useState(false);

  const enviar = async (evento) => {
    evento.preventDefault();
    setEntrando(true);
    setError(null);
    try {
      const sesion = await ingresar(nombreUsuario, contrasena);

      // El capataz de obra va a la pantalla angosta, pensada para el celular
      // en obra. El resto entra al tablero de escritorio.
      navegar(sesion.nombreRol === 'Capataz de Obra' ? '/obra' : '/tablero');
    } catch (fallo) {
      setError(fallo.mensaje);
      setContrasena('');
    } finally {
      setEntrando(false);
    }
  };

  return (
    <div className={estilos.pantalla}>
      {/* Cuadrícula de fondo: la hoja milimetrada del plano. */}
      <div className={estilos.cuadricula} aria-hidden="true" />

      <div className={estilos.contenido}>

        <header className={estilos.encabezado}>
          <div>
            <div className={estilos.marca}>SIGCO</div>
            <div className={estilos.marcaSub}>Granica SRL · obra y refacción</div>
          </div>
          <div className={estilos.version}>v1.0<br />CABA-AR</div>
        </header>

        <form onSubmit={enviar}>
          <p className={estilos.paso}>01 · Credenciales</p>

          <div className={estilos.campos}>
            <div>
              <label className={estilos.etiqueta} htmlFor="usuario">Usuario</label>
              <input
                id="usuario"
                className={estilos.entrada}
                value={nombreUsuario}
                onChange={(e) => setNombreUsuario(e.target.value)}
                autoComplete="username"
                autoFocus
                required
              />
            </div>

            <div>
              <label className={estilos.etiqueta} htmlFor="contrasena">Contraseña</label>
              <input
                id="contrasena"
                type="password"
                className={estilos.entrada}
                value={contrasena}
                onChange={(e) => setContrasena(e.target.value)}
                autoComplete="current-password"
                required
              />
            </div>
          </div>

          {/* El mensaje es el mismo para usuario inexistente y contraseña
              incorrecta: si distinguiera, se podrían descubrir qué usuarios
              existen probando nombres. */}
          {error && <p className={estilos.error} role="alert">{error}</p>}

          <Blueprint as="button" type="submit" claro className={estilos.entrar}
                     disabled={entrando}>
            <span>{entrando ? 'Verificando…' : 'Entrar'}</span>
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                 strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
              <path d="M5 12h14" />
              <path d="m13 6 6 6-6 6" />
            </svg>
          </Blueprint>
        </form>

        <p className={estilos.ayuda}>
          ¿Olvidaste tu contraseña? Solo el dueño puede restablecerla desde
          Usuarios.
        </p>
      </div>
    </div>
  );
}
