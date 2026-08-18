import { useState } from 'react';
import Modal from '../../components/ui/Modal';
import { ORIGENES, actualizarCliente, crearCliente } from './clientesApi';
import estilos from './Clientes.module.css';

const VACIO = {
  nombreApellido: '',
  telefonoContacto: '',
  emailContacto: '',
  origenRecomendacion: '',
  recomendadoPor: '',
};

/**
 * Formulario de alta y edicion de un cliente.
 *
 * El mismo componente sirve para las dos cosas: si recibe un cliente, edita;
 * si no, da de alta. Es el mismo criterio que en el backend, donde las dos
 * operaciones comparten la validacion.
 *
 * Sobre los errores: el formulario NO decide si los datos son validos. Manda
 * lo cargado al servidor y, si el servidor rechaza, muestra debajo de cada
 * campo el motivo que el backend devolvio en "camposInvalidos". Por eso el
 * mensaje que ve el usuario es exactamente la regla que se aplico, y no una
 * copia del frontend que podria quedar desactualizada.
 *
 * El unico control que se hace de este lado es marcar el campo obligatorio con
 * el atributo "required", para no hacer viajar un pedido que ya se sabe que va
 * a fallar. La validacion que manda sigue siendo la del servidor.
 */
export default function ClienteFormulario({ abierto, cliente, onCerrar, onGuardado }) {
  const editando = Boolean(cliente);

  const [datos, setDatos] = useState(cliente ? { ...VACIO, ...limpiar(cliente) } : VACIO);
  const [camposInvalidos, setCamposInvalidos] = useState({});
  const [errorGeneral, setErrorGeneral] = useState(null);
  const [guardando, setGuardando] = useState(false);

  const cambiar = (campo) => (evento) => {
    setDatos((previo) => ({ ...previo, [campo]: evento.target.value }));
    // Al corregir un campo se limpia su error, para que el usuario vea que
    // resolvio eso sin tener que reenviar el formulario.
    setCamposInvalidos((previo) => ({ ...previo, [campo]: undefined }));
  };

  const enviar = async (evento) => {
    evento.preventDefault();
    setGuardando(true);
    setCamposInvalidos({});
    setErrorGeneral(null);

    try {
      const guardado = editando
        ? await actualizarCliente(cliente.idCliente, datos)
        : await crearCliente(datos);
      onGuardado(guardado);
    } catch (fallo) {
      // El interceptor de Axios entrega siempre este mismo objeto, sin importar
      // si el error vino del servidor o de la falta de conexion.
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
    <Modal
      abierto={abierto}
      onCerrar={onCerrar}
      titulo={editando ? 'Editar cliente' : 'Nuevo cliente'}
    >
      <form onSubmit={enviar} noValidate={false}>

        {errorGeneral && <p className={estilos.errorGeneral}>{errorGeneral}</p>}

        <Campo
          id="nombreApellido"
          etiqueta="Nombre y apellido o razón social"
          obligatorio
          valor={datos.nombreApellido}
          onChange={cambiar('nombreApellido')}
          error={camposInvalidos.nombreApellido}
          maxLength={150}
        />

        <div className={estilos.dosColumnas}>
          <Campo
            id="telefonoContacto"
            etiqueta="Teléfono"
            valor={datos.telefonoContacto}
            onChange={cambiar('telefonoContacto')}
            error={camposInvalidos.telefonoContacto}
            maxLength={30}
          />
          <Campo
            id="emailContacto"
            etiqueta="Correo electrónico"
            tipo="email"
            valor={datos.emailContacto}
            onChange={cambiar('emailContacto')}
            error={camposInvalidos.emailContacto}
            maxLength={100}
          />
        </div>

        <div className={estilos.dosColumnas}>
          <div className={estilos.campo}>
            <label className={estilos.etiqueta} htmlFor="origenRecomendacion">
              Cómo llegó
            </label>
            <select
              id="origenRecomendacion"
              className={estilos.control}
              value={datos.origenRecomendacion}
              onChange={cambiar('origenRecomendacion')}
            >
              <option value="">Sin especificar</option>
              {ORIGENES.map((origen) => (
                <option key={origen} value={origen}>{origen}</option>
              ))}
            </select>
            {camposInvalidos.origenRecomendacion && (
              <p className={estilos.errorCampo}>{camposInvalidos.origenRecomendacion}</p>
            )}
          </div>

          <Campo
            id="recomendadoPor"
            etiqueta="Recomendado por"
            valor={datos.recomendadoPor}
            onChange={cambiar('recomendadoPor')}
            error={camposInvalidos.recomendadoPor}
            maxLength={150}
          />
        </div>

        <div className={estilos.accionesFormulario}>
          <button type="button" className={estilos.botonSecundario} onClick={onCerrar}>
            Cancelar
          </button>
          <button type="submit" className={estilos.botonPrimario} disabled={guardando}>
            {guardando ? 'Guardando…' : (editando ? 'Guardar cambios' : 'Dar de alta')}
          </button>
        </div>
      </form>
    </Modal>
  );
}

/** Campo de texto con su etiqueta y el error que devolvio el servidor. */
function Campo({ id, etiqueta, valor, onChange, error, tipo = 'text', obligatorio = false, maxLength }) {
  return (
    <div className={estilos.campo}>
      <label className={estilos.etiqueta} htmlFor={id}>
        {etiqueta}
        {obligatorio && <span className={estilos.obligatorio}> *</span>}
      </label>
      <input
        id={id}
        type={tipo}
        className={`${estilos.control} ${error ? estilos.controlConError : ''}`.trim()}
        value={valor}
        onChange={onChange}
        required={obligatorio}
        maxLength={maxLength}
        aria-invalid={Boolean(error)}
        aria-describedby={error ? `${id}-error` : undefined}
      />
      {error && <p id={`${id}-error`} className={estilos.errorCampo}>{error}</p>}
    </div>
  );
}

/** Convierte los nulos del backend en cadenas vacias, que es lo que espera un input. */
function limpiar(cliente) {
  return {
    nombreApellido: cliente.nombreApellido ?? '',
    telefonoContacto: cliente.telefonoContacto ?? '',
    emailContacto: cliente.emailContacto ?? '',
    origenRecomendacion: cliente.origenRecomendacion ?? '',
    recomendadoPor: cliente.recomendadoPor ?? '',
  };
}
