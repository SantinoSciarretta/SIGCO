import { useCallback, useEffect, useState } from 'react';
import Blueprint from '../../components/ui/Blueprint';
import Modal from '../../components/ui/Modal';
import { listarObras } from '../obras/obrasApi';
import { listarRubros } from '../presupuestacion/catalogoApi';
import EstadoFinancieroPanel from './EstadoFinancieroPanel';
import {
  TIPOS_GASTO, anularGasto, crearGasto, fecha, listarGastos, pesos,
} from './gastosApi';
import estilos from './Gastos.module.css';

/**
 * Gastos de obra.
 *
 * Reemplaza la doble carga que hoy hace la empresa: anotar el gasto en una
 * planilla del celular durante la semana y pasarlo el sábado a la planilla
 * grande de la computadora. Acá se carga una sola vez y queda comparado contra
 * el presupuesto en el momento.
 *
 * La pantalla arranca por una obra y no por el listado completo: la pregunta
 * real del dueño no es "listame gastos", es "cómo viene esta obra".
 */
export default function GastosPage() {
  const [obras, setObras] = useState([]);
  const [rubros, setRubros] = useState([]);
  const [gastos, setGastos] = useState([]);
  const [cargando, setCargando] = useState(false);
  const [error, setError] = useState(null);

  const [idObra, setIdObra] = useState('');
  const [tipo, setTipo] = useState('');
  const [recarga, setRecarga] = useState(0);

  const [altaAbierta, setAltaAbierta] = useState(false);
  const [aAnular, setAAnular] = useState(null);

  const recargar = useCallback(() => setRecarga((n) => n + 1), []);

  useEffect(() => {
    let vigente = true;
    listarObras().then((d) => {
      if (!vigente) return;
      setObras(d);
      // Se preselecciona la primera obra en ejecución: es la única que admite
      // gastos, así la pantalla arranca mostrando algo útil.
      const enEjecucion = d.find((o) => o.estado === 'En ejecución');
      if (enEjecucion) setIdObra(String(enEjecucion.idObra));
    }).catch(() => {});
    listarRubros({ estado: 'Activo' })
      .then((d) => { if (vigente) setRubros(d); }).catch(() => {});
    return () => { vigente = false; };
  }, []);

  useEffect(() => {
    if (!idObra) return undefined;

    let vigente = true;
    (async () => {
      setCargando(true);
      try {
        const datos = await listarGastos({ obra: idObra, tipo });
        if (vigente) { setGastos(datos); setError(null); }
      } catch (fallo) {
        if (vigente) setError(fallo.mensaje);
      } finally {
        if (vigente) setCargando(false);
      }
    })();
    return () => { vigente = false; };
  }, [idObra, tipo, recarga]);

  // Sin obra elegida no hay nada que listar. Derivado, no vaciado desde el
  // efecto: evita un render extra para limpiar una tabla que no se dibuja.
  const gastosVisibles = idObra ? gastos : [];

  const obraElegida = obras.find((o) => String(o.idObra) === idObra);
  const admiteGastos = obraElegida?.estado === 'En ejecución';

  return (
    <>
      <div className={estilos.cabecera}>
        <div>
          <span className="kicker kicker-acento">Módulo</span>
          <h2 className={estilos.titulo}>Gastos</h2>
          <p className={estilos.bajada}>
            Cada gasto comparado contra lo presupuestado, en el momento en que se carga.
          </p>
        </div>

        <div className={estilos.herramientas}>
          <select className={estilos.filtro} value={idObra}
                  onChange={(e) => setIdObra(e.target.value)} aria-label="Obra">
            <option value="">Elegir obra…</option>
            {obras.map((o) => (
              <option key={o.idObra} value={o.idObra}>
                {o.direccionObra} — {o.estado}
              </option>
            ))}
          </select>
          <select className={estilos.filtro} value={tipo}
                  onChange={(e) => setTipo(e.target.value)} aria-label="Tipo de gasto">
            <option value="">Todo tipo</option>
            {TIPOS_GASTO.map((t) => <option key={t} value={t}>{t}</option>)}
          </select>
          <button type="button" className={estilos.botonPrimario}
                  onClick={() => setAltaAbierta(true)} disabled={!admiteGastos}>
            Nuevo gasto
          </button>
        </div>
      </div>

      {error && <p className={estilos.errorGeneral}>{error}</p>}

      {!idObra && (
        <Blueprint className={estilos.bloque}>
          <div className={estilos.vacio}>
            <p className={estilos.vacioTitulo}>Elegí una obra</p>
            <p className={estilos.vacioTexto}>
              Los gastos se cargan y se comparan por obra: sin obra no hay contra
              qué medirlos.
            </p>
          </div>
        </Blueprint>
      )}

      {idObra && !admiteGastos && (
        <Blueprint className={estilos.bloque}>
          <div className={estilos.vacio}>
            <p className={estilos.vacioTitulo}>Esta obra no admite gastos</p>
            <p className={estilos.vacioTexto}>
              Está {obraElegida?.estado?.toLowerCase()}. Solo se cargan gastos de
              obras en ejecución con presupuesto definitivo aprobado: sin eso no
              hay contra qué comparar.
            </p>
          </div>
        </Blueprint>
      )}

      {idObra && admiteGastos && (
        <>
          <EstadoFinancieroPanel idObra={idObra} recarga={recarga} />

          <Blueprint className={estilos.bloque}>
            <div className={estilos.bloqueCabecera}>
              <h3 className={estilos.bloqueTitulo}>Gastos registrados</h3>
            </div>

            {cargando && <p className={estilos.aviso}>Consultando…</p>}

            {!cargando && gastosVisibles.length === 0 && (
              <div className={estilos.vacio}>
                <p className={estilos.vacioTitulo}>Todavía no hay gastos</p>
                <p className={estilos.vacioTexto}>
                  Cargá el primero, o confirmá la recepción de un pedido y el
                  gasto se genera solo.
                </p>
              </div>
            )}

            {!cargando && gastosVisibles.length > 0 && (
              <div className="scroll-x">
                <table className="table">
                  <thead>
                    <tr>
                      <th>Fecha</th>
                      <th>Rubro</th>
                      <th>Tipo</th>
                      <th>Descripción</th>
                      <th style={{ textAlign: 'right' }}>Monto</th>
                      <th />
                    </tr>
                  </thead>
                  <tbody>
                    {gastosVisibles.map((g) => (
                      <tr key={g.idGasto}
                          className={g.estado === 'Anulado' ? estilos.filaAnulada : undefined}>
                        <td className={estilos.dato}>{fecha(g.fechaGasto)}</td>
                        <td>
                          <div className={estilos.rubroNombre}>{g.nombreRubro}</div>
                          {g.nombreSubrubro && (
                            <div className={estilos.subrubroNombre}>{g.nombreSubrubro}</div>
                          )}
                        </td>
                        <td className={estilos.dato}>
                          {/* El gasto hormiga se marca: es el problema que el
                              relevamiento identifica como más invisible. */}
                          <span className={g.tipoGasto === 'Gasto Hormiga'
                            ? estilos.tipoHormiga : undefined}>
                            {g.tipoGasto}
                          </span>
                        </td>
                        <td className={estilos.dato}>
                          {g.descripcion || '—'}
                          {g.idPedido && (
                            <span className={estilos.origenPedido}>
                              ◆ pedido #{g.idPedido}
                            </span>
                          )}
                          {g.estado === 'Anulado' && (
                            <div className={estilos.subrubroNombre}>
                              Anulado: {g.motivoAnulacion}
                            </div>
                          )}
                        </td>
                        <td className={`cifra ${estilos.total} ${estilos.monto}`}>
                          {pesos(g.monto)}
                        </td>
                        <td className={estilos.acciones}>
                          {g.estado !== 'Anulado' && (
                            <button type="button" className={estilos.accionPeligro}
                                    onClick={() => setAAnular(g)}>
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
        </>
      )}

      {altaAbierta && (
        <NuevoGastoModal
          idObra={idObra}
          rubros={rubros}
          onCerrar={() => setAltaAbierta(false)}
          onCreado={() => { setAltaAbierta(false); recargar(); }}
        />
      )}

      {aAnular && (
        <AnularGastoModal
          gasto={aAnular}
          onCerrar={() => setAAnular(null)}
          onAnulado={() => { setAAnular(null); recargar(); }}
        />
      )}
    </>
  );
}

/* ========================================================================== */

function NuevoGastoModal({ idObra, rubros, onCerrar, onCreado }) {
  const hoy = new Date().toISOString().slice(0, 10);
  const [datos, setDatos] = useState({
    idRubro: '', idSubrubro: '', tipoGasto: 'Material',
    monto: '', fechaGasto: hoy, descripcion: '', comprobanteAdjunto: '',
  });
  const [camposInvalidos, setCamposInvalidos] = useState({});
  const [error, setError] = useState(null);
  const [guardando, setGuardando] = useState(false);

  const rubroElegido = rubros.find((r) => String(r.idRubro) === datos.idRubro);
  const subrubros = rubroElegido
    ? rubroElegido.subrubros.filter((s) => s.estado === 'Activo')
    : [];

  const cambiar = (campo) => (e) => {
    const valor = e.target.value;
    setDatos((previo) => ({
      ...previo,
      [campo]: valor,
      // Al cambiar de rubro, el subrubro anterior deja de pertenecerle.
      ...(campo === 'idRubro' ? { idSubrubro: '' } : {}),
    }));
    setCamposInvalidos((previo) => ({ ...previo, [campo]: undefined }));
  };

  const enviar = async (evento) => {
    evento.preventDefault();
    setGuardando(true);
    setCamposInvalidos({});
    setError(null);
    try {
      await crearGasto({
        idObra: Number(idObra),
        idRubro: Number(datos.idRubro),
        idSubrubro: datos.idSubrubro ? Number(datos.idSubrubro) : null,
        tipoGasto: datos.tipoGasto,
        monto: datos.monto,
        fechaGasto: datos.fechaGasto,
        descripcion: datos.descripcion || null,
        comprobanteAdjunto: datos.comprobanteAdjunto || null,
      });
      onCreado();
    } catch (fallo) {
      if (fallo.camposInvalidos) setCamposInvalidos(fallo.camposInvalidos);
      else setError(fallo.mensaje);
    } finally {
      setGuardando(false);
    }
  };

  return (
    <Modal abierto onCerrar={onCerrar} titulo="Nuevo gasto">
      <form onSubmit={enviar}>
        {error && <p className={estilos.errorGeneral}>{error}</p>}

        <div className={estilos.dosColumnas}>
          <div className={estilos.campo}>
            <label className={estilos.etiqueta} htmlFor="idRubro">
              Rubro <span className={estilos.obligatorio}>*</span>
            </label>
            <select id="idRubro" className={estilos.control} value={datos.idRubro}
                    onChange={cambiar('idRubro')} required autoFocus>
              <option value="">Elegir…</option>
              {rubros.map((r) => (
                <option key={r.idRubro} value={r.idRubro}>{r.nombreRubro}</option>
              ))}
            </select>
            <p className={estilos.ayuda}>
              Es contra el rubro que se compara el gasto con el presupuesto.
            </p>
          </div>

          <div className={estilos.campo}>
            <label className={estilos.etiqueta} htmlFor="idSubrubro">Subrubro</label>
            <select id="idSubrubro" className={estilos.control} value={datos.idSubrubro}
                    onChange={cambiar('idSubrubro')} disabled={!rubroElegido}>
              <option value="">Sin subrubro</option>
              {subrubros.map((s) => (
                <option key={s.idSubrubro} value={s.idSubrubro}>{s.nombreSubrubro}</option>
              ))}
            </select>
          </div>
        </div>

        <div className={estilos.tresColumnas}>
          <div className={estilos.campo}>
            <label className={estilos.etiqueta} htmlFor="tipoGasto">
              Tipo <span className={estilos.obligatorio}>*</span>
            </label>
            <select id="tipoGasto" className={estilos.control} value={datos.tipoGasto}
                    onChange={cambiar('tipoGasto')} required>
              {TIPOS_GASTO.map((t) => <option key={t} value={t}>{t}</option>)}
            </select>
          </div>

          <div className={estilos.campo}>
            <label className={estilos.etiqueta} htmlFor="monto">
              Monto <span className={estilos.obligatorio}>*</span>
            </label>
            <input id="monto" type="number" step="0.01" min="0.01"
                   className={estilos.control} value={datos.monto}
                   onChange={cambiar('monto')} required />
            {camposInvalidos.monto && (
              <p className={estilos.errorCampo}>{camposInvalidos.monto}</p>
            )}
          </div>

          <div className={estilos.campo}>
            <label className={estilos.etiqueta} htmlFor="fechaGasto">
              Fecha del gasto <span className={estilos.obligatorio}>*</span>
            </label>
            <input id="fechaGasto" type="date" className={estilos.control}
                   value={datos.fechaGasto} onChange={cambiar('fechaGasto')} required />
            <p className={estilos.ayuda}>Cuándo ocurrió, no cuándo lo cargás.</p>
          </div>
        </div>

        <div className={estilos.campo}>
          <label className={estilos.etiqueta} htmlFor="descripcion">Descripción</label>
          <input id="descripcion" className={estilos.control} maxLength={250}
                 placeholder="Compra de cemento en el corralón"
                 value={datos.descripcion} onChange={cambiar('descripcion')} />
        </div>

        <div className={estilos.campo}>
          <label className={estilos.etiqueta} htmlFor="comprobanteAdjunto">
            Comprobante
          </label>
          <input id="comprobanteAdjunto" className={estilos.control} maxLength={255}
                 placeholder="comprobantes/2026-09-03-ticket.jpg"
                 value={datos.comprobanteAdjunto} onChange={cambiar('comprobanteAdjunto')} />
          <p className={estilos.ayuda}>
            Opcional. La imagen se guarda en Supabase Storage y la base conserva
            solo la referencia.
          </p>
        </div>

        <div className={estilos.accionesFormulario}>
          <button type="button" className={estilos.botonSecundario} onClick={onCerrar}>
            Cancelar
          </button>
          <button type="submit" className={estilos.botonPrimario} disabled={guardando}>
            {guardando ? 'Guardando…' : 'Registrar gasto'}
          </button>
        </div>
      </form>
    </Modal>
  );
}

/* ========================================================================== */

function AnularGastoModal({ gasto, onCerrar, onAnulado }) {
  const [motivo, setMotivo] = useState('');
  const [error, setError] = useState(null);
  const [guardando, setGuardando] = useState(false);

  const enviar = async (evento) => {
    evento.preventDefault();
    setGuardando(true);
    setError(null);
    try {
      await anularGasto(gasto.idGasto, motivo);
      onAnulado();
    } catch (fallo) {
      setError(fallo.mensaje);
    } finally {
      setGuardando(false);
    }
  };

  return (
    <Modal abierto onCerrar={onCerrar} titulo="Anular gasto">
      <form onSubmit={enviar}>
        <p className={estilos.confirmacion}>
          Se va a anular el gasto de <b>{pesos(gasto.monto)}</b> en {gasto.nombreRubro}.
        </p>
        <p className={estilos.ayuda}>
          El gasto no se elimina: deja de sumar en la comparación contra el
          presupuesto, pero sigue registrado con su motivo. Es lo que conserva la
          trazabilidad completa de la obra.
        </p>

        {error && <p className={estilos.errorGeneral}>{error}</p>}

        <div className={estilos.campo}>
          <label className={estilos.etiqueta} htmlFor="motivoGasto">
            Motivo <span className={estilos.obligatorio}>*</span>
          </label>
          <input id="motivoGasto" className={estilos.control} maxLength={200}
                 placeholder="Se cargó por duplicado" value={motivo}
                 onChange={(e) => setMotivo(e.target.value)} required autoFocus />
        </div>

        <div className={estilos.accionesFormulario}>
          <button type="button" className={estilos.botonSecundario} onClick={onCerrar}>
            Cancelar
          </button>
          <button type="submit" className={estilos.botonPeligro} disabled={guardando}>
            {guardando ? 'Anulando…' : 'Anular gasto'}
          </button>
        </div>
      </form>
    </Modal>
  );
}
