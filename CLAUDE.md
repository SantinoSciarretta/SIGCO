# SIGCO — Contexto del Proyecto para Claude Code

> Memoria persistente del proyecto. Se lee sola al arrancar cada sesión de Claude Code en este repo. Es la fuente de verdad para el desarrollo. Construida a partir del Informe consolidado (Relevamiento + Propuesta Técnica + Diccionario de Datos) de Santino Sciarretta.

## Por dónde empezar cada sesión

**Leé `ESTADO.md` primero.** Dice en qué está el proyecto hoy, qué falta, las
decisiones que no hay que revertir y las trampas del entorno que ya costaron
tiempo. Se actualiza al final de cada sesión de trabajo.

## Documento de referencia completo

El informe completo del proyecto está en **`docs/informe-sigco.md`**. Contiene el relevamiento (el *porqué* de cada proceso de la empresa), la propuesta técnica (los 14 módulos con sus circuitos, validaciones y vistas al detalle) y el diccionario de datos. Este CLAUDE.md es el resumen operativo; cuando necesites el detalle fino de un módulo (por ejemplo las vistas de interfaz exactas o el circuito completo de estados), consultá `@docs/informe-sigco.md`. Ante cualquier duda de negocio, ese archivo manda.

---

## 1. Qué es SIGCO

SIGCO (Sistema Integral de Gestión de Obras) es un sistema de gestión web hecho a medida para **Granica SRL**, una empresa familiar de construcción civil, refacción y decoración de locales que opera en CABA y Gran Buenos Aires. Es el Proyecto Integrador Profesional de Ingeniería Informática (marzo–noviembre 2026). El análisis y diseño están cerrados; esta etapa es **desarrollo de código** (Etapa 3, ~295 hs estimadas).

El sistema centraliza procesos que hoy la empresa maneja con herramientas desconectadas: presupuestos armados desde cero en Excel/Word, gastos en planillas del celular con doble carga manual, pedidos de materiales por WhatsApp sin registro, remitos en papel que se pierden, y cobros que dependen de la memoria del dueño. Todo eso hoy pasa por una sola persona (el dueño), que es el cuello de botella que SIGCO busca aliviar sin sacarle el control de las decisiones críticas.

**Cliente e interlocutor:** Ricardo Sciarretta, dueño y gestor central de Granica SRL (padre del desarrollador).

---

## 2. Cómo desarrollar (esto es lo más importante para trabajar juntos)

**Frontend y backend en simultáneo, módulo por módulo.** La cátedra pidió explícitamente desarrollar cada módulo de punta a punta (entidad JPA + repositorio + servicio + controlador REST + vista React) antes de pasar al siguiente, en lugar de hacer todo el backend primero y todo el frontend después. Trabajamos un módulo completo, verticalmente, y recién cuando funciona pasamos al que sigue.

**Explicar el porqué, no solo el qué.** Santino quiere entender cada decisión mientras desarrolla, no que el código aparezca hecho. En cada paso, antes o mientras generás código, explicá qué hace cada parte y por qué se hace así: por qué esa relación JPA, por qué esa validación va en el backend, qué problema real del relevamiento resuelve esa pantalla. El objetivo es que Santino pueda defender el código en la facultad y sepa qué toca cada línea. Preferí avanzar en pasos chicos y comprensibles antes que tirar módulos enteros de una.

**Ir de a poco y confirmar.** Flujo iterativo: se explica y se construye una parte, Santino la revisa, y recién ahí se sigue. No adelantarse varios módulos.

**Tests desde el primer módulo.** Por cada módulo, escribir tests (JUnit en el backend) junto con el código. Correrlos con `mvn test` para detectar y corregir errores en el momento. Esto es la principal herramienta de debugging del proyecto y además sirve como evidencia para la facultad.

### Orden de desarrollo de los módulos

El módulo de **Usuarios y Accesos va ÚLTIMO**, por pedido explícito. Esto significa desarrollar toda la lógica de negocio primero, sin autenticación ni control de roles activo, y recién al final ponerle la capa de seguridad encima. Es una decisión de conveniencia (permite probar los módulos sin fricción de login), pero tiene una consecuencia a tener presente:

> **Nota de diseño (RESUELTA el 11/09/2026):** varias tablas tienen FK a `usuario` (`gasto.id_usuario_registro`, `pedido.id_usuario_solicita`, `pedido.id_usuario_recibe`, `hito.id_usuario_completa`, `inasistencia.id_usuario_registro`, `registro_auditoria.id_usuario`). Durante el desarrollo de los módulos previos quedaron en `null`, con un `// TODO: vincular a usuario real al integrar módulo Accesos` en cada punto.
>
> **Los cinco TODO ya no existen.** El componente `com.sigco.seguridad.SesionActual` es el que los completa: los servicios de Gastos, Compras, Personal y Seguimiento lo reciben por constructor y registran quién hizo cada cosa. Ver `docs/desarrollo/15-usuarios-accesos.md`, sección 13.

Orden sugerido (dependencias de datos primero, seguridad al final):

1. **Clientes** — entidad simple, sin dependencias. Primer módulo para asentar el patrón (entidad → repositorio → servicio → controlador → vista React) y la identidad visual (ver sección 3) que se repiten en todos.
2. **Obras** — depende de Clientes (FK). Es la entidad núcleo del sistema.
3. **Presupuestación** — depende de Obras. Incluye el catálogo de rubros/subrubros. Es el módulo más complejo; conviene tenerlo temprano una vez dominado el patrón.
4. **Materiales** — catálogo, depende de rubro.
5. **Proveedores** — depende de material (cotizaciones).
6. **Compras** — depende de Obras, Proveedores y Materiales.
7. **Gastos** — depende de Obras, rubro, y opcionalmente pedido/operario.
8. **Personal** — operarios e inasistencias, depende de Obras.
9. **Seguimiento de Obras** — hitos, depende de Obras.
10. **Cobros** — cuotas e índice CAC, depende de Obras.
11. **Portfolio Web** — depende de Obras finalizadas.
12. **Dashboard** — no genera datos propios, consolida todo lo anterior. Por eso va casi al final.
13. **Usuarios** — cuentas de acceso. **Penúltimo.**
14. **Accesos** — roles, permisos, auditoría y activación de Spring Security sobre todos los módulos anteriores. **Último.**

(Este orden difiere del numérico de la Propuesta Técnica, que lista Usuarios/Accesos como módulos 13 y 14 por razones de documentación. Para el desarrollo, esta secuencia por dependencias es la que seguimos.)

> **Estado al 11/09/2026: los catorce módulos están desarrollados** (backend,
> frontend, tests y documentación). 230 tests en verde. La seguridad está
> activa sobre todo el sistema: cada endpoint exige su permiso y el frontend
> arma el menú con los permisos del usuario.
>
> La cuenta inicial es `ricardo` / `granica2026`, creada por la migración `V13`.
> **Es de puesta en marcha y hay que cambiarla.**

---

## 3. Identidad visual del frontend (definir ANTES del primer módulo)

El frontend tiene que verse cuidado e intencional, no como una plantilla genérica. Para lograrlo sin rehacer todo dos veces, la regla es: **la identidad visual se define una sola vez, antes de codear las pantallas del primer módulo (Clientes), y todos los módulos siguientes la heredan** reutilizando los mismos componentes base.

Antes de la primera pantalla, definir y dejar asentado un sistema de diseño:
- **Paleta de colores** — combinación de **azul como color primario con tonos claros** (fondos blancos y grises muy suaves, superficies claras). Buscar un resultado limpio y profesional, no recargado. El azul se usa para acciones principales, encabezados y acentos; los fondos claros dan aire. Definir en pantalla, junto con Santino, el azul exacto (más sobrio/corporativo o más vivo) y los grises de apoyo al arrancar el primer módulo. Aparte, definir los colores del semáforo de Gastos (verde / amarillo / rojo), que son funcionales y no deben confundirse con la paleta de marca.
- **Tipografía** — una fuente legible y consistente en toda la app.
- **Componentes base reutilizables** — botones, inputs, tablas, cards, formularios, modales, badges de estado. Definidos una vez, usados en los 14 módulos. Un cambio en el componente base se refleja en todo el sistema.
- **Layout general** — barra lateral de navegación entre módulos, header, y una grilla responsiva que funcione tanto en la computadora del dueño como en el celular de los capataces (las pantallas de Compras y Seguimiento se usan desde el teléfono en obra: priorizar carga simple y botones claros ahí).

Herramienta: usar el plugin **frontend-design** de Anthropic (marketplace oficial de Claude Code, `/plugin`), que conecta al agente con design tokens y patrones de UI para que los componentes se vean intencionales en lugar de genéricos. Cada módulo se desarrolla ya con este estilo aplicado (estético desde el nacimiento), no se embellece después.

---

## 4. Documentación continua del desarrollo

Documentar a medida que se desarrolla es un requisito central del proyecto (insumo para la defensa y para el manual de usuario final). Por eso:

- **Un `.md` por módulo en `docs/desarrollo/`.** Al terminar cada módulo, dejar un archivo (por ejemplo `docs/desarrollo/03-presupuestacion.md`) que registre: qué se construyó (entidades, endpoints, pantallas), qué decisiones de diseño se tomaron y por qué, qué validaciones quedaron implementadas, y cualquier desvío respecto del informe con su justificación. Como Claude Code acaba de hacer el trabajo, esta documentación describe lo realmente hecho, no una suposición.
- **Comentarios explicativos en el código**, en los puntos donde la lógica no es obvia (reglas de negocio, cálculos de subtotales, versionado de presupuestos, actualización por índice CAC). El objetivo es que Santino pueda leer el código y entenderlo solo.
- **El manual de usuario final** (entregable de la Etapa 4, para el dueño y los capataces, con capturas de pantalla) NO se escribe durante el desarrollo. Se arma al final, cuando existan las pantallas reales para capturar. Los `.md` de `docs/desarrollo/` son el insumo para ese manual.

---

## 5. Stack tecnológico (definido en la Propuesta Técnica, no cambiar)

**Backend**
- Java 17
- Spring Boot (API REST, respuestas JSON)
- Spring Data JPA (Hibernate) para persistencia
- Spring Security para autenticación y control de acceso por rol (se activa al final, con Accesos)
- OpenPDF para generar el PDF de presupuestos

**Frontend**
- React (SPA, componentes)
- Vite (build)
- React Router (navegación interna)
- Axios (cliente HTTP, adjunta el token de auth automáticamente a cada request)

**Base de datos y archivos**
- PostgreSQL (modelo relacional normalizado, PK/FK por entidad)
- Supabase Storage para imágenes (fotos de remitos, comprobantes de gastos, imágenes del portfolio). La BD guarda solo la URL/referencia, nunca el binario.

**Deploy**
- Frontend → Vercel
- Backend → Railway
- Base de datos + storage → Supabase

**Arquitectura:** cliente-servidor desacoplada. Frontend y backend son dos apps independientes que se comunican solo por API REST/JSON sobre HTTP/HTTPS. **Toda la lógica de negocio y las validaciones se ejecutan en el backend, nunca en el navegador** — ninguna regla debe poder saltearse manipulando el frontend. El frontend puede duplicar validaciones para UX (feedback inmediato), pero la validación autoritativa siempre está en el servidor.

⚠️ Si en algún archivo viejo aparece MySQL, JSP o Hibernate suelto sin Spring, es del stack original de la primera cursada. No usar. El stack vigente es el de arriba.

---

## 6. Convenciones de código

- **Nombres de entidades, tablas y columnas en español**, exactamente como figuran en el Diccionario de Datos (sección 8). Esto mantiene el código alineado 1:1 con lo que se entrega a la cátedra. Ejemplo: entidad `Obra` → tabla `obra` → columnas `id_obra`, `direccion_obra`, `tipo_obra`.
- Clases Java en PascalCase (`Obra`, `ItemPresupuesto`), columnas de BD en snake_case (`id_obra`, `fecha_creacion`), mapeadas con `@Column(name = "...")` cuando difieran.
- Estructura de paquetes backend **por módulo funcional**, no por capa global. Ejemplo: `com.sigco.clientes` con sus `entity`, `repository`, `service`, `controller` adentro. Cada módulo queda autocontenido y es fácil ubicar todo lo de un módulo.
- Endpoints REST en plural y en español: `/api/obras`, `/api/presupuestos`, `/api/clientes`.
- Importes monetarios: usar `BigDecimal` en Java (mapea a `NUMERIC` de PostgreSQL). **Nunca `double` ni `float`** para plata — el informe lo aclara explícitamente para evitar errores de redondeo en presupuestos, gastos y cobros.
- Estados y tipos (estado de obra, tipo de presupuesto, etc.): por defecto como `String`/`VARCHAR` según el Diccionario, validados contra un conjunto cerrado de valores. Se puede usar `enum` de Java con `@Enumerated(EnumType.STRING)` si se prefiere más seguridad de tipos; confirmar con Santino antes de cambiar el tipo respecto al Diccionario.
- La restricción de "que no suene a IA" aplica solo a los documentos académicos en prosa, **no al código**. Acá priman claridad, nombres explícitos y que Santino entienda todo.

---

## 7. Los 14 módulos (qué hace cada uno y qué problema resuelve)

**1. Obras** — Entidad núcleo. Administra el ciclo de vida de cada obra (datos maestros: cliente, dirección, tipo de inmueble, tipo de obra, fechas, estado). Es el punto de entrada obligatorio: Presupuestación, Gastos, Cobros, Seguimiento y Personal necesitan una obra existente para operar. Estados: En presupuestación → En ejecución → Finalizada / Cancelada. Cambios de estado automáticos: pasa a "En ejecución" al aprobarse el presupuesto definitivo, y a "Finalizada" al completarse el último hito.

**2. Personal** — Registro de operarios y su asignación a obras (relación muchos-a-muchos), más el registro de inasistencias (fecha + motivo opcional). Distinto de Usuarios: Personal registra a todos los operarios trabajen o no con el sistema; Usuarios gestiona solo cuentas de login. Un operario puede vincularse opcionalmente a una cuenta de usuario (para confirmar recepciones desde el celular).

**3. Clientes** — ABM de clientes con su historial de obras. Un cliente tiene muchas obras; cada obra pertenece a un cliente. Guarda el origen de la recomendación (todos los clientes llegan por referido). Se puede dar de alta un cliente rápido desde el formulario de Obra sin salir de la pantalla.

**4. Presupuestación** — El módulo más complejo. Cubre las **tres instancias reales** del proceso: cotización inicial (m² × valor/m²) → anteproyecto (solo reformas, presupuesto general por rubro) → presupuesto definitivo (detallado por rubro/subrubro/ítem). El definitivo **toma como base el anteproyecto sin sobreescribirlo** (versionado real vía `id_presupuesto_base`, autorreferencia). Incluye el catálogo de rubros y subrubros. Genera el PDF con membrete (OpenPDF) sin mostrar precios unitarios al cliente. Estados del presupuesto: Borrador / Enviado / Aprobado / Rechazado.

**5. Gastos** — Registra y clasifica cada gasto de la obra y lo compara en tiempo real contra lo presupuestado por rubro. Reemplaza la doble carga manual (Excel celular → Excel compu semanal). Semáforo visual de desvío: verde (dentro), amarillo (cerca del límite), rojo (superado). Diferencia tipos de gasto, incluido "Gasto Hormiga" (los gastos chicos que hoy se pierden — problema central). Recalcula la ganancia de la obra en cada carga.

**6. Compras** — Formaliza el circuito pedido → aprobación → recepción de materiales, reemplazando el WhatsApp. El capataz genera el pedido desde un catálogo; el dueño lo aprueba (acción no delegable) y lo envía al proveedor; el capataz confirma la recepción desde el celular con foto del remito y nota de diferencias. Respeta la lógica real: solo materiales de corralón y plomería pasan por este circuito; los de instalaciones los maneja el dueño directo.

**7. Materiales** — Catálogo único de materiales, fuente desde la que Presupuestación y Compras seleccionan ítems. Reemplaza los nombres escritos a mano e inconsistentes entre obras. Cada material pertenece a un rubro.

**8. Proveedores** — Registro de corralones/proveedores por zona geográfica, con historial de cotizaciones (precio por material) y observaciones de comportamiento (demoras, diferencias). Reemplaza las cotizaciones que hoy se piden por WhatsApp y se pierden.

**9. Seguimiento de Obras** — Avance físico por hitos ponderados, que generan un porcentaje de avance objetivo. Reemplaza el cronograma que se arma al inicio y no se actualiza. Permite cruzar avance físico contra avance financiero (Gastos) para detectar obras que gastaron mucho sin avanzar. Incluye plantillas de hitos reutilizables para obras similares.

**10. Cobros** — Plan de cobro por obra: anticipo (cuota cero) + cuotas quincenales, con actualización mensual del saldo por índice CAC (ingresado a mano en esta versión). Alertas de cuotas por vencer o vencidas. Vista consolidada de cuánto resta cobrar por obra. Estados de cuota: Pendiente / Abonada / Vencida.

**11. Portfolio Web** — Vidriera digital de obras terminadas, organizada por tipo de trabajo, para mostrar a clientes referidos. **Sin formulario de contacto** — decisión explícita del dueño, no capta clientes nuevos. Una publicación (obra finalizada) tiene varias imágenes en Supabase Storage.

**12. Dashboard** — Pantalla única con el estado de todas las obras activas: desvíos de presupuesto por rubro, cuotas por cobrar de la semana, pagos a proveedores/capataces, avance de obras, pedidos pendientes de recepción, presupuestos enviados esperando respuesta. **No genera datos propios**, consolida los del resto. Es el punto de entrada al sistema.

**13. Usuarios** — Cuentas de acceso (login): dueño, capataz general, capataces de obra. Alta de cuenta, credenciales, vínculo opcional con un registro de Personal. Contraseñas como hash (BCrypt), nunca en texto plano.

**14. Accesos** — Roles, permisos (relación rol↔permiso muchos-a-muchos) y auditoría de acciones sensibles. Es lo que habilita la delegación controlada: los capataces hacen tareas operativas sin ver la información financiera. Acá se activa Spring Security sobre todo el sistema.

---

## 8. Reglas de negocio clave (no generar lógica que las contradiga)

**Obras**
- No se crea una obra sin cliente asociado. Dirección y tipo de obra son obligatorios.
- `tipo_obra` queda bloqueado para edición apenas existe un presupuesto de anteproyecto o definitivo (cambiarlo rompería el circuito de Presupuestación).
- `fecha_inicio_real` no se carga hasta que el presupuesto definitivo esté aprobado.
- Una obra con presupuesto definitivo aprobado no puede cancelarse salvo autorización explícita del dueño.
- No se elimina una obra, solo se cancela (trazabilidad).

**Presupuestación**
- No hay presupuesto sin obra creada previamente.
- No hay presupuesto definitivo de una reforma sin anteproyecto previo de esa obra.
- En construcción nueva, el sistema no habilita generar anteproyecto (el `tipo_obra` lo determina).
- El definitivo toma como base el anteproyecto sin sobreescribirlo: ambos quedan como registros independientes vinculados por `id_presupuesto_base`.
- Solo el rol dueño puede pasar un presupuesto a "Aprobado" (no delegable).
- No se elimina un presupuesto, solo se marca "Rechazado". **Desvío vigente:** a pedido de
  Santino se implementó `DELETE /api/presupuestos/{id}`, que admite incluso los aprobados, para
  poder probar sin arrastrar registros. La regla sigue siendo el criterio de uso recomendado (así
  lo dice la pantalla de confirmación), pero el impedimento técnico se levantó a propósito: **no
  revertir esta decisión.** Justificación completa y efectos sobre la obra en
  `docs/desarrollo/05-presupuestacion-presupuestos.md`, sección 8b.
- No se puede eliminar un rubro/subrubro ya usado en algún presupuesto, solo desactivarlo.
- Un subrubro pertenece a un único rubro; no se puede elegir un subrubro que no corresponda al rubro del ítem.
- No se permiten dos rubros con el mismo nombre.
- Los precios unitarios (`valor_unitario`) son internos, no aparecen en el PDF del cliente (solo subtotales por rubro).
- La suma de anticipo + cuotas debe cerrar al 100% del total; no se guarda un plan de pago que no cierre matemáticamente.
- No se aprueba un definitivo si la obra no tiene cliente válido.

**Gastos**
- Cada gasto queda asociado automáticamente a obra, rubro y tipo de gasto.
- Comparación presupuesto vs. gasto en tiempo real (semáforo verde/amarillo/rojo), sin traspaso manual.

**Compras**
- El dueño aprueba el pedido antes de enviarlo al proveedor (no delegable).
- El circuito de remito con foto cubre solo materiales de corralón y plomería.
- Estados del pedido: Pendiente de Aprobación → Enviado al Proveedor → Recibido Completo / Recibido con Diferencias / Anulado. `nota_diferencia` obligatoria si hay diferencias.

**Personal**
- No se registra inasistencia sin indicar la obra. Un operario solo tiene inasistencias en obras a las que está/estuvo asignado.
- No dos inasistencias para el mismo operario, misma fecha y obra.
- Al marcar operario Inactivo se lo desvincula de obras activas pero se conserva su historial. `motivo` de falta no es obligatorio.

**Seguimiento**
- Los hitos tienen ponderación; el porcentaje de avance sale de los hitos completados.
- `fecha_cumplimiento` obligatoria al marcar un hito como Completado. Al completar el último hito, la obra pasa a "Finalizada".

**Cobros**
- El anticipo es la cuota cero. El saldo se actualiza por índice CAC (registro manual en `registro_cac`).
- `fecha_pago` obligatoria al marcar una cuota como Abonada.

**Portfolio**
- Solo obras finalizadas. Sin canales de contacto para desconocidos.

**Usuarios y Accesos** (implementadas el 11/09/2026)
- Las contraseñas se guardan con hash BCrypt, nunca en texto plano. `Usuario` **no tiene** `getContrasenaHash()` a propósito: sin getter, el hash no puede filtrarse a un DTO ni a un log.
- Cambiar la propia contraseña exige escribir la actual; el dueño reseteando la de otro, no.
- **No se puede dejar el sistema sin ninguna cuenta activa con rol Dueño**, ni dando de baja ni cambiando el rol. Nadie se da de baja a sí mismo.
- **Al rol Dueño no se le pueden quitar `accesos.editar` ni `usuarios.editar`**: nadie podría volver a entrar a la pantalla que lo arregla.
- **`compras.aprobar` es un permiso propio y solo lo tiene el Dueño.** Es lo que hace cumplir la regla de que la aprobación del pedido es indelegable: el sistema rechaza dárselo a un capataz aunque se marque la casilla.
- Una cuenta no se elimina: se da de baja con motivo (`usuario.motivo_baja`, agregado en `V13`).
- El mensaje de error del login es el mismo para usuario inexistente y contraseña incorrecta, y el tiempo de respuesta también. Distinguirlos permitiría descubrir qué usuarios existen.
- Los permisos **no viajan dentro del token**: se leen de la base en cada petición, así un cambio en Accesos se aplica en la petición siguiente.
- **El frontend filtra menú y rutas, pero eso es presentación, no seguridad.** La validación que manda es `@PreAuthorize` en el backend.

---

## 9. Modelo de datos completo (28 tablas)

Nombres, tipos PostgreSQL, PK/FK exactos del Diccionario de Datos. **Respetar estos nombres al 100% en las entidades JPA.** Campos monetarios (`NUMERIC`) → `BigDecimal`; `TIMESTAMP` → `LocalDateTime`; `DATE` → `LocalDate`. Todos los `id_*` PK son `BIGSERIAL` (autoincremental) → `Long` con `@GeneratedValue`.

### Módulo Accesos
- **rol**: `id_rol` (PK), `nombre_rol` (VARCHAR 50, único), `descripcion` (VARCHAR 200)
- **permiso**: `id_permiso` (PK), `nombre_permiso` (VARCHAR 100, único), `modulo` (VARCHAR 50), `descripcion` (VARCHAR 200)
- **rol_permiso** (intermedia N:M): `id_rol` (PK, FK→rol), `id_permiso` (PK, FK→permiso)
- **registro_auditoria**: `id_auditoria` (PK), `id_usuario` (FK→usuario), `accion_realizada` (VARCHAR 150), `modulo_afectado` (VARCHAR 50), `fecha_hora` (TIMESTAMP)

### Módulo Usuarios
- **usuario**: `id_usuario` (PK), `nombre_usuario` (VARCHAR 50, único), `contrasena_hash` (VARCHAR 255), `id_rol` (FK→rol), `id_operario` (FK→operario, opcional), `estado` (VARCHAR 10), `ultima_fecha_acceso` (TIMESTAMP), `fecha_alta` (TIMESTAMP)

### Módulo Clientes
- **cliente**: `id_cliente` (PK), `nombre_apellido` (VARCHAR 150), `telefono_contacto` (VARCHAR 30), `email_contacto` (VARCHAR 100), `origen_recomendacion` (VARCHAR 20), `recomendado_por` (VARCHAR 150), `estado` (VARCHAR 10), `fecha_alta` (TIMESTAMP)

### Módulo Obras
- **obra**: `id_obra` (PK), `id_cliente` (FK→cliente), `direccion_obra` (VARCHAR 200), `tipo_inmueble` (VARCHAR 15), `tipo_obra` (VARCHAR 15), `fecha_inicio_real` (DATE), `fecha_fin_estimada` (DATE), `notas` (TEXT), `estado` (VARCHAR 20), `motivo_cancelacion` (VARCHAR 200), `fecha_creacion` (TIMESTAMP)

### Módulo Personal
- **operario**: `id_operario` (PK), `nombre_apellido` (VARCHAR 150), `telefono_contacto` (VARCHAR 30), `estado` (VARCHAR 10), `fecha_alta` (TIMESTAMP)
- **operario_obra** (intermedia N:M): `id_operario` (PK, FK→operario), `id_obra` (PK, FK→obra), `fecha_asignacion` (DATE)
- **inasistencia**: `id_inasistencia` (PK), `id_operario` (FK→operario), `id_obra` (FK→obra), `fecha_falta` (DATE), `motivo` (VARCHAR 200), `id_usuario_registro` (FK→usuario)

### Módulo Presupuestación
- **rubro**: `id_rubro` (PK), `nombre_rubro` (VARCHAR 100, único), `estado` (VARCHAR 10)
- **subrubro**: `id_subrubro` (PK), `id_rubro` (FK→rubro), `nombre_subrubro` (VARCHAR 100), `estado` (VARCHAR 10)
- **presupuesto**: `id_presupuesto` (PK), `id_obra` (FK→obra), `tipo_presupuesto` (VARCHAR 20), `id_presupuesto_base` (FK→presupuesto, autorreferencia), `version` (INTEGER), `estado` (VARCHAR 15), `metros_cuadrados` (NUMERIC 10,2), `valor_por_m2` (NUMERIC 12,2), `total_presupuesto` (NUMERIC 14,2), `anticipo_porcentaje` (NUMERIC 5,2), `cantidad_cuotas` (INTEGER), `plazo_estimado_obra` (VARCHAR 100), `fecha_creacion` (TIMESTAMP)
- **item_presupuesto**: `id_item` (PK), `id_presupuesto` (FK→presupuesto), `id_rubro` (FK→rubro), `id_subrubro` (FK→subrubro), `id_material` (FK→material, opcional — **agregado en V7**, ver nota abajo), `descripcion` (VARCHAR 250), `unidad_medida` (VARCHAR 20), `cantidad` (NUMERIC 12,2), `valor_unitario` (NUMERIC 12,2, interno), `subtotal` (NUMERIC 14,2)

> **Nota sobre `item_presupuesto.id_material` (agregado en `V7`).** El Diccionario
> original no incluía esta columna, pero la prosa del informe describe que los
> ítems del presupuesto se eligen del catálogo de Materiales. Sin la columna ese
> vínculo no podía existir y la descripción quedaba como texto libre —
> reproduciendo el problema que Materiales viene a resolver. Es **nullable**
> porque no todo ítem es un material (mano de obra, dirección de obra). El
> material debe pertenecer al rubro del ítem y no puede estar inactivo.
> Justificación completa en `docs/desarrollo/05-presupuestacion-presupuestos.md`,
> sección 2b.

### Módulo Materiales
- **material**: `id_material` (PK), `nombre_material` (VARCHAR 150), `id_rubro` (FK→rubro), `unidad_medida` (VARCHAR 20), `estado` (VARCHAR 10), `fecha_alta` (TIMESTAMP)

### Módulo Proveedores
- **proveedor**: `id_proveedor` (PK), `nombre_proveedor` (VARCHAR 150), `zona_cobertura` (VARCHAR 100), `telefono_contacto` (VARCHAR 30), `email_contacto` (VARCHAR 100), `estado` (VARCHAR 10), `fecha_alta` (TIMESTAMP)
- **cotizacion**: `id_cotizacion` (PK), `id_proveedor` (FK→proveedor), `id_material` (FK→material), `precio_cotizado` (NUMERIC 12,2), `fecha_cotizacion` (TIMESTAMP)
- **observacion_proveedor**: `id_observacion` (PK), `id_proveedor` (FK→proveedor), `id_pedido` (FK→pedido), `descripcion` (VARCHAR 300), `fecha` (TIMESTAMP)

### Módulo Compras
- **pedido**: `id_pedido` (PK), `id_obra` (FK→obra), `id_proveedor` (FK→proveedor), `id_usuario_solicita` (FK→usuario), `id_usuario_recibe` (FK→usuario), `estado` (VARCHAR 25), `foto_remito` (VARCHAR 255, ref Supabase), `nota_diferencia` (VARCHAR 300), `fecha_solicitud` (TIMESTAMP), `fecha_aprobacion` (TIMESTAMP), `fecha_recepcion` (TIMESTAMP)
- **pedido_material** (intermedia N:M): `id_pedido` (PK, FK→pedido), `id_material` (PK, FK→material), `cantidad` (NUMERIC 12,2)

### Módulo Gastos
- **gasto**: `id_gasto` (PK), `id_obra` (FK→obra), `id_rubro` (FK→rubro), `id_subrubro` (FK→subrubro), `tipo_gasto` (VARCHAR 15), `monto` (NUMERIC 14,2), `fecha_gasto` (DATE), `fecha_carga` (TIMESTAMP), `id_pedido` (FK→pedido), `id_operario` (FK→operario), `descripcion` (VARCHAR 250), `comprobante_adjunto` (VARCHAR 255, ref Supabase), `id_usuario_registro` (FK→usuario), `estado` (VARCHAR 12), `motivo_anulacion` (VARCHAR 200)

### Módulo Seguimiento de Obras
- **hito**: `id_hito` (PK), `id_obra` (FK→obra), `nombre_hito` (VARCHAR 150), `ponderacion` (NUMERIC 5,2), `orden` (INTEGER), `estado` (VARCHAR 12), `fecha_cumplimiento` (DATE), `observacion` (VARCHAR 250), `id_usuario_completa` (FK→usuario)
- **plantilla_hito**: `id_plantilla` (PK), `nombre_plantilla` (VARCHAR 100), `fecha_creacion` (TIMESTAMP)
- **plantilla_hito_detalle**: `id_detalle` (PK), `id_plantilla` (FK→plantilla_hito), `nombre_hito` (VARCHAR 150), `ponderacion` (NUMERIC 5,2), `orden` (INTEGER)

### Módulo Cobros
- **cuota**: `id_cuota` (PK), `id_obra` (FK→obra), `numero_cuota` (INTEGER, el anticipo es cuota cero), `monto_cuota` (NUMERIC 14,2), `fecha_vencimiento` (DATE), `estado` (VARCHAR 12), `fecha_pago` (DATE), `medio_pago` (VARCHAR 15), `comprobante_emitido` (VARCHAR 20)
- **registro_cac**: `id_cac` (PK), `mes_correspondiente` (DATE), `valor_indice` (NUMERIC 8,4), `fecha_carga` (TIMESTAMP)

### Módulo Portfolio Web
- **publicacion_portfolio**: `id_publicacion` (PK), `id_obra` (FK→obra), `tipo_trabajo` (VARCHAR 30), `estado` (VARCHAR 15), `fecha_publicacion` (TIMESTAMP)
- **imagen_portfolio**: `id_imagen` (PK), `id_publicacion` (FK→publicacion_portfolio), `url_imagen` (VARCHAR 255, ref Supabase), `orden` (INTEGER)

---

## 10. Roles y matriz de permisos (para cuando se implemente Accesos)

Tres roles:
- **Dueño** — acceso total a los 14 módulos. Único que crea/aprueba presupuestos, registra cobros y pagos, aprueba pedidos y administra usuarios.
- **Capataz General** — ve seguimiento de todas las obras activas, pedidos, materiales y proveedores (consulta). Marca hitos y genera pedidos. No accede a información financiera, presupuestos, cobros ni usuarios.
- **Capataz de Obra** — el más restringido. Solo su obra asignada: genera pedidos y confirma recepción desde el celular. No ve info financiera ni otras obras.

Matriz (Total = consulta+edición, Consulta = solo lectura, — = sin acceso):

| Módulo | Dueño | Capataz General | Capataz de Obra |
|---|---|---|---|
| Obras | Total | Consulta | Consulta (su obra) |
| Personal | Total | Consulta | — |
| Clientes | Total | — | — |
| Presupuestación | Total | — | — |
| Proveedores | Total | Consulta | — |
| Materiales | Total | Consulta | Consulta |
| Compras | Total | Total | Total (su obra) |
| Seguimiento de Obras | Total | Total | Consulta (su obra) |
| Gastos | Total | Consulta | — |
| Cobros | Total | — | — |
| Portfolio Web | Total | — | — |
| Usuarios | Total | — | — |
| Accesos | Total | — | — |
| Dashboard | Total | Consulta (reducido) | — |

---

## 11. Seguridad (se implementa con el módulo Accesos, al final)

- Login propio con credenciales únicas. Al ingresar, el backend emite un token firmado (JWT) con usuario y rol; el frontend lo adjunta en cada request (Axios); el backend lo valida antes de cada acción.
- Contraseñas cifradas con hash unidireccional + sal aleatoria (BCrypt). Nunca texto plano.
- Control de acceso por rol validado en el backend (Spring Security) en cada request. Nunca confiar en el frontend.
- Validación de todas las entradas en el backend. Consultas parametrizadas vía JPA (previene inyección). Sanitizar textos.
- HTTPS en producción (lo provee la plataforma de hosting).
- Auditoría (`registro_auditoria`) de acciones sensibles: quién, qué acción, qué módulo, cuándo.

---

## 12. Límites del alcance (esta versión NO incluye)

- App móvil nativa (el acceso desde celular es por navegador responsivo).
- Integraciones externas automáticas: API de WhatsApp, importación automática del índice CAC, servicios de correo. El índice CAC se carga a mano.
- Liquidación de sueldos ni contabilidad/impuestos.
- La arquitectura queda preparada para sumar estas cosas después sin reescribir.
