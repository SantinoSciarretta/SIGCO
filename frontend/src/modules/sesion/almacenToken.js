/**
 * Guarda el token de la sesión en el navegador.
 *
 * Vive en su propio archivo, sin importar nada, a propósito: lo usan tanto el
 * cliente HTTP (para adjuntar el token a cada petición) como el contexto de
 * sesión (para recordar quién entró). Si estuviera dentro de cualquiera de los
 * dos, los dos se importarían mutuamente y quedaría un ciclo de importación.
 *
 * Por qué localStorage y no memoria: recargar la página o abrir el sistema en
 * otra pestaña no debería obligar a volver a entrar.
 *
 * El costo de esa decisión, que conviene poder explicar: lo que está en
 * localStorage lo puede leer cualquier script que corra en la página, así que
 * si alguien lograra inyectar código en el sitio podría robarse el token. La
 * alternativa —una cookie httpOnly, que el JavaScript no puede leer— es más
 * segura, pero exige que backend y frontend compartan dominio, y acá están en
 * Railway y Vercel, que son dominios distintos.
 *
 * Lo que sí acota el riesgo: el token dura ocho horas y no lleva los permisos
 * adentro, así que ni sirve para siempre ni alcanza para inventarse permisos
 * (el backend los vuelve a leer de la base en cada petición).
 */

const CLAVE = 'sigco.token';

export function leerToken() {
  try {
    return localStorage.getItem(CLAVE);
  } catch {
    // Un navegador con el almacenamiento bloqueado no debe romper la app:
    // simplemente no hay sesión guardada y hay que volver a entrar.
    return null;
  }
}

export function guardarToken(token) {
  try {
    localStorage.setItem(CLAVE, token);
  } catch {
    // Sin almacenamiento, la sesión dura lo que dure la pestaña.
  }
}

export function borrarToken() {
  try {
    localStorage.removeItem(CLAVE);
  } catch {
    // Nada que limpiar.
  }
}
