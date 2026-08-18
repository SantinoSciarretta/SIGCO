import { createContext, useContext } from 'react';

/**
 * Contexto y hook de acceso al estado compartido entre pantallas.
 *
 * Va en un archivo aparte del proveedor (DemoProvider.jsx) por un motivo
 * práctico de Vite: la recarga en caliente solo funciona bien cuando un
 * archivo exporta únicamente componentes. Mezclar un componente con funciones
 * sueltas obliga a recargar la página entera en cada cambio.
 */
export const ContextoDemo = createContext(null);

/** Acceso al estado compartido desde cualquier pantalla. */
export function useDemo() {
  const contexto = useContext(ContextoDemo);
  if (!contexto) {
    throw new Error('useDemo debe usarse dentro de <DemoProvider>');
  }
  return contexto;
}
