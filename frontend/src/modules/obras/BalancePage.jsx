import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import Blueprint from '../../components/ui/Blueprint';
import { balanceDeObra } from './obrasApi';
import estilos from './Balance.module.css';

/**
 * El cierre económico de una obra.
 *
 * ------------------------------------------------------------------
 *  Qué problema resuelve
 * ------------------------------------------------------------------
 *
 * Ricardo lo pidió como "un botón para poner obra terminada para ver balance".
 * Hoy ese cierre se arma a mano semanas después de terminar: se junta la
 * planilla de gastos con lo que uno se acuerda de haber cobrado, y el resultado
 * de la obra se sabe cuando ya no sirve para decidir nada.
 *
 * ------------------------------------------------------------------
 *  Por qué son tres números y no uno
 * ------------------------------------------------------------------
 *
 * "Cuánto gané con esta obra" tiene tres respuestas distintas, y confundirlas
 * es lo que hace que una obra parezca rentable cuando no lo es:
 *
 *   - La ganancia estimada es una proyección: lo que va a dejar SI el cliente
 *     termina de pagar.
 *   - El resultado de caja es plata de verdad: lo que entró menos lo que salió.
 *     Puede ser negativo en una obra sana, porque se compró material que
 *     todavía no se cobró.
 *   - El saldo por cobrar es lo que separa a los dos anteriores.
 *
 * Los tres vienen calculados del backend. Esta pantalla no hace ninguna cuenta.
 */
export default function BalancePage() {
  const { id } = useParams();

  const [balance, setBalance] = useState(null);
  const [error, setError] = useState(null);
  const [cargando, setCargando] = useState(true);

  useEffect(() => {
    let vigente = true;
    setCargando(true);

    balanceDeObra(id)
      .then((datos) => { if (vigente) { setBalance(datos); setError(null); } })
      .catch((fallo) => { if (vigente) setError(fallo.mensaje); })
      .finally(() => { if (vigente) setCargando(false); });

    return () => { vigente = false; };
  }, [id]);

  if (cargando) return <p className={estilos.aviso}>Armando el balance…</p>;
  if (error) return <p className={estilos.errorGeneral}>{error}</p>;
  if (!balance) return null;

  const gana = Number(balance.gananciaEstimada) >= 0;
  const cajaPositiva = Number(balance.resultadoDeCaja) >= 0;

  return (
    <>
      <p className={estilos.volver}>
        <Link to={`/obras/${balance.idObra}`}>← Volver a la obra</Link>
      </p>

      <div className={estilos.encabezado}>
        <div>
          <span className="kicker kicker-acento">Balance de obra</span>
          <h2 className={estilos.titulo}>{balance.direccionObra}</h2>
          <p className={estilos.subtitulo}>
            {balance.nombreCliente} · {balance.estadoObra}
            {balance.fechaInicioReal && ` · arrancó el ${fecha(balance.fechaInicioReal)}`}
          </p>
        </div>
        <div className={estilos.avanceBloque}>
          <span className="kicker">Avance</span>
          <div className={`cifra ${estilos.avanceCifra}`}>
            {Math.round(Number(balance.avanceFisico))}%
          </div>
          <span className={estilos.avanceDetalle}>
            {balance.hitosCompletados} de {balance.hitosTotales} hitos
          </span>
        </div>
      </div>

      {/* Lo que queda abierto. Va arriba de todo porque es lo que hay que
          mirar ANTES de dar la obra por cerrada, no después. */}
      {balance.pendientes.length > 0 && (
        <Blueprint className={`${estilos.bloque} ${estilos.pendientes}`}>
          <h3 className={estilos.bloqueTitulo}>Queda abierto</h3>
          <ul className={estilos.listaPendientes}>
            {balance.pendientes.map((p) => <li key={p}>{p}</li>)}
          </ul>
          <p className={estilos.ayuda}>
            Nada de esto impide cerrar la obra: se puede terminar de construir y
            seguir cobrando durante meses.
          </p>
        </Blueprint>
      )}

      <div className={estilos.tarjetas}>
        <Tarjeta
          titulo="Ganancia estimada"
          valor={pesos(balance.gananciaEstimada)}
          tono={gana ? 'bien' : 'mal'}
          pie={`${balance.margenPorcentaje}% de lo presupuestado`}
          explicacion="Presupuestado menos gastado. Es lo que la obra deja si el cliente termina de pagar."
        />
        <Tarjeta
          titulo="Resultado de caja"
          valor={pesos(balance.resultadoDeCaja)}
          tono={cajaPositiva ? 'bien' : 'mal'}
          pie="Cobrado menos gastado"
          explicacion="La plata que de verdad entró menos la que salió. Puede ser negativa en una obra sana."
        />
        <Tarjeta
          titulo="Falta cobrar"
          valor={pesos(balance.saldoPorCobrar)}
          tono={Number(balance.saldoPorCobrar) > 0 ? 'atencion' : 'bien'}
          pie={balance.cuotasVencidas > 0
            ? `${balance.cuotasVencidas} ${balance.cuotasVencidas === 1 ? 'cuota vencida' : 'cuotas vencidas'}`
            : 'Sin cuotas vencidas'}
          explicacion="Lo que separa a la ganancia estimada del resultado de caja."
        />
      </div>

      <Blueprint className={estilos.bloque}>
        <h3 className={estilos.bloqueTitulo}>De dónde salen esos números</h3>

        <div className="scroll-x">
          <table className="table">
            <tbody>
              <Fila concepto="Presupuestado" detalle="Total del definitivo aprobado"
                    monto={balance.economia.totalPresupuestado} />
              <Fila concepto="Gastado" detalle="Gastos confirmados de la obra"
                    monto={balance.economia.totalGastado} />
              <Fila concepto="Plan de cobro" detalle="Anticipo más cuotas"
                    monto={balance.totalPlanDeCobro} />
              <Fila concepto="Cobrado" detalle="Pagos registrados, incluidos los parciales"
                    monto={balance.totalCobrado} />
            </tbody>
          </table>
        </div>
      </Blueprint>

      <Blueprint className={estilos.bloque}>
        <div className={estilos.bloqueCabecera}>
          <h3 className={estilos.bloqueTitulo}>Rubro por rubro</h3>
          <span className={estilos.ayuda}>
            Lo que hay que mirar al presupuestar la próxima obra parecida
          </span>
        </div>

        {balance.economia.rubros.length === 0 ? (
          <p className={estilos.aviso}>Esta obra todavía no tiene gastos cargados.</p>
        ) : (
          <div className="scroll-x">
            <table className="table">
              <thead>
                <tr>
                  <th>Rubro</th>
                  <th style={{ textAlign: 'right' }}>Presupuestado</th>
                  <th style={{ textAlign: 'right' }}>Gastado</th>
                  <th style={{ textAlign: 'right' }}>Diferencia</th>
                </tr>
              </thead>
              <tbody>
                {balance.economia.rubros.map((r) => (
                  <tr key={r.idRubro}
                      className={Number(r.diferencia) < 0 ? estilos.filaExcedida : undefined}>
                    <td>{r.nombreRubro}</td>
                    <td className={`cifra ${estilos.monto}`}>{pesos(r.presupuestado)}</td>
                    <td className={`cifra ${estilos.monto}`}>{pesos(r.gastado)}</td>
                    <td className={`cifra ${estilos.monto} ${
                      Number(r.diferencia) < 0 ? estilos.negativo : ''}`}>
                      {pesos(r.diferencia)}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Blueprint>
    </>
  );
}

/* ========================================================================== */

function Tarjeta({ titulo, valor, tono, pie, explicacion }) {
  return (
    <Blueprint className={`${estilos.tarjeta} ${estilos[tono]}`}>
      <span className="kicker">{titulo}</span>
      <div className={`cifra ${estilos.tarjetaValor}`}>{valor}</div>
      <span className={estilos.tarjetaPie}>{pie}</span>
      <p className={estilos.tarjetaExplicacion}>{explicacion}</p>
    </Blueprint>
  );
}

function Fila({ concepto, detalle, monto }) {
  return (
    <tr>
      <td>
        <div className={estilos.concepto}>{concepto}</div>
        <div className={estilos.conceptoDetalle}>{detalle}</div>
      </td>
      <td className={`cifra ${estilos.monto}`} style={{ textAlign: 'right' }}>
        {pesos(monto)}
      </td>
    </tr>
  );
}

function pesos(monto) {
  if (monto === null || monto === undefined) return '—';
  return '$ ' + Math.round(Number(monto)).toLocaleString('es-AR');
}

function fecha(valor) {
  if (!valor) return '—';
  return new Date(valor + 'T00:00:00').toLocaleDateString('es-AR');
}
