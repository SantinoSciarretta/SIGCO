# 21 — Mandarle la orden al corralón por WhatsApp

**Fecha:** 24/09/2026
**Origen:** idea de Ricardo, posterior a los ocho pedidos del documento 20. Al
ver el PDF de la orden preguntó si el sistema podía mandárselo al corralón por
WhatsApp directamente, teniendo el número cargado.

---

## La pregunta, y por qué la respuesta no es un sí simple

Automatizar WhatsApp tiene tres caminos, y la diferencia entre ellos es de
orden de magnitud:

| Camino | Manda solo | Adjunta el PDF | Qué cuesta |
| --- | --- | --- | --- |
| Link `wa.me` | No | No | Nada |
| WhatsApp Cloud API (Meta) | Sí | Sí | Número dedicado, verificación de empresa, plantillas aprobadas, pago por conversación |
| Librerías no oficiales | Sí | Sí | Viola los términos de WhatsApp; riesgo de que le bloqueen el número |

**Se implementó el primero.** Los motivos, en orden de peso:

1. **El número.** La Cloud API exige un número *dedicado*, que deja de funcionar
   como WhatsApp normal. Ricardo tendría que sacar una segunda línea y
   abandonar la que los corralones tienen agendada hace años. El link usa la
   suya.
2. **El alcance.** La Propuesta Técnica dice, textualmente, que las
   integraciones externas automáticas —y nombra la API de WhatsApp— quedan
   fuera de esta versión. Un link no es esa integración: es la misma tecnología
   que un `mailto:`.
3. **El riesgo.** Las librerías no oficiales hacen exactamente lo pedido, pero
   el número por el que pasan los presupuestos, los clientes, los capataces y
   los proveedores es la empresa. Perderlo es peor que no tener la función.

Lo que se pierde es concreto y conviene tenerlo escrito: **el PDF no va
adjunto**, va como link, y **el mensaje no se manda solo** — Ricardo aprieta
enviar. En la práctica el corralón abre el link con un toque.

---

## Cómo funciona

1. En la fila del pedido aparece **WhatsApp**, solo si está aprobado (antes no
   hay proveedor: lo elige el dueño al aprobar).
2. `POST /api/pedidos/{id}/envio-whatsapp` genera un link público para el PDF y
   arma el mensaje.
3. La pantalla muestra **a quién se le va a escribir, el mensaje y el link**,
   antes de abrir nada.
4. "Abrir WhatsApp" lleva a `wa.me/<número>?text=<mensaje>`, en una pestaña
   nueva.

---

## Las decisiones

### El link público del PDF (`V20`)

El corralón no tiene cuenta en SIGCO ni va a tenerla, así que el PDF tiene que
abrirse sin login. Se agregaron a `pedido` dos columnas: `token_orden` y
`token_orden_vence`.

**Un token y no el id.** Una URL con el id (`/ordenes/47`) se recorre cambiando
el número: quien reciba una orden vería todas las demás, de todas las obras y
todos los proveedores. El token son 32 bytes de `SecureRandom` en Base64 —43
caracteres que no se adivinan ni se enumeran.

**Vence a los 30 días.** Un link sin vencimiento queda vivo para siempre en un
chat que se reenvía.

**Y se puede cortar al instante** (`DELETE` de la misma ruta). Al ser una
columna y no un token firmado, poner el token en `NULL` mata el acceso ya. Con
un token firmado habría que esperar a que venza, y si la orden se le mandó al
corralón equivocado, esperar no es una opción.

**Qué se expone.** La orden lleva materiales, cantidades, precios acordados y
dónde entregar. NO lleva presupuesto de la obra, gasto, ganancia ni el nombre
del cliente — se diseñó así desde el documento 20, justamente porque es un
papel que sale de la empresa hacia afuera. La verificación lo comprueba
buscando esas palabras dentro del PDF servido.

**Vive en `/api/ordenes-publicas`,** con su propio prefijo, igual que
`/api/vidriera` y `/api/archivos/publico`. Así leer la configuración de
seguridad alcanza para saber qué queda abierto, sin revisar endpoint por
endpoint.

### El permiso: `compras.aprobar`, no `compras.editar`

Es más fuerte de lo que parece. El informe dice que enviarle el pedido al
proveedor es del dueño y no se delega; los capataces tienen `compras.editar`
para armar pedidos y confirmar recepciones. Si este endpoint pidiera
`compras.editar`, un capataz podría mandarle una orden a un proveedor por su
cuenta y, de paso, generar un link público de un documento de la empresa.

Verificado con la cuenta del capataz general: **403**.

### El teléfono, que es la parte complicada

`NumeroDeWhatsApp` convierte lo que haya cargado al formato que necesita el
link: internacional, solo dígitos, sin `+`.

El problema es el **15**. En Argentina un celular marcado localmente lleva un 15
adelante del abonado, pero en formato internacional ese 15 no va: se reemplaza
por un 9 después del 54. O sea que `011 15 4567 8900` y `+54 9 11 4567 8900` son
el mismo teléfono escrito de dos maneras que no se parecen en nada.

Sacar el 15 exige saber dónde termina el código de área, que puede tener 2, 3 o
4 dígitos (11 para CABA y GBA, 351 para Córdoba, 2494 para un pueblo). Se prueba
en ese orden, y funciona porque ningún código de área argentino empieza con 15.

**Cuando no se entiende, devuelve vacío. No inventa un número probable.** Es la
decisión importante de la clase: si adivináramos mal, el sistema abriría una
conversación con un desconocido y le mandaría el pedido de una obra. En su lugar
aparece un aviso que dice qué corregir, y el link de la orden igual se genera
para copiarlo y mandarlo a mano.

Por el mismo motivo **la pantalla muestra el número antes de abrir el chat**: es
la última oportunidad de darse cuenta.

---

## Dos errores propios que valió la pena encontrar

### El token se regeneraba en cada llamada

La primera versión generaba un token nuevo cada vez que se preparaba el envío,
con la idea de que un link viejo no mostrara una orden desactualizada.

**Esa idea estaba mal.** El PDF se arma en el momento en que se abre el link,
con los datos de ese momento, así que un link viejo nunca muestra algo vencido.

Y tenía un costo real: si dos llamadas se cruzan, la pantalla queda mostrando un
token que la última ya invalidó. Lo encontró la prueba en el navegador, donde
React llama al efecto dos veces en desarrollo. Ahora **se reusa el link vigente**,
que además es lo que uno espera: si el corralón ya lo tiene en el chat, volver a
mandárselo no debería romperle el anterior.

### `innerText` corta las URLs largas

La prueba en el navegador seguía dando 404 después de arreglar lo anterior. La
causa: sacaba la URL de `document.body.innerText`, e `innerText` devuelve el
texto **renderizado**, con los saltos que el navegador mete al envolver una
línea larga. La URL se cortaba justo donde se envolvía.

Era un 404 de la prueba, no del sistema. Se corrigió leyendo `textContent`. Es
la segunda trampa de Puppeteer de este proyecto, después de la de
`text-transform: uppercase`.

---

## Verificación

**Tests (29 nuevos, 368 en total).** Quince son del teléfono solo: el mismo
celular escrito de siete maneras, códigos de área de tres y cuatro dígitos, y
seis casos que no se pueden interpretar y tienen que devolver vacío. El resto
cubre el armado del link, la reutilización del token, el vencimiento, el corte
inmediato y los pedidos sin proveedor o anulados.

**Contra el backend real,** con el teléfono del proveedor cambiado y restaurado
al final: normaliza `011 15 4567-8900` a `5491145678900`; el texto del link es
exactamente el mensaje; el PDF se abre **sin sesión** (9,4 KB, `%PDF-`); no
contiene el nombre del cliente ni las palabras Ganancia, Presupuesto de la obra
ni Desvío; un token inventado da 404; preparar de nuevo devuelve el mismo token;
cortarlo lo deja en 404; con el teléfono ilegible no arma link y avisa; y el
capataz general recibe 403.

**En el navegador:** el botón aparece solo en los pedidos aprobados, el modal
muestra el número normalizado y el mensaje completo, el enlace apunta a `wa.me`
con el texto adentro y se abre en pestaña nueva, y el link de la orden abre el
PDF en un navegador sin sesión.

---

## Para publicar

Hace falta una variable de entorno nueva:

```
URL_PUBLICA=https://<el-backend-en-railway>
```

Es la dirección por la que se llega al backend **desde afuera**. Ese link se abre
en el teléfono de otra persona, en otra red: `localhost` no sirve. Está en
`backend/.env.example`; en desarrollo toma `http://localhost:8080` sola.

---

## Si algún día se quiere el envío automático de verdad

Queda preparado. El mensaje ya está armado del lado del servidor y el PDF ya
tiene una URL pública, que es justamente lo que la Cloud API necesita para
mandar un documento. Lo que faltaría es el trámite con Meta —número dedicado,
verificación de la empresa, plantilla aprobada— y reemplazar la devolución del
link por una llamada HTTP. El resto no cambia.
