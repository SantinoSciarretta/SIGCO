import { useState } from 'react';
import Modal from '../../components/ui/Modal';
import { cambiarEstadoObra, estadosPosiblesDesde } from './obrasApi';
import estilos from './Obras.module.css';

/**
 * Cambio de estado de una obra.
 *
 * Ofrece solo las transiciones que tienen sentido desde el estado actual, para
 * que el usuario no intente algo que el servidor va a rechazar. La regla que
 * manda sigue siendo la del backend.
 *
 * Los dos destinos piden un dato distinto:
 *   - "En ejecución" habilita cargar la fecha en que arrancaron los trabajos.
 *   - "Cancelada" exige el motivo, sin excepción.
 */
export default function CambiarEstadoObra({ obra, onCerrar, onCambiado }) {
  const posibles = estadosPosiblesDesde(obra.estado);

  const [estado, setEstado] = useState(posibles[0] ?? '');
  const [motivo, setMotivo] = useState('');
  const [fechaInicio, setFechaInicio] = useState('');
  const [error, setError] = useState(null);
  const [guardando, setGuardando] = useState(false);
  const [confirmaEnMarcha, setConfirmaEnMarcha] = useState(false);
  const [confirmaHitos, setConfirmaHitos] = useState(false);

  /**
   * Si cancelar esta obra interrumpe una obra en marcha.
   *
   * Se deduce del estado: una obra llega a "En ejecución" justamente cuando se
   * aprueba su presupuesto definitivo. Quien decide de verdad es el backend,
   * que consulta los presupuestos; acá solo se anticipa para pedir la
   * confirmación antes de mandar algo que va a ser rechazado.
   */
  const obraEnMarcha = obra.estado === 'En ejecución';

  const enviar = async (evento) => {
    evento.preventDefault();
    setGuardando(true);
    setError(null);

    try {
      onCambiado(await cambiarEstadoObra(obra.idObra, {
        estado,
        motivoCancelacion: estado === 'Cancelada' ? motivo : null,
        fechaInicioReal: estado === 'En ejecución' && fechaInicio ? fechaInicio : null,
        confirmaObraEnEjecucion: estado === 'Cancelada' && confirmaEnMarcha,
        confirmaHitosPendientes: estado === 'Finalizada' && confirmaHitos,
      }));
    } catch (fallo) {
      // Un 409 llega acá con el texto de la regla que se violó, tal como la
      // escribió el backend.
      setError(fallo.camposInvalidos?.motivoCancelacion ?? fallo.mensaje);
    } finally {
      setGuardando(false);
    }
  };

  return (
    <Modal abierto onCerrar={onCerrar} titulo="Cambiar estado de la obra">
      <form onSubmit={enviar}>
        <p className={estilos.contexto}>
          <strong>{obra.direccionObra}</strong><br />
          Estado actual: {obra.estado}
        </p>

        {error && <p className={estilos.errorGeneral}>{error}</p>}

        <div className={estilos.campo}>
          <label className={estilos.etiqueta} htmlFor="nuevoEstado">Nuevo estado</label>
          <select
            id="nuevoEstado"
            className={estilos.control}
            value={estado}
            onChange={(e) => setEstado(e.target.value)}
          >
            {posibles.map((e) => <option key={e} value={e}>{e}</option>)}
          </select>
        </div>

        {estado === 'En ejecución' && (
          <div className={estilos.campo}>
            <label className={estilos.etiqueta} htmlFor="fechaInicioReal">
              Fecha de inicio de los trabajos
            </label>
            <input
              id="fechaInicioReal"
              type="date"
              className={estilos.control}
              value={fechaInicio}
              onChange={(e) => setFechaInicio(e.target.value)}
            />
            <p className={estilos.ayuda}>
              Se habilita ahora porque la obra deja la etapa de presupuestación.
            </p>
          </div>
        )}

        {estado === 'Cancelada' && (
          <div className={estilos.campo}>
            <label className={estilos.etiqueta} htmlFor="motivo">
              Motivo de la cancelación <span className={estilos.obligatorio}>*</span>
            </label>
            <textarea
              id="motivo"
              className={estilos.area}
              value={motivo}
              onChange={(e) => setMotivo(e.target.value)}
              maxLength={200}
              placeholder="Precio, plazos, el cliente cambió de planes…"
            />
            <p className={estilos.ayuda}>
              Sin el motivo no se puede cancelar: es lo que permite entender más
              adelante por qué no se concretó el proyecto.
            </p>
          </div>
        )}

        {/* La obra ya arrancó: cancelarla no es descartar una propuesta, es
            interrumpir trabajos con material comprado y cuotas emitidas. El
            informe pide autorización explícita del dueño, y esto es esa
            autorización. El backend la vuelve a exigir. */}
        {estado === 'Cancelada' && obraEnMarcha && (
          <div className={estilos.campo}>
            <label className={estilos.etiqueta} htmlFor="confirmaEnMarcha"
                   style={{ display: 'flex', gap: 8, alignItems: 'flex-start' }}>
              <input
                id="confirmaEnMarcha"
                type="checkbox"
                checked={confirmaEnMarcha}
                onChange={(e) => setConfirmaEnMarcha(e.target.checked)}
                style={{ marginTop: 3 }}
              />
              <span>
                Esta obra tiene el presupuesto definitivo aprobado y está en
                ejecución. Confirmo que quiero interrumpirla.
              </span>
            </label>
          </div>
        )}

        {/* Dar la obra por terminada bloquea sus hitos: ya no se puede cargar
            más avance. Si quedan hitos sin completar el backend lo rechaza sin
            esta confirmación, y el mensaje dice cuántos son. Se ofrece siempre
            porque desde acá no se sabe si quedan: el que sabe es el servidor. */}
        {estado === 'Finalizada' && (
          <div className={estilos.campo}>
            <label className={estilos.etiqueta} htmlFor="confirmaHitos"
                   style={{ display: 'flex', gap: 8, alignItems: 'flex-start' }}>
              <input
                id="confirmaHitos"
                type="checkbox"
                checked={confirmaHitos}
                onChange={(e) => setConfirmaHitos(e.target.checked)}
                style={{ marginTop: 3 }}
              />
              <span>
                La obra terminó aunque queden hitos sin marcar. Al cerrarla, sus
                hitos se bloquean.
              </span>
            </label>
            <p className={estilos.ayuda}>
              Si están todos completos no hace falta tildarlo. Cerrar la obra no
              cancela lo que falte cobrar: el plan de cobro sigue vigente.
            </p>
          </div>
        )}

        <div className={estilos.accionesFormulario}>
          <button type="button" className={estilos.botonSecundario} onClick={onCerrar}>
            Cancelar
          </button>
          <button type="submit" className={estilos.botonPrimario} disabled={guardando || !estado}>
            {guardando ? 'Guardando…' : 'Confirmar cambio'}
          </button>
        </div>
      </form>
    </Modal>
  );
}
