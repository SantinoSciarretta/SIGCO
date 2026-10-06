import { useEffect, useState } from 'react';
import Modal from '../../components/ui/Modal';
import { obtenerObra } from '../obras/obrasApi';
import { listarRubros } from '../presupuestacion/catalogoApi';
import { configurarEtapas, fecha } from './seguimientoApi';
import estilos from './Seguimiento.module.css';

/** Una etapa vacía, para agregar al final. */
const ETAPA_VACIA = { nombreHito: '', idRubro: '', fechaInicio: '', cantidad: '', unidad: 'días' };

/** Suma días a una fecha "aaaa-mm-dd" sin que el huso horario la corra. */
function sumarDias(iso, dias) {
  const [anio, mes, dia] = iso.split('-').map(Number);
  return new Date(Date.UTC(anio, mes - 1, dia + dias)).toISOString().slice(0, 10);
}

/**
 * Las etapas que ya tiene la obra, para corregirlas en lugar de empezar de
 * cero. Solo las cargadas por duración: las que se definieron por porcentaje
 * no tienen días de dónde partir.
 */
function etapasIniciales(hitosActuales) {
  const conDuracion = hitosActuales.filter((h) => h.duracionDias);
  if (conDuracion.length === 0) return [{ ...ETAPA_VACIA }];
  return conDuracion.map((h) => ({
    nombreHito: h.nombreHito,
    idRubro: h.idRubro ? String(h.idRubro) : '',
    fechaInicio: h.fechaInicio ?? '',
    cantidad: String(h.duracionDias),
    unidad: 'días',
  }));
}

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
 *  Fecha de inicio y tareas en simultáneo (06/10/2026)
 * ------------------------------------------------------------------
 *
 * Cada etapa puede llevar su fecha de inicio. Así dos etapas se pueden solapar:
 * la instalación eléctrica puede arrancar antes de que termine la albañilería.
 * Si se deja vacía, la etapa arranca cuando termina la anterior, y la primera
 * cuando arranca la obra.
 *
 * El número de orden ya no se escribe: sale de la fecha de inicio. Esta
 * pantalla lo calcula igual que el servidor para mostrarlo antes de guardar.
 *
 * ------------------------------------------------------------------
 *  Días o semanas
 * ------------------------------------------------------------------
 *
 * Se carga en la unidad que a uno le salga y se convierte a días acá, porque el
 * backend guarda días: mezclar unidades en la base obligaría a convertir en cada
 * consulta, y tarde o temprano alguien sumaría semanas con días. Los días son
 * corridos.
 *
 * El porcentaje que se muestra al lado de cada etapa es una PREVIA: el reparto
 * que manda lo hace el servidor, que además se ocupa de que cierre exactamente
 * en 100 cuando la división no da redonda.
 */

export default function ConfigurarEtapas({ idObra, hitosActuales = [], onCerrar, onGuardado }) {
  const [lineas, setLineas] = useState(() => etapasIniciales(hitosActuales));
  const [rubros, setRubros] = useState([]);
  const [inicioDeLaObra, setInicioDeLaObra] = useState(null);
  const [error, setError] = useState(null);
  const [guardando, setGuardando] = useState(false);

  useEffect(() => {
    let vigente = true;
    // Si el catálogo falla, el formulario sigue sirviendo: el rubro es opcional.
    listarRubros({ estado: 'Activo' })
      .then((d) => { if (vigente) setRubros(d); })
      .catch(() => {});
    // De dónde arranca la primera etapa si no se le pone fecha: el inicio
    // real de la obra, o si todavía no lo tiene, el estimado.
    obtenerObra(idObra)
      .then((o) => {
        if (vigente) setInicioDeLaObra(o.fechaInicioReal ?? o.fechaInicioEstimada ?? null);
      })
      .catch(() => {});
    return () => { vigente = false; };
  }, [idObra]);

  /**
   * Pasa la duración de una etapa a días, por si se cargó en semanas.
   */
  const enDias = (linea) => {
    const n = Number(linea.cantidad);
    if (!(n > 0)) return 0;
    return linea.unidad === 'semanas' ? n * 7 : n;
  };

  // Cuándo arranca cada etapa: la fecha que se le puso, o el día siguiente a
  // que termina la anterior. Es la misma cuenta que hace el servidor.
  const inicios = [];
  lineas.forEach((linea, i) => {
    if (linea.fechaInicio) {
      inicios.push(linea.fechaInicio);
    } else if (i === 0) {
      inicios.push(inicioDeLaObra);
    } else {
      const anterior = inicios[i - 1];
      const duracion = enDias(lineas[i - 1]);
      inicios.push(anterior && duracion > 0 ? sumarDias(anterior, duracion) : null);
    }
  });

  /** El último día de una etapa (una etapa de un día termina el mismo día). */
  const finDe = (i) => {
    const dias = enDias(lineas[i]);
    return inicios[i] && dias > 0 ? sumarDias(inicios[i], dias - 1) : null;
  };

  // El orden sale de la fecha de inicio. Las que arrancan el mismo día, o no
  // tienen fecha, conservan el orden en que se cargaron.
  const porFecha = lineas.map((_, i) => i).sort((a, b) => {
    if (inicios[a] === inicios[b]) return a - b;
    if (!inicios[a]) return 1;
    if (!inicios[b]) return -1;
    return inicios[a] < inicios[b] ? -1 : 1;
  });
  const ordenDe = (i) => porFecha.indexOf(i) + 1;

  const totalDias = lineas.reduce((s, l) => s + enDias(l), 0);

  // De punta a punta: del primer inicio al último fin. Con etapas solapadas es
  // menos que la suma de las duraciones, y esa diferencia es lo que se gana
  // haciendo cosas en simultáneo.
  const conFechas = lineas.map((_, i) => i).filter((i) => inicios[i] && finDe(i));
  const desde = conFechas.length ? conFechas.map((i) => inicios[i]).sort()[0] : null;
  const hasta = conFechas.length ? conFechas.map(finDe).sort().reverse()[0] : null;
  const diasCorridos = desde && hasta
    ? Math.round((Date.parse(hasta) - Date.parse(desde)) / 86400000) + 1
    : null;

  /**
   * Actualiza un dato de una etapa a medida que el dueño escribe.
   */
  const cambiar = (indice, campo) => (e) => {
    const valor = e.target.value;
    setLineas((previas) => previas.map((l, i) => (i === indice ? { ...l, [campo]: valor } : l)));
  };

  /**
   * Agrega una etapa vacía al final de la lista.
   */
  const agregar = () => setLineas((p) => [...p, { ...ETAPA_VACIA }]);

  /**
   * Saca una etapa de la lista.
   */
  const quitar = (i) => setLineas((p) => p.filter((_, j) => j !== i));

  /**
   * Guarda el plan de etapas. El sistema calcula la ponderación de cada una
   * según los días que dura, y el orden según la fecha de inicio.
   */
  const enviar = async (evento) => {
    evento.preventDefault();
    setGuardando(true);
    setError(null);

    try {
      await configurarEtapas(idObra, lineas.map((l, i) => ({
        nombreHito: l.nombreHito,
        idRubro: l.idRubro ? Number(l.idRubro) : null,
        duracionDias: enDias(l),
        // El orden en que se cargaron: sirve para encadenar las que no
        // tienen fecha. El orden final lo calcula el servidor.
        orden: i + 1,
        fechaInicio: l.fechaInicio || null,
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
          Cargá qué hay que hacer, de qué rubro es, cuándo arranca y cuánto creés
          que lleva. Si dejás la fecha vacía, la etapa arranca cuando termina la
          anterior. Poniendo fechas, dos etapas pueden hacerse en simultáneo. El
          orden y el porcentaje de avance de cada etapa los calcula el sistema.
        </p>

        <div className={estilos.campo}>
          <div className={estilos.encabezadoEtapas}>
            <span>N°</span>
            <span>Qué hay que hacer</span>
            <span>Rubro</span>
            <span>Arranca</span>
            {/* Un solo rótulo para los dos campos: la cantidad y su unidad son
                un dato, no dos. Partirlo daba un "Dura" que parecía cortado. */}
            <span className={estilos.rotuloDuracion}>Cuánto lleva</span>
            <span>Termina</span>
            <span>Pesa</span>
            <span />
          </div>

          {lineas.map((linea, i) => {
            const dias = enDias(linea);
            const porcentaje = totalDias > 0 ? (dias * 100) / totalDias : 0;

            return (
              // eslint-disable-next-line react/no-array-index-key
              <div key={i} className={estilos.lineaEtapa}>
                {/* El orden que le toca según su fecha. No se edita. */}
                <span className={`cifra ${estilos.ordenEtapa}`} title="Sale de la fecha de inicio">
                  {ordenDe(i)}
                </span>

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

                {/* Vacía, arranca cuando termina la anterior: abajo se ve qué
                    fecha le tocaría. */}
                <div>
                  <input type="date" className={estilos.control}
                         value={linea.fechaInicio} onChange={cambiar(i, 'fechaInicio')}
                         aria-label={`Fecha de inicio de la etapa ${i + 1}`} />
                  {!linea.fechaInicio && (
                    <span className={estilos.fechaSugerida}>
                      {inicios[i]
                        ? `${fecha(inicios[i])}${i === 0 ? ' (inicio de obra)' : ''}`
                        : 'Al terminar la anterior'}
                    </span>
                  )}
                </div>

                <input type="number" min="1" className={estilos.control} placeholder="5"
                       value={linea.cantidad} onChange={cambiar(i, 'cantidad')} required
                       aria-label={`Duración de la etapa ${i + 1}`} />

                <select className={estilos.control} value={linea.unidad}
                        onChange={cambiar(i, 'unidad')}
                        aria-label={`Unidad de la etapa ${i + 1}`}>
                  <option value="días">días</option>
                  <option value="semanas">semanas</option>
                </select>

                <span className={`cifra ${estilos.finEtapa}`}>{fecha(finDe(i))}</span>

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
          <span className={estilos.etiqueta}>Plazo de la obra</span>
          <span className={`cifra ${estilos.sumaOk}`}>
            {diasCorridos
              ? `${fecha(desde)} al ${fecha(hasta)} · ${diasCorridos} días corridos`
              : `${totalDias} ${totalDias === 1 ? 'día' : 'días'} de trabajo`}
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
