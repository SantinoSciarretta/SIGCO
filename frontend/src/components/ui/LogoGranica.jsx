import svg from '../../assets/granica-logo.svg?raw';
import estilos from './LogoGranica.module.css';

/**
 * El logo de la empresa.
 *
 * SE ESCRIBE UNA SOLA VEZ Y LO USAN TODAS LAS PANTALLAS: la barra superior, el
 * ingreso, las pantallas del capataz y la vidriera pública. El mismo archivo
 * `src/assets/granica-logo.svg` es además la fuente del PNG que llevan los
 * cuatro PDF (ver docs/desarrollo/23-logo-de-la-empresa.md). Si algún día
 * cambia el logo, se cambia ese archivo y cambia en todos lados.
 *
 * POR QUÉ SE INYECTA EL SVG Y NO SE USA UNA <img>
 *
 * El logo tiene que ir sobre dos fondos: el azul profundo de la barra y el
 * blanco de las pantallas. Sus colores originales son casi negros, así que
 * sobre la barra no se vería. Con una <img> el navegador no deja recolorearlo
 * (habría que mantener dos archivos, y el día que cambie el logo uno de los dos
 * se va a olvidar).
 *
 * Inyectando el SVG en el documento, el CSS sí puede pintarlo: una regla de
 * hoja de estilos le gana al atributo `fill="#191919"` que trae el archivo,
 * porque los atributos de presentación tienen menos prioridad que cualquier
 * regla CSS. Así el logo vive en UN archivo y se adapta al fondo.
 *
 * El `?raw` de Vite mete el contenido del archivo en el bundle en tiempo de
 * compilación: no es una petición de red aparte.
 *
 * @param {boolean} [claro]   true sobre fondo oscuro (la barra, el ingreso):
 *                            pinta el logo en blanco
 * @param {number}  [alto]    alto en píxeles; el ancho sale de la proporción
 *                            (el logo mide 930 x 99,5, casi diez veces más
 *                            ancho que alto). Si no se pasa, el alto lo decide
 *                            el CSS: es lo que necesita la vidriera, donde el
 *                            logo tiene que achicarse en el celular y un alto
 *                            fijo en el atributo `style` le ganaría a cualquier
 *                            media query.
 */
export default function LogoGranica({ claro = false, alto, className = '', ...resto }) {
  const clases = [estilos.logo, claro ? estilos.claro : '', className]
    .filter(Boolean)
    .join(' ');

  return (
    <span
      className={clases}
      style={alto ? { height: `${alto}px` } : undefined}
      // El SVG es un archivo del propio repositorio, no entra nada del usuario
      // ni de la red: por eso acá no hay riesgo de inyección.
      dangerouslySetInnerHTML={{ __html: svg }}
      {...resto}
    />
  );
}
