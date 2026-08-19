import { useEffect, useState } from 'react';
import Modal from '../../components/ui/Modal';
import { compararCotizaciones, diasDesde, fechaCorta, pesos } from './proveedoresApi';
import estilos from './Proveedores.module.css';

/** A partir de estos días, una cotización se considera vieja para decidir. */
const DIAS_PARA_ENVEJECER = 30;
const DIAS_PARA_DESCARTAR = 90;

/**
 * Comparador de cotizaciones.
 *
 * Responde la pregunta concreta del dueño al aprobar un pedido: "necesito
 * cemento, ¿quién me lo hace más barato hoy?". Muestra la última cotización de
 * cada proveedor activo, de menor a mayor precio.
 *
 * La antigüedad se marca a propósito. En un contexto de inflación, un precio de
 * hace ocho meses no es un precio: es un dato histórico. Sin esa marca, el
 * comparador podría recomendar al más barato solo porque nadie le pidió
 * cotización nueva.
 */
export default function Comparador({ materiales, onCerrar }) {
  const [idMaterial, setIdMaterial] = useState('');
  const [cotizaciones, setCotizaciones] = useState([]);
  const [cargando, setCargando] = useState(false);
  const [error, setError] = useState(null);

  useEffect(() => {
    if (!idMaterial) return undefined;

    let vigente = true;

    (async () => {
      // setCargando va dentro del bloque asíncrono y no antes: llamarlo de
      // forma síncrona dentro del efecto dispara un render extra por cada
      // cambio de material, y el linter lo marca con razón.
      setCargando(true);
      try {
        const datos = await compararCotizaciones(idMaterial);
        if (vigente) { setCotizaciones(datos); setError(null); }
      } catch (fallo) {
        if (vigente) setError(fallo.mensaje);
      } finally {
        if (vigente) setCargando(false);
      }
    })();

    return () => { vigente = false; };
  }, [idMaterial]);

  const material = materiales.find((m) => String(m.idMaterial) === idMaterial);

  // Sin material elegido no hay nada que mostrar. Se deriva en lugar de vaciar
  // el estado desde el efecto: así no hace falta un render extra solo para
  // limpiar una lista que igual no se va a dibujar.
  const listaVisible = idMaterial ? cotizaciones : [];

  return (
    <Modal abierto onCerrar={onCerrar} titulo="Comparar precios">
      <div className={estilos.campo}>
        <label className={estilos.etiqueta} htmlFor="materialComparar">Material</label>
        <select id="materialComparar" className={estilos.control} value={idMaterial}
                onChange={(e) => setIdMaterial(e.target.value)} autoFocus>
          <option value="">Elegir material…</option>
          {materiales.map((m) => (
            <option key={m.idMaterial} value={m.idMaterial}>
              {m.nombreMaterial} — {m.nombreRubro}
            </option>
          ))}
        </select>
      </div>

      {error && <p className={estilos.errorGeneral}>{error}</p>}
      {cargando && <p className={estilos.aviso}>Consultando…</p>}

      {!cargando && idMaterial && listaVisible.length === 0 && (
        <p className={estilos.aviso}>
          Todavía no hay cotizaciones de {material?.nombreMaterial} de ningún
          proveedor activo.
        </p>
      )}

      {listaVisible.length > 0 && (
        <>
          <p className={estilos.ayuda}>
            Última cotización de cada proveedor activo, de menor a mayor precio.
            El precio es por {material?.unidadMedida}.
          </p>

          <ul className={estilos.comparacion}>
            {listaVisible.map((c, indice) => {
              const dias = diasDesde(c.fechaCotizacion);
              const vieja = dias >= DIAS_PARA_ENVEJECER;
              const muyVieja = dias >= DIAS_PARA_DESCARTAR;

              return (
                <li key={c.idCotizacion}
                    className={indice === 0 ? estilos.masBarato : undefined}>
                  <div className={estilos.comparacionProveedor}>
                    <span className={estilos.nombre}>{c.nombreProveedor}</span>
                    <span className={estilos.zona}>{c.zonaCobertura}</span>
                  </div>

                  <div className={estilos.comparacionPrecio}>
                    <span className={`cifra ${estilos.precio}`}>{pesos(c.precioCotizado)}</span>
                    <span className={
                      muyVieja ? estilos.antiguedadVieja
                        : vieja ? estilos.antiguedadMedia : estilos.antiguedad
                    }>
                      {fechaCorta(c.fechaCotizacion)}
                      {dias === 0 ? ' · hoy' : ` · hace ${dias} d`}
                    </span>
                  </div>
                </li>
              );
            })}
          </ul>

          {listaVisible.some((c) => diasDesde(c.fechaCotizacion) >= DIAS_PARA_DESCARTAR) && (
            <p className={estilos.avisoAntiguedad}>
              Hay cotizaciones de más de {DIAS_PARA_DESCARTAR} días. Con la
              variación de precios actual, conviene pedirlas de nuevo antes de
              decidir.
            </p>
          )}
        </>
      )}

      <div className={estilos.accionesFormulario}>
        <button type="button" className={estilos.botonSecundario} onClick={onCerrar}>
          Cerrar
        </button>
      </div>
    </Modal>
  );
}
