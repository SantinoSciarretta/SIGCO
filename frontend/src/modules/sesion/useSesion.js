import { useContext } from 'react';
import { ContextoSesion } from './contexto';

/**
 * Quién está usando el sistema.
 *
 * Devuelve la sesión, si está cargando, las acciones de entrar y salir, y
 * `puede(permiso)` para decidir qué mostrar.
 *
 * Vive en su propio archivo y no junto al proveedor porque Vite solo recarga en
 * caliente los archivos que exportan únicamente componentes.
 */
export function useSesion() {
  const valor = useContext(ContextoSesion);
  if (!valor) {
    throw new Error('useSesion tiene que usarse dentro de <ProveedorSesion>');
  }
  return valor;
}
