import { useEffect, useState } from 'react';
import Modal from '../../components/ui/Modal';
import {
  fechaCorta, historialDeCotizaciones, observacionesDe, pesos,
  registrarCotizacion, registrarObservacion,
} from './proveedoresApi';
import estilos from './Proveedores.module.css';

/**
 * Ficha de un proveedor: historial de cotizaciones y observaciones, y las dos
 * acciones que las generan.
 *
 * PENDIENTE: el informe pide mostrar además el historial de pedidos realizados.
 * Eso llega con el módulo Compras.
 */
export default function FichaProveedor({ proveedor, materiales, onCerrar, onCambio }) {
  const [pestana, setPestana] = useState('cotizaciones');
  const [cotizaciones, setCotizaciones] = useState([]);
  const [observaciones, setObservaciones] = useState([]);
  const [error, setError] = useState(null);
  const [recarga, setRecarga] = useState(0);

  useEffect(() => {
    let vigente = true;
    (async () => {
      try {
        const [c, o] = await Promise.all([
          historialDeCotizaciones(proveedor.idProveedor),
          observacionesDe(proveedor.idProveedor),
        ]);
        if (vigente) { setCotizaciones(c); setObservaciones(o); setError(null); }
      } catch (fallo) {
        if (vigente) setError(fallo.mensaje);
      }
    })();
    return () => { vigente = false; };
  }, [proveedor.idProveedor, recarga]);

  const recargar = () => { setRecarga((n) => n + 1); onCambio(); };

  return (
    <Modal abierto onCerrar={onCerrar} titulo={proveedor.nombreProveedor}>
      <p className={estilos.fichaEncabezado}>
        {proveedor.zonaCobertura}
        {proveedor.telefonoContacto && ` · ${proveedor.telefonoContacto}`}
        {proveedor.emailContacto && ` · ${proveedor.emailContacto}`}
      </p>

      {error && <p className={estilos.errorGeneral}>{error}</p>}

      <div className={estilos.pestanas} role="tablist">
        <button type="button" role="tab" aria-selected={pestana === 'cotizaciones'}
                className={`${estilos.pestana} ${pestana === 'cotizaciones' ? estilos.pestanaActiva : ''}`.trim()}
                onClick={() => setPestana('cotizaciones')}>
          Cotizaciones ({cotizaciones.length})
        </button>
        <button type="button" role="tab" aria-selected={pestana === 'observaciones'}
                className={`${estilos.pestana} ${pestana === 'observaciones' ? estilos.pestanaActiva : ''}`.trim()}
                onClick={() => setPestana('observaciones')}>
          Comportamiento ({observaciones.length})
        </button>
      </div>

      {pestana === 'cotizaciones'
        ? (
          <Cotizaciones
            proveedor={proveedor}
            materiales={materiales}
            cotizaciones={cotizaciones}
            onRegistrada={recargar}
          />
        )
        : (
          <Observaciones
            proveedor={proveedor}
            observaciones={observaciones}
            onRegistrada={recargar}
          />
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

function Cotizaciones({ proveedor, materiales, cotizaciones, onRegistrada }) {
  const [idMaterial, setIdMaterial] = useState('');
  const [precio, setPrecio] = useState('');
  const [error, setError] = useState(null);
  const [guardando, setGuardando] = useState(false);

  const activo = proveedor.estado === 'Activo';

  const enviar = async (evento) => {
    evento.preventDefault();
    setGuardando(true);
    setError(null);
    try {
      await registrarCotizacion(proveedor.idProveedor, {
        idMaterial: Number(idMaterial), precioCotizado: precio,
      });
      setIdMaterial(''); setPrecio('');
      onRegistrada();
    } catch (fallo) {
      setError(fallo.camposInvalidos?.precioCotizado ?? fallo.mensaje);
    } finally {
      setGuardando(false);
    }
  };

  return (
    <>
      {activo && (
        <form onSubmit={enviar} className={estilos.formularioEnLinea}>
          <select className={estilos.control} value={idMaterial}
                  onChange={(e) => setIdMaterial(e.target.value)} required
                  aria-label="Material">
            <option value="">Material…</option>
            {materiales.map((m) => (
              <option key={m.idMaterial} value={m.idMaterial}>{m.nombreMaterial}</option>
            ))}
          </select>
          <input type="number" step="0.01" min="0" className={estilos.control}
                 placeholder="Precio" value={precio}
                 onChange={(e) => setPrecio(e.target.value)} required
                 aria-label="Precio cotizado" />
          <button type="submit" className={estilos.botonPrimario} disabled={guardando}>
            {guardando ? '…' : 'Registrar'}
          </button>
        </form>
      )}

      {error && <p className={estilos.errorGeneral}>{error}</p>}

      {/* Cada precio informado queda como registro propio: no se corrige el
          anterior. Así se ve la evolución. */}
      {cotizaciones.length === 0 ? (
        <p className={estilos.aviso}>Todavía no se registraron cotizaciones.</p>
      ) : (
        <ul className={estilos.historial}>
          {cotizaciones.map((c) => (
            <li key={c.idCotizacion}>
              <span className={estilos.historialNombre}>
                {c.nombreMaterial}
                <span className={estilos.historialUnidad}>por {c.unidadMedida}</span>
              </span>
              <span className={`cifra ${estilos.precio}`}>{pesos(c.precioCotizado)}</span>
              <span className={estilos.antiguedad}>{fechaCorta(c.fechaCotizacion)}</span>
            </li>
          ))}
        </ul>
      )}
    </>
  );
}

/* ========================================================================== */

function Observaciones({ proveedor, observaciones, onRegistrada }) {
  const [descripcion, setDescripcion] = useState('');
  const [error, setError] = useState(null);
  const [guardando, setGuardando] = useState(false);

  const enviar = async (evento) => {
    evento.preventDefault();
    setGuardando(true);
    setError(null);
    try {
      // TODO: al desarrollar Compras, permitir elegir el pedido que la originó.
      await registrarObservacion(proveedor.idProveedor, { descripcion, idPedido: null });
      setDescripcion('');
      onRegistrada();
    } catch (fallo) {
      setError(fallo.camposInvalidos?.descripcion ?? fallo.mensaje);
    } finally {
      setGuardando(false);
    }
  };

  return (
    <>
      <form onSubmit={enviar} className={estilos.formularioEnLinea}>
        <input className={estilos.control} maxLength={300}
               placeholder="Demoró una semana la entrega de hierro"
               value={descripcion} onChange={(e) => setDescripcion(e.target.value)}
               required aria-label="Observación" />
        <button type="submit" className={estilos.botonPrimario} disabled={guardando}>
          {guardando ? '…' : 'Registrar'}
        </button>
      </form>

      {error && <p className={estilos.errorGeneral}>{error}</p>}

      {observaciones.length === 0 ? (
        <p className={estilos.aviso}>
          Sin observaciones registradas. Acá va lo que hoy queda solo en la
          memoria: demoras, faltantes, material entregado roto.
        </p>
      ) : (
        <ul className={estilos.historial}>
          {observaciones.map((o) => (
            <li key={o.idObservacion} className={estilos.observacion}>
              <span className={estilos.observacionTexto}>{o.descripcion}</span>
              <span className={estilos.antiguedad}>
                {fechaCorta(o.fecha)}
                {o.idPedido && ` · pedido #${o.idPedido}`}
              </span>
            </li>
          ))}
        </ul>
      )}
    </>
  );
}
