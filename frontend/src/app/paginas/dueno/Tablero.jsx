import { useNavigate } from 'react-router-dom';
import Blueprint from '../../../components/ui/Blueprint';
import { useDemo } from '../../../datos/contextoDemo';
import { OBRAS, avanceDeObra, totalPorCobrar } from '../../../datos/demo';
import { colorDeDesvio, millones, porcentaje } from '../../../components/ui/semaforo';
import estilos from './Tablero.module.css';

/**
 * Tablero del dueño — pantalla de entrada del sistema.
 *
 * Reúne en un solo lugar lo que hoy el dueño tiene que ir a buscar obra por
 * obra: cuánto se gastó contra lo presupuestado, cuánto hay por cobrar y cómo
 * viene el avance. No genera información propia, consolida la del resto de los
 * módulos.
 *
 * Al tocar una obra —en el gráfico o en la tabla— queda seleccionada, y el
 * detalle de obra pasa a mostrar esa. Por eso la selección vive en el estado
 * compartido y no dentro de esta pantalla.
 */
export default function Tablero() {
  const { obraSeleccionada, seleccionarObra, hitosCompletados } = useDemo();
  const navegar = useNavigate();

  // Escala del gráfico: la barra más alta es el mayor valor de todo el
  // conjunto, así todas las obras se comparan contra la misma referencia.
  const escala = Math.max(...OBRAS.map((o) => Math.max(o.pres, o.gasto)));

  const irAlDetalle = (indice) => {
    seleccionarObra(indice);
    // El detalle de obra del diseño todavía muestra datos de muestra: la
    // información que consolida (gastos por rubro, hitos) la producen módulos
    // que aún no existen.
    navegar('/vista-diseno/obra');
  };

  const indicadores = [
    {
      titulo: 'Obras activas', codigo: 'A-01',
      valor: String(OBRAS.length), unidad: 'en curso',
      pie: '2 cierran este mes · 1 en pausa',
    },
    {
      titulo: 'Por cobrar esta semana', codigo: 'A-02',
      valor: millones(totalPorCobrar()).replace('$', '').replace(' M', ''),
      unidad: 'millones $',
      pie: 'Vencen 3 certificados el viernes',
    },
    {
      titulo: 'Pedidos a aprobar', codigo: 'A-03',
      valor: '3', unidad: 'esperando',
      pie: 'El más viejo hace 2 días · Casa Aguirre',
    },
    {
      titulo: 'Presupuestos sin respuesta', codigo: 'A-04',
      valor: '7', unidad: 'enviados',
      pie: '$41,2 M en juego · 4 hace +15 días',
    },
  ];

  return (
    <>
      <div className={estilos.tituloFila}>
        <h2 className={estilos.fecha}>{fechaDeHoy()}</h2>
        <span className="kicker">Semana {semanaDelAno()} · cierre viernes</span>
      </div>

      {/* ---------- Indicadores ---------- */}
      <div className={estilos.indicadores}>
        {indicadores.map((indicador) => (
          <Blueprint key={indicador.codigo} className={estilos.indicador}>
            <div className={estilos.indicadorCabecera}>
              <span className="kicker kicker-acento">{indicador.titulo}</span>
              <span className={estilos.indicadorCodigo}>{indicador.codigo}</span>
            </div>
            <div className={estilos.indicadorValorFila}>
              <span className={`cifra ${estilos.indicadorValor}`}>{indicador.valor}</span>
              <span className={estilos.indicadorUnidad}>{indicador.unidad}</span>
            </div>
            <p className={estilos.indicadorPie}>{indicador.pie}</p>
          </Blueprint>
        ))}
      </div>

      {/* ---------- Presupuestado vs. gastado ---------- */}
      <Blueprint className={estilos.bloque}>
        <div className={estilos.bloqueCabecera}>
          <div>
            <h4>Presupuestado vs. gastado por obra</h4>
            <span className="kicker">Certificado a hoy · en millones de $</span>
          </div>
          <ul className={estilos.leyenda}>
            <li><span className={`${estilos.muestra} trama ${estilos.muestraTrama}`} />Presupuestado</li>
            <li><span className={estilos.muestra} style={{ background: 'var(--color-ok)' }} />En presupuesto</li>
            <li><span className={estilos.muestra} style={{ background: 'var(--color-alerta)' }} />Cerca del límite</li>
            <li><span className={estilos.muestra} style={{ background: 'var(--color-excedido)' }} />Excedido</li>
          </ul>
        </div>

        <div className={estilos.grafico}>
          {/* Líneas guía horizontales: dan referencia de altura sin ejes numerados. */}
          <span className={estilos.guia} style={{ top: '0%' }} />
          <span className={estilos.guia} style={{ top: '25%' }} />
          <span className={estilos.guia} style={{ top: '50%' }} />
          <span className={estilos.guia} style={{ top: '75%' }} />

          <div className={estilos.barras}>
            {OBRAS.map((obra, i) => {
              const color = colorDeDesvio(obra.gasto / obra.pres);
              return (
                <button
                  key={obra.codigo}
                  type="button"
                  className={estilos.columna}
                  onClick={() => seleccionarObra(i)}
                  aria-pressed={i === obraSeleccionada}
                  aria-label={`${obra.nombre}: gastado ${millones(obra.gasto)} de ${millones(obra.pres)}`}
                >
                  <span
                    className={`trama ${estilos.barraPresupuesto}`}
                    style={{ height: `${(obra.pres / escala) * 100}%` }}
                  />
                  <span
                    className={estilos.barraGasto}
                    style={{ height: `${(obra.gasto / escala) * 100}%`, background: color }}
                  >
                    <span className={estilos.barraEtiqueta} style={{ color }}>
                      {millones(obra.gasto)}
                    </span>
                  </span>
                </button>
              );
            })}
          </div>
        </div>

        <div className={estilos.ejeX}>
          {OBRAS.map((obra, i) => (
            <div
              key={obra.codigo}
              className={`${estilos.ejeItem} ${i === obraSeleccionada ? estilos.ejeItemActivo : ''}`.trim()}
            >
              <div className={estilos.ejeNombre}>{obra.corto}</div>
              <div className={estilos.ejeRazon}>
                {porcentaje(obra.gasto / obra.pres)} del presupuesto
              </div>
            </div>
          ))}
        </div>
      </Blueprint>

      {/* ---------- Obras activas ---------- */}
      <Blueprint className={estilos.bloque}>
        <div className={estilos.bloqueCabecera}>
          <h4>Obras activas</h4>
          <span className="kicker">Avance físico certificado</span>
        </div>

        <div className="scroll-x">
          <table className="table">
            <thead>
              <tr>
                <th style={{ width: 26 }}>#</th>
                <th>Obra</th>
                <th>Capataz</th>
                <th style={{ width: 290 }}>Avance físico</th>
                <th style={{ textAlign: 'right' }}>Por cobrar</th>
                <th style={{ width: 78 }} />
              </tr>
            </thead>
            <tbody>
              {OBRAS.map((obra, i) => {
                const avance = avanceDeObra(i, hitosCompletados);
                const color = colorDeDesvio(obra.gasto / obra.pres);

                return (
                  <tr
                    key={obra.codigo}
                    className={i === obraSeleccionada ? estilos.filaActiva : undefined}
                    onClick={() => irAlDetalle(i)}
                    style={{ cursor: 'pointer' }}
                  >
                    <td className={estilos.filaNumero}>{String(i + 1).padStart(2, '0')}</td>
                    <td>
                      <div className={estilos.obraNombre}>
                        {/* El cuadrito repite el estado del semáforo del gráfico. */}
                        <span className={estilos.punto} style={{ background: color }} />
                        <span>{obra.nombre}</span>
                      </div>
                      <div className={estilos.obraDir}>{obra.dir}</div>
                    </td>
                    <td className={estilos.capataz}>{obra.capataz}</td>
                    <td>
                      <div className={estilos.avance}>
                        <span className={estilos.avancePista}>
                          <span className={estilos.avanceRelleno} style={{ width: `${avance}%` }} />
                        </span>
                        <span className={`cifra ${estilos.avanceTexto}`}>{avance}%</span>
                      </div>
                    </td>
                    <td className={`cifra ${estilos.cobrar}`}>{millones(obra.cobrar)}</td>
                    <td className={estilos.verCelda}>
                      <span className={estilos.ver}>Ver →</span>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      </Blueprint>
    </>
  );
}

/** "Martes 18 de agosto", con la fecha real del equipo. */
function fechaDeHoy() {
  const texto = new Date().toLocaleDateString('es-AR', {
    weekday: 'long', day: 'numeric', month: 'long',
  });
  return texto.charAt(0).toUpperCase() + texto.slice(1);
}

/** Número de semana del año, que es como la empresa organiza los cierres. */
function semanaDelAno() {
  const hoy = new Date();
  const inicioDeAno = new Date(hoy.getFullYear(), 0, 1);
  const dias = Math.floor((hoy - inicioDeAno) / 86400000);
  return Math.ceil((dias + inicioDeAno.getDay() + 1) / 7);
}
