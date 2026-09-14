import { useEffect, useMemo, useState } from 'react';
import client from '../../api/client';
import { urlDeArchivo, esArchivoSubido } from '../../components/ui/archivos';
import estilos from './Vidriera.module.css';

/**
 * Vidriera pública — la única pantalla del sistema que ve alguien de afuera.
 *
 * El informe: *"Vista pública de la vidriera, accesible desde un navegador, sin
 * formularios de contacto ni datos de acceso."*
 *
 * ------------------------------------------------------------------
 *  Lo que esta pantalla NO tiene, y no es un olvido
 * ------------------------------------------------------------------
 *
 * **No hay formulario de contacto, ni teléfono, ni correo.** Es una decisión
 * explícita del dueño, que trabaja únicamente con clientes referidos: el
 * portfolio es respaldo visual de la trayectoria para alguien que ya llegó por
 * recomendación y está evaluando, no una herramienta para captar desconocidos.
 *
 * **No hay nombre de cliente ni dirección de obra.** No es que se omitan al
 * dibujar: el backend no los manda. `VidrieraRespuesta` no tiene esos campos, y
 * hay un test que verifica por reflexión que nadie los agregue. Una validación
 * se puede olvidar; un campo que no existe, no.
 *
 * **No hay login ni menú.** Es la razón por la que esta pantalla vive fuera del
 * marco de la aplicación y por la que `/api/vidriera` está entre las tres rutas
 * abiertas de la configuración de seguridad.
 */
export default function VidrieraPage() {
  const [obras, setObras] = useState([]);
  const [tipo, setTipo] = useState('');
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    let vigente = true;
    // Sin token: la llamada sale igual porque el interceptor solo adjunta la
    // cabecera si hay uno guardado, y esta ruta del backend es abierta.
    client.get('/vidriera')
      .then((r) => { if (vigente) { setObras(r.data); setError(null); } })
      .catch((fallo) => { if (vigente) setError(fallo.mensaje); })
      .finally(() => { if (vigente) setCargando(false); });
    return () => { vigente = false; };
  }, []);

  // Los tipos salen de lo que hay publicado, no de una lista fija: el informe
  // dice "construcción, refacción, decoración de local, entre otros", así que
  // encerrarlos obligaría a tocar el código cada vez que aparece uno nuevo.
  const tipos = useMemo(
    () => [...new Set(obras.map((o) => o.tipoTrabajo))].sort(),
    [obras],
  );

  const visibles = tipo ? obras.filter((o) => o.tipoTrabajo === tipo) : obras;

  return (
    <div className={estilos.pantalla}>
      {/* Cuadrícula de fondo: la hoja milimetrada del plano, igual que en el
          ingreso. Es lo que hace que esta pantalla se reconozca como parte del
          mismo sistema aunque no tenga la barra de navegación. */}
      <div className={estilos.cuadricula} aria-hidden="true" />

      <header className={estilos.encabezado}>
        <div className={estilos.marca}>GRANICA</div>
        <p className={estilos.bajada}>
          Construcción, refacción y decoración de locales<br />
          CABA y Gran Buenos Aires
        </p>
      </header>

      <main className={estilos.contenido}>

        {cargando && <p className={estilos.aviso}>Cargando obras…</p>}
        {error && <p className={estilos.aviso}>{error}</p>}

        {!cargando && obras.length === 0 && (
          <p className={estilos.aviso}>Todavía no hay obras publicadas.</p>
        )}

        {tipos.length > 1 && (
          <nav className={estilos.filtros} aria-label="Filtrar por tipo de trabajo">
            <button type="button"
                    className={`${estilos.filtro} ${tipo === '' ? estilos.filtroActivo : ''}`.trim()}
                    onClick={() => setTipo('')}>
              Todas
            </button>
            {tipos.map((t) => (
              <button key={t} type="button"
                      className={`${estilos.filtro} ${tipo === t ? estilos.filtroActivo : ''}`.trim()}
                      onClick={() => setTipo(t)}>
                {t}
              </button>
            ))}
          </nav>
        )}

        <div className={estilos.galeria}>
          {visibles.map((obra) => (
            <Obra key={obra.idPublicacion} obra={obra} />
          ))}
        </div>

      </main>

      <footer className={estilos.pie}>
        {/* Sin teléfono ni correo: decisión del dueño, no un olvido. */}
        <p>Granica SRL · Trabajamos por recomendación</p>
      </footer>
    </div>
  );
}

/* ========================================================================== */

/**
 * Una obra de la galería.
 *
 * Muestra el tipo de trabajo y las fotos. Nada más, porque nada más llega: ni
 * el cliente ni la dirección existen en la respuesta.
 */
function Obra({ obra }) {
  const [abierta, setAbierta] = useState(null);

  // Las publicaciones cargadas antes de que existiera la subida tienen un texto
  // escrito a mano en lugar de un archivo. No se pueden mostrar como imagen.
  const fotos = obra.imagenes.filter(esArchivoSubido);

  return (
    <article className={estilos.obra}>
      <header className={estilos.obraCabecera}>
        <span className={estilos.tipo}>{obra.tipoTrabajo}</span>
        <span className={estilos.fecha}>{ano(obra.fechaPublicacion)}</span>
      </header>

      {fotos.length === 0 ? (
        <p className={estilos.sinFotos}>Sin imágenes cargadas.</p>
      ) : (
        <div className={estilos.fotos}>
          {fotos.map((referencia, i) => (
            <button key={referencia} type="button" className={estilos.foto}
                    onClick={() => setAbierta(referencia)}
                    aria-label={`Ampliar foto ${i + 1} de ${obra.tipoTrabajo}`}>
              <img src={urlDeArchivo(referencia)} alt=""
                   loading="lazy" className={estilos.imagen} />
            </button>
          ))}
        </div>
      )}

      {abierta && (
        /* Ampliación a pantalla completa. Se cierra tocando en cualquier lado:
           en un celular no hay lugar para apuntarle a una cruz chica. */
        <div className={estilos.ampliada} role="dialog" aria-modal="true"
             onClick={() => setAbierta(null)}>
          <img src={urlDeArchivo(abierta)} alt={`Obra de ${obra.tipoTrabajo}`} />
          <span className={estilos.cerrar}>Tocá para cerrar</span>
        </div>
      )}
    </article>
  );
}

/** El año de la publicación. La fecha exacta no le dice nada a un visitante. */
function ano(iso) {
  return iso ? new Date(iso).getFullYear() : '';
}
