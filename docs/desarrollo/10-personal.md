# Módulo 10 — Personal

**Fecha de desarrollo:** 05/09/2026
**Depende de:** Obras
**Alimenta a:** Seguimiento (las faltas explican demoras), Gastos (pagos de mano de obra)
**Referencia en el informe:** sección "Módulo 2 – Personal"

---

## 1. Qué resuelve

El objetivo, según el relevamiento, **no es controlar horarios ni descontar del
sueldo**: es que el dueño pueda detectar patrones de faltas reiteradas y decidir
si corresponde hablar con alguien. Todo el módulo está ordenado alrededor de eso.

Registra a los operarios, a qué obras están asignados, y sus inasistencias.

> **Distinto de Usuarios.** Personal registra a **todos** los operarios, trabajen
> o no con el sistema. Usuarios gestiona solo las cuentas de acceso. La mayoría
> de los operarios no va a tener cuenta: el vínculo existe para los que reciben
> materiales en obra y necesitan confirmarlo desde el celular.

---

## 2. Una corrección al modelo (migración `V11`)

El informe pide dos cosas que, con el modelo de `V8`, se contradecían:

> *"Al marcar un operario como Inactivo, se lo desvincula automáticamente de las
> obras activas, **pero se conserva** su historial de inasistencias y de **obras
> anteriores**."*

`operario_obra` solo tenía `fecha_asignacion`. Desvincular significaba **borrar
la fila**, y con eso se perdía justamente el historial de obras que el informe
manda conservar. Tampoco se podría responder después *"¿en qué obras trabajó
este operario?"*, que es la pregunta que la ficha tiene que contestar.

Se agregó `operario_obra.fecha_desasignacion`. La baja pasa a ser **un dato, no
un borrado**: la fila queda y se sabe desde cuándo dejó de estar en esa obra.
Una asignación vigente es la que tiene esa fecha en `NULL`.

```sql
ALTER TABLE operario_obra ADD COLUMN fecha_desasignacion DATE;
CHECK (fecha_desasignacion IS NULL OR fecha_desasignacion >= fecha_asignacion)
```

---

## 3. Decisiones

### Reasignar reabre, no duplica

La clave primaria de `operario_obra` es `(id_operario, id_obra)`, así que no
puede haber dos filas para el mismo par. Si un operario vuelve a una obra donde
ya estuvo, la asignación cerrada **se reabre** en lugar de crear una nueva.

También es lo correcto conceptualmente: es la misma relación, que se retoma.

### La regla de las faltas mira el historial, no lo vigente

> *"Un operario solo puede tener inasistencias registradas en obras a las que
> esté (o **haya estado**) asignado."*

`Operario.estuvoAsignadoA()` recorre **todas** las asignaciones, incluidas las
cerradas. El motivo es práctico: una falta se puede cargar días después, cuando
el operario ya cambió de obra. Si la regla mirara solo lo vigente, el sistema
rechazaría un registro perfectamente válido.

### El motivo no es obligatorio, y se puede completar después

El informe lo aclara: *"muchas faltas no tienen justificación conocida al momento
de registrarlas, pero queda disponible para completarse después si el operario
avisa el motivo más tarde"*.

Por eso existe `PATCH /api/inasistencias/{id}/motivo`: completar el motivo
después es el **caso normal**, no una excepción, y merece su propia operación.

---

## 4. Endpoints

| Método | Ruta | Qué hace |
| --- | --- | --- |
| GET | `/api/operarios` | Listado con `busqueda`, `estado`, `obra` |
| GET | `/api/operarios/{id}` | Ficha con historial de obras |
| POST | `/api/operarios` | Alta |
| PUT | `/api/operarios/{id}` | Editar datos |
| PATCH | `/api/operarios/{id}/estado` | Activar / desactivar |
| POST | `/api/operarios/{id}/asignaciones` | Asignar a una obra |
| DELETE | `/api/operarios/{id}/asignaciones/{idObra}` | Sacarlo de una obra |
| GET | `/api/inasistencias` | Listado con `operario`, `obra`, `desde`, `hasta` |
| POST | `/api/inasistencias` | Registrar una falta |
| PATCH | `/api/inasistencias/{id}/motivo` | Completar el motivo después |

**No hay DELETE de operarios:** se desactivan, para conservar el historial.

El `DELETE` de una asignación **tampoco borra**: cierra la fila con la fecha de
hoy. Se usa ese verbo porque desde afuera la acción es *"sacalo de esta obra"*;
lo que pasa adentro es que la obra queda en su historial.

Las inasistencias van en un controlador propio y no dentro de `/api/operarios`
porque se consultan **de las dos puntas**: por operario (para ver reiteraciones)
y por obra (para cruzar contra el avance en Seguimiento).

---

## 5. Validaciones

| Regla del informe | Dónde se valida |
| --- | --- |
| No hay inasistencia sin obra | FK `NOT NULL` + `@NotNull` |
| Solo en obras donde estuvo asignado | `Operario.estuvoAsignadoA()` |
| No dos faltas del mismo operario, obra y fecha | Índice único (`V8`) + control en el servicio |
| Al desactivar se desvincula pero se conserva el historial | `Operario.desactivar()` |
| El motivo no es obligatorio | Columna nullable, sin `@NotNull` |

**Reglas agregadas:** un operario inactivo no se asigna a obras, y una obra
cancelada o finalizada no admite asignación de personal.

---

## 6. Pantallas

| Pantalla | Dónde |
| --- | --- |
| Listado de operarios | `/personal` |
| Ficha con obras e historial de faltas | modal |
| Registrar falta | formulario en línea dentro de la ficha |
| Completar motivo | edición en línea en la fila de la falta |

**La cantidad de faltas está en el listado y se destaca por tramos**: en ámbar a
partir de una, en rojo a partir de tres. Tres faltas ya es un patrón, no una
casualidad, y es exactamente lo que el dueño necesita ver sin entrar a cada
ficha.

En la ficha, las obras vigentes se muestran en azul y las anteriores en gris con
borde punteado: el historial se ve, pero se distingue de lo actual.

---

## 7. Tests

**10 tests** de este módulo, sobre 180 del sistema.

| Grupo | Casos |
| --- | --- |
| Asignación a obras | 5 |
| Baja del operario | 1 |
| Inasistencias | 4 |

### Verificación manual

| Caso | Resultado |
| --- | --- |
| Falta sin estar asignado | `409` con el nombre del operario y la obra en el mensaje |
| Asignar dos veces a la misma obra | `409` |
| Falta ya asignado, sin motivo | `201`, motivo nulo |
| Misma falta otra vez | `409` |
| Completar el motivo después | `200` |
| Desactivar el operario | `200`, vigentes 0 pero **1 obra en el historial** |

---

## 8. Pendientes

| Pendiente | Se resuelve en |
| --- | --- |
| `id_usuario_registro` real en la inasistencia | Accesos |
| Vínculo `operario` ↔ `usuario` | Usuarios |
| Restringir el registro de faltas a dueño y capataz general | Accesos |
| Resumen de inasistencias dentro de la ficha de obra | Cuando se arme esa ficha |
| Cruzar faltas contra demoras del avance | Ya es posible: Seguimiento las puede consultar por obra |
