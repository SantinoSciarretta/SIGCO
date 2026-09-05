# Módulo 09 — Gastos

**Fecha de desarrollo:** 05/09/2026
**Depende de:** Obras, Presupuestación (rubros y el definitivo aprobado), Compras
**Referencia en el informe:** sección "Módulo 5 – Gastos"

---

## 1. Qué resuelve

Hoy la empresa carga cada gasto **dos veces**: durante la semana en una planilla
del celular, y el sábado se pasa a la planilla grande de la computadora. El
resultado económico de una obra se conoce recién cuando termina.

El módulo elimina las dos cosas: el gasto se carga una vez, en el momento, y
queda comparado contra lo presupuestado en el mismo rubro **al instante**.

> **Lo central del módulo no es guardar un gasto.** Es cruzarlo contra el
> presupuesto. Ese cruce es posible porque `gasto` e `item_presupuesto`
> comparten la clasificación por rubro: si cada módulo tuviera su propia lista,
> la comparación no existiría.

---

## 2. Corrección al modelo (migración `V10`)

`V8` había creado `gasto.estado` con el conjunto `('Registrado', 'Anulado')`. El
informe, en la tabla "Campos del Formulario de Gasto", enumera explícitamente:

> *Estado_Gasto | Lista desplegable | **Confirmado** / Anulado*

Se corrigió el conjunto. De paso se cerró `tipo_gasto`, que `V8` no había
restringido: `Material / Mano de Obra / Gasto Hormiga / Otro`.

---

## 3. Una contradicción del informe, sin resolver

**El informe dice dos cosas distintas sobre el subrubro.**

| Fuente | Qué dice |
| --- | --- |
| Sección "Validaciones y Lógica" | *"El subrubro es obligatorio en todo gasto"* |
| Tabla "Campos del Formulario de Gasto" | *Subrubro_Asociado \| Relación (**opcional**)* |
| Diccionario de Datos | No lo marca obligatorio |

Se implementó **opcional**, por dos razones:

1. Coincide con dos de las tres fuentes.
2. El semáforo que describe el propio informe compara **por rubro**, no por
   subrubro. Exigirlo agregaría fricción sin servir al cálculo, y hay rubros del
   catálogo que no tienen subrubros definidos.

Queda anotado en `Gasto.java` y **pendiente de decisión**.

---

## 4. El semáforo

Es la funcionalidad central. Los umbrales son textuales del informe:

> *"verde mientras el gasto acumulado no supere el noventa por ciento del monto
> presupuestado para ese rubro, amarillo entre el noventa y el cien por ciento, y
> rojo al **superar** el monto presupuestado"*

| Consumo del rubro | Semáforo |
| --- | --- |
| menos de 90% | Verde |
| 90% a 100% **inclusive** | Amarillo |
| más de 100% | Rojo |
| Hay gasto pero no hay presupuesto | Sin presupuesto |

### El 100% exacto es amarillo, no rojo

El informe dice *"al superar"*, no *"al alcanzar"*. Gastar exactamente lo
presupuestado no es un desvío. Hay un test específico para ese borde
(`cienExactoEsAmarillo`).

### «Sin presupuesto» es un cuarto estado, no rojo

Un rubro donde se está gastando sin que nadie lo haya presupuestado no es un
desvío: es un faltante de planificación, y merece un aviso distinto. Marcarlo
rojo lo mezclaría con los rubros que simplemente se excedieron.

### Se calcula, nunca se guarda

El total gastado sale de una consulta agregada (`SUM`) sobre los gastos
confirmados, no de un contador acumulado en la tabla. El informe lo pide
explícitamente, y el motivo es sólido:

> Un contador guardado hay que recalcularlo en cada alta, edición y anulación.
> Basta que una de esas ramas se olvide para que el número quede mal **para
> siempre**, sin que nadie se entere.

Lo mismo vale para la **ganancia estimada**: es `total presupuestado − total
gastado`, derivada en cada consulta. Por eso "se recalcula con cada gasto nuevo"
sale sola, sin código que la mantenga.

---

## 5. Estado financiero de la obra

`GET /api/obras/{id}/estado-financiero` devuelve la comparación completa.

La ruta cuelga de la obra y no de `/api/gastos` porque así es como se consulta:
se parte de *"cómo viene esta obra"*, no de *"listame gastos"*. Va en un
controlador propio (`EstadoFinancieroController`), igual que el comparador de
cotizaciones en Proveedores.

Devuelve la **unión** de dos conjuntos de rubros:

- Los que tienen presupuesto (aunque no tengan gasto): todavía no se ejecutaron.
- Los que tienen gasto (aunque no tengan presupuesto): se está gastando en algo
  que nadie previó. Éstos son los más importantes de ver.

---

## 6. Integración con Compras

Es la funcionalidad que el informe describe como *"vinculación automática de cada
compra confirmada con el módulo Gastos, generando el gasto correspondiente sin
necesidad de cargarlo dos veces"*.

Al confirmar la recepción de un pedido, `PedidoService` agrupa los materiales por
rubro y llama a `GastoService.generarDesdeRecepcion()`.

### Un gasto por rubro, no uno por pedido

Un pedido puede mezclar cemento (Albañilería) con cable (Electricidad). Un gasto
único habría que imputarlo a un solo rubro, y el semáforo del otro quedaría
mintiendo. Por eso se agrupa antes de generar.

### La dirección de la dependencia

`generarDesdeRecepcion()` recibe **datos primitivos** (obra, id del pedido, un
mapa `rubro → monto`, fecha), no la entidad `Pedido`. Así **Gastos no depende de
Compras** y la relación queda en una sola dirección: Compras → Gastos.

### Si la obra no admite gastos, la recepción no falla

Que el camión haya llegado a la obra es un **hecho físico**: no puede depender
del estado de la presupuestación. Si la obra no está en ejecución o no tiene
definitivo aprobado, no se genera el gasto y la recepción se confirma igual.

### No se duplican

`countByIdPedido` evita generar dos veces los gastos del mismo pedido si la
recepción se reprocesa.

---

## 7. Endpoints

| Método | Ruta | Qué hace |
| --- | --- | --- |
| GET | `/api/gastos` | Listado con filtros `obra`, `rubro`, `tipo`, `estado`, `desde`, `hasta` |
| GET | `/api/gastos/{id}` | Un gasto |
| POST | `/api/gastos` | Alta |
| PUT | `/api/gastos/{id}` | Editar |
| PATCH | `/api/gastos/{id}/anulacion` | Anular con motivo |
| **GET** | **`/api/obras/{id}/estado-financiero`** | **Presupuestado contra gastado, por rubro** |

**No hay DELETE.** El informe lo pide explícitamente: *"No se permite eliminar un
gasto ya registrado, únicamente anularlo dejando un motivo, para conservar la
trazabilidad completa de la obra."* Un gasto anulado deja de sumar en la
comparación, pero sigue existiendo.

### La obra no se puede cambiar al editar

`PUT` recibe el mismo DTO que el alta pero **ignora `idObra`**. Mover un gasto de
obra alteraría el resultado económico de las dos. Si se cargó en la obra
equivocada, corresponde anularlo y cargarlo bien.

---

## 8. Validaciones

| Regla del informe | Dónde se valida |
| --- | --- |
| No hay gasto sin obra y rubro existentes | FK `NOT NULL` + `RecursoNoEncontrado` |
| Solo obras **En ejecución** con definitivo aprobado | `exigirObraEnEjecucionConPresupuesto()` |
| `Fecha_Gasto` distinta de `Fecha_Carga` | Dos columnas separadas |
| No se elimina, se anula con motivo | No existe DELETE + `CHECK` |
| El gasto hormiga no se excluye de la comparación | Suma como cualquier otro, se mide aparte |
| Solo dueño o capataz general registran gastos | Pendiente: Accesos |

**Regla agregada:** el subrubro debe pertenecer al rubro del gasto. Misma regla
que en `item_presupuesto` y por el mismo motivo: si un gasto de Albañilería
pudiera llevar un subrubro de Plomería, el agrupamiento por rubro dejaría de
significar algo.

---

## 9. Pantallas

| Pantalla | Dónde |
| --- | --- |
| Estado financiero + listado | `/gastos` |
| Nuevo gasto | modal |
| Anular gasto | modal |

**La pantalla arranca por una obra, no por el listado completo.** La pregunta
real del dueño no es "listame gastos", es "cómo viene esta obra". Se preselecciona
la primera obra en ejecución.

El panel muestra cuatro tarjetas (presupuestado, gastado, ganancia estimada,
gasto hormiga) y la tabla por rubro con barra de consumo. **La ganancia negativa
se muestra en rojo y cambia la etiqueta a "Pérdida estimada"**: es el dato que
hoy aparece recién al cerrar la obra.

En el listado, los gastos generados por una compra se marcan con `◆ PEDIDO #n`, y
los anulados quedan atenuados y con el monto tachado — siguen visibles porque no
se eliminan.

Los colores del semáforo son los **funcionales** del sistema (`--color-ok`,
`--color-alerta`, `--color-excedido`), no los de la marca: acá el color es
información, no decoración.

---

## 10. Tests

**17 tests** de este módulo, sobre 156 del sistema.

| Grupo | Casos |
| --- | --- |
| Alta | 4 |
| Semáforo y estado financiero | 8 |
| Integración con Compras | 3 |
| Anulación y edición | 2 |

Los del semáforo cubren los cuatro tramos y los dos bordes exactos (90% y 100%),
que es donde un umbral mal escrito no se nota hasta que importa.

### Verificación manual del circuito completo

Sobre la obra 5 (Ituzaingo 231, Pilar), con definitivo aprobado de $95.439.000:

| Paso | Resultado |
| --- | --- |
| Estado financiero inicial | Todo en cero, verde, ganancia $95.439.000 |
| Gasto manual de $40.000.000 en Albañilería | Rubro pasa a **91,82% amarillo** |
| Gasto hormiga de $350.000 | Se contabiliza aparte |
| Pedido recibido (cemento + cable, $3.900.000) | **2 gastos generados solos**: $2.600.000 Albañilería y $1.300.000 Electricidad |
| Anular el gasto de Electricidad | Vuelve a $0 y la ganancia sube; el registro queda |
| Gasto en obra "En presupuestación" | `409` |
| Tipo de gasto no válido | `400` |

---

## 11. Pendientes

| Pendiente | Se resuelve en |
| --- | --- |
| Decidir si el subrubro es obligatorio | Con Santino |
| `id_usuario_registro` real | Accesos |
| Restringir a dueño y capataz general | Accesos |
| Vincular el gasto al operario en pagos de mano de obra | Personal |
| Carga real del comprobante a Supabase Storage | Integración de almacenamiento |
| Exportar reporte de gastos a PDF o Excel | Pendiente del informe |
| Reporte de gastos hormiga como vista propia | Hoy se filtra por tipo en el listado |
