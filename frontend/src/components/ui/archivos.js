/**
 * Utilidades de los archivos adjuntos.
 *
 * Están separadas del componente que los sube porque un archivo que exporta un
 * componente y además funciones rompe la recarga en caliente de Vite: cada
 * cambio recargaría la aplicación entera en vez de la pantalla.
 */

/**
 * De la referencia guardada en la base a la dirección desde donde se ve.
 *
 * `VITE_API_URL` es "/api" en desarrollo (el proxy de Vite) y la dirección del
 * backend en producción, así que la misma línea sirve en los dos. El archivo se
 * sirve siempre a través del backend y no con la URL del almacenamiento: así el
 * permiso lo decide el sistema y no la configuración del bucket.
 */
export function urlDeArchivo(referencia) {
  if (!referencia) return null;
  return `${import.meta.env.VITE_API_URL}/archivos/${referencia}`;
}

/**
 * Si la referencia apunta a un archivo del sistema o es texto viejo.
 *
 * Hace falta porque hay filas cargadas ANTES de que existiera la subida, con un
 * texto escrito a mano ("remitos/2026-09-03.jpg"). Esas no se pueden mostrar
 * como imagen —el archivo no existe— y se muestran como texto.
 */
export function esArchivoSubido(referencia) {
  return typeof referencia === 'string'
    && (referencia.startsWith('publico/') || referencia.startsWith('privado/'));
}
