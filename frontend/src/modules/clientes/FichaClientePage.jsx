import { useEffect, useMemo, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import Blueprint from '../../components/ui/Blueprint';
import { obtenerCliente } from './clientesApi';
import { listarObras } from '../obras/obrasApi';
import { useSesion } from '../sesion/useSesion';
import estilos from './Clientes.module.css';

/**
 * Ficha de cliente.
 *
 * El informe: *"vista de detalle con los datos de contacto, el origen de la
 * recomendación y el listado completo de sus obras, **distinguiendo entre
 * activas, finalizadas y canceladas**"*.
 *
 * Esa distinción es el punto de la pantalla y por eso las obras van en tres
 * grupos y no en una lista ordenada por fecha: lo que el dueño viene a mirar
 * acá es "¿con este cliente estoy trabajando ahora, o ya trabajé?", y una lista
 * mezclada obliga a leerla entera para contestarlo.
 *
 * ------------------------------------------------------------------
 *  Por qué el origen de la recomendación tiene tanto lugar
 * ------------------------------------------------------------------
 *
 * Porque es la única fuente de trabajo de la empresa. El relevamiento es
 * explícito: todos los clientes llegan por referido. Saber quién recomendó a
 * quién es información comercial, no un dato de contacto más.
 */
export default function FichaClientePage() {
  const { id } = useParams();
  const { puede } = useSesion();

  const [cliente, setCliente] = useState(null);
  const [obras, setObras] = useState([]);
  const [error, setError] = useState(null);

  // "Está cargando" se deriva de para qué cliente son los datos que hay, en
  // lugar de ser un estado aparte que hay que poner en true y en false a mano.
  const [cargadoPara, setCargadoPara] = useState(null);
  const cargando = cargadoPara !== id;

  useEffect(() => {
    let vigente = true;

    obtenerCliente(id)
      .then((datos) => { if (vigente) { setCliente(datos); setError(null); } })
      .catch((fallo) => { if (vigente) setError(fallo.mensaje); })
      .finally(() => { if (vigente) setCargadoPara(id); });

    // El historial de obras: es el endpoint que existe desde el módulo Obras y
    // que hasta ahora no tenía pantalla que lo usara.
    if (puede('obras.ver')) {
      listarObras({ cliente: id })
        .then((d) => { if (vigente) setObras(d); })
        .catch(() => {});
    }

    return () => { vigente = false; };
  }, [id, puede]);

  const grupos = useMemo(() => ({
    activas: obras.filter((o) => o.estado === 'En ejecución'
      || o.estado === 'En presupuestación'),
    finalizadas: obras.filter((o) => o.estado === 'Finalizada'),
    canceladas: obras.filter((o) => o.estado === 'Cancelada'),
  }), [obras]);

  if (cargando) {
    return <p className={estilos.aviso}>Consultando el cliente…</p>;
  }
  if (error || !cliente) {
    return <p className={estilos.errorGeneral}>{error ?? 'No se encontró el cliente.'}</p>;
  }

  return (
    <>
      <p className={estilos.volver}>
        <Link to="/clientes">← Volver a clientes</Link>
      </p>

      <header className={estilos.cabecera}>
        <div>
          <span className="kicker kicker-acento">Cliente</span>
          <h2 className={estilos.titulo}>{cliente.nombreApellido}</h2>
          <p className={estilos.bajada}>
            {cliente.estado}
            {cliente.cantidadObras > 0 && <> · {cliente.cantidadObras} obra
              {cliente.cantidadObras > 1 ? 's' : ''}</>}
          </p>
        </div>
      </header>

      <div className={estilos.fichaColumnas}>

        {/* ---------- Datos de contacto y origen ---------- */}
        <Blueprint className={estilos.bloque}>
          <div className={estilos.bloqueCabecera}>
            <h4>Contacto</h4>
          </div>

          <Dato etiqueta="Teléfono" valor={cliente.telefonoContacto} />
          <Dato etiqueta="Correo" valor={cliente.emailContacto} />

          <div className={estilos.bloqueCabecera} style={{ marginTop: 22 }}>
            <h4>Cómo llegó</h4>
          </div>

          <Dato etiqueta="Origen" valor={cliente.origenRecomendacion} />
          <Dato etiqueta="Recomendado por" valor={cliente.recomendadoPor} />

          <p className={estilos.ayuda}>
            Todos los clientes de la empresa llegan por recomendación, así que
            esto no es un dato de contacto más: es de dónde sale el trabajo.
          </p>
        </Blueprint>

        {/* ---------- Historial de obras ---------- */}
        <div className={estilos.fichaColumnaDerecha}>
          {!puede('obras.ver') ? (
            <Blueprint className={estilos.bloque}>
              <p className={estilos.aviso}>
                Tu rol no tiene acceso al módulo Obras, así que no se muestra el
                historial.
              </p>
            </Blueprint>
          ) : obras.length === 0 ? (
            <Blueprint className={estilos.bloque}>
              <p className={estilos.aviso}>
                Este cliente todavía no tiene ninguna obra.
              </p>
            </Blueprint>
          ) : (
            <>
              <GrupoDeObras titulo="Activas" obras={grupos.activas}
                            vacio="No tiene obras en curso." />
              <GrupoDeObras titulo="Finalizadas" obras={grupos.finalizadas}
                            vacio={null} />
              <GrupoDeObras titulo="Canceladas" obras={grupos.canceladas}
                            vacio={null} />
            </>
          )}
        </div>
      </div>
    </>
  );
}

/* ========================================================================== */

function Dato({ etiqueta, valor }) {
  return (
    <div className={estilos.dato}>
      <span className={estilos.datoEtiqueta}>{etiqueta}</span>
      {/* Un guion y no un vacío: deja claro que el campo existe y está sin
          completar, en lugar de parecer que la pantalla se cortó. */}
      <span className={estilos.datoValor}>{valor || '—'}</span>
    </div>
  );
}

/**
 * Un grupo de obras.
 *
 * Los grupos vacíos no se dibujan, salvo el de activas: que un cliente no tenga
 * obras canceladas no es información, pero que no tenga ninguna en curso sí.
 */
function GrupoDeObras({ titulo, obras, vacio }) {
  if (obras.length === 0 && !vacio) return null;

  return (
    <Blueprint className={estilos.bloque}>
      <div className={estilos.bloqueCabecera}>
        <h4>{titulo}</h4>
        <span className="kicker">{obras.length}</span>
      </div>

      {obras.length === 0 ? (
        <p className={estilos.aviso}>{vacio}</p>
      ) : (
        <ul className={estilos.listaObras}>
          {obras.map((obra) => (
            <li key={obra.idObra}>
              <Link to={`/obras/${obra.idObra}`} className={estilos.obraEnlace}>
                <span className={estilos.obraDireccion}>{obra.direccionObra}</span>
                <span className={estilos.obraDetalle}>
                  {obra.tipoObra} · {obra.tipoInmueble} · {obra.estado}
                  {obra.motivoCancelacion && <> · {obra.motivoCancelacion}</>}
                </span>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </Blueprint>
  );
}
