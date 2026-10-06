import { useEffect, useState } from 'react';
import { cargarPlanilla, guardarPlanilla } from './presupuestosApi';
import estilos from './Presupuestos.module.css';

/**
 * Carga de un rubro del presupuesto, como una hoja de cálculo.
 *
 * ------------------------------------------------------------------
 *  Qué problema resuelve
 * ------------------------------------------------------------------
 *
 * Antes, presupuestar un rubro era agregar los ítems de a uno: abrir un
 * formulario, buscar el material, escribir cantidad y precio, guardar, y otra
 * vez desde el principio. Con veinte materiales son veinte vueltas.
 *
 * Ricardo lo pidió de otra forma al probar el sistema: que al elegir un rubro
 * aparezcan TODOS los materiales del catálogo en una planilla, y él vaya
 * completando cantidad y precio donde corresponda. Es como se presupuesta en
 * papel, y se recorre la lista una sola vez.
 *
 * ------------------------------------------------------------------
 *  Va en la página, no en una ventana
 * ------------------------------------------------------------------
 *
 * La primera versión abría un modal. Ricardo pidió que apareciera directamente
 * debajo de los rubros, y tiene razón: una ventana emergente tapa el
 * presupuesto que se está armando, y justamente lo que uno quiere mientras
 * carga un rubro es ver cómo se mueve el total y qué hay cargado en los otros.
 *
 * ------------------------------------------------------------------
 *  El rubro de mano de obra se ve distinto
 * ------------------------------------------------------------------
 *
 * Si el rubro está marcado como el de mano de obra, sus filas no son materiales
 * sino una por cada rubro y subrubro del catálogo ("Albañilería / Demolición",
 * "Albañilería / Colocación"...), y de cada una se carga solo el TOTAL de mano
 * de obra. Las filas que quedan vacías o en 0 no entran al presupuesto. El
 * backend arma las filas y manda `esManoDeObra`.
 *
 * En los demás rubros, una fila con cantidad 0 tampoco entra al presupuesto.
 */
export default function PlanillaDeRubro({ idPresupuesto, rubro, onCerrar, onGuardado }) {
  const [planilla, setPlanilla] = useState(null);
  const [filas, setFilas] = useState([]);
  const [cargando, setCargando] = useState(true);
  const [guardando, setGuardando] = useState(false);
  const [error, setError] = useState(null);

  useEffect(() => {
    let vigente = true;
    setCargando(true);

    cargarPlanilla(idPresupuesto, rubro.idRubro)
      .then((datos) => {
        if (!vigente) return;
        setPlanilla(datos);
        // Los números llegan como null cuando la fila está vacía; en el input se
        // manejan como texto para que el campo pueda quedar en blanco mientras
        // se escribe, en lugar de saltar a 0.
        setFilas(datos.filas.map((f) => ({
          ...f,
          cantidad: f.cantidad ?? '',
          valorUnitario: f.valorUnitario ?? '',
        })));
        setError(null);
      })
      .catch((fallo) => { if (vigente) setError(fallo.mensaje); })
      .finally(() => { if (vigente) setCargando(false); });

    return () => { vigente = false; };
  }, [idPresupuesto, rubro.idRubro]);

  const esManoDeObra = planilla?.esManoDeObra;

  /**
   * Actualiza la cantidad o el precio de una fila de la planilla a medida que
   * el dueño escribe.
   */
  const cambiar = (indice, campo) => (evento) => {
    const valor = evento.target.value;
    setFilas((previas) => previas.map(
      (f, i) => (i === indice ? { ...f, [campo]: valor } : f)));
  };

  /**
   * Escribe el total de mano de obra de una fila. El ítem se guarda como 1
   * global por ese importe, así no hace falta una columna nueva en la base.
   */
  const escribirTotal = (indice) => (evento) => {
    const valor = evento.target.value;
    setFilas((previas) => previas.map((f, i) => (i === indice ? {
      ...f,
      cantidad: valor === '' ? '' : '1',
      unidadMedida: 'global',
      valorUnitario: valor,
    } : f)));
  };

  /**
   * Calcula el subtotal de una fila. En mano de obra es el total escrito, y en
   * los materiales, cantidad por precio. Una fila vacía o en 0 no cuenta, igual
   * que en el presupuesto.
   */
  const subtotalDe = (fila) => {
    const v = Number(fila.valorUnitario);
    if (esManoDeObra) {
      return fila.valorUnitario !== '' && v > 0 ? v : null;
    }
    const c = Number(fila.cantidad);
    return c > 0 && fila.valorUnitario !== '' ? c * v : null;
  };

  /** El total de lo cargado, para verlo crecer sin guardar. */
  const total = filas.reduce((suma, f) => suma + (subtotalDe(f) ?? 0), 0);

  const cargadas = filas.filter((f) => subtotalDe(f) !== null).length;

  /**
   * Guarda la planilla completa del rubro. Las filas vacías no se cargan, y lo
   * que había antes de ese rubro se reemplaza por lo que se envía.
   */
  const enviar = async (evento) => {
    evento.preventDefault();
    setGuardando(true);
    setError(null);

    try {
      // Se manda TODO, incluidas las filas vacías: el backend reemplaza los
      // ítems de este rubro con lo que llegue, así vaciar una fila la borra.
      onGuardado(await guardarPlanilla(idPresupuesto, rubro.idRubro,
        filas.map((f) => ({
          idMaterial: f.idMaterial,
          idSubrubro: f.idSubrubro,
          descripcion: f.descripcion,
          unidadMedida: f.unidadMedida,
          cantidad: f.cantidad === '' ? null : Number(f.cantidad),
          valorUnitario: f.valorUnitario === '' ? null : Number(f.valorUnitario),
        }))));
    } catch (fallo) {
      setError(fallo.mensaje);
    } finally {
      setGuardando(false);
    }
  };

  return (
    <section className={estilos.planillaInline}>
      <div className={estilos.planillaCabecera}>
        <h5 className={estilos.planillaTitulo}>
          Presupuestar {rubro.nombreRubro}
        </h5>
        <button type="button" className={estilos.cerrarPlanilla} onClick={onCerrar}>
          Cerrar
        </button>
      </div>

      {cargando ? (
        <p className={estilos.aviso}>Armando la planilla…</p>
      ) : (
        <form onSubmit={enviar}>
          {error && <p className={estilos.errorGeneral}>{error}</p>}

          <p className={estilos.ayuda}>
            {esManoDeObra
              ? 'Cada rubro con sus subrubros: escribí el total de mano de obra '
                + 'de los trabajos que lleva la obra. '
              : 'Todos los materiales del rubro, con su unidad. '}
            Completá las filas que vayan al presupuesto. Las que dejes vacías o
            en 0 no se cargan.
          </p>

          {filas.length === 0 ? (
            <p className={estilos.aviso}>
              {esManoDeObra
                ? 'No hay otros rubros cargados en el catálogo.'
                : 'Este rubro no tiene materiales en el catálogo. '
                  + 'Cargalos desde Materiales y volvé.'}
            </p>
          ) : (
            <div className="scroll-x">
              <table className={`table ${estilos.planilla}`}>
                <thead>
                  <tr>
                    {esManoDeObra ? (
                      <>
                        <th>Rubro</th>
                        <th>Subrubro</th>
                        <th style={{ width: 170, textAlign: 'right' }}>Total</th>
                      </>
                    ) : (
                      <>
                        <th>Material</th>
                        <th style={{ width: 110, textAlign: 'right' }}>Cantidad</th>
                        <th style={{ width: 96 }}>Unidad</th>
                        <th style={{ width: 140, textAlign: 'right' }}>Precio unit.</th>
                        <th style={{ width: 150, textAlign: 'right' }}>Subtotal</th>
                      </>
                    )}
                  </tr>
                </thead>
                <tbody>
                  {filas.map((fila, i) => {
                    const subtotal = subtotalDe(fila);

                    if (esManoDeObra) {
                      return (
                        <tr key={`d-${fila.descripcion}-${i}`}
                            className={subtotal !== null ? estilos.filaCargada : undefined}>
                          <td>{fila.rubroReferido ?? fila.descripcion}</td>
                          <td>{fila.subrubroReferido ?? '—'}</td>
                          <td>
                            <input
                              type="number" min="0" step="0.01"
                              className={estilos.celda}
                              value={fila.valorUnitario}
                              onChange={escribirTotal(i)}
                              placeholder="—"
                              aria-label={`Total de mano de obra de ${fila.descripcion}`}
                            />
                          </td>
                        </tr>
                      );
                    }

                    return (
                      <tr key={fila.idMaterial ?? `d-${fila.descripcion}-${i}`}
                          className={subtotal !== null ? estilos.filaCargada : undefined}>
                        <td>{fila.descripcion}</td>

                        <td>
                          <input
                            type="number" min="0" step="0.01"
                            className={estilos.celda}
                            value={fila.cantidad}
                            onChange={cambiar(i, 'cantidad')}
                            placeholder="—"
                            aria-label={`Cantidad de ${fila.descripcion}`}
                          />
                        </td>

                        <td>
                          <input
                            type="text"
                            className={estilos.celda}
                            value={fila.unidadMedida ?? ''}
                            onChange={cambiar(i, 'unidadMedida')}
                            aria-label={`Unidad de ${fila.descripcion}`}
                          />
                        </td>

                        <td>
                          <input
                            type="number" min="0" step="0.01"
                            className={estilos.celda}
                            value={fila.valorUnitario}
                            onChange={cambiar(i, 'valorUnitario')}
                            placeholder="—"
                            aria-label={`Precio de ${fila.descripcion}`}
                          />
                        </td>

                        <td className={`cifra ${estilos.total}`}>
                          {subtotal !== null
                            ? '$ ' + Math.round(subtotal).toLocaleString('es-AR')
                            : '—'}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          )}

          <div className={estilos.planillaPie}>
            <span className={estilos.ayuda}>
              {cargadas} de {filas.length} {filas.length === 1 ? 'fila' : 'filas'}
            </span>
            <span className={`cifra ${estilos.calculoTotal}`}>
              $ {Math.round(total).toLocaleString('es-AR')}
            </span>
          </div>

          <div className={estilos.accionesFormulario}>
            <button type="button" className={estilos.botonSecundario} onClick={onCerrar}>
              Cancelar
            </button>
            <button type="submit" className={estilos.botonPrimario} disabled={guardando}>
              {guardando ? 'Guardando…' : 'Guardar el rubro'}
            </button>
          </div>
        </form>
      )}
    </section>
  );
}
