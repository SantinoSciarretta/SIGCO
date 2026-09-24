import { useEffect, useMemo, useState } from 'react';
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
 * sino los otros rubros: se carga de una sola vez cuánto sale la mano de obra de
 * albañilería, de plomería, de pintura. El backend decide eso y manda
 * `esManoDeObra`.
 *
 * Y ahí la carga admite las dos formas en que se piensa el número: jornales por
 * valor del jornal, o el total directo. Ricardo lo pidió así porque en la
 * práctica a veces sabe los jornales y a veces le pasan un precio cerrado por
 * el trabajo. Obligarlo a inventar una de las dos partes para poder cargar la
 * otra daría un dato falso en la planilla.
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

  const cambiar = (indice, campo) => (evento) => {
    const valor = evento.target.value;
    setFilas((previas) => previas.map(
      (f, i) => (i === indice ? { ...f, [campo]: valor } : f)));
  };

  /**
   * Escribir el total directamente, sin pasar por los jornales.
   *
   * El presupuesto guarda cantidad por valor unitario, así que un total suelto
   * se representa como una cantidad de 1: el ítem queda "Albañilería, 1 global,
   * $450.000". No hace falta una columna nueva en la base ni un caso especial
   * al sumar, y el PDF lo muestra igual que cualquier otro ítem.
   *
   * Que los jornales pasen a 1 se VE en la pantalla, y está bien que se vea: es
   * lo que acaba de pasar con el dato.
   */
  const escribirTotal = (indice) => (evento) => {
    const valor = evento.target.value;
    setFilas((previas) => previas.map((f, i) => (i === indice ? {
      ...f,
      cantidad: valor === '' ? '' : '1',
      unidadMedida: valor === '' ? f.unidadMedida : 'global',
      valorUnitario: valor,
    } : f)));
  };

  const subtotalDe = (fila) => {
    const c = Number(fila.cantidad);
    const v = Number(fila.valorUnitario);
    return c > 0 && fila.valorUnitario !== '' ? c * v : null;
  };

  /** El total de lo cargado, para verlo crecer sin guardar. */
  const total = useMemo(
    () => filas.reduce((suma, f) => suma + (subtotalDe(f) ?? 0), 0), [filas]);

  const cargadas = filas.filter((f) => subtotalDe(f) !== null).length;

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
              ? 'Cargá los jornales y el valor de cada uno, o escribí el total '
                + 'directamente en la última columna. '
              : 'Todos los materiales del rubro, con su unidad. '}
            Completá las filas que vayan al presupuesto; las que dejes vacías no
            se cargan.
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
                    <th>{esManoDeObra ? 'Rubro' : 'Material'}</th>
                    <th style={{ width: 110, textAlign: 'right' }}>
                      {esManoDeObra ? 'Jornales' : 'Cantidad'}
                    </th>
                    {!esManoDeObra && <th style={{ width: 96 }}>Unidad</th>}
                    <th style={{ width: 140, textAlign: 'right' }}>
                      {esManoDeObra ? 'Valor del jornal' : 'Precio unit.'}
                    </th>
                    <th style={{ width: 150, textAlign: 'right' }}>
                      {esManoDeObra ? 'Total' : 'Subtotal'}
                    </th>
                  </tr>
                </thead>
                <tbody>
                  {filas.map((fila, i) => {
                    const subtotal = subtotalDe(fila);

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
                            aria-label={`${esManoDeObra ? 'Jornales' : 'Cantidad'} de ${fila.descripcion}`}
                          />
                        </td>

                        {!esManoDeObra && (
                          <td>
                            <input
                              type="text"
                              className={estilos.celda}
                              value={fila.unidadMedida ?? ''}
                              onChange={cambiar(i, 'unidadMedida')}
                              aria-label={`Unidad de ${fila.descripcion}`}
                            />
                          </td>
                        )}

                        <td>
                          <input
                            type="number" min="0" step="0.01"
                            className={estilos.celda}
                            value={fila.valorUnitario}
                            onChange={cambiar(i, 'valorUnitario')}
                            placeholder="—"
                            aria-label={`${esManoDeObra ? 'Valor del jornal' : 'Precio'} de ${fila.descripcion}`}
                          />
                        </td>

                        {/* En mano de obra el total se puede escribir: a veces
                            se sabe el jornal y a veces le pasan un precio
                            cerrado por el trabajo. En los materiales el
                            subtotal siempre sale de cantidad por precio. */}
                        <td className={`cifra ${estilos.total}`}>
                          {esManoDeObra ? (
                            <input
                              type="number" min="0" step="0.01"
                              className={estilos.celda}
                              value={subtotal ?? ''}
                              onChange={escribirTotal(i)}
                              placeholder="—"
                              aria-label={`Total de mano de obra de ${fila.descripcion}`}
                            />
                          ) : (
                            subtotal !== null
                              ? '$ ' + Math.round(subtotal).toLocaleString('es-AR')
                              : '—'
                          )}
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
