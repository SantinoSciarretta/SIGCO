import { useRef, useState } from 'react';
import client from '../../api/client';
import { urlDeArchivo } from './archivos';
import estilos from './SubirImagen.module.css';

/**
 * Subida de una imagen, con vista previa.
 *
 * Lo usan los tres lugares del sistema donde se adjunta un archivo: la foto del
 * remito (Compras), el comprobante de un gasto (Gastos) y las fotos del
 * portfolio. Está escrito una vez porque los tres necesitan exactamente lo
 * mismo, y un cambio —el tamaño máximo, el mensaje de error— tiene que valer
 * para los tres.
 *
 * Lo que devuelve es la REFERENCIA que guarda la base ("privado/remitos/a1b2.jpg"),
 * no el archivo: el binario se queda en el almacenamiento y la base solo apunta.
 *
 * En el celular, `capture="environment"` hace que el botón abra directamente la
 * cámara trasera en lugar del explorador de archivos. Es lo que convierte
 * "adjuntar un archivo" en "sacarle una foto al remito", que es lo que el
 * capataz realmente hace parado al lado del camión.
 *
 * @param {string}   carpeta   remitos | comprobantes | portfolio
 * @param {string}   valor     referencia ya guardada, si la hay
 * @param {Function} onSubida  recibe la referencia nueva
 * @param {string}   etiqueta  texto del botón cuando no hay nada subido
 */
export default function SubirImagen({ carpeta, valor, onSubida, etiqueta = 'Subir imagen' }) {
  const entrada = useRef(null);
  const [subiendo, setSubiendo] = useState(false);
  const [error, setError] = useState(null);

  const elegir = async (evento) => {
    const archivo = evento.target.files?.[0];
    if (!archivo) return;

    setSubiendo(true);
    setError(null);
    try {
      const datos = new FormData();
      datos.append('archivo', archivo);
      datos.append('carpeta', carpeta);

      // Sin Content-Type a mano: el navegador tiene que ponerlo él, porque
      // multipart lleva un separador que genera al momento de enviar. Si se
      // escribe la cabecera, ese separador falta y el backend no puede leerlo.
      const { data } = await client.post('/archivos', datos);
      onSubida(data.referencia);
    } catch (fallo) {
      setError(fallo.mensaje);
    } finally {
      setSubiendo(false);
      // Se limpia para que elegir dos veces el mismo archivo vuelva a disparar
      // el evento: sin esto, un reintento después de un error no hace nada.
      if (entrada.current) entrada.current.value = '';
    }
  };

  return (
    <div className={estilos.contenedor}>
      <input
        ref={entrada}
        type="file"
        className={estilos.entradaOculta}
        accept="image/jpeg,image/png,image/webp,application/pdf"
        capture="environment"
        onChange={elegir}
        aria-label={etiqueta}
      />

      {valor ? (
        <div className={estilos.previa}>
          <VistaPrevia referencia={valor} />
          <div className={estilos.previaAcciones}>
            <button type="button" className={estilos.boton}
                    onClick={() => entrada.current?.click()} disabled={subiendo}>
              {subiendo ? 'Subiendo…' : 'Cambiar'}
            </button>
            <button type="button" className={estilos.botonQuitar}
                    onClick={() => onSubida(null)} disabled={subiendo}>
              Quitar
            </button>
          </div>
        </div>
      ) : (
        <button type="button" className={estilos.zona}
                onClick={() => entrada.current?.click()} disabled={subiendo}>
          <svg width="30" height="30" viewBox="0 0 24 24" fill="none" stroke="currentColor"
               strokeWidth="1.4" strokeLinecap="round" strokeLinejoin="round">
            <path d="M14.5 4h-5L8 6H4a2 2 0 0 0-2 2v10a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2V8a2 2 0 0 0-2-2h-4z" />
            <circle cx="12" cy="13" r="3.5" />
          </svg>
          <span>{subiendo ? 'Subiendo…' : etiqueta}</span>
          <span className={estilos.ayuda}>JPG, PNG o PDF · hasta 8 MB</span>
        </button>
      )}

      {error && <p className={estilos.error}>{error}</p>}
    </div>
  );
}

/**
 * Muestra el archivo ya subido.
 *
 * La URL se arma con la referencia, y funciona igual con el almacenamiento
 * local y con Supabase: en los dos casos el archivo se sirve a través del
 * backend, así el permiso lo decide el sistema y no la configuración del
 * bucket.
 */
export function VistaPrevia({ referencia, className }) {
  if (!referencia) return null;

  const url = urlDeArchivo(referencia);
  const esPdf = referencia.toLowerCase().endsWith('.pdf');

  if (esPdf) {
    return (
      <a href={url} target="_blank" rel="noreferrer"
         className={`${estilos.pdf} ${className ?? ''}`.trim()}>
        Ver comprobante (PDF)
      </a>
    );
  }

  return (
    <img src={url} alt="Archivo adjunto"
         className={`${estilos.imagen} ${className ?? ''}`.trim()} />
  );
}
