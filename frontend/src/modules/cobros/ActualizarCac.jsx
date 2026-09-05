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

  const subio = previa
    ? ((Number(previa.coeficiente) - 1) * 100).toLocaleString('es-AR',
      { maximumFractionDigits: 2 })
    : null;

  return (
    <Modal abierto onCerrar={onCerrar} titulo="Actualizar saldo por índice CAC">
      {error && <p className={estilos.errorGeneral}>{error}</p>}

      <CargarIndice onCargado={recargar} onError={setError} />

      {indices.length > 0 && (
        <div className={estilos.campo}>
          <label className={estilos.etiqueta}>Índices cargados</label>
          <div>
            {indices.slice(0, 6).map((i) => (
              <span key={i.idCac} className={estilos.indice}>
                {mes(i.mesCorrespondiente)} · {i.valorIndice}
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
              <span>{mes(previa.mesAnterior)} → {mes(previa.mesActual)}</span>
              <span className="cifra">{previa.indiceAnterior} → {previa.indiceActual}</span>
            </div>
            <div className={estilos.previaFila}>
              <span>Coeficiente</span>
              <span className="cifra">{previa.coeficiente} (subió {subio}%)</span>
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
            lo mismo.
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

function CargarIndice({ onCargado, onError }) {
  const mesActual = new Date().toISOString().slice(0, 7);
  const [mesCorrespondiente, setMes] = useState(mesActual);
  const [valorIndice, setValor] = useState('');
  const [guardando, setGuardando] = useState(false);

  const enviar = async (evento) => {
    evento.preventDefault();
    setGuardando(true);
    try {
      // El input type=month da "2026-09"; la API espera una fecha completa y
      // el backend la normaliza al día 1 igual.
      await registrarIndice({
        mesCorrespondiente: `${mesCorrespondiente}-01`,
        valorIndice,
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
      <label className={estilos.etiqueta}>Cargar el índice del mes</label>
      <form onSubmit={enviar} className={estilos.formularioEnLinea ?? estilos.campo}>
        <input type="month" className={estilos.control} value={mesCorrespondiente}
               onChange={(e) => setMes(e.target.value)} required aria-label="Mes" />
        <input type="number" step="0.0001" min="0.0001" className={estilos.control}
               placeholder="Valor del índice" value={valorIndice}
               onChange={(e) => setValor(e.target.value)} required
               aria-label="Valor del índice" />
        <button type="submit" className={estilos.botonSecundario} disabled={guardando}>
          {guardando ? '…' : 'Guardar'}
        </button>
      </form>
      <p className={estilos.ayuda}>
        El ajuste sale de comparar el índice del mes con el del mes anterior, así
        que hacen falta al menos dos meses cargados.
      </p>
    </div>
  );
}
