import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { abrirPdf } from '../../api/documentos';
import Blueprint from '../../components/ui/Blueprint';
import Modal from '../../components/ui/Modal';
import { listarMaterialesDisponibles } from '../materiales/materialesApi';
import { listarRubros } from './catalogoApi';
import {
  UNIDADES, agregarItem, actualizarItem, cambiarEstadoPresupuesto, definirPlanDePago,
  duplicarPresupuesto, estadosPosiblesDesde, obtenerPresupuesto, pesos, quitarItem,
} from './presupuestosApi';
import PlanillaDeRubro from './PlanillaDeRubro';
import estilos from './Presupuestos.module.css';

/**
 * Detalle de un presupuesto: carga de ítems, plan de pago y estado.
 *
 * El total se recalcula en el servidor con cada cambio de ítem y vuelve en la
 * respuesta, así que la pantalla nunca hace la cuenta por su lado. Es la misma
 * razón por la que el subtotal de cada ítem no se puede escribir a mano: se
 * deriva siempre de cantidad por valor unitario.
 *
 * Un presupuesto solo se edita mientras está en Borrador. Una vez enviado al
 * cliente queda congelado, y los cambios se hacen generando una versión nueva.
 * Eso es lo que resuelve el problema del relevamiento, donde las versiones se
 * pisaban en Excel.
 */
export default function PresupuestoDetalle() {
  const { id } = useParams();

  const [presupuesto, setPresupuesto] = useState(null);
  const [rubros, setRubros] = useState([]);
  const [materiales, setMateriales] = useState([]);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState(null);

  const [itemEnEdicion, setItemEnEdicion] = useState(null);
  const [formularioItem, setFormularioItem] = useState(false);
  const [planAbierto, setPlanAbierto] = useState(false);
  const [duplicarAbierto, setDuplicarAbierto] = useState(false);

  /** Rubro que se está presupuestando en la planilla, o null. */
  const [rubroEnPlanilla, setRubroEnPlanilla] = useState(null);

  /**
   * Trae el presupuesto con sus ítems.
   *
   * La bandera `vigente` descarta la respuesta si el componente se desmontó o
   * si se navegó a otro presupuesto mientras la consulta estaba en curso.
   */
  useEffect(() => {
    let vigente = true;

    (async () => {
      try {
        const datos = await obtenerPresupuesto(id);
        if (vigente) {
          setPresupuesto(datos);
          setError(null);
        }
      } catch (fallo) {
        if (vigente) setError(fallo.mensaje);
      } finally {
        if (vigente) setCargando(false);
      }
    })();

    return () => { vigente = false; };
  }, [id]);

  // El catálogo se pide una sola vez: alimenta los desplegables de rubro y
  // subrubro del formulario de ítem.
  useEffect(() => {
    let vigente = true;
    listarRubros({ estado: 'Activo' })
      .then((datos) => { if (vigente) setRubros(datos); })
      .catch(() => {});
    // El catálogo de materiales alimenta el selector del ítem. Si falla, el
    // formulario sigue sirviendo: el material es opcional.
    listarMaterialesDisponibles()
      .then((datos) => { if (vigente) setMateriales(datos); })
      .catch(() => {});
    return () => { vigente = false; };
  }, []);

  const cambiarEstado = async (nuevoEstado) => {
    try {
      setPresupuesto(await cambiarEstadoPresupuesto(id, nuevoEstado));
      setError(null);
    } catch (fallo) {
      setError(fallo.mensaje);
    }
  };

  const eliminarItem = async (idItem) => {
    try {
      setPresupuesto(await quitarItem(id, idItem));
    } catch (fallo) {
      setError(fallo.mensaje);
    }
  };

  if (cargando) return <p className={estilos.aviso}>Consultando…</p>;
  if (!presupuesto) return <p className={estilos.errorGeneral}>{error}</p>;

  const editable = presupuesto.estado === 'Borrador';
  const llevaItems = presupuesto.tipoPresupuesto !== 'Cotización inicial';

  return (
    <>
      <p className={estilos.volver}><Link to="/presupuestos">← Presupuestos</Link></p>

      <header className={estilos.encabezado}>
        <div>
          <span className="kicker kicker-acento">
            {presupuesto.tipoPresupuesto} · versión {presupuesto.version}
            {presupuesto.idPresupuestoBase && ` · a partir del #${presupuesto.idPresupuestoBase}`}
          </span>
          <h2 className={estilos.tituloDetalle}>{presupuesto.direccionObra}</h2>
          <p className={estilos.subtitulo}>
            {presupuesto.nombreCliente}
            {presupuesto.plazoEstimadoObra && ` · Plazo ${presupuesto.plazoEstimadoObra}`}
          </p>
        </div>

        <div className={estilos.totalBloque}>
          <span className="kicker">Total</span>
          <span className={`cifra ${estilos.totalCifra}`}>{pesos(presupuesto.totalPresupuesto)}</span>
          <span className={claseDeEstado(presupuesto.estado)}>{presupuesto.estado}</span>
        </div>
      </header>

      {error && <p className={estilos.errorGeneral}>{error}</p>}

      {/* ---------- Acciones ---------- */}
      <div className={estilos.barraAcciones}>
        {editable && llevaItems && (
          <button type="button" className={estilos.botonPrimario}
                  onClick={() => { setItemEnEdicion(null); setFormularioItem(true); }}>
            Agregar ítem
          </button>
        )}
        {editable && (
          <button type="button" className={estilos.botonSecundario} onClick={() => setPlanAbierto(true)}>
            Plan de pago
          </button>
        )}
        {estadosPosiblesDesde(presupuesto.estado).map((e) => (
          <button key={e} type="button" className={estilos.botonSecundario}
                  onClick={() => cambiarEstado(e)}>
            Marcar {e.toLowerCase()}
          </button>
        ))}
        <button type="button" className={estilos.botonSecundario} onClick={() => setDuplicarAbierto(true)}>
          Usar como base
        </button>
        {/* Se pide con Axios y NO con un enlace: un enlace no envía el token,
            y desde que la seguridad está activa el backend lo exige. */}
        <button type="button" className={estilos.botonSecundario}
                onClick={() => abrirPdf(`/presupuestos/${id}/pdf`,
                                        `presupuesto-${id}.pdf`)}>
          Ver PDF
        </button>
      </div>

      {!editable && (
        <p className={estilos.avisoCongelado}>
          Este presupuesto está {presupuesto.estado.toLowerCase()} y ya no se modifica.
          Para cambios, generá una versión nueva con “Usar como base”.
        </p>
      )}

      {/* ---------- Cotización inicial ---------- */}
      {!llevaItems && (
        <Blueprint className={estilos.bloque}>
          <h4 className={estilos.bloqueTitulo}>Cálculo estimativo</h4>
          <div className={estilos.calculo}>
            <span className={`cifra ${estilos.calculoValor}`}>{presupuesto.metrosCuadrados} m²</span>
            <span className={estilos.calculoOperador}>×</span>
            <span className={`cifra ${estilos.calculoValor}`}>{pesos(presupuesto.valorPorM2)}</span>
            <span className={estilos.calculoOperador}>=</span>
            <span className={`cifra ${estilos.calculoTotal}`}>{pesos(presupuesto.totalPresupuesto)}</span>
          </div>
          <p className={estilos.ayuda}>
            La cotización inicial no lleva ítems: es el precio estimativo que se le
            pasa al cliente en el primer contacto.
          </p>
        </Blueprint>
      )}

      {/* ---------- Ítems ---------- */}
      {llevaItems && (
        <Blueprint className={estilos.bloque}>
          <div className={estilos.bloqueCabecera}>
            <h4 className={estilos.bloqueTitulo}>Ítems</h4>
            <span className="kicker">
              El valor unitario es interno: no aparece en el PDF del cliente
            </span>
          </div>

          {/* Presupuestar por rubro, con la planilla.
              Es la forma principal de cargar desde el pedido de Ricardo: se
              elige un rubro y aparecen todos sus materiales para completar.
              El "Agregar ítem" de a uno sigue existiendo para lo que no está en
              el catálogo (dirección de obra, un trabajo puntual). */}
          {editable && rubros.length > 0 && (
            <div className={estilos.elegirRubro}>
              <span className={estilos.etiqueta}>Presupuestar un rubro:</span>
              {rubros.filter((r) => r.estado === 'Activo').map((r) => (
                <button
                  key={r.idRubro}
                  type="button"
                  className={claseDeRubro(r, rubroEnPlanilla, estilos)}
                  // Volver a apretar el rubro abierto lo cierra: es el gesto
                  // que uno espera de algo que se despliega en la pagina.
                  onClick={() => setRubroEnPlanilla(
                    rubroEnPlanilla?.idRubro === r.idRubro ? null : r)}
                >
                  {r.nombreRubro}
                </button>
              ))}
            </div>
          )}

          {/* La planilla del rubro elegido, ahi mismo. Antes era una ventana
              emergente; Ricardo pidio que apareciera en la pagina, y tiene
              razon: el modal tapaba el presupuesto que se esta armando, que es
              justo lo que uno quiere mirar mientras carga un rubro. */}
          {editable && rubroEnPlanilla && (
            <PlanillaDeRubro
              idPresupuesto={presupuesto.idPresupuesto}
              rubro={rubroEnPlanilla}
              onCerrar={() => setRubroEnPlanilla(null)}
              onGuardado={(actualizado) => {
                setPresupuesto(actualizado);
                setRubroEnPlanilla(null);
              }}
            />
          )}

          {presupuesto.items.length === 0 ? (
            <p className={estilos.aviso}>
              Todavía no hay ítems. {editable && 'Agregá el primero con el botón de arriba.'}
            </p>
          ) : (
            <div className="scroll-x">
              <table className="table">
                <thead>
                  <tr>
                    <th>Rubro / Subrubro</th>
                    <th>Descripción</th>
                    <th style={{ textAlign: 'right' }}>Cant.</th>
                    <th>Unidad</th>
                    <th style={{ textAlign: 'right' }}>V. unitario</th>
                    <th style={{ textAlign: 'right' }}>Subtotal</th>
                    {editable && <th />}
                  </tr>
                </thead>
                <tbody>
                  {presupuesto.items.map((item) => (
                    <tr key={item.idItem}>
                      <td>
                        <div className={estilos.rubroNombre}>{item.nombreRubro}</div>
                        {item.nombreSubrubro && (
                          <div className={estilos.subrubroNombre}>{item.nombreSubrubro}</div>
                        )}
                      </td>
                      <td className={estilos.dato}>
                        {item.descripcion}
                        {/* Señal de que el ítem salió del catálogo y no de
                            texto libre. Es lo que permite después agrupar el
                            gasto del mismo insumo entre obras distintas. */}
                        {item.nombreMaterial && (
                          <span className={estilos.materialVinculado}
                                title="Vinculado al catálogo de Materiales">
                            {item.nombreMaterial}
                          </span>
                        )}
                      </td>
                      <td className={`cifra ${estilos.numero}`}>{item.cantidad}</td>
                      <td className={estilos.dato}>{item.unidadMedida}</td>
                      <td className={`cifra ${estilos.numero} ${estilos.interno}`}>
                        {pesos(item.valorUnitario)}
                      </td>
                      <td className={`cifra ${estilos.numero}`}>{pesos(item.subtotal)}</td>
                      {editable && (
                        <td className={estilos.acciones}>
                          <button type="button" className={estilos.accion}
                                  onClick={() => { setItemEnEdicion(item); setFormularioItem(true); }}>
                            Editar
                          </button>
                          <button type="button" className={estilos.accion}
                                  onClick={() => eliminarItem(item.idItem)}>
                            Quitar
                          </button>
                        </td>
                      )}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </Blueprint>
      )}

      {/* ---------- Subtotales por rubro ---------- */}
      {presupuesto.subtotalesPorRubro.length > 0 && (
        <Blueprint className={estilos.bloque}>
          <div className={estilos.bloqueCabecera}>
            <h4 className={estilos.bloqueTitulo}>Subtotales por rubro</h4>
            <span className="kicker">Es lo único que ve el cliente en el PDF</span>
          </div>
          <ul className={estilos.subtotales}>
            {presupuesto.subtotalesPorRubro.map((s) => (
              <li key={s.idRubro}>
                <span>{s.nombreRubro}</span>
                <span className={`cifra ${estilos.numero}`}>{pesos(s.subtotal)}</span>
              </li>
            ))}
            <li className={estilos.subtotalTotal}>
              <span>Total</span>
              <span className={`cifra ${estilos.numero}`}>{pesos(presupuesto.totalPresupuesto)}</span>
            </li>
          </ul>
        </Blueprint>
      )}

      {/* ---------- Plan de pago ---------- */}
      {presupuesto.anticipoPorcentaje !== null && (
        <Blueprint className={estilos.bloque}>
          <h4 className={estilos.bloqueTitulo}>Plan de pago</h4>
          <div className={estilos.plan}>
            <div>
              <span className="kicker">Anticipo ({presupuesto.anticipoPorcentaje}%)</span>
              <div className={`cifra ${estilos.planCifra}`}>{pesos(presupuesto.montoAnticipo)}</div>
            </div>
            <div>
              <span className="kicker">
                {presupuesto.cantidadCuotas === 0
                  ? 'Sin cuotas'
                  : `${presupuesto.cantidadCuotas} cuotas de`}
              </span>
              <div className={`cifra ${estilos.planCifra}`}>{pesos(presupuesto.montoCuota)}</div>
            </div>
          </div>
          <p className={estilos.ayuda}>
            El módulo Cobros toma estos valores para generar el plan de cuotas de la obra.
          </p>
        </Blueprint>
      )}

      {formularioItem && (
        <ItemModal
          idPresupuesto={id}
          item={itemEnEdicion}
          rubros={rubros}
          materiales={materiales}
          onCerrar={() => setFormularioItem(false)}
          onGuardado={(actualizado) => { setPresupuesto(actualizado); setFormularioItem(false); }}
        />
      )}

      {planAbierto && (
        <PlanModal
          idPresupuesto={id}
          presupuesto={presupuesto}
          onCerrar={() => setPlanAbierto(false)}
          onGuardado={(actualizado) => { setPresupuesto(actualizado); setPlanAbierto(false); }}
        />
      )}

      {duplicarAbierto && (
        <DuplicarModal
          idPresupuesto={id}
          onCerrar={() => setDuplicarAbierto(false)}
        />
      )}
    </>
  );
}

/* ========================================================================== */

/** Alta y edición de un ítem. El subrubro se limita al rubro elegido. */
function ItemModal({ idPresupuesto, item, rubros, materiales, onCerrar, onGuardado }) {
  const [datos, setDatos] = useState({
    idRubro: item ? String(item.idRubro) : '',
    idSubrubro: item?.idSubrubro ? String(item.idSubrubro) : '',
    idMaterial: item?.idMaterial ? String(item.idMaterial) : '',
    descripcion: item?.descripcion ?? '',
    unidadMedida: item?.unidadMedida ?? 'm²',
    cantidad: item?.cantidad ?? '',
    valorUnitario: item?.valorUnitario ?? '',
  });
  const [camposInvalidos, setCamposInvalidos] = useState({});
  const [error, setError] = useState(null);
  const [guardando, setGuardando] = useState(false);

  const rubroElegido = rubros.find((r) => String(r.idRubro) === datos.idRubro);
  // Solo se ofrecen los subrubros del rubro elegido. El backend valida lo mismo:
  // acá es para que el usuario ni siquiera pueda intentar una combinación
  // inválida.
  const subrubrosDisponibles = rubroElegido
    ? rubroElegido.subrubros.filter((s) => s.estado === 'Activo')
    : [];

  // Cada material pertenece a un rubro, así que solo se ofrecen los del rubro
  // elegido. El backend valida lo mismo y rechaza la combinación inválida.
  const materialesDisponibles = rubroElegido
    ? materiales.filter((m) => m.idRubro === rubroElegido.idRubro)
    : [];

  /**
   * Al elegir un material del catálogo se completan descripción y unidad.
   *
   * Quedan editables a propósito: la descripción es lo que ve el cliente en el
   * PDF y suele necesitar más detalle que el nombre del catálogo ("Cemento CP40
   * para la carpeta del baño"). Lo que se guarda vinculado es el material; el
   * texto es lo que se imprime.
   */
  const elegirMaterial = (e) => {
    const valor = e.target.value;
    const material = materiales.find((m) => String(m.idMaterial) === valor);
    setDatos((previo) => ({
      ...previo,
      idMaterial: valor,
      descripcion: material && !previo.descripcion ? material.nombreMaterial : previo.descripcion,
      unidadMedida: material ? material.unidadMedida : previo.unidadMedida,
    }));
    setError(null);
  };

  const cambiar = (campo) => (e) => {
    const valor = e.target.value;
    setDatos((previo) => ({
      ...previo,
      [campo]: valor,
      // Al cambiar de rubro, el subrubro y el material anteriores dejan de
      // pertenecer a él.
      ...(campo === 'idRubro' ? { idSubrubro: '', idMaterial: '' } : {}),
    }));
    setCamposInvalidos((previo) => ({ ...previo, [campo]: undefined }));
  };

  // El subtotal se muestra calculado, pero no es editable: lo define el
  // servidor a partir de cantidad por valor unitario.
  const subtotal = (Number(datos.cantidad) || 0) * (Number(datos.valorUnitario) || 0);

  const enviar = async (evento) => {
    evento.preventDefault();
    setGuardando(true);
    setCamposInvalidos({});
    setError(null);

    const cuerpo = {
      idRubro: Number(datos.idRubro),
      idSubrubro: datos.idSubrubro ? Number(datos.idSubrubro) : null,
      idMaterial: datos.idMaterial ? Number(datos.idMaterial) : null,
      descripcion: datos.descripcion,
      unidadMedida: datos.unidadMedida,
      cantidad: datos.cantidad,
      valorUnitario: datos.valorUnitario,
    };

    try {
      onGuardado(item
        ? await actualizarItem(idPresupuesto, item.idItem, cuerpo)
        : await agregarItem(idPresupuesto, cuerpo));
    } catch (fallo) {
      if (fallo.camposInvalidos) setCamposInvalidos(fallo.camposInvalidos);
      else setError(fallo.mensaje);
    } finally {
      setGuardando(false);
    }
  };

  return (
    <Modal abierto onCerrar={onCerrar} titulo={item ? 'Editar ítem' : 'Nuevo ítem'}>
      <form onSubmit={enviar}>
        {error && <p className={estilos.errorGeneral}>{error}</p>}

        <div className={estilos.dosColumnas}>
          <div className={estilos.campo}>
            <label className={estilos.etiqueta} htmlFor="idRubro">
              Rubro <span className={estilos.obligatorio}>*</span>
            </label>
            <select id="idRubro" className={estilos.control} value={datos.idRubro}
                    onChange={cambiar('idRubro')} required>
              <option value="">Elegir…</option>
              {rubros.map((r) => <option key={r.idRubro} value={r.idRubro}>{r.nombreRubro}</option>)}
            </select>
          </div>

          <div className={estilos.campo}>
            <label className={estilos.etiqueta} htmlFor="idSubrubro">Subrubro</label>
            <select id="idSubrubro" className={estilos.control} value={datos.idSubrubro}
                    onChange={cambiar('idSubrubro')} disabled={!rubroElegido}>
              <option value="">Sin subrubro</option>
              {subrubrosDisponibles.map((s) => (
                <option key={s.idSubrubro} value={s.idSubrubro}>{s.nombreSubrubro}</option>
              ))}
            </select>
          </div>
        </div>

        <div className={estilos.campo}>
          <label className={estilos.etiqueta} htmlFor="idMaterial">Material del catálogo</label>
          <select id="idMaterial" className={estilos.control} value={datos.idMaterial}
                  onChange={elegirMaterial} disabled={!rubroElegido}>
            <option value="">Sin material del catálogo</option>
            {materialesDisponibles.map((m) => (
              <option key={m.idMaterial} value={m.idMaterial}>
                {m.nombreMaterial} — por {m.unidadMedida}
              </option>
            ))}
          </select>
          <p className={estilos.ayuda}>
            {!rubroElegido
              ? 'Elegí primero el rubro.'
              : materialesDisponibles.length === 0
                ? `Todavía no hay materiales cargados en ${rubroElegido.nombreRubro}.`
                : 'Opcional: no todo ítem es un material. La mano de obra o la dirección de obra van sin material.'}
          </p>
        </div>

        <div className={estilos.campo}>
          <label className={estilos.etiqueta} htmlFor="descripcion">
            Descripción <span className={estilos.obligatorio}>*</span>
          </label>
          <input id="descripcion" className={estilos.control} maxLength={250}
                 value={datos.descripcion} onChange={cambiar('descripcion')} required />
          {camposInvalidos.descripcion && (
            <p className={estilos.errorCampo}>{camposInvalidos.descripcion}</p>
          )}
        </div>

        <div className={estilos.tresColumnas}>
          <div className={estilos.campo}>
            <label className={estilos.etiqueta} htmlFor="cantidad">
              Cantidad <span className={estilos.obligatorio}>*</span>
            </label>
            <input id="cantidad" type="number" step="0.01" min="0.01" className={estilos.control}
                   value={datos.cantidad} onChange={cambiar('cantidad')} required />
            {camposInvalidos.cantidad && (
              <p className={estilos.errorCampo}>{camposInvalidos.cantidad}</p>
            )}
          </div>

          <div className={estilos.campo}>
            <label className={estilos.etiqueta} htmlFor="unidadMedida">
              Unidad <span className={estilos.obligatorio}>*</span>
            </label>
            <select id="unidadMedida" className={estilos.control} value={datos.unidadMedida}
                    onChange={cambiar('unidadMedida')} required>
              {UNIDADES.map((u) => <option key={u} value={u}>{u}</option>)}
            </select>
          </div>

          <div className={estilos.campo}>
            <label className={estilos.etiqueta} htmlFor="valorUnitario">
              Valor unitario <span className={estilos.obligatorio}>*</span>
            </label>
            <input id="valorUnitario" type="number" step="0.01" min="0" className={estilos.control}
                   value={datos.valorUnitario} onChange={cambiar('valorUnitario')} required />
            {camposInvalidos.valorUnitario && (
              <p className={estilos.errorCampo}>{camposInvalidos.valorUnitario}</p>
            )}
          </div>
        </div>

        <p className={estilos.subtotalPreview}>
          Subtotal: <span className="cifra">{pesos(subtotal)}</span>
          <span className={estilos.ayuda}> — lo calcula el servidor al guardar</span>
        </p>

        <div className={estilos.accionesFormulario}>
          <button type="button" className={estilos.botonSecundario} onClick={onCerrar}>
            Cancelar
          </button>
          <button type="submit" className={estilos.botonPrimario} disabled={guardando}>
            {guardando ? 'Guardando…' : 'Guardar'}
          </button>
        </div>
      </form>
    </Modal>
  );
}

/* ========================================================================== */

/** Plan de pago: anticipo y cuotas. */
function PlanModal({ idPresupuesto, presupuesto, onCerrar, onGuardado }) {
  const [anticipo, setAnticipo] = useState(presupuesto.anticipoPorcentaje ?? '30');
  const [cuotas, setCuotas] = useState(presupuesto.cantidadCuotas ?? '6');
  const [plazo, setPlazo] = useState(presupuesto.plazoEstimadoObra ?? '');
  const [error, setError] = useState(null);
  const [guardando, setGuardando] = useState(false);

  const total = Number(presupuesto.totalPresupuesto) || 0;
  const montoAnticipo = total * (Number(anticipo) || 0) / 100;
  const montoCuota = Number(cuotas) > 0 ? (total - montoAnticipo) / Number(cuotas) : 0;

  const enviar = async (evento) => {
    evento.preventDefault();
    setGuardando(true);
    setError(null);
    try {
      onGuardado(await definirPlanDePago(idPresupuesto, {
        anticipoPorcentaje: anticipo,
        cantidadCuotas: Number(cuotas),
        plazoEstimadoObra: plazo || null,
      }));
    } catch (fallo) {
      setError(fallo.camposInvalidos?.anticipoPorcentaje ?? fallo.mensaje);
    } finally {
      setGuardando(false);
    }
  };

  return (
    <Modal abierto onCerrar={onCerrar} titulo="Plan de pago">
      <form onSubmit={enviar}>
        {error && <p className={estilos.errorGeneral}>{error}</p>}

        <div className={estilos.dosColumnas}>
          <div className={estilos.campo}>
            <label className={estilos.etiqueta} htmlFor="anticipo">Anticipo (%)</label>
            <input id="anticipo" type="number" step="0.01" min="0" max="100"
                   className={estilos.control} value={anticipo}
                   onChange={(e) => setAnticipo(e.target.value)} required />
          </div>
          <div className={estilos.campo}>
            <label className={estilos.etiqueta} htmlFor="cuotas">Cantidad de cuotas</label>
            <input id="cuotas" type="number" min="0" className={estilos.control} value={cuotas}
                   onChange={(e) => setCuotas(e.target.value)} required />
          </div>
        </div>

        <div className={estilos.campo}>
          <label className={estilos.etiqueta} htmlFor="plazo">Plazo estimado de obra</label>
          <input id="plazo" className={estilos.control} maxLength={100} placeholder="6 meses"
                 value={plazo} onChange={(e) => setPlazo(e.target.value)} />
        </div>

        {/* Vista previa: el cálculo definitivo lo hace el servidor. */}
        <div className={estilos.previa}>
          <div>
            <span className="kicker">Anticipo</span>
            <div className={`cifra ${estilos.planCifra}`}>{pesos(montoAnticipo)}</div>
          </div>
          <div>
            <span className="kicker">Cada cuota</span>
            <div className={`cifra ${estilos.planCifra}`}>
              {Number(cuotas) > 0 ? pesos(montoCuota) : '—'}
            </div>
          </div>
        </div>

        <div className={estilos.accionesFormulario}>
          <button type="button" className={estilos.botonSecundario} onClick={onCerrar}>
            Cancelar
          </button>
          <button type="submit" className={estilos.botonPrimario} disabled={guardando}>
            {guardando ? 'Guardando…' : 'Guardar'}
          </button>
        </div>
      </form>
    </Modal>
  );
}

/* ========================================================================== */

/** Genera una versión nueva a partir de ésta, copiando sus ítems. */
function DuplicarModal({ idPresupuesto, onCerrar }) {
  const [tipo, setTipo] = useState('Definitivo');
  const [error, setError] = useState(null);
  const [guardando, setGuardando] = useState(false);
  const [creado, setCreado] = useState(null);

  const enviar = async (evento) => {
    evento.preventDefault();
    setGuardando(true);
    setError(null);
    try {
      setCreado(await duplicarPresupuesto(idPresupuesto, {
        tipoPresupuesto: tipo, idObraDestino: null,
      }));
    } catch (fallo) {
      setError(fallo.mensaje);
    } finally {
      setGuardando(false);
    }
  };

  return (
    <Modal abierto onCerrar={onCerrar} titulo="Usar como base">
      {creado ? (
        <div>
          <p>
            Se creó el presupuesto <strong>{creado.tipoPresupuesto} v{creado.version}</strong> con
            una copia de los ítems. El original quedó intacto.
          </p>
          <p><Link to={`/presupuestos/${creado.idPresupuesto}`} onClick={onCerrar}>Abrirlo →</Link></p>
        </div>
      ) : (
        <form onSubmit={enviar}>
          {error && <p className={estilos.errorGeneral}>{error}</p>}

          <p className={estilos.ayuda}>
            Se crea un presupuesto nuevo con una copia de los ítems de éste. El
            presupuesto actual no se modifica: quedan los dos vinculados.
          </p>

          <div className={estilos.campo}>
            <label className={estilos.etiqueta} htmlFor="tipoDestino">Nueva instancia</label>
            <select id="tipoDestino" className={estilos.control} value={tipo}
                    onChange={(e) => setTipo(e.target.value)}>
              <option value="Anteproyecto">Anteproyecto</option>
              <option value="Definitivo">Definitivo</option>
              <option value="Adicional">Adicional</option>
            </select>
          </div>

          <div className={estilos.accionesFormulario}>
            <button type="button" className={estilos.botonSecundario} onClick={onCerrar}>
              Cancelar
            </button>
            <button type="submit" className={estilos.botonPrimario} disabled={guardando}>
              {guardando ? 'Generando…' : 'Generar'}
            </button>
          </div>
        </form>
      )}
    </Modal>
  );
}

/**
 * La clase del boton de un rubro.
 *
 * Marca cual esta abierto para que, con la planilla desplegada mas abajo, se
 * pueda ver de un vistazo a que rubro corresponde.
 */
function claseDeRubro(rubro, abierto, estilos) {
  if (abierto?.idRubro === rubro.idRubro) return estilos.rubroAbierto;
  return rubro.esManoDeObra ? estilos.rubroManoDeObra : estilos.rubroBoton;
}

function claseDeEstado(estado) {
  if (estado === 'Aprobado') return estilos.estadoAprobado;
  if (estado === 'Enviado') return estilos.estadoEnviado;
  if (estado === 'Rechazado') return estilos.estadoRechazado;
  return estilos.estadoBorrador;
}
