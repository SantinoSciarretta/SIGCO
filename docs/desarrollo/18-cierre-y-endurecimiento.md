# 18 — Cierre: endurecimiento y pendientes

> Este documento no describe un módulo nuevo. Describe el trabajo de cierre
> sobre los catorce módulos ya construidos: las defensas que hacían falta antes
> de publicar el sistema en internet, y los pendientes que quedaban anotados en
> los documentos de cada módulo.
>
> Se escribe con el mismo criterio que el resto: qué se construyó, qué se
> decidió y por qué.

**Fecha de desarrollo:** 17/09/2026
**Afecta a:** Usuarios, Accesos, Obras, Presupuestación, Compras, Gastos,
Cobros, Portfolio, Dashboard y Almacenamiento
**Migración Flyway:** `V14__seguridad_ingreso.sql`

---

## 1. Por qué existe este trabajo

Los catorce módulos estaban terminados y los 248 tests pasaban. Pero el sistema
nunca había estado expuesto a internet, y varias cosas que son irrelevantes en
`localhost` dejan de serlo apenas hay una dirección pública:

- Cualquiera podía probar contraseñas contra `/api/sesion` sin límite.
- La contraseña del dueño estaba escrita en un archivo del repositorio, que está
  publicado en GitHub.
- La sesión vencía a las ocho horas en medio de la jornada.
- Se auditaban las cuentas de usuario, pero no las aprobaciones de dinero.

Además quedaban pendientes anotados en los `.md` de los módulos, y una regla del
informe que nunca se había implementado.

---

## 2. Límite de intentos de ingreso

### El problema

`AutenticacionService` ya estaba bien defendido contra dos ataques: el mensaje
de error no distingue "usuario inexistente" de "contraseña incorrecta", y el
tiempo de respuesta es parecido en los dos casos (por eso existe
`HASH_DE_DESCARTE`). Pero nada limitaba *cuántas veces* se podía intentar.

### Qué se construyó

Cinco intentos fallidos consecutivos bloquean la cuenta quince minutos. Los
valores están en `application.properties`:

```properties
sigco.seguridad.intentos-maximos=5
sigco.seguridad.minutos-bloqueo=15
```

### Tres decisiones que conviene poder defender

**El contador vive en la base, no en memoria.** `ServicioJwt` deja explícito que
el backend es *stateless* para que Railway pueda reiniciarlo o correr dos copias.
Un contador en memoria se perdería en cada reinicio —reiniciar pasaría a ser la
forma de saltear el bloqueo— y con dos copias corriendo cada una contaría por su
lado, dejando el límite real en el doble.

**El bloqueo se verifica ANTES de comparar la contraseña.** Es la decisión más
importante del módulo y la más fácil de hacer mal.

Si se verificara después, quien está probando contraseñas podría seguir
probándolas durante el bloqueo: el sistema le diría "incorrecta" hasta que
acertara, y recién ahí le avisaría que la cuenta está bloqueada. Le habríamos
impedido entrar en ese momento, pero le habríamos **confirmado cuál era la
contraseña**, y le alcanzaría con esperar quince minutos.

Verificando antes, durante el bloqueo ningún intento se evalúa. El ritmo máximo
queda en cinco intentos cada quince minutos —veinte por hora— y adivinar deja de
ser viable.

El test `cuentaBloqueadaNoEntraNiConLaCorrecta` es el que fija esta decisión:
usa la contraseña **correcta** a propósito, y espera el mensaje de bloqueo. Si
alguien invirtiera el orden del código, ese test se pondría en rojo.

**El incremento va en su propia transacción.** Este es el detalle que hace que
todo funcione, y es invisible leyendo el código por encima.

Cuando la contraseña es incorrecta, el método termina lanzando
`ReglaDeNegocioException`, que es una `RuntimeException`. Spring deshace la
transacción ante una `RuntimeException`. Si el contador se incrementara dentro
de la transacción del ingreso, **ese incremento se deshacería junto con todo lo
demás**: el contador quedaría siempre en cero, la cuenta no se bloquearía nunca,
y el código se vería perfectamente correcto.

Por eso existe `RegistroDeIntentos`, en un archivo aparte y con
`@Transactional(propagation = REQUIRES_NEW)`: suspende la transacción del
ingreso, abre una propia, guarda el intento y la confirma. Tiene que ser una
clase separada porque llamar a un método `@Transactional` desde otro método de
la misma clase no pasa por el proxy de Spring y la anotación se ignora.

Es el mismo problema que `ESTADO.md` anota para `GastoService`: **una excepción
no sirve como control de flujo cruzando un límite transaccional.**

### Lo que este mensaje revela, y por qué se acepta

Decir "esta cuenta está bloqueada" admite que la cuenta existe, y el resto del
archivo se cuida de no revelar eso. La contradicción es real y la decisión es
deliberada:

- Lo que protege el sistema es la contraseña, no la lista de nombres. En Granica
  las cuentas son tres y se llaman por el nombre de pila de gente que cualquiera
  que conozca la empresa ya conoce.
- Un mensaje genérico dejaría al usuario legítimo sin entender por qué no entra
  si está escribiendo bien la contraseña.

Es el mismo criterio que ya se aplicaba con las cuentas dadas de baja.

### El costo

El bloqueo es por cuenta, así que alguien puede dejar afuera al dueño a
propósito fallando cinco veces contra su usuario. Se acepta: dura quince
minutos, se libera solo y queda anotado en la auditoría.

---

## 3. Cambio obligatorio de contraseña

### El problema

La migración `V13` crea la cuenta `ricardo` con la contraseña `granica2026`
escrita en el archivo, y ese archivo está versionado en GitHub. Es correcto para
poner el sistema en marcha —alguien tiene que poder entrar la primera vez— y
deja de serlo apenas el sistema es alcanzable desde internet.

Hasta ahora, cambiarla era una recomendación escrita en `ESTADO.md`.

### Qué se construyó

Una cuenta queda obligada a cambiar su contraseña en dos momentos, y los dos son
el mismo caso de fondo: **existe una contraseña que conocen dos personas.**

1. Cuando el dueño crea la cuenta (él eligió la contraseña inicial).
2. Cuando el dueño resetea la contraseña de otro.

`V14` marca además todas las cuentas existentes, incluida `ricardo`.

Por eso en la entidad hay dos métodos y no uno con un booleano:

| Método | Quién lo usa | Efecto |
| --- | --- | --- |
| `cambiarContrasena()` | El titular elige una propia | **Levanta** la obligación |
| `resetearContrasena()` | El dueño le pone una a otro | **Impone** la obligación |

Con un solo método y un parámetro, un reseteo dejaría en pie una contraseña
compartida sin que nadie lo note.

### La obligación es del backend, no del frontend

`FiltroCambioDeContrasena` rechaza con 403 **toda** petición de una cuenta en
esa situación, salvo tres:

| Se permite | Por qué |
| --- | --- |
| `GET /api/sesion` | Es como el frontend se entera de que está trabado |
| `PATCH /api/usuarios/{propio id}/contrasena` | Es la única salida |
| `OPTIONS` | La consulta previa de CORS; no lleva datos |

El frontend además lleva al usuario directo a `/mi-cuenta`, pero eso es
comodidad: quien tiene una contraseña que no le pertenece no va a usar la
pantalla, va a llamar a la API. **Una obligación que vive en el navegador se
saltea sin herramientas especiales.**

El 403 viaja con `error: "CAMBIO_DE_CONTRASENA_PENDIENTE"` para que el frontend
lo distinga de un "no tenés permiso" común sin tener que leer el texto del
mensaje, que puede cambiar.

### Un detalle que hace la diferencia

La contraseña nueva no puede ser igual a la actual. Sin esa validación, el
cambio obligatorio se saltea solo: alcanzaría con volver a escribir la
contraseña conocida para que el sistema la diera por cambiada.

---

## 4. Renovación de la sesión

### El problema

El token dura ocho horas contadas desde el ingreso. A media tarde el dueño se
quedaba afuera en medio del trabajo, aunque hubiera estado usando el sistema
todo el día.

### Qué se construyó

Renovación deslizante. Cuando a una petición válida le queda menos de un cuarto
de la duración del token (dos horas de ocho), el backend emite uno nuevo y lo
devuelve en la cabecera `X-Token-Renovado`. El interceptor de Axios lo guarda,
en un solo lugar para los catorce módulos.

**El efecto:** quien trabaja nunca se cae; quien deja la pestaña abierta y se va
vence igual, porque nadie está renovando nada.

### Por qué no un *refresh token*

La solución clásica es un segundo token de refresco, pero obliga a guardarlo en
una tabla y a poder revocarlo: le agrega estado al servidor, que es justo lo que
este diseño evita.

### La trampa de CORS

La cabecera tiene que estar declarada en `setExposedHeaders` de `CorsConfig`. El
navegador no deja leer una cabecera de respuesta que el servidor no autorizó
explícitamente, y sin esa línea la renovación pasaría inadvertida: el backend
mandaría el token, el JavaScript no lo vería, y la sesión se caería igual a las
ocho horas. El síntoma sería "a veces se cierra sola", que es de los más
difíciles de diagnosticar.

---

## 5. Auditoría de las acciones sensibles

`ServicioAuditoria` ya definía el criterio: *si el día de mañana alguien
pregunta "quién hizo esto", ¿la respuesta importa?* Pero solo estaban
enganchadas las acciones de Usuarios, Accesos y el ingreso.

Se agregaron las que faltaban:

| Acción | Módulo | Por qué |
| --- | --- | --- |
| Aprobación de un presupuesto | Presupuestación | Fija el precio que se le cobra al cliente y pone la obra en marcha |
| Aprobación de un pedido | Compras | El informe la define como indelegable; esto la vuelve verificable |
| Anulación de un pedido | Compras | |
| Anulación de un gasto | Gastos | Cambia el desvío del rubro y la ganancia de la obra |
| Registro de un cobro | Cobros | Afirma un hecho del mundo real que no se puede verificar en otra pantalla |
| Anulación de un cobro | Cobros | |
| Cancelación de una obra | Obras | |
| Bloqueo de una cuenta | Accesos | Es la señal de que alguien estuvo probando contraseñas |

Los intentos fallidos sueltos **no** se auditan: un error de tipeo no le interesa
a nadie y llenaría la tabla. Solo el bloqueo.

---

## 6. Una regla del informe que no estaba implementada

El informe dice:

> Una obra con presupuesto definitivo aprobado no puede cancelarse salvo
> autorización explícita del dueño.

Estaba anotada como `TODO` en `ObraService` desde el módulo Obras, esperando que
existiera Presupuestación. Presupuestación se construyó y el `TODO` quedó.

### Qué se construyó

Al cancelar, si la obra tiene un presupuesto definitivo aprobado, hace falta
`confirmaObraEnEjecucion: true` además del motivo. Sin eso el backend rechaza la
cancelación.

**La diferencia es de fondo:** cancelar una obra que todavía se estaba
presupuestando es descartar una propuesta que no prosperó; cancelar una con el
definitivo aprobado es interrumpir una obra en marcha, con material comprado,
gente asignada y cuotas emitidas. Lo segundo no puede pasar por un clic de más.

No alcanza con exigir el rol Dueño —solo el dueño llega a esa pantalla de todos
modos—: lo que hace falta es que la decisión sea **deliberada**.

### Una dependencia nueva, en un solo sentido

`ObraService` necesita saber si la obra tiene un definitivo aprobado, así que
ahora depende de `PresupuestoRepository`. Se inyecta el **repositorio** y no
`PresupuestoService`, porque ese servicio ya depende de `ObraRepository` y pedir
el servicio entero cerraría un ciclo entre los dos módulos.

---

## 7. Tablero reducido para el Capataz General

La matriz del informe le da al Capataz General acceso de **consulta (reducido)**
al tablero, y **ningún** acceso a Presupuestación ni a Cobros. Hasta ahora veía
el mismo tablero que el dueño, con la ganancia de cada obra y el saldo por
cobrar.

### Qué se oculta

| Sale | Queda |
| --- | --- |
| Lo presupuestado y la ganancia (Presupuestación) | Lo gastado y el semáforo (Gastos es Consulta para el rol) |
| El saldo, las cuotas vencidas y los vencimientos (Cobros) | El avance físico y el desfasaje |
| Los pendientes de presupuestos y de cobros | Los pedidos pendientes |

En pantalla, las dos tarjetas financieras del encabezado se reemplazan por
"Obras excedidas" y "Obras desfasadas", y las dos columnas financieras de la
tabla desaparecen. Un tablero lleno de guiones parece roto.

### Dos decisiones

**Se arma completo y después se recorta.** Parece más prolijo no calcular lo que
no se va a mostrar, y sería más rápido. No se hizo así porque, con el recorte
repartido en cada paso del armado, alcanzaría con agregar un campo nuevo y
olvidarse de una rama para que una cifra financiera se le escape al capataz sin
que nadie lo note. Con un único punto de recorte, lo que se oculta se lee de un
vistazo y se prueba en un solo lugar. El costo —calcular de más para un rol que
entra poco, con menos de diez obras activas— es despreciable frente a filtrar
mal.

**Los campos ocultos viajan en `null`, no en cero.** Cero es una afirmación —"no
hay nada por cobrar"— y sería mentira. `null` dice "esto no es asunto tuyo", y
la función `pesos()` del frontend lo muestra como un guion. Un cero de verdad
sigue mostrándose como `$ 0`.

---

## 8. El plan de cobro NO se genera automáticamente

`12-cobros.md` anotaba como pendiente *"generar el plan automáticamente al
aprobar el definitivo"*. **Se decidió no hacerlo**, y el motivo es de negocio,
no técnico.

El presupuesto definitivo trae el porcentaje de anticipo, la cantidad de cuotas
y el total. Falta un solo dato para armar el plan: **la fecha del primer
vencimiento**, que se pacta con el cliente y no está en ningún presupuesto.

Generar el plan solo obligaría al sistema a inventar esa fecha, es decir, a
inventar un compromiso de pago que el cliente nunca aceptó. Las cuotas
quincenales siguientes se calculan a partir de ahí, así que el error se
arrastraría a todo el plan.

**Lo que sí se hizo:** el tablero reclama las obras en ejecución sin plan de
cobro, como pendiente de urgencia alta. La obra arrancó y todavía no hay de
dónde cobrarla, que es exactamente el tipo de cosa que hoy se le traspapela al
dueño — y el tablero existe para reemplazar esa memoria.

---

## 9. Archivos: peso, rotación y huérfanos

### Las fotos se achican en el navegador

Una foto de celular pesa entre 3 y 4 MB y el sistema la muestra a 160 px.
`comprimirImagen.js` la reduce a 1600 px de lado mayor y la exporta como JPEG al
82 % de calidad: baja alrededor del 90 % sin volver ilegible el texto de un
remito.

Importa por tres motivos concretos:

1. El capataz sube el remito desde la obra, con datos del celular.
2. El plan gratuito de Supabase Storage da 1 GB: a 4 MB por foto son 250
   remitos; a 250 KB, cuatro mil.
3. La foto se vuelve a descargar cada vez que alguien abre la pantalla.

**De paso resuelve la rotación**, que era otro pendiente.
`createImageBitmap(archivo, { imageOrientation: 'from-image' })` le pide al
navegador que aplique la marca EXIF al decodificar, así que lo que se dibuja ya
sale derecho. Como el resultado es una imagen nueva sin EXIF, deja de depender
de cómo la interprete cada visor.

**Nunca falla.** Si el archivo es un PDF, si el navegador no sabe decodificar el
formato (HEIC de iPhone) o si la imagen está dañada, devuelve el archivo
original y la subida sigue. Comprimir es una mejora; que el capataz no pueda
registrar el remito porque la compresión falló sería un problema peor.

### Los archivos huérfanos ya se pueden borrar

Los archivos se suben **antes** de guardar el formulario —el capataz ve la foto
antes de confirmar— así que quien sube una foto y cierra el formulario deja un
archivo que no referencia nadie.

`DELETE /api/archivos` los borra, con una regla que lo vuelve seguro:
`ArchivosEnUso` verifica que **ninguna tabla** apunte a esa referencia. Un remito
ya confirmado o una imagen del portfolio no se pueden borrar por esa vía aunque
alguien mande su referencia a propósito.

Ante la duda contesta que sí está en uso: no borrar un archivo huérfano
desperdicia unos kilobytes; borrar uno en uso deja un remito sin su foto y no se
puede recuperar.

El frontend lo llama al **quitar** una imagen y al **reemplazarla** (en ese caso,
después de que la nueva subió bien: si se borrara antes y la subida fallara, el
usuario se quedaría sin ninguna de las dos).

**Lo que queda afuera:** si alguien sube una foto y cierra la pestaña de golpe,
el archivo sigue quedando huérfano. Cubrirlo requeriría una limpieza periódica
que recorra el almacenamiento.

---

## 10. Reordenar las fotos del portfolio

La primera imagen es la portada con la que la obra se presenta en la vidriera al
cliente referido. Hasta ahora quedaban en el orden en que se subieron, y
cambiarlo obligaba a borrarlas todas y volver a subirlas.

`PUT /api/portfolio/{id}/imagenes/orden` recibe la lista **completa** de ids en
el orden deseado, no un par "imagen, posición nueva". Mover una sola imagen
obliga a correr a todas las que están entre su posición vieja y la nueva, y esa
cuenta hecha de a una deja huecos y posiciones repetidas en cuanto dos
operaciones se pisan. Con la lista entera, el orden guardado es exactamente el
que el usuario ve.

Se valida que la lista tenga las mismas imágenes que la publicación, sin
repetidos: una lista incompleta dejaría imágenes con el orden viejo mezcladas
con las nuevas.

En pantalla se puede arrastrar **y** mover con botones ‹ ›. Los botones no
sobran: el arrastre de HTML no responde al dedo en una pantalla táctil, así que
sin ellos el portfolio no se podría ordenar desde el celular ni con el teclado.

---

## 11. TODO que ya estaban resueltos

Cuatro comentarios quedaron desactualizados cuando los módulos que esperaban se
construyeron. Se corrigieron para que el código no mienta:

| Archivo | Decía | Realidad |
| --- | --- | --- |
| `PresupuestoService` | Restringir la aprobación al rol dueño | Ya lo hace: `presupuestos.editar` lo tiene solo el Dueño |
| `PedidoService` | Tomar el usuario de la sesión | Ya lo toma, en la línea siguiente |
| `ObraService` | Pasar a ejecución al aprobar el definitivo | Ya lo dispara `PresupuestoService` |
| `ObraService` | Finalizar al completar el último hito | Ya lo dispara `SeguimientoService` |

---

## 12. Cómo se verificó

**274 tests automáticos**, sin fallos (26 más que antes).

Y, como los tests con mocks no ejercitan Spring Security, Hibernate ni Flyway,
se ejercitó el backend real por HTTP. Lo verificado:

| Qué | Resultado |
| --- | --- |
| Flyway aplica `V14` y Hibernate valida el esquema | El backend arranca con `ddl-auto=validate` |
| El login avisa `debeCambiarContrasena` | ✅ |
| Una cuenta obligada recibe 403 en el resto del sistema | ✅ con el código esperado |
| `GET /api/sesion` sigue permitido | ✅ |
| Rechaza repetir la misma contraseña | ✅ |
| Tras el cambio, el sistema se habilita | ✅ |
| El quinto intento fallido bloquea la cuenta | ✅ |
| Bloqueada, **no entra ni con la contraseña correcta** | ✅ — confirma el orden del chequeo |
| Un token recién emitido no se renueva | ✅ |

**Lo que no se verificó en navegador:** el flujo de cambio obligatorio y el
reordenamiento de fotos se probaron a nivel de API y de compilación, no con
Puppeteer.

---

## 13. Lo que este trabajo NO resolvió

| Pendiente | Por qué |
| --- | --- |
| Limpieza de archivos huérfanos por cerrar la pestaña | Requiere una tarea periódica que recorra el almacenamiento |
| `AlmacenSupabase` contra Supabase real | Nunca se probó: hace falta la cuenta |
| La imagen de Docker | Nunca se construyó: no hay Docker en esta máquina |
| `gasto.id_subrubro`, ¿obligatorio? | El informe se contradice; hay que resolverlo con Ricardo |
