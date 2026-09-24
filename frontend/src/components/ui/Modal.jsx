import { useEffect, useRef } from 'react';
import Blueprint from './Blueprint';
import estilos from './Modal.module.css';

/**
 * Ventana modal del sistema.
 *
 * Se escribe una vez y la reutilizan los 14 modulos: formularios de alta,
 * confirmaciones, edicion rapida.
 *
 * Resuelve tres cosas que un div flotante no da gratis y que se olvidan seguido:
 *   - se cierra con la tecla Escape;
 *   - al abrirse, el foco del teclado entra en la ventana en lugar de quedar
 *     detras, en la pagina;
 *   - el fondo bloquea el desplazamiento de la pagina.
 *
 * @param {boolean}  abierto
 * @param {function} onCerrar
 * @param {string}   titulo
 */
export default function Modal({ abierto, onCerrar, titulo, children, ancho }) {
  const contenedor = useRef(null);

  useEffect(() => {
    if (!abierto) return undefined;

    const alPresionarTecla = (evento) => {
      if (evento.key === 'Escape') onCerrar();
    };

    document.addEventListener('keydown', alPresionarTecla);

    // Sin esto, la pagina de atras se sigue desplazando con la rueda del mouse
    // mientras la ventana esta abierta.
    const desbordeOriginal = document.body.style.overflow;
    document.body.style.overflow = 'hidden';

    contenedor.current?.focus();

    return () => {
      document.removeEventListener('keydown', alPresionarTecla);
      document.body.style.overflow = desbordeOriginal;
    };
  }, [abierto, onCerrar]);

  if (!abierto) return null;

  return (
    <div
      className={estilos.fondo}
      // Cierra al tocar fuera de la ventana, pero no al tocar adentro: por eso
      // se compara que el clic haya ocurrido sobre el fondo mismo.
      onClick={(evento) => { if (evento.target === evento.currentTarget) onCerrar(); }}
    >
      <Blueprint
        className={`${estilos.ventana} ${ancho === 'ancho' ? estilos.ventanaAncha : ''}`.trim()}
        role="dialog"
        aria-modal="true"
        aria-label={titulo}
        tabIndex={-1}
        ref={contenedor}
      >
        <div className={estilos.cabecera}>
          <h3 className={estilos.titulo}>{titulo}</h3>
          <button
            type="button"
            className={estilos.cerrar}
            onClick={onCerrar}
            aria-label="Cerrar"
          >
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                 strokeWidth="1.6" strokeLinecap="round">
              <path d="M18 6 6 18" />
              <path d="m6 6 12 12" />
            </svg>
          </button>
        </div>

        <div className={estilos.cuerpo}>{children}</div>
      </Blueprint>
    </div>
  );
}
