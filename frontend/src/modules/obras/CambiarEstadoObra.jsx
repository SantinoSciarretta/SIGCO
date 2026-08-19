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

  const enviar = async (evento) => {
    evento.preventDefault();
    setGuardando(true);
    setError(null);

    try {
      onCambiado(await cambiarEstadoObra(obra.idObra, {
        estado,
        motivoCancelacion: estado === 'Cancelada' ? motivo : null,
        fechaInicioReal: estado === 'En ejecución' && fechaInicio ? fechaInicio : null,
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
