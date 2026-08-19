# Módulo 06 — Materiales

**Fecha de desarrollo:** 19/08/2026
**Depende de los módulos:** Presupuestación (catálogo de rubros)
**Referencia en el informe:** `docs/informe-sigco.md`, sección "Módulo 7 – Materiales"

---

## 1. Qué resuelve

Hoy los materiales se escriben a mano en cada pedido, y el nombre cambia de una
obra a otra: "cemento", "Cemento CP40", "bolsa cemento". Cuando después se
quiere saber cuánto cemento se compró en el año, o comparar precios entre
corralones, no hay forma de agrupar: para el sistema son tres cosas distintas.

El módulo establece una **lista única** de la que eligen Presupuestación (al
cargar ítems) y Compras (al armar pedidos). Nadie escribe el nombre de un
material: lo selecciona.

---

## 2. Modelo de datos

| Tabla | PK | FK | Descripción |
| --- | --- | --- | --- |
| `material` | `id_material` | `id_rubro` → `rubro` | Catálogo único de materiales |

**Migración Flyway:** `V5__material.sql`

### El rubro sale del catálogo de Presupuestación

No hay una lista propia de rubros para materiales. El informe lo pide
explícitamente: mantener una sola clasificación es lo que permite que el gasto
en materiales se compare contra lo presupuestado para ese mismo rubro.

### La unidad de medida NO lleva restricción de conjunto cerrado

A diferencia de `estado`, que sí tiene un `CHECK`, la unidad se guarda como
texto libre.

El informe la enumera como *"Unidad / Bolsa / Metro cuadrado / Metro lineal /
Litro, **entre otras**"*. Ese "entre otras" es la clave: es una lista abierta.
Encerrarla en un `CHECK` obligaría a una migración de base de datos cada vez que
aparezca un material que se mide distinto —una membrana por rollo, un alambre
por kilo—, que es exactamente el tipo de fricción que hace que un sistema no se
use.

La pantalla ofrece las unidades habituales con un `datalist`, pero deja escribir
cualquier otra.

### El nombre es único dentro de su rubro

```sql
CREATE UNIQUE INDEX ux_material_nombre_por_rubro
    ON material (id_rubro, LOWER(nombre_material));
```

Mismo criterio que en subrubro. "Membrana" puede existir en Techos y en
Impermeabilizaciones —son productos distintos—, pero no dos veces en el mismo
rubro. El `LOWER` evita que "Cemento CP40" y "CEMENTO cp40" entren como
materiales diferentes, que es precisamente el duplicado a evitar.

---

## 3. Una diferencia con subrubro que vale la pena entender

| | Subrubro | Material |
| --- | --- | --- |
| ¿Puede cambiar de rubro? | **No** | **Sí** |

No es una inconsistencia: el informe permite explícitamente "modificar el
nombre, **el rubro** o la unidad de medida" de un material.

La razón de fondo es distinta en cada caso. Un subrubro **es parte de** su
rubro: "Demolición" dentro de Albañilería no es la misma entidad que
"Demolición" dentro de Plomería, aunque se llamen igual. En cambio, clasificar
un material en un rubro es una **decisión** que se puede haber tomado mal y se
corrige.

Eso trae una consecuencia en el código: al mover un material de rubro hay que
controlar el duplicado en el rubro **destino**, no en el de origen. Puede haber
allá otro material con ese mismo nombre. Está cubierto por un test.

---

## 4. Endpoints

| Método | Ruta | Qué hace |
| --- | --- | --- |
| GET | `/api/materiales` | Catálogo con buscador y filtros por rubro y estado |
| GET | `/api/materiales?disponibles=true` | Solo los elegibles para un presupuesto o pedido |
| GET | `/api/materiales/{id}` | Un material |
| POST | `/api/materiales` | Alta |
| PUT | `/api/materiales/{id}` | Editar nombre, rubro y unidad |
| PATCH | `/api/materiales/{id}/estado` | Activar / desactivar |

**Sin DELETE**, por cuarta vez en el sistema. Acá el motivo es directo: un
material usado en un presupuesto o un pedido no puede borrarse sin romperlos.

### Por qué dos vistas del mismo listado

- **Mantenimiento** (`GET /api/materiales`): incluye los inactivos, porque para
  administrar el catálogo hay que poder verlos y reactivarlos.
- **Disponibles** (`?disponibles=true`): solo activos y con su rubro activo. Es
  lo que van a consumir Presupuestación y Compras.

Son dos necesidades distintas del mismo dato, y mezclarlas obligaría a que cada
módulo consumidor filtrara por su cuenta —y alguno se olvidaría.

---

## 5. Validaciones y reglas

| Regla del informe | Dónde se valida | Cómo |
| --- | --- | --- |
| Nombre obligatorio | DTO + base | `@NotBlank` + `NOT NULL` |
| Unidad de medida obligatoria | DTO + base | `@NotBlank` + `NOT NULL` |
| Rubro obligatorio, del catálogo de Presupuestación | Base + servicio | FK `NOT NULL` + `404` si no existe |
| No dos materiales iguales en el mismo rubro | Servicio + base | Comprobación previa + índice único sobre `LOWER` |
| Un material no se elimina, se desactiva | Diseño de la API | No existe DELETE |
| Un material de rubro inactivo no se ofrece | Repositorio | La consulta exige ambos activos |

### Tres reglas que agregué, con su razón

**1. No se puede cargar un material en un rubro inactivo.** Quedaría cargado
pero el sistema no lo ofrecería nunca, porque la consulta de disponibles exige
que el rubro esté activo. Es un dato que nace inútil, y conviene avisar.

**2. No se puede activar un material cuyo rubro está inactivo.** Mismo
razonamiento; el mensaje dice qué hacer: *"Active primero el rubro"*.

**3. Pero sí se puede editar un material que quedó bajo un rubro inactivo**,
mientras no se lo mueva de rubro. Corregirle el nombre a algo que quedó mal
cargado tiene que seguir siendo posible. La restricción es para mover un
material **hacia** un rubro inactivo, no para dejarlo donde ya está.

Las tres son extensiones del informe, con la misma lógica que se aplicó a los
subrubros en la Parte A de Presupuestación.

---

## 6. Pantalla

| Pantalla | Ruta |
| --- | --- |
| Catálogo de materiales | `/materiales` |

Se agregó **Materiales** al menú: es un módulo propio del informe y lo consultan
tanto Presupuestación como Compras.

Dos detalles de la pantalla que responden a reglas del backend:

- Si **no hay rubros cargados**, el botón de alta queda deshabilitado y aparece
  un aviso con un enlace al catálogo de rubros. Sin rubros no se puede cargar
  ningún material, y decirlo antes es mejor que dejar que el formulario falle.
- Si un material pertenece a un **rubro inactivo**, se muestra una marca junto
  al nombre del rubro. Sin eso, el material figuraría como "Activo" y nadie
  entendería por qué no aparece al armar un pedido.

**Pendiente:** el "Historial de uso por material" que pide el informe —en qué
presupuestos y pedidos se usó— necesita el módulo Compras para estar completo.

---

## 7. Tests

**98 tests, 0 fallos.** De ellos, 11 son de este módulo:

| Grupo | Casos |
| --- | --- |
| Alta | 4 |
| Edición | 4 |
| Estado | 3 |

Casos cubiertos: nace Activo con su rubro y unidad, recorte de espacios,
duplicado en el mismo rubro, rubro inactivo, rubro inexistente, cambio de rubro,
**duplicado detectado en el rubro destino al mover**, renombrar sin que cuente
como duplicado de sí mismo, editar bajo un rubro inactivo, desactivar
conservando el registro, y no poder activar bajo un rubro inactivo.

### Verificación manual

| Caso | Resultado |
| --- | --- |
| Alta de "Cemento CP40" y "Arena gruesa" en Albañilería | `201` |
| "CEMENTO cp40" en el mismo rubro | `409` "El rubro ya tiene un material llamado Cemento CP40" |
| "Cemento CP40" en Electricidad | `201` — otro rubro, otro material |
| Material en Plomería (rubro inactivo) | `409` "un material cargado ahi no se ofreceria en ningun pedido" |

---

## 8. Pendientes

| Pendiente | Se resuelve en |
| --- | --- |
| Historial de uso: en qué presupuestos y pedidos se usó | Compras |
| Impedir desactivar un material que está en un pedido pendiente | Compras |
| Que Presupuestación permita elegir el material de un ítem | Mejora de Presupuestación |
| Cargar el catálogo real de Granica | Con Ricardo, antes de la puesta en marcha |
| Restringir el módulo por rol (el capataz solo consulta) | Accesos |
