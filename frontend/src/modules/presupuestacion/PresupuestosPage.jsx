import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import Blueprint from '../../components/ui/Blueprint';
import Modal from '../../components/ui/Modal';
import { listarObras } from '../obras/obrasApi';
import {
  TIPOS, crearPresupuesto, eliminarPresupuesto, listarPorObra, pesos,
} from './presupuestosApi';
import estilos from './Presupuestos.module.css';

/**
 * Listado de presupuestos, agrupado por obra.
 *
 * Reemplaza el circuito actual, donde cada presupuesto se arma desde cero en
 * Excel, las versiones se pisan entre si y no queda registro de cual aprobo el
 * cliente. Aca cada version es un registro propio, con su tipo, su numero y su
 * estado.
 *
 * Ricardo pidio al probar el sistema que la entrada fuera la obra y no el
 * presupuesto: antes esta pantalla mezclaba en una sola lista los presupuestos
 * de todas las obras, y para saber en que instancia estaba una habia que
 * buscarla entre las filas. Ahora hay una fila por obra, y al abrirla se ven
 * sus instancias.
 */
export default function PresupuestosPage() {
  const [obras, setObras] = useState([]);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState(null);

  const [busqueda, setBusqueda] = useState('');
  const [altaAbierta, setAltaAbierta] = useState(false);

  const [recarga, setRecarga] = useState(0);

  useEffect(() => {
    let vigente = true;

    (async () => {
      try {
        const datos = await listarPorObra();
        if (vigente) {
          setObras(datos);
          setError(null);
        }
      } catch (fallo) {
        if (vigente) setError(fallo.mensaje);
      } finally {
        if (vigente) setCargando(false);
      }
    })();

    return () => { vigente = false; };
  }, [recarga]);

  const recargar = useCallback(() => {
    setCargando(true);
    setRecarga((n) => n + 1);
  }, []);

  // La busqueda filtra aca y no en el servidor: son pocas obras y el listado ya
  // esta en memoria, asi que pedirlo de nuevo en cada tecla seria peor.
  const texto = busqueda.trim().toLowerCase();
  const visibles = texto
    ? obras.filter((o) => (o.direccionObra + ' ' + o.nombreCliente).toLowerCase().includes(texto))
    : obras;

  return (
    <>
      <div className={estilos.cabecera}>
        <div>
          <span className="kicker kicker-acento">Módulo</span>
          <h2 className={estilos.titulo}>Presupuestos</h2>
          <p className={estilos.bajada}>
            Una fila por obra; adentro, sus instancias.{' '}
            <Link to="/presupuestos/catalogo">Ver catálogo de rubros</Link>
          </p>
        </div>

        <div className={estilos.herramientas}>
          <input className={`${estilos.filtro} ${estilos.buscador}`} value={busqueda} type="search"
                 placeholder="Buscar por obra o cliente"
                 onChange={(e) => setBusqueda(e.target.value)}
                 aria-label="Buscar por obra o cliente" />
          <button type="button" className={estilos.botonPrimario} onClick={() => setAltaAbierta(true)}>
            Nuevo presupuesto
          </button>
        </div>
      </div>

      <Blueprint className={estilos.bloque}>
        {error && <p className={estilos.errorGeneral}>{error}</p>}
        {cargando && <p className={estilos.aviso}>Consultando…</p>}

        {!cargando && !error && visibles.length === 0 && (
          <div className={estilos.vacio}>
            <p className={estilos.vacioTitulo}>
              {texto ? 'Ninguna obra coincide' : 'Todavía no hay presupuestos'}
            </p>
            <p className={estilos.vacioTexto}>
              {texto
                ? 'Probá con otra dirección o con el nombre del cliente.'
                : 'El circuito arranca con la cotización inicial de una obra ya creada.'}
            </p>
          </div>
        )}

        {!cargando && visibles.length > 0 && (
          <div className="scroll-x">
            <table className="table">
              <thead>
                <tr>
                  <th>Obra</th>
                  <th>Instancias</th>
                  <th>Vigente</th>
                  <th style={{ textAlign: 'right' }}>Total vigente</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {visibles.map((o) => (
                  <tr key={o.idObra}>
                    <td>
                      <div className={estilos.obra}>{o.direccionObra}</div>
                      <div className={estilos.cliente}>
                        {o.nombreCliente} · {o.tipoObra} · {o.estadoObra}
                      </div>
                    </td>

                    {/* El recorrido del circuito de un vistazo: qué instancias
                        tiene la obra y cuántas versiones de cada una. */}
                    <td>
                      <div className={estilos.instancias}>
                        {resumirInstancias(o.presupuestos).map((i) => (
                          <span key={i.tipo} className={estilos.instancia}>
                            {i.tipo}
                            {i.veces > 1 && <b className={estilos.veces}>×{i.veces}</b>}
                          </span>
                        ))}
                      </div>
                    </td>

                    <td>
                      <div className={estilos.dato}>{o.tipoVigente}</div>
                      <span className={claseDeEstado(o.estadoVigente)}>{o.estadoVigente}</span>
                    </td>

                    <td className={`cifra ${estilos.total}`}>{pesos(o.totalVigente)}</td>

                    <td className={estilos.acciones}>
                      <Link className={estilos.accion} to={`/presupuestos/obra/${o.idObra}`}>
                        Ver las instancias
                      </Link>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

      </Blueprint>

      {altaAbierta && (
        <NuevoPresupuestoModal
          onCerrar={() => setAltaAbierta(false)}
          onCreado={() => { setAltaAbierta(false); recargar(); }}
        />
      )}
    </>
  );
}

/**
 * Confirmación de baja.
 *
 * Pide confirmar porque la baja es definitiva y no hay papelera: a diferencia
 * del resto del sistema, acá el registro desaparece de verdad.
 *
 * El error del backend se muestra tal cual llega. Es el que explica el motivo
 * real cuando no se puede borrar (por ejemplo, que otro presupuesto se generó a
 * partir de éste), y duplicar esa regla acá la dejaría desactualizada.
 */
export function ConfirmarEliminacion({ presupuesto, onCerrar, onEliminado }) {
  const [error, setError] = useState(null);
  const [eliminando, setEliminando] = useState(false);

  const esDefinitivoAprobado = presupuesto.tipoPresupuesto === 'Definitivo'
    && presupuesto.estado === 'Aprobado';

  const confirmar = async () => {
    setEliminando(true);
    setError(null);
    try {
      await eliminarPresupuesto(presupuesto.idPresupuesto);
      onEliminado();
    } catch (fallo) {
      setError(fallo.mensaje);
    } finally {
      setEliminando(false);
    }
  };

  return (
    <Modal abierto onCerrar={onCerrar} titulo="Eliminar presupuesto">
      <p className={estilos.confirmacion}>
        Se va a eliminar el{' '}
        <b>{presupuesto.tipoPresupuesto.toLowerCase()} v{presupuesto.version}</b>
        {' '}de <b>{presupuesto.direccionObra}</b>, junto con todos sus ítems.
      </p>
      {/* Aprobar el definitivo puso la obra en ejecución. Al eliminarlo el
          backend deshace ese cambio, y conviene avisarlo antes: es un efecto
          sobre otro módulo que desde esta pantalla no se ve. */}
      {esDefinitivoAprobado && (
        <p className={estilos.advertencia}>
          Es el definitivo aprobado de esta obra. Al eliminarlo, la obra vuelve
          a <b>En presupuestación</b> y se borra su fecha de inicio real.
        </p>
      )}

      <p className={estilos.ayuda}>
        La baja es definitiva y no se puede deshacer. Si el presupuesto quedó sin
        efecto pero querés conservar el registro, marcalo como Rechazado desde su
        pantalla en lugar de eliminarlo.
      </p>

      {error && <p className={estilos.errorGeneral}>{error}</p>}

      <div className={estilos.accionesFormulario}>
        <button type="button" className={estilos.botonSecundario} onClick={onCerrar}>
          Cancelar
        </button>
        <button type="button" className={estilos.botonPeligro} onClick={confirmar}
                disabled={eliminando}>
          {eliminando ? 'Eliminando…' : 'Eliminar'}
        </button>
      </div>
    </Modal>
  );
}

/**
 * Alta de un presupuesto.
 *
 * El formulario cambia según el tipo: la cotización inicial pide metros y valor
 * por metro, y el resto no pide nada más porque los ítems se cargan después,
 * dentro del presupuesto.
 */
function NuevoPresupuestoModal({ onCerrar, onCreado }) {
  const [obras, setObras] = useState([]);
  const [datos, setDatos] = useState({
    idObra: '', tipoPresupuesto: '', metrosCuadrados: '', valorPorM2: '', plazoEstimadoObra: '',
  });
  const [camposInvalidos, setCamposInvalidos] = useState({});
  const [error, setError] = useState(null);
  const [guardando, setGuardando] = useState(false);

  useEffect(() => {
    // Solo obras que todavía pueden recibir presupuestos.
    listarObras({ estado: 'En presupuestación' })
      .then(setObras)
      .catch((fallo) => setError(fallo.mensaje));
  }, []);

  const esCotizacion = datos.tipoPresupuesto === 'Cotización inicial';
  const obraElegida = obras.find((o) => String(o.idObra) === datos.idObra);

  const cambiar = (campo) => (e) => {
    setDatos((previo) => ({ ...previo, [campo]: e.target.value }));
    setCamposInvalidos((previo) => ({ ...previo, [campo]: undefined }));
  };

  const enviar = async (evento) => {
    evento.preventDefault();
    setGuardando(true);
    setCamposInvalidos({});
    setError(null);

    try {
      onCreado(await crearPresupuesto({
        idObra: Number(datos.idObra),
        tipoPresupuesto: datos.tipoPresupuesto,
        metrosCuadrados: esCotizacion && datos.metrosCuadrados ? datos.metrosCuadrados : null,
        valorPorM2: esCotizacion && datos.valorPorM2 ? datos.valorPorM2 : null,
        plazoEstimadoObra: datos.plazoEstimadoObra || null,
      }));
    } catch (fallo) {
      // Acá llegan los 409 del circuito: "el anteproyecto corresponde solo a
      // las reformas", "sin un anteproyecto previo"…
      if (fallo.camposInvalidos) setCamposInvalidos(fallo.camposInvalidos);
      else setError(fallo.mensaje);
    } finally {
      setGuardando(false);
    }
  };

  return (
    <Modal abierto onCerrar={onCerrar} titulo="Nuevo presupuesto">
      <form onSubmit={enviar}>
        {error && <p className={estilos.errorGeneral}>{error}</p>}

        <div className={estilos.campo}>
          <label className={estilos.etiqueta} htmlFor="idObra">
            Obra <span className={estilos.obligatorio}>*</span>
          </label>
          <select id="idObra" className={estilos.control} value={datos.idObra}
                  onChange={cambiar('idObra')} required>
            <option value="">Elegir obra…</option>
            {obras.map((o) => (
              <option key={o.idObra} value={o.idObra}>
                {o.direccionObra} — {o.nombreCliente} ({o.tipoObra})
              </option>
            ))}
          </select>
          {obras.length === 0 && (
            <p className={estilos.ayuda}>
              No hay obras en presupuestación. Creá una desde el módulo Obras.
            </p>
          )}
        </div>

        <div className={estilos.campo}>
          <label className={estilos.etiqueta} htmlFor="tipoPresupuesto">
            Instancia <span className={estilos.obligatorio}>*</span>
          </label>
          <select id="tipoPresupuesto" className={estilos.control} value={datos.tipoPresupuesto}
                  onChange={cambiar('tipoPresupuesto')} required>
            <option value="">Elegir…</option>
            {TIPOS.map((t) => <option key={t} value={t}>{t}</option>)}
          </select>
          {/* El tipo de obra determina el circuito, así que conviene decirlo
              antes de que el servidor lo rechace. */}
          {obraElegida?.tipoObra === 'Construcción' && (
            <p className={estilos.ayuda}>
              Es una construcción: no lleva anteproyecto, va directo al definitivo.
            </p>
          )}
          {obraElegida?.tipoObra === 'Reforma' && (
            <p className={estilos.ayuda}>
              Es una reforma: el definitivo necesita un anteproyecto previo.
            </p>
          )}
        </div>

        {esCotizacion && (
          <div className={estilos.dosColumnas}>
            <div className={estilos.campo}>
              <label className={estilos.etiqueta} htmlFor="metrosCuadrados">
                Metros cuadrados <span className={estilos.obligatorio}>*</span>
              </label>
              <input id="metrosCuadrados" type="number" step="0.01" min="0.01"
                     className={estilos.control} value={datos.metrosCuadrados}
                     onChange={cambiar('metrosCuadrados')} required />
              {camposInvalidos.metrosCuadrados && (
                <p className={estilos.errorCampo}>{camposInvalidos.metrosCuadrados}</p>
              )}
            </div>
            <div className={estilos.campo}>
              <label className={estilos.etiqueta} htmlFor="valorPorM2">
                Valor por m² <span className={estilos.obligatorio}>*</span>
              </label>
              <input id="valorPorM2" type="number" step="0.01" min="0.01"
                     className={estilos.control} value={datos.valorPorM2}
                     onChange={cambiar('valorPorM2')} required />
              {camposInvalidos.valorPorM2 && (
                <p className={estilos.errorCampo}>{camposInvalidos.valorPorM2}</p>
              )}
            </div>
          </div>
        )}

        <div className={estilos.campo}>
          <label className={estilos.etiqueta} htmlFor="plazoEstimadoObra">Plazo estimado</label>
          <input id="plazoEstimadoObra" className={estilos.control} maxLength={100}
                 placeholder="6 meses" value={datos.plazoEstimadoObra}
                 onChange={cambiar('plazoEstimadoObra')} />
        </div>

        <div className={estilos.accionesFormulario}>
          <button type="button" className={estilos.botonSecundario} onClick={onCerrar}>
            Cancelar
          </button>
          <button type="submit" className={estilos.botonPrimario} disabled={guardando}>
            {guardando ? 'Creando…' : 'Crear'}
          </button>
        </div>
      </form>
    </Modal>
  );
}

/**
 * Cuantas versiones tiene cada instancia, en el orden en que llegan.
 *
 * El backend ya devuelve los presupuestos ordenados por el circuito, asi que
 * alcanza con recorrerlos y contar: no hay que volver a ordenar nada aca.
 */
function resumirInstancias(presupuestos) {
  const conteo = [];
  presupuestos.forEach((p) => {
    const ya = conteo.find((i) => i.tipo === p.tipoPresupuesto);
    if (ya) ya.veces += 1;
    else conteo.push({ tipo: p.tipoPresupuesto, veces: 1 });
  });
  return conteo;
}

export function claseDeEstado(estado) {
  if (estado === 'Aprobado') return estilos.estadoAprobado;
  if (estado === 'Enviado') return estilos.estadoEnviado;
  if (estado === 'Rechazado') return estilos.estadoRechazado;
  return estilos.estadoBorrador;
}
