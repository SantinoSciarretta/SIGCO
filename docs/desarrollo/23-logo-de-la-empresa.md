# 23 · El logo de Gránica en el sistema

> Qué se hizo el 28/09/2026: poner el logo de la empresa en los cuatro PDF que
> emite el sistema, en las cuatro pantallas donde se mostraba el nombre escrito
> a mano, y en el icono de la pestaña del navegador.

---

## 1. Un archivo, todos los lugares

El logo vive en **`frontend/src/assets/granica-logo.svg`**. Es la fuente única:
todo lo demás sale de ahí.

| Dónde se ve | Qué usa | Cómo llega |
| --- | --- | --- |
| Barra superior del sistema | `LogoGranica` | el SVG, inyectado por Vite |
| Pantalla de ingreso | `LogoGranica` | ídem |
| Pantallas del capataz (celular) | `LogoGranica` | ídem |
| Vidriera pública | `LogoGranica` | ídem |
| Pestaña del navegador | `public/favicon.svg` | generado del SVG (solo la G) |
| PDF de presupuesto | `EstiloPdf.membrete()` | PNG exportado del SVG |
| PDF de orden de pedido | ídem | ídem |
| PDF de planilla de pagos | ídem | ídem |
| PDF de reporte de gastos | ídem | ídem |

Que sean derivados y no copias es lo que importa: si el logo cambia, se cambia
el SVG, se vuelven a generar los dos derivados (abajo está cómo) y cambia en los
nueve lugares.

---

## 2. En el frontend: por qué se inyecta el SVG y no se usa una `<img>`

El logo tiene que ir sobre **dos fondos**: el azul profundo de la barra y el
blanco de las pantallas. Sus colores originales son casi negros (`#191919` el
marco de la G, `#666666` el interior, `#464646` la palabra), así que sobre el
azul no se vería.

Con una `<img>` el navegador no deja recolorear el contenido del archivo.
Habría que mantener **dos archivos**, uno oscuro y uno claro, y el día que
cambie el logo uno de los dos se va a olvidar.

La solución es inyectar el SVG en el documento. Ahí el CSS sí puede pintarlo:

```css
.logo svg path            { fill: var(--color-accent-900); }
.logo svg path:nth-of-type(2) { fill: var(--color-accent-600); }  /* el interior de la G */

.claro svg path            { fill: #f2f2f3; }
.claro svg path:nth-of-type(2) { fill: rgba(242, 242, 243, 0.45); }
```

**Funciona porque `fill` escrito como atributo en el SVG es un "atributo de
presentación", y esos pierden contra cualquier regla de hoja de estilos.** No
hay que tocar el archivo ni ponerle `fill="currentColor"`.

El `?raw` de Vite mete el contenido del archivo en el bundle en tiempo de
compilación: **no es una petición de red aparte**.

Sobre fondo claro el logo no queda en su negro original sino en el acero del
sistema (`--color-accent-900`). El resto de la interfaz no usa negro puro en
ningún lado, y el logo habría quedado más pesado que todo lo que tiene alrededor.

### El alto se puede fijar o dejar al CSS

`<LogoGranica alto={17} />` fija el alto en el atributo `style`. **La vidriera
no pasa `alto` a propósito**: ahí el logo tiene que achicarse en el celular, y
un alto en `style` le ganaría a cualquier media query. Cuando no se pasa, el
alto lo decide el CSS.

Hace falta porque el logo mide **930 × 99,5**: casi diez veces más ancho que
alto. A 44 px de alto ocupa 410 px de ancho, que en un celular no entra.

---

## 3. El icono de la pestaña es solo la G

`frontend/public/favicon.svg` se queda con **los dos trazos de la G** y recorta
el `viewBox` a su caja.

No es una decisión estética: a 32 px de alto, que es el tamaño real del icono en
la pestaña, la palabra "GRÁNICA S.R.L. CONSTRUCTORA" mediría 3 px de alto y no
se leería nada. La G sola sí se reconoce.

---

## 4. En los PDF: por qué un PNG y no el SVG

**OpenPDF no dibuja SVG.** Solo sabe insertar imágenes de mapa de bits. Así que
el membrete lleva un PNG, en `backend/src/main/resources/documentos/granica-logo.png`,
exportado del mismo SVG a 1860 × 199 px.

Ese tamaño no es arbitrario: en el membrete el logo se dibuja a 185 puntos de
ancho, y 185 pt a 300 dpi son 771 px. Con 1860 px hay margen de sobra para que
imprima nítido, y el archivo sigue pesando poco porque es un logo plano.

### Se cachean los bytes, no el objeto `Image`

```java
private static final byte[] LOGO = cargarLogo();
```

Una `Image` de OpenPDF guarda adentro **en qué documento y en qué posición quedó
colocada**, así que reutilizar la misma instancia en dos PDF distintos la deja
apuntando al documento equivocado. Por eso se cachean los bytes y se crea una
`Image` nueva por documento: eso no cuesta nada, releer el archivo del disco en
cada presupuesto sí.

### Si el logo falta, el documento sale igual

`cargarLogo()` devuelve `null` en vez de propagar la excepción, y `membrete()`
cae al nombre escrito en texto. **Que falte el logo no puede impedir que se
emita un presupuesto.**

### El membrete estaba duplicado, y ahora no

`GeneradorDePdf` (el del presupuesto) es anterior a `EstiloPdf` y tenía **su
propio membrete copiado**, con su propia paleta y sus propias fuentes. Eso
significaba que el logo había que ponerlo en dos lugares, y que el día que
alguien cambiara uno el otro quedaría distinto — que es exactamente lo que el
comentario de `EstiloPdf` advertía desde que se escribió.

Ahora el presupuesto llama a `EstiloPdf.membrete()` como los otros tres. Se
borraron de `GeneradorDePdf` las constantes `TITULO` y `SUBTITULO`, que solo
usaba ese membrete.

Quedan duplicados en ese archivo los helpers `dato()`, `encabezado()`,
`importe()`, `linea()` y `pesos()`, que existen también en `EstiloPdf` con
diferencias chicas de relleno. **No se unificaron**: cambiarlos movería el
diseño del documento que se le manda al cliente, y eso no es parte de poner el
logo. Queda anotado como pendiente menor.

---

## 5. El test que cuida que el logo esté

`MembreteTest` genera un PDF y comprueba que adentro haya un XObject de subtipo
`Image`.

Comprueba algo que **no se ve mirando el código**: que el PNG esté empaquetado
donde el código lo busca. Si alguien mueve el archivo de `resources`, el
membrete sigue compilando y sigue emitiendo el PDF, solo que sin logo. Sin este
test nadie se entera hasta que un presupuesto le llega así a un cliente.

---

## 6. Cómo regenerar los derivados

Si cambia `frontend/src/assets/granica-logo.svg`, hay que rehacer dos archivos.

**El PNG de los PDF** — necesita Node y Chrome:

```js
// exportar-logo.mjs
import puppeteer from 'puppeteer';
import { readFileSync, writeFileSync } from 'fs';

const svg = readFileSync('frontend/src/assets/granica-logo.svg', 'utf8');
const ancho = 1860;
const alto = Math.round(ancho * 99.5 / 930);   // la proporción del viewBox

const nav = await puppeteer.launch({ headless: 'shell' });
const p = await nav.newPage();
await p.setViewport({ width: ancho, height: alto });
await p.setContent(
  `<body style="margin:0"><div style="width:${ancho}px;height:${alto}px">`
  + svg.replace('<svg', '<svg width="100%" height="100%"') + '</div></body>');
writeFileSync('backend/src/main/resources/documentos/granica-logo.png',
              await p.screenshot({ omitBackground: true, type: 'png' }));
await nav.close();
```

`omitBackground: true` es lo que deja el PNG con fondo transparente. Sin eso el
logo llevaría un rectángulo blanco encima de la hoja.

**El favicon** — quedarse con los dos primeros `<path>` del SVG y poner
`viewBox="0 2 183 105"`, que es la caja de la G con algo de aire.

Después, `mvn test -Dtest=MembreteTest` confirma que el PNG quedó donde va.

---

## 7. Una diferencia que apareció: la empresa lleva tilde

El logo dice **GRÁNICA**. Todo el sistema y toda la documentación venían
escribiendo "Granica", sin tilde.

Se corrigieron **los textos que ve el usuario**: el título de la pestaña, el pie
de la vidriera, el catálogo del capataz y el texto de ayuda del catálogo de
rubros. Un logo que dice "GRÁNICA" al lado de un texto que dice "Granica" se lee
como un descuido.

**No se tocaron** los nombres de archivos, clases, variables ni la
documentación, que siguen diciendo "Granica" sin tilde. Eso es un cambio grande,
mecánico y sin efecto sobre lo que ve nadie — y antes de hacerlo conviene
confirmar con Ricardo cómo se escribe el nombre en la razón social, porque el
logo y los papeles de la empresa podrían no coincidir.
