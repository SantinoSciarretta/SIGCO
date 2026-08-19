import { useCallback, useEffect, useState } from 'react';
import Blueprint from '../../components/ui/Blueprint';
import ClienteFormulario from './ClienteFormulario';
import { ORIGENES, cambiarEstadoCliente, listarClientes } from './clientesApi';
import estilos from './Clientes.module.css';

/**
 * Listado de clientes.
 *
 * Reemplaza la situacion que describe el relevamiento: hoy no existe una lista
 * unificada de clientes y los datos de contacto quedan desparramados entre las
 * carpetas de obra en la computadora del dueño.
 *
 * Es el primer modulo conectado al backend de verdad. Todo lo que se ve aca
 * sale de GET /api/clientes.
 */
export default function ClientesPage() {
  const [clientes, setClientes] = useState([]);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState(null);

  const [busqueda, setBusqueda] = useState('');
  const [origen, setOrigen] = useState('');
  const [estado, setEstado] = useState('');

  const [formularioAbierto, setFormularioAbierto] = useState(false);
  const [clienteEnEdicion, setClienteEnEdicion] = useState(null);

  const cargar = useCallback(async (filtros) => {
    setCargando(true);
    setError(null);
    try {
      setClientes(await listarClientes(filtros));
    } catch (fallo) {
      setError(fallo.mensaje);
    } finally {
      setCargando(false);
    }
  }, []);

  // Se espera 300 ms antes de consultar. Sin esa pausa, escribir "Ferrari"
  // dispararia siete consultas al servidor, una por letra.
  useEffect(() => {
    const temporizador = setTimeout(() => {
      cargar({ busqueda, origen, estado });
    }, 300);
    return () => clearTimeout(temporizador);
  }, [busqueda, origen, estado, cargar]);

  const abrirAlta = () => {
    setClienteEnEdicion(null);
    setFormularioAbierto(true);
  };

  const abrirEdicion = (cliente) => {
    setClienteEnEdicion(cliente);
    setFormularioAbierto(true);
  };

  const alGuardar = () => {
    setFormularioAbierto(false);
    cargar({ busqueda, origen, estado });
  };

  const alternarEstado = async (cliente) => {
    const nuevoEstado = cliente.estado === 'Activo' ? 'Inactivo' : 'Activo';
    try {
      await cambiarEstadoCliente(cliente.idCliente, nuevoEstado);
      cargar({ busqueda, origen, estado });
    } catch (fallo) {
      setError(fallo.mensaje);
    }
  };

  const hayFiltros = busqueda || origen || estado;

  return (
    <>
      <div className={estilos.cabecera}>
        <div>
          <span className="kicker kicker-acento">Módulo</span>
          <h2 className={estilos.titulo}>Clientes</h2>
        </div>

        <div className={estilos.herramientas}>
          <input
            type="search"
            className={estilos.buscador}
            placeholder="Buscar por nombre"
            value={busqueda}
            onChange={(e) => setBusqueda(e.target.value)}
            aria-label="Buscar cliente por nombre"
          />

          <select
            className={estilos.filtro}
            value={origen}
            onChange={(e) => setOrigen(e.target.value)}
            aria-label="Filtrar por origen"
          >
            <option value="">Todo origen</option>
            {ORIGENES.map((o) => <option key={o} value={o}>{o}</option>)}
          </select>

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

          <button type="button" className={estilos.botonPrimario} onClick={abrirAlta}>
            Nuevo cliente
          </button>
        </div>
      </div>

      <Blueprint className={estilos.bloque}>
        {error && (
          <p className={estilos.errorGeneral}>{error}</p>
        )}

        {cargando && <p className={estilos.aviso}>Consultando…</p>}

        {!cargando && !error && clientes.length === 0 && (
          <div className={estilos.vacio}>
            <p className={estilos.vacioTitulo}>
              {hayFiltros ? 'Ningún cliente coincide con la búsqueda' : 'Todavía no hay clientes cargados'}
            </p>
            <p className={estilos.vacioTexto}>
              {hayFiltros
                ? 'Probá con otro nombre o quitá los filtros.'
                : 'El primer cliente se carga con el botón "Nuevo cliente".'}
            </p>
          </div>
        )}

        {!cargando && clientes.length > 0 && (
          <div className="scroll-x">
            <table className="table">
              <thead>
                <tr>
                  <th>Cliente</th>
                  <th>Teléfono</th>
                  <th>Correo</th>
                  <th>Cómo llegó</th>
                  <th style={{ textAlign: 'right' }}>Obras</th>
                  <th>Estado</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {clientes.map((cliente) => (
                  <tr key={cliente.idCliente}>
                    <td className={estilos.nombre}>{cliente.nombreApellido}</td>
                    <td className={estilos.dato}>{cliente.telefonoContacto || '—'}</td>
                    <td className={estilos.dato}>{cliente.emailContacto || '—'}</td>
                    <td className={estilos.dato}>
                      {cliente.origenRecomendacion || '—'}
                      {cliente.recomendadoPor && (
                        <span className={estilos.recomendadoPor}>{cliente.recomendadoPor}</span>
                      )}
                    </td>
                    {/* La cantidad de obras la aporta el módulo Obras, en una
                        sola consulta agrupada para toda la lista. */}
                    <td className={`cifra ${estilos.obras}`}>{cliente.cantidadObras}</td>
                    <td>
                      <span className={cliente.estado === 'Activo' ? estilos.activo : estilos.inactivo}>
                        {cliente.estado}
                      </span>
                    </td>
                    <td className={estilos.acciones}>
                      <button
                        type="button"
                        className={estilos.accion}
                        onClick={() => abrirEdicion(cliente)}
                      >
                        Editar
                      </button>
                      <button
                        type="button"
                        className={estilos.accion}
                        onClick={() => alternarEstado(cliente)}
                      >
                        {cliente.estado === 'Activo' ? 'Desactivar' : 'Activar'}
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Blueprint>

      {/* PENDIENTE: el informe pide mostrar la cantidad de obras de cada cliente
          y una ficha con su historial. Se agregan al desarrollar el módulo
          Obras, que es el que tiene esa información. */}

      {formularioAbierto && (
        <ClienteFormulario
          abierto={formularioAbierto}
          cliente={clienteEnEdicion}
          onCerrar={() => setFormularioAbierto(false)}
          onGuardado={alGuardar}
        />
      )}
    </>
  );
}
