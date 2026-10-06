import { useEffect, useState } from 'react';
import Modal from '../../components/ui/Modal';
import { aprobarPedido, obtenerPedido, pesos, preciosSugeridos } from './pedidosApi';
import estilos from './Compras.module.css';

/** El IVA que se le suma a los precios del corralón, que se cargan sin IVA. */
const TASA_IVA = 0.21;

/**
 * Aprobación del pedido: el dueño carga los precios que cotizó el corralón.
 *
 * El corralón ya viene elegido desde que se cargó el pedido; se puede cambiar
 * si al final se le compra a otro. Se precargan los precios de su última
 * cotización como referencia, y el dueño los corrige con lo que le contestó.
 *
 * Los precios van SIN IVA. Al aprobar quedan guardados como cotización del
 * corralón para cada material, y la compra se carga como gasto de la obra.
 */
export default function AprobarPedido({ pedido, proveedores, onCerrar, onAprobado }) {
  const [detalle, setDetalle] = useState(null);
  const [idProveedor, setIdProveedor] = useState(
    pedido.idProveedor ? String(pedido.idProveedor) : '');
  const [precios, setPrecios] = useState({});
  const [sugeridos, setSugeridos] = useState({});
  const [error, setError] = useState(null);
  const [guardando, setGuardando] = useState(false);

  // El listado no trae las líneas del pedido; el detalle sí.
  useEffect(() => {
    let vigente = true;
    obtenerPedido(pedido.idPedido)
      .then((d) => { if (vigente) setDetalle(d); })
      .catch((f) => { if (vigente) setError(f.mensaje); });
    return () => { vigente = false; };
  }, [pedido.idPedido]);

  // Al cambiar de proveedor se vuelven a pedir los precios: cada corralón
  // tiene los suyos y arrastrar los del anterior sería un error caro.
  useEffect(() => {
    if (!idProveedor) return undefined;

    let vigente = true;
    (async () => {
      try {
        const datos = await preciosSugeridos(pedido.idPedido, idProveedor);
        if (!vigente) return;
        setSugeridos(datos);
        setPrecios((previos) => {
          const nuevos = { ...previos };
          Object.entries(datos).forEach(([idMaterial, precio]) => {
            nuevos[idMaterial] = String(precio);
          });
          return nuevos;
        });
      } catch {
        if (vigente) setSugeridos({});
      }
    })();
    return () => { vigente = false; };
  }, [idProveedor, pedido.idPedido]);

  /**
   * Guarda el precio que el dueño escribe para un material del pedido.
   */
  const cambiarPrecio = (idMaterial) => (e) => {
    const valor = e.target.value;
    setPrecios((previos) => ({ ...previos, [idMaterial]: valor }));
  };

  // Sin proveedor elegido no hay sugerencias que mostrar. Se deriva en lugar
  // de vaciar el estado desde el efecto: así no hace falta un render extra
  // solo para limpiar algo que igual no se va a dibujar.
  const sugeridosVisibles = idProveedor ? sugeridos : {};

  const lineas = detalle?.materiales ?? [];

  // Totales en vivo, para que el dueño vea cuánto está aprobando antes de
  // hacerlo. Los precios son sin IVA; el total suma el 21%.
  const subtotal = lineas.reduce((suma, l) => {
    const precio = Number(precios[l.idMaterial]) || 0;
    return suma + precio * Number(l.cantidad);
  }, 0);
  const iva = subtotal * TASA_IVA;

  /**
   * Aprueba el pedido con el proveedor elegido y los precios confirmados de
   * cada material.
   */
  const enviar = async (evento) => {
    evento.preventDefault();
    setGuardando(true);
    setError(null);
    try {
      await aprobarPedido(pedido.idPedido, {
        idProveedor: Number(idProveedor),
        precios: lineas.map((l) => ({
          idMaterial: l.idMaterial,
          precioUnitario: precios[l.idMaterial],
        })),
      });
      onAprobado();
    } catch (fallo) {
      setError(fallo.mensaje);
    } finally {
      setGuardando(false);
    }
  };

  return (
    <Modal abierto onCerrar={onCerrar} titulo="Aprobar con la cotización">
      <form onSubmit={enviar}>
        <p className={estilos.confirmacion}>
          Pedido de <b>{pedido.direccionObra}</b> — {pedido.nombreCliente}
        </p>

        {error && <p className={estilos.errorGeneral}>{error}</p>}

        <div className={estilos.campo}>
          <label className={estilos.etiqueta} htmlFor="idProveedor">
            Corralón <span className={estilos.obligatorio}>*</span>
          </label>
          <select id="idProveedor" className={estilos.control} value={idProveedor}
                  onChange={(e) => setIdProveedor(e.target.value)} required>
            <option value="">Elegir corralón…</option>
            {proveedores.map((p) => (
              <option key={p.idProveedor} value={p.idProveedor}>
                {p.nombreProveedor} — {p.zonaCobertura}
              </option>
            ))}
          </select>
          <p className={estilos.ayuda}>
            El que se eligió al cargar el pedido. Cambialo solo si al final le
            comprás a otro.
          </p>
        </div>

        {!detalle && <p className={estilos.aviso}>Cargando el detalle…</p>}

        {lineas.length > 0 && (
          <div className={estilos.campo}>
            <label className={estilos.etiqueta}>
              Precios sin IVA <span className={estilos.obligatorio}>*</span>
            </label>
            <div className="scroll-x">
              <table className="table">
                <thead>
                  <tr>
                    <th>Material</th>
                    <th style={{ textAlign: 'right' }}>Cantidad</th>
                    <th style={{ width: 150 }}>Precio unit. sin IVA</th>
                    <th style={{ textAlign: 'right' }}>Subtotal</th>
                  </tr>
                </thead>
                <tbody>
                  {lineas.map((l) => {
                    const precio = Number(precios[l.idMaterial]) || 0;
                    return (
                      <tr key={l.idMaterial}>
                        <td className={estilos.dato}>
                          {l.nombreMaterial}
                          <div className={estilos.subrubroNombre}>{l.nombreRubro}</div>
                        </td>
                        <td className={`cifra ${estilos.numero}`}>
                          {l.cantidad} {l.unidadMedida}
                        </td>
                        <td>
                          <input type="number" step="0.01" min="0" className={estilos.control}
                                 value={precios[l.idMaterial] ?? ''}
                                 onChange={cambiarPrecio(l.idMaterial)} required
                                 aria-label={`Precio de ${l.nombreMaterial}`} />
                          {/* Señal de que el precio viene de una cotización real
                              y no lo escribió alguien de memoria. */}
                          {sugeridosVisibles[l.idMaterial] !== undefined && (
                            <span className={estilos.sugerido}>◆ última cotización</span>
                          )}
                        </td>
                        <td className={`cifra ${estilos.total}`}>
                          {pesos(precio * Number(l.cantidad))}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>

            <div className={estilos.totalBloque}>
              <span className={estilos.etiqueta}>Subtotal</span>
              <span className="cifra">{pesos(subtotal)}</span>
            </div>
            <div className={estilos.totalBloque}>
              <span className={estilos.etiqueta}>IVA 21%</span>
              <span className="cifra">{pesos(iva)}</span>
            </div>
            <div className={estilos.totalBloque}>
              <span className={estilos.etiqueta}>Total del pedido</span>
              <span className={`cifra ${estilos.totalCifra}`}>{pesos(subtotal + iva)}</span>
            </div>

            <p className={estilos.ayuda}>
              Al aprobar, estos precios quedan guardados como cotización de este
              corralón, y la compra se carga como gasto de la obra (sin IVA),
              agrupada por rubro.
            </p>
          </div>
        )}

        <div className={estilos.accionesFormulario}>
          <button type="button" className={estilos.botonSecundario} onClick={onCerrar}>
            Cancelar
          </button>
          <button type="submit" className={estilos.botonPrimario}
                  disabled={guardando || !detalle}>
            {guardando ? 'Aprobando…' : 'Aprobar'}
          </button>
        </div>
      </form>
    </Modal>
  );
}
