import { useCallback, useEffect, useState } from 'react';
import CabeceraCapataz from './CabeceraCapataz';
import { avanceDeObra, completarHito, fecha } from '../../../modules/seguimiento/seguimientoApi';
import { useSesion } from '../../../modules/sesion/useSesion';
import { useObraDelCapataz } from './useObraDelCapataz';
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
 * ------------------------------------------------------------------
 *  Quién puede marcar
 * ------------------------------------------------------------------
 *
 * Marcar un hito exige `seguimiento.editar`. Según la matriz del informe, el
 * **Capataz de Obra tiene Seguimiento en "Consulta"**: mira el avance de su
 * obra pero no lo modifica. Quien marca es el Capataz General, que tiene el
 * módulo en "Total".
 *
 * El diseño original mostraba al capataz marcando hitos; la matriz dice otra
 * cosa y la matriz manda. La pantalla es la misma para los dos roles y se
 * adapta: con permiso las filas son botones, sin permiso son solo lectura.
 *
 * De todos modos esto es presentación: el backend rechaza el PATCH con 403 si
 * el rol no tiene el permiso, aunque alguien fuerce el botón.
 */
export default function Hitos() {
  const { puede } = useSesion();
  const { obra, cargando: buscandoObra, error: errorObra } = useObraDelCapataz();

  const [avance, setAvance] = useState(null);
  const [error, setError] = useState(null);
  const [marcando, setMarcando] = useState(null);

  const puedeMarcar = puede('seguimiento.editar');

  const recargar = useCallback(() => {
    if (!obra) return;
    avanceDeObra(obra.idObra)
      .then((a) => { setAvance(a); setError(null); })
      .catch((fallo) => setError(fallo.mensaje));
  }, [obra]);

  useEffect(() => { recargar(); }, [recargar]);

  const marcar = async (hito) => {
    if (!puedeMarcar || hito.estado === 'Completado') return;
    setMarcando(hito.idHito);
    setError(null);
    try {
      // La fecha de cumplimiento es hoy: el capataz marca el hito cuando lo
      // termina, no después. El informe la exige al completar.
      const actualizado = await completarHito(hito.idHito, {
        fechaCumplimiento: new Date().toISOString().slice(0, 10),
      });
      setAvance(actualizado);
    } catch (fallo) {
      setError(fallo.mensaje);
    } finally {
      setMarcando(null);
    }
  };

  if (buscandoObra) {
    return <p className={estilos.nota}>Buscando tu obra…</p>;
  }
  if (errorObra || !obra) {
    return <p className={estilos.nota}>{errorObra ?? 'No tenés ninguna obra asignada.'}</p>;
  }

  const porcentaje = avance ? Math.round(Number(avance.avanceFisico)) : 0;
  const hitos = avance?.hitos ?? [];

  return (
    <>
      <CabeceraCapataz titulo={`Hitos · ${corto(obra.direccionObra)}`}>
        <div className={estilos.resumen}>
          <div className={estilos.cifraFila}>
            <span className={`cifra ${estilos.numero}`}>{porcentaje}</span>
            <span className={`cifra ${estilos.simbolo}`}>%</span>
          </div>
          <div className={estilos.detalle}>
            avance<br />
            {avance ? `${avance.hitosCompletados}/${avance.hitosTotales} hitos` : '—'}
          </div>
        </div>

        <div className={estilos.pista}>
          <span className={estilos.relleno} style={{ width: `${porcentaje}%` }} />
        </div>
      </CabeceraCapataz>

      {error && <p className={estilos.error}>{error}</p>}

      {hitos.length === 0 && (
        <p className={estilos.nota}>
          Esta obra todavía no tiene hitos definidos. Los define la oficina
          desde el módulo Avance.
        </p>
      )}

      <ul className={estilos.lista}>
        {hitos.map((hito) => {
          const cumplido = hito.estado === 'Completado';
          const ocupado = marcando === hito.idHito;

          return (
            <li key={hito.idHito}>
              <button
                type="button"
                className={`${estilos.fila} ${cumplido ? estilos.filaHecha : ''}`.trim()}
                onClick={() => marcar(hito)}
                disabled={!puedeMarcar || cumplido || ocupado}
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
                    {hito.nombreHito}
                  </span>
                  <span className={estilos.subtitulo}>
                    {cumplido
                      ? `Completado ${fecha(hito.fechaCumplimiento)}`
                      : `Pesa ${Math.round(Number(hito.ponderacion))}% del avance`}
                  </span>
                </span>

                <span className={estilos.marca}>
                  {ocupado ? '…' : (cumplido ? 'Hecho' : '')}
                </span>
              </button>
            </li>
          );
        })}
      </ul>

      <p className={estilos.nota}>
        {puedeMarcar
          ? 'Tocá un hito para marcarlo. La oficina lo ve al instante.'
          : 'Solo se consulta: los hitos los marca el capataz general.'}
      </p>
    </>
  );
}

/** "Av. Cabildo 2340, Belgrano" → "Av. Cabildo 2340", que es lo que entra. */
function corto(direccion) {
  return direccion.split(',')[0];
}
