# Módulo 04 — Presupuestación · Parte A: catálogo de rubros y subrubros

**Fecha de desarrollo:** 19/08/2026
**Depende de los módulos:** ninguno
**Referencia en el informe:** `docs/informe-sigco.md`, sección "Módulo 4 – Presupuestación"

---

## 1. Por qué el módulo se partió en dos

Presupuestación es el módulo más grande del sistema: cubre tres instancias de
presupuesto (cotización inicial, anteproyecto y definitivo), versionado entre
ellas, cuatro estados, plan de pago, generación de PDF y el catálogo de rubros.

El corte lo sugiere el propio informe. El **paso 1 de su circuito** es mantener
el catálogo, y aclara: *"Este paso no se repite por cada obra, solo se realiza
cuando surge la necesidad de sumar un rubro nuevo"*. Es decir, el catálogo es
una tarea de mantenimiento independiente, no parte de armar un presupuesto.

| Parte | Contenido | Estado |
| --- | --- | --- |
| **A** | Catálogo de rubros y subrubros | **Terminada** |
| **B** | Presupuestos: tres instancias, versionado, estados, plan de pago, PDF | Pendiente |

La Parte A es prerrequisito de la B: no se puede cargar un ítem de presupuesto
sin un rubro al que asignarlo.

---

## 2. Qué resuelve esta parte

El catálogo es la **clasificación compartida de todo el sistema**, no solo de
Presupuestación:

- **Presupuestación** agrupa los ítems por rubro y subrubro.
- **Gastos** clasifica cada gasto por rubro, y es lo que permite compararlo
  contra lo presupuestado.
- **Materiales** asigna cada material a un rubro.

Que exista **una sola lista** es justamente lo que hace posible el semáforo de
desvíos: si cada módulo tuviera la suya, los nombres no coincidirían y la
comparación entre presupuestado y gastado no cerraría. El relevamiento describe
exactamente ese problema hoy, con nombres escritos a mano e inconsistentes entre
obras.

---

## 3. Modelo de datos

| Tabla | PK | FK | Descripción |
| --- | --- | --- | --- |
| `rubro` | `id_rubro` | — | Clasificación de trabajos |
| `subrubro` | `id_subrubro` | `id_rubro` → `rubro` | Subdivisión de un rubro |

**Migración Flyway:** `V3__rubro_subrubro.sql`

### Los índices únicos son sobre `LOWER(nombre)`, no sobre la columna

```sql
CREATE UNIQUE INDEX ux_rubro_nombre_lower ON rubro (LOWER(nombre_rubro));
```

Un `UNIQUE` común distingue mayúsculas, así que dejaría pasar "Albañilería" y
"albañilería" como dos rubros distintos. Eso es exactamente el duplicado que el
informe quiere evitar, porque partiría el gasto de un mismo trabajo entre dos
etiquetas.

### La unicidad del subrubro es *por rubro*, no global

```sql
CREATE UNIQUE INDEX ux_subrubro_nombre_por_rubro
    ON subrubro (id_rubro, LOWER(nombre_subrubro));
```

"Demolición" puede existir en Albañilería y también en Plomería: son trabajos
diferentes. Lo que no puede haber es dos "Demolición" dentro del mismo rubro.

**Esto es una extensión del informe**, que pide unicidad solo para los rubros.
Se aplicó el mismo razonamiento que da para ellos: dos subrubros iguales dentro
de un rubro son un error de carga y dividirían el gasto de un mismo trabajo.

---

## 4. Entidades JPA

`Rubro` **sí** declara la colección `@OneToMany` de sus subrubros, a diferencia
de lo que se hizo en `Cliente` con sus obras. Son casos distintos:

| | `Cliente.obras` | `Rubro.subrubros` |
| --- | --- | --- |
| Tamaño | crece sin límite | corta y acotada |
| Qué se necesita | solo contarlas | mostrarlas todas |
| Decisión | consulta agrupada, sin colección | colección con `JOIN FETCH` |

La regla no es "nunca usar `@OneToMany`", sino no cargar colecciones que no se
van a usar.

---

## 5. Endpoints REST

| Método | Ruta | Qué hace |
| --- | --- | --- |
| GET | `/api/rubros` | Catálogo completo, cada rubro con sus subrubros anidados |
| POST | `/api/rubros` | Alta de rubro |
| PUT | `/api/rubros/{id}` | Renombrar |
| PATCH | `/api/rubros/{id}/estado` | Activar / desactivar |
| POST | `/api/rubros/{idRubro}/subrubros` | Alta de subrubro dentro de su rubro |
| PUT | `/api/subrubros/{id}` | Renombrar |
| PATCH | `/api/subrubros/{id}/estado` | Activar / desactivar |
| GET | `/api/subrubros?rubro={id}` | Solo los disponibles para un presupuesto nuevo |

El subrubro se crea colgando de la dirección de su rubro porque no existe fuera
de él, pero después se lo referencia por su propio identificador: es más corto y
hace imposible moverlo de rubro por accidente al editarlo.

**Sin DELETE**, por tercera vez en el sistema. Acá el motivo es especialmente
claro: un rubro usado en un presupuesto no puede borrarse sin romper ese
presupuesto. El informe resuelve el caso desactivándolo, para que no aparezca en
las cargas nuevas sin afectar las existentes.

---

## 6. Validaciones y reglas

| Regla | Dónde se valida | Cómo |
| --- | --- | --- |
| Nombre de rubro obligatorio | DTO + base | `@NotBlank` + `NOT NULL` |
| No dos rubros con el mismo nombre | Servicio + base | Comprobación previa + índice único sobre `LOWER` |
| No dos subrubros iguales dentro de un rubro | Servicio + base | Ídem, índice compuesto |
| Un subrubro pertenece a un único rubro | Base + diseño de la API | FK `NOT NULL`; el rubro no viaja en la edición |
| Un rubro no se elimina, se desactiva | Diseño de la API | No existe DELETE |
| Un subrubro no se ofrece si su rubro está inactivo | Repositorio | La consulta exige ambos activos |

La comprobación de duplicados existe **además** del índice único de la base. No
es redundante: la base garantiza que el dato nunca entre mal, y la comprobación
previa permite devolver *"Ya existe un rubro llamado Albañilería"* en lugar de
un error de restricción que el usuario no entendería.

### Desactivar un rubro no toca a sus subrubros

Al desactivar un rubro, sus subrubros **conservan su estado**. Lo que ocurre es
que la consulta de disponibles exige que el rubro también esté activo, así que
dejan de ofrecerse solos.

La ventaja es que la información no se pierde: si el rubro se reactiva, cada
subrubro vuelve con el estado que tenía. Propagar la baja obligaría a recordar
cuáles estaban desactivados de antes.

La contracara es un caso que había que resolver: activar un subrubro cuyo rubro
está inactivo dejaría un dato incoherente —marcado como disponible pero nunca
ofrecido—. El servicio lo rechaza con un `409` que dice qué hacer:
*"Active primero el rubro"*.

---

## 7. Pantalla

| Pantalla | Ruta |
| --- | --- |
| Catálogo de rubros | `/presupuestos/catalogo` |

Se muestra como un **árbol de dos niveles**, porque es como se lo piensa:
"Albañilería, y adentro Demolición y Contrapisos". Anidar los subrubros en la
respuesta de la API evita que la pantalla haga una segunda llamada y después
cruce los resultados por identificador.

Las cuatro operaciones —alta y renombrado, de rubro y de subrubro— comparten un
único formulario. Todas piden lo mismo, un nombre, así que cuatro modales casi
idénticos solo darían más lugares donde equivocarse.

No se agregó una entrada nueva al menú: el catálogo pertenece a Presupuestación
y se llega desde ahí.

---

## 8. Tests

**62 tests, 0 fallos** (`mvnw test`). De ellos, 20 son de este módulo:

| Clase | Cantidad | Qué verifica |
| --- | --- | --- |
| `CatalogoServiceTest` | 12 | Reglas de negocio, con Mockito |
| `CatalogoRepositoryTest` | 8 | Consultas e índices, contra PostgreSQL real |

### Dos errores que los tests detectaron

**1. Los subrubros volvían en orden de inserción, no alfabético.**

La entidad tenía `@OrderBy("nombreSubrubro ASC")`, pero esa anotación **no se
aplica cuando la colección se trae con `JOIN FETCH`**: en ese caso Hibernate la
llena en el orden en que vienen las filas de la consulta. Solo tiene efecto
cuando la colección se carga por separado. La corrección fue ordenar en la
propia consulta.

**2. El test no estaba probando la consulta.**

Corregido el punto anterior, el test seguía fallando. La causa era más de fondo:
dentro de una misma transacción, Hibernate devuelve las entidades que ya tiene
en memoria. Como los datos de prueba se cargaban en el `@BeforeEach`, la consulta
devolvía **esas mismas instancias**, con la colección tal como la había armado el
código Java, y no lo que había en la base.

Es decir: el test pasaba o fallaba según el orden en que se escribieran los datos
de prueba, no según lo que hiciera la consulta.

La corrección fue llamar a `flush()` y `clear()` sobre el `EntityManager` al
terminar de preparar los datos, forzando a que todo se escriba y se vuelva a leer
desde la base — que es lo que ocurre en producción, donde cada petición abre su
propia transacción. Como consecuencia, los tests guardan los identificadores y
vuelven a buscar la entidad cuando necesitan modificarla.

**Es un error que vale la pena tener presente para todos los `@DataJpaTest` que
vengan:** sin limpiar el contexto de persistencia, se está probando el grafo de
objetos en memoria y no la consulta.

### Verificación manual

| Caso | Resultado |
| --- | --- |
| Alta de tres rubros con acentos | `201`, se guardan y devuelven correctamente |
| "ALBAÑILERÍA" con "Albañilería" ya existente | `409` "Ya existe un rubro llamado Albañilería" |
| "Demolición" repetido en el mismo rubro | `409` |
| "Demolición" en otro rubro | `201` |
| Subrubros disponibles antes de desactivar | 5 |
| Desactivar el rubro Plomería | `200` |
| Subrubros disponibles después | 3 — los dos de Plomería salen solos |
| Activar un subrubro de un rubro inactivo | `409` "Active primero el rubro" |

---

## 9. Decisiones tomadas

**Un solo servicio para rubros y subrubros.** No son dos cosas independientes:
un subrubro no existe fuera de su rubro y las reglas de uno hablan del otro.
Separarlos habría obligado a que un servicio llame al otro para casi todo.

**El catálogo no viene precargado.** Sería fácil agregar una migración con
rubros de ejemplo, pero cuáles usa Granica es un dato del negocio que
corresponde definir con Ricardo, no inventar. La pantalla vacía lo dice
explícitamente y sugiere los que menciona el informe.

---

## 10. Pendientes

| Pendiente | Se resuelve en |
| --- | --- |
| Impedir desactivar un rubro sin avisar que está en uso en presupuestos | Parte B |
| Que al cargar un ítem no se pueda elegir un subrubro de otro rubro | Parte B |
| Cargar el catálogo real de Granica | Con Ricardo, antes de la puesta en marcha |
| Restringir el módulo al rol Dueño | Accesos |
