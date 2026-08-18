import { Link } from 'react-router-dom';
import Blueprint from '../../../components/ui/Blueprint';
import { useDemo } from '../../../datos/contextoDemo';
import { HITOS, OBRAS, OBRA_DEL_CAPATAZ, avancePorHitos } from '../../../datos/demo';
import estilos from './Home.module.css';

/**
 * Pantalla de inicio del capataz.
 *
 * Muestra una sola obra —la que tiene asignada— y dos accesos, nada más. El
 * relevamiento fue claro en esto: si la herramienta complica el trabajo en
 * obra, no se usa. Por eso acá no hay menús, listas ni información financiera:
 * cuánto va la obra, y las dos cosas que el capataz necesita hacer.
 */
export default function Home() {
  const { hitosCompletados } = useDemo();

  const obra = OBRAS[OBRA_DEL_CAPATAZ];
  const avance = avancePorHitos(hitosCompletados);
  const cumplidos = HITOS.filter((_, i) => hitosCompletados[i]).length;

  return (
    <>
      <header className={estilos.cabecera}>
        <div className={estilos.cabeceraFila}>
          <span className={estilos.marca}>SIGCO</span>
          <span className={estilos.momento}>{momentoActual()}</span>
        </div>

        <p className={estilos.kicker}>Tu obra de hoy</p>
        <h1 className={estilos.obra}>{obra.nombre}</h1>
        <p className={estilos.direccion}>{obra.dir}</p>
      </header>

      <div className={estilos.cuerpo}>

        <Blueprint className={estilos.avanceBloque}>
          <div className={estilos.avanceCabecera}>
            <span className="kicker">Avance de obra</span>
            <span className={estilos.avanceHitos}>{cumplidos}/{HITOS.length} hitos</span>
          </div>

          <div className={estilos.avanceCifra}>
            <span className={`cifra ${estilos.avanceNumero}`}>{avance}</span>
            <span className={`cifra ${estilos.avanceSimbolo}`}>%</span>
          </div>

          <div className={estilos.avancePista}>
            <span className={estilos.avanceRelleno} style={{ width: `${avance}%` }} />
          </div>
        </Blueprint>

        {/* Dos acciones, de 104 px. Son los únicos objetos tocables de la
            pantalla: imposible errarles con el dedo. */}
        <div className={estilos.acciones}>
          <Blueprint as={Link} to="/obra/hitos" claro className={`${estilos.accion} ${estilos.accionPrimaria}`}>
            <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                 strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
              <path d="M20 6 9 17l-5-5" />
            </svg>
            <span className={estilos.accionTexto}>Marcar hito<br />completado</span>
          </Blueprint>

          <Blueprint as={Link} to="/obra/materiales" className={estilos.accion}>
            <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                 strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
              <path d="M3 6h18" />
              <path d="M6 6v13a1 1 0 0 0 1 1h10a1 1 0 0 0 1-1V6" />
              <path d="M12 10v6" />
              <path d="M9 13h6" />
            </svg>
            <span className={estilos.accionTexto}>Pedir<br />materiales</span>
          </Blueprint>
        </div>

        {/* Novedades del día. Solo estado operativo: ningún importe, porque el
            capataz no accede a la información financiera de la obra. */}
        <section className={estilos.novedades}>
          <div className={estilos.novedadesCabecera}>
            <span>Hoy en la obra</span>
            <span>6 en cuadrilla</span>
          </div>

          <div className={estilos.novedad}>
            <span className={estilos.punto} style={{ background: 'var(--color-alerta)' }} />
            <span className={estilos.novedadTexto}>Pedido de cemento en aprobación</span>
            <span className={estilos.novedadHora}>ayer 16:20</span>
          </div>

          <div className={`${estilos.novedad} ${estilos.novedadUltima}`}>
            <span className={estilos.punto} style={{ background: 'var(--color-ok)' }} />
            <span className={estilos.novedadTexto}>Recepción de hierro confirmada</span>
            <span className={estilos.novedadHora}>lun 11:05</span>
          </div>
        </section>

      </div>
    </>
  );
}

/**
 * "Mar 18/08 · 07:40" con la fecha y hora reales del equipo.
 *
 * hour12: false es obligatorio. Sin eso el navegador devuelve "07:40 p. m.",
 * que además de no ser como se dice la hora acá, es tan largo que desborda el
 * ancho de un celular.
 */
function momentoActual() {
  const ahora = new Date();
  const dia = ahora.toLocaleDateString('es-AR', { weekday: 'short' });
  const fecha = ahora.toLocaleDateString('es-AR', { day: '2-digit', month: '2-digit' });
  const hora = ahora.toLocaleTimeString('es-AR', {
    hour: '2-digit', minute: '2-digit', hour12: false,
  });
  return `${dia.charAt(0).toUpperCase()}${dia.slice(1)} ${fecha} · ${hora}`;
}
