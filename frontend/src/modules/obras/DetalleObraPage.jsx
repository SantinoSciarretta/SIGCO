import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import Blueprint from '../../components/ui/Blueprint';
import { obtenerObra } from './obrasApi';
import { estadoFinanciero } from '../gastos/gastosApi';
import { avanceDeObra, fecha } from '../seguimiento/seguimientoApi';
import { planDeCobro } from '../cobros/cobrosApi';
import { listarPedidos } from '../compras/pedidosApi';
import { useSesion } from '../sesion/useSesion';
import estilos from './DetalleObra.module.css';

/** Radio de los anillos del gráfico de torta. */
const RADIO = 86;

/**
 * Tonos para los rubros de la torta.
 *
 * Salen de la rampa de azules del sistema, de más oscuro a más claro, y no de
 * colores inventados. El orden importa: el rubro que más pesa queda en el tono
 * más fuerte, así el gráfico se lee incluso en blanco y negro.
 *
 * NO se usan los colores del semáforo acá: verde, amarillo y rojo significan
 * una cosa concreta en este sistema —dentro, al límite, excedido— y usarlos
 * para distinguir rubros los vaciaría de significado.
 */
const TONOS = [
  'var(--color-accent-900)', 'var(--color-accent-700)', 'var(--color-accent-500)',
  'var(--color-accent-400)', 'var(--color-accent-300)', 'var(--color-accent-200)',
];

/**
 * Ficha de obra — la pantalla donde el dueño ve en qué se fue la plata.
 *
 * Es el lugar donde convergen todos los módulos para UNA obra. Hasta ahora,
 * para ver una obra completa había que recorrer seis pantallas distintas:
 * Presupuestos para el total, Gastos para el desvío, Avance para los hitos,
 * Cobranzas para el saldo y Pedidos para los materiales.
 *
 * Cruza las tres informaciones que la empresa hoy tiene separadas: en qué
 * rubros se gastó (la torta), cuánto se gastó contra lo presupuestado en cada
 * rubro (las barras con la marca del tope), y cuánto avanzó la obra de verdad
 * (la línea de hitos).
 *
 * Ese cruce es lo que permite detectar la situación que el relevamiento marca
 * como más costosa: **una obra que gastó mucho sin avanzar**.
 *
 * ------------------------------------------------------------------
 *  Por qué consulta cinco endpoints y no uno
 * ------------------------------------------------------------------
 *
 * Mismo criterio que el Tablero: cada número lo calcula el módulo que es su
 * dueño. El semáforo lo da Gastos, el avance Seguimiento, el saldo Cobros. Un
 * endpoint nuevo que devolviera todo junto tendría que recalcularlos, y el día
 * que cambie un umbral habría dos lugares para cambiarlo.
 *
 * Las cinco llamadas salen en paralelo, así que la pantalla tarda lo que tarda
 * la más lenta, no la suma.
 */
export default function DetalleObraPage() {
  const { id } = useParams();
  const { puede } = useSesion();

  const [obra, setObra] = useState(null);
  const [finanzas, setFinanzas] = useState(null);
  const [avance, setAvance] = useState(null);
  const [cobros, setCobros] = useState(null);
  const [pedidos, setPedidos] = useState([]);
  const [error, setError] = useState(null);

  /**
   * Para qué obra son los datos que hay cargados.
   *
   * "Está cargando" se DERIVA de comparar esto con el id de la dirección, en
   * lugar de ser un estado aparte que hay que acordarse de poner en true al
   * entrar y en false al salir. Así, al pasar de una obra a otra la pantalla
   * muestra "consultando" sola, sin una línea que lo diga.
   */
  const [cargadoPara, setCargadoPara] = useState(null);
  const cargando = cargadoPara !== id;

  useEffect(() => {
    let vigente = true;

    // La obra es lo único obligatorio: sin ella no hay pantalla. El resto son
    // partes que pueden no existir todavía —una obra recién creada no tiene
    // gastos, ni hitos, ni plan de cobro— y su ausencia no es un error.
    obtenerObra(id)
      .then((datos) => { if (vigente) { setObra(datos); setError(null); } })
      .catch((fallo) => { if (vigente) setError(fallo.mensaje); })
      .finally(() => { if (vigente) setCargadoPara(id); });

    if (puede('gastos.ver')) {
      estadoFinanciero(id).then((d) => { if (vigente) setFinanzas(d); }).catch(() => {});
    }
    if (puede('seguimiento.ver')) {
      avanceDeObra(id).then((d) => { if (vigente) setAvance(d); }).catch(() => {});
    }
    if (puede('cobros.ver')) {
      planDeCobro(id).then((d) => { if (vigente) setCobros(d); }).catch(() => {});
    }
    if (puede('compras.ver')) {
      listarPedidos({ obra: id }).then((d) => { if (vigente) setPedidos(d); }).catch(() => {});
    }

    return () => { vigente = false; };
  }, [id, puede]);

  if (cargando) {
    return <p className={estilos.aviso}>Consultando la obra…</p>;
  }
  if (error || !obra) {
    return <p className={estilos.errorGeneral}>{error ?? 'No se encontró la obra.'}</p>;
  }

  const rubros = prepararRubros(finanzas);
  const segmentos = armarSegmentos(rubros);
  const avanceFisico = avance ? Math.round(Number(avance.avanceFisico)) : 0;

  return (
    <>
      <p className={estilos.volver}>
        <Link to="/obras">← Volver a obras</Link>
      </p>

      {/* ---------- Encabezado de la obra ---------- */}
      <header className={estilos.encabezado}>
        <div>
          <div className="kicker kicker-acento">
            Obra {String(obra.idObra).padStart(2, '0')} · {obra.tipoObra}
          </div>
          <h2 className={estilos.nombre}>{obra.direccionObra}</h2>
          <div className={estilos.subtitulo}>
            {obra.nombreCliente} · {obra.tipoInmueble} · {obra.estado}
            {obra.fechaInicioReal && <> · inicio {fecha(obra.fechaInicioReal)}</>}
          </div>
        </div>

        <div className={estilos.totales}>
          {finanzas && (
            <>
              <div>
                <div className="kicker">Presupuestado</div>
                <div className={`cifra ${estilos.total}`}>
                  {millones(finanzas.totalPresupuestado)}
                </div>
              </div>
              <div>
                <div className="kicker">Gastado</div>
                <div className={`cifra ${estilos.total}`}
                     style={{ color: colorDeSemaforo(finanzas.semaforoGeneral) }}>
                  {millones(finanzas.totalGastado)}
                </div>
              </div>
            </>
          )}
          {avance && (
            <div>
              <div className="kicker">Avance físico</div>
              <div className={`cifra ${estilos.total}`}>{avanceFisico}%</div>
            </div>
          )}
          {cobros && (
            <div>
              <div className="kicker">Por cobrar</div>
              <div className={`cifra ${estilos.total}`}>{millones(cobros.saldoPendiente)}</div>
            </div>
          )}
        </div>
      </header>

      {/* El cruce que el informe pide: si el financiero va más rápido que el
          físico, la obra está gastando más de lo que construye. */}
      {avance?.alertaDesfasaje && (
        <p className={estilos.alerta}>
          Esta obra gastó el <b>{Math.round(Number(avance.avanceFinanciero))}%</b> del
          presupuesto y avanzó el <b>{avanceFisico}%</b>. Va{' '}
          {Math.round(Number(avance.desfasaje))} puntos más rápido gastando que
          construyendo.
        </p>
      )}

      {!finanzas && (
        <Blueprint className={estilos.bloque}>
          <p className={estilos.aviso}>
            Esta obra todavía no tiene un presupuesto definitivo aprobado, así que
            no hay contra qué comparar los gastos.{' '}
            {puede('presupuestos.ver') && (
              <Link to={`/presupuestos?obra=${obra.idObra}`}>Ver sus presupuestos</Link>
            )}
          </p>
        </Blueprint>
      )}

      {finanzas && (
        <div className={estilos.columnas}>

          {/* ---------- Desglose del gasto ---------- */}
          <Blueprint className={estilos.bloque}>
            <h4>Desglose del gasto</h4>
            <div className="kicker">Sobre lo gastado a hoy</div>

            <div className={estilos.tortaMarco}>
              <svg width="280" height="280" viewBox="0 0 280 280" role="img"
                   aria-label={`Reparto del gasto por rubro de ${obra.direccionObra}`}>
                {/* Círculos de referencia: la retícula del plano. El color va
                    por CSS y no por atributo: color-mix() es confiable como
                    propiedad de hoja de estilos, no como atributo SVG. */}
                <circle className={estilos.guiaTorta} cx="140" cy="140" r="104"
                        fill="none" strokeWidth="1" />
                <circle className={estilos.guiaTortaTenue} cx="140" cy="140" r="132"
                        fill="none" strokeWidth="1" />

                {/* Un anillo por rubro. Cada uno muestra solo su porción de la
                    circunferencia (stroke-dasharray) y arranca donde terminó el
                    anterior (stroke-dashoffset). El giro de -90° pone el inicio
                    arriba en lugar de a la derecha. */}
                <g transform="rotate(-90 140 140)" fill="none" strokeWidth="34">
                  {segmentos.map((segmento, i) => (
                    <circle key={i} cx="140" cy="140" r={RADIO}
                            stroke={segmento.color}
                            strokeDasharray={segmento.dasharray}
                            strokeDashoffset={segmento.dashoffset} />
                  ))}
                </g>
              </svg>

              <div className={estilos.tortaCentro}>
                <div className="kicker">Gastado</div>
                <div className={`cifra ${estilos.tortaValor}`}>
                  {millones(finanzas.totalGastado)}
                </div>
                <div className={estilos.tortaPie}>
                  {Math.round(Number(finanzas.porcentajeConsumido))}% del presupuesto
                </div>
              </div>
            </div>

            <ul className={estilos.referencias}>
              {rubros.filter((r) => r.gastado > 0).map((rubro, i) => (
                <li key={rubro.idRubro}>
                  <span className={estilos.referenciaMuestra}
                        style={{ background: TONOS[i % TONOS.length] }} />
                  <span className={estilos.referenciaNombre}>{rubro.nombreRubro}</span>
                  <span className={`cifra ${estilos.referenciaValor}`}>
                    {Math.round(rubro.participacion * 100)}%
                  </span>
                </li>
              ))}
            </ul>

            {Number(finanzas.totalGastoHormiga) > 0 && (
              <p className={estilos.hormiga}>
                De eso, <b>{pesos(finanzas.totalGastoHormiga)}</b> son gastos
                hormiga: los chicos que antes no quedaban registrados en ningún lado.
              </p>
            )}
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
                  const color = colorDeSemaforo(rubro.semaforo);
                  return (
                    <div key={rubro.idRubro}>
                      <div className={estilos.rubroCabecera}>
                        <span className={estilos.rubroNombre}>{rubro.nombreRubro}</span>
                        <span className={estilos.rubroCifras}>
                          <span className={`cifra ${estilos.rubroGasto}`} style={{ color }}>
                            {millones(rubro.gastado)}
                          </span>
                          <span className={estilos.rubroPresupuesto}>
                            de {millones(rubro.presupuestado)}
                          </span>
                          {/* El estado va escrito, no solo en color: quien no
                              distingue verde de rojo tiene que poder leerlo. */}
                          <span className={estilos.rubroEstado} style={{ color }}>
                            {textoDeSemaforo(rubro.semaforo)}
                          </span>
                        </span>
                      </div>

                      <div className={`trama ${estilos.rubroBarra}`}>
                        <span className={estilos.rubroRelleno}
                              style={{ width: `${rubro.anchoGasto}%`, background: color }} />
                        <span className={estilos.rubroTope}
                              style={{ left: `${rubro.anchoTope}%` }} />
                      </div>
                    </div>
                  );
                })}
              </div>
            </Blueprint>

            {/* ---------- Hitos ---------- */}
            {avance && avance.hitos.length > 0 && (
              <Blueprint className={estilos.bloque}>
                <div className={estilos.bloqueCabecera}>
                  <h4>Hitos de obra</h4>
                  <div className="kicker">
                    {avance.hitosCompletados} de {avance.hitosTotales} completados
                    {proximoHito(avance) && <> · próximo: {proximoHito(avance)}</>}
                  </div>
                </div>

                <div className={estilos.linea}>
                  <span className={estilos.lineaBase} />
                  <span className={estilos.lineaAvance} style={{ width: `${avanceFisico}%` }} />

                  <div className={estilos.hitos}>
                    {avance.hitos.map((hito) => {
                      const cumplido = hito.estado === 'Completado';
                      return (
                        <div key={hito.idHito} className={estilos.hito}>
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
                            {hito.nombreHito}
                          </span>
                          <span className={estilos.hitoFecha}>
                            {cumplido ? fecha(hito.fechaCumplimiento)
                              : `${Math.round(Number(hito.ponderacion))}%`}
                          </span>
                        </div>
                      );
                    })}
                  </div>
                </div>
              </Blueprint>
            )}

          </div>
        </div>
      )}

      {/* ---------- A dónde seguir ---------- */}
      <Blueprint className={estilos.bloque}>
        <div className={estilos.bloqueCabecera}>
          <h4>Todo lo de esta obra</h4>
          <div className="kicker">Cada módulo, ya filtrado</div>
        </div>

        <div className={estilos.accesos}>
          <Acceso permiso="presupuestos.ver" puede={puede}
                  a={`/presupuestos?obra=${obra.idObra}`}
                  titulo="Presupuestos" detalle="Cotización, anteproyecto y definitivo" />
          <Acceso permiso="gastos.ver" puede={puede}
                  a={`/gastos?obra=${obra.idObra}`}
                  titulo="Gastos" detalle={finanzas
                    ? `${millones(finanzas.totalGastado)} gastados`
                    : 'Cargar y ver gastos'} />
          <Acceso permiso="compras.ver" puede={puede}
                  a={`/pedidos?obra=${obra.idObra}`}
                  titulo="Pedidos" detalle={`${pedidos.length} pedidos de materiales`} />
          <Acceso permiso="seguimiento.ver" puede={puede}
                  a={`/seguimiento?obra=${obra.idObra}`}
                  titulo="Avance" detalle={avance
                    ? `${avance.hitosCompletados}/${avance.hitosTotales} hitos`
                    : 'Definir los hitos'} />
          <Acceso permiso="cobros.ver" puede={puede}
                  a={`/cobranzas?obra=${obra.idObra}`}
                  titulo="Cobranzas" detalle={cobros
                    ? `${millones(cobros.saldoPendiente)} por cobrar`
                    : 'Generar el plan de cobro'} />
          <Acceso permiso="personal.ver" puede={puede}
                  a={`/personal?obra=${obra.idObra}`}
                  titulo="Personal" detalle="Operarios asignados e inasistencias" />
        </div>
      </Blueprint>
    </>
  );
}

/* ========================================================================== */

/** Un acceso a otro módulo, que solo aparece si el usuario puede entrar. */
function Acceso({ permiso, puede, a, titulo, detalle }) {
  if (!puede(permiso)) return null;
  return (
    <Link to={a} className={estilos.acceso}>
      <span className={estilos.accesoTitulo}>{titulo}</span>
      <span className={estilos.accesoDetalle}>{detalle}</span>
    </Link>
  );
}

/* --------------------------------------------------------------------------
   Preparación de los datos para dibujar.

   Nada de esto decide nada: el semáforo, los totales y los porcentajes llegan
   ya calculados desde el backend. Acá solo se traducen a anchos y ángulos.
   -------------------------------------------------------------------------- */

function prepararRubros(finanzas) {
  if (!finanzas) return [];

  const rubros = finanzas.rubros.map((r) => ({
    ...r,
    gastado: Number(r.gastado),
    presupuestado: Number(r.presupuestado),
  }));

  // La escala de las barras es común a todos los rubros: cada barra se mide
  // contra el mayor valor del conjunto, no contra sí misma. Si cada una usara
  // su propia escala, un rubro chico excedido se vería igual de largo que uno
  // grande, y la comparación entre rubros dejaría de significar algo.
  const escala = Math.max(1, ...rubros.map((r) => Math.max(r.gastado, r.presupuestado)));
  const totalGastado = Math.max(1, Number(finanzas.totalGastado));

  return rubros
    .map((r) => ({
      ...r,
      participacion: r.gastado / totalGastado,
      anchoGasto: (r.gastado / escala) * 100,
      anchoTope: (r.presupuestado / escala) * 100,
    }))
    .sort((a, b) => b.gastado - a.gastado);
}

/** Cada rubro ocupa su porción del anillo, empezando donde terminó el anterior. */
function armarSegmentos(rubros) {
  const circunferencia = 2 * Math.PI * RADIO;
  let acumulado = 0;

  return rubros
    .filter((r) => r.gastado > 0)
    .map((rubro, i) => {
      const largo = rubro.participacion * circunferencia;
      const segmento = {
        color: TONOS[i % TONOS.length],
        dasharray: `${largo} ${circunferencia - largo}`,
        dashoffset: -acumulado,
      };
      acumulado += largo;
      return segmento;
    });
}

function proximoHito(avance) {
  return avance.hitos.find((h) => h.estado !== 'Completado')?.nombreHito ?? null;
}

/* ---------- Presentación ---------- */

function colorDeSemaforo(semaforo) {
  if (semaforo === 'Rojo') return 'var(--color-excedido)';
  if (semaforo === 'Amarillo') return 'var(--color-alerta)';
  if (semaforo === 'Sin presupuesto') return 'var(--color-neutral-500)';
  return 'var(--color-ok)';
}

function textoDeSemaforo(semaforo) {
  if (semaforo === 'Rojo') return 'Excedido';
  if (semaforo === 'Amarillo') return 'Al límite';
  if (semaforo === 'Sin presupuesto') return 'Sin presupuesto';
  return 'En presupuesto';
}

function pesos(monto) {
  return '$ ' + Math.round(Number(monto ?? 0)).toLocaleString('es-AR');
}

/** Abreviado para las cifras grandes: 12980000 → "$12,98 M" */
function millones(monto) {
  const valor = Number(monto ?? 0) / 1000000;
  if (valor === 0) return '$0';
  const texto = valor >= 10 ? valor.toFixed(1) : valor.toFixed(2);
  return '$' + texto.replace('.', ',') + ' M';
}
