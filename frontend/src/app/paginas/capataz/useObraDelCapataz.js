import { useEffect, useState } from 'react';
import { misObras } from '../../../modules/obras/obrasApi';

/**
 * Sobre qué obra está trabajando el capataz.
 *
 * Las tres pantallas del celular necesitan lo mismo: una obra. Averiguarlo
 * tiene una vuelta que conviene tener resuelta en un solo lugar, porque los dos
 * roles de capataz llegan acá de formas distintas:
 *
 *   - El **Capataz de Obra** tiene una sola obra asignada. El backend se la
 *     devuelve sola y la pantalla entra derecho.
 *   - El **Capataz General** ve todas las obras en ejecución, así que hay que
 *     preguntarle en cuál está. La elección se recuerda en el navegador para
 *     que no tenga que repetirla en cada pantalla.
 *
 * Y el caso que no hay que tapar: un capataz de obra **sin obra asignada** no
 * ve ninguna. Es lo correcto —sin el vínculo con Personal no hay forma de saber
 * cuál es la suya— y la pantalla lo dice en lugar de mostrarse vacía.
 */

const CLAVE = 'sigco.obraElegida';

/**
 * Lee del navegador cuál fue la última obra que eligió el capataz, para volver
 * a mostrarle esa.
 */
function leerElegida() {
  try {
    const valor = localStorage.getItem(CLAVE);
    return valor ? Number(valor) : null;
  } catch {
    return null;
  }
}

/**
 * Guarda en el navegador la obra que eligió el capataz, para recordarla la
 * próxima vez.
 */
function guardarElegida(idObra) {
  try {
    localStorage.setItem(CLAVE, String(idObra));
  } catch {
    // Sin almacenamiento, la elección dura lo que dure la pantalla.
  }
}

/**
 * Trae las obras en las que trabaja el capataz y recuerda cuál eligió. Si tiene
 * una sola, la elige sola.
 */
export function useObraDelCapataz() {
  const [obras, setObras] = useState([]);
  const [idElegida, setIdElegida] = useState(leerElegida);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    let vigente = true;
    misObras()
      .then((lista) => {
        if (!vigente) return;
        setObras(lista);
        setError(null);
        // Con una sola obra no hay nada que elegir.
        if (lista.length === 1) {
          setIdElegida(lista[0].idObra);
          guardarElegida(lista[0].idObra);
        }
      })
      .catch((fallo) => { if (vigente) setError(fallo.mensaje); })
      .finally(() => { if (vigente) setCargando(false); });
    return () => { vigente = false; };
  }, []);

  /**
   * Cambia la obra elegida y la recuerda para la próxima vez.
   */
  const elegir = (idObra) => {
    setIdElegida(idObra);
    guardarElegida(idObra);
  };

  // Si la obra guardada ya no está en la lista (se finalizó, o lo desasignaron),
  // se ignora: mostrar una obra que el backend ya no le devuelve terminaría en
  // un 409 al primer intento de hacer algo.
  const obra = obras.find((o) => o.idObra === idElegida) ?? null;

  return { obra, obras, elegir, cargando, error };
}
