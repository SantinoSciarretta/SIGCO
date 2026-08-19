# Módulo 07 — Proveedores

**Fecha de desarrollo:** 19/08/2026
**Depende de los módulos:** Materiales (para las cotizaciones)
**Referencia en el informe:** `docs/informe-sigco.md`, sección "Módulo 8 – Proveedores"

---

## 1. Qué resuelve

Hoy las cotizaciones se piden por WhatsApp y se pierden en el historial del
chat. Cuando el dueño tiene que aprobar un pedido, decide de memoria: se acuerda
de que un corralón estaba más barato, o de que otro demoró la última vez. Si
alguien le pregunta cuánto salía el cemento hace tres meses, no hay respuesta.

El módulo registra las tres cosas que hacen falta para elegir a quién pedirle:

1. **Quién llega a cada zona** — un corralón que no entrega en la obra no sirve
   por más barato que sea.
2. **A cuánto cotizó cada uno cada material**, con la fecha.
3. **Cómo se comportó** en los pedidos anteriores.

---

## 2. Modelo de datos

| Tabla | PK | FK | Descripción |
| --- | --- | --- | --- |
| `proveedor` | `id_proveedor` | — | El corralón, con su zona |
| `cotizacion` | `id_cotizacion` | `id_proveedor`, `id_material` | Precio informado, con fecha |
| `observacion_proveedor` | `id_observacion` | `id_proveedor`, *(`id_pedido`)* | Comportamiento |

**Migración Flyway:** `V6__proveedor.sql`

### La unicidad es por nombre **y** zona

```sql
CREATE UNIQUE INDEX ux_proveedor_nombre_zona
    ON proveedor (LOWER(nombre_proveedor), LOWER(zona_cobertura));
```

El informe pide no permitir dos proveedores con el mismo nombre **y** la misma
zona. La combinación importa: una cadena de corralones puede tener sucursales
homónimas en zonas distintas, y a efectos de a quién pedirle son proveedores
diferentes. Verificado: "Corralón San Martín" existe en Zona Norte y en Zona
Oeste sin conflicto, pero un segundo "Corralón San Martín" en Zona Norte se
rechaza con `409`.

### Una clave foránea que todavía no se puede crear

`observacion_proveedor.id_pedido` referencia a la tabla `pedido`, que **no
existe todavía**: Compras va después en el orden de desarrollo.

La columna se creó sin la restricción de clave foránea, con un `TODO` en la
migración y otro en la entidad. La restricción se agrega en la migración de
Compras. En Java, el campo es un `Long` suelto en lugar de un `@ManyToOne`, y
se reemplaza cuando exista la entidad.

Es la primera vez en el proyecto que aparece esta situación, y la alternativa
—adelantar la tabla `pedido`— habría significado desarrollar medio módulo
Compras antes de tiempo.

---

## 3. El comparador de cotizaciones

Es la pieza central del módulo. Responde la pregunta concreta del dueño al
aprobar un pedido: *"necesito cemento, ¿quién me lo hace más barato hoy?"*.

### El problema técnico: "el último de cada grupo"

La tabla guarda **todas** las cotizaciones históricas. Para comparar hace falta
quedarse con **una sola por proveedor**: la más reciente.

```sql
AND c.idCotizacion = (
    SELECT MAX(c2.idCotizacion) FROM Cotizacion c2
    WHERE c2.proveedor = c.proveedor AND c2.material = c.material)
```

### Por qué `MAX(id)` y no `MAX(fecha)`

La fecha la pone el sistema al insertar, así que el orden de los identificadores
y el de las fechas es **siempre el mismo**. Pero el identificador es **único** y
la fecha no: si dos cotizaciones cayeran en el mismo instante, comparar por
fecha devolvería las dos y el mismo proveedor aparecería repetido en el
comparador.

### Verificado con un caso construido a propósito

Cargué cuatro cotizaciones de cemento: tres proveedores distintos, y el primero
cotizando **dos veces** (18.500 y después 21.000).

```
COMPARADOR
  16.200,00  Corralón San Martín (Zona Oeste)
  17.800,00  Corralón Sur (Zona Sur)
  21.000,00  Corralón San Martín (Zona Norte)   ← su cotización nueva, no la vieja
```

Tres proveedores, tres filas, ordenadas por precio. Y el historial del primero
conserva **las dos** cotizaciones:

```
21.000,00  Cemento CP40  2026-08-19T14:55:09.28
18.500,00  Cemento CP40  2026-08-19T14:55:09.20
```

### Una cotización no se corrige: se agrega otra

`Cotizacion` no tiene ningún método de modificación, y la API no expone `PUT` ni
`DELETE` para ella. Si el proveedor informa un precio nuevo, se registra una
cotización nueva.

Con la variación de precios actual, esa historia vale por sí misma: permite ver
cuánto subió un material en el año, que es información que hoy la empresa no
tiene de ninguna forma.

---

## 4. La antigüedad de la cotización

El informe pide poder distinguir las cotizaciones recientes de las que pueden
estar desactualizadas. La pantalla lo marca en tres niveles:

| Antigüedad | Marca |
| --- | --- |
| menos de 30 días | gris, normal |
| 30 a 89 días | ámbar |
| 90 días o más | rojo, con un aviso al pie |

**Por qué importa tanto:** con inflación, un precio de hace ocho meses no es un
precio, es un dato histórico. Sin esa marca, el comparador recomendaría al más
barato solo porque **nadie le pidió cotización nueva** — exactamente el error que
se busca evitar.

---

## 5. Endpoints

| Método | Ruta | Qué hace |
| --- | --- | --- |
| GET | `/api/proveedores` | Listado con buscador y filtros por zona y estado |
| GET | `/api/proveedores/zonas` | Zonas ya cargadas, para el filtro |
| GET | `/api/proveedores/{id}` | Un proveedor |
| POST | `/api/proveedores` | Alta |
| PUT | `/api/proveedores/{id}` | Editar |
| PATCH | `/api/proveedores/{id}/estado` | Activar / desactivar |
| POST | `/api/proveedores/{id}/cotizaciones` | Registrar un precio |
| GET | `/api/proveedores/{id}/cotizaciones` | Historial del proveedor |
| POST | `/api/proveedores/{id}/observaciones` | Registrar comportamiento |
| GET | `/api/proveedores/{id}/observaciones` | Observaciones del proveedor |
| **GET** | **`/api/materiales/{id}/cotizaciones`** | **Comparador** |

### El comparador cuelga del material, no del proveedor

Porque así es como se lo consulta: se parte de *"necesito cemento"* y se
pregunta quién lo ofrece, no al revés. Va en un controlador propio
(`ComparadorController`) para que la ruta quede donde corresponde sin mezclar
dos jerarquías de recursos en la misma clase.

Devuelve `404` si el material no existe, para no confundir ese caso con "todavía
nadie lo cotizó", que devuelve lista vacía. Son problemas distintos y el usuario
necesita saber cuál tiene.

### Sin DELETE en ninguno de los tres recursos, por motivos distintos

| Recurso | Por qué no se borra |
| --- | --- |
| `proveedor` | Borrarlo eliminaría su historial de precios y comportamiento |
| `cotizacion` | Es el registro de un precio informado en una fecha |
| `observacion` | Es el registro de un hecho ocurrido en un pedido |

---

## 6. Validaciones

| Regla del informe | Dónde se valida |
| --- | --- |
| No dos proveedores con mismo nombre y zona | Servicio + índice único sobre `LOWER` |
| Zona obligatoria | DTO + `NOT NULL` |
| No eliminar un proveedor con pedidos o cotizaciones | No existe DELETE |
| La cotización queda con la fecha en que se cargó | La pone la entidad |
| Las observaciones se asocian a fecha y pedido | Ídem |

**Regla agregada:** no se le registran cotizaciones a un proveedor inactivo. Un
proveedor desactivado no aparece en el comparador, así que una cotización nueva
suya no se usaría para nada.

---

## 7. Pantallas

| Pantalla | Dónde |
| --- | --- |
| Listado de proveedores | `/proveedores` |
| Ficha con cotizaciones y comportamiento | modal, dos pestañas |
| Comparador de precios | modal |
| Alta / edición | modal |

El listado muestra la **cantidad de observaciones** de cada proveedor, resaltada
cuando hay alguna: es señal de comportamiento y conviene que salte a la vista al
decidir a quién pedirle.

Tanto la cotización como la observación se registran con un formulario **de una
sola línea** dentro de la ficha, sin abrir otro modal encima. Son acciones de
dos campos que se hacen seguido; obligarlas a pasar por un modal aparte sería
fricción sin sentido.

**Pendiente:** el historial de pedidos del proveedor, que pide el informe en la
ficha, llega con Compras.

---

## 8. Tests

**112 tests, 0 fallos.** De ellos, 14 son de este módulo:

| Grupo | Casos |
| --- | --- |
| Alta y duplicados | 4 |
| Cotizaciones | 4 |
| Comparador | 3 |
| Observaciones y estado | 3 |

Uno de los tests verifica explícitamente que registrar dos cotizaciones del
mismo material produce **dos altas y ningún borrado**: es la regla de que el
historial de precios no se pisa.

### Verificación manual

| Caso | Resultado |
| --- | --- |
| "corralón san martín" / "zona norte" con el mismo ya cargado | `409` |
| "Corralón San Martín" en Zona Oeste | `201` — otra zona, otro proveedor |
| Comparador de cemento con 4 cotizaciones de 3 proveedores | 3 filas, la última de cada uno, por precio |
| Historial del proveedor que re-cotizó | Conserva las dos cotizaciones |
| `GET /api/materiales/999/cotizaciones` | `404`, no lista vacía |

---

## 9. Pendientes

| Pendiente | Se resuelve en |
| --- | --- |
| Clave foránea de `observacion_proveedor.id_pedido` hacia `pedido` | Compras |
| Reemplazar el `Long idPedido` por un `@ManyToOne` | Compras |
| Elegir el pedido al registrar una observación | Compras |
| Historial de pedidos en la ficha del proveedor | Compras |
| Cargar los proveedores reales de Granica | Con Ricardo |
| Restringir el módulo por rol (el capataz solo consulta) | Accesos |
