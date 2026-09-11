import { createContext } from 'react';

/**
 * El contexto donde vive la sesión.
 *
 * Está en su propio archivo, separado del proveedor y del hook, porque Vite
 * solo recarga en caliente los archivos que exportan únicamente componentes.
 * Con el contexto adentro del archivo del proveedor, cada cambio en el
 * proveedor recargaba la aplicación entera y se perdía el estado de la pantalla
 * en la que uno estaba trabajando.
 */
export const ContextoSesion = createContext(null);
