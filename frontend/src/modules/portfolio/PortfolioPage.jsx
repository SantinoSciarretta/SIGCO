import { useCallback, useEffect, useState } from 'react';
import Blueprint from '../../components/ui/Blueprint';
import Modal from '../../components/ui/Modal';
import { listarObras } from '../obras/obrasApi';
import client from '../../api/client';
import estilos from './Portfolio.module.css';

/* --------------------------------------------------------------------------
   Cliente del módulo. Va en el mismo archivo porque son seis llamadas cortas
   que solo usa esta pantalla.
   -------------------------------------------------------------------------- */

const api = {
  listar: () => client.get('/portfolio').then((r) => r.data),
  crear: (datos) => client.post('/portfolio', datos).then((r) => r.data),
  publicar: (id) => client.patch(`/portfolio/${id}/publicacion`).then((r) => r.data),
  despublicar: (id) => client.patch(`/portfolio/${id}/despublicacion`).then((r) => r.data),
  agregarImagen: (id, urlImagen) =>
    client.post(`/portfolio/${id}/imagenes`, { urlImagen }).then((r) => r.data),
  quitarImagen: (id, idImagen) =>
    client.delete(`/portfolio/${id}/imagenes/${idImagen}`).then((r) => r.data),
};

/**
 * Panel de administración del portfolio.
 *
 * Reemplaza las fotos dispersas y sin ordenar en el teléfono del dueño, que hoy
 * son difíciles de encontrar cuando un cliente las pide.
 *
 * El portfolio es respaldo visual para clientes que llegan por recomendación:
 * NO capta clientes nuevos. Por eso la vidriera no tiene formulario de contacto,
 * por decisión explícita del dueño.
 */
export default function PortfolioPage() {
  const [publicaciones, setPublicaciones] = useState([]);
  const [obras, setObras] = useState([]);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState(null);
  const [recarga, setRecarga] = useState(0);
  const [altaAbierta, setAltaAbierta] = useState(false);

  const recargar = useCallback(() => setRecarga((n) => n + 1), []);

  useEffect(() => {
    let vigente = true;
    (async () => {
      try {
        const datos = await api.listar();
        if (vigente) { setPublicaciones(datos); setError(null); }
      } catch (fallo) {
        if (vigente) setError(fallo.mensaje);
      } finally {
        if (vigente) setCargando(false);
      }
    })();
    return () => { vigente = false; };
  }, [recarga]);

  useEffect(() => {
    let vigente = true;
    listarObras().then((d) => { if (vigente) setObras(d); }).catch(() => {});
    return () => { vigente = false; };
  }, []);

  // Solo obras finalizadas y que no estén ya publicadas: el backend valida lo
  // mismo, acá se evita ofrecer lo que va a fallar.
  const yaPublicadas = new Set(publicaciones.map((p) => p.idObra));
  const disponibles = obras.filter(
    (o) => o.estado === 'Finalizada' && !yaPublicadas.has(o.idObra));

  const accion = async (fn) => {
    try { await fn(); recargar(); } catch (fallo) { setError(fallo.mensaje); }
  };

  return (
    <>
      <div className={estilos.cabecera}>
        <div>
          <span className="kicker kicker-acento">Módulo</span>
          <h2 className={estilos.titulo}>Portfolio web</h2>
          <p className={estilos.bajada}>
            Vidriera de obras terminadas, para mostrar a clientes referidos.
          </p>
        </div>

        <div className={estilos.herramientas}>
          <button type="button" className={estilos.botonPrimario}
                  onClick={() => setAltaAbierta(true)} disabled={disponibles.length === 0}>
            Publicar una obra
          </button>
        </div>
      </div>

      <p className={estilos.privacidad}>
        La vidriera muestra únicamente las imágenes y el tipo de trabajo.{' '}
        <b>No expone el nombre del cliente ni la dirección de la obra</b>, y no
        tiene formulario de contacto: el portfolio es respaldo visual, no capta
        clientes nuevos.
      </p>

      {error && <p className={estilos.errorGeneral}>{error}</p>}

      {cargando && <p className={estilos.aviso}>Consultando…</p>}

      {!cargando && publicaciones.length === 0 && (
        <Blueprint className={estilos.bloque}>
          <div className={estilos.vacio}>
            <p className={estilos.vacioTitulo}>Todavía no hay obras en el portfolio</p>
            <p className={estilos.vacioTexto}>
              {disponibles.length === 0
                ? 'Solo se publican obras finalizadas, y todavía no hay ninguna disponible.'
                : 'Elegí una obra terminada y cargale las fotos.'}
            </p>
          </div>
        </Blueprint>
      )}

      {publicaciones.map((p) => (
        <div key={p.idPublicacion} className={estilos.tarjetaObra}>
          <div className={estilos.tarjetaCabecera}>
            <div>
              <div className={estilos.obra}>{p.direccionObra}</div>
              <div className={estilos.cliente}>{p.nombreCliente}</div>
            </div>
            <div>
              <span className={estilos.tipoTrabajo}>{p.tipoTrabajo}</span>{' '}
              <span className={p.estado === 'Publicada'
                ? estilos.publicada : estilos.despublicada}>
                {p.estado}
              </span>
            </div>
          </div>

          <div className={estilos.galeria}>
            {p.imagenes.map((img) => (
              <div key={img.idImagen} className={estilos.miniatura}>
                {img.urlImagen}
                <button type="button" className={estilos.quitarImagen}
                        onClick={() => accion(() => api.quitarImagen(p.idPublicacion, img.idImagen))}
                        aria-label="Quitar imagen">
                  ×
                </button>
              </div>
            ))}
            {p.imagenes.length === 0 && (
              <p className={estilos.ayuda}>Sin imágenes cargadas.</p>
            )}
          </div>

          <AgregarImagen
            idPublicacion={p.idPublicacion}
            onAgregada={recargar}
            onError={setError}
          />

          <div className={estilos.accionesFormulario}>
            {p.estado === 'Publicada' ? (
              <button type="button" className={estilos.botonSecundario}
                      onClick={() => accion(() => api.despublicar(p.idPublicacion))}>
                Despublicar
              </button>
            ) : (
              <button type="button" className={estilos.botonPrimario}
                      onClick={() => accion(() => api.publicar(p.idPublicacion))}>
                Publicar en la vidriera
              </button>
            )}
          </div>

          {p.estado === 'Despublicada' && p.imagenes.length > 0 && (
            <p className={estilos.ayuda}>
              Las {p.imagenes.length} imágenes quedan guardadas: se puede volver
              a publicar sin recargarlas.
            </p>
          )}
        </div>
      ))}

      {altaAbierta && (
        <NuevaPublicacionModal
          obras={disponibles}
          onCerrar={() => setAltaAbierta(false)}
          onCreada={() => { setAltaAbierta(false); recargar(); }}
        />
      )}
    </>
  );
}

/* ========================================================================== */

function AgregarImagen({ idPublicacion, onAgregada, onError }) {
  const [urlImagen, setUrl] = useState('');
  const [guardando, setGuardando] = useState(false);

  const enviar = async (evento) => {
    evento.preventDefault();
    setGuardando(true);
    try {
      await api.agregarImagen(idPublicacion, urlImagen);
      setUrl('');
      onAgregada();
    } catch (fallo) {
      onError(fallo.mensaje);
    } finally {
      setGuardando(false);
    }
  };

  return (
    <form onSubmit={enviar} className={estilos.formularioEnLinea ?? estilos.campo}>
      <input className={estilos.control} maxLength={255}
             placeholder="portfolio/cocina-terminada.jpg" value={urlImagen}
             onChange={(e) => setUrl(e.target.value)} required
             aria-label="Referencia de la imagen" />
      <button type="submit" className={estilos.botonSecundario} disabled={guardando}>
        {guardando ? '…' : 'Agregar imagen'}
      </button>
    </form>
  );
}

/* ========================================================================== */

function NuevaPublicacionModal({ obras, onCerrar, onCreada }) {
  const [idObra, setIdObra] = useState('');
  const [tipoTrabajo, setTipoTrabajo] = useState('');
  const [error, setError] = useState(null);
  const [guardando, setGuardando] = useState(false);

  const enviar = async (evento) => {
    evento.preventDefault();
    setGuardando(true);
    setError(null);
    try {
      await api.crear({ idObra: Number(idObra), tipoTrabajo });
      onCreada();
    } catch (fallo) {
      setError(fallo.mensaje);
    } finally {
      setGuardando(false);
    }
  };

  return (
    <Modal abierto onCerrar={onCerrar} titulo="Publicar una obra">
      <form onSubmit={enviar}>
        {error && <p className={estilos.errorGeneral}>{error}</p>}

        <div className={estilos.campo}>
          <label className={estilos.etiqueta} htmlFor="idObraPortfolio">
            Obra <span className={estilos.obligatorio}>*</span>
          </label>
          <select id="idObraPortfolio" className={estilos.control} value={idObra}
                  onChange={(e) => setIdObra(e.target.value)} required autoFocus>
            <option value="">Elegir obra terminada…</option>
            {obras.map((o) => (
              <option key={o.idObra} value={o.idObra}>{o.direccionObra}</option>
            ))}
          </select>
          <p className={estilos.ayuda}>
            Solo aparecen obras finalizadas: el portfolio muestra trabajos
            terminados.
          </p>
        </div>

        <div className={estilos.campo}>
          <label className={estilos.etiqueta} htmlFor="tipoTrabajo">
            Tipo de trabajo <span className={estilos.obligatorio}>*</span>
          </label>
          <input id="tipoTrabajo" className={estilos.control} maxLength={30}
                 list="tiposSugeridos" placeholder="Refacción"
                 value={tipoTrabajo} onChange={(e) => setTipoTrabajo(e.target.value)} required />
          {/* Lista abierta a propósito: el informe dice "entre otros". Se
              sugieren los habituales sin encerrar el campo. */}
          <datalist id="tiposSugeridos">
            <option value="Construcción" />
            <option value="Refacción" />
            <option value="Decoración de local" />
          </datalist>
          <p className={estilos.ayuda}>
            Es lo que se usa para agrupar la vidriera. Podés escribir uno nuevo.
          </p>
        </div>

        <div className={estilos.accionesFormulario}>
          <button type="button" className={estilos.botonSecundario} onClick={onCerrar}>
            Cancelar
          </button>
          <button type="submit" className={estilos.botonPrimario} disabled={guardando}>
            {guardando ? 'Creando…' : 'Crear publicación'}
          </button>
        </div>
      </form>
    </Modal>
  );
}
