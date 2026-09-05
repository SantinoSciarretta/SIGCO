import { useCallback, useEffect, useState } from 'react';
import Blueprint from '../../components/ui/Blueprint';
import Modal from '../../components/ui/Modal';
import { listarObras } from '../obras/obrasApi';
import FichaOperario from './FichaOperario';
import { cambiarEstadoOperario, crearOperario, listarOperarios } from './personalApi';
import estilos from './Personal.module.css';

/**
 * Listado de operarios.
 *
 * El objetivo del relevamiento no es controlar horarios: es que el dueño pueda
 * detectar patrones de faltas reiteradas y decidir si corresponde hablar con
 * alguien. Por eso la cantidad de inasistencias está en el listado y se
 * destaca cuando empieza a ser significativa.
 */
export default function PersonalPage() {
  const [operarios, setOperarios] = useState([]);
  const [obras, setObras] = useState([]);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState(null);

  const [busqueda, setBusqueda] = useState('');
  const [estado, setEstado] = useState('');
  const [obra, setObra] = useState('');
  const [recarga, setRecarga] = useState(0);

  const [altaAbierta, setAltaAbierta] = useState(false);
  const [fichaAbierta, setFichaAbierta] = useState(null);

  const recargar = useCallback(() => setRecarga((n) => n + 1), []);

  useEffect(() => {
    let vigente = true;
    const temporizador = setTimeout(() => {
      (async () => {
        try {
          const datos = await listarOperarios({ busqueda, estado, obra });
          if (vigente) { setOperarios(datos); setError(null); }
        } catch (fallo) {
          if (vigente) setError(fallo.mensaje);
        } finally {
          if (vigente) setCargando(false);
        }
      })();
    }, 300);
    return () => { vigente = false; clearTimeout(temporizador); };
  }, [busqueda, estado, obra, recarga]);

  useEffect(() => {
    let vigente = true;
    listarObras().then((d) => { if (vigente) setObras(d); }).catch(() => {});
    return () => { vigente = false; };
  }, []);

  const alternarEstado = async (operario) => {
    try {
      await cambiarEstadoOperario(
        operario.idOperario, operario.estado === 'Activo' ? 'Inactivo' : 'Activo');
      recargar();
    } catch (fallo) {
      setError(fallo.mensaje);
    }
  };

  const hayFiltros = busqueda || estado || obra;

  return (
    <>
      <div className={estilos.cabecera}>
        <div>
          <span className="kicker kicker-acento">Módulo</span>
          <h2 className={estilos.titulo}>Personal</h2>
          <p className={estilos.bajada}>
            Quién trabaja en cada obra y el registro de faltas, para detectar reiteraciones.
          </p>
        </div>

        <div className={estilos.herramientas}>
          <input type="search" className={estilos.buscador} placeholder="Buscar operario"
                 value={busqueda} onChange={(e) => setBusqueda(e.target.value)}
                 aria-label="Buscar operario" />
          <select className={estilos.filtro} value={obra}
                  onChange={(e) => setObra(e.target.value)} aria-label="Filtrar por obra">
            <option value="">Toda obra</option>
            {obras.map((o) => (
              <option key={o.idObra} value={o.idObra}>{o.direccionObra}</option>
            ))}
          </select>
          <select className={estilos.filtro} value={estado}
                  onChange={(e) => setEstado(e.target.value)} aria-label="Filtrar por estado">
            <option value="">Todo estado</option>
            <option value="Activo">Activos</option>
            <option value="Inactivo">Inactivos</option>
          </select>
          <button type="button" className={estilos.botonPrimario}
                  onClick={() => setAltaAbierta(true)}>
            Nuevo operario
          </button>
        </div>
      </div>

      {error && <p className={estilos.errorGeneral}>{error}</p>}

      <Blueprint className={estilos.bloque}>
        {cargando && <p className={estilos.aviso}>Consultando…</p>}

        {!cargando && !error && operarios.length === 0 && (
          <div className={estilos.vacio}>
            <p className={estilos.vacioTitulo}>
              {hayFiltros ? 'Ningún operario coincide' : 'Todavía no hay operarios'}
            </p>
            <p className={estilos.vacioTexto}>
              {hayFiltros
                ? 'Probá con otro nombre o quitá los filtros.'
                : 'Cargá a los operarios que trabajan en las obras de la empresa.'}
            </p>
          </div>
        )}

        {!cargando && operarios.length > 0 && (
          <div className="scroll-x">
            <table className="table">
              <thead>
                <tr>
                  <th>Operario</th>
                  <th>Contacto</th>
                  <th style={{ textAlign: 'right' }}>Obras</th>
                  <th style={{ textAlign: 'right' }}>Faltas</th>
                  <th>Estado</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {operarios.map((o) => (
                  <tr key={o.idOperario}>
                    <td className={estilos.nombre}>{o.nombreApellido}</td>
                    <td className={estilos.dato}>{o.telefonoContacto || '—'}</td>
                    <td className={`cifra ${estilos.numero}`}>{o.cantidadObrasVigentes}</td>
                    <td className={`cifra ${estilos.numero}`}>
                      {/* Tres o más faltas ya es un patrón, no una casualidad:
                          es justamente lo que el dueño necesita ver. */}
                      <span className={
                        o.cantidadInasistencias >= 3 ? estilos.muchasFaltas
                          : o.cantidadInasistencias > 0 ? estilos.conFaltas : ''
                      }>
                        {o.cantidadInasistencias}
                      </span>
                    </td>
                    <td>
                      <span className={o.estado === 'Activo' ? estilos.activo : estilos.inactivo}>
                        {o.estado}
                      </span>
                    </td>
                    <td className={estilos.acciones}>
                      <button type="button" className={estilos.accion}
                              onClick={() => setFichaAbierta(o)}>
                        Ver ficha
                      </button>
                      <button type="button" className={estilos.accion}
                              onClick={() => alternarEstado(o)}>
                        {o.estado === 'Activo' ? 'Desactivar' : 'Activar'}
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Blueprint>

      {altaAbierta && (
        <NuevoOperarioModal
          onCerrar={() => setAltaAbierta(false)}
          onCreado={() => { setAltaAbierta(false); recargar(); }}
        />
      )}

      {fichaAbierta && (
        <FichaOperario
          operario={fichaAbierta}
          obras={obras}
          onCerrar={() => setFichaAbierta(null)}
          onCambio={recargar}
        />
      )}
    </>
  );
}

/* ========================================================================== */

function NuevoOperarioModal({ onCerrar, onCreado }) {
  const [datos, setDatos] = useState({ nombreApellido: '', telefonoContacto: '' });
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
      await crearOperario(datos);
      onCreado();
    } catch (fallo) {
      if (fallo.camposInvalidos) setCamposInvalidos(fallo.camposInvalidos);
      else setError(fallo.mensaje);
    } finally {
      setGuardando(false);
    }
  };

  return (
    <Modal abierto onCerrar={onCerrar} titulo="Nuevo operario">
      <form onSubmit={enviar}>
        {error && <p className={estilos.errorGeneral}>{error}</p>}

        <div className={estilos.campo}>
          <label className={estilos.etiqueta} htmlFor="nombreApellido">
            Nombre y apellido <span className={estilos.obligatorio}>*</span>
          </label>
          <input id="nombreApellido" className={estilos.control} maxLength={150}
                 value={datos.nombreApellido} onChange={cambiar('nombreApellido')}
                 required autoFocus />
          {camposInvalidos.nombreApellido && (
            <p className={estilos.errorCampo}>{camposInvalidos.nombreApellido}</p>
          )}
        </div>

        <div className={estilos.campo}>
          <label className={estilos.etiqueta} htmlFor="telefonoContacto">Teléfono</label>
          <input id="telefonoContacto" className={estilos.control} maxLength={30}
                 value={datos.telefonoContacto} onChange={cambiar('telefonoContacto')} />
          <p className={estilos.ayuda}>
            Opcional. Las obras se asignan después, desde la ficha.
          </p>
        </div>

        <div className={estilos.accionesFormulario}>
          <button type="button" className={estilos.botonSecundario} onClick={onCerrar}>
            Cancelar
          </button>
          <button type="submit" className={estilos.botonPrimario} disabled={guardando}>
            {guardando ? 'Guardando…' : 'Crear'}
          </button>
        </div>
      </form>
    </Modal>
  );
}
