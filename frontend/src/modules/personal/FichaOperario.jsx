import { useEffect, useState } from 'react';
import Modal from '../../components/ui/Modal';
import {
  asignarAObra, desasignarDeObra, fecha, listarInasistencias,
  obtenerOperario, registrarInasistencia, registrarMotivo,
} from './personalApi';
import estilos from './Personal.module.css';

/**
 * Ficha del operario: sus obras y su historial de faltas.
 *
 * El historial de obras incluye las cerradas, no solo las vigentes. Es el
 * requisito del informe: al dar de baja a alguien hay que conservar el registro
 * de dónde trabajó, y una falta se puede cargar en una obra donde ya no está.
 */
export default function FichaOperario({ operario, obras, onCerrar, onCambio }) {
  const [detalle, setDetalle] = useState(null);
  const [faltas, setFaltas] = useState([]);
  const [error, setError] = useState(null);
  const [recarga, setRecarga] = useState(0);

  useEffect(() => {
    let vigente = true;
    (async () => {
      try {
        const [d, f] = await Promise.all([
          obtenerOperario(operario.idOperario),
          listarInasistencias({ operario: operario.idOperario }),
        ]);
        if (vigente) { setDetalle(d); setFaltas(f); setError(null); }
      } catch (fallo) {
        if (vigente) setError(fallo.mensaje);
      }
    })();
    return () => { vigente = false; };
  }, [operario.idOperario, recarga]);

  const recargar = () => { setRecarga((n) => n + 1); onCambio(); };

  const asignaciones = detalle?.asignaciones ?? [];
  const vigentes = asignaciones.filter((a) => a.vigente);

  // Solo se puede registrar una falta en obras donde el operario está o estuvo:
  // el backend valida lo mismo, acá se evita ofrecer lo que va a fallar.
  const obrasPosibles = asignaciones;

  return (
    <Modal abierto onCerrar={onCerrar} titulo={operario.nombreApellido}>
      <p className={estilos.fichaEncabezado ?? estilos.ayuda}>
        {detalle?.telefonoContacto || 'Sin teléfono'} ·{' '}
        {detalle?.estado} · {faltas.length} falta{faltas.length === 1 ? '' : 's'}
      </p>

      {error && <p className={estilos.errorGeneral}>{error}</p>}
      {!detalle && <p className={estilos.aviso}>Cargando…</p>}

      {detalle && (
        <>
          <div className={estilos.campo}>
            <label className={estilos.etiqueta}>Obras</label>
            {asignaciones.length === 0 && (
              <p className={estilos.ayuda}>Todavía no está asignado a ninguna obra.</p>
            )}
            <div>
              {asignaciones.map((a) => (
                <span key={a.idObra}
                      className={a.vigente ? estilos.obraChip : estilos.obraChipCerrada}
                      title={a.vigente
                        ? `Desde ${fecha(a.fechaAsignacion)}`
                        : `De ${fecha(a.fechaAsignacion)} a ${fecha(a.fechaDesasignacion)}`}>
                  {a.direccionObra}
                  {a.vigente && (
                    <button type="button" className={estilos.quitarLinea ?? estilos.accion}
                            style={{ marginLeft: 6 }}
                            onClick={async () => {
                              try {
                                await desasignarDeObra(operario.idOperario, a.idObra);
                                recargar();
                              } catch (f) { setError(f.mensaje); }
                            }}
                            aria-label={`Sacar de ${a.direccionObra}`}>
                      ×
                    </button>
                  )}
                </span>
              ))}
            </div>
            <p className={estilos.ayuda}>
              Las obras en gris son anteriores: se conservan en el historial aunque
              el operario ya no trabaje ahí.
            </p>
          </div>

          {detalle.estado === 'Activo' && (
            <AsignarAObra
              operario={operario}
              obras={obras}
              yaAsignadas={vigentes.map((a) => a.idObra)}
              onAsignado={recargar}
              onError={setError}
            />
          )}

          <RegistrarFalta
            operario={operario}
            obras={obrasPosibles}
            onRegistrada={recargar}
            onError={setError}
          />

          <div className={estilos.campo}>
            <label className={estilos.etiqueta}>Historial de faltas</label>
            {faltas.length === 0 ? (
              <p className={estilos.ayuda}>Sin faltas registradas.</p>
            ) : (
              <div className="scroll-x">
                <table className="table">
                  <thead>
                    <tr><th>Fecha</th><th>Obra</th><th>Motivo</th><th /></tr>
                  </thead>
                  <tbody>
                    {faltas.map((f) => (
                      <tr key={f.idInasistencia}>
                        <td className={estilos.dato}>{fecha(f.fechaFalta)}</td>
                        <td className={estilos.dato}>{f.direccionObra}</td>
                        <td className={f.motivo ? estilos.dato : estilos.sinMotivo}>
                          {f.motivo || 'Sin motivo informado'}
                        </td>
                        <td className={estilos.acciones}>
                          {!f.motivo && (
                            <CompletarMotivo
                              inasistencia={f}
                              onCompletado={recargar}
                              onError={setError}
                            />
                          )}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        </>
      )}

      <div className={estilos.accionesFormulario}>
        <button type="button" className={estilos.botonSecundario} onClick={onCerrar}>
          Cerrar
        </button>
      </div>
    </Modal>
  );
}

/* ========================================================================== */

function AsignarAObra({ operario, obras, yaAsignadas, onAsignado, onError }) {
  const [idObra, setIdObra] = useState('');
  const [guardando, setGuardando] = useState(false);

  const disponibles = obras.filter(
    (o) => !yaAsignadas.includes(o.idObra)
      && o.estado !== 'Cancelada' && o.estado !== 'Finalizada');

  const enviar = async (evento) => {
    evento.preventDefault();
    setGuardando(true);
    try {
      await asignarAObra(operario.idOperario, Number(idObra));
      setIdObra('');
      onAsignado();
    } catch (fallo) {
      onError(fallo.mensaje);
    } finally {
      setGuardando(false);
    }
  };

  if (disponibles.length === 0) return null;

  return (
    <form onSubmit={enviar} className={estilos.formularioEnLinea ?? estilos.campo}>
      <select className={estilos.control} value={idObra}
              onChange={(e) => setIdObra(e.target.value)} required aria-label="Obra a asignar">
        <option value="">Asignar a una obra…</option>
        {disponibles.map((o) => (
          <option key={o.idObra} value={o.idObra}>{o.direccionObra}</option>
        ))}
      </select>
      <button type="submit" className={estilos.botonSecundario} disabled={guardando}>
        {guardando ? '…' : 'Asignar'}
      </button>
    </form>
  );
}

/* ========================================================================== */

function RegistrarFalta({ operario, obras, onRegistrada, onError }) {
  const hoy = new Date().toISOString().slice(0, 10);
  const [idObra, setIdObra] = useState('');
  const [fechaFalta, setFechaFalta] = useState(hoy);
  const [motivo, setMotivo] = useState('');
  const [guardando, setGuardando] = useState(false);

  const enviar = async (evento) => {
    evento.preventDefault();
    setGuardando(true);
    try {
      await registrarInasistencia({
        idOperario: operario.idOperario,
        idObra: Number(idObra),
        fechaFalta,
        motivo: motivo.trim() || null,
      });
      setMotivo('');
      onRegistrada();
    } catch (fallo) {
      onError(fallo.mensaje);
    } finally {
      setGuardando(false);
    }
  };

  if (obras.length === 0) return null;

  return (
    <div className={estilos.campo}>
      <label className={estilos.etiqueta}>Registrar una falta</label>
      <form onSubmit={enviar} className={estilos.formularioEnLinea ?? estilos.campo}>
        <select className={estilos.control} value={idObra}
                onChange={(e) => setIdObra(e.target.value)} required aria-label="Obra">
          <option value="">Obra…</option>
          {obras.map((o) => (
            <option key={o.idObra} value={o.idObra}>{o.direccionObra}</option>
          ))}
        </select>
        <input type="date" className={estilos.control} value={fechaFalta}
               onChange={(e) => setFechaFalta(e.target.value)} required
               aria-label="Fecha de la falta" />
        <input className={estilos.control} maxLength={200} placeholder="Motivo (opcional)"
               value={motivo} onChange={(e) => setMotivo(e.target.value)}
               aria-label="Motivo" />
        <button type="submit" className={estilos.botonPrimario} disabled={guardando}>
          {guardando ? '…' : 'Registrar'}
        </button>
      </form>
      <p className={estilos.ayuda}>
        El motivo no es obligatorio: muchas faltas no tienen justificación conocida
        en el momento, y se puede completar después.
      </p>
    </div>
  );
}

/* ========================================================================== */

function CompletarMotivo({ inasistencia, onCompletado, onError }) {
  const [editando, setEditando] = useState(false);
  const [motivo, setMotivo] = useState('');

  if (!editando) {
    return (
      <button type="button" className={estilos.accion} onClick={() => setEditando(true)}>
        Agregar motivo
      </button>
    );
  }

  return (
    <form onSubmit={async (e) => {
      e.preventDefault();
      try {
        await registrarMotivo(inasistencia.idInasistencia, motivo);
        onCompletado();
      } catch (fallo) { onError(fallo.mensaje); }
    }}>
      <input className={estilos.control} maxLength={200} value={motivo} autoFocus
             onChange={(e) => setMotivo(e.target.value)} required
             placeholder="Avisó que estaba enfermo" aria-label="Motivo" />
    </form>
  );
}
