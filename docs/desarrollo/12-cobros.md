# Módulo 12 — Cobros

**Fecha de desarrollo:** 05/09/2026
**Depende de:** Obras, Presupuestación (el plan sale del definitivo aprobado)
**Referencia en el informe:** sección "Módulo 10 – Cobros"

---

## 1. Qué resuelve

Hoy los cobros se llevan en **una planilla por obra más una planilla resumen que
se actualiza a mano**, sin ninguna alerta que avise cuándo una cuota está por
vencer. El seguimiento de las deudas depende de que el dueño se acuerde.

El módulo centraliza el plan de cobro, calcula los vencimientos, aplica el ajuste
por índice CAC y **marca las cuotas vencidas solo, a partir del calendario**.

---

## 2. El anticipo es la cuota cero

No es una tabla aparte ni un campo especial de la obra: es la **primera fila del
plan**, con `numero_cuota = 0`. Lo dice el Diccionario y tiene una consecuencia
práctica importante:

> El saldo, el total cobrado y el próximo vencimiento salen de **una sola
> consulta** sobre `cuota`. Si el anticipo viviera aparte habría que sumarlo por
> separado en cada cálculo, y basta olvidarse una vez para que un total quede
> mal.

---

## 3. El plan sale del presupuesto, no se vuelve a cargar

`POST /api/obras/{id}/cobros` recibe **un solo dato**: la fecha del primer
vencimiento. Todo lo demás —porcentaje de anticipo, cantidad de cuotas, total—
sale del definitivo aprobado.

Pedirlos de nuevo abriría la puerta a que el plan no coincida con lo que el
cliente aceptó, que es exactamente el error que hoy se comete al copiar números
entre planillas.

### Los centavos del redondeo se ajustan en la última cuota

La regla del informe es que **anticipo + cuotas = total del presupuesto**. Con
$1.000 en 3 cuotas, $333,33 × 3 da $999,99: falta un centavo. El sistema lo suma
a la última cuota ($333,34).

Unos centavos incumplen la regla igual que muchos, y una planilla que no cierra
es exactamente lo que hace desconfiar del sistema.

---

## 4. La actualización por índice CAC

Es la parte más delicada del módulo.

### El coeficiente sale de comparar dos meses, no de un índice suelto

El CAC es un número absoluto (por ejemplo 1234,56). Por sí solo **no dice cuánto
subió nada**: lo que importa es cuánto creció respecto del mes anterior.

```
coeficiente = índice del mes / índice del mes anterior
```

Por eso el sistema exige al menos dos meses cargados. Con uno solo, devuelve un
`409` explicando por qué.

### Solo se recalculan las cuotas pendientes

> *"El índice CAC se aplica sobre el saldo pendiente y recalcula únicamente las
> cuotas que todavía no fueron abonadas, **sin modificar las ya cobradas**."*

`Cuota.actualizarPorCac()` no hace nada si la cuota está abonada. Reajustar algo
ya pagado sería cobrar dos veces por lo mismo.

### La previa es obligatoria antes de confirmar

El informe la pide explícitamente, y la pantalla la muestra: el dueño ve el
coeficiente, el saldo actual, el saldo actualizado y cuántas cuotas se afectan
**antes** de aplicar el cambio. Una actualización de saldo no debería ser una
sorpresa.

### El valor se carga a mano

La importación automática desde la Cámara Argentina de la Construcción está
**explícitamente fuera del alcance** de esta versión, según el informe. El dueño
lo ingresa una vez por mes.

---

## 5. El estado Vencida se deriva del calendario

`Cuota.revisarVencimiento()` marca la cuota como vencida si pasó su fecha y sigue
impaga, y la devuelve a Pendiente si la fecha se corrigió.

**Nadie lo marca a mano.** Si dependiera de que alguien lo haga, volvería a
depender de la memoria del dueño, que es justamente lo que el módulo viene a
resolver.

---

## 6. Se anula el pago, no la cuota

> *"Una cuota abonada no puede eliminarse, únicamente puede anularse dejando un
> motivo, para conservar la trazabilidad del estado de cuenta."*

Lo que se anula es **el pago**: la cuota vuelve a Pendiente y sigue debiéndose.
El Diccionario no previó dónde guardar el motivo, así que `V12` agregó
`cuota.motivo_anulacion`, con el mismo criterio que en Obras, Gastos y Compras.

---

## 7. Endpoints

| Método | Ruta | Qué hace |
| --- | --- | --- |
| GET | `/api/obras/{id}/cobros` | Plan completo con su saldo |
| POST | `/api/obras/{id}/cobros` | Genera el plan desde el definitivo aprobado |
| GET | `/api/obras/{id}/cobros/previa-cac` | Efecto del ajuste antes de aplicarlo |
| POST | `/api/obras/{id}/cobros/actualizacion-cac` | Aplica el ajuste |
| GET | `/api/cobros` | Vista consolidada: cuánto resta de cada obra |
| GET | `/api/cobros/alertas` | Cuotas por vencer o vencidas |
| PATCH | `/api/cuotas/{id}/pago` | Registrar un pago |
| PATCH | `/api/cuotas/{id}/anulacion` | Anular el pago con motivo |
| GET | `/api/cac` | Índices cargados |
| POST | `/api/cac` | Cargar o corregir el índice del mes |

**No hay DELETE.** Ni el plan ni un pago se borran.

El pago **no recibe el monto**: es el de la cuota. Si el cliente paga fuera de
término se mantiene el valor sin recargos, tal como maneja hoy la empresa.

---

## 8. Ajustes al modelo (`V12`)

- `cuota.motivo_anulacion` — el Diccionario no lo tenía.
- `medio_pago` y `comprobante_emitido` como conjuntos cerrados, según los enumera
  el informe.

---

## 9. Pantallas

| Pantalla | Dónde |
| --- | --- |
| Plan de cobro + consolidado | `/cobranzas` |
| Registrar pago | modal |
| Anular pago | modal |
| Actualización por CAC, con previa | modal |

El anticipo se destaca en azul y las cuotas vencidas en rojo. En la vista
consolidada, **las obras con cuotas vencidas van primero**: son las que hay que
reclamar.

---

## 10. Tests

**13 tests** de este módulo, sobre 201 del sistema.

| Grupo | Casos |
| --- | --- |
| Generación del plan | 5 |
| Pagos | 3 |
| Actualización por CAC | 4 |
| Vencimiento derivado | 1 |

Un test tenía fechas fijas de septiembre y **fallaba según el día en que se
corriera**, porque el estado Vencida sale del calendario. Se cambió a fechas
relativas a `LocalDate.now()`.

### Verificación manual

Sobre la obra 5, con definitivo aprobado de $95.439.000 (30% + 6 cuotas):

| Caso | Resultado |
| --- | --- |
| Generar plan | `200`, 7 cuotas, anticipo $28.631.700, total exacto |
| Generar de nuevo | `409` |
| Cobrar el anticipo | `200`, saldo baja a $66.807.300 |
| Previa del CAC sin índices | `409` "hacen falta al menos dos meses" |
| Cargar ago 1000 y sep 1120 | `200`, coeficiente **1,12** |
| Aplicar el ajuste | **Anticipo cobrado intacto ($28.631.700); cuota pendiente $11.134.550 → $12.470.696** |
| Anular el pago | `200`, cuota vuelve a Pendiente |

---

## 11. Pendientes

| Pendiente | Se resuelve en |
| --- | --- |
| Restringir todo el módulo al rol dueño | Accesos |
| Generar la planilla de pagos en PDF para el cliente | Pendiente del informe |
| Generar el plan automáticamente al aprobar el definitivo | **Descartado el 17/09/2026**: falta la fecha del primer vencimiento, que se pacta con el cliente. En su lugar el tablero lo reclama. Ver `18-cierre-y-endurecimiento.md` §8 |
| Estado de cuenta por cliente | Cruza obras y cobros; falta la vista |
