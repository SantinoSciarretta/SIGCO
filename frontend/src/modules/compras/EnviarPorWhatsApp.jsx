import { useEffect, useState } from 'react';
import Modal from '../../components/ui/Modal';
import { prepararEnvioPorWhatsApp } from './pedidosApi';
import estilos from './Compras.module.css';

/**
 * Mandarle la orden al corralón por WhatsApp.
 *
 * ------------------------------------------------------------------
 *  Qué hace y qué NO hace
 * ------------------------------------------------------------------
 *
 * SIGCO no manda el mensaje: arma el link y lo abre. El envío lo hace Ricardo
 * apretando "Enviar" en su propio WhatsApp, desde su propio número, que es el
 * que los corralones ya tienen agendado.
 *
 * Mandarlo de verdad requeriría la API oficial de Meta, que exige un número
 * dedicado —Ricardo perdería el suyo como WhatsApp normal—, verificación de la
 * empresa, plantillas aprobadas y pago por conversación. La Propuesta Técnica,
 * además, deja esa integración explícitamente fuera del alcance de esta
 * versión.
 *
 * ------------------------------------------------------------------
 *  Por qué se muestra el número antes de abrir el chat
 * ------------------------------------------------------------------
 *
 * Los teléfonos están cargados a mano y en cualquier formato. El backend los
 * interpreta, pero esta pantalla muestra el resultado antes de abrir nada: es
 * la última oportunidad de darse cuenta de que el número está mal antes de
 * mandarle el pedido de una obra a un desconocido.
 *
 * Cuando no se pudo interpretar, no hay botón de WhatsApp — hay un aviso y el
 * link de la orden para copiar y mandar a mano.
 */
export default function EnviarPorWhatsApp({ pedido, onCerrar }) {
  const [envio, setEnvio] = useState(null);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState(null);
  const [copiado, setCopiado] = useState(false);

  useEffect(() => {
    let vigente = true;

    prepararEnvioPorWhatsApp(pedido.idPedido)
      .then((datos) => { if (vigente) { setEnvio(datos); setError(null); } })
      .catch((fallo) => { if (vigente) setError(fallo.mensaje); })
      .finally(() => { if (vigente) setCargando(false); });

    return () => { vigente = false; };
  }, [pedido.idPedido]);

  const copiarLink = async () => {
    try {
      await navigator.clipboard.writeText(envio.urlOrden);
      setCopiado(true);
    } catch {
      // Si el navegador no deja copiar, el link está a la vista igual.
      setCopiado(false);
    }
  };

  return (
    <Modal abierto onCerrar={onCerrar} titulo="Enviar la orden por WhatsApp">
      {cargando && <p className={estilos.aviso}>Preparando el envío…</p>}
      {error && <p className={estilos.errorGeneral}>{error}</p>}

      {envio && (
        <>
          <p className={estilos.contexto}>
            Pedido <strong>#{envio.idPedido}</strong> para{' '}
            <strong>{envio.nombreProveedor}</strong>
          </p>

          {envio.telefonoParaWhatsApp ? (
            <div className={estilos.campo}>
              <span className={estilos.etiqueta}>Se le va a escribir a</span>
              <p className={`cifra ${estilos.numeroWhatsApp}`}>
                +{envio.telefonoParaWhatsApp}
              </p>
              <p className={estilos.ayuda}>
                Cargado como “{envio.telefonoCargado}”. Si no es el número del
                corralón, corregilo en Proveedores antes de mandar.
              </p>
            </div>
          ) : (
            <p className={estilos.advertencia}>{envio.aviso}</p>
          )}

          <div className={estilos.campo}>
            <span className={estilos.etiqueta}>El mensaje</span>
            <pre className={estilos.mensajeWhatsApp}>{envio.mensaje}</pre>
          </div>

          <div className={estilos.campo}>
            <span className={estilos.etiqueta}>Link de la orden</span>
            <p className={estilos.linkOrden}>{envio.urlOrden}</p>
            <p className={estilos.ayuda}>
              El corralón lo abre sin cuenta ni contraseña. Vence el{' '}
              {fecha(envio.vence)}, y la orden no muestra el presupuesto de la
              obra, ni la ganancia, ni el nombre del cliente.
            </p>
            <button type="button" className={estilos.botonSecundario} onClick={copiarLink}>
              {copiado ? 'Copiado' : 'Copiar el link'}
            </button>
          </div>

          <div className={estilos.accionesFormulario}>
            <button type="button" className={estilos.botonSecundario} onClick={onCerrar}>
              Cerrar
            </button>
            {envio.urlWhatsApp && (
              // Se abre en una pestaña nueva para no perder SIGCO. El mensaje
              // llega escrito; enviarlo lo decide quien está del otro lado.
              <a className={estilos.botonPrimario} href={envio.urlWhatsApp}
                 target="_blank" rel="noopener noreferrer">
                Abrir WhatsApp
              </a>
            )}
          </div>
        </>
      )}
    </Modal>
  );
}

function fecha(valor) {
  if (!valor) return '—';
  return new Date(valor).toLocaleDateString('es-AR');
}
