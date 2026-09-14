# Módulo 03 — Obras

**Fecha de desarrollo:** 18/08/2026
**Depende de los módulos:** Clientes
**Referencia en el informe:** `docs/informe-sigco.md`, sección "Módulo 1 – Obras"

Entidad núcleo del sistema. Presupuestación, Gastos, Cobros, Seguimiento y
Personal necesitan una obra existente para operar, así que este módulo es el
punto de entrada obligatorio del resto.

Además cierra tres pendientes que había dejado el módulo Clientes.

---

## 1. Qué resuelve este módulo

Hoy la información de cada proyecto queda repartida entre carpetas de la
computadora del dueño y mensajes sueltos. Cuando necesita los datos de una obra
—la dirección exacta, cuándo arrancó, qué se acordó con el cliente— tiene que
recordar dónde los guardó.

El módulo administra el ciclo de vida completo de cada obra, desde que se
registra el primer contacto hasta que queda finalizada o cancelada, y concentra
los datos maestros que después consumen todos los demás módulos.

---

## 2. Modelo de datos

| Tabla | PK | FK | Descripción |
| --- | --- | --- | --- |
| `obra` | `id_obra` | `id_cliente` → `cliente` | Proyectos que ejecuta la empresa |

**Migración Flyway:** `V2__obra.sql`

### Decisiones de modelado

**`id_cliente` es `NOT NULL` además de clave foránea.** La primera regla del
informe para este módulo es que no puede existir una obra sin cliente. Al
declararlo `NOT NULL` en la base, esa regla no depende de que la aplicación se
acuerde de validarla.

**El motivo de cancelación se exige desde la base:**

```sql
CONSTRAINT ck_obra_motivo_cancelacion
    CHECK (estado <> 'Cancelada' OR motivo_cancelacion IS NOT NULL)
```

Es una regla condicional —el campo es obligatorio solo en un estado— y aun así
se puede expresar como restricción. El informe pide ese motivo para poder
entender más adelante por qué un proyecto no se concretó.

**Coherencia de fechas.** Una restricción impide que la fecha estimada de fin
sea anterior a la de inicio. La obra no puede terminar antes de empezar.

**Dos índices:** sobre `id_cliente`, que usa la ficha del cliente para traer sus
obras, y sobre `estado`, que usa el filtro del listado.

---

## 3. Entidad JPA

La relación hacia cliente es `@ManyToOne(fetch = FetchType.LAZY)`. Con `EAGER`,
listar cincuenta obras dispararía cincuenta consultas extra para resolver el
nombre de cada cliente sin que nadie se entere.

Como la aplicación corre con `spring.jpa.open-in-view=false`, si una pantalla
necesitara el cliente y no se lo hubiera cargado, el error aparece en
desarrollo en lugar de convertirse en consultas invisibles. Por eso la consulta
del listado usa `JOIN FETCH`: trae la obra y su cliente **en una sola
consulta**.

Igual que `Cliente`, la entidad no expone setters sueltos: tiene
`actualizarDatos`, `registrarInicioReal`, `pasarAEjecucion`, `finalizar` y
`cancelar`. Cada transición es una operación con nombre propio.

---

## 4. Endpoints REST

| Método | Ruta | Qué hace | Devuelve |
| --- | --- | --- | --- |
| GET | `/api/obras` | Listado con filtros `cliente`, `tipoObra`, `estado`, `desde`, `hasta`, `busqueda` | `200` + lista |
| GET | `/api/obras/{id}` | Una obra | `200` · `404` |
| POST | `/api/obras` | Alta | `201` + `Location` |
| PUT | `/api/obras/{id}` | Datos maestros | `200` · `404` · `409` |
| PATCH | `/api/obras/{id}/estado` | Avanza el ciclo de vida | `200` · `404` · `409` |

**Sin DELETE**, igual que en Clientes: el informe establece que una obra no se
elimina, solo se cancela, para no perder la trazabilidad de los presupuestos,
gastos y cobros que ya pueda tener asociados.

### Dos objetos distintos para alta y edición

`ObraSolicitud` (alta) tiene cliente y tipo de obra. `ObraEdicion` no los tiene.

No es una omisión: es cómo se implementa la regla. El cliente no se cambia
—la obra pertenece a quien la encargó— y el tipo de obra queda bloqueado porque
determina el circuito de Presupuestación. Al no estar los campos en el objeto,
**no hay forma de enviarlos**: la regla no depende de que alguien se acuerde de
validarla.

---

## 5. Ciclo de vida

```
En presupuestación  ──►  En ejecución  ──►  Finalizada
        │                      │
        └──────────────────────┴──►  Cancelada
```

Finalizada y Cancelada son terminales: una obra en esos estados ya no admite
cambios.

| Regla del informe | Dónde se valida | Cómo |
| --- | --- | --- |
| No hay obra sin cliente | Base + servicio | `NOT NULL` + `404` si el cliente no existe |
| Dirección y tipo de obra obligatorios | DTO + base | `@NotBlank` + `NOT NULL` |
| Tipo de obra bloqueado para edición | Diseño de la API | No está en `ObraEdicion` |
| Fecha de inicio recién con el presupuesto aprobado | Servicio | Se rechaza mientras la obra siga "En presupuestación" |
| Motivo obligatorio al cancelar | Servicio + base | `409` + `CHECK` |
| Una obra no se elimina, se cancela | Diseño de la API | No existe DELETE |
| Toda obra nace "En presupuestación" | Entidad | Lo fija el constructor |

### La aproximación de la fecha de inicio

El informe dice que `fecha_inicio_real` no puede cargarse hasta que el
presupuesto definitivo esté aprobado. Presupuestación todavía no existe, así que
no hay contra qué comprobarlo.

La solución no fue omitir la regla ni dejarla pasar: se comprueba contra el
estado de la obra. Mientras siga "En presupuestación", ese presupuesto todavía
no se aprobó, así que la fecha se rechaza. Es una aproximación correcta con lo
que hay hoy, y queda marcada con un `// TODO` para comprobarla directamente
cuando exista el módulo.

### Cambios automáticos, todavía manuales

El informe indica que la obra debería pasar sola a "En ejecución" al aprobarse
el presupuesto definitivo, y a "Finalizada" al completarse el último hito. Esos
disparadores pertenecen a Presupuestación y a Seguimiento. Por ahora las
transiciones se hacen a mano desde la pantalla, con las validaciones ya puestas,
y quedan marcadas con `// TODO` en el servicio.

---

## 6. Pantallas

| Pantalla | Ruta | Descripción |
| --- | --- | --- |
| Listado de Obras | `/obras` | Tabla con buscador y filtros por estado y tipo |
| Alta / edición | modal | Incluye alta rápida de cliente |
| Cambio de estado | modal | Ofrece solo las transiciones válidas |

Las obras **en ejecución aparecen primero y con fondo destacado**, como pide el
informe. El orden lo resuelve la consulta del backend con un `CASE` sobre el
estado, no el navegador.

El modal de cambio de estado ofrece únicamente los destinos posibles desde el
estado actual, y pide lo que corresponde a cada uno: la fecha de inicio al pasar
a ejecución, el motivo al cancelar. Es una copia de la regla del backend para
que el usuario no intente algo que va a fallar; la que manda sigue siendo la del
servidor.

### La pantalla del diseño quedó como referencia

`/obras` era, hasta ahora, la pantalla de detalle de obra del diseño importado,
con datos de muestra. Esa pantalla consolida gastos por rubro e hitos, que
producen módulos que todavía no existen. Se movió a `/vista-diseno/obra`,
fuera de la navegación, como referencia de cómo va a quedar la ficha cuando esos
módulos estén.

---

## 7. Pendientes de Clientes que este módulo cierra

| Pendiente | Cómo se resolvió |
| --- | --- |
| Cantidad de obras en el listado de clientes | `ClienteRespuesta.cantidadObras`, alimentado por una consulta agrupada |
| Alta rápida de cliente desde el formulario de obra | Botón "Es nuevo" en el alta de obra, que usa el mismo `POST /api/clientes` |
| Historial de obras de un cliente | `GET /api/obras?cliente={id}` ya lo resuelve; falta la pantalla de la ficha |

Sobre el conteo: se resuelve con **una sola consulta agrupada** para toda la
lista (`SELECT id_cliente, COUNT(*) ... GROUP BY id_cliente`), no preguntando
cliente por cliente. Con cien clientes, lo segundo serían cien consultas extra.

Esto introduce la única dependencia de Clientes hacia otro módulo, y es de solo
lectura: `ClienteService` consulta `ObraRepository`.

---

## 8. Tests

**42 tests, 0 fallos** (`mvnw test`).

| Clase | Cantidad | Qué verifica |
| --- | --- | --- |
| `ObraServiceTest` | 14 | Ciclo de vida, alta, edición y cancelación |
| `ObraRepositoryTest` | 7 | Filtros y orden, contra PostgreSQL real |
| `ClienteServiceTest` | 8 | Sin cambios de alcance |
| `ClienteRepositoryTest` | 6 | Sin cambios de alcance |
| `ClienteControllerTest` | 6 | Sin cambios de alcance |
| `SigcoBackendApplicationTests` | 1 | La aplicación levanta |

`ObraServiceTest` usa clases anidadas (`@Nested`) para agrupar por tema: Alta,
Ciclo de vida, Cancelación y Edición. Con catorce casos, la agrupación hace que
el informe de resultados se lea como la lista de reglas del módulo.

Uno de los tests del repositorio verifica algo específico: que los valores con
acento del Diccionario (`"En presupuestación"`, `"Construcción"`) se guardan y
recuperan correctamente. Es donde se manifestaría un problema de codificación
entre el archivo SQL, el driver y la base.

### Verificación manual del ciclo de vida

| Caso | Resultado |
| --- | --- |
| Alta de dos obras | `201`, ambas "En presupuestación" |
| Alta con cliente inexistente | `404` |
| Finalizar una obra en presupuestación | `409` "primero tiene que ejecutarse" |
| Cancelar sin motivo | `409` "hay que indicar el motivo" |
| Pasar a ejecución con fecha de inicio | `200`, fecha registrada |
| Cancelar con motivo | `200`, el registro se conserva |
| Cambiar una obra ya cancelada | `409` "ya no admite cambios" |

---

## 9. Un error importante detectado y corregido

Durante el desarrollo apareció un fallo que **también afectaba al módulo
Clientes ya entregado**, y que es el hallazgo más relevante de este módulo.

### El síntoma

```
ERROR: no existe la función lower(bytea)
ERROR: no se pudo determinar el tipo del parámetro $7
```

### La causa

Las consultas usaban el patrón habitual para filtros opcionales:

```sql
WHERE (:busqueda IS NULL OR LOWER(nombre) LIKE LOWER('%' || :busqueda || '%'))
```

Cuando un parámetro aparece únicamente dentro de `? IS NULL`, PostgreSQL no
tiene de dónde deducir su tipo. Le asigna uno por defecto (`bytea`) y después
falla al intentar usarlo como texto o como fecha.

### Por qué era grave

**No fallaba siempre.** La consulta de Clientes había funcionado en todas las
pruebas anteriores, incluidas las capturas de pantalla del módulo entregado. El
resultado depende de cómo PostgreSQL resuelva la inferencia en cada conexión, y
eso cambia según qué consulta se ejecutó primero. Es el peor tipo de error: pasa
los tests y rompe en producción.

### La corrección

Ningún parámetro de las consultas de filtro puede llegar en `null`. La ausencia
de filtro se expresa con valores neutros:

| Tipo de filtro | Valor de "sin filtro" |
| --- | --- |
| Texto (búsqueda, estado, tipo) | cadena vacía `''` |
| Identificador de cliente | `0` (los `BIGSERIAL` arrancan en uno) |
| Rango de fechas | rango amplio, del 2000 al 2999 |

Con valores neutros todos los parámetros viajan tipados y la consulta es
estable. La búsqueda por texto no necesita condición aparte: `LIKE '%%'`
coincide con todo.

Se corrigieron las dos consultas —Clientes y Obras— y el test
`ClienteServiceTest.filtroVacioNoFiltra` ahora verifica explícitamente que los
filtros ausentes viajan como cadena vacía y nunca como null.

Si más adelante los filtros opcionales crecen mucho, la alternativa es armar la
consulta con Specifications, que agrega solo las condiciones que aplican en
lugar de neutralizarlas. Queda anotado en el código.

---

## 10. Desvíos respecto del informe

- **La ficha de obra no se implementó.** El informe la describe como una vista
  que centraliza accesos a presupuestos, avance, gastos, cobros y personal. Hoy
  no habría nada que mostrar: ninguno de esos módulos existe.
- **El historial de estados tampoco.** Requiere una tabla propia que registre
  cada cambio con su fecha, y no está en el Diccionario de Datos. Antes de
  agregarla hay que decidir si se resuelve así o con el registro de auditoría
  del módulo Accesos, que ya guarda quién hizo qué y cuándo.

---

## 11. Pendientes

| Pendiente | Se resuelve en |
| --- | --- |
| Ficha de obra con accesos a los demás módulos | **Resuelto el 14/09/2026**: `/obras/{id}`, ver abajo |
| Historial de estados | A definir junto con Accesos |
| Paso automático a "En ejecución" al aprobar el presupuesto | Presupuestación |
| Paso automático a "Finalizada" al completar el último hito | Seguimiento de Obras |
| Permitir cambiar el tipo de obra mientras no haya presupuesto | Presupuestación |
| Impedir cancelar una obra con presupuesto definitivo aprobado | Presupuestación |
| Comprobar la fecha de inicio contra el presupuesto y no contra el estado | Presupuestación |
| Restringir el módulo por rol | Accesos |


---

## La ficha de obra (14/09/2026)

`/obras/{id}` es el lugar donde convergen todos los módulos para **una** obra.
Hasta que existió, ver una obra completa era recorrer seis pantallas: Presupuestos
para el total, Gastos para el desvío, Avance para los hitos, Cobranzas para el
saldo y Pedidos para los materiales.

Reemplaza a `/vista-diseno/obra`, que era la pantalla del diseño con datos de
muestra. Se conservó el diseño entero —la torta, las barras con la marca del
tope, la línea de hitos— y se le conectaron los datos reales.

### Lo que cruza

Las tres informaciones que la empresa hoy tiene separadas:

| Qué | De dónde |
| --- | --- |
| En qué rubros se gastó (la torta) | `GET /api/obras/{id}/estado-financiero` |
| Gastado contra presupuestado por rubro (las barras) | ídem |
| Cuánto avanzó de verdad (la línea de hitos) | `GET /api/obras/{id}/avance` |
| Cuánto falta cobrar | `GET /api/obras/{id}/cobros` |
| Cuántos pedidos tiene | `GET /api/pedidos?obra={id}` |

Ese cruce es lo que permite detectar la situación que el relevamiento marca como
más costosa: **una obra que gastó mucho sin avanzar**. Cuando pasa, la pantalla
lo dice arriba de todo con una frase, no con un color:

> Esta obra gastó el **101%** del presupuesto y avanzó el **70%**. Va 31 puntos
> más rápido gastando que construyendo.

### Cinco endpoints y no uno

Mismo criterio que el Tablero: cada número lo calcula el módulo que es su dueño.
Un endpoint nuevo que devolviera todo junto tendría que recalcularlos, y el día
que cambie un umbral habría dos lugares para cambiarlo. Las cinco llamadas salen
en paralelo, así que la pantalla tarda lo que tarda la más lenta.

Cada llamada se hace **solo si el usuario tiene el permiso**: un capataz general
que abra la ficha no ve la parte financiera, y su pantalla no se llena de
errores por peticiones que iban a ser rechazadas.

### Detalles de presentación que son decisiones

- **La escala de las barras es común a todos los rubros.** Si cada una se midiera
  contra sí misma, un rubro chico excedido se vería igual de largo que uno
  grande y la comparación entre rubros dejaría de significar algo.
- **Los colores de la torta salen de la rampa de azules, no del semáforo.**
  Verde, amarillo y rojo significan algo concreto en este sistema —dentro, al
  límite, excedido— y usarlos para distinguir rubros los vaciaría de significado.
- **El estado de cada rubro va escrito además de en color**, para quien no
  distingue verde de rojo.
- **Los accesos al pie solo aparecen si el usuario puede entrar** a ese módulo.
