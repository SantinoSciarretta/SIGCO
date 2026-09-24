import { useCallback, useEffect, useState } from 'react';
import Blueprint from '../../components/ui/Blueprint';
import Modal from '../../components/ui/Modal';
import { listarObras } from '../obras/obrasApi';
import ConfigurarEtapas from './ConfigurarEtapas';
import ConfigurarHitos from './ConfigurarHitos';
import {
  avanceDeObra, completarHito, fecha, reabrirHito, textoDePlazo,
} from './seguimientoApi';
import estilos from './Seguimiento.module.css';

/**
 * Panel de avance de obra.
 *
 * Reemplaza el cronograma que hoy se arma al inicio y no se actualiza, y el
 * avance que se evalúa de memoria durante las visitas del dueño.
 *
 * Lo central de la pantalla son las DOS barras: avance físico contra avance
 * financiero. Esa comparación es la que detecta obras que consumieron
 * presupuesto sin avanzar en la misma medida, y es información que hoy no
 * existe en ninguna parte.
 */
export default function SeguimientoPage() {
  const [obras, setObras] = useState([]);
  const [idObra, setIdObra] = useState('');
  const [avance, setAvance] = useState(null);
  const [error, setError] = useState(null);
  const [recarga, setRecarga] = useState(0);

  const [configAbierta, setConfigAbierta] = useState(false);
  const [etapasAbiertas, setEtapasAbiertas] = useState(false);
  const [aCompletar, setACompletar] = useState(null);

  const recargar = useCallback(() => setRecarga((n) => n + 1), []);

  useEffect(() => {
    let vigente = true;
    listarObras().then((d) => {
      if (!vigente) return;
      setObras(d);
      const enEjecucion = d.find((o) => o.estado === 'En ejecución');
      if (enEjecucion) setIdObra(String(enEjecucion.idObra));
    }).catch(() => {});
    return () => { vigente = false; };
  }, []);

  useEffect(() => {
    if (!idObra) return undefined;

    let vigente = true;
    (async () => {
      try {
        const datos = await avanceDeObra(idObra);
        if (vigente) { setAvance(datos); setError(null); }
      } catch (fallo) {
        if (vigente) { setAvance(null); setError(fallo.mensaje); }
      }
    })();
    return () => { vigente = false; };
  }, [idObra, recarga]);

  const obraElegida = obras.find((o) => String(o.idObra) === idObra);
  const editable = obraElegida?.estado === 'En ejecución';
  const avanceVisible = idObra ? avance : null;

  return (
    <>
      <div className={estilos.cabecera}>
        <div>
          <span className="kicker kicker-acento">Módulo</span>
          <h2 className={estilos.titulo}>Seguimiento de obras</h2>
          <p className={estilos.bajada}>
            Avance físico por hitos, comparado contra lo que se lleva gastado.
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
          {/* Dos formas de cargar lo mismo. La de etapas es la que pidió
              Ricardo: se carga la duración y el porcentaje lo saca el sistema.
              La de hitos, con el porcentaje escrito a mano, se conserva para
              quien ya tiene los pesos decididos. */}
          <button type="button" className={estilos.botonPrimario}
                  onClick={() => setEtapasAbiertas(true)} disabled={!editable}>
            Cargar etapas
          </button>
          <button type="button" className={estilos.botonSecundario}
                  onClick={() => setConfigAbierta(true)} disabled={!editable}>
            {avanceVisible?.hitosTotales > 0 ? 'Redefinir por %' : 'Definir por %'}
          </button>
        </div>
      </div>

      {error && <p className={estilos.errorGeneral}>{error}</p>}

      {!idObra && (
        <Blueprint className={estilos.bloque}>
          <div className={estilos.vacio}>
            <p className={estilos.vacioTitulo}>Elegí una obra</p>
            <p className={estilos.vacioTexto}>
              El avance se sigue por obra: cada una tiene sus propias etapas.
            </p>
          </div>
        </Blueprint>
      )}

      {avanceVisible && (
        <>
          {/* La alerta va arriba de todo: es la razón por la que el dueño
              entra a esta pantalla. */}
          {avanceVisible.alertaDesfasaje && (
            <p className={estilos.alerta}>
              <b>Se está gastando más rápido de lo que se avanza.</b>{' '}
              La obra lleva {avanceVisible.avanceFisico}% de avance físico y{' '}
              {avanceVisible.avanceFinanciero}% del presupuesto consumido:{' '}
              {avanceVisible.desfasaje} puntos de diferencia.
            </p>
          )}

          <Blueprint className={estilos.bloque}>
            <div className={estilos.comparativa}>
              <Medidor etiqueta="Avance físico" valor={avanceVisible.avanceFisico}
                       detalle={`${avanceVisible.hitosCompletados} de ${avanceVisible.hitosTotales} hitos`} />
              <Medidor etiqueta="Avance financiero" valor={avanceVisible.avanceFinanciero}
                       tipo="financiero" detalle="del presupuesto aprobado" />
            </div>

            <p className={estilos.ayuda}>
              {textoDePlazo(avanceVisible)}
              {avanceVisible.fechaFinEstimada
                && ` · fecha estimada ${fecha(avanceVisible.fechaFinEstimada)}`}
              {avanceVisible.estadoObra === 'Finalizada'
                && ' · la obra está finalizada y sus hitos quedaron bloqueados'}
            </p>
          </Blueprint>

          <Blueprint className={estilos.bloque}>
            <div className={estilos.bloqueCabecera}>
              <h3 className={estilos.bloqueTitulo}>Hitos</h3>
            </div>

            {avanceVisible.hitos.length === 0 ? (
              <div className={estilos.vacio}>
                <p className={estilos.vacioTitulo}>Esta obra todavía no tiene hitos</p>
                <p className={estilos.vacioTexto}>
                  Definí las etapas y su peso. Las ponderaciones tienen que sumar 100%.
                </p>
              </div>
            ) : (
              <div className="scroll-x">
                <table className="table">
                  <thead>
                    <tr>
                      <th style={{ width: 50 }}>#</th>
                      <th>Etapa</th>
                      <th style={{ textAlign: 'right' }}>Peso</th>
                      <th>Estado</th>
                      <th>Cumplido</th>
                      <th />
                    </tr>
                  </thead>
                  <tbody>
                    {avanceVisible.hitos.map((h) => (
                      <tr key={h.idHito}
                          className={h.estado === 'Completado' ? estilos.filaCompletada : undefined}>
                        <td className={`cifra ${estilos.numero}`}>{h.orden}</td>
                        <td className={estilos.rubroNombre}>
                          {h.nombreHito}
                          {h.observacion && (
                            <div className={estilos.subrubroNombre}>{h.observacion}</div>
                          )}
                        </td>
                        <td className={`cifra ${estilos.ponderacion}`}>{h.ponderacion}%</td>
                        <td>
                          <span className={h.estado === 'Completado'
                            ? estilos.hitoCompletado : estilos.hitoPendiente}>
                            {h.estado}
                          </span>
                        </td>
                        <td className={estilos.dato}>{fecha(h.fechaCumplimiento)}</td>
                        <td className={estilos.acciones}>
                          {editable && h.estado === 'Pendiente' && (
                            <button type="button" className={estilos.accion}
                                    onClick={() => setACompletar(h)}>
                              Completar
                            </button>
                          )}
                          {editable && h.estado === 'Completado' && (
                            <button type="button" className={estilos.accion}
                                    onClick={async () => {
                                      try { await reabrirHito(h.idHito); recargar(); }
                                      catch (f) { setError(f.mensaje); }
                                    }}>
                              Reabrir
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

      {etapasAbiertas && (
        <ConfigurarEtapas
          idObra={idObra}
          onCerrar={() => setEtapasAbiertas(false)}
          onGuardado={() => { setEtapasAbiertas(false); recargar(); }}
        />
      )}

      {configAbierta && (
        <ConfigurarHitos
          idObra={idObra}
          hitosActuales={avanceVisible?.hitos ?? []}
          onCerrar={() => setConfigAbierta(false)}
          onGuardado={() => { setConfigAbierta(false); recargar(); }}
        />
      )}

      {aCompletar && (
        <CompletarHitoModal
          hito={aCompletar}
          onCerrar={() => setACompletar(null)}
          onCompletado={() => { setACompletar(null); recargar(); }}
        />
      )}
    </>
  );
}

/* ========================================================================== */

function Medidor({ etiqueta, valor, tipo, detalle }) {
  return (
    <div className={estilos.medidor}>
      <div className={estilos.medidorCabecera}>
        <span>{etiqueta}</span>
        <span className={`cifra ${estilos.medidorValor}`}>{valor}%</span>
      </div>
      <div className={estilos.medidorPista}>
        <div className={estilos.medidorRelleno} data-tipo={tipo}
             style={{ width: `${Math.min(Number(valor), 100)}%` }} />
      </div>
      <span className={estilos.ayuda}>{detalle}</span>
    </div>
  );
}

/* ========================================================================== */

function CompletarHitoModal({ hito, onCerrar, onCompletado }) {
  const hoy = new Date().toISOString().slice(0, 10);
  const [fechaCumplimiento, setFechaCumplimiento] = useState(hoy);
  const [observacion, setObservacion] = useState('');
  const [forzar, setForzar] = useState(false);
  const [error, setError] = useState(null);
  const [guardando, setGuardando] = useState(false);

  const enviar = async (evento) => {
    evento.preventDefault();
    setGuardando(true);
    setError(null);
    try {
      await completarHito(hito.idHito, {
        fechaCumplimiento,
        observacion: observacion.trim() || null,
        forzar,
      });
      onCompletado();
    } catch (fallo) {
      setError(fallo.mensaje);
    } finally {
      setGuardando(false);
    }
  };

  return (
    <Modal abierto onCerrar={onCerrar} titulo={`Completar: ${hito.nombreHito}`}>
      <form onSubmit={enviar}>
        <p className={estilos.confirmacion}>
          Suma <b>{hito.ponderacion}%</b> al avance de la obra.
        </p>

        {error && <p className={estilos.errorGeneral}>{error}</p>}

        <div className={estilos.campo}>
          <label className={estilos.etiqueta} htmlFor="fechaCumplimiento">
            Fecha de cumplimiento <span className={estilos.obligatorio}>*</span>
          </label>
          <input id="fechaCumplimiento" type="date" className={estilos.control}
                 value={fechaCumplimiento} onChange={(e) => setFechaCumplimiento(e.target.value)}
                 required autoFocus />
          <p className={estilos.ayuda}>
            Es obligatoria: sin ella no se puede analizar si la obra viene en plazo.
          </p>
        </div>

        <div className={estilos.campo}>
          <label className={estilos.etiqueta} htmlFor="observacion">Observación</label>
          <input id="observacion" className={estilos.control} maxLength={250}
                 placeholder="Se demoró por lluvia" value={observacion}
                 onChange={(e) => setObservacion(e.target.value)} />
        </div>

        {/* El informe lo prevé: en la práctica algunas tareas se adelantan
            respecto del orden previsto. */}
        <div className={estilos.campo}>
          <label className={estilos.etiqueta}>
            <input type="checkbox" checked={forzar}
                   onChange={(e) => setForzar(e.target.checked)} />
            {' '}Permitir saltear etapas anteriores sin completar
          </label>
          <p className={estilos.ayuda}>
            Marcalo solo si esta etapa se adelantó de verdad.
          </p>
        </div>

        <div className={estilos.accionesFormulario}>
          <button type="button" className={estilos.botonSecundario} onClick={onCerrar}>
            Cancelar
          </button>
          <button type="submit" className={estilos.botonPrimario} disabled={guardando}>
            {guardando ? 'Guardando…' : 'Marcar completado'}
          </button>
        </div>
      </form>
    </Modal>
  );
}
