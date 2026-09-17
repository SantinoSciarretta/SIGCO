# Módulo 14 — Dashboard (Tablero)

**Fecha de desarrollo:** 11/09/2026
**Depende de:** Obras, Presupuestación, Gastos, Compras, Seguimiento, Cobros
**Referencia en el informe:** sección "Módulo 12 – Dashboard"

---

## 1. Qué resuelve

Hoy el dueño tiene que ir a buscar obra por obra, en planillas distintas, tres
cosas que en realidad mira juntas: **cuánto se gastó contra lo presupuestado,
cuánto hay por cobrar y cómo viene el avance**. Y hay una cuarta que no está en
ninguna planilla: qué está esperando una decisión suya.

El Tablero las reúne en una pantalla.

---

## 2. La decisión de diseño del módulo

Es la más importante y la que hay que poder defender:

> **El Tablero no calcula nada.**

Ni un porcentaje, ni un saldo, ni un semáforo. Todo lo pide a los módulos que
son dueños de cada número:

| Qué | Quién lo calcula |
| --- | --- |
| Semáforo y ganancia estimada | Gastos |
| Avance físico y desfasaje | Seguimiento |
| Saldo y próximo vencimiento | Cobros |
| Pedidos por aprobar | Compras |
| Presupuestos sin respuesta | Presupuestación |

Sería más rápido resolverlo con unas consultas SQL agregadas directamente contra
las tablas. **No se hizo, a propósito:** si el Tablero recalculara el semáforo
por su cuenta, el día que cambie el umbral habría que acordarse de cambiarlo en
dos lugares, y hasta que alguien lo note la ficha de la obra y el tablero
mostrarían colores distintos para la misma obra. Ese es exactamente el problema
que el sistema viene a resolver.

### El costo, que también hay que poder defender

El tablero hace unas pocas consultas **por obra en ejecución**, en lugar de un
puñado de consultas totales. Con la cantidad de obras simultáneas que maneja la
empresa —menos de diez— es irrelevante. Si algún día fueran cientos, habría que
agregar consultas agregadas; pero recién entonces, y no antes por las dudas.

Por eso este módulo va anteúltimo: no puede existir hasta que existan los once
módulos que producen la información que consolida.

---

## 3. Una obra sin presupuesto no puede tirar abajo el tablero

`GastoService.estadoFinanciero()` lanza una excepción si la obra no tiene
definitivo aprobado. El Tablero recorre **todas** las obras en ejecución, y
alguna puede no tenerlo: con ese método, esa sola obra hacía fallar la pantalla
entera.

Se agregó `GastoService.resumenOVacio()`, que devuelve ceros y semáforo
"Sin presupuesto" en lugar de lanzar.

> **No se resolvió atrapando la excepción**, y el motivo ya nos costó un error
> antes en este proyecto: al lanzar dentro de un método `@Transactional`, Spring
> marca la transacción como *rollback-only*, y atraparla afuera no deshace esa
> marca — la transacción del llamador explota al confirmar con
> `UnexpectedRollbackException`. **Una excepción no sirve como control de flujo
> cruzando un límite transaccional.**

Hay un test que cubre exactamente este caso.

---

## 4. "Esperando una decisión tuya"

Es la parte del tablero que reemplaza la memoria del dueño: hoy un presupuesto
enviado hace veinte días o un pedido esperando aprobación no aparecen en ningún
lado hasta que alguien pregunta.

Entran **solo cosas que únicamente el dueño puede destrabar**:

| Tipo | Cuándo es urgente |
| --- | --- |
| Presupuesto enviado sin respuesta | 15 días o más |
| Pedido esperando aprobación | 2 días o más (frena la compra) |
| Pedido enviado sin confirmar recepción | nunca urgente, solo informativo |
| Cuota vencida | siempre |
| Cuota que vence esta semana | nunca urgente |

**Un hito sin completar no entra**, aunque esté atrasado: lo resuelve el
capataz, no el dueño. Una lista donde aparece lo que uno no puede hacer se deja
de mirar.

---

## 5. El orden de las obras no es cronológico

Primero las excedidas, después las que se desfasan, y dentro de cada grupo la de
mayor consumo. Una lista ordenada por fecha obligaría a leerla entera para
encontrar el problema, que es lo que pasa hoy con las planillas.

---

## 6. Endpoints

| Método | Ruta | Qué hace |
| --- | --- | --- |
| GET | `/api/tablero` | Todo el panel en una sola respuesta |

**Uno solo, y de lectura.** El Tablero no crea, no modifica y no borra nada:
todo lo que se puede hacer desde la pantalla lleva al módulo que corresponde y
se hace allí, con sus propias validaciones. Si tuviera endpoints de escritura,
una regla de negocio terminaría viviendo en dos lugares.

Y es **una** llamada y no cinco porque la pantalla no sirve de a partes: el
dueño entra para ver el estado general, media pantalla cargada no le dice nada,
y así todos los números corresponden al mismo instante.

---

## 7. Modelo de datos

**Ninguno.** No hay tabla `tablero` ni entidad `Tablero`: el módulo no tiene
datos propios. Es el único del sistema del que no hay nada en el Diccionario, y
está bien que así sea.

---

## 8. Pantalla

`/tablero`, que es además la pantalla de entrada del sistema.

Reemplaza a la maqueta con datos de muestra que existía desde el módulo 1. Se
conservó el diseño completo —los cuatro indicadores, el gráfico de barras
comparadas, la tabla de obras— y se le conectaron los datos reales.

Lo que se agregó:

- **La lista de pendientes**, arriba de los gráficos, porque es lo accionable.
- **La marca del avance financiero** sobre la barra del avance físico: si queda
  a la derecha del relleno, se gastó más de lo que se construyó. Es el cruce que
  el informe pide entre avance físico y financiero, en un solo vistazo.
- **La ganancia estimada** por obra, en rojo cuando es negativa.

---

## 9. Tests

**6 tests** de este módulo, sobre 230 del sistema.

No testean cálculos —el módulo no calcula nada, esa es su decisión de diseño—
sino que consolide bien lo que le dan los otros: que sume los totales, que no se
caiga con una obra sin presupuesto, que ordene primero lo que necesita atención
y que arme los pendientes con la urgencia correcta.

### Verificación manual

| Caso | Resultado |
| --- | --- |
| `GET /api/tablero` sin token | `401` |
| Con token de dueño | `200`, panel completo |
| Obra en ejecución sin definitivo aprobado | Aparece con semáforo "Sin presupuesto", no falla |
| Presupuesto enviado hace 9 días | Aparece como pendiente, urgencia media |
| Cuota que vence en 4 días | Aparece como pendiente, urgencia media |

---

## 10. Pendientes

| Pendiente | Se resuelve en |
| --- | --- |
| ~~Versión reducida para el Capataz General~~ | **Resuelto el 17/09/2026**, ver `18-cierre-y-endurecimiento.md` §7 |
| Consultas agregadas si crece la cantidad de obras | No hace falta con menos de diez obras activas |
| Que las tarjetas lleven a la obra filtrada y no al módulo entero | Hoy el enlace abre el módulo |
