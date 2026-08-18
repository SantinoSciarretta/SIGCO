import { useState } from 'react';
import Blueprint from '../../../components/ui/Blueprint';
import CabeceraCapataz from './CabeceraCapataz';
import { useDemo } from '../../../datos/contextoDemo';
import { CATALOGO, OBRAS, OBRA_DEL_CAPATAZ } from '../../../datos/demo';
import estilos from './Materiales.module.css';

/**
 * Materiales — pedido y recepción, desde el celular en obra.
 *
 * Reemplaza el circuito que hoy pasa por WhatsApp y no deja registro. Son dos
 * momentos distintos del mismo circuito, por eso van en pestañas:
 *
 *   Pedir   → el capataz arma el pedido desde el catálogo. El dueño lo aprueba
 *             antes de que salga al proveedor: esa aprobación no es delegable.
 *   Recibir → cuando llega el camión, el capataz saca la foto del remito y
 *             declara si vino todo. Es lo que hoy se pierde en papel.
 *
 * TODO: conectar con POST /api/pedidos y PATCH /api/pedidos/{id}/recepcion
 *       al desarrollar módulo Compras
 */
export default function Materiales() {
  const [pestana, setPestana] = useState('pedido');
  const obra = OBRAS[OBRA_DEL_CAPATAZ];

  return (
    <>
      <CabeceraCapataz titulo={`Materiales · ${obra.corto}`}>
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

      {pestana === 'pedido' ? <Pedido /> : <Recepcion />}
    </>
  );
}

/* ========================================================================== */

/** Armado del pedido desde el catálogo. */
function Pedido() {
  const { cantidades, sumarCantidad, restarCantidad, pedidoEnviado, enviarPedido } = useDemo();

  const lineas = CATALOGO.filter((_, i) => cantidades[i]).length;
  const unidades = CATALOGO.reduce((suma, _, i) => suma + (cantidades[i] || 0), 0);

  return (
    <>
      <div className={estilos.catalogoCabecera}>
        <span className="kicker">Catálogo Granica</span>
        <span className={estilos.entrega}>Entrega mañana AM</span>
      </div>

      <ul className={estilos.catalogo}>
        {CATALOGO.map((material, i) => {
          const cantidad = cantidades[i] || 0;

          return (
            <li
              key={material.nombre}
              className={`${estilos.material} ${cantidad ? estilos.materialPedido : ''}`.trim()}
            >
              <div className={estilos.materialTexto}>
                <span className={estilos.materialNombre}>{material.nombre}</span>
                <span className={estilos.materialUnidad}>{material.unidad}</span>
              </div>

              {/* El paso no es de a uno: nadie pide una bolsa de cemento sola.
                  Cada material sube y baja de a lo que se pide en la realidad. */}
              <div className={estilos.contador}>
                <button
                  type="button"
                  className={estilos.contadorBoton}
                  onClick={() => restarCantidad(i, material.paso)}
                  disabled={cantidad === 0}
                  aria-label={`Quitar ${material.paso} de ${material.nombre}`}
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
                  onClick={() => sumarCantidad(i, material.paso)}
                  aria-label={`Agregar ${material.paso} de ${material.nombre}`}
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
            {lineas === 0 ? 'nada aún' : `${lineas} ítems · ${unidades} u.`}
          </span>
        </div>

        <Blueprint
          as="button" type="button" claro
          className={estilos.enviar}
          onClick={enviarPedido}
          disabled={lineas === 0}
        >
          <span>{pedidoEnviado ? 'Pedido enviado ✓' : 'Enviar pedido'}</span>
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

/** Confirmación de recepción del pedido, con foto del remito. */
function Recepcion() {
  const {
    fotoTomada, alternarFoto,
    huboDiferencias, setHuboDiferencias,
    notaDiferencia, setNotaDiferencia,
    recepcionConfirmada, confirmarRecepcion,
  } = useDemo();

  // Regla del informe: si hubo diferencias, la nota es obligatoria. Sin eso la
  // recepción no se puede confirmar, porque un "faltó algo" sin detalle no le
  // sirve a nadie después.
  const listo = fotoTomada
    && huboDiferencias !== null
    && (huboDiferencias === 'no' || notaDiferencia.trim().length > 0);

  return (
    <div className={estilos.recepcion}>

      <Blueprint className={estilos.pedidoBloque}>
        <div className={estilos.pedidoCabecera}>
          <span className="kicker kicker-acento">Pedido PD-1194</span>
          <span className={estilos.aprobado}>Aprobado</span>
        </div>
        <p className={estilos.pedidoDetalle}>40 bolsas cemento<br />6 m³ arena gruesa</p>
        <p className={estilos.pedidoPie}>Corralón San Martín · llegó 09:15</p>
      </Blueprint>

      {/* ---------- Paso 1: foto ---------- */}
      <p className={estilos.paso}>01 · Foto del remito</p>
      <button
        type="button"
        className={`${estilos.foto} ${fotoTomada ? estilos.fotoLista : ''}`.trim()}
        onClick={alternarFoto}
        aria-pressed={fotoTomada}
      >
        <svg width="58" height="58" viewBox="0 0 24 24" fill="none" stroke="currentColor"
             strokeWidth="1.3" strokeLinecap="round" strokeLinejoin="round">
          <path d="M14.5 4h-5L8 6H4a2 2 0 0 0-2 2v10a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2V8a2 2 0 0 0-2-2h-4z" />
          <circle cx="12" cy="13" r="3.5" />
        </svg>
        <span className={estilos.fotoTitulo}>
          {fotoTomada ? 'Remito 4471 · foto lista' : 'Sacar foto del remito'}
        </span>
        <span className={estilos.fotoSub}>
          {fotoTomada ? 'Tocá para sacarla de nuevo' : 'Se guarda con el pedido PD-1194'}
        </span>
      </button>

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
        className={`${estilos.confirmar} ${recepcionConfirmada ? estilos.confirmado : ''} ${listo ? estilos.confirmarListo : ''}`.trim()}
        onClick={confirmarRecepcion}
        disabled={!listo}
      >
        <span>{recepcionConfirmada ? 'Recepción confirmada' : 'Confirmar recepción'}</span>
        <svg width="34" height="34" viewBox="0 0 24 24" fill="none" stroke="currentColor"
             strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round">
          <path d="M20 6 9 17l-5-5" />
        </svg>
      </Blueprint>

      <p className={estilos.notaFinal}>
        {recepcionConfirmada
          ? 'Listo. La oficina ya lo tiene.'
          : 'Necesita la foto y la respuesta de arriba.'}
      </p>
    </div>
  );
}
