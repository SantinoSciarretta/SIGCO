import axios from 'axios';

import { borrarToken, guardarToken, leerToken } from '../modules/sesion/almacenToken';

/**
 * Cliente HTTP unico de SIGCO.
 *
 * TODAS las llamadas al backend pasan por aca. Ningun componente debe importar
 * axios por su cuenta ni escribir una URL completa a mano.
 *
 * El motivo es el modulo 14 (Accesos), donde se incorpora la autenticacion:
 * cuando exista el login, el token se va a adjuntar a cada peticion en UN SOLO
 * lugar (el interceptor de peticiones de abajo), y la expiracion de la sesion
 * se va a resolver tambien en un solo lugar (el interceptor de respuestas).
 * Si cada componente usara axios directamente, agregar la seguridad obligaria
 * a modificar los 14 modulos.
 */
const client = axios.create({
  // La direccion del backend no se escribe en el codigo: viene de las
  // variables de entorno de Vite. En desarrollo apunta a localhost:8080 y en
  // produccion al backend publicado en Railway, sin tocar una linea.
  baseURL: import.meta.env.VITE_API_URL,
  headers: {
    'Content-Type': 'application/json',
  },
  timeout: 15000,
});

/**
 * Interceptor de PETICIONES: se ejecuta antes de enviar cada llamada.
 *
 * Adjunta el token a TODAS las peticiones, en un solo lugar. Esta es la razon
 * por la que desde el primer modulo ningun componente importa axios por su
 * cuenta: agregar la autenticacion fue tocar este archivo y nada mas, en lugar
 * de los catorce modulos.
 */
client.interceptors.request.use(
  (config) => {
    const token = leerToken();
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => Promise.reject(error),
);

/**
 * Interceptor de RESPUESTAS: se ejecuta con la respuesta de cada llamada.
 *
 * Aprovecha que el backend devuelve todos sus errores con el mismo formato
 * (RespuestaError) para traducirlos a un objeto uniforme que los componentes
 * puedan mostrar sin tener que interpretar la estructura de axios.
 */
client.interceptors.response.use(
  (response) => {
    // Renovacion deslizante de la sesion.
    //
    // Cuando al token le queda poco, el backend manda uno nuevo en esta
    // cabecera y hay que reemplazar el guardado. Es lo que evita que el sistema
    // eche al usuario a las ocho horas en medio del trabajo: mientras usa el
    // sistema, el token se va renovando solo. Si deja de usarlo, nadie renueva
    // nada y la sesion vence como corresponde.
    //
    // Va aca y no en el contexto de sesion porque cualquier peticion de
    // cualquier modulo puede traerla.
    const renovado = response.headers?.['x-token-renovado'];
    if (renovado) {
      guardarToken(renovado);
    }
    return response;
  },
  (error) => {
    const respuesta = error.response;

    // 401: el token vencio o la cuenta se dio de baja. Se limpia la sesion y se
    // vuelve al ingreso.
    //
    // Se usa location.assign y no el router de React porque este archivo no es
    // un componente: no tiene acceso a los hooks de navegacion. Ademas, forzar
    // una recarga completa garantiza que no quede en memoria nada del usuario
    // anterior, que es justamente lo que se quiere al cerrar una sesion.
    //
    // La excepcion es el propio login: si alguien escribe mal la contrasena, el
    // backend contesta 401 y recargar la pagina borraria lo que escribio sin
    // mostrarle el error.
    if (respuesta?.status === 401 && !error.config?.url?.endsWith('/sesion')) {
      borrarToken();
      if (window.location.pathname !== '/') {
        window.location.assign('/');
      }
    }

    // El backend contesto con un error y su formato conocido.
    if (respuesta?.data) {
      return Promise.reject({
        estado: respuesta.status,
        mensaje: respuesta.data.mensaje ?? 'Ocurrio un error en el servidor',
        camposInvalidos: respuesta.data.camposInvalidos ?? null,
      });
    }

    // No hubo respuesta: el backend esta apagado o no hay red.
    return Promise.reject({
      estado: 0,
      mensaje: 'No se pudo conectar con el servidor. Verifique que el backend este en ejecucion.',
      camposInvalidos: null,
    });
  },
);

export default client;
