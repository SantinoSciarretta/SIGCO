import client from './client';

/**
 * Llamadas al endpoint de diagnostico del backend.
 *
 * Sigue el patron que van a usar todos los modulos: un archivo por modulo que
 * agrupa sus llamadas, para que los componentes pidan datos por el nombre de la
 * operacion ("consultarEstado") sin conocer la URL. Si manana cambia la ruta,
 * se toca un solo archivo.
 */
export async function consultarEstado() {
  const respuesta = await client.get('/estado');
  return respuesta.data;
}
