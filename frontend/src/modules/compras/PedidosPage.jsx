import { useCallback, useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import Blueprint from '../../components/ui/Blueprint';
import Modal from '../../components/ui/Modal';
import { abrirPdf } from '../../api/documentos';
import { listarMaterialesDisponibles } from '../materiales/materialesApi';
import { listarObras } from '../obras/obrasApi';
import { listarProveedores } from '../proveedores/proveedoresApi';
import { useSesion } from '../sesion/useSesion';
import AprobarPedido from './AprobarPedido';
import EnviarPorWhatsApp from './EnviarPorWhatsApp';
import RecibirPedido from './RecibirPedido';
import {
  ESTADOS_PEDIDO, anularPedido, claseDeEstadoPedido, crearPedido,
  fechaHora, listarPedidos, pesos,
} from './pedidosApi';
import estilos from './Compras.module.css';

/**
 * Listado de pedidos de materiales.
 *
 * El circuito (05/10/2026): se carga el pedido con el corralón, se le pide
 * cotización por WhatsApp, y cuando contesta se aprueba cargando los precios.
 *
 * Reemplaza el circuito por WhatsApp entre el capataz y el dueño, que hoy no
 * deja registro ni permite saber en qué estado está un pedido.
 *
 * Las acciones que ofrece cada fila dependen del estado, y no al revés: no se
 * muestra "Aprobar" en un pedido ya enviado ni "Recibir" en uno que nadie
 * aprobó. El backend rechaza esos casos igual; acá se ocultan para no ofrecer
 * algo que va a fallar.
 */
export default function PedidosPage() {
  const { puede } = useSesion();

  // Llegar desde "Cada módulo, ya filtrado" de la ficha de obra
  // (/pedidos?obra=123) tiene que dejar ese filtro cargado: antes se ignoraba
  // y el link mostraba los pedidos de todas las obras.
  const [parametros] = useSearchParams();

  const [pedidos, setPedidos] = useState([]);
  const [aEnviar, setAEnviar] = useState(null);
  const [obras, setObras] = useState([]);
  const [proveedores, setProveedores] = useState([]);
  const [materiales, setMateriales] = useState([]);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState(null);

  const [obra, setObra] = useState(parametros.get('obra') || '');
  const [estado, setEstado] = useState('');
  const [recarga, setRecarga] = useState(0);

  const [altaAbierta, setAltaAbierta] = useState(false);
  const [aAprobar, setAAprobar] = useState(null);
  const [aRecibir, setARecibir] = useState(null);
  const [aAnular, setAAnular] = useState(null);

  const recargar = useCallback(() => setRecarga((n) => n + 1), []);

  useEffect(() => {
    let vigente = true;
    (async () => {
      setCargando(true);
      try {
        const datos = await listarPedidos({ obra, estado });
        if (vigente) { setPedidos(datos); setError(null); }
      } catch (fallo) {
        if (vigente) setError(fallo.mensaje);
      } finally {
        if (vigente) setCargando(false);
      }
    })();
    return () => { vigente = false; };
  }, [obra, estado, recarga]);

  useEffect(() => {
    let vigente = true;
    listarObras().then((d) => { if (vigente) setObras(d); }).catch(() => {});
    listarProveedores({ estado: 'Activo' })
      .then((d) => { if (vigente) setProveedores(d); }).catch(() => {});
    listarMaterialesDisponibles()
      .then((d) => { if (vigente) setMateriales(d); }).catch(() => {});
    return () => { vigente = false; };
  }, []);

  const hayFiltros = obra || estado;

  return (
    <>
      <div className={estilos.cabecera}>
        <div>
          <span className="kicker kicker-acento">Módulo</span>
          <h2 className={estilos.titulo}>Pedidos de materiales</h2>
          <p className={estilos.bajada}>
            Del pedido en obra a la confirmación de que llegó, con registro de cada paso.
          </p>
        </div>

        <div className={estilos.herramientas}>
          <select className={estilos.filtro} value={obra}
                  onChange={(e) => setObra(e.target.value)} aria-label="Filtrar por obra">
            <option value="">Toda obra</option>
            {obras.map((o) => (
              <option key={o.idObra} value={o.idObra}>{o.direccionObra}</option>
            ))}
          </select>
          <select className={estilos.filtro} value={estado}
                  onChange={(e) => setEstado(e.target.value)} aria-label="Filtrar por estado">
            <option value="">Todo estado</option>
            {ESTADOS_PEDIDO.map((e) => <option key={e} value={e}>{e}</option>)}
          </select>
          <button type="button" className={estilos.botonPrimario}
                  onClick={() => setAltaAbierta(true)} disabled={materiales.length === 0}>
            Nuevo pedido
          </button>
        </div>
      </div>

      {error && <p className={estilos.errorGeneral}>{error}</p>}

      <Blueprint className={estilos.bloque}>
        {cargando && <p className={estilos.aviso}>Consultando…</p>}

        {!cargando && !error && pedidos.length === 0 && (
          <div className={estilos.vacio}>
            <p className={estilos.vacioTitulo}>
              {hayFiltros ? 'Ningún pedido coincide' : 'Todavía no hay pedidos'}
            </p>
            <p className={estilos.vacioTexto}>
              {hayFiltros
                ? 'Probá con otros filtros.'
                : 'Cargá el primer pedido con los materiales que faltan en una obra.'}
            </p>
          </div>
        )}

        {!cargando && pedidos.length > 0 && (
          <div className="scroll-x">
            <table className="table">
              <thead>
                <tr>
                  <th>Obra</th>
                  <th>Proveedor</th>
                  <th style={{ textAlign: 'right' }}>Ítems</th>
                  <th style={{ textAlign: 'right' }}>Total</th>
                  <th>Solicitado</th>
                  <th>Estado</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {pedidos.map((p) => (
                  <tr key={p.idPedido}
                      className={p.estado === 'Pendiente de Aprobación'
                        ? estilos.filaPendiente : undefined}>
                    <td>
                      <div className={estilos.obra}>{p.direccionObra}</div>
                      <div className={estilos.cliente}>{p.nombreCliente}</div>
                    </td>
                    <td className={estilos.dato}>
                      {/* El corralón se elige al cargar el pedido. Los pedidos
                          viejos, de antes de ese cambio, pueden no tenerlo. */}
                      {p.nombreProveedor ?? <span className={estilos.ayuda}>A definir</span>}
                      {p.zonaCobertura && (
                        <div className={estilos.subrubroNombre}>{p.zonaCobertura}</div>
                      )}
                    </td>
                    <td className={`cifra ${estilos.numero}`}>{p.cantidadMateriales}</td>
                    {/* Con IVA: es lo que se le paga al corralón. */}
                    <td className={`cifra ${estilos.total}`}>
                      {p.tieneTodosLosPrecios ? pesos(p.total) : '—'}
                    </td>
                    <td className={estilos.dato}>{fechaHora(p.fechaSolicitud)}</td>
                    <td>
                      <span className={claseDeEstadoPedido(p.estado, estilos)}>{p.estado}</span>
                      {p.notaDiferencia && <p className={estilos.nota}>{p.notaDiferencia}</p>}
                    </td>
                    <td className={estilos.acciones}>
                      {/* La orden en PDF para mandarle al corralón. Va en
                          cualquier estado: sirve aprobada, para que preparen el
                          pedido, y sin aprobar, para pedir cotización. */}
                      <button type="button" className={estilos.accion}
                              onClick={() => abrirPdf(`/pedidos/${p.idPedido}/orden`,
                                                      `pedido-${p.idPedido}.pdf`)}>
                        Orden PDF
                      </button>
                      {/* Pedirle cotización al corralón, mientras el pedido
                          espera los precios. El permiso lo vuelve a exigir el
                          backend: escribirle al proveedor es del dueño. */}
                      {p.estado === 'Pendiente de Aprobación' && p.idProveedor
                        && puede('compras.aprobar') && (
                        <button type="button" className={estilos.accion}
                                onClick={() => setAEnviar(p)}>
                          Pedir cotización
                        </button>
                      )}
                      {p.estado === 'Pendiente de Aprobación' && (
                        <button type="button" className={estilos.accion}
                                onClick={() => setAAprobar(p)}>
                          Aprobar
                        </button>
                      )}
                      {p.estado === 'Enviado al Proveedor' && (
                        <button type="button" className={estilos.accion}
                                onClick={() => setARecibir(p)}>
                          Confirmar recepción
                        </button>
                      )}
                      {(p.estado === 'Pendiente de Aprobación'
                        || p.estado === 'Enviado al Proveedor') && (
                        <button type="button" className={estilos.accionPeligro}
                                onClick={() => setAAnular(p)}>
                          Anular
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Blueprint>

      {altaAbierta && (
        <NuevoPedidoModal
          obras={obras}
          proveedores={proveedores}
          materiales={materiales}
          onCerrar={() => setAltaAbierta(false)}
          onCreado={(creado) => {
            setAltaAbierta(false);
            recargar();
            // Recién cargado, lo que sigue es pedirle cotización al corralón:
            // se abre directamente el WhatsApp.
            if (puede('compras.aprobar')) setAEnviar(creado);
          }}
        />
      )}

      {aAprobar && (
        <AprobarPedido
          pedido={aAprobar}
          proveedores={proveedores}
          onCerrar={() => setAAprobar(null)}
          onAprobado={() => { setAAprobar(null); recargar(); }}
        />
      )}

      {aEnviar && (
        <EnviarPorWhatsApp pedido={aEnviar} onCerrar={() => setAEnviar(null)} />
      )}

      {aRecibir && (
        <RecibirPedido
          pedido={aRecibir}
          onCerrar={() => setARecibir(null)}
          onRecibido={() => { setARecibir(null); recargar(); }}
        />
      )}

      {aAnular && (
        <AnularPedidoModal
          pedido={aAnular}
          onCerrar={() => setAAnular(null)}
          onAnulado={() => { setAAnular(null); recargar(); }}
        />
      )}
    </>
  );
}

/* ========================================================================== */

/**
 * Alta de un pedido: la obra, el corralón y los materiales con su cantidad.
 *
 * El corralón se elige acá (05/10/2026) porque lo que sigue es pedirle
 * cotización. Los precios se cargan después, al aprobar, con lo que conteste.
 */
function NuevoPedidoModal({ obras, proveedores, materiales, onCerrar, onCreado }) {
  const [idObra, setIdObra] = useState('');
  const [idProveedor, setIdProveedor] = useState('');
  const [lineas, setLineas] = useState([{ idMaterial: '', cantidad: '' }]);
  const [error, setError] = useState(null);
  const [guardando, setGuardando] = useState(false);

  const obrasDisponibles = obras.filter(
    (o) => o.estado !== 'Cancelada' && o.estado !== 'Finalizada');

  /**
   * Actualiza el material o la cantidad de una línea del pedido que se está
   * armando.
   */
  const cambiarLinea = (indice, campo) => (e) => {
    const valor = e.target.value;
    setLineas((previas) => previas.map((l, i) => (i === indice ? { ...l, [campo]: valor } : l)));
  };

  /**
   * Agrega una línea vacía al pedido para sumar otro material.
   */
  const agregarLinea = () => setLineas((p) => [...p, { idMaterial: '', cantidad: '' }]);
  /**
   * Saca una línea del pedido.
   */
  const quitarLinea = (i) => setLineas((p) => p.filter((_, j) => j !== i));

  // Un material no puede ir dos veces: la clave primaria compuesta de
  // pedido_material lo impide, así que se filtra de los desplegables.
  const yaElegidos = new Set(lineas.map((l) => l.idMaterial).filter(Boolean));

  /**
   * Envía el pedido nuevo al servidor. Queda pendiente de la aprobación del
   * dueño.
   */
  const enviar = async (evento) => {
    evento.preventDefault();
    setGuardando(true);
    setError(null);
    try {
      const creado = await crearPedido({
        idObra: Number(idObra),
        idProveedor: Number(idProveedor),
        materiales: lineas.map((l) => ({
          idMaterial: Number(l.idMaterial), cantidad: l.cantidad,
        })),
      });
      onCreado(creado);
    } catch (fallo) {
      setError(fallo.mensaje);
    } finally {
      setGuardando(false);
    }
  };

  return (
    <Modal abierto onCerrar={onCerrar} titulo="Nuevo pedido de materiales">
      <form onSubmit={enviar}>
        {error && <p className={estilos.errorGeneral}>{error}</p>}

        <div className={estilos.campo}>
          <label className={estilos.etiqueta} htmlFor="idObra">
            Obra <span className={estilos.obligatorio}>*</span>
          </label>
          <select id="idObra" className={estilos.control} value={idObra}
                  onChange={(e) => setIdObra(e.target.value)} required autoFocus>
            <option value="">Elegir…</option>
            {obrasDisponibles.map((o) => (
              <option key={o.idObra} value={o.idObra}>
                {o.direccionObra} — {o.nombreCliente}
              </option>
            ))}
          </select>
        </div>

        <div className={estilos.campo}>
          <label className={estilos.etiqueta} htmlFor="idProveedor">
            Corralón <span className={estilos.obligatorio}>*</span>
          </label>
          <select id="idProveedor" className={estilos.control} value={idProveedor}
                  onChange={(e) => setIdProveedor(e.target.value)} required>
            <option value="">Elegir corralón…</option>
            {proveedores.map((p) => (
              <option key={p.idProveedor} value={p.idProveedor}>
                {p.nombreProveedor} — {p.zonaCobertura}
              </option>
            ))}
          </select>
          <p className={estilos.ayuda}>
            Es a quien se le pide la cotización. La zona es el criterio
            principal: un corralón que no llega a la obra no sirve por más
            barato que sea.
          </p>
        </div>

        <div className={estilos.campo}>
          <label className={estilos.etiqueta}>
            Materiales <span className={estilos.obligatorio}>*</span>
          </label>
          {lineas.map((linea, i) => (
            // eslint-disable-next-line react/no-array-index-key
            <div key={i} className={estilos.lineaMaterial}>
              <select className={estilos.control} value={linea.idMaterial}
                      onChange={cambiarLinea(i, 'idMaterial')} required
                      aria-label={`Material ${i + 1}`}>
                <option value="">Elegir material…</option>
                {materiales
                  .filter((m) => !yaElegidos.has(String(m.idMaterial))
                    || String(m.idMaterial) === linea.idMaterial)
                  .map((m) => (
                    <option key={m.idMaterial} value={m.idMaterial}>
                      {m.nombreMaterial} — por {m.unidadMedida}
                    </option>
                  ))}
              </select>
              <input type="number" step="0.01" min="0.01" className={estilos.control}
                     placeholder="Cantidad" value={linea.cantidad}
                     onChange={cambiarLinea(i, 'cantidad')} required
                     aria-label={`Cantidad ${i + 1}`} />
              <button type="button" className={estilos.quitarLinea}
                      onClick={() => quitarLinea(i)} disabled={lineas.length === 1}
                      aria-label="Quitar material">
                ×
              </button>
            </div>
          ))}
          <button type="button" className={estilos.botonSecundario} onClick={agregarLinea}>
            Agregar material
          </button>
          <p className={estilos.ayuda}>
            Al guardar se abre WhatsApp para pedirle cotización al corralón. Los
            precios se cargan al aprobar, con lo que conteste.
          </p>
        </div>

        <div className={estilos.accionesFormulario}>
          <button type="button" className={estilos.botonSecundario} onClick={onCerrar}>
            Cancelar
          </button>
          <button type="submit" className={estilos.botonPrimario} disabled={guardando}>
            {guardando ? 'Guardando…' : 'Cargar pedido'}
          </button>
        </div>
      </form>
    </Modal>
  );
}

/* ========================================================================== */

/**
 * Ventana para anular un pedido, pidiendo el motivo.
 */
function AnularPedidoModal({ pedido, onCerrar, onAnulado }) {
  const [motivo, setMotivo] = useState('');
  const [error, setError] = useState(null);
  const [guardando, setGuardando] = useState(false);

  /**
   * Envía la anulación al servidor y, si sale bien, cierra la ventana.
   */
  const enviar = async (evento) => {
    evento.preventDefault();
    setGuardando(true);
    setError(null);
    try {
      await anularPedido(pedido.idPedido, motivo);
      onAnulado();
    } catch (fallo) {
      setError(fallo.mensaje);
    } finally {
      setGuardando(false);
    }
  };

  return (
    <Modal abierto onCerrar={onCerrar} titulo="Anular pedido">
      <form onSubmit={enviar}>
        <p className={estilos.confirmacion}>
          Se va a anular el pedido de <b>{pedido.direccionObra}</b>.
        </p>
        <p className={estilos.ayuda}>
          El pedido no se elimina: queda registrado como anulado con el motivo,
          para conservar el historial de lo que se le pidió a cada proveedor.
        </p>

        {error && <p className={estilos.errorGeneral}>{error}</p>}

        <div className={estilos.campo}>
          <label className={estilos.etiqueta} htmlFor="motivo">
            Motivo <span className={estilos.obligatorio}>*</span>
          </label>
          <input id="motivo" className={estilos.control} maxLength={200}
                 placeholder="El proveedor no tenía stock" value={motivo}
                 onChange={(e) => setMotivo(e.target.value)} required autoFocus />
        </div>

        <div className={estilos.accionesFormulario}>
          <button type="button" className={estilos.botonSecundario} onClick={onCerrar}>
            Cancelar
          </button>
          <button type="submit" className={estilos.botonPeligro} disabled={guardando}>
            {guardando ? 'Anulando…' : 'Anular pedido'}
          </button>
        </div>
      </form>
    </Modal>
  );
}
