import { useEffect, useState } from 'react';
import Modal from '../../components/ui/Modal';
import { listarRubros } from '../presupuestacion/catalogoApi';
import { configurarEtapas } from './seguimientoApi';
import estilos from './Seguimiento.module.css';

/**
 * Carga de las etapas de la obra por duración.
 *
 * ------------------------------------------------------------------
 *  Qué problema resuelve
 * ------------------------------------------------------------------
 *
 * Ricardo lo pidió así: "que pueda cargar un ítem de algo que se tenga que
 * hacer, por ejemplo demolición de una pared, que ponga el rubro al que
 * pertenece, cuántas semanas o días cree que va a tardar, y que eso calcule el
 * porcentaje que representaría en avance".
 *
 * La otra pantalla —Definir hitos— pide escribir el porcentaje de cada etapa y
 * cuidar que sumen 100. Eso obliga a hacer una cuenta que nadie tiene ganas de
 * hacer, y es de donde salen los planes que suman 97 o 103. Acá se carga lo que
 * uno sabe —cuánto lleva cada cosa— y el porcentaje lo saca el sistema.
 *
 * ------------------------------------------------------------------
 *  Días o semanas
 * ------------------------------------------------------------------
 *
 * Se carga en la unidad que a uno le salga y se convierte a días acá, porque el
 * backend guarda días: mezclar unidades en la base obligaría a convertir en cada
 * consulta, y tarde o temprano alguien sumaría semanas con días.
 *
 * El porcentaje que se muestra al lado de cada etapa es una PREVIA: el reparto
 * que manda lo hace el servidor, que además se ocupa de que cierre exactamente
 * en 100 cuando la división no da redonda.
 */
export default function ConfigurarEtapas({ idObra, onCerrar, onGuardado }) {
  const [lineas, setLineas] = useState([
    { nombreHito: '', idRubro: '', cantidad: '', unidad: 'días', orden: 1 },
  ]);
  const [rubros, setRubros] = useState([]);
  const [error, setError] = useState(null);
  const [guardando, setGuardando] = useState(false);

  useEffect(() => {
    let vigente = true;
    // Si el catálogo falla, el formulario sigue sirviendo: el rubro es opcional.
    listarRubros({ estado: 'Activo' })
      .then((d) => { if (vigente) setRubros(d); })
      .catch(() => {});
    return () => { vigente = false; };
  }, []);

  const enDias = (linea) => {
    const n = Number(linea.cantidad);
    if (!(n > 0)) return 0;
    return linea.unidad === 'semanas' ? n * 7 : n;
  };

  const totalDias = lineas.reduce((s, l) => s + enDias(l), 0);

  const cambiar = (indice, campo) => (e) => {
    const valor = e.target.value;
    setLineas((previas) => previas.map((l, i) => (i === indice ? { ...l, [campo]: valor } : l)));
  };

  const agregar = () => setLineas((p) => [
    ...p, { nombreHito: '', idRubro: '', cantidad: '', unidad: 'días', orden: p.length + 1 }]);

  // Al quitar una línea se renumeran las que quedan: el orden define la
  // secuencia de la obra y no puede tener huecos ni repetidos.
  const quitar = (i) => setLineas((p) => p
    .filter((_, j) => j !== i)
    .map((l, j) => ({ ...l, orden: j + 1 })));

  const enviar = async (evento) => {
    evento.preventDefault();
    setGuardando(true);
    setError(null);

    try {
      await configurarEtapas(idObra, lineas.map((l) => ({
        nombreHito: l.nombreHito,
        idRubro: l.idRubro ? Number(l.idRubro) : null,
        duracionDias: enDias(l),
        orden: Number(l.orden),
      })));
      onGuardado();
    } catch (fallo) {
      setError(fallo.mensaje);
    } finally {
      setGuardando(false);
    }
  };

  const completas = lineas.every((l) => l.nombreHito.trim() && enDias(l) > 0);

  return (
    <Modal abierto onCerrar={onCerrar} titulo="Cargar etapas de la obra" ancho="ancho">
      <form onSubmit={enviar}>
        {error && <p className={estilos.errorGeneral}>{error}</p>}

        <p className={estilos.ayuda}>
          Cargá qué hay que hacer, de qué rubro es y cuánto creés que lleva. El
          porcentaje de avance que representa cada etapa lo calcula el sistema a
          partir de su duración.
        </p>

        <div className={estilos.campo}>
          <div className={estilos.encabezadoEtapas}>
            <span>N°</span>
            <span>Qué hay que hacer</span>
            <span>Rubro</span>
            {/* Un solo rótulo para los dos campos: la cantidad y su unidad son
                un dato, no dos. Partirlo daba un "Dura" que parecía cortado. */}
            <span className={estilos.rotuloDuracion}>Cuánto lleva</span>
            <span>Pesa</span>
            <span />
          </div>

          {lineas.map((linea, i) => {
            const dias = enDias(linea);
            const porcentaje = totalDias > 0 ? (dias * 100) / totalDias : 0;

            return (
              // eslint-disable-next-line react/no-array-index-key
              <div key={i} className={estilos.lineaEtapa}>
                <input type="number" min="1" className={estilos.control}
                       value={linea.orden} onChange={cambiar(i, 'orden')} required
                       aria-label={`Orden ${i + 1}`} />

                <input className={estilos.control} maxLength={150}
                       placeholder="Demolición de una pared"
                       value={linea.nombreHito} onChange={cambiar(i, 'nombreHito')} required
                       aria-label={`Etapa ${i + 1}`} />

                <select className={estilos.control} value={linea.idRubro}
                        onChange={cambiar(i, 'idRubro')}
                        aria-label={`Rubro de la etapa ${i + 1}`}>
                  <option value="">Sin rubro</option>
                  {rubros.map((r) => (
                    <option key={r.idRubro} value={r.idRubro}>{r.nombreRubro}</option>
                  ))}
                </select>

                <input type="number" min="1" className={estilos.control} placeholder="5"
                       value={linea.cantidad} onChange={cambiar(i, 'cantidad')} required
                       aria-label={`Duración de la etapa ${i + 1}`} />

                <select className={estilos.control} value={linea.unidad}
                        onChange={cambiar(i, 'unidad')}
                        aria-label={`Unidad de la etapa ${i + 1}`}>
                  <option value="días">días</option>
                  <option value="semanas">semanas</option>
                </select>

                {/* La previa del reparto. Se recalcula con cada tecla, así se
                    ve cómo una etapa larga le saca peso a las demás. */}
                <span className={`cifra ${estilos.pesoEtapa}`}>
                  {dias > 0 ? `${porcentaje.toFixed(1)}%` : '—'}
                </span>

                <button type="button" className={estilos.quitarLinea} onClick={() => quitar(i)}
                        disabled={lineas.length === 1} aria-label="Quitar etapa">
                  ×
                </button>
              </div>
            );
          })}

          <button type="button" className={estilos.botonSecundario} onClick={agregar}>
            Agregar etapa
          </button>
        </div>

        <div className={estilos.totalBloque}>
          <span className={estilos.etiqueta}>Duración total</span>
          <span className={`cifra ${estilos.sumaOk}`}>
            {totalDias} {totalDias === 1 ? 'día' : 'días'}
            {totalDias >= 7 && ` · ${(totalDias / 7).toFixed(1)} semanas`}
          </span>
        </div>

        <p className={estilos.ayuda}>
          Los porcentajes salen de repartir el 100% entre las etapas según su
          duración, así que siempre cierran. Una etapa de tres semanas pesa más
          que una de dos días sin que tengas que calcularlo.
        </p>

        <div className={estilos.accionesFormulario}>
          <button type="button" className={estilos.botonSecundario} onClick={onCerrar}>
            Cancelar
          </button>
          <button type="submit" className={estilos.botonPrimario}
                  disabled={guardando || !completas}>
            {guardando ? 'Guardando…' : 'Guardar las etapas'}
          </button>
        </div>
      </form>
    </Modal>
  );
}
