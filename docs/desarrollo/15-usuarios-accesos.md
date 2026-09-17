# Módulos 15 y 16 — Usuarios y Accesos

**Fecha de desarrollo:** 11/09/2026
**Depende de:** todos (activa la seguridad sobre el sistema entero)
**Referencia en el informe:** secciones "Módulo 13 – Usuarios" y "Módulo 14 – Accesos"

---

## 1. Por qué van últimos

Fue una decisión explícita del proyecto, y conviene poder explicarla: los doce
módulos de negocio se desarrollaron y probaron **sin login**, para no arrastrar
la fricción de autenticarse en cada prueba, y la seguridad se puso encima al
final.

Eso tuvo una consecuencia concreta que quedó anotada en el código desde el
primer día: cinco columnas guardan *quién hizo cada cosa*
(`gasto.id_usuario_registro`, `pedido.id_usuario_solicita`,
`pedido.id_usuario_recibe`, `hito.id_usuario_completa`,
`inasistencia.id_usuario_registro`) y quedaron en `null`, cada una con un
`// TODO: vincular a usuario real al integrar el módulo Accesos`.

**Este módulo es el que los completa.** Los cinco TODO ya no existen.

---

## 2. Usuario no es lo mismo que Operario

Es la distinción del informe y define el módulo:

| | Qué registra |
| --- | --- |
| **Personal** (módulo 8) | TODOS los operarios, trabajen o no con el sistema |
| **Usuarios** (este) | Solo los que tienen con qué entrar |

La mayoría de los operarios no usa el sistema y no tiene cuenta. El vínculo
entre los dos es **opcional** y existe para un caso concreto: que el capataz que
confirma una recepción desde el celular quede identificado como la persona de
Personal, y no como una cuenta suelta.

---

## 3. Las contraseñas

### Nunca en texto plano, ni un instante

Se cifran con **BCrypt**: hash unidireccional con sal incorporada. Del hash no
se puede volver a la contraseña, y dos personas con la misma contraseña tienen
hashes distintos.

Tres decisiones refuerzan eso:

1. **La entidad `Usuario` no tiene `getContrasenaHash()`.** Sin getter, el hash
   no puede terminar en un DTO ni en un log por descuido.
2. **La comparación se hace adentro de la entidad** (`verificarContra()`), que
   recibe la función de comparación. Quien pregunta recibe un sí o un no, nunca
   el hash.
3. **Ningún DTO de salida tiene campo para la contraseña.** No es que se omita
   al completarlo: el campo no existe. Mismo criterio que la vidriera del
   Portfolio con los datos del cliente.

Incluso la migración `V13` carga la cuenta inicial con el hash ya calculado.

### El PIN de cuatro dígitos del diseño se reemplazó por una contraseña

El diseño original del login tenía un PIN de 4 dígitos. Se cambió por una
contraseña de mínimo 8 caracteres, y el motivo es técnico: **un PIN de cuatro
dígitos tiene diez mil combinaciones**, así que guardarlo con hash no protege
gran cosa — se prueban todas en segundos. El informe pide "credenciales únicas"
y no especifica el formato.

### Cambiar la propia exige la actual; resetear la de otro, no

| Caso | Pide la contraseña actual | Por qué |
| --- | --- | --- |
| Uno cambia la suya | **Sí** | Que alguien que encuentre la sesión abierta no se apropie de la cuenta |
| El dueño resetea la de otro | No | Si la supiera, no haría falta resetearla |

Es el único endpoint del módulo que **no** exige el permiso de administrar
cuentas: un capataz no administra usuarios, pero tiene que poder cambiar su
contraseña. La condición vive en la anotación, al lado del método:

```java
@PreAuthorize("hasAuthority('usuarios.editar') or #id == principal.idUsuario")
```

---

## 4. Las dos reglas que protegen al sistema de sí mismo

Son las más importantes del módulo y las dos existen por el mismo motivo: una
casilla mal marcada puede dejar el sistema **inutilizable**, y el único camino
de vuelta sería editar la base a mano.

### No se puede quedar sin ningún dueño activo

Se rechaza dar de baja —o cambiarle el rol a— la última cuenta activa con rol
Dueño. Sin ningún dueño, nadie puede administrar usuarios ni aprobar un
presupuesto.

Y **nadie se da de baja a sí mismo**: quedaría afuera en el acto, y si además
era el último dueño, nadie podría reactivarlo.

### Al rol Dueño no se le pueden quitar `accesos.editar` ni `usuarios.editar`

Si se pudiera, bastaría una casilla desmarcada por error para que nadie —ni
siquiera el dueño— pudiera volver a entrar a la pantalla que lo arregla.

---

## 5. Cómo se decide quién puede hacer qué

En **tres capas**, y las tres usan literalmente el mismo texto. Eso es lo que
permite verificar la matriz del informe de punta a punta: se lee una fila de la
tabla del informe y se busca esa cadena en el código.

```
tabla permiso (V13)   →  'compras.aprobar'
backend               →  @PreAuthorize("hasAuthority('compras.aprobar')")
frontend              →  { ruta: '/pedidos', permiso: 'compras.ver' }
```

| Capa | Qué resuelve | Es seguridad |
| --- | --- | --- |
| `ConfiguracionSeguridad` | Qué se puede pedir **sin estar autenticado** | Sí |
| `@PreAuthorize` en cada controlador | Qué permiso hace falta para cada operación | **Sí, esta es la que manda** |
| `RutaProtegida` y el menú del frontend | Qué pantallas ofrecer | **No: es presentación** |

> Esconder un botón no protege nada. Quien quiera saltear el frontend manda la
> petición directo a la API. Lo que protege es el backend, que revalida el
> permiso en **cada** petición. El frontend evita ofrecer algo que va a ser
> rechazado, que es una cuestión de uso, no de seguridad.

### Los permisos son `<módulo>.ver` y `<módulo>.editar`

La matriz del informe tiene tres niveles —Total, Consulta y sin acceso— y se
representan con dos permisos: Total es ver+editar, Consulta es solo ver, y sin
acceso es no tener ninguno. Un tercer permiso "total" sería redundante y habría
que mantener la coherencia entre los tres a mano.

La excepción es **`compras.aprobar`**, que es un permiso propio y separado de
`compras.editar`. Es lo que hace cumplir la regla del informe de que la
aprobación del pedido es indelegable: un capataz puede generar pedidos y
confirmar recepciones, pero no aprobar. Y el sistema **rechaza** dárselo aunque
se marque la casilla.

---

## 6. Los tokens

### Por qué un token y no una sesión en el servidor

El backend no guarda sesiones: cada petición llega con toda la información
necesaria. Eso es lo que permite que Railway lo reinicie o corra dos copias sin
que nadie pierda la sesión.

### Qué lleva el token y qué NO

Lleva el id del usuario, su nombre y su rol. **No lleva los permisos.**

Es deliberado: un token dura ocho horas, y si los permisos viajaran adentro,
quitarle un permiso a un rol no tendría efecto hasta que todos volvieran a
entrar. Los permisos se leen de la base en cada petición, así un cambio en la
pantalla de Accesos se aplica en la petición siguiente.

El token está **firmado, no cifrado**: cualquiera que lo tenga puede leer su
contenido, pero nadie puede modificarlo sin la clave. Por eso adentro no va nada
secreto.

### El filtro consulta la base en cada petición

Sería más rápido confiar en el token. No se hace: una cuenta se puede dar de
baja en cualquier momento, y si el filtro confiara en el token, un usuario dado
de baja seguiría entrando hasta ocho horas después. Es una consulta indexada por
clave primaria; el costo es despreciable frente a eso.

### Dónde se guarda el token, y el costo de esa decisión

En `localStorage`, para que recargar la página o abrir otra pestaña no obligue a
volver a entrar.

El costo, que conviene poder explicar: **lo que está en localStorage lo puede
leer cualquier script que corra en la página**. La alternativa —una cookie
`httpOnly`, que el JavaScript no puede leer— es más segura, pero exige que
backend y frontend compartan dominio, y acá están en Railway y Vercel.

Lo que acota el riesgo: el token dura ocho horas y no lleva permisos adentro.

---

## 7. El mensaje de error del login es siempre el mismo

Usuario inexistente y contraseña incorrecta devuelven **exactamente el mismo
texto**. Si el sistema contestara "ese usuario no existe", cualquiera podría
probar nombres hasta descubrir cuáles son cuentas reales, y después concentrarse
en adivinar la contraseña de una que sabe que existe.

Va más allá del mensaje: **se verifica la contraseña aunque el usuario no
exista**, contra un hash de descarte, para que el tiempo de respuesta sea
parecido en los dos casos. Si el sistema contestara instantáneamente ante un
usuario inexistente y tardara al comparar el hash, la diferencia de tiempo
revelaría cuáles existen.

Una cuenta **dada de baja** sí recibe un mensaje distinto: quien la usaba tiene
que entender que no es un problema de tipeo, y ya sabía que la cuenta existía.

---

## 8. La auditoría

Se registran **solo las acciones sensibles**, no todo lo que pasa. Si se
registrara cada consulta, la tabla crecería sin límite y encontrar lo que
importa sería imposible.

El criterio: *si el día de mañana alguien pregunta "quién hizo esto", ¿la
respuesta importa?* Aprobar un presupuesto, aprobar un pedido, anular un gasto,
registrar un pago, cambiar permisos y dar de baja una cuenta entran. Listar
clientes, no.

**Se escribe en la misma transacción de la acción que la genera**
(`Propagation.MANDATORY`). Si la acción falla y se deshace, su registro de
auditoría se deshace también: de lo contrario la auditoría afirmaría que pasaron
cosas que no pasaron, que es peor que no tener auditoría.

Ningún registro se puede editar ni borrar desde ninguna parte del sistema.

---

## 9. Ajustes al modelo (`V13`)

`V13` **no crea tablas**: las cinco (rol, permiso, rol_permiso, usuario,
registro_auditoria) ya existían desde `V8`. Lo que hace es cargar los datos con
los que el sistema arranca, más un agregado:

| Cambio | Por qué |
| --- | --- |
| `usuario.motivo_baja` | El informe dice que una cuenta se desactiva, pero no decía dónde anotar por qué. Sin eso, "Inactivo" no distingue una renuncia de una suspensión por seguridad. Mismo criterio que en obra, gasto, pedido y cuota |
| Los 3 roles | Datos de configuración, no de operación: no hay pantalla para crear roles |
| Los 28 permisos | Un permiso nuevo no es un dato, es código nuevo que hay que proteger |
| `rol_permiso` según la matriz | Transcripción directa de la tabla del informe |
| La cuenta `ricardo` | Sin ella el sistema queda inaccesible: hace falta estar autenticado para administrar usuarios |
| Dos índices en `registro_auditoria` | Es la tabla que más crece; se consulta por fecha o por usuario |

### La cuenta inicial

```
usuario: ricardo
contraseña: granica2026
```

**Es de puesta en marcha y hay que cambiarla en el primer ingreso**, desde
Usuarios o desde "Mi contraseña".

---

## 10. Endpoints

### Sesión

| Método | Ruta | Qué hace |
| --- | --- | --- |
| POST | `/api/sesion` | Ingresar. **Única ruta abierta sin autenticación**, junto al diagnóstico y la vidriera |
| GET | `/api/sesion` | Quién soy: lo usa el frontend al recargarse |

**No hay endpoint para salir.** Con tokens no hace falta: el servidor no guarda
nada que borrar, así que cerrar sesión es que el navegador olvide el token. Un
endpoint de salida daría la impresión de que el servidor invalida algo, y no es
así.

### Usuarios

| Método | Ruta | Permiso |
| --- | --- | --- |
| GET | `/api/usuarios` | `usuarios.ver` |
| POST | `/api/usuarios` | `usuarios.editar` |
| PATCH | `/api/usuarios/{id}/contrasena` | `usuarios.editar` **o ser uno mismo** |
| PATCH | `/api/usuarios/{id}/rol` | `usuarios.editar` |
| PATCH | `/api/usuarios/{id}/operario` | `usuarios.editar` |
| PATCH | `/api/usuarios/{id}/baja` | `usuarios.editar` |
| PATCH | `/api/usuarios/{id}/reactivacion` | `usuarios.editar` |

**No hay DELETE.** Una cuenta se da de baja: la auditoría guarda quién hizo cada
cosa, y borrar el usuario dejaría esos registros apuntando a nadie.

### Accesos

| Método | Ruta | Permiso |
| --- | --- | --- |
| GET | `/api/roles` | `accesos.ver` |
| GET | `/api/permisos` | `accesos.ver` |
| PUT | `/api/roles/{id}/permisos` | `accesos.editar` |
| GET | `/api/auditoria` | `accesos.ver` |

**No hay POST ni DELETE de roles ni permisos.** El PUT reemplaza la lista
completa, no casilla por casilla: el dueño marca y desmarca varias antes de
guardar, y aplicar cada cambio por separado dejaría el rol en estados
intermedios que nadie pidió.

---

## 11. Qué rutas quedan abiertas sin autenticación

Tres, y ninguna más:

| Ruta | Por qué |
| --- | --- |
| `POST /api/sesion` | El login no puede exigir estar logueado |
| `GET /api/estado` | Diagnóstico: tiene que responder aunque nadie haya entrado |
| `GET /api/vidriera/**` | **La vidriera del portfolio es pública por diseño** |

La vidriera es la que confirma una decisión tomada en el módulo 13: vive en
`/api/vidriera` y no dentro de `/api/portfolio` desde el primer día,
justamente para que esto fuera posible sin desarmar rutas.

---

## 12. Pantallas

| Pantalla | Dónde | Quién |
| --- | --- | --- |
| Ingreso | `/` | Todos |
| Usuarios | `/usuarios` | `usuarios.ver` |
| Accesos (matriz + auditoría) | `/accesos` | `accesos.ver` |
| Mi contraseña | `/mi-cuenta` | Todos |

Usuarios y Accesos van en un **menú secundario** detrás del nombre de usuario,
no en la barra principal. Dos motivos: con catorce entradas la barra queda
impracticable —el diseño original ya preveía un menú de administración— y son
pantallas que se abren cada tanto, no todos los días.

### El menú principal ahora se arma con los permisos

Cada uno ve las entradas a las que puede entrar y ninguna más. Un capataz de
obra ve cuatro (Obras, Materiales, Pedidos, Avance) en lugar de doce.

---

## 13. Lo que hubo que tocar de los módulos anteriores

| Archivo | Cambio |
| --- | --- |
| `client.js` (frontend) | El interceptor adjunta el token. **Un solo lugar** — por esto desde el módulo 1 ningún componente importa axios por su cuenta |
| `CorsConfig` | Pasó de `WebMvcConfigurer` a bean `CorsConfigurationSource`: los filtros de seguridad corren antes que Spring MVC, y la consulta previa (OPTIONS) del navegador quedaba rechazada con 401 |
| `ManejadorGlobalDeErrores` | Atrapa `AccessDeniedException` y devuelve **403**. Sin esto, un permiso faltante llegaba al frontend como 500 ("error del sistema") en lugar de 403 ("no tenés permiso") |
| `GastoService`, `PedidoService`, `PersonalService`, `SeguimientoService` | Reciben `SesionActual` y completan las cinco columnas que estaban en `null` |
| `Hito.completar()` | Recibe quién lo completó |
| Tests de esos módulos | Un mock de `SesionActual` |
| `ClienteControllerTest` | `@WithMockUser` y los beans de seguridad simulados: con Spring Security activo, el corte `@WebMvcTest` levanta la cadena de filtros |

---

## 14. Tests

**23 tests** entre los tres módulos (10 de Usuarios, 6 de Accesos, 7 de
autenticación), sobre 230 del sistema.

| Qué se verifica | Por qué importa |
| --- | --- |
| La contraseña se guarda cifrada | Se usa BCrypt de verdad y no un mock: con uno simulado, la prueba no probaría nada |
| Cambiar la propia exige la actual | Que una sesión abierta no permita apropiarse de la cuenta |
| No se da de baja al último dueño | El sistema quedaría inutilizable |
| Nadie se da de baja a sí mismo | Ídem |
| Al Dueño no se le quitan los dos permisos críticos | Ídem |
| `compras.aprobar` es indelegable | Regla textual del informe |
| El mensaje del login no revela si el usuario existe | Se comparan los dos mensajes literalmente |
| Una cuenta dada de baja no entra | Aunque la contraseña sea correcta |
| Un token con otra firma se rechaza | JWT de verdad, no simulado |
| Un token vencido se rechaza | |

### Verificación sobre el sistema real

Los tests unitarios usan mocks y no simulan Jackson, Hibernate ni el manejo de
sesiones de Spring. Varios errores de este proyecto solo aparecieron al
ejercitar el sistema de verdad, así que se verificó también sobre HTTP y en el
navegador:

| Caso | Resultado |
| --- | --- |
| `GET /api/obras` sin token | `401` con el formato de error del sistema |
| `GET /api/estado` y `/api/vidriera` sin token | `200` |
| Login con contraseña incorrecta | Mismo mensaje que con usuario inexistente |
| Login correcto | Token, rol Dueño y **28 permisos** |
| Capataz de obra: obras, pedidos, materiales | `200` |
| Capataz de obra: presupuestos, cobros, clientes, gastos, usuarios, tablero | `403` |
| Auditoría después del primer ingreso | 1 registro: "Ingreso al sistema" |
| Login en el navegador como dueño | Entra a `/tablero`, menú con **12 entradas** |
| Login en el navegador como capataz de obra | Entra a `/obra`, menú con **4 entradas** |
| Capataz escribiendo `/cobranzas` a mano | "Esta sección no está habilitada para tu rol" |

---

## 15. Pendientes

| Pendiente | Nota |
| --- | --- |
| ~~**Alcance por obra del Capataz de Obra**~~ | **Resuelto el 14/09/2026**: lo implementa `AlcanceDeObras`, con sus tests en `AlcanceDeObrasTest` |
| ~~Renovación del token antes de que venza~~ | **Resuelto el 17/09/2026**: renovación deslizante, ver `18-cierre-y-endurecimiento.md` §4 |
| ~~Bloqueo tras varios intentos fallidos~~ | **Resuelto el 17/09/2026**: 5 intentos, 15 minutos, ver `18-cierre-y-endurecimiento.md` §2 |
| ~~Auditar más acciones~~ | **Resuelto el 17/09/2026**: aprobaciones, anulaciones y cobros, ver `18-cierre-y-endurecimiento.md` §5 |
| ~~Cambio obligatorio de contraseña en el primer ingreso~~ | **Resuelto el 17/09/2026**: lo exige el backend, ver `18-cierre-y-endurecimiento.md` §3 |
