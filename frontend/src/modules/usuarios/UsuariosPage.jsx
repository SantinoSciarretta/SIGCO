import { useCallback, useEffect, useState } from 'react';
import Blueprint from '../../components/ui/Blueprint';
import Modal from '../../components/ui/Modal';
import { listarOperarios } from '../personal/personalApi';
import { useSesion } from '../sesion/useSesion';
import { accesosApi, momento, usuariosApi } from './usuariosApi';
import estilos from './Usuarios.module.css';

/**
 * Módulo 13 — Usuarios.
 *
 * Administra las cuentas de acceso. Es la pantalla desde la que el dueño da de
 * alta a un capataz, le resetea la contraseña cuando se la olvida y lo da de
 * baja cuando deja la empresa.
 *
 * Es distinta de Personal: ahí están TODOS los operarios trabajen o no con el
 * sistema; acá solo los que tienen con qué entrar. El vínculo entre las dos
 * cosas es opcional y existe para que quien confirma una recepción desde el
 * celular quede identificado como la persona de Personal.
 */
export default function UsuariosPage() {
  const { sesion } = useSesion();

  const [usuarios, setUsuarios] = useState([]);
  const [roles, setRoles] = useState([]);
  const [operarios, setOperarios] = useState([]);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState(null);
  const [recarga, setRecarga] = useState(0);

  const [altaAbierta, setAltaAbierta] = useState(false);
  const [reseteando, setReseteando] = useState(null);
  const [dandoDeBaja, setDandoDeBaja] = useState(null);

  const recargar = useCallback(() => setRecarga((n) => n + 1), []);

  useEffect(() => {
    let vigente = true;
    usuariosApi.listar()
      .then((datos) => { if (vigente) { setUsuarios(datos); setError(null); } })
      .catch((fallo) => { if (vigente) setError(fallo.mensaje); })
      .finally(() => { if (vigente) setCargando(false); });
    return () => { vigente = false; };
  }, [recarga]);

  useEffect(() => {
    let vigente = true;
    accesosApi.roles().then((d) => { if (vigente) setRoles(d); }).catch(() => {});
    // Los operarios son para el vínculo opcional con Personal. Si el usuario no
    // tiene permiso sobre Personal, simplemente no se ofrece el vínculo.
    listarOperarios().then((d) => { if (vigente) setOperarios(d); }).catch(() => {});
    return () => { vigente = false; };
  }, []);

  const accion = async (fn) => {
    try { await fn(); recargar(); setError(null); } catch (fallo) { setError(fallo.mensaje); }
  };

  return (
    <>
      <div className={estilos.cabecera}>
        <div>
          <span className="kicker kicker-acento">Módulo</span>
          <h2 className={estilos.titulo}>Usuarios</h2>
          <p className={estilos.bajada}>
            Quién puede entrar al sistema y con qué rol.
          </p>
        </div>

        <div className={estilos.herramientas}>
          <button type="button" className={estilos.botonPrimario}
                  onClick={() => setAltaAbierta(true)}>
            Nueva cuenta
          </button>
        </div>
      </div>

      <p className={estilos.aclaracion}>
        Una cuenta no se elimina: se da de baja dejando el motivo. La auditoría
        guarda quién hizo cada cosa, y borrar el usuario dejaría esos registros
        apuntando a nadie.
      </p>

      {error && <p className={estilos.errorGeneral}>{error}</p>}
      {cargando && <p className={estilos.aviso}>Consultando…</p>}

      {!cargando && (
        <Blueprint className={estilos.bloque}>
          <div className="scroll-x">
            <table className="table">
              <thead>
                <tr>
                  <th>Usuario</th>
                  <th>Rol</th>
                  <th>Vinculado a</th>
                  <th>Último ingreso</th>
                  <th>Estado</th>
                  <th style={{ textAlign: 'right' }}>Acciones</th>
                </tr>
              </thead>
              <tbody>
                {usuarios.map((u) => {
                  const esYo = u.idUsuario === sesion?.idUsuario;
                  return (
                    <tr key={u.idUsuario}
                        className={u.estado === 'Inactivo' ? estilos.filaInactiva : undefined}>
                      <td>
                        <div className={estilos.nombreUsuario}>
                          {u.nombreUsuario}
                          {esYo && <span className={estilos.marcaYo}>vos</span>}
                        </div>
                      </td>
                      <td>{u.nombreRol}</td>
                      <td className={estilos.tenue}>{u.nombreOperario ?? '—'}</td>
                      <td className={estilos.tenue}>{momento(u.ultimaFechaAcceso)}</td>
                      <td>
                        <span className={u.estado === 'Activo'
                          ? estilos.activo : estilos.inactivo}>
                          {u.estado}
                        </span>
                        {u.motivoBaja && (
                          <div className={estilos.motivo}>{u.motivoBaja}</div>
                        )}
                      </td>
                      <td className={estilos.acciones}>
                        <select className={estilos.selectChico} value={u.idRol}
                                onChange={(e) => accion(() =>
                                  usuariosApi.cambiarRol(u.idUsuario, Number(e.target.value)))}
                                aria-label={`Rol de ${u.nombreUsuario}`}>
                          {roles.map((r) => (
                            <option key={r.idRol} value={r.idRol}>{r.nombreRol}</option>
                          ))}
                        </select>

                        <button type="button" className={estilos.botonSecundario}
                                onClick={() => setReseteando(u)}>
                          Contraseña
                        </button>

                        {u.estado === 'Activo' ? (
                          <button type="button" className={estilos.botonPeligro}
                                  onClick={() => setDandoDeBaja(u)}
                                  // Nadie se da de baja a sí mismo: quedaría
                                  // afuera del sistema en el acto. El backend
                                  // lo rechaza igual; acá solo se evita ofrecerlo.
                                  disabled={esYo}
                                  title={esYo ? 'No podés dar de baja tu propia cuenta' : undefined}>
                            Dar de baja
                          </button>
                        ) : (
                          <button type="button" className={estilos.botonSecundario}
                                  onClick={() => accion(() => usuariosApi.reactivar(u.idUsuario))}>
                            Reactivar
                          </button>
                        )}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </Blueprint>
      )}

      {altaAbierta && (
        <NuevaCuentaModal
          roles={roles}
          operarios={operarios.filter((o) => o.estado === 'Activo')}
          onCerrar={() => setAltaAbierta(false)}
          onCreada={() => { setAltaAbierta(false); recargar(); }}
        />
      )}

      {reseteando && (
        <ContrasenaModal
          usuario={reseteando}
          esLaPropia={reseteando.idUsuario === sesion?.idUsuario}
          onCerrar={() => setReseteando(null)}
          onListo={() => { setReseteando(null); recargar(); }}
        />
      )}

      {dandoDeBaja && (
        <BajaModal
          usuario={dandoDeBaja}
          onCerrar={() => setDandoDeBaja(null)}
          onListo={() => { setDandoDeBaja(null); recargar(); }}
        />
      )}
    </>
  );
}

/* ========================================================================== */

function NuevaCuentaModal({ roles, operarios, onCerrar, onCreada }) {
  const [datos, setDatos] = useState({
    nombreUsuario: '', contrasena: '', idRol: '', idOperario: '',
  });
  const [error, setError] = useState(null);
  const [guardando, setGuardando] = useState(false);

  const cambiar = (campo) => (e) => setDatos({ ...datos, [campo]: e.target.value });

  const enviar = async (evento) => {
    evento.preventDefault();
    setGuardando(true);
    setError(null);
    try {
      await usuariosApi.crear({
        nombreUsuario: datos.nombreUsuario,
        contrasena: datos.contrasena,
        idRol: Number(datos.idRol),
        idOperario: datos.idOperario ? Number(datos.idOperario) : null,
      });
      onCreada();
    } catch (fallo) {
      setError(fallo.mensaje);
    } finally {
      setGuardando(false);
    }
  };

  return (
    <Modal abierto onCerrar={onCerrar} titulo="Nueva cuenta de acceso">
      <form onSubmit={enviar}>
        {error && <p className={estilos.errorGeneral}>{error}</p>}

        <div className={estilos.campo}>
          <label className={estilos.etiqueta} htmlFor="nombreUsuario">
            Usuario <span className={estilos.obligatorio}>*</span>
          </label>
          <input id="nombreUsuario" className={estilos.control} maxLength={50}
                 value={datos.nombreUsuario} onChange={cambiar('nombreUsuario')}
                 placeholder="jorge" required autoFocus />
          <p className={estilos.ayuda}>
            Sin espacios ni acentos: es lo que se escribe para entrar, y un
            espacio invisible vuelve imposible iniciar sesión sin que se entienda
            por qué.
          </p>
        </div>

        <div className={estilos.campo}>
          <label className={estilos.etiqueta} htmlFor="contrasena">
            Contraseña inicial <span className={estilos.obligatorio}>*</span>
          </label>
          <input id="contrasena" type="password" className={estilos.control}
                 minLength={8} value={datos.contrasena} onChange={cambiar('contrasena')}
                 required />
          <p className={estilos.ayuda}>
            Mínimo 8 caracteres. Se guarda cifrada: ni vos ni nadie con acceso a
            la base puede volver a leerla, solo restablecerla.
          </p>
        </div>

        <div className={estilos.campo}>
          <label className={estilos.etiqueta} htmlFor="idRol">
            Rol <span className={estilos.obligatorio}>*</span>
          </label>
          <select id="idRol" className={estilos.control} value={datos.idRol}
                  onChange={cambiar('idRol')} required>
            <option value="">Elegir rol…</option>
            {roles.map((r) => (
              <option key={r.idRol} value={r.idRol}>{r.nombreRol}</option>
            ))}
          </select>
          <p className={estilos.ayuda}>
            El rol define qué módulos ve. Se puede cambiar después desde el
            listado.
          </p>
        </div>

        <div className={estilos.campo}>
          <label className={estilos.etiqueta} htmlFor="idOperario">
            Vincular con un operario
          </label>
          <select id="idOperario" className={estilos.control} value={datos.idOperario}
                  onChange={cambiar('idOperario')}>
            <option value="">Sin vincular</option>
            {operarios.map((o) => (
              <option key={o.idOperario} value={o.idOperario}>{o.nombreApellido}</option>
            ))}
          </select>
          <p className={estilos.ayuda}>
            Opcional. Sirve para que quien confirma una recepción de materiales
            desde el celular quede identificado como esa persona de Personal.
          </p>
        </div>

        <div className={estilos.accionesFormulario}>
          <button type="button" className={estilos.botonSecundario} onClick={onCerrar}>
            Cancelar
          </button>
          <button type="submit" className={estilos.botonPrimario} disabled={guardando}>
            {guardando ? 'Creando…' : 'Crear cuenta'}
          </button>
        </div>
      </form>
    </Modal>
  );
}

/* ========================================================================== */

/**
 * Cambio de contraseña.
 *
 * El formulario cambia según el caso, y la diferencia importa: cambiar la
 * propia exige escribir la actual, para que alguien que encuentre la sesión
 * abierta no pueda apropiarse de la cuenta. El dueño reseteando la de otro no
 * la necesita, porque no la sabe.
 */
function ContrasenaModal({ usuario, esLaPropia, onCerrar, onListo }) {
  const [actual, setActual] = useState('');
  const [nueva, setNueva] = useState('');
  const [error, setError] = useState(null);
  const [guardando, setGuardando] = useState(false);

  const enviar = async (evento) => {
    evento.preventDefault();
    setGuardando(true);
    setError(null);
    try {
      await usuariosApi.cambiarContrasena(usuario.idUsuario, {
        contrasenaActual: esLaPropia ? actual : null,
        contrasenaNueva: nueva,
      });
      onListo();
    } catch (fallo) {
      setError(fallo.mensaje);
    } finally {
      setGuardando(false);
    }
  };

  return (
    <Modal abierto onCerrar={onCerrar}
           titulo={esLaPropia ? 'Cambiar mi contraseña'
             : `Restablecer la contraseña de ${usuario.nombreUsuario}`}>
      <form onSubmit={enviar}>
        {error && <p className={estilos.errorGeneral}>{error}</p>}

        {esLaPropia && (
          <div className={estilos.campo}>
            <label className={estilos.etiqueta} htmlFor="actual">
              Contraseña actual <span className={estilos.obligatorio}>*</span>
            </label>
            <input id="actual" type="password" className={estilos.control}
                   value={actual} onChange={(e) => setActual(e.target.value)}
                   required autoFocus />
          </div>
        )}

        {!esLaPropia && (
          <p className={estilos.ayuda}>
            Le vas a tener que pasar la contraseña nueva por otro medio: el
            sistema no la muestra después de guardarla.
          </p>
        )}

        <div className={estilos.campo}>
          <label className={estilos.etiqueta} htmlFor="nueva">
            Contraseña nueva <span className={estilos.obligatorio}>*</span>
          </label>
          <input id="nueva" type="password" className={estilos.control} minLength={8}
                 value={nueva} onChange={(e) => setNueva(e.target.value)}
                 required autoFocus={!esLaPropia} />
          <p className={estilos.ayuda}>Mínimo 8 caracteres.</p>
        </div>

        <div className={estilos.accionesFormulario}>
          <button type="button" className={estilos.botonSecundario} onClick={onCerrar}>
            Cancelar
          </button>
          <button type="submit" className={estilos.botonPrimario} disabled={guardando}>
            {guardando ? 'Guardando…' : 'Guardar'}
          </button>
        </div>
      </form>
    </Modal>
  );
}

/* ========================================================================== */

function BajaModal({ usuario, onCerrar, onListo }) {
  const [motivo, setMotivo] = useState('');
  const [error, setError] = useState(null);
  const [guardando, setGuardando] = useState(false);

  const enviar = async (evento) => {
    evento.preventDefault();
    setGuardando(true);
    setError(null);
    try {
      await usuariosApi.desactivar(usuario.idUsuario, motivo);
      onListo();
    } catch (fallo) {
      setError(fallo.mensaje);
    } finally {
      setGuardando(false);
    }
  };

  return (
    <Modal abierto onCerrar={onCerrar} titulo={`Dar de baja a ${usuario.nombreUsuario}`}>
      <form onSubmit={enviar}>
        {error && <p className={estilos.errorGeneral}>{error}</p>}

        <p className={estilos.ayuda}>
          La cuenta deja de poder entrar en el acto, incluso si tiene una sesión
          abierta. Su historial y su auditoría se conservan, y se puede
          reactivar más adelante.
        </p>

        <div className={estilos.campo}>
          <label className={estilos.etiqueta} htmlFor="motivo">
            Motivo <span className={estilos.obligatorio}>*</span>
          </label>
          <input id="motivo" className={estilos.control} maxLength={200}
                 value={motivo} onChange={(e) => setMotivo(e.target.value)}
                 placeholder="Dejó la empresa" required autoFocus />
        </div>

        <div className={estilos.accionesFormulario}>
          <button type="button" className={estilos.botonSecundario} onClick={onCerrar}>
            Cancelar
          </button>
          <button type="submit" className={estilos.botonPeligro} disabled={guardando}>
            {guardando ? 'Dando de baja…' : 'Dar de baja'}
          </button>
        </div>
      </form>
    </Modal>
  );
}
