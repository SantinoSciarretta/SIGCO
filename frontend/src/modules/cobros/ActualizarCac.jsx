import { useCallback, useEffect, useState } from 'react';
import Modal from '../../components/ui/Modal';
import {
  aplicarCac, listarIndices, mes, pesos, previaCac, registrarIndice,
} from './cobrosApi';
import estilos from './Cobros.module.css';

/**
 * Actualización del saldo por índice CAC.
 *
 * El informe pide esta previa explícitamente: el dueño ve cómo quedan las
 * cuotas ANTES de confirmar. Una actualización de saldo no debería ser una
 * sorpresa.
 *
 * El índice se carga a mano: la importación automática desde la Cámara
 * Argentina de la Construcción está fuera del alcance de esta versión.
 */
export default function ActualizarCac({ idObra, onCerrar, onAplicado }) {
  const [indices, setIndices] = useState([]);
  const [previa, setPrevia] = useState(null);
  const [error, setError] = useState(null);
  const [aviso, setAviso] = useState(null);
  const [aplicando, setAplicando] = useState(false);
  const [recarga, setRecarga] = useState(0);

  const recargar = useCallback(() => setRecarga((n) => n + 1), []);

  useEffect(() => {
    let vigente = true;
    (async () => {
      try {
        const lista = await listarIndices();
        if (vigente) setIndices(lista);
      } catch { /* el listado es informativo */ }

      try {
        const p = await previaCac(idObra);
        if (vigente) { setPrevia(p); setAviso(null); }
      } catch (fallo) {
        if (vigente) { setPrevia(null); setAviso(fallo.mensaje); }
      }
    })();
    return () => { vigente = false; };
  }, [idObra, recarga]);

  const confirmar = async () => {
    setAplicando(true);
    setError(null);
    try {
      await aplicarCac(idObra);
      onAplicado();
    } catch (fallo) {
      setError(fallo.mensaje);
    } finally {
      setAplicando(false);
    }
  };

  // Un coeficiente menor a 1 baja las cuotas. Es raro pero posible, y decir
  // "subió -12%" sería confuso.
  const variacion = previa ? (Number(previa.coeficiente) - 1) * 100 : null;
  const comoPorcentaje = variacion === null ? null
    : Math.abs(variacion).toLocaleString('es-AR', { maximumFractionDigits: 2 });

  return (
    <Modal abierto onCerrar={onCerrar} titulo="Actualizar las cuotas por CAC">
      {error && <p className={estilos.errorGeneral}>{error}</p>}

      <CargarIndice onCargado={recargar} onError={setError} />

      {indices.length > 0 && (
        <div className={estilos.campo}>
          <label className={estilos.etiqueta}>Actualizaciones cargadas</label>
          <div>
            {indices.slice(0, 6).map((i) => (
              <span key={i.idCac} className={estilos.indice}>
                {mes(i.mesCorrespondiente)} · ×{i.coeficiente}
              </span>
            ))}
          </div>
        </div>
      )}

      {aviso && <p className={estilos.aviso}>{aviso}</p>}

      {previa && (
        <>
          <div className={estilos.previa}>
            <div className={estilos.previaFila}>
              <span>Mes</span>
              <span className="cifra">{mes(previa.mesActual)}</span>
            </div>
            <div className={estilos.previaFila}>
              <span>Se multiplica por</span>
              <span className="cifra">
                {previa.coeficiente}
                {variacion !== 0
                  && ` (${variacion > 0 ? 'sube' : 'baja'} ${comoPorcentaje}%)`}
              </span>
            </div>
            <div className={estilos.previaFila}>
              <span>Saldo pendiente actual</span>
              <span className="cifra">{pesos(previa.saldoActual)}</span>
            </div>
            <div className={`${estilos.previaFila} ${estilos.previaTotal}`}>
              <span>Saldo actualizado</span>
              <span className="cifra">{pesos(previa.saldoActualizado)}</span>
            </div>
          </div>

          <p className={estilos.ayuda}>
            Se recalculan las <b>{previa.cuotasAfectadas} cuotas pendientes</b>.
            Las ya cobradas no se tocan: reajustarlas sería cobrar dos veces por
            lo mismo. Mirá el saldo actualizado antes de aplicar — si el número
            no cierra, el coeficiente está mal cargado.
          </p>
        </>
      )}

      <div className={estilos.accionesFormulario}>
        <button type="button" className={estilos.botonSecundario} onClick={onCerrar}>
          Cancelar
        </button>
        <button type="button" className={estilos.botonPrimario}
                onClick={confirmar} disabled={aplicando || !previa}>
          {aplicando ? 'Aplicando…' : 'Aplicar actualización'}
        </button>
      </div>
    </Modal>
  );
}

/* ========================================================================== */

/**
 * Carga del coeficiente del mes.
 *
 * Se escribe POR CUÁNTO se multiplican las cuotas, no el nivel del índice que
 * publica la Cámara. Antes se cargaba el nivel y el sistema sacaba la relación
 * entre dos meses; con 0,1 y 1,6 cargados eso daba 16, y las cuotas se
 * multiplicaban por dieciséis. Lo reportó Ricardo, y el cambio es de fondo: el
 * campo ahora guarda lo mismo que uno piensa al escribirlo.
 */
function CargarIndice({ onCargado, onError }) {
  const mesActual = new Date().toISOString().slice(0, 7);
  const [mesCorrespondiente, setMes] = useState(mesActual);
  const [coeficiente, setValor] = useState('');
  const [guardando, setGuardando] = useState(false);

  const enviar = async (evento) => {
    evento.preventDefault();
    setGuardando(true);
    try {
      // El input type=month da "2026-09"; la API espera una fecha completa y
      // el backend la normaliza al día 1 igual.
      await registrarIndice({
        mesCorrespondiente: `${mesCorrespondiente}-01`,
        coeficiente,
      });
      setValor('');
      onCargado();
    } catch (fallo) {
      onError(fallo.mensaje);
    } finally {
      setGuardando(false);
    }
  };

  return (
    <div className={estilos.campo}>
      <label className={estilos.etiqueta}>Cargar la actualización del mes</label>
      <form onSubmit={enviar} className={estilos.formularioEnLinea ?? estilos.campo}>
        <input type="month" className={estilos.control} value={mesCorrespondiente}
               onChange={(e) => setMes(e.target.value)} required aria-label="Mes" />
        <input type="number" step="0.01" min="0.0001" max="10" className={estilos.control}
               placeholder="1,4" value={coeficiente}
               onChange={(e) => setValor(e.target.value)} required
               aria-label="Coeficiente del mes" />
        <button type="submit" className={estilos.botonSecundario} disabled={guardando}>
          {guardando ? '…' : 'Guardar'}
        </button>
      </form>
      <p className={estilos.ayuda}>
        Escribí <b>por cuánto se multiplican las cuotas</b>: 1,4 sube un 40%, o
        sea que una cuota de $1.000 pasa a $1.400. Un 1 exacto deja todo igual.
        No es el valor del índice que publica la Cámara.
      </p>
    </div>
  );
}
