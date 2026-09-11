import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import Blueprint from '../../../components/ui/Blueprint';
import {
  cargarTablero, colorDeSemaforo, millones, pesos, porcentaje, textoDeSemaforo,
} from '../../../modules/dashboard/tableroApi';
import estilos from './Tablero.module.css';

/**
 * Tablero del dueño — pantalla de entrada del sistema.
 *
 * Reúne en un solo lugar lo que hoy el dueño tiene que ir a buscar obra por
 * obra, en planillas distintas: cuánto se gastó contra lo presupuestado, cuánto
 * hay por cobrar, cómo viene el avance y qué está esperando una decisión suya.
 *
 * No genera información propia: consolida la del resto de los módulos. El
 * semáforo, el avance y el saldo llegan ya calculados desde el backend, y esta
 * pantalla solo decide cómo se escriben. Si los recalculara acá, el tablero y
 * la ficha de la obra podrían mostrar números distintos para lo mismo.
 *
 * Hasta el módulo Dashboard esta pantalla mostraba datos de muestra, porque los
 * módulos que producen esta información todavía no existían.
 */
export default function Tablero() {
  const [tablero, setTablero] = useState(null);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState(null);
  const navegar = useNavigate();

  useEffect(() => {
    let vigente = true;
    cargarTablero()
      .then((datos) => { if (vigente) { setTablero(datos); setError(null); } })
      .catch((fallo) => { if (vigente) setError(fallo.mensaje); })
      .finally(() => { if (vigente) setCargando(false); });
    return () => { vigente = false; };
  }, []);

  if (cargando) {
    return <p className={estilos.aviso}>Consultando el estado de las obras…</p>;
  }

  if (error) {
    return (
      <Blueprint className={estilos.bloque}>
        <p className={estilos.errorGeneral}>{error}</p>
      </Blueprint>
    );
  }

  const { resumen, obras, pendientes } = tablero;

  // Escala del gráfico: la barra más alta es el mayor valor de todo el
  // conjunto, así todas las obras se comparan contra la misma referencia.
  const escala = Math.max(
    1,
    ...obras.map((o) => Math.max(Number(o.totalPresupuestado), Number(o.totalGastado))),
  );

  const indicadores = [
    {
      titulo: 'Obras en ejecución', codigo: 'A-01',
      valor: String(resumen.obrasEnEjecucion), unidad: 'en curso',
      pie: `${resumen.obrasEnPresupuestacion} en presupuestación`,
    },
    {
      titulo: 'Por cobrar esta semana', codigo: 'A-02',
      valor: millones(resumen.porCobrarEstaSemana), unidad: 'millones $',
      pie: `${pesos(resumen.saldoPorCobrar)} pendientes en total`,
    },
    {
      titulo: 'Ganancia estimada', codigo: 'A-03',
      valor: millones(resumen.gananciaEstimada), unidad: 'millones $',
      // Presupuestado menos gastado. Se recalcula con cada gasto que se carga.
      pie: `${pesos(resumen.totalGastado)} gastados de ${pesos(resumen.totalPresupuestado)}`,
    },
    {
      titulo: 'Esperando una decisión', codigo: 'A-04',
      valor: String(pendientes.length), unidad: 'pendientes',
      pie: resumen.pendientesUrgentes > 0
        ? `${resumen.pendientesUrgentes} ya están demoradas`
        : 'Ninguna demorada',
    },
  ];

  return (
    <>
      <div className={estilos.tituloFila}>
        <h2 className={estilos.fecha}>{fechaDeHoy()}</h2>
        <span className="kicker">
          {resumen.obrasExcedidas > 0
            ? `${resumen.obrasExcedidas} obra${resumen.obrasExcedidas > 1 ? 's' : ''} excedida${resumen.obrasExcedidas > 1 ? 's' : ''}`
            : 'Ninguna obra excedida'}
        </span>
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

      {/* ---------- Esperando una decisión ---------- */}
      {pendientes.length > 0 && (
        <Blueprint className={estilos.bloque}>
          <div className={estilos.bloqueCabecera}>
            <div>
              <h4>Esperando una decisión tuya</h4>
              <span className="kicker">
                Solo lo que nadie más puede destrabar
              </span>
            </div>
          </div>

          <ul className={estilos.pendientes}>
            {pendientes.map((p, i) => (
              <li key={`${p.tipo}-${i}`}>
                <button type="button" className={estilos.pendiente}
                        onClick={() => navegar(p.ruta)}>
                  <span className={`${estilos.pendienteTipo} ${
                    p.urgencia === 'alta' ? estilos.pendienteUrgente : ''}`.trim()}>
                    {p.tipo}
                  </span>
                  <span className={estilos.pendienteTexto}>
                    <span className={estilos.pendienteTitulo}>{p.titulo}</span>
                    <span className={estilos.pendienteDetalle}>{p.detalle}</span>
                  </span>
                  <span className={estilos.ver}>Ver →</span>
                </button>
              </li>
            ))}
          </ul>
        </Blueprint>
      )}

      {obras.length === 0 && (
        <Blueprint className={estilos.bloque}>
          <p className={estilos.aviso}>
            No hay obras en ejecución. Una obra pasa a ejecución cuando se
            aprueba su presupuesto definitivo.
          </p>
        </Blueprint>
      )}

      {/* ---------- Presupuestado vs. gastado ---------- */}
      {obras.length > 0 && (
        <Blueprint className={estilos.bloque}>
          <div className={estilos.bloqueCabecera}>
            <div>
              <h4>Presupuestado vs. gastado por obra</h4>
              <span className="kicker">A hoy · en millones de $</span>
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
              {obras.map((obra) => {
                const color = colorDeSemaforo(obra.semaforo);
                return (
                  <button
                    key={obra.idObra}
                    type="button"
                    className={estilos.columna}
                    onClick={() => navegar(`/gastos?obra=${obra.idObra}`)}
                    aria-label={`${obra.direccionObra}: gastado ${pesos(obra.totalGastado)} de ${pesos(obra.totalPresupuestado)} — ${textoDeSemaforo(obra.semaforo)}`}
                  >
                    <span
                      className={`trama ${estilos.barraPresupuesto}`}
                      style={{ height: `${(Number(obra.totalPresupuestado) / escala) * 100}%` }}
                    />
                    <span
                      className={estilos.barraGasto}
                      style={{
                        height: `${(Number(obra.totalGastado) / escala) * 100}%`,
                        background: color,
                      }}
                    >
                      <span className={estilos.barraEtiqueta} style={{ color }}>
                        ${millones(obra.totalGastado)} M
                      </span>
                    </span>
                  </button>
                );
              })}
            </div>
          </div>

          <div className={estilos.ejeX}>
            {obras.map((obra) => (
              <div key={obra.idObra} className={estilos.ejeItem}>
                <div className={estilos.ejeNombre}>{obra.direccionObra}</div>
                <div className={estilos.ejeRazon}>
                  {obra.semaforo === 'Sin presupuesto'
                    ? 'sin presupuesto aprobado'
                    : `${porcentaje(obra.avanceFinanciero)} del presupuesto`}
                </div>
              </div>
            ))}
          </div>
        </Blueprint>
      )}

      {/* ---------- Obras en ejecución ---------- */}
      {obras.length > 0 && (
        <Blueprint className={estilos.bloque}>
          <div className={estilos.bloqueCabecera}>
            <h4>Obras en ejecución</h4>
            <span className="kicker">
              Primero las que necesitan atención
            </span>
          </div>

          <div className="scroll-x">
            <table className="table">
              <thead>
                <tr>
                  <th style={{ width: 26 }}>#</th>
                  <th>Obra</th>
                  <th style={{ width: 250 }}>Avance físico vs. financiero</th>
                  <th style={{ textAlign: 'right' }}>Ganancia estimada</th>
                  <th style={{ textAlign: 'right' }}>Por cobrar</th>
                  <th style={{ width: 78 }} />
                </tr>
              </thead>
              <tbody>
                {obras.map((obra, i) => {
                  const color = colorDeSemaforo(obra.semaforo);
                  return (
                    <tr key={obra.idObra}
                        onClick={() => navegar(`/seguimiento?obra=${obra.idObra}`)}
                        style={{ cursor: 'pointer' }}>
                      <td className={estilos.filaNumero}>{String(i + 1).padStart(2, '0')}</td>
                      <td>
                        <div className={estilos.obraNombre}>
                          {/* El cuadrito repite el estado del semáforo del gráfico. */}
                          <span className={estilos.punto} style={{ background: color }}
                                title={textoDeSemaforo(obra.semaforo)} />
                          <span>{obra.direccionObra}</span>
                        </div>
                        <div className={estilos.obraDir}>
                          {obra.nombreCliente}
                          {obra.atrasada && <span className={estilos.atrasada}> · atrasada</span>}
                        </div>
                      </td>
                      <td>
                        <div className={estilos.avance}>
                          <span className={estilos.avancePista}>
                            <span className={estilos.avanceRelleno}
                                  style={{ width: `${Math.min(100, Number(obra.avanceFisico))}%` }} />
                            {/* La marca del avance financiero: si queda a la
                                derecha del relleno, se gastó más de lo que se
                                construyó. Es el cruce que el informe pide. */}
                            <span className={estilos.marcaFinanciera}
                                  style={{ left: `${Math.min(100, Number(obra.avanceFinanciero))}%` }}
                                  title={`Gastado: ${porcentaje(obra.avanceFinanciero)}`} />
                          </span>
                          <span className={`cifra ${estilos.avanceTexto}`}>
                            {porcentaje(obra.avanceFisico)}
                          </span>
                        </div>
                        {obra.alertaDesfasaje && (
                          <div className={estilos.desfasaje}>
                            Gasta {porcentaje(obra.desfasaje)} más rápido de lo que avanza
                          </div>
                        )}
                      </td>
                      <td className={`cifra ${estilos.cobrar}`}
                          style={{ color: Number(obra.gananciaEstimada) < 0
                            ? 'var(--color-excedido)' : undefined }}>
                        {pesos(obra.gananciaEstimada)}
                      </td>
                      <td className={`cifra ${estilos.cobrar}`}>
                        {pesos(obra.saldoPendiente)}
                        {obra.cuotasVencidas > 0 && (
                          <div className={estilos.vencidas}>
                            {obra.cuotasVencidas} vencida{obra.cuotasVencidas > 1 ? 's' : ''}
                          </div>
                        )}
                      </td>
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
      )}
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
