# 20 — Los cambios que pidió Ricardo al probar el sistema

**Fecha:** 23/09/2026
**Origen:** Ricardo Sciarretta usó el sistema corriendo en local y anotó ocho
cambios. No son errores: son cosas que, viéndolo funcionar, le harían el día a
día distinto. Este documento registra qué se hizo con cada uno y por qué.

Vale la pena decirlo de entrada porque es el hallazgo más útil de la prueba:
**ninguno de los ocho pedidos fue un bug.** El sistema hacía lo que el informe
decía. Lo que faltaba era la forma en que él trabaja — presupuestar recorriendo
una lista en vez de cargar ítems de a uno, entrar por la obra y no por el
presupuesto, mandarle un papel al corralón. El relevamiento capturó bien el
*qué*; estas ocho cosas son el *cómo*.

---

## Estado de los ocho pedidos

| # | Pedido | Estado |
| --- | --- | --- |
| 1 | Comienzo de obra + meses estimados → fecha de fin calculada | **Hecho** (`c65380a`) |
| 2 | Pedidos solo de obras en ejecución | **Hecho** (`c65380a`) |
| 3 | PDF del pedido para mandar al proveedor | **Hecho** (`c65380a`) |
| 4 | Filtro por obra en el Tablero | **Hecho** (`c65380a`) |
| 5 | Presupuestos tipo planilla, con mano de obra como rubro propio | **Hecho** (`083f8fe`) |
| 6 | Agrupar presupuestos por obra y ver las tres instancias | **Hecho** (este documento) |
| 7 | Botón de obra terminada para ver el balance | **Hecho** |
| 8 | Etapas por obra que alimenten el Seguimiento | **Hecho** |

---

## 1. El plazo de obra se calcula

**Lo que pidió:** *"Comienzo de obra, cantidad de meses estimado en nueva obra y
que calcule solo la fecha tentativa de final de obra y lo muestre."*

**Qué se construyó.** `V17` agregó a `obra` dos columnas: `fecha_inicio_estimada`
(DATE) y `meses_estimados` (INTEGER, con `CHECK` de 1 a 120). `fecha_fin_estimada`
ya existía, pero se cargaba a mano.

La decisión de fondo: **la fecha de fin dejó de ser un campo que se escribe y
pasó a ser un resultado.** `Obra.estimarPlazo(inicio, meses)` la calcula y la
guarda. Si se dejaran los tres campos editables por separado, nada impediría
guardar "empieza en marzo, dura 6 meses, termina en julio", y a partir de ahí no
se sabría cuál de los tres es el dato bueno.

Cuando se registra el inicio real, el plazo se recalcula desde esa fecha y no
desde la estimada: si la obra arrancó dos semanas tarde, termina dos semanas
más tarde, y esa es la fecha que hay que mirar.

## 2. Los pedidos, solo de obras en ejecución

**Lo que pidió:** *"Los pedidos que únicamente te deje cargar de obras que estén
en ejecución."*

**Por qué tiene razón.** Mientras se presupuesta no se compra nada. El
presupuesto puede no aprobarse, y un pedido cargado antes generaría un gasto
contra una obra que quizás nunca arranca — que es exactamente el tipo de gasto
que después no se sabe a qué imputar.

`PedidoService.exigirObraOperativa` lo rechaza con un mensaje que dice qué hacer:
si la obra está en presupuestación, avisa que los pedidos se habilitan cuando se
aprueba el presupuesto; si está cancelada o finalizada, lo dice así.

## 3. El PDF del pedido

**Lo que pidió:** *"Cuando pone enviar pedido que genere un pdf para poder
enviarle al proveedor o corralón."*

`GET /api/pedidos/{id}/orden` devuelve la orden con el membrete: materiales,
cantidades con su unidad, precios acordados y dónde entregar.

**Lo que NO lleva, a propósito:** presupuesto de la obra, gastos, ganancia ni el
nombre del cliente. Es un documento que sale de la empresa hacia afuera, y el
corralón no tiene por qué saber cuánto se le cobra al cliente de Palermo. Es el
mismo criterio que el informe aplica al PDF del presupuesto, donde los valores
unitarios no aparecen.

## 4. El Tablero filtra por obra

El Tablero consolidaba todo junto. Ahora tiene un selector de obra que acota los
desvíos, las cuotas y los pedidos a una sola. Sirve para la conversación
concreta: "estoy por llamar al cliente de Cabildo, ¿cómo viene esa obra?".

---

## 5. El presupuesto como planilla

**Lo que pidió:** *"Que los presupuestos se carguen tipo planilla (…) que cuando
elija un rubro para presupuestar en el definitivo o en el anteproyecto le
aparezcan todos los materiales de catálogo en una planilla como si fuese Excel y
que vaya poniendo precio y cantidad. Para mano de obra tendría que ser como su
propio rubro."*

### El problema

Presupuestar un rubro era agregar los ítems de a uno: abrir el formulario,
buscar el material, escribir cantidad y precio, guardar, y otra vez desde el
principio. Con veinte materiales son veinte vueltas por el mismo formulario. Y
peor: hay que **acordarse** de qué materiales lleva ese rubro, porque la pantalla
no los muestra hasta que uno los busca. El catálogo de Materiales existía pero no
se veía en el momento en que hace falta.

### La solución

`GET /api/presupuestos/{id}/planilla/{idRubro}` devuelve **una fila por cada
material del catálogo de ese rubro**, con su unidad, y con la cantidad y el
precio ya cargados si ese material se presupuestó antes. La pantalla la muestra
como una hoja de cálculo y se completa lo que vaya.

`PUT` de la misma ruta la guarda. Dos reglas:

- **Las filas incompletas no se cargan.** `FilaCompletada.tieneCarga()` exige
  cantidad mayor a cero *y* precio presente. Una cantidad sin precio no suma
  nada al total: guardarla dejaría un ítem que aporta cero y ensucia el detalle.
- **Guardar reemplaza los ítems de ese rubro, no aplica cambios sueltos.** Este
  es el punto delicado. La planilla llega entera, y el resultado tiene que ser
  exactamente lo que el usuario ve. Si se aplicaran cambios sueltos habría que
  llevar la cuenta de qué fila se editó y cuál se vació, y un despiste ahí deja
  ítems fantasma: invisibles en la planilla pero sumando al total del
  presupuesto. Los otros rubros no se tocan.

### La mano de obra

`V18` agregó `rubro.es_mano_de_obra` (BOOLEAN). El rubro marcado se comporta
distinto: **su planilla no lista materiales sino los otros rubros**, para cargar
de una sola vez cuánto sale la mano de obra de albañilería, de plomería, de
pintura. Los ítems que genera pertenecen al rubro Mano de obra, así ese total
queda junto y no repartido entre los rubros de material.

Tres decisiones que conviene explicar:

- **Es una marca y no el nombre del rubro.** Reconocerlo por cómo se llama se
  rompería el día que alguien lo renombre a "Mano de obra y jornales".
- **Un índice único parcial** (`WHERE es_mano_de_obra = TRUE`) impide que haya
  dos marcados: la planilla no sabría cuál es. Marcar uno nuevo desmarca el
  anterior, con un `flush` en el medio para que el índice no rechace el
  instante en que hay dos.
- **No se incluye a sí mismo** en su propia planilla. Sería "mano de obra de la
  mano de obra".

El "agregar ítem" de a uno sigue existiendo, para lo que no está en el catálogo:
dirección de obra, un trabajo puntual, un material que se compró una sola vez.

### Un detalle que apareció al mirar la pantalla

La primera versión hacía los campos transparentes para que la tabla pareciera
una hoja de cálculo. El resultado fue que las celdas editables se leían como
texto vacío: no había forma de saber dónde escribir. Ahora tienen fondo tenue,
borde suave y un guión de marcador de posición. Es el tipo de cosa que no la
encuentra ningún test.

---

## 6. Presupuestos agrupados por obra

**Lo que pidió:** *"Agrupar presupuestos por obra y que cuando se ingresa se
puedan ver las 3 versiones."*

### El problema

El listado mostraba una fila por presupuesto, mezclando los de todas las obras.
Para saber en qué instancia estaba una obra había que buscar sus filas entre las
demás y compararlas. Y el circuito de Granica tiene tres instancias por obra
(cotización inicial → anteproyecto → definitivo), así que el listado plano crecía
al triple de las obras.

### La solución

`GET /api/presupuestos/por-obra` devuelve una entrada por obra con sus
presupuestos adentro. La pantalla pasó a tener dos niveles: el listado con una
fila por obra, y al entrar, las instancias de esa obra en bloques separados
(`/presupuestos/obra/{idObra}`).

### Por qué el agrupado se hace en el backend

Agrupar una lista es presentación y podría hacerse en React. Lo que no es
presentación es **decidir cuál presupuesto gobierna la obra**, y eso viene en la
misma respuesta (`idPresupuestoVigente`, `totalVigente`). Una obra puede tener
cinco presupuestos y solo uno es el que se cobra, el que Gastos usa de referencia
y el que puso la obra en ejecución. Si esa regla viviera en la pantalla, el
Tablero y esta vista podrían mostrar números distintos de la misma obra.

La regla, en `ObraPresupuestada.elVigente`:

1. **El definitivo aprobado**, si existe — el de mayor versión. Es el que el
   cliente aceptó.
2. Si no hay, **el último del circuito que siga en juego**. Un presupuesto
   rechazado no representa a la obra: si el definitivo se rechazó, lo que sigue
   en pie es el anteproyecto.
3. Si están todos rechazados, el último igual, para no devolver `null`.

### El orden de las instancias

Se ordenan por el circuito (cotización → anteproyecto → definitivo → adicional)
y después por versión, **no por fecha de creación**. El anteproyecto va antes que
el definitivo aunque se haya cargado después, porque así se lee la historia de la
negociación.

### Lo que se perdió y lo que se ganó

El listado plano tenía filtros por tipo y por estado. En una vista agrupada esos
filtros no tienen sentido: filtrar por "Definitivo" escondería las instancias de
cada obra. Se reemplazaron por un **buscador por dirección o cliente**, que filtra
en memoria porque son pocas obras y el listado ya está cargado.

La eliminación de un presupuesto se mudó a la pantalla de la obra, que es donde
ahora se ve cada versión. El diálogo de confirmación es el mismo.

### Verificación

**Tests (6 nuevos, 321 en total):** que agrupe una entrada por obra, que ordene
por el circuito y no por como vinieron, que el definitivo aprobado gobierne, que
un rechazado no gobierne si hay otro en pie, que entre dos aprobados mande el de
mayor versión, y que siempre devuelva un vigente aunque estén todos rechazados.

**Contra el backend real:** con los datos de prueba (7 obras, 13 presupuestos) se
comprobó que no se pierde ni se duplica ningún presupuesto al agrupar, que el
total vigente coincide con el del presupuesto vigente, que el orden del circuito
se respeta en las 7 obras, y que sin token el endpoint responde 401.

**En el navegador:** las 7 filas con sus instancias, el buscador filtrando por
dirección, la pantalla de la obra con un bloque por instancia y cada versión
abriéndose en su detalle.

---

## 7. Cerrar la obra y ver el balance

**Lo que pidió:** *"Que también haya un botón para poner obra terminada para
ver balance."*

### El cierre

Dar la obra por terminada ya era posible (`PATCH /api/obras/{id}/estado`), pero
tenía un agujero: al pasar a Finalizada **los hitos quedan bloqueados** y ya no
se puede cargar avance. Cerrar por error una obra que sigue no se podía deshacer
desde la pantalla.

Ahora, si quedan hitos sin completar, el servicio pide una confirmación
explícita (`confirmaHitosPendientes`) y el mensaje dice cuántos son. No lo
impide —una obra puede terminarse con hitos sin marcar, pasa— pero deja de ser
un clic de más. Es el mismo criterio que la cancelación de una obra en ejecución.

Lo que el cierre **no** hace: cancelar lo que falte cobrar. El plan de cobro
sigue vigente después de terminada la obra, que es como funciona en la realidad.

### El balance

`GET /api/balance/{idObra}` devuelve el cierre económico. La pantalla está en
`/obras/{id}/balance`, con acceso desde la ficha de la obra.

**Tres números, no uno.** Es lo que justifica la pantalla entera. "Cuánto gané
con esta obra" tiene tres respuestas distintas, y confundirlas hace que una obra
parezca rentable cuando no lo es:

| Número | Cuenta | Qué dice |
| --- | --- | --- |
| Ganancia estimada | presupuestado − gastado | Lo que deja **si** el cliente termina de pagar. Es una proyección. |
| Resultado de caja | cobrado − gastado | La plata que de verdad entró menos la que salió. |
| Falta cobrar | plan − cobrado | Lo que separa a los dos anteriores. |

Una obra puede tener buena ganancia estimada y caja negativa sin que haya nada
mal: se compró material que todavía no se cobró. Mostrar un solo número
escondería eso. La obra de prueba lo ilustra: ganancia estimada −$1.744.000 pero
caja −$10.739.000, con $17.293.854 todavía por cobrar.

**Lo que queda abierto.** Arriba de todo, antes de los números: cuántos hitos
faltan, cuánto falta cobrar, cuántas cuotas están vencidas y cuántos rubros se
pasaron del presupuesto. Es la parte útil del botón de cerrar — conviene verlo
*antes* de dar la obra por cerrada, no después.

### Dónde vive y por qué

En el módulo **Dashboard**, no en Obras. Gastos, Cobros y Seguimiento ya
dependen de Obras; si el balance viviera ahí, Obras pasaría a depender de los
tres y quedaría un ciclo. Dashboard es el módulo que consolida, y esto es una
consolidación.

Como el Tablero, **no calcula nada propio salvo las tres restas**: el
presupuestado y el gastado por rubro los pide a Gastos, lo cobrado a Cobros y el
avance a Seguimiento.

### Los dos permisos

El endpoint exige `gastos.ver` **y** `cobros.ver`. Según la matriz del informe,
el Capataz General tiene consulta sobre Gastos pero no accede a Cobros. Si el
balance pidiera solo `gastos.ver`, vería por acá la información financiera que
la matriz le niega. Con los dos, hoy queda solo el Dueño, y si mañana se crea un
rol que administre cobros alcanza con darle los dos permisos.

### Un hallazgo de la verificación

La primera versión usaba `GastoService.estadoFinanciero`, que **lanza** cuando la
obra no tiene un definitivo aprobado. Al probar contra la base real, **seis de
las siete obras devolvían 409** al pedir su balance.

Está bien que ese método falle: quien entra a la pantalla de gastos de esa obra
tiene que enterarse de que no hay contra qué comparar. Pero el balance se
consulta de cualquier obra, y ahí la ausencia de presupuesto no es un error: es
un cero, con los gastos igual de visibles. Se agregó `estadoFinancieroOVacio`.

No se resolvió atrapando la excepción, y el motivo ya estaba documentado en el
mismo archivo para un caso anterior: `estadoFinanciero` es `@Transactional`, y al
lanzar deja la transacción marcada como rollback-only. Atraparla desde afuera no
deshace esa marca y la transacción del llamador explota al confirmar. **Una
excepción no sirve como control de flujo cruzando un límite transaccional.**

---

## 8. Las etapas que alimentan el Seguimiento

**Lo que pidió:** *"Que pueda cargar un ítem de algo que se tenga que hacer, por
ejemplo demolición de una pared, que ponga el rubro al que pertenece, cuántas
semanas o días cree que va a tardar, y que eso calcule el porcentaje que
representaría en avance en cuanto a la duración de la obra, y que pueda
ordenarlos por cuál va primero."*

### Por qué NO hay una tabla `etapa`

Esto **es** el hito que ya existe, cargado de otra manera. Hoy el hito tiene
nombre, ponderación y orden, y la ponderación se escribe a mano cuidando que el
conjunto sume 100. Lo que Ricardo pide es no escribirla: cargar cuánto dura cada
etapa y que el porcentaje salga de ahí.

Una tabla nueva significaría **dos fuentes de avance para la misma obra**, y en
algún momento se contradirían: la sección de etapas diría 60% y la de
seguimiento 45%. Duplicar el concepto es exactamente lo que el sistema viene a
evitar.

`V19` le agrega al hito dos columnas, las dos **nullable**: `id_rubro` (FK a
rubro) y `duracion_dias`. Nullable porque los hitos ya cargados no las tienen, y
obligarlos retroactivamente sería inventarles un dato.

### El reparto, y el centavo que sobra

`PUT /api/obras/{id}/etapas` recibe las etapas con su duración y reparte 100
puntos en proporción. Tres etapas de 5, 30 y 15 días dan 10%, 60% y 30%.

El problema fino: **tres etapas de un día dan 33,33 cada una y suman 99,99.** La
regla del módulo exige que la suma sea exactamente 100 —con 99,99 el avance
nunca llegaría a completo— así que el centésimo que falta hay que ponerlo en
algún lado.

Se lo suma a la etapa **más larga**. Podría ir a la primera o a la última, pero
en la más larga es donde menos se nota: sumarle un centésimo a una etapa de 45
días la distorsiona muchísimo menos que a una de un día. Con duraciones iguales
gana la primera, que es estable y no depende del orden en que llegaron.

### Días o semanas

La pantalla deja elegir la unidad y convierte a días antes de mandar, porque **la
base guarda días**. Mezclar unidades en la tabla obligaría a convertir en cada
consulta, y tarde o temprano alguien sumaría semanas con días.

El porcentaje que se ve al lado de cada etapa mientras se escribe es una
**previa**: el reparto que manda lo hace el servidor, que además se ocupa del
redondeo.

### Dos formas de cargar, no una con un campo opcional

`configurarEtapas` es un endpoint aparte de `configurarHitos`, y la pantalla
tiene dos botones: "Cargar etapas" y "Definir por %". Son dos formas
excluyentes: o se escriben las ponderaciones o se derivan. Un solo endpoint que
aceptara las dos cosas tendría que decidir cuál gana cuando llegan ambas, y esa
decisión no la puede tomar el servidor sin adivinar.

La carga por porcentaje se conserva para quien ya tiene los pesos decididos, y
porque las plantillas de hitos siguen funcionando así.

### Lo que se reutiliza

Las reglas del módulo se aplican igual: la obra tiene que estar en ejecución, no
puede haber dos etapas con el mismo nombre ni con el mismo orden, y **no se
redefine el plan si ya hay hitos completados** (borraría el registro histórico).
Los métodos que las comprueban se extrajeron para que vivan en un solo lugar.

### Verificación

**Tests (16 nuevos entre los dos pedidos, 339 en total):** el reparto por
duración, que cierre en 100 cuando no divide exacto, que el sobrante vaya a la
más larga, que guarde el rubro, que rechace un rubro inactivo, que no pise hitos
completados, que no admita nombres repetidos; y del lado del cierre, las tres
restas, el margen sin presupuesto, la lista de pendientes y la confirmación de
hitos.

**Contra el backend real:** las siete obras devuelven su balance y las tres
restas cierran contra sus propios insumos en todas. En etapas: 5 + 30 + 15 días
→ 10% / 60% / 30%; tres etapas de un día → 33,34 + 33,33 + 33,33 = 100;
completar la etapa de 30 días da 60% de avance; duración cero → 400; rubro
inexistente → 404; rubro inactivo → 409. La obra de prueba se dejó exactamente
como estaba (5 hitos, 2 completados, 45% de avance).

**En el navegador:** el balance con sus tres tarjetas y los pendientes, el
acceso desde la ficha de la obra, y el formulario de etapas recalculando el
reparto con cada tecla (5 + 15 días → 25% / 75%).

Dos cosas salieron de mirar las capturas, no de los tests: el monto del
pendiente salía sin separador de miles (`$ 17293854`), y el color de las tres
tarjetas no se veía porque el borde chocaba con el del bloque `.blueprint`. El
color pasó a la cifra, que además es lo que uno mira.
