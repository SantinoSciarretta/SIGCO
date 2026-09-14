import client from '../../api/client';

/**
 * Llamadas al backend del modulo Obras.
 */

/** GET /api/obras con los filtros del listado (todos opcionales). */
export async function listarObras({ cliente, tipoObra, estado, desde, hasta, busqueda } = {}) {
  const respuesta = await client.get('/obras', {
    params: {
      cliente: cliente || undefined,
      tipoObra: tipoObra || undefined,
      estado: estado || undefined,
      desde: desde || undefined,
      hasta: hasta || undefined,
      busqueda: busqueda || undefined,
    },
  });
  return respuesta.data;
}

/** GET /api/obras/{id} */
/**
 * Las obras del usuario que está pidiendo.
 *
 * Para un Capataz de Obra son las que tiene asignadas; para el dueño y el
 * capataz general, las que están en ejecución. Es lo que usan las pantallas de
 * obra del celular para saber sobre qué obra están trabajando.
 */
export async function misObras() {
  const { data } = await client.get('/obras/mias');
  return data;
}

export async function obtenerObra(id) {
  const respuesta = await client.get(`/obras/${id}`);
  return respuesta.data;
}

/** POST /api/obras — la obra nace "En presupuestación". */
export async function crearObra(datos) {
  const respuesta = await client.post('/obras', datos);
  return respuesta.data;
}

/** PUT /api/obras/{id} — datos maestros. No admite cambiar cliente ni tipo de obra. */
export async function actualizarObra(id, datos) {
  const respuesta = await client.put(`/obras/${id}`, datos);
  return respuesta.data;
}

/**
 * PATCH /api/obras/{id}/estado — avanza el ciclo de vida.
 *
 * No existe una operacion de baja: el informe establece que una obra no se
 * elimina, solo se cancela, para no perder la trazabilidad de sus presupuestos,
 * gastos y cobros.
 */
export async function cambiarEstadoObra(id, cambio) {
  const respuesta = await client.patch(`/obras/${id}/estado`, cambio);
  return respuesta.data;
}

/** Valores validos, los mismos que valida el backend. */
export const TIPOS_INMUEBLE = ['Casa', 'Departamento', 'Local'];
export const TIPOS_OBRA = ['Construcción', 'Reforma'];
export const ESTADOS = ['En presupuestación', 'En ejecución', 'Finalizada', 'Cancelada'];

/**
 * Transiciones validas desde cada estado, replicadas del backend para que la
 * pantalla ofrezca solo lo que se puede hacer. La regla que manda sigue siendo
 * la del servidor: esto evita que el usuario intente algo que va a fallar.
 */
export function estadosPosiblesDesde(estado) {
  switch (estado) {
    case 'En presupuestación':
      return ['En ejecución', 'Cancelada'];
    case 'En ejecución':
      return ['Finalizada', 'Cancelada'];
    default:
      // Finalizada y Cancelada son terminales.
      return [];
  }
}
