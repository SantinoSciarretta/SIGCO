import Blueprint from '../../../components/ui/Blueprint';
import { useDemo } from '../../../datos/contextoDemo';
import {
  HITOS, OBRAS, OBRA_DEL_CAPATAZ,
  avanceDeObra, rubrosDeObra, segmentosDeTorta,
} from '../../../datos/demo';
import {
  colorDeDesvio, estadoDeDesvio, millones, porcentaje, textoDeEstado,
} from '../../../components/ui/semaforo';
import estilos from './DetalleObra.module.css';

/** Radio de los anillos del gráfico de torta. */
const RADIO = 86;

/**
 * Detalle de una obra — la pantalla donde el dueño ve en qué se fue la plata.
 *
 * Cruza tres informaciones que hoy la empresa tiene separadas: en qué rubros se
 * gastó (el gráfico de torta), cuánto se gastó contra lo presupuestado en cada
 * rubro (las barras con la marca del tope), y cuánto avanzó la obra de verdad
 * (la línea de hitos). Ese cruce es lo que permite detectar la situación que el
 * relevamiento marca como más costosa: una obra que gastó mucho sin avanzar.
 *
 * Muestra la obra seleccionada en el tablero.
 */
export default function DetalleObra() {
  const { obraSeleccionada, hitosCompletados } = useDemo();

  const obra = OBRAS[obraSeleccionada];
  const rubros = rubrosDeObra(obraSeleccionada);
  const segmentos = segmentosDeTorta(obraSeleccionada, RADIO);

  const razon = obra.gasto / obra.pres;
  const color = colorDeDesvio(razon);
  const avance = avanceDeObra(obraSeleccionada, hitosCompletados);

  // Hitos cumplidos. En la obra del capataz salen de lo que él fue tildando;
  // en el resto se deducen del porcentaje de avance cargado.
  const hitosCumplidos = obraSeleccionada === OBRA_DEL_CAPATAZ
    ? HITOS.filter((_, i) => hitosCompletados[i]).length
    : Math.round((avance / 100) * HITOS.length);

  const proximoHito = (HITOS[hitosCumplidos] ?? HITOS[HITOS.length - 1]).nombre;

  return (
    <>
      {/* ---------- Encabezado de la obra ---------- */}
      <header className={estilos.encabezado}>
        <div>
          <div className="kicker kicker-acento">
            Obra {String(obraSeleccionada + 1).padStart(2, '0')} · {obra.codigo}
          </div>
          <h2 className={estilos.nombre}>{obra.nombre}</h2>
          <div className={estilos.subtitulo}>
            {obra.dir} · Capataz {obra.capataz} · Inicio 04/03/26
          </div>
        </div>

        <div className={estilos.totales}>
          <div>
            <div className="kicker">Presupuestado</div>
            <div className={`cifra ${estilos.total}`}>{millones(obra.pres)}</div>
          </div>
          <div>
            <div className="kicker">Gastado</div>
            <div className={`cifra ${estilos.total}`} style={{ color }}>
              {millones(obra.gasto)}
            </div>
          </div>
          <div>
            <div className="kicker">Avance físico</div>
            <div className={`cifra ${estilos.total}`}>{avance}%</div>
          </div>
        </div>
      </header>

      <div className={estilos.columnas}>

        {/* ---------- Desglose del gasto ---------- */}
        <Blueprint className={estilos.bloque}>
          <h4>Desglose del gasto</h4>
          <div className="kicker">Sobre lo gastado a hoy</div>

          <div className={estilos.tortaMarco}>
            <svg width="280" height="280" viewBox="0 0 280 280" role="img"
                 aria-label={`Reparto del gasto por rubro de ${obra.nombre}`}>
              {/* Círculos de referencia: la retícula del plano. El color va por
                  CSS y no por atributo: color-mix() es confiable como propiedad
                  de hoja de estilos, no como atributo de presentación SVG. */}
              <circle className={estilos.guiaTorta} cx="140" cy="140" r="104" fill="none" strokeWidth="1" />
              <circle className={estilos.guiaTortaTenue} cx="140" cy="140" r="132" fill="none" strokeWidth="1" />

              {/* Un anillo por rubro. Cada uno muestra solo su porción de la
                  circunferencia (stroke-dasharray) y arranca donde terminó el
                  anterior (stroke-dashoffset). El giro de -90° pone el inicio
                  arriba en lugar de a la derecha. */}
              <g transform="rotate(-90 140 140)" fill="none" strokeWidth="34">
                {segmentos.map((segmento, i) => (
                  <circle
                    key={i}
                    cx="140" cy="140" r={RADIO}
                    stroke={segmento.color}
                    strokeDasharray={segmento.dasharray}
                    strokeDashoffset={segmento.dashoffset}
                  />
                ))}
              </g>
            </svg>

            <div className={estilos.tortaCentro}>
              <div className="kicker">Gastado</div>
              <div className={`cifra ${estilos.tortaValor}`}>{millones(obra.gasto)}</div>
              <div className={estilos.tortaPie}>{porcentaje(razon)} del total</div>
            </div>
          </div>

          <ul className={estilos.referencias}>
            {rubros.map((rubro) => (
              <li key={rubro.nombre}>
                <span className={estilos.referenciaMuestra} style={{ background: rubro.color }} />
                <span className={estilos.referenciaNombre}>{rubro.nombre}</span>
                <span className={`cifra ${estilos.referenciaValor}`}>
                  {porcentaje(rubro.participacion)}
                </span>
              </li>
            ))}
          </ul>
        </Blueprint>

        <div className={estilos.columnaDerecha}>

          {/* ---------- Gastado vs. presupuestado por rubro ---------- */}
          <Blueprint className={estilos.bloque}>
            <div className={estilos.bloqueCabecera}>
              <div>
                <h4>Gastado contra presupuestado, por rubro</h4>
                <div className="kicker">La línea marca el tope de cada rubro</div>
              </div>
              <ul className={estilos.leyenda}>
                <li><span className={estilos.muestraTope} />Tope</li>
                <li><span className={estilos.muestra} style={{ background: 'var(--color-ok)' }} />OK</li>
                <li><span className={estilos.muestra} style={{ background: 'var(--color-alerta)' }} />Al límite</li>
                <li><span className={estilos.muestra} style={{ background: 'var(--color-excedido)' }} />Excedido</li>
              </ul>
            </div>

            <div className={estilos.rubros}>
              {rubros.map((rubro) => {
                const colorRubro = colorDeDesvio(rubro.razon);
                return (
                  <div key={rubro.nombre}>
                    <div className={estilos.rubroCabecera}>
                      <span className={estilos.rubroNombre}>{rubro.nombre}</span>
                      <span className={estilos.rubroCifras}>
                        <span className={`cifra ${estilos.rubroGasto}`} style={{ color: colorRubro }}>
                          {millones(rubro.gastado)}
                        </span>
                        <span className={estilos.rubroPresupuesto}>
                          de {millones(rubro.presupuestado)}
                        </span>
                        {/* El estado va escrito, no solo en color: quien no
                            distingue verde de rojo tiene que poder leerlo. */}
                        <span className={estilos.rubroEstado} style={{ color: colorRubro }}>
                          {textoDeEstado(estadoDeDesvio(rubro.razon))}
                        </span>
                      </span>
                    </div>

                    <div className={`trama ${estilos.rubroBarra}`}>
                      <span
                        className={estilos.rubroRelleno}
                        style={{ width: `${rubro.anchoGasto}%`, background: colorRubro }}
                      />
                      <span className={estilos.rubroTope} style={{ left: `${rubro.anchoTope}%` }} />
                    </div>
                  </div>
                );
              })}
            </div>
          </Blueprint>

          {/* ---------- Hitos ---------- */}
          <Blueprint className={estilos.bloque}>
            <div className={estilos.bloqueCabecera}>
              <h4>Hitos de obra</h4>
              <div className="kicker">
                {hitosCumplidos} de {HITOS.length} completados · próximo: {proximoHito}
              </div>
            </div>

            <div className={estilos.linea}>
              <span className={estilos.lineaBase} />
              <span className={estilos.lineaAvance} style={{ width: `${avance}%` }} />

              <div className={estilos.hitos}>
                {HITOS.map((hito, i) => {
                  const cumplido = i < hitosCumplidos;
                  return (
                    <div key={hito.nombre} className={estilos.hito}>
                      <span className={`${estilos.hitoMarca} ${cumplido ? estilos.hitoMarcaOk : ''}`.trim()}>
                        {cumplido && (
                          <svg width="13" height="13" viewBox="0 0 24 24" fill="none"
                               stroke="currentColor" strokeWidth="2.4" strokeLinecap="round"
                               strokeLinejoin="round">
                            <path d="M20 6 9 17l-5-5" />
                          </svg>
                        )}
                      </span>
                      <span className={`${estilos.hitoNombre} ${cumplido ? '' : estilos.hitoPendiente}`.trim()}>
                        {hito.nombre}
                      </span>
                      <span className={estilos.hitoFecha}>
                        {cumplido ? hito.fecha : `prev. ${hito.fecha}`}
                      </span>
                    </div>
                  );
                })}
              </div>
            </div>
          </Blueprint>

        </div>
      </div>
    </>
  );
}
