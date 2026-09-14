import { useCallback, useEffect, useState } from 'react';
import Blueprint from '../../../components/ui/Blueprint';
import CabeceraCapataz from './CabeceraCapataz';
import SubirImagen from '../../../components/ui/SubirImagen';
import { crearPedido, listarPedidos, recibirPedido } from '../../../modules/compras/pedidosApi';
import { listarMaterialesDisponibles } from '../../../modules/materiales/materialesApi';
import { useObraDelCapataz } from './useObraDelCapataz';
import estilos from './Materiales.module.css';

/**
 * Materiales — pedido y recepción, desde el celular en obra.
 *
 * Reemplaza el circuito que hoy pasa por WhatsApp y no deja registro. Son dos
 * momentos distintos del mismo circuito, por eso van en pestañas:
 *
 *   Pedir   → el capataz arma el pedido desde el catálogo. El dueño lo aprueba
 *             antes de que salga al proveedor: esa aprobación no es delegable.
 *   Recibir → cuando llega el camión, el capataz registra el remito y declara
 *             si vino todo. Es lo que hoy se pierde en papel.
 *
 * Las dos operan siempre sobre la obra del capataz, y el backend lo vuelve a
 * verificar: un capataz de obra solo puede pedir y recibir en las obras que
 * tiene asignadas.
 */
export default function Materiales() {
  const [pestana, setPestana] = useState('pedido');
  const { obra, cargando, error } = useObraDelCapataz();

  if (cargando) {
    return <p className={estilos.nota}>Buscando tu obra…</p>;
  }
  if (error || !obra) {
    return <p className={estilos.nota}>{error ?? 'No tenés ninguna obra asignada.'}</p>;
  }

  return (
    <>
      <CabeceraCapataz titulo={`Materiales · ${corto(obra.direccionObra)}`}>
        <div className={estilos.pestanas} role="tablist">
          <button
            type="button" role="tab"
            aria-selected={pestana === 'pedido'}
            className={`${estilos.pestana} ${pestana === 'pedido' ? estilos.pestanaActiva : ''}`.trim()}
            onClick={() => setPestana('pedido')}
          >
            Pedir
          </button>
          <button
            type="button" role="tab"
            aria-selected={pestana === 'recepcion'}
            className={`${estilos.pestana} ${pestana === 'recepcion' ? estilos.pestanaActiva : ''}`.trim()}
            onClick={() => setPestana('recepcion')}
          >
            Recibir
          </button>
        </div>
      </CabeceraCapataz>

      {pestana === 'pedido'
        ? <Pedido obra={obra} />
        : <Recepcion obra={obra} />}
    </>
  );
}

/* ========================================================================== */

/** Armado del pedido desde el catálogo real de materiales. */
function Pedido({ obra }) {
  const [materiales, setMateriales] = useState([]);
  const [cantidades, setCantidades] = useState({});
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState(null);
  const [enviando, setEnviando] = useState(false);
  const [enviado, setEnviado] = useState(false);

  useEffect(() => {
    let vigente = true;
    // Solo los materiales activos: pedir uno dado de baja fallaría en el
    // backend, y ofrecerlo sería ofrecer algo que va a ser rechazado.
    listarMaterialesDisponibles()
      .then((lista) => { if (vigente) { setMateriales(lista); setError(null); } })
      .catch((fallo) => { if (vigente) setError(fallo.mensaje); })
      .finally(() => { if (vigente) setCargando(false); });
    return () => { vigente = false; };
  }, []);

  const cambiar = (idMaterial, delta) => {
    setCantidades((actual) => {
      const nueva = Math.max(0, (actual[idMaterial] ?? 0) + delta);
      return { ...actual, [idMaterial]: nueva };
    });
    setEnviado(false);
  };

  const lineas = Object.entries(cantidades).filter(([, c]) => c > 0);
  const unidades = lineas.reduce((suma, [, c]) => suma + c, 0);

  const enviar = async () => {
    setEnviando(true);
    setError(null);
    try {
      await crearPedido({
        idObra: obra.idObra,
        materiales: lineas.map(([idMaterial, cantidad]) => ({
          idMaterial: Number(idMaterial),
          cantidad,
        })),
      });
      setCantidades({});
      setEnviado(true);
    } catch (fallo) {
      setError(fallo.mensaje);
    } finally {
      setEnviando(false);
    }
  };

  if (cargando) {
    return <p className={estilos.nota}>Cargando el catálogo…</p>;
  }

  return (
    <>
      <div className={estilos.catalogoCabecera}>
        <span className="kicker">Catálogo Granica</span>
        <span className={estilos.entrega}>{materiales.length} materiales</span>
      </div>

      {error && <p className={estilos.error}>{error}</p>}

      {materiales.length === 0 && (
        <p className={estilos.nota}>
          El catálogo está vacío. Lo carga la oficina desde el módulo Materiales.
        </p>
      )}

      <ul className={estilos.catalogo}>
        {materiales.map((material) => {
          const cantidad = cantidades[material.idMaterial] ?? 0;
          // El paso no es de a uno para todo: nadie pide una bolsa de cemento
          // sola. Se deduce de la unidad de medida, que es el dato que hay.
          const paso = pasoSegunUnidad(material.unidadMedida);

          return (
            <li
              key={material.idMaterial}
              className={`${estilos.material} ${cantidad ? estilos.materialPedido : ''}`.trim()}
            >
              <div className={estilos.materialTexto}>
                <span className={estilos.materialNombre}>{material.nombreMaterial}</span>
                <span className={estilos.materialUnidad}>
                  {material.unidadMedida} · {material.nombreRubro}
                </span>
              </div>

              <div className={estilos.contador}>
                <button
                  type="button"
                  className={estilos.contadorBoton}
                  onClick={() => cambiar(material.idMaterial, -paso)}
                  disabled={cantidad === 0}
                  aria-label={`Quitar ${paso} de ${material.nombreMaterial}`}
                >
                  <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                       strokeWidth="1.8" strokeLinecap="round">
                    <path d="M5 12h14" />
                  </svg>
                </button>

                <span className={`cifra ${estilos.contadorValor} ${cantidad ? '' : estilos.contadorVacio}`.trim()}>
                  {cantidad === 0 ? '—' : cantidad}
                </span>

                <button
                  type="button"
                  className={`${estilos.contadorBoton} ${cantidad ? estilos.contadorMas : ''}`.trim()}
                  onClick={() => cambiar(material.idMaterial, paso)}
                  aria-label={`Agregar ${paso} de ${material.nombreMaterial}`}
                >
                  <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                       strokeWidth="1.8" strokeLinecap="round">
                    <path d="M12 5v14" />
                    <path d="M5 12h14" />
                  </svg>
                </button>
              </div>
            </li>
          );
        })}
      </ul>

      <div className={estilos.cierre}>
        <div className={estilos.resumen}>
          <span className="kicker">En el pedido</span>
          <span className={`cifra ${estilos.resumenValor}`}>
            {lineas.length === 0 ? 'nada aún' : `${lineas.length} ítems · ${unidades} u.`}
          </span>
        </div>

        <Blueprint
          as="button" type="button" claro
          className={estilos.enviar}
          onClick={enviar}
          disabled={lineas.length === 0 || enviando}
        >
          <span>{enviando ? 'Enviando…' : (enviado ? 'Pedido enviado ✓' : 'Enviar pedido')}</span>
          <svg width="30" height="30" viewBox="0 0 24 24" fill="none" stroke="currentColor"
               strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
            <path d="M5 12h14" />
            <path d="m13 6 6 6-6 6" />
          </svg>
        </Blueprint>

        <p className={estilos.nota}>Lo aprueba la oficina antes de salir del corralón.</p>
      </div>
    </>
  );
}

/* ========================================================================== */

/**
 * Confirmación de recepción de los pedidos que llegaron.
 *
 * Solo aparecen los pedidos en "Enviado al Proveedor": son los únicos que se
 * pueden recibir. Un pedido que todavía espera aprobación no llegó, y uno ya
 * recibido no se recibe dos veces.
 */
function Recepcion({ obra }) {
  const [pedidos, setPedidos] = useState([]);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState(null);
  const [elegido, setElegido] = useState(null);

  const recargar = useCallback(() => {
    listarPedidos({ obra: obra.idObra, estado: 'Enviado al Proveedor' })
      .then((lista) => {
        setPedidos(lista);
        setError(null);
        // Con uno solo no hay nada que elegir: es el caso normal en obra.
        setElegido((actual) => actual ?? (lista.length === 1 ? lista[0] : null));
      })
      .catch((fallo) => setError(fallo.mensaje))
      .finally(() => setCargando(false));
  }, [obra.idObra]);

  useEffect(() => { recargar(); }, [recargar]);

  if (cargando) {
    return <p className={estilos.nota}>Buscando pedidos en camino…</p>;
  }

  if (pedidos.length === 0) {
    return (
      <div className={estilos.recepcion}>
        <p className={estilos.nota}>
          No hay pedidos esperando recepción. Acá aparecen los que la oficina ya
          aprobó y envió al proveedor.
        </p>
      </div>
    );
  }

  if (!elegido) {
    return (
      <div className={estilos.recepcion}>
        <p className={estilos.paso}>¿Cuál llegó?</p>
        {pedidos.map((p) => (
          <Blueprint as="button" type="button" key={p.idPedido}
                     className={estilos.pedidoBloque}
                     onClick={() => setElegido(p)}>
            <div className={estilos.pedidoCabecera}>
              <span className="kicker kicker-acento">Pedido #{p.idPedido}</span>
              <span className={estilos.aprobado}>{p.nombreProveedor}</span>
            </div>
            <p className={estilos.pedidoDetalle}>{resumen(p)}</p>
          </Blueprint>
        ))}
      </div>
    );
  }

  return (
    <FormularioRecepcion
      pedido={elegido}
      varios={pedidos.length > 1}
      onVolver={() => setElegido(null)}
      onConfirmado={() => { setElegido(null); recargar(); }}
      errorPrevio={error}
    />
  );
}

/* ========================================================================== */

function FormularioRecepcion({ pedido, varios, onVolver, onConfirmado, errorPrevio }) {
  const [remito, setRemito] = useState('');
  const [huboDiferencias, setHuboDiferencias] = useState(null);
  const [notaDiferencia, setNotaDiferencia] = useState('');
  const [error, setError] = useState(errorPrevio ?? null);
  const [confirmando, setConfirmando] = useState(false);

  // Regla del informe: si hubo diferencias, la nota es obligatoria. Sin eso la
  // recepción no se puede confirmar, porque un "faltó algo" sin detalle no le
  // sirve a nadie después.
  const listo = remito.trim().length > 0
    && huboDiferencias !== null
    && (huboDiferencias === 'no' || notaDiferencia.trim().length > 0);

  const confirmar = async () => {
    setConfirmando(true);
    setError(null);
    try {
      // La nota vacía es lo que le dice al backend que vino todo: el estado
      // final (Recibido Completo o con Diferencias) lo deduce él de la nota,
      // no lo elige esta pantalla.
      await recibirPedido(pedido.idPedido, {
        fotoRemito: remito.trim(),
        notaDiferencia: huboDiferencias === 'si' ? notaDiferencia.trim() : null,
      });
      onConfirmado();
    } catch (fallo) {
      setError(fallo.mensaje);
    } finally {
      setConfirmando(false);
    }
  };

  return (
    <div className={estilos.recepcion}>

      <Blueprint className={estilos.pedidoBloque}>
        <div className={estilos.pedidoCabecera}>
          <span className="kicker kicker-acento">Pedido #{pedido.idPedido}</span>
          <span className={estilos.aprobado}>Aprobado</span>
        </div>
        <p className={estilos.pedidoDetalle}>{resumen(pedido)}</p>
        <p className={estilos.pedidoPie}>{pedido.nombreProveedor}</p>
      </Blueprint>

      {varios && (
        <button type="button" className={estilos.cambiar} onClick={onVolver}>
          ← Elegir otro pedido
        </button>
      )}

      {error && <p className={estilos.error}>{error}</p>}

      {/* ---------- Paso 1: foto del remito ---------- */}
      <p className={estilos.paso}>01 · Foto del remito</p>
      <SubirImagen
        carpeta="remitos"
        valor={remito}
        onSubida={setRemito}
        etiqueta="Sacar foto del remito"
      />
      <p className={estilos.ayudaFoto}>
        Desde el celular el botón abre la cámara directo. Es lo que reemplaza al
        remito en papel, que es lo que hoy se pierde.
      </p>

      {/* ---------- Paso 2: diferencias ---------- */}
      <p className={estilos.paso}>02 · ¿Hubo diferencias?</p>
      <div className={estilos.diferencias}>
        <button
          type="button"
          className={`${estilos.opcion} ${huboDiferencias === 'no' ? estilos.opcionSinDif : ''}`.trim()}
          onClick={() => setHuboDiferencias('no')}
          aria-pressed={huboDiferencias === 'no'}
        >
          Vino todo
        </button>
        <button
          type="button"
          className={`${estilos.opcion} ${huboDiferencias === 'si' ? estilos.opcionConDif : ''}`.trim()}
          onClick={() => setHuboDiferencias('si')}
          aria-pressed={huboDiferencias === 'si'}
        >
          Faltó algo
        </button>
      </div>

      {huboDiferencias === 'si' && (
        <textarea
          className={estilos.nota2}
          value={notaDiferencia}
          onChange={(evento) => setNotaDiferencia(evento.target.value)}
          placeholder="Ej: faltaron 4 bolsas, 2 rotas"
          aria-label="Detalle de la diferencia"
        />
      )}

      {/* ---------- Confirmación ---------- */}
      <Blueprint
        as="button" type="button"
        className={`${estilos.confirmar} ${listo ? estilos.confirmarListo : ''}`.trim()}
        onClick={confirmar}
        disabled={!listo || confirmando}
      >
        <span>{confirmando ? 'Confirmando…' : 'Confirmar recepción'}</span>
        <svg width="34" height="34" viewBox="0 0 24 24" fill="none" stroke="currentColor"
             strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round">
          <path d="M20 6 9 17l-5-5" />
        </svg>
      </Blueprint>

      <p className={estilos.notaFinal}>
        {listo
          ? 'Al confirmar, el gasto de los materiales se carga solo en la obra.'
          : 'Necesita el remito y la respuesta de arriba.'}
      </p>
    </div>
  );
}

/* ========================================================================== */

/** "40 cemento, 6 arena gruesa" — lo que entra en dos líneas de un celular. */
function resumen(pedido) {
  return (pedido.materiales ?? [])
    .slice(0, 3)
    .map((m) => `${Math.round(Number(m.cantidad))} ${m.nombreMaterial}`)
    .join('\n');
}

/**
 * De a cuánto sube y baja el contador, según la unidad.
 *
 * Es presentación pura: el backend acepta cualquier cantidad. Acá se acomoda a
 * cómo se pide en la realidad, para que armar un pedido de cuarenta bolsas no
 * sean cuarenta toques.
 */
function pasoSegunUnidad(unidad) {
  const u = (unidad ?? '').toLowerCase();
  if (u.includes('bolsa')) return 10;
  if (u.includes('m³') || u.includes('m3') || u.includes('metro cúbico')) return 1;
  if (u.includes('m²') || u.includes('m2')) return 5;
  if (u.includes('kg') || u.includes('litro')) return 5;
  return 1;
}

/** "Av. Cabildo 2340, Belgrano" → "Av. Cabildo 2340", que es lo que entra. */
function corto(direccion) {
  return direccion.split(',')[0];
}
