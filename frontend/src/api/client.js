import axios from 'axios';

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
 * Hoy no hace nada porque todavia no existe el login. Queda preparado para el
 * modulo 14, donde va a leer el token guardado y agregarlo a la cabecera
 * Authorization de cada peticion.
 */
client.interceptors.request.use(
  (config) => {
    // TODO: adjuntar el token JWT al integrar modulo Accesos
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
  (response) => response,
  (error) => {
    // TODO: ante un 401 (sesion vencida), redirigir al login
    //       al integrar modulo Accesos

    const respuesta = error.response;

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
