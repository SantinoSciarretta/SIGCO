# Módulo 08 — Compras

**Fecha de desarrollo:** 05/09/2026
**Depende de:** Obras, Materiales, Proveedores
**Alimenta a:** Gastos (genera el gasto al confirmar la recepción)
**Referencia en el informe:** sección "Módulo 6 – Compras"

---

## 1. Qué resuelve

Hoy el circuito de materiales vive en WhatsApp. El capataz avisa que falta algo,
el dueño contesta, alguien llama al corralón, el camión llega y el remito en
papel se pierde. No queda registro de qué se pidió, a quién, cuándo llegó, ni si
llegó completo.

El módulo formaliza los tres momentos del circuito real y deja registro de cada
uno:

1. **Alguien en la obra arma el pedido** — elige materiales del catálogo e
   indica cantidades. Queda *Pendiente de Aprobación*.
2. **El dueño aprueba y elige el proveedor** — según la zona de la obra, y
   confirma los precios. Pasa a *Enviado al Proveedor*. Es una decisión **no
   delegable**.
3. **Quien recibe confirma en obra** — con foto del remito y, si hubo faltantes,
   una nota. Queda *Recibido Completo* o *Recibido con Diferencias*.

---

## 2. Correcciones al modelo (migración `V9`)

Al contrastar el circuito del informe contra las tablas que creó `V8`
aparecieron tres faltantes que habrían hecho el módulo inviable.

### El proveedor no puede ser obligatorio al crear

`V8` había dejado `pedido.id_proveedor` como `NOT NULL`. Pero el informe dice:

> *"El dueño revisa el pedido, lo aprueba y selecciona el proveedor al que se lo
> va a enviar, tomándolo del registro de Proveedores según la zona de la obra."*

El capataz que arma el pedido **no decide a quién comprarle**. Con `NOT NULL` el
pedido no se podía ni crear.

Se hizo la columna nullable, pero con un `CHECK` que la exige apenas el pedido
sale de *Pendiente de Aprobación*: no se puede enviar ni recibir algo sin saber
a quién se le compró.

```sql
CHECK (estado = 'Pendiente de Aprobación'
       OR estado = 'Anulado'
       OR id_proveedor IS NOT NULL)
```

### No había de dónde sacar el monto del gasto

El informe promete que al confirmar la recepción el sistema genera el gasto solo,
y que *"toma como monto el valor cargado en el pedido"*. Pero ni `pedido` ni
`pedido_material` tenían precio: ese valor no existía en ninguna parte, y la
integración Compras → Gastos no podía implementarse.

Se agregó `pedido_material.precio_unitario`, **nullable**. Es nulo al crear por
el mismo motivo que el proveedor: quien pide en la obra sabe que faltan 20 bolsas
de cemento, no a cuánto están. Se completa al aprobar.

### Faltaba el motivo de anulación

El informe pide anular *"dejando un motivo"*. El Diccionario no previó la
columna. Se agregó `pedido.motivo_anulacion` con su `CHECK`, mismo criterio que
`obra.motivo_cancelacion` y `gasto.motivo_anulacion`.

---

## 3. Modelo de datos

| Tabla | PK | FK | Descripción |
| --- | --- | --- | --- |
| `pedido` | `id_pedido` | `id_obra`, `id_proveedor`, `id_usuario_solicita`, `id_usuario_recibe` | Cabecera del pedido y su estado |
| `pedido_material` | `(id_pedido, id_material)` | ambas | Qué material, cuánto y a qué precio |

### La clave primaria compuesta no es un detalle de forma

`pedido_material` tiene PK `(id_pedido, id_material)` en lugar de un id propio.
Eso impide **por construcción** que el mismo material aparezca dos veces en un
pedido, sin depender de una validación que alguien pueda olvidarse de escribir.

El servicio igual controla el duplicado, pero solo para devolver un mensaje útil
(*"sumá las cantidades en una sola línea"*) en vez de un error de restricción de
PostgreSQL.

---

## 4. Decisiones de circuito

### Aprobar y enviar son la misma transición

Los estados van de *Pendiente de Aprobación* directo a *Enviado al Proveedor*.
No hay un estado "Aprobado" intermedio, porque el circuito real no lo tiene: el
informe describe que el dueño *"lo aprueba y selecciona el proveedor al que se lo
va a enviar"*. Son tres decisiones que toma juntas —aprobar, elegir corralón,
confirmar precios— y separarlas inventaría un estado que nadie usa.

### El estado de recepción se deriva, no se elige

`Pedido.recibir()` decide el estado final a partir de si hay nota de diferencia:

```java
this.estado = (notaDiferencia == null || notaDiferencia.isBlank())
        ? ESTADO_RECIBIDO_COMPLETO
        : ESTADO_RECIBIDO_CON_DIFERENCIAS;
```

Es un dato, no una opción del usuario. Así **nadie puede marcar "completo" y a la
vez anotar que faltaron cuatro bolsas**, que es exactamente el tipo de
inconsistencia que ensucia un historial.

### Un pedido recibido no se anula

El material llegó a la obra: anularlo borraría del historial algo que
efectivamente pasó. El mensaje deriva al usuario al mecanismo correcto:

> *"No se anula un pedido ya recibido: el material llegó a la obra. Si hubo un
> problema, registralo como observación del proveedor."*

### Los precios se proponen desde la última cotización

Al elegir proveedor en la pantalla de aprobación, el sistema precarga el precio
de cada material desde la última cotización de ese corralón
(`CotizacionRepository.ultimaDe`). El dueño puede corregirlos: la cotización es
una referencia, no el precio final.

Los materiales que ese proveedor nunca cotizó **quedan vacíos**, no en cero. Un
cero se puede confirmar sin mirar; un campo vacío obliga a completarlo.

Esa consulta ordena por `id` y no por fecha, por el mismo motivo que el
comparador de Proveedores: la fecha la pone el sistema al insertar, así que el
orden es el mismo, pero el id es único y la fecha no.

---

## 5. Endpoints

| Método | Ruta | Qué hace |
| --- | --- | --- |
| GET | `/api/pedidos` | Listado con filtros `obra`, `proveedor`, `estado` |
| GET | `/api/pedidos/pendientes` | Panel de aprobación del dueño |
| GET | `/api/pedidos/{id}` | Detalle con sus materiales |
| GET | `/api/pedidos/{id}/precios-sugeridos?proveedor=` | Última cotización por material |
| POST | `/api/pedidos` | Alta. Nace pendiente, sin proveedor |
| PATCH | `/api/pedidos/{id}/aprobacion` | Aprobar, elegir proveedor y precios |
| PATCH | `/api/pedidos/{id}/recepcion` | Confirmar recepción en obra |
| PATCH | `/api/pedidos/{id}/anulacion` | Anular con motivo |

**No hay DELETE.** Un pedido no se elimina, se anula. El historial de lo que se
le pidió a cada proveedor es información que hoy la empresa pierde.

Las tres transiciones son `PATCH` sobre un sub-recurso y no un `PUT` del pedido
completo, porque cada una es una acción distinta con reglas y responsables
distintos, no una edición genérica.

---

## 6. Validaciones

| Regla del informe | Dónde se valida |
| --- | --- |
| No hay pedido sin al menos un material | `@NotEmpty` en el DTO |
| Solo el dueño aprueba y elige proveedor | Pendiente: se activa con Accesos |
| No se envía sin aprobar | El estado solo pasa por `aprobar()` |
| No se recibe lo que no se envió | `PedidoService.recibir()` |
| Foto del remito obligatoria | `@NotNull` en el DTO |
| Nota obligatoria si hay diferencias | `CHECK` en la base + la deriva la entidad |
| No se elimina un pedido, se anula con motivo | No existe DELETE + `CHECK` |

**Reglas agregadas** (no están en el informe, pero se desprenden):

- Una obra **cancelada o finalizada** no admite pedidos nuevos.
- Un **material inactivo** no se puede pedir. Los pedidos viejos que lo
  referencian no se tocan: son historia.
- Un **proveedor inactivo** no recibe pedidos.
- Al aprobar hay que confirmar el precio de **todos** los materiales, no de
  algunos: el total es lo que después se convierte en gasto, y un total al que le
  falta una línea no sirve para comparar contra el presupuesto.

---

## 7. Pantallas

| Pantalla | Dónde |
| --- | --- |
| Listado de pedidos | `/pedidos` |
| Nuevo pedido | modal |
| Aprobar y enviar | modal, con precios sugeridos |
| Confirmar recepción | modal, pensado para el celular |
| Anular | modal |

**Las acciones dependen del estado, no al revés.** No se ofrece "Aprobar" en un
pedido ya enviado ni "Confirmar recepción" en uno que nadie aprobó. El backend
rechaza esos casos igual; en pantalla se ocultan para no ofrecer algo que va a
fallar.

La fila de un pedido pendiente se destaca en ámbar: es la que frena el circuito
esperando al dueño.

En la confirmación de recepción se **anticipa el estado resultante** mientras se
escribe: si el campo de nota tiene texto, avisa que el pedido va a quedar con
diferencias. El usuario ve la consecuencia antes de confirmar.

---

## 8. Tests

**16 tests** de este módulo, sobre 156 del sistema.

| Grupo | Casos |
| --- | --- |
| Alta del pedido | 4 |
| Aprobación | 4 |
| Recepción en obra | 5 |
| Anulación | 2 |
| No encontrado | 1 |

### Verificación manual

| Caso | Resultado |
| --- | --- |
| Crear pedido | `201`, estado Pendiente, **sin proveedor** |
| Recibir sin haber enviado | `409` |
| Precios sugeridos | `200`, propuso $21.000 desde una cotización real |
| Aprobar con un precio faltante | `409` |
| Aprobar completo | `200`, pasa a Enviado, total $1.220.000 |
| Recibir con nota | `200`, **Recibido con Diferencias** |
| Recibir sin nota | `200`, **Recibido Completo** |
| Anular ya recibido | `409` |
| Anular pendiente | `200`, con motivo |

---

## 9. Pendientes

| Pendiente | Se resuelve en |
| --- | --- |
| `id_usuario_solicita` / `id_usuario_recibe` reales | Accesos |
| Restringir `aprobar()` al rol dueño | Accesos |
| Restringir `recibir()` a quien tenga la obra asignada | Accesos |
| Carga real de la foto del remito a Supabase Storage | Integración de almacenamiento |
| Historial de pedidos en la ficha del proveedor | Pendiente de Proveedores, ahora desbloqueado |
| Elegir el pedido al registrar una observación del proveedor | Ídem |
