import { Fragment, useCallback, useEffect, useMemo, useState } from 'react';
import Blueprint from '../../components/ui/Blueprint';
import { accesosApi, momento } from './usuariosApi';
import estilos from './Usuarios.module.css';

/**
 * Módulo 14 — Accesos.
 *
 * Dos cosas en una pantalla, y las dos son la misma pregunta vista desde
 * lugares distintos: qué PUEDE hacer cada rol (la matriz) y qué HIZO cada
 * usuario (la auditoría).
 *
 * La matriz es la traducción visual de la tabla del informe. Cada casilla es
 * una fila de `rol_permiso` en la base, y el texto de cada permiso es
 * literalmente el mismo que usa el backend en @PreAuthorize: la matriz del
 * informe se puede verificar buscando esa cadena en el código.
 *
 * Se guarda la fila completa de una vez, no casilla por casilla: el dueño marca
 * y desmarca varias antes de guardar, y aplicar cada cambio por separado
 * dejaría el rol en estados intermedios que nadie pidió.
 */
export default function AccesosPage() {
  const [roles, setRoles] = useState([]);
  const [permisos, setPermisos] = useState([]);
  const [auditoria, setAuditoria] = useState([]);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState(null);
  const [aviso, setAviso] = useState(null);
  const [recarga, setRecarga] = useState(0);

  /** Cambios pendientes de guardar: idRol -> Set de idsPermisos. */
  const [edicion, setEdicion] = useState({});
  const [guardando, setGuardando] = useState(null);

  const recargar = useCallback(() => setRecarga((n) => n + 1), []);

  useEffect(() => {
    let vigente = true;
    Promise.all([accesosApi.roles(), accesosApi.permisos()])
      .then(([r, p]) => {
        if (!vigente) return;
        setRoles(r);
        setPermisos(p);
        // El estado de edición arranca reflejando lo que hay en la base.
        setEdicion(Object.fromEntries(r.map((rol) => [rol.idRol, new Set(rol.idsPermisos)])));
        setError(null);
      })
      .catch((fallo) => { if (vigente) setError(fallo.mensaje); })
      .finally(() => { if (vigente) setCargando(false); });
    return () => { vigente = false; };
  }, [recarga]);

  useEffect(() => {
    let vigente = true;
    accesosApi.auditoria().then((d) => { if (vigente) setAuditoria(d); }).catch(() => {});
    return () => { vigente = false; };
  }, [recarga]);

  // Los permisos se muestran agrupados por módulo, que es como está organizado
  // el sistema y como se lee la matriz del informe.
  const porModulo = useMemo(() => {
    const grupos = new Map();
    permisos.forEach((p) => {
      if (!grupos.has(p.modulo)) grupos.set(p.modulo, []);
      grupos.get(p.modulo).push(p);
    });
    return [...grupos.entries()];
  }, [permisos]);

  const alternar = (idRol, idPermiso) => {
    setEdicion((actual) => {
      const copia = { ...actual };
      const conjunto = new Set(copia[idRol]);
      if (conjunto.has(idPermiso)) conjunto.delete(idPermiso);
      else conjunto.add(idPermiso);
      copia[idRol] = conjunto;
      return copia;
    });
    setAviso(null);
  };

  const guardar = async (rol) => {
    setGuardando(rol.idRol);
    setError(null);
    try {
      await accesosApi.definirPermisos(rol.idRol, [...edicion[rol.idRol]]);
      setAviso(`Permisos de ${rol.nombreRol} guardados.`);
      recargar();
    } catch (fallo) {
      setError(fallo.mensaje);
    } finally {
      setGuardando(null);
    }
  };

  const hayCambios = (rol) => {
    const actual = edicion[rol.idRol] ?? new Set();
    return actual.size !== rol.idsPermisos.length
      || rol.idsPermisos.some((id) => !actual.has(id));
  };

  return (
    <>
      <div className={estilos.cabecera}>
        <div>
          <span className="kicker kicker-acento">Módulo</span>
          <h2 className={estilos.titulo}>Accesos</h2>
          <p className={estilos.bajada}>
            Qué puede hacer cada rol, y qué hizo cada usuario.
          </p>
        </div>
      </div>

      <p className={estilos.aclaracion}>
        Esto es lo que habilita la delegación controlada: los capataces hacen
        tareas operativas sin ver la información financiera. Cada casilla que
        marques se aplica en la petición siguiente de quien tenga ese rol — no
        hace falta que vuelva a entrar.
      </p>

      {error && <p className={estilos.errorGeneral}>{error}</p>}
      {aviso && <p className={estilos.avisoOk}>{aviso}</p>}
      {cargando && <p className={estilos.aviso}>Consultando…</p>}

      {/* ---------- Matriz de permisos ---------- */}
      {!cargando && (
        <Blueprint className={estilos.bloque}>
          <div className={estilos.bloqueCabecera}>
            <h4>Permisos por rol</h4>
            <span className="kicker">Marcá y guardá la fila</span>
          </div>

          <div className="scroll-x">
            <table className="table">
              <thead>
                <tr>
                  <th style={{ minWidth: 230 }}>Permiso</th>
                  {roles.map((r) => (
                    <th key={r.idRol} style={{ textAlign: 'center', minWidth: 120 }}>
                      {r.nombreRol}
                      <div className={estilos.cuentas}>
                        {r.cuentasActivas} {r.cuentasActivas === 1 ? 'cuenta' : 'cuentas'}
                      </div>
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {porModulo.map(([modulo, lista]) => (
                  // Fragment CON key: agrupa el encabezado del módulo y sus
                  // permisos sin envolverlos en un elemento, que dentro de un
                  // <tbody> rompería la tabla.
                  <Fragment key={modulo}>
                    <tr className={estilos.filaModulo}>
                      <td colSpan={roles.length + 1}>{modulo}</td>
                    </tr>
                    {lista.map((p) => (
                      <tr key={p.idPermiso}>
                        <td>
                          <div className={estilos.permisoNombre}>{p.nombrePermiso}</div>
                          <div className={estilos.tenue}>{p.descripcion}</div>
                        </td>
                        {roles.map((r) => (
                          <td key={r.idRol} style={{ textAlign: 'center' }}>
                            <input
                              type="checkbox"
                              checked={edicion[r.idRol]?.has(p.idPermiso) ?? false}
                              onChange={() => alternar(r.idRol, p.idPermiso)}
                              aria-label={`${p.nombrePermiso} para ${r.nombreRol}`}
                            />
                          </td>
                        ))}
                      </tr>
                    ))}
                  </Fragment>
                ))}
              </tbody>
              <tfoot>
                <tr>
                  <td />
                  {roles.map((r) => (
                    <td key={r.idRol} style={{ textAlign: 'center' }}>
                      <button type="button" className={estilos.botonPrimario}
                              disabled={!hayCambios(r) || guardando === r.idRol}
                              onClick={() => guardar(r)}>
                        {guardando === r.idRol ? 'Guardando…' : 'Guardar'}
                      </button>
                    </td>
                  ))}
                </tr>
              </tfoot>
            </table>
          </div>

          <p className={estilos.ayuda}>
            Hay dos cosas que el sistema no deja hacer, y las rechaza el backend
            aunque marques la casilla: quitarle al rol Dueño la administración de
            accesos o de usuarios —nadie podría volver a entrar a corregirlo— y
            darle a un capataz el permiso de aprobar pedidos, que el informe
            define como indelegable.
          </p>
        </Blueprint>
      )}

      {/* ---------- Auditoría ---------- */}
      {!cargando && (
        <Blueprint className={estilos.bloque}>
          <div className={estilos.bloqueCabecera}>
            <h4>Auditoría</h4>
            <span className="kicker">Últimas acciones sensibles</span>
          </div>

          {auditoria.length === 0 ? (
            <p className={estilos.aviso}>Todavía no hay acciones registradas.</p>
          ) : (
            <div className="scroll-x">
              <table className="table">
                <thead>
                  <tr>
                    <th style={{ width: 120 }}>Cuándo</th>
                    <th style={{ width: 130 }}>Usuario</th>
                    <th style={{ width: 130 }}>Módulo</th>
                    <th>Acción</th>
                  </tr>
                </thead>
                <tbody>
                  {auditoria.map((a) => (
                    <tr key={a.idAuditoria}>
                      <td className={estilos.tenue}>{momento(a.fechaHora)}</td>
                      <td>{a.nombreUsuario}</td>
                      <td className={estilos.tenue}>{a.moduloAfectado}</td>
                      <td>{a.accionRealizada}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          <p className={estilos.ayuda}>
            Se registran solo las acciones sensibles —aprobar, anular, cobrar,
            cambiar permisos, dar de baja—, no cada consulta: si se registrara
            todo, encontrar lo que importa sería imposible. Ningún registro se
            puede editar ni borrar desde el sistema.
          </p>
        </Blueprint>
      )}
    </>
  );
}
