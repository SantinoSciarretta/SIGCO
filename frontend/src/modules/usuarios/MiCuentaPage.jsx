import { useState } from 'react';
import Blueprint from '../../components/ui/Blueprint';
import { useSesion } from '../sesion/useSesion';
import { usuariosApi } from './usuariosApi';
import estilos from './Usuarios.module.css';

/**
 * Mi cuenta: cambiar la propia contraseña.
 *
 * Existe porque es la única acción del módulo Usuarios que NO requiere el
 * permiso de administrar cuentas. Un capataz no administra usuarios, pero tiene
 * que poder cambiar su contraseña — sobre todo la primera vez, después de que
 * el dueño le dio una inicial.
 *
 * Pide la contraseña actual: así, alguien que encuentre la sesión abierta en
 * una computadora no puede apropiarse de la cuenta cambiándole la clave.
 */
export default function MiCuentaPage() {
  const { sesion } = useSesion();

  const [actual, setActual] = useState('');
  const [nueva, setNueva] = useState('');
  const [repetida, setRepetida] = useState('');
  const [error, setError] = useState(null);
  const [listo, setListo] = useState(false);
  const [guardando, setGuardando] = useState(false);

  const enviar = async (evento) => {
    evento.preventDefault();
    setError(null);
    setListo(false);

    // Se verifica acá y no en el backend porque es una confusión de tipeo, no
    // una regla de negocio: el backend no tiene por qué saber que el formulario
    // pidió la contraseña dos veces.
    if (nueva !== repetida) {
      setError('Las dos contraseñas nuevas no coinciden.');
      return;
    }

    setGuardando(true);
    try {
      await usuariosApi.cambiarContrasena(sesion.idUsuario, {
        contrasenaActual: actual,
        contrasenaNueva: nueva,
      });
      setListo(true);
      setActual('');
      setNueva('');
      setRepetida('');
    } catch (fallo) {
      setError(fallo.mensaje);
    } finally {
      setGuardando(false);
    }
  };

  return (
    <>
      <div className={estilos.cabecera}>
        <div>
          <span className="kicker kicker-acento">Mi cuenta</span>
          <h2 className={estilos.titulo}>{sesion?.nombreUsuario}</h2>
          <p className={estilos.bajada}>{sesion?.nombreRol}</p>
        </div>
      </div>

      <Blueprint className={estilos.bloque} style={{ maxWidth: 460 }}>
        <div className={estilos.bloqueCabecera}>
          <h4>Cambiar mi contraseña</h4>
        </div>

        <form onSubmit={enviar}>
          {error && <p className={estilos.errorGeneral}>{error}</p>}
          {listo && <p className={estilos.avisoOk}>Contraseña actualizada.</p>}

          <div className={estilos.campo}>
            <label className={estilos.etiqueta} htmlFor="actual">
              Contraseña actual <span className={estilos.obligatorio}>*</span>
            </label>
            <input id="actual" type="password" className={estilos.control}
                   autoComplete="current-password"
                   value={actual} onChange={(e) => setActual(e.target.value)} required />
          </div>

          <div className={estilos.campo}>
            <label className={estilos.etiqueta} htmlFor="nueva">
              Contraseña nueva <span className={estilos.obligatorio}>*</span>
            </label>
            <input id="nueva" type="password" className={estilos.control} minLength={8}
                   autoComplete="new-password"
                   value={nueva} onChange={(e) => setNueva(e.target.value)} required />
            <p className={estilos.ayuda}>Mínimo 8 caracteres.</p>
          </div>

          <div className={estilos.campo}>
            <label className={estilos.etiqueta} htmlFor="repetida">
              Repetir la nueva <span className={estilos.obligatorio}>*</span>
            </label>
            <input id="repetida" type="password" className={estilos.control} minLength={8}
                   autoComplete="new-password"
                   value={repetida} onChange={(e) => setRepetida(e.target.value)} required />
          </div>

          <div className={estilos.accionesFormulario}>
            <button type="submit" className={estilos.botonPrimario} disabled={guardando}>
              {guardando ? 'Guardando…' : 'Cambiar contraseña'}
            </button>
          </div>
        </form>

        <p className={estilos.ayuda}>
          La contraseña se guarda cifrada con un hash que no se puede revertir:
          si te la olvidás, nadie puede recuperártela — el dueño te la
          restablece desde Usuarios.
        </p>
      </Blueprint>
    </>
  );
}
