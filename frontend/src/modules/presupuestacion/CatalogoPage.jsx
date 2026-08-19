import { useCallback, useEffect, useState } from 'react';
import Blueprint from '../../components/ui/Blueprint';
import Modal from '../../components/ui/Modal';
import {
  cambiarEstadoRubro, cambiarEstadoSubrubro, crearRubro, crearSubrubro,
  listarRubros, renombrarRubro, renombrarSubrubro,
} from './catalogoApi';
import estilos from './Catalogo.module.css';

/**
 * Catálogo de rubros y subrubros.
 *
 * Es el paso 1 del circuito de Presupuestación, y el informe aclara que no se
 * repite por cada obra: el catálogo se mantiene cuando hace falta y después se
 * reutiliza en todos los presupuestos.
 *
 * Es además la clasificación compartida del sistema: Gastos clasifica cada
 * gasto por rubro y Materiales asigna cada material a uno. Que exista una sola
 * lista es lo que permite comparar lo gastado contra lo presupuestado — si cada
 * módulo tuviera la suya, los nombres no coincidirían.
 *
 * Se muestra como un árbol de dos niveles porque es como se lo piensa:
 * "Albañilería, y adentro Demolición y Contrapisos".
 */
export default function CatalogoPage() {
  const [rubros, setRubros] = useState([]);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState(null);

  const [busqueda, setBusqueda] = useState('');
  const [estado, setEstado] = useState('');

  // { tipo: 'rubro' | 'subrubro', modo: 'alta' | 'edicion', ... }
  const [formulario, setFormulario] = useState(null);

  const cargar = useCallback(async (filtros) => {
    setCargando(true);
    setError(null);
    try {
      setRubros(await listarRubros(filtros));
    } catch (fallo) {
      setError(fallo.mensaje);
    } finally {
      setCargando(false);
    }
  }, []);

  useEffect(() => {
    const temporizador = setTimeout(() => cargar({ busqueda, estado }), 300);
    return () => clearTimeout(temporizador);
  }, [busqueda, estado, cargar]);

  const recargar = () => cargar({ busqueda, estado });

  const alternarRubro = async (rubro) => {
    try {
      await cambiarEstadoRubro(rubro.idRubro, rubro.estado === 'Activo' ? 'Inactivo' : 'Activo');
      recargar();
    } catch (fallo) {
      setError(fallo.mensaje);
    }
  };

  const alternarSubrubro = async (subrubro) => {
    try {
      await cambiarEstadoSubrubro(
        subrubro.idSubrubro,
        subrubro.estado === 'Activo' ? 'Inactivo' : 'Activo',
      );
      recargar();
    } catch (fallo) {
      // Acá llega, por ejemplo, el 409 de intentar activar un subrubro cuyo
      // rubro está inactivo, con el texto que escribió el backend.
      setError(fallo.mensaje);
    }
  };

  const hayFiltros = busqueda || estado;

  return (
    <>
      <div className={estilos.cabecera}>
        <div>
          <span className="kicker kicker-acento">Presupuestación</span>
          <h2 className={estilos.titulo}>Catálogo de rubros</h2>
          <p className={estilos.bajada}>
            Clasificación con la que se arman los presupuestos y se ordenan los gastos.
          </p>
        </div>

        <div className={estilos.herramientas}>
          <input
            type="search"
            className={estilos.buscador}
            placeholder="Buscar rubro"
            value={busqueda}
            onChange={(e) => setBusqueda(e.target.value)}
            aria-label="Buscar rubro"
          />
          <select
            className={estilos.filtro}
            value={estado}
            onChange={(e) => setEstado(e.target.value)}
            aria-label="Filtrar por estado"
          >
            <option value="">Todo estado</option>
            <option value="Activo">Activos</option>
            <option value="Inactivo">Inactivos</option>
          </select>
          <button
            type="button"
            className={estilos.botonPrimario}
            onClick={() => setFormulario({ tipo: 'rubro', modo: 'alta' })}
          >
            Nuevo rubro
          </button>
        </div>
      </div>

      {error && <p className={estilos.errorGeneral}>{error}</p>}
      {cargando && <p className={estilos.aviso}>Consultando…</p>}

      {!cargando && !error && rubros.length === 0 && (
        <Blueprint className={estilos.vacio}>
          <p className={estilos.vacioTitulo}>
            {hayFiltros ? 'Ningún rubro coincide con la búsqueda' : 'El catálogo está vacío'}
          </p>
          <p className={estilos.vacioTexto}>
            {hayFiltros
              ? 'Probá con otro nombre o quitá los filtros.'
              : 'Cargá los rubros con los que Granica arma sus presupuestos: Albañilería, Plomería, Electricidad…'}
          </p>
        </Blueprint>
      )}

      <div className={estilos.lista}>
        {rubros.map((rubro) => (
          <Blueprint key={rubro.idRubro} className={estilos.rubro}>
            <div className={estilos.rubroCabecera}>
              <div className={estilos.rubroTitulo}>
                <h3 className={estilos.rubroNombre}>{rubro.nombreRubro}</h3>
                <span className={rubro.estado === 'Activo' ? estilos.activo : estilos.inactivo}>
                  {rubro.estado}
                </span>
                <span className={estilos.conteo}>
                  {rubro.subrubros.length === 0
                    ? 'sin subrubros'
                    : `${rubro.subrubros.length} subrubro${rubro.subrubros.length > 1 ? 's' : ''}`}
                </span>
              </div>

              <div className={estilos.acciones}>
                <button
                  type="button"
                  className={estilos.accion}
                  onClick={() => setFormulario({
                    tipo: 'subrubro', modo: 'alta', idRubro: rubro.idRubro,
                    nombreRubro: rubro.nombreRubro,
                  })}
                >
                  Agregar subrubro
                </button>
                <button
                  type="button"
                  className={estilos.accion}
                  onClick={() => setFormulario({
                    tipo: 'rubro', modo: 'edicion',
                    id: rubro.idRubro, valor: rubro.nombreRubro,
                  })}
                >
                  Renombrar
                </button>
                <button type="button" className={estilos.accion} onClick={() => alternarRubro(rubro)}>
                  {rubro.estado === 'Activo' ? 'Desactivar' : 'Activar'}
                </button>
              </div>
            </div>

            {rubro.subrubros.length > 0 && (
              <ul className={estilos.subrubros}>
                {rubro.subrubros.map((subrubro) => (
                  <li key={subrubro.idSubrubro} className={estilos.subrubro}>
                    <span className={estilos.subrubroNombre}>{subrubro.nombreSubrubro}</span>
                    <span className={subrubro.estado === 'Activo' ? estilos.activo : estilos.inactivo}>
                      {subrubro.estado}
                    </span>
                    <span className={estilos.subrubroAcciones}>
                      <button
                        type="button"
                        className={estilos.accion}
                        onClick={() => setFormulario({
                          tipo: 'subrubro', modo: 'edicion',
                          id: subrubro.idSubrubro, valor: subrubro.nombreSubrubro,
                        })}
                      >
                        Renombrar
                      </button>
                      <button
                        type="button"
                        className={estilos.accion}
                        onClick={() => alternarSubrubro(subrubro)}
                      >
                        {subrubro.estado === 'Activo' ? 'Desactivar' : 'Activar'}
                      </button>
                    </span>
                  </li>
                ))}
              </ul>
            )}
          </Blueprint>
        ))}
      </div>

      {formulario && (
        <FormularioCatalogo
          formulario={formulario}
          onCerrar={() => setFormulario(null)}
          onGuardado={() => { setFormulario(null); recargar(); }}
        />
      )}
    </>
  );
}

/**
 * Un único formulario para las cuatro operaciones del catálogo: alta y
 * renombrado, de rubro y de subrubro. Todas piden lo mismo —un nombre— así que
 * repetir cuatro modales casi idénticos solo daría más lugares donde
 * equivocarse.
 */
function FormularioCatalogo({ formulario, onCerrar, onGuardado }) {
  const { tipo, modo } = formulario;
  const esRubro = tipo === 'rubro';
  const editando = modo === 'edicion';

  const [nombre, setNombre] = useState(formulario.valor ?? '');
  const [error, setError] = useState(null);
  const [guardando, setGuardando] = useState(false);

  const titulo = editando
    ? `Renombrar ${esRubro ? 'rubro' : 'subrubro'}`
    : (esRubro ? 'Nuevo rubro' : `Nuevo subrubro en ${formulario.nombreRubro}`);

  const enviar = async (evento) => {
    evento.preventDefault();
    setGuardando(true);
    setError(null);

    try {
      if (esRubro && editando) await renombrarRubro(formulario.id, nombre);
      else if (esRubro) await crearRubro(nombre);
      else if (editando) await renombrarSubrubro(formulario.id, nombre);
      else await crearSubrubro(formulario.idRubro, nombre);
      onGuardado();
    } catch (fallo) {
      // Un 409 trae el texto de la regla ("Ya existe un rubro llamado…") y un
      // 400 trae el detalle por campo. Los dos se muestran igual.
      setError(fallo.camposInvalidos?.nombreRubro
        ?? fallo.camposInvalidos?.nombreSubrubro
        ?? fallo.mensaje);
    } finally {
      setGuardando(false);
    }
  };

  return (
    <Modal abierto onCerrar={onCerrar} titulo={titulo}>
      <form onSubmit={enviar}>
        {error && <p className={estilos.errorGeneral}>{error}</p>}

        <div className={estilos.campo}>
          <label className={estilos.etiqueta} htmlFor="nombre">
            Nombre <span className={estilos.obligatorio}>*</span>
          </label>
          <input
            id="nombre"
            className={estilos.control}
            value={nombre}
            onChange={(e) => setNombre(e.target.value)}
            maxLength={100}
            required
            autoFocus
            placeholder={esRubro ? 'Albañilería' : 'Demolición'}
          />
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
