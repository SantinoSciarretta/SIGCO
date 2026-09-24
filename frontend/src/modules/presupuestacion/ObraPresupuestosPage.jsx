import { useCallback, useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import Blueprint from '../../components/ui/Blueprint';
import { ConfirmarEliminacion, claseDeEstado } from './PresupuestosPage';
import { listarPorObra, pesos } from './presupuestosApi';
import estilos from './Presupuestos.module.css';

/**
 * Las instancias presupuestarias de una obra.
 *
 * ------------------------------------------------------------------
 *  Qué problema resuelve
 * ------------------------------------------------------------------
 *
 * El circuito real de Granica tiene tres instancias: la cotización inicial que
 * se pasa por teléfono, el anteproyecto (solo en reformas) y el definitivo que
 * firma el cliente. Antes esas tres vivían en el mismo listado plano junto a
 * las de todas las demás obras, así que para reconstruir la negociación de una
 * obra había que ir filtrando y comparando filas.
 *
 * Esta pantalla las muestra juntas y en orden: primero lo que se cotizó, después
 * lo que se detalló, y arriba de todo cuánto vale hoy la obra. Es lo que Ricardo
 * pidió cuando dijo "que cuando se ingresa se puedan ver las 3 versiones".
 *
 * ------------------------------------------------------------------
 *  De dónde salen los datos
 * ------------------------------------------------------------------
 *
 * Del mismo endpoint agrupado del listado, filtrando la obra. No hay una
 * consulta propia porque sería la misma: el agrupado ya trae las instancias de
 * cada obra con el vigente resuelto, y duplicar esa lógica en un segundo
 * endpoint abriría la puerta a que las dos pantallas muestren cosas distintas.
 */
export default function ObraPresupuestosPage() {
  const { idObra } = useParams();

  const [obra, setObra] = useState(null);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState(null);
  const [aEliminar, setAEliminar] = useState(null);
  const [recarga, setRecarga] = useState(0);

  useEffect(() => {
    let vigente = true;

    (async () => {
      try {
        const todas = await listarPorObra();
        if (!vigente) return;
        setObra(todas.find((o) => String(o.idObra) === String(idObra)) ?? null);
        setError(null);
      } catch (fallo) {
        if (vigente) setError(fallo.mensaje);
      } finally {
        if (vigente) setCargando(false);
      }
    })();

    return () => { vigente = false; };
  }, [idObra, recarga]);

  const recargar = useCallback(() => {
    setCargando(true);
    setRecarga((n) => n + 1);
  }, []);

  if (cargando) return <p className={estilos.aviso}>Consultando…</p>;
  if (error) return <p className={estilos.errorGeneral}>{error}</p>;

  if (!obra) {
    return (
      <>
        <p className={estilos.volver}><Link to="/presupuestos">← Presupuestos</Link></p>
        <Blueprint className={estilos.bloque}>
          <div className={estilos.vacio}>
            <p className={estilos.vacioTitulo}>Esta obra no tiene presupuestos</p>
            <p className={estilos.vacioTexto}>
              Puede que se hayan eliminado, o que la obra todavía no se haya
              presupuestado.
            </p>
          </div>
        </Blueprint>
      </>
    );
  }

  // Las instancias vienen ya ordenadas por el circuito desde el backend; acá
  // solo se parten en grupos para que cada tipo tenga su bloque con su título.
  const porTipo = agruparPorTipo(obra.presupuestos);

  return (
    <>
      <p className={estilos.volver}><Link to="/presupuestos">← Presupuestos</Link></p>

      <div className={estilos.encabezado}>
        <div>
          <span className="kicker kicker-acento">Obra</span>
          <h2 className={estilos.tituloDetalle}>{obra.direccionObra}</h2>
          <p className={estilos.subtitulo}>
            {obra.nombreCliente} · {obra.tipoObra} · {obra.estadoObra}
            {' · '}
            <Link to={`/obras/${obra.idObra}`}>Ver la obra</Link>
          </p>
        </div>

        {/* El número que importa: lo que vale la obra hoy, según el presupuesto
            que gobierna. Cuál es ese lo decide el backend, no esta pantalla. */}
        <div className={estilos.totalBloque}>
          <span className="kicker">{obra.tipoVigente} vigente</span>
          <div className={`cifra ${estilos.totalCifra}`}>{pesos(obra.totalVigente)}</div>
          <span className={claseDeEstado(obra.estadoVigente)}>{obra.estadoVigente}</span>
        </div>
      </div>

      {porTipo.map((grupo) => (
        <Blueprint key={grupo.tipo} className={estilos.bloque}>
          <div className={estilos.bloqueCabecera}>
            <h3 className={estilos.bloqueTitulo}>{grupo.tipo}</h3>
            <span className={estilos.ayuda}>{descripcionDe(grupo.tipo)}</span>
          </div>

          <div className="scroll-x">
            <table className="table">
              <thead>
                <tr>
                  <th style={{ width: 90 }}>Versión</th>
                  <th>Estado</th>
                  <th>Creado</th>
                  <th style={{ textAlign: 'right' }}>Total</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {grupo.versiones.map((p) => (
                  <tr key={p.idPresupuesto}
                      className={p.idPresupuesto === obra.idPresupuestoVigente
                        ? estilos.filaAprobada : undefined}>
                    <td className={`cifra ${estilos.version}`}>
                      v{p.version}
                      {/* La cadena de versiones: de qué presupuesto salió éste.
                          Es el versionado real que reemplaza al Excel pisado. */}
                      {p.idPresupuestoBase && (
                        <span className={estilos.base} title="Generado a partir de otro presupuesto">
                          ← #{p.idPresupuestoBase}
                        </span>
                      )}
                      {p.idPresupuesto === obra.idPresupuestoVigente && (
                        <span className={estilos.marcaVigente}>vigente</span>
                      )}
                    </td>
                    <td><span className={claseDeEstado(p.estado)}>{p.estado}</span></td>
                    <td className={estilos.dato}>{fecha(p.fechaCreacion)}</td>
                    <td className={`cifra ${estilos.total}`}>{pesos(p.totalPresupuesto)}</td>
                    <td className={estilos.acciones}>
                      <Link className={estilos.accion} to={`/presupuestos/${p.idPresupuesto}`}>
                        Abrir
                      </Link>
                      <button type="button" className={estilos.accionPeligro}
                              onClick={() => setAEliminar(p)}>
                        Eliminar
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </Blueprint>
      ))}

      {aEliminar && (
        <ConfirmarEliminacion
          presupuesto={aEliminar}
          onCerrar={() => setAEliminar(null)}
          onEliminado={() => { setAEliminar(null); recargar(); }}
        />
      )}
    </>
  );
}

/** Parte la lista ya ordenada en un grupo por tipo, conservando ese orden. */
function agruparPorTipo(presupuestos) {
  const grupos = [];
  presupuestos.forEach((p) => {
    const ya = grupos.find((g) => g.tipo === p.tipoPresupuesto);
    if (ya) ya.versiones.push(p);
    else grupos.push({ tipo: p.tipoPresupuesto, versiones: [p] });
  });
  return grupos;
}

/**
 * Qué es cada instancia, en una línea.
 *
 * Va acá y no en la base porque describe el circuito, que no cambia: es el
 * mismo texto del informe, puesto donde hace falta leerlo.
 */
function descripcionDe(tipo) {
  if (tipo === 'Cotización inicial') return 'Estimación por metro cuadrado, antes de detallar nada.';
  if (tipo === 'Anteproyecto') return 'Presupuesto general por rubro. Solo en reformas.';
  if (tipo === 'Definitivo') return 'Detallado por rubro, subrubro e ítem. Es el que se firma.';
  return 'Trabajos que se suman después de aprobado el definitivo.';
}

function fecha(valor) {
  if (!valor) return '—';
  return new Date(valor).toLocaleDateString('es-AR');
}
