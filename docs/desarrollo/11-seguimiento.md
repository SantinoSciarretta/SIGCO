# Módulo 11 — Seguimiento de Obras

**Fecha de desarrollo:** 05/09/2026
**Depende de:** Obras, Gastos (para el avance financiero)
**Cierra:** el ciclo de vida de la obra (la pasa a Finalizada)
**Referencia en el informe:** sección "Módulo 9 – Seguimiento de Obras"

---

## 1. Qué resuelve

Hoy el cronograma se arma al inicio de la obra y **no se actualiza nunca**. El
avance se evalúa de memoria durante las visitas diarias del dueño y no queda
registrado en ninguna parte.

El módulo lo convierte en un porcentaje objetivo, y hace algo que hoy es
imposible: **cruzar el avance físico contra el financiero**, para detectar obras
que consumieron buena parte del presupuesto sin haber avanzado en la misma
proporción.

---

## 2. Ajustes al modelo (migración `V11`)

### Dos estados, no tres

`V8` permitía `Pendiente`, `En curso` y `Completado`. El informe enumera solo
dos, y hay un motivo de fondo:

> *"El porcentaje de avance físico se calcula únicamente sobre los hitos
> completados y su ponderación, **sin estimaciones intermedias de hitos en
> curso**, para que el valor sea siempre objetivo."*

Un estado "En curso" invitaría justamente a la estimación intermedia que el
informe quiere evitar. Con dos estados, el número no admite interpretación.

### Un hito no se repite dentro de la obra

Se agregaron dos índices únicos: `(id_obra, LOWER(nombre_hito))` y
`(id_obra, orden)`. Dos hitos con el mismo nombre son casi con seguridad una
carga duplicada y **romperían el cálculo sumando dos veces la misma
ponderación**. Y el orden define la secuencia prevista: repetirlo no significa
nada.

---

## 3. Decisiones

### El conjunto de hitos se define de una vez, no de a uno

`PUT /api/obras/{id}/hitos` reemplaza la configuración **entera**. Es PUT y no
POST porque la regla de las ponderaciones aplica al conjunto: la suma tiene que
dar exactamente 100%.

Cargándolos de a uno, la obra quedaría en un estado inválido entre altas y el
avance calculado en el medio no significaría nada.

### Por qué exactamente 100%

Con 90, el avance nunca llegaría a completo y la obra jamás se finalizaría sola.
Con 110, una obra a medias podría mostrar 100%. Es la regla que hace que el
porcentaje signifique algo.

La pantalla muestra la **suma acumulada mientras se escribe**, como pide el
informe: el error se ve al tipear, no al intentar guardar.

### El orden se respeta, salvo excepción explícita

> *"No se puede completar un hito posterior si hay hitos anteriores sin
> completar, **salvo que el dueño lo habilite de forma explícita**, ya que en la
> práctica algunas tareas pueden adelantarse."*

El flag `forzar` implementa esa excepción. El mensaje de error nombra el hito que
falta, así el usuario sabe qué está salteando antes de confirmarlo.

### Completar el último hito finaliza la obra

Es el cierre del ciclo de vida que arrancó cuando se aprobó el presupuesto
definitivo y la obra pasó a *En ejecución*. Una vez finalizada, los hitos quedan
bloqueados, conservando las fechas en que se completó cada uno.

### No se redefine el plan si ya hay hitos completados

No está en el informe, pero es la misma lógica que rige el resto del sistema:
redefinir el conjunto borraría el registro de lo que ya se completó, y **lo que
ya pasó no se pisa**.

---

## 4. El cruce con el avance financiero

Es la funcionalidad que justifica el módulo. El panel muestra dos barras:

- **Avance físico** — suma de las ponderaciones de los hitos completados.
- **Avance financiero** — porcentaje del presupuesto ya gastado, que aporta
  Gastos.

Cuando el financiero supera al físico por más de **15 puntos porcentuales** (el
margen que sugiere el informe), se dispara una alerta: se está gastando más
rápido de lo que se avanza.

### Un bug de transacciones que apareció acá

La primera versión llamaba a `GastoService.estadoFinanciero()` y atrapaba su
excepción cuando la obra no tenía presupuesto aprobado. **El panel entero fallaba
con `UnexpectedRollbackException`.**

El motivo: `estadoFinanciero()` es `@Transactional`. Cuando lanza una
`RuntimeException`, Spring marca la transacción como *rollback-only*. Atraparla
afuera **no deshace esa marca**, y la transacción del llamador explota al
confirmar.

> **Una excepción no sirve como control de flujo cruzando un límite
> transaccional.** Se agregó `GastoService.porcentajeConsumidoOCero()`, que
> consulta y devuelve cero sin lanzar nada.

Los tests no lo detectaron porque los mocks no simulan el manejo transaccional de
Spring. Apareció al abrir la pantalla contra el backend real.

---

## 5. Plantillas de hitos

El informe las pide para no rearmar desde cero los hitos de cada obra: la mayoría
de las reformas pasan por las mismas etapas y lo único que cambia son los pesos.

Una plantilla se valida con la misma regla del 100% que una configuración
manual. Aplicarla a una obra crea sus hitos.

---

## 6. Endpoints

| Método | Ruta | Qué hace |
| --- | --- | --- |
| GET | `/api/obras/{id}/avance` | Panel: hitos, avance físico y financiero, plazos |
| PUT | `/api/obras/{id}/hitos` | Define el conjunto completo de hitos |
| POST | `/api/obras/{id}/hitos/desde-plantilla/{idPlantilla}` | Aplica una plantilla |
| PATCH | `/api/hitos/{id}/cumplimiento` | Completar (finaliza la obra si es el último) |
| PATCH | `/api/hitos/{id}/reapertura` | Deshacer un cumplimiento marcado por error |
| PATCH | `/api/hitos/{id}/observacion` | Registrar una demora o cambio |
| GET | `/api/plantillas-hito` | Plantillas disponibles |
| POST | `/api/plantillas-hito` | Crear una plantilla |

Las rutas de avance y configuración cuelgan de la **obra** porque así se
consulta; las de cumplimiento cuelgan del **hito**, que es lo que se modifica.

---

## 7. Validaciones

| Regla del informe | Dónde se valida |
| --- | --- |
| Solo obras **En ejecución** admiten hitos | `SeguimientoService.configurar()` |
| Las ponderaciones suman exactamente 100% | `exigirQueSumeCien()` |
| Fecha de cumplimiento obligatoria | `@NotNull` en el DTO |
| No se completa salteando, salvo habilitación explícita | flag `forzar` |
| El avance solo cuenta hitos completados | Sin estado intermedio |
| Alerta de desfasaje sobre un margen configurable | `MARGEN_DESFASAJE = 15` |
| Obra finalizada: hitos bloqueados | `SeguimientoService.completar()` |
| Último hito completado → obra Finalizada | Ídem |

---

## 8. Pantallas

| Pantalla | Dónde |
| --- | --- |
| Panel de avance | `/seguimiento` |
| Configuración de hitos | modal, con suma acumulada en vivo |
| Completar hito | modal, con la casilla de saltear etapas |

**La alerta de desfasaje va arriba de todo**, antes que cualquier otra cosa: es
la razón por la que el dueño entra a esta pantalla.

Las dos barras van una arriba de la otra, con colores distintos (azul el físico,
gris el financiero), para que la comparación se lea sin hacer la cuenta.

---

## 9. Tests

**14 tests** de este módulo, sobre 180 del sistema.

| Grupo | Casos |
| --- | --- |
| Configuración de hitos | 5 |
| Cumplimiento y avance | 6 |
| Cruce con el avance financiero | 3 |

### Un bug que encontró la verificación manual

El campo `forzar` estaba declarado como `boolean` primitivo. **Omitirlo en la
llamada devolvía `400`**, porque Jackson no puede mapear `null` a un primitivo.
Un flag opcional que rompe la llamada cuando no se manda no es opcional.

Se cambió a `Boolean` con un método `forzarOFalso()`, y se agregó un test de
regresión que lo pasa como `null`. Los tests originales no lo detectaron porque
siempre lo pasaban explícito.

### Verificación manual

| Caso | Resultado |
| --- | --- |
| Hitos que suman 70 | `409` "suman 70% y tienen que sumar exactamente 100%" |
| Hitos en obra sin ejecutar | `409` |
| Hitos que suman 100 | `200`, 4 hitos |
| Completar el 3º sin el 1º | `409` nombrando el hito que falta |
| Completar el 1º | `200`, **20% físico contra 45% financiero → alerta activada** |
| Adelantar con `forzar` | `200` |
| Completar el último | `200`, avance 100%, **obra Finalizada** |
| Tocar un hito de obra finalizada | `409` |

---

## 10. Pendientes

| Pendiente | Se resuelve en |
| --- | --- |
| `id_usuario_completa` real | Accesos |
| Vista de todas las obras activas con su avance | Dashboard |
| Gráfico de evolución del avance en el tiempo | Requiere histórico, hoy solo hay el estado actual |
| Cruzar faltas de Personal contra demoras | Ya consultable, falta mostrarlo junto al hito |
