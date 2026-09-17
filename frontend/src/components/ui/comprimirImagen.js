/**
 * Achica una foto en el navegador antes de subirla.
 *
 * ------------------------------------------------------------------
 *  El problema que resuelve
 * ------------------------------------------------------------------
 *
 * Una foto de celular moderno pesa entre 3 y 4 MB. El sistema la muestra a
 * 160 px en las listas, y al abrirla, a lo ancho de la pantalla. O sea que se
 * suben, se guardan y se descargan varios megabytes para mostrar una imagen
 * que nunca necesita ese detalle.
 *
 * Importa por tres motivos concretos, y ninguno es teórico:
 *
 *   1. El capataz sube el remito desde la obra, con datos del celular. Cuatro
 *      megas por foto es su plan de datos, y a veces la señal de un sótano.
 *   2. El plan gratuito de Supabase Storage da 1 GB. A 4 MB por remito son
 *      250 fotos; a 250 KB son 4000.
 *   3. La foto se vuelve a descargar cada vez que alguien abre la pantalla.
 *
 * Achicar antes de subir ataca los tres a la vez.
 *
 * ------------------------------------------------------------------
 *  La rotación
 * ------------------------------------------------------------------
 *
 * Las fotos de celular suelen guardarse siempre en horizontal, con una marca
 * EXIF que dice cómo hay que girarlas. Algunos navegadores la respetan al
 * mostrar la imagen y otros no, y por eso los remitos a veces se veían
 * acostados.
 *
 * Acá se resuelve solo: `imageOrientation: 'from-image'` le pide al navegador
 * que aplique esa marca al decodificar, y lo que se dibuja en el lienzo ya sale
 * derecho. Como el resultado es una imagen nueva, sin EXIF, deja de depender de
 * cómo la interprete cada visor.
 *
 * ------------------------------------------------------------------
 *  Qué NO hace
 * ------------------------------------------------------------------
 *
 * Nunca falla. Si el archivo es un PDF, si el navegador no soporta algo o si la
 * imagen está dañada, devuelve el archivo original y la subida sigue su curso.
 * Comprimir es una mejora; que el capataz no pueda registrar el remito porque
 * la compresión falló sería un problema peor que subir cuatro megas.
 *
 * Y la validación de tamaño del backend sigue en pie: esto reduce lo que se
 * manda, no reemplaza el control del servidor, que es el que manda.
 */

/** Lado máximo, en píxeles. Alcanza para leer un remito a pantalla completa. */
const LADO_MAXIMO = 1600;

/**
 * Calidad del JPEG resultante, de 0 a 1.
 *
 * 0,82 es el punto donde la foto de un papel escrito sigue siendo legible y el
 * archivo baja alrededor de un 90 %. Más abajo empiezan a aparecer manchas
 * alrededor de las letras, justo lo que hay que poder leer en un remito.
 */
const CALIDAD = 0.82;

/** Por debajo de esto no vale la pena tocar nada. */
const MINIMO_PARA_COMPRIMIR = 400 * 1024;

export async function comprimirImagen(archivo) {
  // Los PDF salen intactos: no son imágenes y el lienzo no sabe dibujarlos.
  if (!archivo.type.startsWith('image/')) {
    return archivo;
  }

  // Una imagen ya chica se sube tal cual. Recomprimirla la haría más fea sin
  // ahorrar nada apreciable.
  if (archivo.size < MINIMO_PARA_COMPRIMIR) {
    return archivo;
  }

  try {
    const bitmap = await createImageBitmap(archivo, { imageOrientation: 'from-image' });

    const escala = Math.min(1, LADO_MAXIMO / Math.max(bitmap.width, bitmap.height));
    const ancho = Math.round(bitmap.width * escala);
    const alto = Math.round(bitmap.height * escala);

    const lienzo = document.createElement('canvas');
    lienzo.width = ancho;
    lienzo.height = alto;

    const contexto = lienzo.getContext('2d');
    contexto.drawImage(bitmap, 0, 0, ancho, alto);
    bitmap.close();

    const blob = await new Promise((resolver) => {
      lienzo.toBlob(resolver, 'image/jpeg', CALIDAD);
    });

    // Si el navegador no pudo generar el blob, o si el resultado terminó
    // pesando más que el original —pasa con capturas de pantalla y con PNG de
    // pocos colores—, se sube el archivo tal como vino.
    if (!blob || blob.size >= archivo.size) {
      return archivo;
    }

    // Se renombra a .jpg porque el contenido ahora es JPEG: si conservara un
    // nombre .png, el backend guardaría un archivo cuya extensión miente sobre
    // su contenido.
    return new File([blob], nombreJpeg(archivo.name), {
      type: 'image/jpeg',
      lastModified: Date.now(),
    });
  } catch {
    // Formato que el navegador no sabe decodificar (HEIC de iPhone en algunos
    // casos), imagen dañada, o memoria insuficiente con una foto enorme.
    return archivo;
  }
}

function nombreJpeg(nombre) {
  const sinExtension = nombre.replace(/\.[^.]+$/, '');
  return `${sinExtension || 'imagen'}.jpg`;
}
