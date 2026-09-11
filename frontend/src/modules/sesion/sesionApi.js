import client from '../../api/client';

/**
 * Llamadas de la sesión.
 *
 * Dónde se guarda el token es problema de almacenToken.js; acá solo se habla
 * con la API.
 */

/** POST /api/sesion — ingresar. */
export function ingresar(nombreUsuario, contrasena) {
  return client.post('/sesion', { nombreUsuario, contrasena }).then((r) => r.data);
}

/**
 * GET /api/sesion — quién soy.
 *
 * Se llama al arrancar la aplicación, cuando ya hay un token guardado: el token
 * sobrevive a la recarga pero el estado de la aplicación no, así que hay que
 * volver a preguntar quién es el usuario y qué permisos tiene.
 *
 * Se le pregunta al backend en lugar de guardar los permisos junto al token,
 * porque lo que el navegador guarda el usuario lo puede editar.
 */
export function sesionActual() {
  return client.get('/sesion').then((r) => r.data);
}
