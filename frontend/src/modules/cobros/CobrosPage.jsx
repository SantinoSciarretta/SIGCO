import { useCallback, useEffect, useState } from 'react';
import Blueprint from '../../components/ui/Blueprint';
import Modal from '../../components/ui/Modal';
import { listarObras } from '../obras/obrasApi';
import ActualizarCac from './ActualizarCac';
import {
  MEDIOS_DE_PAGO, COMPROBANTES, anularPago, claseDeEstadoCuota, consolidado,
  fecha, generarPlan, pesos, pesosCorto, planDeCobro, registrarPago,
} from './cobrosApi';
import estilos from './Cobros.module.css';

/**
 * Cobros de una obra.
 *
 * Reemplaza la planilla por obra más la planilla resumen que hoy se actualiza a
 * mano. Sobre todo elimina la dependencia de la memoria del dueño: las cuotas
 * vencidas se marcan solas, a partir del calendario.
 */
export default function CobrosPage() {
  const [obras, setObras] = useState([]);
  const [idObra, setIdObra] = useState('');
  const [plan, setPlan] = useState(null);
  const [resumen, setResumen] = useState([]);
  const [error, setError] = useState(null);
  const [recarga, setRecarga] = useState(0);

  const [aCobrar, setACobrar] = useState(null);
  const [aAnular, setAAnular] = useState(null);
  const [cacAbierto, setCacAbierto] = useState(false);
  const [generando, setGenerando] = useState(false);

  const recargar = useCallback(() => setRecarga((n) => n + 1), []);

  useEffect(() => {
    let vigente = true;
    listarObras().then((d) => {
      if (!vigente) return;
      setObras(d);
      const conCobros = d.find((o) => o.estado === 'En ejecución' || o.estado === 'Finalizada');
      if (conCobros) setIdObra(String(conCobros.idObra));
    }).catch(() => {});
    return () => { vigente = false; };
  }, []);

  useEffect(() => {
    let vigente = true;
    consolidado().then((d) => { if (vigente) setResumen(d); }).catch(() => {});
    return () => { vigente = false; };
  }, [recarga]);

  useEffect(() => {
    if (!idObra) return undefined;

    let vigente = true;
    (async () => {
      try {
        const datos = await planDeCobro(idObra);
        if (vigente) { setPlan(datos); setError(null); }
      } catch (fallo) {
        if (vigente) { setPlan(null); setError(fallo.mensaje); }
      }
    })();
    return () => { vigente = false; };
  }, [idObra, recarga]);

  const planVisible = idObra ? plan : null;
  const obraElegida = obras.find((o) => String(o.idObra) === idObra);

  const generar = async () => {
    setGenerando(true);
    try {
      // Primer vencimiento por defecto: dentro de 15 días. El dueño lo puede
      // cambiar después registrando los pagos cuando corresponda.
      const enQuince = new Date();
      enQuince.setDate(enQuince.getDate() + 15);
      await generarPlan(idObra, enQuince.toISOString().slice(0, 10));
      recargar();
    } catch (fallo) {
      setError(fallo.mensaje);
    } finally {
      setGenerando(false);
    }
  };

  return (
    <>
      <div className={estilos.cabecera}>
        <div>
          <span className="kicker kicker-acento">Módulo</span>
          <h2 className={estilos.titulo}>Cobros</h2>
          <p className={estilos.bajada}>
            Anticipo, cuotas quincenales y actualización del saldo por índice CAC.
          </p>
        </div>

        <div className={estilos.herramientas}>
          <select className={estilos.filtro} value={idObra}
                  onChange={(e) => setIdObra(e.target.value)} aria-label="Obra">
            <option value="">Elegir obra…</option>
            {obras.map((o) => (
              <option key={o.idObra} value={o.idObra}>
                {o.direccionObra} — {o.estado}
              </option>
            ))}
          </select>
          {planVisible && (
            <button type="button" className={estilos.botonSecundario}
                    onClick={() => setCacAbierto(true)}>
              Actualizar por CAC
            </button>
          )}
        </div>
      </div>

      {error && !planVisible && (
        <Blueprint className={estilos.bloque}>
          <div className={estilos.vacio}>
            <p className={estilos.vacioTitulo}>{error}</p>
            {error.includes('todavía no tiene plan') && (
              <>
                <p className={estilos.vacioTexto}>
                  El plan sale del presupuesto definitivo aprobado: anticipo,
                  cantidad de cuotas y total.
                </p>
                <button type="button" className={estilos.botonPrimario}
                        onClick={generar} disabled={generando}>
                  {generando ? 'Generando…' : 'Generar plan de cobro'}
                </button>
              </>
            )}
          </div>
        </Blueprint>
      )}

      {planVisible && (
        <>
          <div className={estilos.resumen}>
            <div className={estilos.tarjeta}>
              <span className={`cifra ${estilos.tarjetaValor}`}>
                {pesosCorto(planVisible.totalPlan)}
              </span>
              <span className={estilos.tarjetaEtiqueta}>Total del plan</span>
            </div>
            <div className={estilos.tarjeta}>
              <span className={`cifra ${estilos.tarjetaValor} ${estilos.cobrado}`}>
                {pesosCorto(planVisible.totalCobrado)}
              </span>
              <span className={estilos.tarjetaEtiqueta}>
                Cobrado · {planVisible.cuotasAbonadas} de {planVisible.cuotasTotales}
              </span>
            </div>
            <div className={estilos.tarjeta}>
              <span className={`cifra ${estilos.tarjetaValor} ${estilos.pendiente}`}>
                {pesosCorto(planVisible.saldoPendiente)}
              </span>
              <span className={estilos.tarjetaEtiqueta}>Resta cobrar</span>
            </div>
            <div className={estilos.tarjeta}>
              <span className={`cifra ${estilos.tarjetaValor}`}>
                {fecha(planVisible.proximoVencimiento)}
              </span>
              <span className={estilos.tarjetaEtiqueta}>
                {planVisible.cuotasVencidas > 0
                  ? `${planVisible.cuotasVencidas} vencida${planVisible.cuotasVencidas > 1 ? 's' : ''}`
                  : 'Próximo vencimiento'}
              </span>
            </div>
          </div>

          <Blueprint className={estilos.bloque}>
            <div className={estilos.bloqueCabecera}>
              <h3 className={estilos.bloqueTitulo}>
                Plan de cobro — {obraElegida?.direccionObra}
              </h3>
            </div>

            <div className="scroll-x">
              <table className="table">
                <thead>
                  <tr>
                    <th style={{ width: 90 }}>Cuota</th>
                    <th style={{ textAlign: 'right' }}>Monto</th>
                    <th>Vence</th>
                    <th>Estado</th>
                    <th>Pago</th>
                    <th />
                  </tr>
                </thead>
                <tbody>
                  {planVisible.cuotas.map((c) => (
                    <tr key={c.idCuota}
                        className={c.esAnticipo ? estilos.filaAnticipo
                          : c.estado === 'Vencida' ? estilos.filaVencida : undefined}>
                      <td className={estilos.rubroNombre}>
                        {/* El anticipo es la cuota cero: no es un campo aparte,
                            es la primera fila del plan. */}
                        {c.esAnticipo ? 'Anticipo' : `Cuota ${c.numeroCuota}`}
                      </td>
                      <td className={`cifra ${estilos.total}`}>{pesos(c.montoCuota)}</td>
                      <td className={estilos.dato}>{fecha(c.fechaVencimiento)}</td>
                      <td>
                        <span className={claseDeEstadoCuota(c.estado, estilos)}>{c.estado}</span>
                      </td>
                      <td className={estilos.dato}>
                        {c.fechaPago ? `${fecha(c.fechaPago)} · ${c.medioPago}` : '—'}
                        {/* Con pagos parciales, el monto de la cuota ya no dice
                            cuánto se debe: hay que mostrar el saldo. */}
                        {Number(c.totalPagado) > 0 && Number(c.saldo) > 0 && (
                          <div className={estilos.subrubroNombre}>
                            Pagó {pesos(c.totalPagado)} · resta {pesos(c.saldo)}
                          </div>
                        )}
                        {c.motivoAnulacion && (
                          <div className={estilos.subrubroNombre}>
                            Pago anulado: {c.motivoAnulacion}
                          </div>
                        )}
                      </td>
                      <td className={estilos.acciones}>
                        {c.estado !== 'Abonada' && (
                          <button type="button" className={estilos.accion}
                                  onClick={() => setACobrar(c)}>
                            Registrar pago
                          </button>
                        )}
                        {Number(c.totalPagado) > 0 && (
                          <button type="button" className={estilos.accionPeligro}
                                  onClick={() => setAAnular(c)}>
                            Anular pago
                          </button>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            <p className={estilos.ayuda}>
              Si el cliente paga fuera de término, el monto de la cuota no cambia:
              la empresa no cobra recargos.
            </p>
          </Blueprint>
        </>
      )}

      {resumen.length > 0 && (
        <Blueprint className={estilos.bloque}>
          <div className={estilos.bloqueCabecera}>
            <h3 className={estilos.bloqueTitulo}>Cuánto resta cobrar de cada obra</h3>
          </div>
          <div className="scroll-x">
            <table className="table">
              <thead>
                <tr>
                  <th>Obra</th>
                  <th style={{ textAlign: 'right' }}>Total</th>
                  <th style={{ textAlign: 'right' }}>Cobrado</th>
                  <th style={{ textAlign: 'right' }}>Resta</th>
                  <th>Próximo vto.</th>
                  <th style={{ textAlign: 'right' }}>Vencidas</th>
                </tr>
              </thead>
              <tbody>
                {resumen.map((f) => (
                  <tr key={f.idObra} className={f.cuotasVencidas > 0 ? estilos.filaVencida : undefined}>
                    <td>
                      <div className={estilos.obra}>{f.direccionObra}</div>
                      <div className={estilos.cliente}>{f.nombreCliente}</div>
                    </td>
                    <td className={`cifra ${estilos.numero}`}>{pesosCorto(f.totalPlan)}</td>
                    <td className={`cifra ${estilos.numero}`}>{pesosCorto(f.totalCobrado)}</td>
                    <td className={`cifra ${estilos.total}`}>{pesosCorto(f.saldoPendiente)}</td>
                    <td className={estilos.dato}>{fecha(f.proximoVencimiento)}</td>
                    <td className={`cifra ${estilos.numero}`}>
                      <span className={f.cuotasVencidas > 0 ? estilos.cuotaVencida : ''}>
                        {f.cuotasVencidas}
                      </span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <p className={estilos.ayuda}>
            Reemplaza la planilla resumen que hoy se actualiza a mano. Las obras
            con cuotas vencidas van primero.
          </p>
        </Blueprint>
      )}

      {aCobrar && (
        <RegistrarPagoModal
          cuota={aCobrar}
          onCerrar={() => setACobrar(null)}
          onRegistrado={() => { setACobrar(null); recargar(); }}
        />
      )}

      {aAnular && (
        <AnularPagoModal
          cuota={aAnular}
          onCerrar={() => setAAnular(null)}
          onAnulado={() => { setAAnular(null); recargar(); }}
        />
      )}

      {cacAbierto && (
        <ActualizarCac
          idObra={idObra}
          onCerrar={() => setCacAbierto(false)}
          onAplicado={() => { setCacAbierto(false); recargar(); }}
        />
      )}
    </>
  );
}

/* ========================================================================== */

function RegistrarPagoModal({ cuota, onCerrar, onRegistrado }) {
  const hoy = new Date().toISOString().slice(0, 10);
  const [datos, setDatos] = useState({
    // Propone el saldo completo: pagar la cuota entera sigue siendo lo normal,
    // y así quien cobra todo no tiene que escribir el monto. Quien cobra una
    // parte lo edita.
    monto: cuota.saldo,
    fechaPago: hoy, medioPago: 'Transferencia', comprobanteEmitido: 'Recibo',
  });
  const [error, setError] = useState(null);
  const [guardando, setGuardando] = useState(false);

  const cambiar = (campo) => (e) => setDatos((p) => ({ ...p, [campo]: e.target.value }));

  const enviar = async (evento) => {
    evento.preventDefault();
    setGuardando(true);
    setError(null);
    try {
      await registrarPago(cuota.idCuota, datos);
      onRegistrado();
    } catch (fallo) {
      setError(fallo.mensaje);
    } finally {
      setGuardando(false);
    }
  };

  return (
    <Modal abierto onCerrar={onCerrar}
           titulo={cuota.esAnticipo ? 'Cobrar el anticipo' : `Cobrar cuota ${cuota.numeroCuota}`}>
      <form onSubmit={enviar}>
        <p className={estilos.confirmacion}>
          {Number(cuota.totalPagado) > 0 ? (
            <>
              Ya pagó <b>{pesos(cuota.totalPagado)}</b> de {pesos(cuota.montoCuota)} ·
              resta <b>{pesos(cuota.saldo)}</b>
            </>
          ) : (
            <>Monto: <b>{pesos(cuota.montoCuota)}</b></>
          )} · vencía el {fecha(cuota.fechaVencimiento)}
        </p>

        {error && <p className={estilos.errorGeneral}>{error}</p>}

        <div className={estilos.campo}>
          <label className={estilos.etiqueta} htmlFor="monto">
            Monto cobrado <span className={estilos.obligatorio}>*</span>
          </label>
          <input id="monto" type="number" step="0.01" min="0.01"
                 max={cuota.saldo}
                 className={estilos.control}
                 value={datos.monto} onChange={cambiar('monto')} required />
          <p className={estilos.ayuda}>
            Viene cargado el saldo completo. Si el cliente pagó solo una parte,
            cambiá el importe: la cuota queda como pagada en parte y el resto
            sigue figurando como deuda.
          </p>
        </div>

        <div className={estilos.tresColumnas}>
          <div className={estilos.campo}>
            <label className={estilos.etiqueta} htmlFor="fechaPago">
              Fecha de pago <span className={estilos.obligatorio}>*</span>
            </label>
            <input id="fechaPago" type="date" className={estilos.control}
                   value={datos.fechaPago} onChange={cambiar('fechaPago')} required autoFocus />
          </div>

          <div className={estilos.campo}>
            <label className={estilos.etiqueta} htmlFor="medioPago">
              Medio <span className={estilos.obligatorio}>*</span>
            </label>
            <select id="medioPago" className={estilos.control}
                    value={datos.medioPago} onChange={cambiar('medioPago')} required>
              {MEDIOS_DE_PAGO.map((m) => <option key={m} value={m}>{m}</option>)}
            </select>
          </div>

          <div className={estilos.campo}>
            <label className={estilos.etiqueta} htmlFor="comprobanteEmitido">Comprobante</label>
            <select id="comprobanteEmitido" className={estilos.control}
                    value={datos.comprobanteEmitido} onChange={cambiar('comprobanteEmitido')}>
              <option value="">Ninguno</option>
              {COMPROBANTES.map((c) => <option key={c} value={c}>{c}</option>)}
            </select>
          </div>
        </div>

        <div className={estilos.accionesFormulario}>
          <button type="button" className={estilos.botonSecundario} onClick={onCerrar}>
            Cancelar
          </button>
          <button type="submit" className={estilos.botonPrimario} disabled={guardando}>
            {guardando ? 'Registrando…' : 'Registrar pago'}
          </button>
        </div>
      </form>
    </Modal>
  );
}

/* ========================================================================== */

function AnularPagoModal({ cuota, onCerrar, onAnulado }) {
  const [motivo, setMotivo] = useState('');
  const [error, setError] = useState(null);
  const [guardando, setGuardando] = useState(false);

  const enviar = async (evento) => {
    evento.preventDefault();
    setGuardando(true);
    setError(null);
    try {
      await anularPago(cuota.idCuota, motivo);
      onAnulado();
    } catch (fallo) {
      setError(fallo.mensaje);
    } finally {
      setGuardando(false);
    }
  };

  return (
    <Modal abierto onCerrar={onCerrar} titulo="Anular pago">
      <form onSubmit={enviar}>
        <p className={estilos.confirmacion}>
          Se va a anular el pago de <b>{pesos(cuota.montoCuota)}</b>.
        </p>
        <p className={estilos.ayuda}>
          Se anula el <b>pago</b>, no la cuota: vuelve a estar pendiente y sigue
          debiéndose. El motivo queda registrado en el estado de cuenta.
        </p>

        {error && <p className={estilos.errorGeneral}>{error}</p>}

        <div className={estilos.campo}>
          <label className={estilos.etiqueta} htmlFor="motivoPago">
            Motivo <span className={estilos.obligatorio}>*</span>
          </label>
          <input id="motivoPago" className={estilos.control} maxLength={200}
                 placeholder="El cheque rebotó" value={motivo}
                 onChange={(e) => setMotivo(e.target.value)} required autoFocus />
        </div>

        <div className={estilos.accionesFormulario}>
          <button type="button" className={estilos.botonSecundario} onClick={onCerrar}>
            Cancelar
          </button>
          <button type="submit" className={estilos.botonPeligro} disabled={guardando}>
            {guardando ? 'Anulando…' : 'Anular pago'}
          </button>
        </div>
      </form>
    </Modal>
  );
}
