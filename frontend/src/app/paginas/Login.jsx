import { useNavigate } from 'react-router-dom';
import Blueprint from '../../components/ui/Blueprint';
import { useDemo } from '../../datos/contextoDemo';
import estilos from './Login.module.css';

/**
 * Ingreso al sistema, con selección de rol.
 *
 * El rol define dos experiencias distintas: el Dueño entra a un tablero de
 * escritorio con información densa, y el Capataz a la obra del día, con
 * bloques grandes pensados para usarse con una mano en obra.
 *
 * ATENCIÓN: esta pantalla es solo la interfaz. Hoy el rol se elige tocando un
 * botón y no hay ninguna validación: no existe autenticación en el sistema.
 * El login real (usuario, contraseña con hash, token JWT y control de permisos
 * por rol en el backend) se implementa en los módulos 13 y 14, que van al
 * final del desarrollo.
 * TODO: conectar con POST /api/sesion al integrar módulo Usuarios/Accesos
 */
export default function Login() {
  const { rol, setRol } = useDemo();
  const navegar = useNavigate();

  const esDueno = rol === 'dueno';

  const entrar = () => navegar(esDueno ? '/tablero' : '/obra');

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
          <div className={estilos.version}>v2.4<br />ROS-AR</div>
        </header>

        <p className={estilos.paso}>01 · Entro como</p>
        <div className={estilos.roles}>
          <BotonRol
            activo={esDueno}
            onClick={() => setRol('dueno')}
            titulo="Dueño"
            detalle="Tablero y plata"
            icono={
              <>
                <path d="M3 21V8l9-5 9 5v13" />
                <path d="M9 21v-6h6v6" />
              </>
            }
          />
          <BotonRol
            activo={!esDueno}
            onClick={() => setRol('capataz')}
            titulo="Capataz"
            detalle="Obra del día"
            icono={
              <>
                <path d="M2 18h20" />
                <path d="M4 18a8 8 0 0 1 16 0" />
                <path d="M12 4v6" />
                <path d="M8.5 5.5 10 10" />
                <path d="M15.5 5.5 14 10" />
              </>
            }
          />
        </div>

        <p className={estilos.paso}>02 · Credenciales</p>
        <div className={estilos.campos}>
          <div>
            <label className={estilos.etiqueta} htmlFor="usuario">Usuario</label>
            <input
              id="usuario"
              className={estilos.entrada}
              value={esDueno ? 'mgranica' : 'rduarte'}
              readOnly
            />
          </div>

          <div>
            <span className={estilos.etiqueta}>PIN de 4 dígitos</span>
            {/* Representación del PIN. El teclado numérico real llega con el
                módulo Usuarios; hoy es una maqueta no funcional. */}
            <div className={estilos.pin} aria-hidden="true">
              <span className={estilos.pinCasilla}>•</span>
              <span className={estilos.pinCasilla}>•</span>
              <span className={estilos.pinCasilla}>•</span>
              <span className={`${estilos.pinCasilla} ${estilos.pinCursor}`}>|</span>
            </div>
          </div>
        </div>

        <Blueprint as="button" type="button" claro className={estilos.entrar} onClick={entrar}>
          <span>{esDueno ? 'Entrar al tablero' : 'Entrar a la obra'}</span>
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor"
               strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
            <path d="M5 12h14" />
            <path d="m13 6 6 6-6 6" />
          </svg>
        </Blueprint>

        <p className={estilos.ayuda}>Olvidé mi PIN · Hablar con oficina</p>
      </div>
    </div>
  );
}

/** Tarjeta de selección de rol. */
function BotonRol({ activo, onClick, titulo, detalle, icono }) {
  return (
    <Blueprint
      as="button"
      type="button"
      claro
      onClick={onClick}
      aria-pressed={activo}
      className={`${estilos.rol} ${activo ? estilos.rolActivo : ''}`.trim()}
    >
      <svg width="26" height="26" viewBox="0 0 24 24" fill="none" stroke="currentColor"
           strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
        {icono}
      </svg>
      <span className={estilos.rolTitulo}>{titulo}</span>
      <span className={estilos.rolDetalle}>{detalle}</span>
    </Blueprint>
  );
}
