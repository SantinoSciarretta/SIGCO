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
| 7 | Botón de obra terminada para ver el balance | Pendiente |
| 8 | Etapas por obra que alimenten el Seguimiento | Pendiente |

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

## 7 y 8 — Lo que falta

### Botón de obra terminada para ver el balance

*"Que también haya un botón para poner obra terminada para ver balance."*

Hoy la obra pasa a Finalizada sola, al completarse el último hito. Falta la
acción explícita y, sobre todo, **la pantalla de balance**: presupuestado contra
gastado por rubro, cobrado contra total, y la ganancia real de la obra. Los tres
números existen por separado en Gastos, Cobros y Presupuestación; lo que falta es
juntarlos en un cierre.

### Etapas por obra que alimenten el Seguimiento

*"Que pueda cargar un ítem de algo que se tenga que hacer, por ejemplo demolición
de una pared, que ponga el rubro al que pertenece, cuántas semanas o días cree
que va a tardar, y que eso calcule el porcentaje que representaría en avance en
cuanto a la duración de la obra, y que pueda ordenarlos por cuál va primero."*

Esto es, en el fondo, **una forma distinta de cargar los hitos que ya existen**.
Hoy el hito tiene nombre, ponderación y orden, y la ponderación se escribe a
mano cuidando que sumen 100. Lo que Ricardo pide es no escribir la ponderación:
cargar cuánto dura cada etapa y que el porcentaje salga de ahí.

La dirección decidida: agregarle al hito el rubro y la duración en días, y
**derivar la ponderación de la duración** (la de cada etapa sobre el total de
días). Es menos trabajo para él y da un número más honesto: una etapa que lleva
tres semanas pesa más que una que lleva dos días, sin que nadie tenga que
estimarlo.
