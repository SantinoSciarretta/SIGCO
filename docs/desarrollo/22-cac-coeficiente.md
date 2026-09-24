# 22 — El CAC se carga como coeficiente

**Fecha:** 24/09/2026
**Origen:** lo reportó Ricardo usando el sistema: *"el índice CAC está
multiplicando mal (…) ahora pusimos 1.6 y las cuotas aumentaron una
barbaridad"*.

---

## Qué estaba pasando

La tabla `registro_cac` guardaba el **nivel** del índice —el número que publica
la Cámara Argentina de la Construcción, del orden de 1.200 puntos— y el sistema
sacaba el coeficiente de actualización dividiendo un mes por el anterior:

```
coeficiente = índice del mes / índice del mes anterior
```

Eso es lo correcto para el índice publicado: un nivel suelto no dice cuánto
subieron los costos, lo que importa es cuánto creció respecto del mes pasado.

**Pero no es lo que Ricardo carga.** Él no busca el índice de la Cámara: decide
cuánto quiere actualizar y escribe ese número. Con los dos meses que había
cargados —0,1 y 1,6— la división daba **16**, y las cuotas se multiplicaban por
dieciséis.

No era un error de redondeo ni un bug de cálculo: el campo decía "índice" y
esperaba un nivel, y quien lo cargaba escribía un multiplicador. **La división
estaba bien hecha sobre datos que significaban otra cosa.**

---

## Qué guarda ahora

El **coeficiente del mes**: por cuánto se multiplican las cuotas pendientes.

| Se carga | Una cuota de $1.000 pasa a |
| --- | --- |
| 1,4 | $1.400 |
| 1,05 | $1.050 |
| 1 | $1.000 (no cambia) |

Con un solo mes alcanza. Antes hacían falta dos, porque el coeficiente salía de
compararlos.

### La columna se llama `coeficiente`

Se podría haber dejado `valor_indice` cambiándole el significado. No se hizo a
propósito: **fue justamente esa distancia entre el nombre y el contenido la que
produjo el error.** Un campo llamado "índice" que guarda 1,4 vuelve a invitar a
la misma confusión dentro de seis meses.

Es el único cambio de esta lista que se aparta del Diccionario de Datos por una
razón de claridad y no de funcionalidad. Está anotado en
`docs/CAMBIOS-PARA-LA-PROPUESTA-TECNICA.md`.

### Los valores que había se borraron

Eran niveles de índice, no coeficientes, y **no se pueden reinterpretar**: un
0,1 leído como coeficiente significaría bajar las cuotas a la décima parte.
Dejarlos era peor que no tenerlos.

Se pudo borrar sin riesgo porque el sistema todavía no está publicado y los
únicos registros eran de prueba. Si hubiera habido datos reales, la migración
tendría que haberlos convertido y no vaciado — está dicho así en el comentario
de `V21`, porque es la clase de decisión que hay que poder justificar después.

---

## Las dos redes contra un número mal cargado

### El tope de 10

`CHECK (coeficiente > 0 AND coeficiente <= 10)`, más la misma validación en el
DTO con un mensaje que explica el malentendido:

> El coeficiente parece un valor del índice publicado. Acá va por cuánto se
> multiplican las cuotas: 1,4 sube un 40%.

**No es una regla de negocio.** Nadie va a actualizar un 900% en un mes; el tope
existe para atrapar exactamente el error que motivó todo esto: cargar 1.234,5
creyendo que es el multiplicador.

### La vista previa, que es la protección real

Antes de aplicar nada, la pantalla muestra en pesos cuánto pasa a deberse:

```
Mes                      sep 2026
Se multiplica por        1,4 (sube 40%)
Saldo pendiente actual   $ 17.293.853,77
Saldo actualizado        $ 24.211.395,28
```

Un número absurdo se nota ahí. Ningún `CHECK` puede saber si 1,4 es correcto
para este mes; lo que sí se puede es mostrar el resultado antes de tocar las
cuotas.

---

## Lo que no cambió

- **El CAC se aplica solo sobre el saldo impago.** Con pagos parciales, una
  cuota de $1.000 con $400 ya cobrados y coeficiente 1,4 pasa a
  `400 + 600 × 1,4 = 1.240`, no a 1.400. Encarecer lo ya pagado sería cobrarlo
  dos veces.
- **Las cuotas abonadas no se tocan.**
- La carga sigue siendo a mano: la importación automática desde la CAC está
  fuera del alcance de esta versión.

---

## Verificación

**Tests (373 en total).** Dos nuevos y directos sobre el caso reportado: que el
número cargado sea el multiplicador (tres cuotas de $1.000 con coeficiente 1,4
dan $4.200 de saldo y cada cuota $1.400), y que un coeficiente de 1 deje todo
igual. El test que exigía dos meses se reemplazó por uno que verifica el
mensaje cuando no hay nada cargado.

**Contra el backend real:** la migración dejó la tabla vacía; se carga 1,4 y
vuelve como 1,4; cargar 1.234,5 devuelve 400 con el mensaje que explica el
malentendido; y la previa sobre la obra de Cabildo da $17.293.853,77 →
$24.211.395,28, que es exactamente × 1,4.

**En el navegador:** la pantalla dice "Se multiplica por 1,4 (sube 40%)",
explica el ejemplo de $1.000 → $1.400, aclara que no es el valor que publica la
Cámara, y ya no pide dos meses.

---

## Algo que conviene tener presente

**Aplicar la actualización dos veces compone.** Si se aprieta "Aplicar" dos
veces con el coeficiente 1,4 cargado, las cuotas quedan multiplicadas por 1,96.
El sistema no lleva registro de qué actualizaciones ya se aplicaron a cada obra.

Esto **ya pasaba antes** de este cambio —no lo introduce— pero ahora es más
visible, porque cada mes es una acción deliberada sobre un número que se escribe
a mano. Hoy lo único que lo evita es la vista previa y que el usuario se dé
cuenta.

Resolverlo bien es guardar en la cuota qué actualización se le aplicó y rechazar
repetirla. Es una columna y una regla; no se hizo acá porque excede lo que se
reportó, pero queda anotado.
