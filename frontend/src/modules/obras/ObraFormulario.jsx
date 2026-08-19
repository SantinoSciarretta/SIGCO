import { useEffect, useState } from 'react';
import Modal from '../../components/ui/Modal';
import { crearCliente, listarClientes } from '../clientes/clientesApi';
import { TIPOS_INMUEBLE, TIPOS_OBRA, actualizarObra, crearObra } from './obrasApi';
import estilos from './Obras.module.css';

const VACIO = {
  idCliente: '',
  direccionObra: '',
  tipoInmueble: '',
  tipoObra: '',
  fechaFinEstimada: '',
  notas: '',
};

/**
 * Formulario de alta y edición de una obra.
 *
 * Incluye el alta rápida de cliente que pide el informe: cuando llega una
 * consulta de alguien que todavía no está registrado, el dueño lo carga sin
 * salir de esta pantalla. Usa el mismo endpoint que el alta completa de
 * Clientes, así no hay dos reglas de negocio distintas según desde dónde se
 * cargue.
 *
 * En edición hay dos campos que no aparecen, y no es un olvido: el cliente y el
 * tipo de obra no se pueden cambiar. El backend directamente no los acepta en
 * la operación de edición.
 */
export default function ObraFormulario({ abierto, obra, onCerrar, onGuardado }) {
  const editando = Boolean(obra);

  const [datos, setDatos] = useState(obra ? desdeObra(obra) : VACIO);
  const [clientes, setClientes] = useState([]);
  const [camposInvalidos, setCamposInvalidos] = useState({});
  const [errorGeneral, setErrorGeneral] = useState(null);
  const [guardando, setGuardando] = useState(false);

  // Alta rápida de cliente
  const [altaRapida, setAltaRapida] = useState(false);
  const [nombreNuevo, setNombreNuevo] = useState('');
  const [telefonoNuevo, setTelefonoNuevo] = useState('');
  const [errorCliente, setErrorCliente] = useState(null);

  useEffect(() => {
    if (editando) return;
    listarClientes({ estado: 'Activo' })
      .then(setClientes)
      .catch((fallo) => setErrorGeneral(fallo.mensaje));
  }, [editando]);

  const cambiar = (campo) => (evento) => {
    setDatos((previo) => ({ ...previo, [campo]: evento.target.value }));
    setCamposInvalidos((previo) => ({ ...previo, [campo]: undefined }));
  };

  const guardarClienteNuevo = async () => {
    setErrorCliente(null);
    try {
      const creado = await crearCliente({
        nombreApellido: nombreNuevo,
        telefonoContacto: telefonoNuevo,
      });
      setClientes((previo) => [...previo, creado]);
      setDatos((previo) => ({ ...previo, idCliente: String(creado.idCliente) }));
      setAltaRapida(false);
      setNombreNuevo('');
      setTelefonoNuevo('');
    } catch (fallo) {
      setErrorCliente(fallo.camposInvalidos?.nombreApellido ?? fallo.mensaje);
    }
  };

  const enviar = async (evento) => {
    evento.preventDefault();
    setGuardando(true);
    setCamposInvalidos({});
    setErrorGeneral(null);

    // Los campos de fecha vacíos van como null, no como cadena vacía: el
    // backend espera una fecha o nada.
    const cuerpo = {
      ...datos,
      fechaFinEstimada: datos.fechaFinEstimada || null,
      notas: datos.notas || null,
    };

    try {
      if (editando) {
        const { direccionObra, tipoInmueble, fechaFinEstimada, notas } = cuerpo;
        onGuardado(await actualizarObra(obra.idObra, {
          direccionObra,
          tipoInmueble,
          fechaInicioReal: datos.fechaInicioReal || null,
          fechaFinEstimada,
          notas,
        }));
      } else {
        onGuardado(await crearObra({ ...cuerpo, idCliente: Number(cuerpo.idCliente) }));
      }
    } catch (fallo) {
      if (fallo.camposInvalidos) {
        setCamposInvalidos(fallo.camposInvalidos);
      } else {
        setErrorGeneral(fallo.mensaje);
      }
    } finally {
      setGuardando(false);
    }
  };

  return (
    <Modal abierto={abierto} onCerrar={onCerrar} titulo={editando ? 'Editar obra' : 'Nueva obra'}>
      <form onSubmit={enviar}>

        {errorGeneral && <p className={estilos.errorGeneral}>{errorGeneral}</p>}

        {/* ---------- Cliente: solo en el alta ---------- */}
        {!editando && (
          <div className={estilos.campo}>
            <label className={estilos.etiqueta} htmlFor="idCliente">
              Cliente <span className={estilos.obligatorio}>*</span>
            </label>

            {!altaRapida ? (
              <div className={estilos.filaCliente}>
                <select
                  id="idCliente"
                  className={`${estilos.control} ${camposInvalidos.idCliente ? estilos.controlConError : ''}`.trim()}
                  value={datos.idCliente}
                  onChange={cambiar('idCliente')}
                  required
                >
                  <option value="">Elegir cliente…</option>
                  {clientes.map((c) => (
                    <option key={c.idCliente} value={c.idCliente}>{c.nombreApellido}</option>
                  ))}
                </select>
                <button
                  type="button"
                  className={estilos.botonSecundario}
                  onClick={() => setAltaRapida(true)}
                >
                  Es nuevo
                </button>
              </div>
            ) : (
              // Alta rápida: solo lo imprescindible. El resto de los datos del
              // cliente se completan después desde su propio módulo.
              <div className={estilos.altaRapida}>
                <p className={estilos.altaRapidaTitulo}>Cliente nuevo</p>
                <input
                  className={estilos.control}
                  placeholder="Nombre y apellido o razón social"
                  value={nombreNuevo}
                  onChange={(e) => setNombreNuevo(e.target.value)}
                  aria-label="Nombre del cliente nuevo"
                />
                <input
                  className={estilos.control}
                  placeholder="Teléfono (opcional)"
                  value={telefonoNuevo}
                  onChange={(e) => setTelefonoNuevo(e.target.value)}
                  aria-label="Teléfono del cliente nuevo"
                />
                {errorCliente && <p className={estilos.errorCampo}>{errorCliente}</p>}
                <div className={estilos.altaRapidaAcciones}>
                  <button type="button" className={estilos.botonSecundario}
                          onClick={() => { setAltaRapida(false); setErrorCliente(null); }}>
                    Volver a la lista
                  </button>
                  <button type="button" className={estilos.botonPrimario} onClick={guardarClienteNuevo}>
                    Guardar cliente
                  </button>
                </div>
              </div>
            )}

            {camposInvalidos.idCliente && (
              <p className={estilos.errorCampo}>{camposInvalidos.idCliente}</p>
            )}
          </div>
        )}

        {/* ---------- Datos de la obra ---------- */}
        <div className={estilos.campo}>
          <label className={estilos.etiqueta} htmlFor="direccionObra">
            Dirección de la obra <span className={estilos.obligatorio}>*</span>
          </label>
          <input
            id="direccionObra"
            className={`${estilos.control} ${camposInvalidos.direccionObra ? estilos.controlConError : ''}`.trim()}
            value={datos.direccionObra}
            onChange={cambiar('direccionObra')}
            maxLength={200}
            required
          />
          {camposInvalidos.direccionObra && (
            <p className={estilos.errorCampo}>{camposInvalidos.direccionObra}</p>
          )}
        </div>

        <div className={estilos.dosColumnas}>
          <div className={estilos.campo}>
            <label className={estilos.etiqueta} htmlFor="tipoInmueble">
              Tipo de inmueble <span className={estilos.obligatorio}>*</span>
            </label>
            <select
              id="tipoInmueble"
              className={estilos.control}
              value={datos.tipoInmueble}
              onChange={cambiar('tipoInmueble')}
              required
            >
              <option value="">Elegir…</option>
              {TIPOS_INMUEBLE.map((t) => <option key={t} value={t}>{t}</option>)}
            </select>
          </div>

          {/* El tipo de obra solo se define al dar de alta: después queda
              bloqueado porque determina el circuito de Presupuestación. */}
          {!editando ? (
            <div className={estilos.campo}>
              <label className={estilos.etiqueta} htmlFor="tipoObra">
                Tipo de obra <span className={estilos.obligatorio}>*</span>
              </label>
              <select
                id="tipoObra"
                className={estilos.control}
                value={datos.tipoObra}
                onChange={cambiar('tipoObra')}
                required
              >
                <option value="">Elegir…</option>
                {TIPOS_OBRA.map((t) => <option key={t} value={t}>{t}</option>)}
              </select>
            </div>
          ) : (
            <div className={estilos.campo}>
              <span className={estilos.etiqueta}>Tipo de obra</span>
              <p className={estilos.bloqueado}>
                {obra.tipoObra}
                <span className={estilos.bloqueadoNota}>
                  No se modifica: define el circuito de presupuestación
                </span>
              </p>
            </div>
          )}
        </div>

        <div className={estilos.dosColumnas}>
          {/* La fecha de inicio solo se habilita cuando la obra salió de
              presupuestación, es decir cuando se aprobó el presupuesto. */}
          {editando && obra.estado !== 'En presupuestación' && (
            <div className={estilos.campo}>
              <label className={estilos.etiqueta} htmlFor="fechaInicioReal">Inicio real</label>
              <input
                id="fechaInicioReal"
                type="date"
                className={estilos.control}
                value={datos.fechaInicioReal ?? ''}
                onChange={cambiar('fechaInicioReal')}
              />
            </div>
          )}

          <div className={estilos.campo}>
            <label className={estilos.etiqueta} htmlFor="fechaFinEstimada">Fin estimado</label>
            <input
              id="fechaFinEstimada"
              type="date"
              className={estilos.control}
              value={datos.fechaFinEstimada}
              onChange={cambiar('fechaFinEstimada')}
            />
          </div>
        </div>

        <div className={estilos.campo}>
          <label className={estilos.etiqueta} htmlFor="notas">Notas</label>
          <textarea
            id="notas"
            className={estilos.area}
            value={datos.notas}
            onChange={cambiar('notas')}
            placeholder="Restricciones de horario, acceso al edificio, pedidos del cliente…"
          />
        </div>

        <div className={estilos.accionesFormulario}>
          <button type="button" className={estilos.botonSecundario} onClick={onCerrar}>
            Cancelar
          </button>
          <button type="submit" className={estilos.botonPrimario} disabled={guardando || altaRapida}>
            {guardando ? 'Guardando…' : (editando ? 'Guardar cambios' : 'Crear obra')}
          </button>
        </div>
      </form>
    </Modal>
  );
}

/** Pasa los nulos del backend a cadenas vacías, que es lo que espera un input. */
function desdeObra(obra) {
  return {
    idCliente: String(obra.idCliente),
    direccionObra: obra.direccionObra ?? '',
    tipoInmueble: obra.tipoInmueble ?? '',
    tipoObra: obra.tipoObra ?? '',
    fechaInicioReal: obra.fechaInicioReal ?? '',
    fechaFinEstimada: obra.fechaFinEstimada ?? '',
    notas: obra.notas ?? '',
  };
}
