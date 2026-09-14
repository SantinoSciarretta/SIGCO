import { useState } from 'react';
import Modal from '../../components/ui/Modal';
import SubirImagen from '../../components/ui/SubirImagen';
import { recibirPedido } from './pedidosApi';
import estilos from './Compras.module.css';

/**
 * Confirmación de recepción en obra.
 *
 * Pensada para completarse desde el celular cuando llega el camión, así que
 * tiene dos campos y nada más. Es la pantalla que reemplaza al remito en papel
 * que hoy se pierde.
 *
 * El estado final NO se elige: si hay nota de diferencia, el pedido queda
 * "Recibido con Diferencias"; si no, "Recibido Completo". Así nadie puede
 * marcar completo y a la vez anotar que faltaron bolsas.
 *
 * TODO: la carga real de la foto a Supabase Storage se implementa al integrar
 *       el almacenamiento. Por ahora se guarda la referencia del archivo, que
 *       es lo que la base almacena en cualquier caso.
 */
export default function RecibirPedido({ pedido, onCerrar, onRecibido }) {
  const [fotoRemito, setFotoRemito] = useState('');
  const [notaDiferencia, setNotaDiferencia] = useState('');
  const [error, setError] = useState(null);
  const [guardando, setGuardando] = useState(false);

  const hayDiferencias = notaDiferencia.trim().length > 0;

  const enviar = async (evento) => {
    evento.preventDefault();
    setGuardando(true);
    setError(null);
    try {
      await recibirPedido(pedido.idPedido, {
        fotoRemito: fotoRemito.trim(),
        notaDiferencia: hayDiferencias ? notaDiferencia.trim() : null,
      });
      onRecibido();
    } catch (fallo) {
      setError(fallo.mensaje);
    } finally {
      setGuardando(false);
    }
  };

  return (
    <Modal abierto onCerrar={onCerrar} titulo="Confirmar recepción">
      <form onSubmit={enviar}>
        <p className={estilos.confirmacion}>
          Pedido de <b>{pedido.direccionObra}</b>
          {pedido.nombreProveedor && <> — {pedido.nombreProveedor}</>}
        </p>

        {error && <p className={estilos.errorGeneral}>{error}</p>}

        <div className={estilos.campo}>
          <label className={estilos.etiqueta} htmlFor="fotoRemito">
            Foto del remito <span className={estilos.obligatorio}>*</span>
          </label>
          <SubirImagen
            carpeta="remitos"
            valor={fotoRemito}
            onSubida={setFotoRemito}
            etiqueta="Sacar o elegir la foto del remito"
          />
          <p className={estilos.ayuda}>
            Es obligatoria: reemplaza al remito en papel, que es lo que hoy se
            pierde. Desde el celular, el botón abre la cámara. La base guarda
            solo la referencia; el archivo va al almacenamiento.
          </p>
        </div>

        <div className={estilos.campo}>
          <label className={estilos.etiqueta} htmlFor="notaDiferencia">
            ¿Faltó algo o llegó distinto?
          </label>
          <input id="notaDiferencia" className={estilos.control} maxLength={300}
                 placeholder="Faltaron 4 bolsas de cemento" value={notaDiferencia}
                 onChange={(e) => setNotaDiferencia(e.target.value)} />
          <p className={estilos.ayuda}>
            Dejalo vacío si llegó todo. Si escribís algo, el pedido queda marcado
            con diferencias para que el dueño le reclame al proveedor.
          </p>
        </div>

        {/* Se anticipa el estado resultante: el usuario ve la consecuencia de
            lo que escribió antes de confirmar. */}
        <p className={hayDiferencias ? estilos.advertencia : estilos.ayuda}>
          {hayDiferencias
            ? 'El pedido va a quedar como Recibido con Diferencias.'
            : 'El pedido va a quedar como Recibido Completo.'}
        </p>

        <div className={estilos.accionesFormulario}>
          <button type="button" className={estilos.botonSecundario} onClick={onCerrar}>
            Cancelar
          </button>
          <button type="submit" className={estilos.botonPrimario} disabled={guardando}>
            {guardando ? 'Confirmando…' : 'Confirmar recepción'}
          </button>
        </div>
      </form>
    </Modal>
  );
}
