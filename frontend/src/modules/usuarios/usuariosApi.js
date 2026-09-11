import client from '../../api/client';

/** Llamadas del módulo Usuarios. */
export const usuariosApi = {
  listar: () => client.get('/usuarios').then((r) => r.data),

  crear: (datos) => client.post('/usuarios', datos).then((r) => r.data),

  /**
   * Cambio de contraseña.
   *
   * `contrasenaActual` va sola cuando uno cambia la propia. El dueño
   * reseteando la de otro la manda vacía: si la supiera, no haría falta
   * resetearla. El backend decide cuál de los dos casos es según quién pide.
   */
  cambiarContrasena: (id, datos) =>
    client.patch(`/usuarios/${id}/contrasena`, datos).then((r) => r.data),

  cambiarRol: (id, idRol) =>
    client.patch(`/usuarios/${id}/rol`, { idRol }).then((r) => r.data),

  vincularOperario: (id, idOperario) =>
    client.patch(`/usuarios/${id}/operario`, { idOperario }).then((r) => r.data),

  /** No hay DELETE: una cuenta se da de baja con motivo, no se elimina. */
  desactivar: (id, motivo) =>
    client.patch(`/usuarios/${id}/baja`, { motivo }).then((r) => r.data),

  reactivar: (id) =>
    client.patch(`/usuarios/${id}/reactivacion`).then((r) => r.data),
};

/** Llamadas del módulo Accesos. */
export const accesosApi = {
  roles: () => client.get('/roles').then((r) => r.data),

  permisos: () => client.get('/permisos').then((r) => r.data),

  /** Reemplaza la lista completa de permisos del rol. */
  definirPermisos: (idRol, idsPermisos) =>
    client.put(`/roles/${idRol}/permisos`, { idsPermisos }).then((r) => r.data),

  auditoria: (filtros = {}) =>
    client.get('/auditoria', { params: filtros }).then((r) => r.data),
};

/** "2026-09-11T19:32:27" → "11/09 19:32" */
export function momento(iso) {
  if (!iso) return '—';
  const f = new Date(iso);
  return f.toLocaleDateString('es-AR', { day: '2-digit', month: '2-digit' })
    + ' ' + f.toLocaleTimeString('es-AR', { hour: '2-digit', minute: '2-digit' });
}
