import { useCallback, useEffect, useState } from 'react';
import Blueprint from '../../components/ui/Blueprint';
import ObraFormulario from './ObraFormulario';
import CambiarEstadoObra from './CambiarEstadoObra';
import { ESTADOS, TIPOS_OBRA, estadosPosiblesDesde, listarObras } from './obrasApi';
import estilos from './Obras.module.css';

/**
 * Listado de obras.
 *
 * Es el punto de entrada al proyecto: desde acá se crea la obra, se sigue su
 * ciclo de vida y, a medida que se desarrollen los demás módulos, se accederá
 * a sus presupuestos, gastos, avance y cobros.
 *
 * Las obras en ejecución aparecen primero —el orden lo resuelve la consulta del
 * backend— porque es lo que el dueño necesita ver al entrar.
 */
export default function ObrasPage() {
  const [obras, setObras] = useState([]);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState(null);

  const [busqueda, setBusqueda] = useState('');
  const [estado, setEstado] = useState('');
  const [tipoObra, setTipoObra] = useState('');

  const [formularioAbierto, setFormularioAbierto] = useState(false);
  const [obraEnEdicion, setObraEnEdicion] = useState(null);
  const [obraCambiandoEstado, setObraCambiandoEstado] = useState(null);

  const cargar = useCallback(async (filtros) => {
    setCargando(true);
    setError(null);
    try {
      setObras(await listarObras(filtros));
    } catch (fallo) {
      setError(fallo.mensaje);
    } finally {
      setCargando(false);
    }
  }, []);

  useEffect(() => {
    const temporizador = setTimeout(() => {
      cargar({ busqueda, estado, tipoObra });
    }, 300);
    return () => clearTimeout(temporizador);
  }, [busqueda, estado, tipoObra, cargar]);

  const recargar = () => cargar({ busqueda, estado, tipoObra });

  const abrirAlta = () => {
    setObraEnEdicion(null);
    setFormularioAbierto(true);
  };

  const abrirEdicion = (obra) => {
    setObraEnEdicion(obra);
    setFormularioAbierto(true);
  };

  const hayFiltros = busqueda || estado || tipoObra;

  return (
    <>
      <div className={estilos.cabecera}>
        <div>
          <span className="kicker kicker-acento">Módulo</span>
          <h2 className={estilos.titulo}>Obras</h2>
        </div>

        <div className={estilos.herramientas}>
          <input
            type="search"
            className={estilos.buscador}
            placeholder="Buscar por dirección o cliente"
            value={busqueda}
            onChange={(e) => setBusqueda(e.target.value)}
            aria-label="Buscar obra"
          />
          <select className={estilos.filtro} value={estado}
                  onChange={(e) => setEstado(e.target.value)} aria-label="Filtrar por estado">
            <option value="">Todo estado</option>
            {ESTADOS.map((e) => <option key={e} value={e}>{e}</option>)}
          </select>
          <select className={estilos.filtro} value={tipoObra}
                  onChange={(e) => setTipoObra(e.target.value)} aria-label="Filtrar por tipo de obra">
            <option value="">Todo tipo</option>
            {TIPOS_OBRA.map((t) => <option key={t} value={t}>{t}</option>)}
          </select>
          <button type="button" className={estilos.botonPrimario} onClick={abrirAlta}>
            Nueva obra
          </button>
        </div>
      </div>

      <Blueprint className={estilos.bloque}>
        {error && <p className={estilos.errorGeneral}>{error}</p>}
        {cargando && <p className={estilos.aviso}>Consultando…</p>}

        {!cargando && !error && obras.length === 0 && (
          <div className={estilos.vacio}>
            <p className={estilos.vacioTitulo}>
              {hayFiltros ? 'Ninguna obra coincide con la búsqueda' : 'Todavía no hay obras cargadas'}
            </p>
            <p className={estilos.vacioTexto}>
              {hayFiltros
                ? 'Probá con otros filtros.'
                : 'Antes de cotizar nada, la obra se crea acá con el botón "Nueva obra".'}
            </p>
          </div>
        )}

        {!cargando && obras.length > 0 && (
          <div className="scroll-x">
            <table className="table">
              <thead>
                <tr>
                  <th>Obra</th>
                  <th>Cliente</th>
                  <th>Tipo</th>
                  <th>Fechas</th>
                  <th>Estado</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {obras.map((obra) => (
                  <tr key={obra.idObra}
                      className={obra.estado === 'En ejecución' ? estilos.filaActiva : undefined}>
                    <td>
                      <div className={estilos.direccion}>{obra.direccionObra}</div>
                      <div className={estilos.inmueble}>{obra.tipoInmueble}</div>
                    </td>
                    <td className={estilos.dato}>{obra.nombreCliente}</td>
                    <td className={estilos.dato}>{obra.tipoObra}</td>
                    <td className={estilos.fechas}>
                      {obra.fechaInicioReal
                        ? <span>Inicio {formatearFecha(obra.fechaInicioReal)}</span>
                        : <span className={estilos.sinDato}>Sin iniciar</span>}
                      {obra.fechaFinEstimada && (
                        <span className={estilos.finEstimado}>
                          Fin est. {formatearFecha(obra.fechaFinEstimada)}
                        </span>
                      )}
                    </td>
                    <td>
                      <span className={claseDeEstado(obra.estado)}>{obra.estado}</span>
                      {obra.motivoCancelacion && (
                        <span className={estilos.motivo}>{obra.motivoCancelacion}</span>
                      )}
                    </td>
                    <td className={estilos.acciones}>
                      {!obra.estado.startsWith('Finaliz') && !obra.estado.startsWith('Cancel') && (
                        <button type="button" className={estilos.accion}
                                onClick={() => abrirEdicion(obra)}>
                          Editar
                        </button>
                      )}
                      {estadosPosiblesDesde(obra.estado).length > 0 && (
                        <button type="button" className={estilos.accion}
                                onClick={() => setObraCambiandoEstado(obra)}>
                          Cambiar estado
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Blueprint>

      {/* PENDIENTE: la ficha de obra con presupuestos, gastos, avance, cobros y
          personal asignado se completa a medida que se desarrollen esos
          módulos. Hoy no habría nada que mostrar en ella. */}

      {formularioAbierto && (
        <ObraFormulario
          abierto={formularioAbierto}
          obra={obraEnEdicion}
          onCerrar={() => setFormularioAbierto(false)}
          onGuardado={() => { setFormularioAbierto(false); recargar(); }}
        />
      )}

      {obraCambiandoEstado && (
        <CambiarEstadoObra
          obra={obraCambiandoEstado}
          onCerrar={() => setObraCambiandoEstado(null)}
          onCambiado={() => { setObraCambiandoEstado(null); recargar(); }}
        />
      )}
    </>
  );
}

function claseDeEstado(estado) {
  if (estado === 'En ejecución') return estilos.estadoEjecucion;
  if (estado === 'Finalizada') return estilos.estadoFinalizada;
  if (estado === 'Cancelada') return estilos.estadoCancelada;
  return estilos.estadoPresupuestacion;
}

/** "2026-03-04" -> "04/03/26" */
function formatearFecha(iso) {
  const [anio, mes, dia] = iso.split('-');
  return `${dia}/${mes}/${anio.slice(2)}`;
}
