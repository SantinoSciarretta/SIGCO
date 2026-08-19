import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import Blueprint from '../../components/ui/Blueprint';
import Modal from '../../components/ui/Modal';
import { listarRubros } from '../presupuestacion/catalogoApi';
import {
  UNIDADES_SUGERIDAS, actualizarMaterial, cambiarEstadoMaterial, crearMaterial, listarMateriales,
} from './materialesApi';
import estilos from './Materiales.module.css';

/**
 * Catálogo de materiales.
 *
 * Reemplaza los nombres escritos a mano que hoy cambian de una obra a otra:
 * "cemento", "Cemento CP40", "bolsa cemento". Con una lista única, al armar un
 * pedido o un ítem de presupuesto se elige de acá y no se escribe.
 *
 * El rubro sale del catálogo de Presupuestación, no de una lista propia: es lo
 * que mantiene coherentes las dos clasificaciones.
 */
export default function MaterialesPage() {
  const [materiales, setMateriales] = useState([]);
  const [rubros, setRubros] = useState([]);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState(null);

  const [busqueda, setBusqueda] = useState('');
  const [rubro, setRubro] = useState('');
  const [estado, setEstado] = useState('');
  const [recarga, setRecarga] = useState(0);

  const [formulario, setFormulario] = useState(null);

  useEffect(() => {
    let vigente = true;
    const temporizador = setTimeout(() => {
      (async () => {
        try {
          const datos = await listarMateriales({ busqueda, rubro, estado });
          if (vigente) { setMateriales(datos); setError(null); }
        } catch (fallo) {
          if (vigente) setError(fallo.mensaje);
        } finally {
          if (vigente) setCargando(false);
        }
      })();
    }, 300);

    return () => { vigente = false; clearTimeout(temporizador); };
  }, [busqueda, rubro, estado, recarga]);

  useEffect(() => {
    let vigente = true;
    listarRubros({ estado: 'Activo' })
      .then((datos) => { if (vigente) setRubros(datos); })
      .catch(() => {});
    return () => { vigente = false; };
  }, []);

  const recargar = () => { setCargando(true); setRecarga((n) => n + 1); };

  const alternarEstado = async (material) => {
    try {
      await cambiarEstadoMaterial(
        material.idMaterial, material.estado === 'Activo' ? 'Inactivo' : 'Activo');
      recargar();
    } catch (fallo) {
      setError(fallo.mensaje);
    }
  };

  const hayFiltros = busqueda || rubro || estado;
  const sinRubros = rubros.length === 0;

  return (
    <>
      <div className={estilos.cabecera}>
        <div>
          <span className="kicker kicker-acento">Módulo</span>
          <h2 className={estilos.titulo}>Materiales</h2>
          <p className={estilos.bajada}>
            Catálogo único: de acá eligen Presupuestación y Compras.
          </p>
        </div>

        <div className={estilos.herramientas}>
          <input
            type="search"
            className={estilos.buscador}
            placeholder="Buscar material"
            value={busqueda}
            onChange={(e) => setBusqueda(e.target.value)}
            aria-label="Buscar material"
          />
          <select className={estilos.filtro} value={rubro}
                  onChange={(e) => setRubro(e.target.value)} aria-label="Filtrar por rubro">
            <option value="">Todo rubro</option>
            {rubros.map((r) => (
              <option key={r.idRubro} value={r.idRubro}>{r.nombreRubro}</option>
            ))}
          </select>
          <select className={estilos.filtro} value={estado}
                  onChange={(e) => setEstado(e.target.value)} aria-label="Filtrar por estado">
            <option value="">Todo estado</option>
            <option value="Activo">Activos</option>
            <option value="Inactivo">Inactivos</option>
          </select>
          <button type="button" className={estilos.botonPrimario}
                  onClick={() => setFormulario({ modo: 'alta' })} disabled={sinRubros}>
            Nuevo material
          </button>
        </div>
      </div>

      {error && <p className={estilos.errorGeneral}>{error}</p>}

      {/* Sin rubros no se puede cargar nada: cada material pertenece a uno. */}
      {sinRubros && (
        <Blueprint className={estilos.avisoBloque}>
          <p className={estilos.avisoTitulo}>Primero hace falta el catálogo de rubros</p>
          <p className={estilos.vacioTexto}>
            Cada material pertenece a un rubro, y el rubro sale del catálogo de
            Presupuestación. <Link to="/presupuestos/catalogo">Cargar rubros →</Link>
          </p>
        </Blueprint>
      )}

      <Blueprint className={estilos.bloque}>
        {cargando && <p className={estilos.aviso}>Consultando…</p>}

        {!cargando && !error && materiales.length === 0 && !sinRubros && (
          <div className={estilos.vacio}>
            <p className={estilos.vacioTitulo}>
              {hayFiltros ? 'Ningún material coincide' : 'El catálogo está vacío'}
            </p>
            <p className={estilos.vacioTexto}>
              {hayFiltros
                ? 'Probá con otro nombre o quitá los filtros.'
                : 'Cargá los materiales que la empresa pide habitualmente: cemento, arena, ladrillo hueco…'}
            </p>
          </div>
        )}

        {!cargando && materiales.length > 0 && (
          <div className="scroll-x">
            <table className="table">
              <thead>
                <tr>
                  <th>Material</th>
                  <th>Rubro</th>
                  <th>Unidad</th>
                  <th>Estado</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {materiales.map((m) => (
                  <tr key={m.idMaterial}>
                    <td className={estilos.nombre}>{m.nombreMaterial}</td>
                    <td className={estilos.dato}>
                      {m.nombreRubro}
                      {/* Si el rubro está inactivo, el material no se ofrece
                          aunque figure Activo. Conviene que se vea. */}
                      {m.estadoRubro === 'Inactivo' && (
                        <span className={estilos.rubroInactivo}>rubro inactivo</span>
                      )}
                    </td>
                    <td className={estilos.dato}>{m.unidadMedida}</td>
                    <td>
                      <span className={m.estado === 'Activo' ? estilos.activo : estilos.inactivo}>
                        {m.estado}
                      </span>
                    </td>
                    <td className={estilos.acciones}>
                      <button type="button" className={estilos.accion}
                              onClick={() => setFormulario({ modo: 'edicion', material: m })}>
                        Editar
                      </button>
                      <button type="button" className={estilos.accion}
                              onClick={() => alternarEstado(m)}>
                        {m.estado === 'Activo' ? 'Desactivar' : 'Activar'}
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
        <MaterialFormulario
          material={formulario.material}
          rubros={rubros}
          onCerrar={() => setFormulario(null)}
          onGuardado={() => { setFormulario(null); recargar(); }}
        />
      )}
    </>
  );
}

/* ========================================================================== */

/** Alta y edición de un material. */
function MaterialFormulario({ material, rubros, onCerrar, onGuardado }) {
  const editando = Boolean(material);

  const [datos, setDatos] = useState({
    nombreMaterial: material?.nombreMaterial ?? '',
    idRubro: material ? String(material.idRubro) : '',
    unidadMedida: material?.unidadMedida ?? '',
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

    const cuerpo = { ...datos, idRubro: Number(datos.idRubro) };

    try {
      onGuardado(editando
        ? await actualizarMaterial(material.idMaterial, cuerpo)
        : await crearMaterial(cuerpo));
    } catch (fallo) {
      // Acá llega el 409 del duplicado ("El rubro ya tiene un material
      // llamado…") y el del rubro inactivo.
      if (fallo.camposInvalidos) setCamposInvalidos(fallo.camposInvalidos);
      else setError(fallo.mensaje);
    } finally {
      setGuardando(false);
    }
  };

  return (
    <Modal abierto onCerrar={onCerrar} titulo={editando ? 'Editar material' : 'Nuevo material'}>
      <form onSubmit={enviar}>
        {error && <p className={estilos.errorGeneral}>{error}</p>}

        <div className={estilos.campo}>
          <label className={estilos.etiqueta} htmlFor="nombreMaterial">
            Nombre <span className={estilos.obligatorio}>*</span>
          </label>
          <input id="nombreMaterial" className={estilos.control} maxLength={150}
                 placeholder="Cemento CP40" value={datos.nombreMaterial}
                 onChange={cambiar('nombreMaterial')} required autoFocus />
          {camposInvalidos.nombreMaterial && (
            <p className={estilos.errorCampo}>{camposInvalidos.nombreMaterial}</p>
          )}
        </div>

        <div className={estilos.dosColumnas}>
          <div className={estilos.campo}>
            <label className={estilos.etiqueta} htmlFor="idRubro">
              Rubro <span className={estilos.obligatorio}>*</span>
            </label>
            <select id="idRubro" className={estilos.control} value={datos.idRubro}
                    onChange={cambiar('idRubro')} required>
              <option value="">Elegir…</option>
              {rubros.map((r) => (
                <option key={r.idRubro} value={r.idRubro}>{r.nombreRubro}</option>
              ))}
            </select>
            <p className={estilos.ayuda}>Del catálogo de Presupuestación</p>
          </div>

          <div className={estilos.campo}>
            <label className={estilos.etiqueta} htmlFor="unidadMedida">
              Unidad de medida <span className={estilos.obligatorio}>*</span>
            </label>
            {/* Lista abierta: el datalist sugiere las habituales pero deja
                escribir cualquier otra, como pide el informe. */}
            <input id="unidadMedida" className={estilos.control} list="unidades"
                   maxLength={20} placeholder="bolsa 50 kg" value={datos.unidadMedida}
                   onChange={cambiar('unidadMedida')} required />
            <datalist id="unidades">
              {UNIDADES_SUGERIDAS.map((u) => <option key={u} value={u} />)}
            </datalist>
            <p className={estilos.ayuda}>Define cómo se interpreta la cantidad</p>
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
