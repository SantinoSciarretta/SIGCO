import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import Blueprint from '../../../components/ui/Blueprint';
import { avanceDeObra } from '../../../modules/seguimiento/seguimientoApi';
import { listarPedidos } from '../../../modules/compras/pedidosApi';
import { useSesion } from '../../../modules/sesion/useSesion';
import { useObraDelCapataz } from './useObraDelCapataz';
import estilos from './Home.module.css';

/**
 * Pantalla de inicio del capataz.
 *
 * Muestra una sola obra —la que tiene asignada— y dos accesos, nada más. El
 * relevamiento fue claro en esto: si la herramienta complica el trabajo en
 * obra, no se usa. Por eso acá no hay menús, listas ni información financiera:
 * cuánto va la obra, y las dos cosas que el capataz necesita hacer.
 *
 * El avance sale de los hitos completados, no de una estimación que alguien
 * declara. Es lo que resuelve el problema del cronograma que se arma al inicio
 * y nunca se actualiza.
 */
export default function Home() {
  const { sesion, puede } = useSesion();
  const { obra, obras, elegir, cargando, error } = useObraDelCapataz();

  const [avance, setAvance] = useState(null);
  const [pedidos, setPedidos] = useState([]);

  useEffect(() => {
    if (!obra) return undefined;
    let vigente = true;

    avanceDeObra(obra.idObra)
      .then((a) => { if (vigente) setAvance(a); })
      .catch(() => {});

    // Las novedades son los últimos movimientos de materiales. Nunca importes:
    // el capataz no accede a la información financiera de la obra.
    listarPedidos({ obra: obra.idObra })
      .then((p) => { if (vigente) setPedidos(p.slice(0, 3)); })
      .catch(() => {});

    return () => { vigente = false; };
  }, [obra]);

  if (cargando) {
    return <p className={estilos.aviso}>Buscando tu obra…</p>;
  }

  if (error) {
    return <p className={estilos.aviso}>{error}</p>;
  }

  // Un capataz de obra sin obra asignada. Se dice el motivo en vez de mostrar
  // una pantalla vacía que parece un error del sistema.
  if (obras.length === 0) {
    return (
      <div className={estilos.cuerpo}>
        <p className={estilos.aviso}>
          No tenés ninguna obra asignada. Pedile a la oficina que te asigne a
          una desde el módulo Personal.
        </p>
      </div>
    );
  }

  // Varias obras: el capataz general elige en cuál está trabajando.
  if (!obra) {
    return (
      <>
        <header className={estilos.cabecera}>
          <div className={estilos.cabeceraFila}>
            <span className={estilos.marca}>SIGCO</span>
            <span className={estilos.momento}>{momentoActual()}</span>
          </div>
          <p className={estilos.kicker}>Elegí la obra</p>
          <h1 className={estilos.obra}>¿Dónde estás hoy?</h1>
        </header>

        <div className={estilos.cuerpo}>
          <ul className={estilos.listaObras}>
            {obras.map((o) => (
              <li key={o.idObra}>
                <button type="button" className={estilos.obraOpcion}
                        onClick={() => elegir(o.idObra)}>
                  <span className={estilos.obraOpcionNombre}>{o.direccionObra}</span>
                  <span className={estilos.obraOpcionCliente}>{o.nombreCliente}</span>
                </button>
              </li>
            ))}
          </ul>
        </div>
      </>
    );
  }

  const porcentaje = avance ? Math.round(Number(avance.avanceFisico)) : 0;

  return (
    <>
      <header className={estilos.cabecera}>
        <div className={estilos.cabeceraFila}>
          <span className={estilos.marca}>SIGCO</span>
          <span className={estilos.momento}>{momentoActual()}</span>
        </div>

        <p className={estilos.kicker}>
          Tu obra de hoy{sesion?.nombreUsuario ? ` · ${sesion.nombreUsuario}` : ''}
        </p>
        <h1 className={estilos.obra}>{obra.direccionObra}</h1>
        <p className={estilos.direccion}>{obra.nombreCliente}</p>
      </header>

      <div className={estilos.cuerpo}>

        <Blueprint className={estilos.avanceBloque}>
          <div className={estilos.avanceCabecera}>
            <span className="kicker">Avance de obra</span>
            <span className={estilos.avanceHitos}>
              {avance ? `${avance.hitosCompletados}/${avance.hitosTotales} hitos` : '—'}
            </span>
          </div>

          <div className={estilos.avanceCifra}>
            <span className={`cifra ${estilos.avanceNumero}`}>{porcentaje}</span>
            <span className={`cifra ${estilos.avanceSimbolo}`}>%</span>
          </div>

          <div className={estilos.avancePista}>
            <span className={estilos.avanceRelleno} style={{ width: `${porcentaje}%` }} />
          </div>

          {avance?.atrasada && (
            <p className={estilos.atrasada}>
              La obra pasó su fecha estimada de fin.
            </p>
          )}
        </Blueprint>

        {/* Dos acciones, de 104 px. Son los únicos objetos tocables de la
            pantalla: imposible errarles con el dedo. */}
        <div className={estilos.acciones}>
          <Blueprint as={Link} to="/obra/hitos" claro
                     className={`${estilos.accion} ${estilos.accionPrimaria}`}>
            <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                 strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
              <path d="M20 6 9 17l-5-5" />
            </svg>
            {/* El texto depende del permiso: el Capataz de Obra tiene
                Seguimiento en "Consulta" según la matriz del informe, así que
                mira el avance pero no marca hitos. El Capataz General sí. */}
            <span className={estilos.accionTexto}>
              {puede('seguimiento.editar')
                ? <>Marcar hito<br />completado</>
                : <>Ver avance<br />de la obra</>}
            </span>
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
        {pedidos.length > 0 && (
          <section className={estilos.novedades}>
            <div className={estilos.novedadesCabecera}>
              <span>Últimos movimientos</span>
              <Link to="/obra/materiales" className={estilos.verTodos}>Ver todos</Link>
            </div>

            {pedidos.map((pedido, i) => (
              <div key={pedido.idPedido}
                   className={`${estilos.novedad} ${i === pedidos.length - 1 ? estilos.novedadUltima : ''}`.trim()}>
                <span className={estilos.punto} style={{ background: colorDeEstado(pedido.estado) }} />
                <span className={estilos.novedadTexto}>{textoDeEstado(pedido)}</span>
                <span className={estilos.novedadHora}>{cuando(pedido)}</span>
              </div>
            ))}
          </section>
        )}

        {obras.length > 1 && (
          <button type="button" className={estilos.cambiarObra}
                  onClick={() => elegir(null)}>
            Cambiar de obra
          </button>
        )}

      </div>
    </>
  );
}

/* --------------------------------------------------------------------------
   Presentación del estado de un pedido.

   El capataz no necesita el nombre técnico del estado: necesita saber si tiene
   que hacer algo. "Esperando aprobación" y "Llegó: falta confirmar" dicen eso;
   "Pendiente de Aprobación" y "Enviado al Proveedor" no.
   -------------------------------------------------------------------------- */

function colorDeEstado(estado) {
  if (estado === 'Pendiente de Aprobación') return 'var(--color-alerta)';
  if (estado === 'Enviado al Proveedor') return 'var(--color-accent)';
  if (estado === 'Anulado') return 'var(--color-neutral-500)';
  return 'var(--color-ok)';
}

function textoDeEstado(pedido) {
  const cuantos = `${pedido.materiales?.length ?? 0} materiales`;
  switch (pedido.estado) {
    case 'Pendiente de Aprobación': return `Pedido de ${cuantos} · esperando aprobación`;
    case 'Enviado al Proveedor': return `Pedido en camino · falta confirmar la recepción`;
    case 'Recibido Completo': return `Recepción confirmada · vino todo`;
    case 'Recibido con Diferencias': return `Recepción confirmada · hubo diferencias`;
    case 'Anulado': return 'Pedido anulado';
    default: return pedido.estado;
  }
}

/** La fecha del último movimiento del pedido, que es la que le importa al capataz. */
function cuando(pedido) {
  const iso = pedido.fechaRecepcion ?? pedido.fechaAprobacion ?? pedido.fechaSolicitud;
  if (!iso) return '';
  const f = new Date(iso);
  const hoy = new Date();
  const mismoDia = f.toDateString() === hoy.toDateString();
  return mismoDia
    ? f.toLocaleTimeString('es-AR', { hour: '2-digit', minute: '2-digit', hour12: false })
    : f.toLocaleDateString('es-AR', { day: '2-digit', month: '2-digit' });
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
