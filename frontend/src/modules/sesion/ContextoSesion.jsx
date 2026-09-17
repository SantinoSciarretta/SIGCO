import { useCallback, useEffect, useMemo, useState } from 'react';
import { ContextoSesion } from './contexto';
import { borrarToken, guardarToken, leerToken } from './almacenToken';
import { ingresar as ingresarApi, sesionActual } from './sesionApi';

/**
 * Quién está usando el sistema, disponible para toda la aplicación.
 *
 * Vive en un contexto y no en el estado de una pantalla porque lo necesitan
 * cosas muy separadas entre sí: el menú (qué módulos mostrar), el router (a
 * dónde puede entrar) y cada pantalla (qué botones habilitar). Pasarlo por
 * props obligaría a atravesar media aplicación con un dato que casi ningún
 * componente intermedio usa.
 *
 * IMPORTANTE, y es lo que hay que tener claro para defenderlo: lo que se hace
 * acá con los permisos es PRESENTACIÓN, no seguridad. Esconder un botón evita
 * que alguien intente algo que va a ser rechazado, pero no protege nada: quien
 * quiera saltearlo manda la petición directo a la API. La seguridad real está
 * en el backend, que revalida el permiso en cada petición.
 */

export function ProveedorSesion({ children }) {
  const [sesion, setSesion] = useState(null);
  // "cargando" arranca en true cuando hay un token guardado: hasta saber si
  // sigue siendo válido no se puede decidir si mostrar el login o el sistema,
  // y mostrar el login un instante para después saltar adentro se ve como un
  // parpadeo.
  const [cargando, setCargando] = useState(Boolean(leerToken()));

  useEffect(() => {
    // Sin token no hay nada que verificar. No hace falta tocar "cargando":
    // ya arrancó en false, porque su valor inicial se derivó del token.
    if (!leerToken()) {
      return undefined;
    }

    let vigente = true;
    sesionActual()
      .then((datos) => { if (vigente) setSesion(datos); })
      .catch(() => {
        // El token venció o la cuenta se dio de baja. Se limpia y a empezar
        // de nuevo; no hay nada que avisar porque el usuario todavía no pidió
        // nada concreto.
        borrarToken();
        if (vigente) setSesion(null);
      })
      .finally(() => { if (vigente) setCargando(false); });

    return () => { vigente = false; };
  }, []);

  const ingresar = useCallback(async (nombreUsuario, contrasena) => {
    const datos = await ingresarApi(nombreUsuario, contrasena);
    guardarToken(datos.token);
    setSesion(datos);
    return datos;
  }, []);

  /**
   * Vuelve a preguntarle al backend quién es el usuario.
   *
   * Hace falta cuando algo que viaja en la sesión cambió sin pasar por el
   * login. Hoy el caso es uno: al cambiar la contraseña, la cuenta deja de
   * estar obligada a cambiarla. Sin refrescar, el frontend seguiría creyendo
   * que la obligación sigue y devolvería al usuario a la misma pantalla una y
   * otra vez.
   *
   * Se le pregunta al backend en lugar de corregir el dato a mano acá, para
   * que lo que el frontend cree sea siempre lo que el backend sabe.
   */
  const refrescar = useCallback(async () => {
    const datos = await sesionActual();
    setSesion(datos);
    return datos;
  }, []);

  /**
   * Cerrar sesión es olvidar el token.
   *
   * No hay llamada al backend, y no es una omisión: con tokens firmados el
   * servidor no guarda ninguna sesión que invalidar. Un endpoint de salida daría
   * la impresión de que el servidor revoca algo, y no es así.
   */
  const salir = useCallback(() => {
    borrarToken();
    setSesion(null);
  }, []);

  const valor = useMemo(() => ({
    sesion,
    cargando,
    ingresar,
    salir,
    refrescar,
    /** Si el usuario tiene un permiso. Sirve para mostrar u ocultar acciones. */
    puede: (permiso) => Boolean(sesion?.permisos?.includes(permiso)),
    esDueno: sesion?.nombreRol === 'Dueño',
  }), [sesion, cargando, ingresar, salir, refrescar]);

  return <ContextoSesion.Provider value={valor}>{children}</ContextoSesion.Provider>;
}
