# 19 — Auditoría previa a la entrega

> Revisión completa del sistema antes de entregárselo a Granica SRL para uso
> real: verificación de que esté completo respecto del informe, y auditoría de
> seguridad.
>
> Se documenta con el mismo criterio que el resto: qué se encontró, qué se hizo
> y por qué. Incluye lo que se buscó y **no** se encontró, que para una auditoría
> vale tanto como lo que sí.

**Fecha:** 22–23/09/2026
**Alcance:** los 14 módulos, backend y frontend
**Migraciones:** `V15__version_de_sesion.sql`, `V16__pagos_parciales.sql`

---

## 1. Qué se revisó

| Frente | Método |
| --- | --- |
| Completitud de los 14 módulos | Inventario de entidad, repositorio, servicio, controlador y vista, contra el informe |
| Autorización | Los 122 endpoints, uno por uno, contra la matriz de permisos del informe |
| IDOR | Dónde se aplica `AlcanceDeObras` y qué queda sin cubrir |
| Inyección SQL | Búsqueda de queries nativas y concatenación de strings |
| Validación de entrada | Bean Validation en todos los DTOs de entrada, con foco en los montos |
| Secretos | Qué está versionado en el repositorio |
| Código muerto | TODO, FIXME, datos de prueba, componentes sin usar |

---

## 2. Lo que se buscó y NO existe

Vale la pena dejarlo escrito, porque varias preocupaciones razonables resultaron
no aplicar a este sistema:

| Se buscó | Resultado |
| --- | --- |
| Módulo "Reportes" faltante | **No existe en el informe.** Son 14 módulos; los reportes son vistas dentro de Gastos y Cobros, y están hechas |
| Usuario placeholder sembrado | **No existe.** Los cinco `TODO` de "vincular a usuario real" se habían resuelto con `SesionActual` |
| Inyección SQL | **Cero vectores.** Ninguna query nativa, ninguna concatenación; el único `EntityManager` usa `setParameter` |
| Endpoints sin autorización | **122 endpoints, todos con control**, salvo cinco públicos por diseño |
| Matriz de permisos desalineada | `V13` coincide **exactamente** con la matriz del informe, para los tres roles |
| Montos negativos | `@Positive` / `@DecimalMin` / `@PositiveOrZero` en todos los DTOs de entrada |
| Semáforo mal calibrado | Exacto: rojo >100 %, amarillo ≥90 %, verde <90 % |
| Secretos versionados | `.env` ignorado; sin credenciales en el código |
| Registro público de usuarios | No existe: crear cuenta exige `usuarios.editar` |

### Las cinco rutas públicas, y por qué

| Ruta | Motivo |
| --- | --- |
| `POST /api/sesion` | El login no puede exigir estar logueado |
| `GET /api/estado` | Diagnóstico: tiene que responder aunque nadie haya entrado |
| `GET /api/vidriera/**` | La vidriera del portfolio es pública por diseño |
| `GET /api/archivos/publico/**` | Las fotos del portfolio, por el mismo motivo |
| `OPTIONS /**` | La consulta previa de CORS; no lleva datos |

### Sobre el IDOR

`AlcanceDeObras` se aplica en Obras, Compras y Seguimiento, que son exactamente
los tres módulos que la matriz le da al Capataz de Obra con la aclaración "(su
obra)". El resto de los módulos se los bloquea el permiso: un capataz de obra no
tiene `gastos.ver`, `cobros.ver` ni `presupuestos.ver`, así que no llega ni a
pedirlos.

Verificado por HTTP con una cuenta de capataz real: no puede abrir una obra
ajena por id, ni ver su avance, ni acceder a Gastos o Cobros.

---

## 3. Hallazgo Alto — la sesión no moría nunca

**Lo introdujo el trabajo del 17/09**, al agregar la renovación deslizante del
token.

### El problema

El token se renovaba mientras se usaba, y esa cadena no tenía fin. Un token
robado, usado cada tanto por quien lo robó, se renovaba **indefinidamente**:
la sesión no vencía jamás. Antes de la renovación, el token moría a las ocho
horas sí o sí.

Y no había forma de cerrar una sesión ya abierta: si a Ricardo le robaban el
teléfono, la única salida era dar de baja la cuenta entera.

### La solución, en dos partes

**Tope absoluto.** El token lleva adentro cuándo **empezó la sesión**, y las
renovaciones **conservan** ese instante en lugar de grabar el actual. Pasadas 24
horas desde el ingreso, la cadena se corta. Para el dueño es escribir la
contraseña una vez por día; para quien se hizo del token, es el plazo en que
deja de servirle.

El test `renovarConservaElInicio` es el que fija esto: si alguien hiciera que la
renovación grabara el instante actual, el tope no llegaría nunca y ese test se
pondría en rojo.

**Versión de sesión.** Un contador en `usuario` que viaja dentro del token y que
`FiltroJwt` compara en cada petición. Incrementarlo deja sin efecto, de golpe,
todos los tokens emitidos hasta ese momento. Cambiar la contraseña lo
incrementa.

Por qué un contador y no una lista de tokens revocados: una lista hay que
guardarla, consultarla y limpiarla, y crece sin límite. El contador se compara
contra el usuario que el filtro **ya consulta** en cada petición —lo hace desde
Accesos, para que un permiso revocado se aplique en el acto— así que no agrega
ni una consulta.

### El detalle que lo hace usable

Cambiar la propia contraseña invalida las sesiones, **incluida la que se está
usando**. Sin nada más, quien acaba de cambiarla quedaría afuera sin entender por
qué. El backend le devuelve un token nuevo por la cabecera `X-Token-Renovado`,
la misma que ya usaba la renovación: el interceptor de Axios lo guarda solo.

Los tokens anteriores quedan igualmente muertos, que es el objetivo.

### Los tokens viejos se rechazan

Un token emitido antes de `V15` no tiene los claims de versión ni de inicio, así
que no se le puede poner tope ni cortar. Se rechaza. Quien lo tenga vuelve a
entrar una vez: es el precio de cerrar el hueco.

---

## 4. Hallazgo Medio — contraseñas previsibles

El mínimo era de 8 caracteres y no había nada más. `12345678` y `granica2026`
eran contraseñas válidas.

Importa por el momento en que se aplica: desde el 17/09 el sistema **obliga** a
cambiar la contraseña en el primer ingreso, y es justo cuando más tienta poner
cualquier cosa para salir del paso.

**Qué se hizo:** mínimo de 10 caracteres, y `PoliticaDeContrasenas` rechaza las
que nombran a la empresa, las secuencias de teclado, las que repiten siempre el
mismo carácter y las que contienen el propio nombre de usuario.

**Qué NO se hizo, a propósito:** exigir mayúsculas, números y símbolos. Es la
regla más común y la que peor funciona: empuja a todo el mundo a la misma
contraseña previsible —una mayúscula al principio, un número y un signo al
final— y termina anotada en un papel. `Granica2026!` cumple todas las reglas de
complejidad y es la primera que probaría alguien que conoce la empresa.

**Efecto secundario que conviene saber:** `granica2026` ya no se puede volver a
poner. Se puede entrar con ella —el hash está en la base desde `V13`— pero al
cambiarla, el sistema no la acepta de vuelta.

---

## 5. Tres reglas del informe que estaban incompletas

### El tipo de obra no se podía corregir nunca

El informe lo bloquea *"apenas existe un presupuesto de anteproyecto o
definitivo"*. La implementación era más restrictiva: no se podía cambiar nunca,
así que una obra cargada con el tipo equivocado no tenía arreglo — había que
cancelarla y rehacerla, perdiendo su historial.

Ahora se corrige mientras la obra no tenga ningún presupuesto.

### La fecha de inicio real se validaba con un proxy

La regla es "no hasta que el definitivo esté aprobado", pero se comprobaba
`obra.estaEnPresupuestacion()`. Casi lo mismo, pero no: una obra pasada a
ejecución a mano —transición que existe y es válida— admitía la fecha sin tener
ningún definitivo aprobado. Ahora se comprueba contra el presupuesto.

### Una observación podía colgar de un pedido ajeno

`ObservacionProveedor` guarda el pedido que la originó, y no se verificaba nada.
Se podía registrar una queja contra un pedido inexistente o, peor, contra el
pedido de **otro** proveedor: el historial de comportamiento que el dueño usa
para decidir a quién comprarle quedaba contaminado.

Ahora se verifica que exista y sea de ese proveedor. El vínculo sigue siendo
opcional, porque no toda queja nace de un pedido.

---

## 6. Código muerto con datos ficticios

`frontend/src/datos/` (`demo.js`, `DemoProvider.jsx`, `contextoDemo.js`) y
`app/paginas/ModuloPendiente.jsx`: 308 líneas de obras, hitos y rubros
**inventados**.

`DemoProvider` envolvía toda la aplicación desde `main.jsx`, pero **ningún
componente consumía su contexto**: quedó de cuando las pantallas del capataz
usaban datos de muestra, antes de conectarlas al backend. Eliminado.

Se eliminaron también cinco `TODO` que describían el sistema como era antes de
Accesos ("al integrar Accesos, restringir al rol dueño") cuando ya estaba
restringido. Un comentario que miente sobre el estado del código es peor que no
tenerlo.

---

## 7. Pagos parciales — alcance nuevo

**No es una corrección: el informe no pide pagos parciales.** Define la cuota
con estados Pendiente / Abonada / Vencida, o sea que se cobra entera o no se
cobra, y eso es lo que el sistema hacía. Se agregó a pedido de Santino.

Está documentado aparte en `docs/PREGUNTAS-PARA-RICARDO.md`, punto 3, porque el
cliente tiene que saber que el sistema hace algo que su informe no describe.

### Las decisiones de diseño

**Una tabla y no una columna `monto_pagado`.** Con una columna se sabría cuánto
entró y nada más: se perdería cuándo se pagó cada parte, por qué medio y con qué
comprobante — justo lo que hace falta cuando el cliente pregunta si una
transferencia quedó registrada. Mismo criterio que Gastos, que no guarda un total
por rubro sino cada gasto.

**El estado se deriva, no se marca.** Nadie pone una cuota en "Abonada": se
registran pagos y el estado sale de ahí. Así no puede existir una cuota Abonada
con saldo pendiente, que es la inconsistencia típica cuando el estado se escribe
en un lugar y los montos en otro.

**Una cuota parcial vencida sigue vencida.** Que haya entrado algo no cambia que
el resto está impago y fuera de término: es lo que hay que reclamar, así que
tiene que seguir apareciendo en las alertas.

**El CAC ajusta solo el saldo impago:**

```
nuevoMonto = totalPagado + (saldo × coeficiente)
```

Multiplicar el monto entero encarecería retroactivamente una parte ya cobrada, y
el cliente terminaría debiendo plata por algo que ya pagó.

### La migración convierte datos

`V16` genera un pago por cada cuota hoy Abonada, con su fecha y su medio. **Sin
ese paso, todas las obras ya cobradas figurarían impagas** y el sistema le diría
a Ricardo que reclame plata que ya cobró.

Verificado contra la base real antes y después: 3 cuotas por **$13.706.666,66**
convertidas, **descuadre $0,00**, ninguna cuota abonada sin su pago.

---

## 8. Cómo se verificó

**301 tests automáticos**, sin fallos (27 más que antes de la auditoría).

Y contra el backend real por HTTP, porque los mocks no ejercitan Spring
Security, Flyway ni Hibernate — y esta vez había una migración que convierte
datos:

| Qué | Resultado |
| --- | --- |
| Migración de pagos sin perder plata | ✅ descuadre $0,00 |
| El token anterior queda invalidado al cambiar la contraseña | ✅ 401 |
| El token nuevo llega por la cabecera y funciona | ✅ |
| Rechaza contraseñas con el nombre de la empresa | ✅ |
| Rechaza contraseñas con el nombre de usuario | ✅ |
| Rechaza contraseñas de menos de 10 caracteres | ✅ |
| Pago parcial: estado, monto pagado y saldo | ✅ |
| Rechaza cobrar más que el saldo | ✅ |
| Completar la cuota la deja Abonada | ✅ |
| Anular devuelve la cuota a deberse entera | ✅ |
| La planilla PDF sigue generándose | ✅ |
| El capataz no abre una obra ajena por id | ✅ |
| El capataz no accede a Gastos ni a Cobros | ✅ |

Antes de correr nada se hizo un `pg_dump` de la base de desarrollo, porque `V16`
convierte datos y no tiene vuelta atrás.

---

## 9. Lo que queda abierto

| Qué | Por qué |
| --- | --- |
| Las dos contradicciones del informe | Son decisiones de negocio: `docs/PREGUNTAS-PARA-RICARDO.md` |
| Sin límite de peticiones fuera del login | Solo el ingreso tiene límite. Con tres a diez usuarios internos es menor, pero conviene saberlo |
| Poder cargar gastos sin poder anularlos | Hoy `gastos.editar` habilita las dos cosas. Separarlo requiere tocar código |
| `AlmacenSupabase` contra Supabase real | Nunca se probó: hace falta la cuenta |
| La imagen de Docker | Nunca se construyó: no hay Docker en esta máquina |
| Limpieza de archivos huérfanos por cerrar la pestaña | Requiere una tarea periódica |
