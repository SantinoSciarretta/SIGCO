# Módulo 02 — Clientes

**Fecha de desarrollo:** 18/08/2026
**Depende de los módulos:** ninguno (es el primero)
**Referencia en el informe:** `docs/informe-sigco.md`, sección "Módulo 3 – Clientes"

Primer módulo desarrollado de punta a punta. Además de resolver su propia
funcionalidad, asienta el patrón que van a repetir los trece restantes:
migración SQL → entidad JPA → repositorio → servicio → controlador REST →
pantalla React, con tests en cada capa.

---

## 1. Qué resuelve este módulo

Hoy Granica SRL no tiene una lista unificada de clientes. Los datos de contacto
quedan desparramados entre las carpetas de obra de la computadora del dueño, así
que para retomar el contacto con alguien hay que acordarse de en qué proyecto
trabajó y buscar la carpeta. Cuando un cliente vuelve a llamar después de dos
años, encontrarlo depende de la memoria.

El módulo centraliza a todas las personas y empresas que contratan a la empresa,
con sus datos de contacto y el origen de la recomendación. Ese último dato no es
decorativo: **todos los clientes de Granica llegan por referido**, y registrar de
dónde vienen permite saber qué canal trae más trabajo.

---

## 2. Modelo de datos

| Tabla | PK | FK | Descripción |
| --- | --- | --- | --- |
| `cliente` | `id_cliente` | — | Personas y empresas que contratan a la empresa |

**Migración Flyway:** `V1__cliente.sql` — la primera del sistema.

Es la única tabla que no referencia a ninguna otra: la clave foránea de la
relación cliente–obras vive del lado de `obra`, así que ésta puede crearse
primero. Por eso Clientes encabeza el orden de desarrollo.

### Decisiones de modelado

**Los conjuntos cerrados se validan también en la base.** `estado` y
`origen_recomendacion` tienen restricciones `CHECK` en PostgreSQL además de la
validación de la aplicación. El motivo: la validación de la aplicación protege
lo que entra por la API, pero no lo que entra por una carga manual, un script de
migración de datos o una consulta directa. La restricción de la base cubre todos
los caminos.

**El índice es sobre `LOWER(nombre_apellido)`, no sobre la columna.** La búsqueda
del listado no distingue mayúsculas, así que la consulta compara
`LOWER(nombre_apellido)`. Un índice sobre la columna cruda no serviría para esa
comparación: PostgreSQL solo usa el índice si está construido sobre la misma
expresión que aparece en la consulta.

**Un solo campo obligatorio.** Solo `nombre_apellido`, `estado` y `fecha_alta`
son `NOT NULL`, y los dos últimos los pone el sistema. El informe es explícito:
cuando llega la consulta de un cliente nuevo no siempre se tienen el teléfono ni
el correo, y exigirlos haría que el dato se cargue mal o no se cargue.

---

## 3. Entidad JPA

`Cliente` refleja la tabla campo por campo, con `@Column(name = "...")` en cada
uno porque el código Java usa camelCase y la base snake_case.

- `@GeneratedValue(strategy = GenerationType.IDENTITY)` — delega la generación
  del número a la base, que es lo que hace el tipo `BIGSERIAL` de la migración.
- No tiene relación `@OneToMany` hacia obras, y es deliberado: la cantidad de
  obras se obtiene con una consulta agrupada en lugar de cargar la colección
  entera solo para contarla (ver sección 11).

La entidad **no tiene setters sueltos**. En su lugar expone operaciones con
nombre propio: `actualizarDatos(...)`, `activar()`, `desactivar()`. La diferencia
importa: con setters, cualquier parte del código podría cambiar el estado o
pisar la fecha de alta sin que nada lo impida. Con estos métodos, el objeto
controla qué se puede modificar y qué no.

Como la aplicación corre con `spring.jpa.hibernate.ddl-auto=validate`, si esta
clase dejara de coincidir con la migración la aplicación no arrancaría e
indicaría exactamente qué campo difiere.

---

## 4. Endpoints REST

| Método | Ruta | Qué hace | Devuelve |
| --- | --- | --- | --- |
| GET | `/api/clientes` | Listado con filtros opcionales `busqueda`, `origen`, `estado` | `200` + lista |
| GET | `/api/clientes/{id}` | Un cliente | `200` · `404` si no existe |
| POST | `/api/clientes` | Alta | `201` + cabecera `Location` |
| PUT | `/api/clientes/{id}` | Edición de los datos de contacto | `200` · `404` |
| PATCH | `/api/clientes/{id}/estado` | Activar / marcar como inactivo | `200` · `404` |

### No hay DELETE, y es deliberado

El informe establece que un cliente **no se elimina, se marca como inactivo**,
para no perder la trazabilidad de sus obras anteriores. La lista de acciones del
módulo en la propuesta técnica tampoco incluye "Eliminar".

Se podría haber implementado un DELETE con la validación "solo si no tiene obras
asociadas", pero eso habría requerido dejar la comprobación stubbeada hasta que
exista el módulo Obras. Se prefirió no exponer la operación: el circuito
correcto es desactivar, y ningún endpoint permite otra cosa.

### Por qué PATCH y no PUT para el estado

`PUT` reemplaza el recurso completo; `PATCH` modifica una parte. Cambiar el
estado toca un solo campo, así que corresponde `PATCH`. Además evita que el
frontend tenga que reenviar todos los datos del cliente solo para desactivarlo,
con el riesgo de pisar un dato que otro usuario modificó mientras tanto.

---

## 5. Validaciones y reglas de negocio

| Regla del informe | Dónde se valida | Cómo |
| --- | --- | --- |
| El nombre del cliente es obligatorio | Backend (DTO) + base | `@NotBlank` + `NOT NULL` |
| El origen de la recomendación es opcional | Backend (DTO) | El campo admite nulo |
| El origen debe ser uno de los tres valores previstos | Backend (DTO) + base | `@Pattern` + `CHECK ck_cliente_origen` |
| El estado solo puede ser Activo o Inactivo | Backend (DTO) + base | `@Pattern` + `CHECK ck_cliente_estado` |
| Un cliente no se elimina, se desactiva | Backend (diseño de la API) | No existe operación de borrado |
| El alta rápida desde Obras no genera lógica duplicada | Backend (servicio) | Usa el mismo `ClienteService.crear` |
| Búsqueda por nombre y filtrado por origen | Backend (repositorio) | Consulta con filtros combinables |
| Largos máximos de cada campo | Backend (DTO) + base | `@Size` + `VARCHAR(n)` |

**El frontend no valida nada por su cuenta**, salvo marcar el campo obligatorio
con `required` para no hacer viajar un pedido que ya se sabe que va a fallar.
Cuando el servidor rechaza, el formulario muestra debajo de cada campo el
mensaje que devolvió el backend en `camposInvalidos`. La consecuencia práctica
es que el usuario ve exactamente la regla que se aplicó, y no una copia escrita
en el navegador que podría quedar desactualizada.

### Normalización de textos

El servicio convierte a `null` los textos vacíos o en blanco antes de guardar.
"Sin dato" y "dato vacío" son la misma cosa, y guardarlos de dos maneras
distintas complica después toda consulta que pregunte si un campo está cargado.
Lo mismo aplica a los filtros: si el usuario borra lo que escribió en el
buscador, espera ver todos los clientes, no ninguno.

---

## 6. Pantallas

| Pantalla | Ruta | Descripción |
| --- | --- | --- |
| Listado de Clientes | `/clientes` | Tabla con buscador por nombre y filtros por origen y estado |
| Alta / edición | modal sobre `/clientes` | Mismo formulario para las dos operaciones |

Componentes del sistema de diseño reutilizados: `Blueprint`, la tabla `.table`,
las utilidades `.kicker` y `.scroll-x`.

Componente nuevo, que queda disponible para el resto de los módulos:
**`Modal`** (`components/ui/Modal.jsx`). Resuelve el cierre con la tecla Escape,
el traslado del foco del teclado a la ventana y el bloqueo del desplazamiento de
la página de fondo.

El buscador espera 300 ms antes de consultar. Sin esa pausa, escribir "Ferrari"
dispararía siete consultas al servidor, una por letra.

### Entrada en la navegación

El diseño importado no contemplaba Clientes en la barra superior. Se agregó
**solo este módulo**, porque es el que se desarrolló; los ocho que siguen sin
entrada se suman cuando le toque a cada uno, no antes.

---

## 7. Tests

**21 tests, 0 fallos** (`mvnw test`).

| Clase | Qué verifica | Cómo |
| --- | --- | --- |
| `ClienteServiceTest` (8) | Reglas de negocio | Mockito, sin base de datos |
| `ClienteRepositoryTest` (6) | Consulta del listado y sus filtros | PostgreSQL real (`sigco_test`) |
| `ClienteControllerTest` (6) | Contrato HTTP y validación | MockMvc, servicio simulado |
| `SigcoBackendApplicationTests` (1) | La aplicación levanta | Contexto completo |

Casos cubiertos: alta que nace Activa con fecha, normalización de textos,
404 al consultar y al editar inexistentes, la edición no toca estado ni fecha de
alta, desactivar conserva el registro, reactivar, filtro vacío que no filtra,
búsqueda parcial sin distinguir mayúsculas, filtros combinados, `201` con
`Location`, `400` con el detalle por campo, y que la validación corta **antes**
de llegar al servicio.

### Por qué los tests del repositorio corren contra PostgreSQL real

Se creó una base aparte, `sigco_test`, en lugar de usar una base en memoria. La
consulta usa `LOWER` y `LIKE`, la migración usa `BIGSERIAL` y restricciones
`CHECK`: una base distinta podría comportarse distinto y dar una prueba que pasa
mientras el sistema real falla. Cada test corre dentro de una transacción que se
deshace al terminar, así que no deja datos.

Estos tests verifican además algo que ninguna aserción escribe explícitamente:
como la aplicación corre con `ddl-auto=validate`, si la entidad dejara de
coincidir con la migración el contexto no levantaría y todos fallarían. Es un
control automático de que el código sigue fiel al Diccionario de Datos.

### Verificación manual de la API

| Caso | Resultado |
| --- | --- |
| Alta de tres clientes | `201` con `Location` |
| Alta sin nombre | `400` · `camposInvalidos.nombreApellido` |
| Alta con correo inválido | `400` · `camposInvalidos.emailContacto` |
| Alta con origen "Instagram" | `400` · `camposInvalidos.origenRecomendacion` |
| Búsqueda "rossi" | devuelve "Estudio Rossi Arquitectura" |
| Filtro `origen=Otro` | devuelve "Lucia Peralta" |
| `PATCH .../3/estado` a Inactivo | `200`, el registro se conserva |
| Filtros por estado | 2 activos, 1 inactivo |
| `GET /api/clientes/999` | `404` con el formato de error del sistema |

---

## 8. Decisiones tomadas y por qué

**Estados como texto y no como `enum` de Java.** El Diccionario de Datos define
`estado` como `VARCHAR(10)`. Se mantuvo `String`, validado contra un conjunto
cerrado declarado como constantes en la entidad y como `CHECK` en la base. Un
`enum` con `@Enumerated(EnumType.STRING)` daría más seguridad de tipos y mapea al
mismo `VARCHAR`; es una alternativa razonable, pero cambiar el tipo respecto del
Diccionario es una decisión que conviene tomar para los catorce módulos a la vez
y no módulo por módulo.

**Sin campo "cantidad de obras" todavía.** El informe pide mostrarlo en el
listado. No se incluyó devolviendo un cero fijo, porque sería un dato falso en el
contrato de la API. Se agrega al desarrollar Obras, que es el módulo que tiene
esa información.

**DTO desde el primer módulo, aunque acá todavía no se note.** El cliente no
tiene relaciones, así que devolver la entidad directa funcionaría igual. Se
sostuvo el patrón porque los dos problemas que evita aparecen apenas entra Obras:
los ciclos infinitos al convertir a JSON una relación de ida y vuelta, y la
exposición de campos internos.

---

## 9. Desvíos respecto del informe

- **No se implementó la ficha de cliente con historial de obras.** Es una de las
  tres vistas que pide el informe, pero muestra información que produce el
  módulo Obras. Se desarrolla junto con ese módulo.
- **No se implementó el formulario de alta rápida desde Obras.** El endpoint ya
  está preparado —es el mismo `POST /api/clientes`—, pero la pantalla desde la
  que se invoca todavía no existe.

Ambos desvíos son por dependencia, no por decisión: se resuelven al desarrollar
Obras, que es el módulo siguiente.

---

## 10. Pendientes

| Pendiente | Estado |
| --- | --- |
| Columna "cantidad de obras" en el listado | **Resuelto** en el módulo Obras |
| Formulario de alta rápida desde Obras | **Resuelto** en el módulo Obras |
| Ficha de cliente con historial de obras | **Resuelta el 14/09/2026**: `/clientes/{id}`, con las obras en tres grupos —activas, finalizadas y canceladas— como pide el informe |
| Restringir el módulo al rol Dueño | **Resuelto**: `clientes.ver` / `clientes.editar`, que solo tiene el Dueño |

---

## 11. Corrección posterior a la entrega del módulo

Al desarrollar Obras se detectó que **la consulta del listado de clientes tenía
un fallo intermitente** que no se había manifestado durante las pruebas de este
módulo.

El patrón `(:busqueda IS NULL OR LOWER(nombre) LIKE ...)` hace que PostgreSQL no
pueda deducir el tipo del parámetro cuando llega en null, le asigne `bytea` por
defecto y falle con `no existe la función lower(bytea)`. Que funcionara o no
dependía de cómo resolviera la inferencia cada conexión, así que pasó todas las
pruebas de este módulo y rompió después.

La consulta se corrigió: los filtros ausentes viajan como cadena vacía y nunca
como null. El detalle completo está en `03-obras.md`, sección 9.

La entidad `Cliente` **no** recibió la relación `@OneToMany` hacia obras: la
cantidad se resuelve con una consulta agrupada desde `ObraRepository`, que evita
cargar la colección entera solo para contarla.
