import CabeceraCapataz from './CabeceraCapataz';
import { useDemo } from '../../../datos/contextoDemo';
import { HITOS, OBRAS, OBRA_DEL_CAPATAZ, avancePorHitos } from '../../../datos/demo';
import estilos from './Hitos.module.css';

/**
 * Hitos de la obra, para marcar desde el celular.
 *
 * Es la pantalla que resuelve el problema del cronograma que se arma al inicio
 * y nunca se actualiza: acá el avance no se declara, se deduce de los hitos
 * efectivamente completados.
 *
 * Cada fila mide 88 px y es tocable entera —no hay que apuntarle a una casilla
 * chica— porque se usa parado en obra.
 *
 * Al tildar un hito el porcentaje cambia acá, en la pantalla de inicio del
 * capataz y en el tablero del dueño, porque el dato vive en el estado
 * compartido y no dentro de esta pantalla.
 * TODO: reemplazar por PATCH /api/hitos/{id} al desarrollar módulo Seguimiento
 */
export default function Hitos() {
  const { hitosCompletados, alternarHito } = useDemo();

  const obra = OBRAS[OBRA_DEL_CAPATAZ];
  const avance = avancePorHitos(hitosCompletados);
  const cumplidos = HITOS.filter((_, i) => hitosCompletados[i]).length;

  return (
    <>
      <CabeceraCapataz titulo={`Hitos · ${obra.corto}`}>
        <div className={estilos.resumen}>
          <div className={estilos.cifraFila}>
            <span className={`cifra ${estilos.numero}`}>{avance}</span>
            <span className={`cifra ${estilos.simbolo}`}>%</span>
          </div>
          <div className={estilos.detalle}>
            avance<br />{cumplidos}/{HITOS.length} hitos
          </div>
        </div>

        <div className={estilos.pista}>
          <span className={estilos.relleno} style={{ width: `${avance}%` }} />
        </div>
      </CabeceraCapataz>

      <ul className={estilos.lista}>
        {HITOS.map((hito, i) => {
          const cumplido = !!hitosCompletados[i];

          return (
            <li key={hito.nombre}>
              <button
                type="button"
                className={`${estilos.fila} ${cumplido ? estilos.filaHecha : ''}`.trim()}
                onClick={() => alternarHito(i)}
                aria-pressed={cumplido}
              >
                <span className={`${estilos.casilla} ${cumplido ? estilos.casillaOk : ''}`.trim()}>
                  {cumplido && (
                    <svg width="30" height="30" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                         strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round">
                      <path d="M20 6 9 17l-5-5" />
                    </svg>
                  )}
                </span>

                <span className={estilos.texto}>
                  <span className={`${estilos.nombre} ${cumplido ? estilos.nombreHecho : ''}`.trim()}>
                    {hito.nombre}
                  </span>
                  <span className={estilos.subtitulo}>
                    {cumplido ? `Completado ${hito.fecha}` : `Previsto ${hito.fecha}`}
                  </span>
                </span>

                <span className={estilos.marca}>{cumplido ? 'Hecho' : ''}</span>
              </button>
            </li>
          );
        })}
      </ul>

      <p className={estilos.nota}>Tocá un hito para marcarlo. La oficina lo ve al instante.</p>
    </>
  );
}
