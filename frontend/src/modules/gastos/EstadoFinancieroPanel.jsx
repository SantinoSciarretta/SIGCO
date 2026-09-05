import { useEffect, useState } from 'react';
import Blueprint from '../../components/ui/Blueprint';
import { claseDeSemaforo, estadoFinanciero, pesosCorto } from './gastosApi';
import estilos from './Gastos.module.css';

/**
 * Panel de estado financiero de la obra.
 *
 * Es la vista que hoy no existe: el dueño conoce el resultado económico de una
 * obra recién cuando termina. Acá lo ve en cualquier momento, rubro por rubro.
 *
 * Se recalcula solo con cada gasto nuevo porque el backend no guarda totales:
 * los deriva con SUM sobre los gastos confirmados. Un contador acumulado habría
 * que recalcularlo en cada alta, edición y anulación, y basta que una rama se
 * olvide para que el número quede mal para siempre.
 */
export default function EstadoFinancieroPanel({ idObra, recarga }) {
  const [estado, setEstado] = useState(null);
  const [error, setError] = useState(null);

  useEffect(() => {
    if (!idObra) return undefined;

    let vigente = true;
    (async () => {
      try {
        const datos = await estadoFinanciero(idObra);
        if (vigente) { setEstado(datos); setError(null); }
      } catch (fallo) {
        if (vigente) { setEstado(null); setError(fallo.mensaje); }
      }
    })();
    return () => { vigente = false; };
  }, [idObra, recarga]);

  if (error) {
    return (
      <Blueprint className={estilos.bloque}>
        <p className={estilos.aviso}>{error}</p>
      </Blueprint>
    );
  }

  if (!estado) return null;

  const perdiendo = Number(estado.gananciaEstimada) < 0;

  return (
    <>
      <div className={estilos.resumen}>
        <div className={estilos.tarjeta}>
          <span className={`cifra ${estilos.tarjetaValor}`}>
            {pesosCorto(estado.totalPresupuestado)}
          </span>
          <span className={estilos.tarjetaEtiqueta}>Presupuestado</span>
        </div>
        <div className={estilos.tarjeta}>
          <span className={`cifra ${estilos.tarjetaValor}`}>
            {pesosCorto(estado.totalGastado)}
          </span>
          <span className={estilos.tarjetaEtiqueta}>
            Gastado · {estado.porcentajeConsumido}%
          </span>
        </div>
        <div className={estilos.tarjeta}>
          {/* Ganancia negativa = la obra está perdiendo plata. Es el dato que
              hoy aparece recién al cerrar el proyecto. */}
          <span className={`cifra ${estilos.tarjetaValor} ${
            perdiendo ? estilos.negativo : estilos.positivo}`}>
            {pesosCorto(estado.gananciaEstimada)}
          </span>
          <span className={estilos.tarjetaEtiqueta}>
            {perdiendo ? 'Pérdida estimada' : 'Ganancia estimada'}
          </span>
        </div>
        <div className={estilos.tarjeta}>
          <span className={`cifra ${estilos.tarjetaValor}`}>
            {pesosCorto(estado.totalGastoHormiga)}
          </span>
          <span className={estilos.tarjetaEtiqueta}>Gasto hormiga</span>
        </div>
      </div>

      <Blueprint className={estilos.bloque}>
        <div className={estilos.bloqueCabecera}>
          <h3 className={estilos.bloqueTitulo}>Presupuestado contra gastado, por rubro</h3>
          <span className={claseDeSemaforo(estado.semaforoGeneral, estilos)}>
            {estado.semaforoGeneral}
          </span>
        </div>

        <div className="scroll-x">
          <table className="table">
            <thead>
              <tr>
                <th>Rubro</th>
                <th style={{ textAlign: 'right' }}>Presupuestado</th>
                <th style={{ textAlign: 'right' }}>Gastado</th>
                <th style={{ textAlign: 'right' }}>Diferencia</th>
                <th style={{ width: 180 }}>Consumo</th>
              </tr>
            </thead>
            <tbody>
              {estado.rubros.map((r) => {
                const excedido = Number(r.diferencia) < 0;
                return (
                  <tr key={r.idRubro}>
                    <td className={estilos.rubroNombre}>{r.nombreRubro}</td>
                    <td className={`cifra ${estilos.numero}`}>
                      {pesosCorto(r.presupuestado)}
                    </td>
                    <td className={`cifra ${estilos.numero}`}>{pesosCorto(r.gastado)}</td>
                    <td className={`cifra ${estilos.numero} ${
                      excedido ? estilos.negativo : ''}`}>
                      {pesosCorto(r.diferencia)}
                    </td>
                    <td>
                      <span className={claseDeSemaforo(r.semaforo, estilos)}>
                        {r.semaforo === 'Sin presupuesto' ? 'Sin presupuesto' : `${r.porcentaje}%`}
                      </span>
                      <div className={estilos.barra}>
                        <div className={estilos.barraRelleno}
                             data-semaforo={r.semaforo}
                             style={{ width: `${Math.min(Number(r.porcentaje), 100)}%` }} />
                      </div>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>

        <p className={estilos.ayuda}>
          Verde hasta el 90% de lo presupuestado, amarillo entre 90% y 100%, rojo
          al superarlo. «Sin presupuesto» marca un rubro donde se está gastando
          sin que nadie lo haya previsto.
        </p>
      </Blueprint>
    </>
  );
}
