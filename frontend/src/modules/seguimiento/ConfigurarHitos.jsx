import { useEffect, useState } from 'react';
import Modal from '../../components/ui/Modal';
import { aplicarPlantilla, configurarHitos, listarPlantillas } from './seguimientoApi';
import estilos from './Seguimiento.module.css';

/**
 * Configuración de los hitos de una obra.
 *
 * Se define el conjunto COMPLETO de una vez y no hito por hito, porque la regla
 * de las ponderaciones aplica al conjunto: la suma tiene que dar exactamente
 * 100%. Cargándolos de a uno, la obra quedaría en un estado inválido entre
 * altas y el avance calculado en el medio no significaría nada.
 *
 * La suma acumulada se muestra en todo momento, como pide el informe: el error
 * se ve mientras se escribe, no al intentar guardar.
 */
export default function ConfigurarHitos({ idObra, hitosActuales, onCerrar, onGuardado }) {
  const [lineas, setLineas] = useState(
    hitosActuales.length > 0
      ? hitosActuales.map((h) => ({
        nombreHito: h.nombreHito, ponderacion: String(h.ponderacion), orden: h.orden,
      }))
      : [{ nombreHito: '', ponderacion: '', orden: 1 }]);
  const [plantillas, setPlantillas] = useState([]);
  const [error, setError] = useState(null);
  const [guardando, setGuardando] = useState(false);

  useEffect(() => {
    let vigente = true;
    listarPlantillas().then((d) => { if (vigente) setPlantillas(d); }).catch(() => {});
    return () => { vigente = false; };
  }, []);

  const suma = lineas.reduce((s, l) => s + (Number(l.ponderacion) || 0), 0);
  const cierra = Math.abs(suma - 100) < 0.001;

  const cambiar = (indice, campo) => (e) => {
    const valor = e.target.value;
    setLineas((previas) => previas.map((l, i) => (i === indice ? { ...l, [campo]: valor } : l)));
  };

  const agregar = () => setLineas((p) => [
    ...p, { nombreHito: '', ponderacion: '', orden: p.length + 1 }]);

  // Al quitar una línea se renumeran las que quedan: el orden define la
  // secuencia de la obra y no puede tener huecos ni repetidos.
  const quitar = (i) => setLineas((p) => p
    .filter((_, j) => j !== i)
    .map((l, j) => ({ ...l, orden: j + 1 })));

  const usarPlantilla = async (idPlantilla) => {
    if (!idPlantilla) return;
    setError(null);
    try {
      const hitos = await aplicarPlantilla(idObra, Number(idPlantilla));
      onGuardado(hitos);
    } catch (fallo) {
      setError(fallo.mensaje);
    }
  };

  const enviar = async (evento) => {
    evento.preventDefault();
    setGuardando(true);
    setError(null);
    try {
      await configurarHitos(idObra, lineas.map((l) => ({
        nombreHito: l.nombreHito,
        ponderacion: l.ponderacion,
        orden: Number(l.orden),
      })));
      onGuardado();
    } catch (fallo) {
      setError(fallo.mensaje);
    } finally {
      setGuardando(false);
    }
  };

  return (
    <Modal abierto onCerrar={onCerrar} titulo="Definir hitos de la obra">
      <form onSubmit={enviar}>
        {error && <p className={estilos.errorGeneral}>{error}</p>}

        {plantillas.length > 0 && (
          <div className={estilos.campo}>
            <label className={estilos.etiqueta} htmlFor="plantilla">
              Partir de una plantilla
            </label>
            <select id="plantilla" className={estilos.control} defaultValue=""
                    onChange={(e) => usarPlantilla(e.target.value)}>
              <option value="">Armar desde cero…</option>
              {plantillas.map((p) => (
                <option key={p.idPlantilla} value={p.idPlantilla}>
                  {p.nombrePlantilla} — {p.etapas.length} etapas
                </option>
              ))}
            </select>
            <p className={estilos.ayuda}>
              Aplica las etapas de la plantilla directamente a esta obra.
            </p>
          </div>
        )}

        <div className={estilos.campo}>
          <label className={estilos.etiqueta}>
            Etapas <span className={estilos.obligatorio}>*</span>
          </label>

          {lineas.map((linea, i) => (
            // eslint-disable-next-line react/no-array-index-key
            <div key={i} className={estilos.lineaHito}>
              <input type="number" min="1" className={estilos.control}
                     value={linea.orden} onChange={cambiar(i, 'orden')} required
                     aria-label={`Orden ${i + 1}`} />
              <input className={estilos.control} maxLength={150} placeholder="Demolición"
                     value={linea.nombreHito} onChange={cambiar(i, 'nombreHito')} required
                     aria-label={`Etapa ${i + 1}`} />
              <input type="number" step="0.01" min="0.01" max="100" className={estilos.control}
                     placeholder="% del total" value={linea.ponderacion}
                     onChange={cambiar(i, 'ponderacion')} required
                     aria-label={`Ponderación ${i + 1}`} />
              <button type="button" className={estilos.quitarLinea} onClick={() => quitar(i)}
                      disabled={lineas.length === 1} aria-label="Quitar etapa">
                ×
              </button>
            </div>
          ))}

          <button type="button" className={estilos.botonSecundario} onClick={agregar}>
            Agregar etapa
          </button>
        </div>

        {/* La suma acumulada, visible mientras se escribe. Es lo que el informe
            pide para asegurar que llegue al 100% antes de intentar guardar. */}
        <div className={estilos.totalBloque}>
          <span className={estilos.etiqueta}>Suma de ponderaciones</span>
          <span className={`cifra ${cierra ? estilos.sumaOk : estilos.sumaMal}`}>
            {suma.toLocaleString('es-AR', { maximumFractionDigits: 2 })}%
            {!cierra && ` (faltan ${(100 - suma).toLocaleString('es-AR', { maximumFractionDigits: 2 })})`}
          </span>
        </div>

        <p className={estilos.ayuda}>
          Tiene que dar exactamente 100%. Con menos, el avance nunca llegaría a
          completo; con más, una obra a medias podría mostrar 100%.
        </p>

        <div className={estilos.accionesFormulario}>
          <button type="button" className={estilos.botonSecundario} onClick={onCerrar}>
            Cancelar
          </button>
          <button type="submit" className={estilos.botonPrimario}
                  disabled={guardando || !cierra}>
            {guardando ? 'Guardando…' : 'Guardar hitos'}
          </button>
        </div>
      </form>
    </Modal>
  );
}
