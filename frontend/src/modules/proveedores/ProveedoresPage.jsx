import { useEffect, useState } from 'react';
import Blueprint from '../../components/ui/Blueprint';
import Modal from '../../components/ui/Modal';
import { listarMaterialesDisponibles } from '../materiales/materialesApi';
import Comparador from './Comparador';
import FichaProveedor from './FichaProveedor';
import {
  actualizarProveedor, cambiarEstadoProveedor, crearProveedor,
  listarProveedores, listarZonas,
} from './proveedoresApi';
import estilos from './Proveedores.module.css';

/**
 * Listado de proveedores.
 *
 * Reemplaza lo que hoy vive en la memoria del dueño y en el historial de
 * WhatsApp: a quién pedirle en cada zona, a cuánto cotizó cada uno y cómo se
 * comportó.
 *
 * La zona es el dato que más pesa: un corralón que no llega a la obra no sirve
 * por más barato que sea. Por eso está en el listado y en el filtro.
 */
export default function ProveedoresPage() {
  const [proveedores, setProveedores] = useState([]);
  const [zonas, setZonas] = useState([]);
  const [materiales, setMateriales] = useState([]);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState(null);

  const [busqueda, setBusqueda] = useState('');
  const [zona, setZona] = useState('');
  const [estado, setEstado] = useState('');
  const [recarga, setRecarga] = useState(0);

  const [formulario, setFormulario] = useState(null);
  const [fichaAbierta, setFichaAbierta] = useState(null);
  const [comparadorAbierto, setComparadorAbierto] = useState(false);

  useEffect(() => {
    let vigente = true;
    const temporizador = setTimeout(() => {
      (async () => {
        try {
          const datos = await listarProveedores({ busqueda, zona, estado });
          if (vigente) { setProveedores(datos); setError(null); }
        } catch (fallo) {
          if (vigente) setError(fallo.mensaje);
        } finally {
          if (vigente) setCargando(false);
        }
      })();
    }, 300);
    return () => { vigente = false; clearTimeout(temporizador); };
  }, [busqueda, zona, estado, recarga]);

  useEffect(() => {
    let vigente = true;
    listarZonas().then((z) => { if (vigente) setZonas(z); }).catch(() => {});
    listarMaterialesDisponibles()
      .then((m) => { if (vigente) setMateriales(m); })
      .catch(() => {});
    return () => { vigente = false; };
  }, [recarga]);

  const recargar = () => { setCargando(true); setRecarga((n) => n + 1); };

  const alternarEstado = async (proveedor) => {
    try {
      await cambiarEstadoProveedor(
        proveedor.idProveedor, proveedor.estado === 'Activo' ? 'Inactivo' : 'Activo');
      recargar();
    } catch (fallo) {
      setError(fallo.mensaje);
    }
  };

  const hayFiltros = busqueda || zona || estado;

  return (
    <>
      <div className={estilos.cabecera}>
        <div>
          <span className="kicker kicker-acento">Módulo</span>
          <h2 className={estilos.titulo}>Proveedores</h2>
          <p className={estilos.bajada}>
            Corralones por zona, con su historial de precios y de comportamiento.
          </p>
        </div>

        <div className={estilos.herramientas}>
          <input type="search" className={estilos.buscador} placeholder="Buscar proveedor"
                 value={busqueda} onChange={(e) => setBusqueda(e.target.value)}
                 aria-label="Buscar proveedor" />
          <select className={estilos.filtro} value={zona}
                  onChange={(e) => setZona(e.target.value)} aria-label="Filtrar por zona">
            <option value="">Toda zona</option>
            {zonas.map((z) => <option key={z} value={z}>{z}</option>)}
          </select>
          <select className={estilos.filtro} value={estado}
                  onChange={(e) => setEstado(e.target.value)} aria-label="Filtrar por estado">
            <option value="">Todo estado</option>
            <option value="Activo">Activos</option>
            <option value="Inactivo">Inactivos</option>
          </select>
          <button type="button" className={estilos.botonSecundario}
                  onClick={() => setComparadorAbierto(true)} disabled={materiales.length === 0}>
            Comparar precios
          </button>
          <button type="button" className={estilos.botonPrimario}
                  onClick={() => setFormulario({ modo: 'alta' })}>
            Nuevo proveedor
          </button>
        </div>
      </div>

      {error && <p className={estilos.errorGeneral}>{error}</p>}

      <Blueprint className={estilos.bloque}>
        {cargando && <p className={estilos.aviso}>Consultando…</p>}

        {!cargando && !error && proveedores.length === 0 && (
          <div className={estilos.vacio}>
            <p className={estilos.vacioTitulo}>
              {hayFiltros ? 'Ningún proveedor coincide' : 'Todavía no hay proveedores'}
            </p>
            <p className={estilos.vacioTexto}>
              {hayFiltros
                ? 'Probá con otro nombre o quitá los filtros.'
                : 'Cargá los corralones con los que trabaja la empresa, con su zona de cobertura.'}
            </p>
          </div>
        )}

        {!cargando && proveedores.length > 0 && (
          <div className="scroll-x">
            <table className="table">
              <thead>
                <tr>
                  <th>Proveedor</th>
                  <th>Zona</th>
                  <th>Contacto</th>
                  <th style={{ textAlign: 'right' }}>Cotiz.</th>
                  <th style={{ textAlign: 'right' }}>Obs.</th>
                  <th>Estado</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {proveedores.map((p) => (
                  <tr key={p.idProveedor}>
                    <td className={estilos.nombre}>{p.nombreProveedor}</td>
                    <td className={estilos.zona}>{p.zonaCobertura}</td>
                    <td className={estilos.dato}>
                      {p.telefonoContacto || '—'}
                      {p.emailContacto && (
                        <span className={estilos.email}>{p.emailContacto}</span>
                      )}
                    </td>
                    <td className={`cifra ${estilos.numero}`}>{p.cantidadCotizaciones}</td>
                    <td className={`cifra ${estilos.numero}`}>
                      {/* Las observaciones son señal de comportamiento: si hay
                          varias, conviene que salten a la vista. */}
                      <span className={p.cantidadObservaciones > 0 ? estilos.conObservaciones : ''}>
                        {p.cantidadObservaciones}
                      </span>
                    </td>
                    <td>
                      <span className={p.estado === 'Activo' ? estilos.activo : estilos.inactivo}>
                        {p.estado}
                      </span>
                    </td>
                    <td className={estilos.acciones}>
                      <button type="button" className={estilos.accion}
                              onClick={() => setFichaAbierta(p)}>
                        Ver ficha
                      </button>
                      <button type="button" className={estilos.accion}
                              onClick={() => setFormulario({ modo: 'edicion', proveedor: p })}>
                        Editar
                      </button>
                      <button type="button" className={estilos.accion}
                              onClick={() => alternarEstado(p)}>
                        {p.estado === 'Activo' ? 'Desactivar' : 'Activar'}
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Blueprint>

      {formulario && (
        <ProveedorFormulario
          proveedor={formulario.proveedor}
          onCerrar={() => setFormulario(null)}
          onGuardado={() => { setFormulario(null); recargar(); }}
        />
      )}

      {fichaAbierta && (
        <FichaProveedor
          proveedor={fichaAbierta}
          materiales={materiales}
          onCerrar={() => setFichaAbierta(null)}
          onCambio={recargar}
        />
      )}

      {comparadorAbierto && (
        <Comparador materiales={materiales} onCerrar={() => setComparadorAbierto(false)} />
      )}
    </>
  );
}

/* ========================================================================== */

/** Alta y edición de un proveedor. */
function ProveedorFormulario({ proveedor, onCerrar, onGuardado }) {
  const editando = Boolean(proveedor);

  const [datos, setDatos] = useState({
    nombreProveedor: proveedor?.nombreProveedor ?? '',
    zonaCobertura: proveedor?.zonaCobertura ?? '',
    telefonoContacto: proveedor?.telefonoContacto ?? '',
    emailContacto: proveedor?.emailContacto ?? '',
  });
  const [camposInvalidos, setCamposInvalidos] = useState({});
  const [error, setError] = useState(null);
  const [guardando, setGuardando] = useState(false);

  const cambiar = (campo) => (e) => {
    setDatos((previo) => ({ ...previo, [campo]: e.target.value }));
    setCamposInvalidos((previo) => ({ ...previo, [campo]: undefined }));
  };

  const enviar = async (evento) => {
    evento.preventDefault();
    setGuardando(true);
    setCamposInvalidos({});
    setError(null);

    try {
      onGuardado(editando
        ? await actualizarProveedor(proveedor.idProveedor, datos)
        : await crearProveedor(datos));
    } catch (fallo) {
      if (fallo.camposInvalidos) setCamposInvalidos(fallo.camposInvalidos);
      else setError(fallo.mensaje);
    } finally {
      setGuardando(false);
    }
  };

  return (
    <Modal abierto onCerrar={onCerrar} titulo={editando ? 'Editar proveedor' : 'Nuevo proveedor'}>
      <form onSubmit={enviar}>
        {error && <p className={estilos.errorGeneral}>{error}</p>}

        <div className={estilos.campo}>
          <label className={estilos.etiqueta} htmlFor="nombreProveedor">
            Nombre <span className={estilos.obligatorio}>*</span>
          </label>
          <input id="nombreProveedor" className={estilos.control} maxLength={150}
                 placeholder="Corralón San Martín" value={datos.nombreProveedor}
                 onChange={cambiar('nombreProveedor')} required autoFocus />
          {camposInvalidos.nombreProveedor && (
            <p className={estilos.errorCampo}>{camposInvalidos.nombreProveedor}</p>
          )}
        </div>

        <div className={estilos.campo}>
          <label className={estilos.etiqueta} htmlFor="zonaCobertura">
            Zona de cobertura <span className={estilos.obligatorio}>*</span>
          </label>
          <input id="zonaCobertura" className={estilos.control} maxLength={100}
                 placeholder="Zona Norte, CABA" value={datos.zonaCobertura}
                 onChange={cambiar('zonaCobertura')} required />
          <p className={estilos.ayuda}>
            Es el criterio principal para elegir a quién pedirle: un corralón que
            no llega a la obra no sirve por más barato que sea.
          </p>
          {camposInvalidos.zonaCobertura && (
            <p className={estilos.errorCampo}>{camposInvalidos.zonaCobertura}</p>
          )}
        </div>

        <div className={estilos.dosColumnas}>
          <div className={estilos.campo}>
            <label className={estilos.etiqueta} htmlFor="telefonoContacto">Teléfono</label>
            <input id="telefonoContacto" className={estilos.control} maxLength={30}
                   value={datos.telefonoContacto} onChange={cambiar('telefonoContacto')} />
          </div>
          <div className={estilos.campo}>
            <label className={estilos.etiqueta} htmlFor="emailContacto">Correo</label>
            <input id="emailContacto" type="email" className={estilos.control} maxLength={100}
                   value={datos.emailContacto} onChange={cambiar('emailContacto')} />
            {camposInvalidos.emailContacto && (
              <p className={estilos.errorCampo}>{camposInvalidos.emailContacto}</p>
            )}
          </div>
        </div>

        <div className={estilos.accionesFormulario}>
          <button type="button" className={estilos.botonSecundario} onClick={onCerrar}>
            Cancelar
          </button>
          <button type="submit" className={estilos.botonPrimario} disabled={guardando}>
            {guardando ? 'Guardando…' : (editando ? 'Guardar' : 'Crear')}
          </button>
        </div>
      </form>
    </Modal>
  );
}
