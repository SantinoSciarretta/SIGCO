import client from './client';

/**
 * Abre un PDF del sistema en una pestaña nueva.
 *
 * ------------------------------------------------------------------
 *  Por qué no alcanza con un enlace
 * ------------------------------------------------------------------
 *
 * Hasta el módulo 14 esto era un `<a href="/api/presupuestos/5/pdf">` y
 * funcionaba. Con la seguridad activa dejó de funcionar, y el motivo es
 * concreto: **un enlace no envía la cabecera `Authorization`**. El navegador
 * pide la dirección sin token y el backend contesta 401, así que el botón
 * "Ver PDF" pasó a abrir una pestaña con un error en JSON.
 *
 * El propio archivo `presupuestosApi.js` lo había anticipado con un comentario
 * desde el módulo 4. Esta función es la respuesta a ese comentario.
 *
 * ------------------------------------------------------------------
 *  Cómo se resuelve
 * ------------------------------------------------------------------
 *
 * Se pide el PDF con Axios —que sí adjunta el token, porque pasa por el
 * interceptor— y lo que vuelve se convierte en una dirección temporal del
 * navegador (`blob:`) que se abre en otra pestaña.
 *
 * La alternativa habría sido una URL firmada de un solo uso, como hacen los
 * servicios de almacenamiento. Es más trabajo en el backend y resuelve un
 * problema que acá no existe: estos PDF los abre alguien que ya está adentro
 * del sistema, no se comparten por dirección.
 *
 * @param {string} ruta     ruta de la API, sin el prefijo: "/presupuestos/5/pdf"
 * @param {string} [nombre] nombre sugerido al guardarlo
 */
export async function abrirPdf(ruta, nombre) {
  // responseType blob: sin esto, Axios interpreta la respuesta como texto y el
  // binario del PDF llega corrompido.
  const { data } = await client.get(ruta, { responseType: 'blob' });

  const url = URL.createObjectURL(new Blob([data], { type: 'application/pdf' }));
  const pestana = window.open(url, '_blank', 'noopener');

  // Si el navegador bloqueó la ventana emergente, se cae a una descarga: es
  // preferible a que el botón no haga nada y parezca roto.
  if (!pestana) {
    const enlace = document.createElement('a');
    enlace.href = url;
    enlace.download = nombre ?? 'documento.pdf';
    enlace.click();
  }

  // La dirección temporal se libera después de un rato: revocarla en el acto
  // deja a la pestaña recién abierta sin nada que mostrar.
  setTimeout(() => URL.revokeObjectURL(url), 60000);
}
