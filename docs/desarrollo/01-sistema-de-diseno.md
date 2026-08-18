# 01 — Sistema de diseño e implementación de las pantallas

> Registra la identidad visual del sistema y las pantallas construidas a partir
> del diseño aprobado. Es el paso previo obligatorio al desarrollo de los
> módulos: todo lo que se construya de acá en adelante hereda estos tokens y
> estos componentes.

**Fecha:** 18/08/2026
**Origen del diseño:** proyecto "SIGCO app para Granica SRL" en Claude Design,
archivo `SIGCO.dc.html`, sobre el sistema de diseño **Industry**.

---

## 1. Cómo se decidió la identidad visual

El plan original era elegir entre tres paletas azules preparadas para este
proyecto. Ese camino quedó descartado: el diseño se resolvió en Claude Design y
se importó ya completo, con paleta, tipografía, componentes y seis pantallas
diseñadas. Lo que sigue documenta lo que efectivamente se implementó.

El sistema **Industry** se puede describir en una frase: **wireframe técnico,
acero sobre papel**. Los bloques son objetos de plano —rectángulos de línea
fina, esquinas vivas, fondo transparente— con marcas de registro en las cuatro
esquinas. En cada pantalla hay un número protagonista en tipografía condensada
grande, y el resto es línea.

Cumple con lo que pedía el informe (azul primario, tonos claros, limpio y
profesional) y agrega algo que una paleta genérica no da: el sistema se parece
a los planos con los que la empresa trabaja.

---

## 2. Tokens

Definidos una única vez en `frontend/src/styles/tokens.css`. Ninguna pantalla
escribe un color o una tipografía a mano.

### Color

| Rol | Valor | Uso |
| --- | --- | --- |
| Fondo | `#f2f2f3` | papel |
| Superficie | `#e9e9ea` | |
| Texto | `#1d1f20` | tinta |
| Acento | `#5980a6` | acero, acción principal |
| Acento 900 | `#1d2d3d` | barra de navegación, pantallas oscuras |

El acento tiene rampa completa de 100 a 900, generada sobre una misma escala de
luminosidad. Los cinco pasos del gráfico de torta salen de esa rampa
(800 → 300), lo que evita inventar colores para el gráfico.

### Semáforo de Gastos

Color **funcional**, separado de la marca. Verde, amarillo y rojo significan lo
mismo siempre, sin importar el resto de la paleta. Están desaturados a propósito
para convivir con el acero sin gritar.

| Estado | Valor | Umbral (gastado / presupuestado) |
| --- | --- | --- |
| En presupuesto | `#3f7d63` | menor a 0,90 |
| Al límite | `#c08a2e` | 0,90 a 0,99 |
| Excedido | `#ad4d3d` | 1,00 o más |

Los umbrales viven en `src/components/ui/semaforo.js`. **Son de presentación.**
Cuando se desarrolle el módulo Gastos, el cálculo autoritativo lo hace el
backend: una regla de negocio que vive solo en el navegador se puede saltear.

El estado **nunca viaja solo en color**: al lado del color siempre está el texto
("En presupuesto", "Al límite", "Excedido"). Quien no distingue verde de rojo
tiene que poder leer el estado igual.

### Tipografía

- **Barlow Condensed** (600) — cifras y títulos. Comprime mucha información en
  poco ancho, que es exactamente lo que pide una tabla de obra.
- **Barlow** (400/500/700) — texto corrido.

Se cargan desde Google Fonts en `index.html`, con la fuente del sistema
operativo como alternativa declarada en el token.

La clase `.cifra` aplica la condensada con `font-variant-numeric: tabular-nums`,
que da a todos los dígitos el mismo ancho: sin eso, una columna de importes
queda desalineada y no se puede comparar de un vistazo.

### Alturas táctiles

Las pantallas del capataz se usan en obra, con una mano y a veces con guantes.
Ningún objeto tocable baja de estos valores:

| Token | Valor | Uso |
| --- | --- | --- |
| `--tactil-min` | 56px | campos, botones de contador |
| `--tactil-fila` | 88px | filas de lista tocables |
| `--tactil-accion` | 96px | acciones principales |

---

## 3. Componentes base

| Componente | Archivo | Qué resuelve |
| --- | --- | --- |
| `Blueprint` | `components/ui/Blueprint.jsx` | El bloque del sistema: marco de línea fina con las cuatro marcas de registro |
| `semaforo` | `components/ui/semaforo.js` | Umbrales de desvío, colores, y formato de importes |
| `CabeceraCapataz` | `app/paginas/capataz/` | Cabecera oscura con botón de volver |
| `.blueprint`, `.corner` | `styles/base.css` | Marco y marcas |
| `.table` | `styles/base.css` | Tabla del sistema |
| `.cifra`, `.kicker`, `.trama` | `styles/base.css` | Utilidades tipográficas y de trama |

### Desvío respecto del diseño original: las marcas de esquina

En el CSS del sistema Industry las marcas están definidas como
`.blueprint > .corner`, es decir que solo se dibujan si el elemento padre tiene
la clase `.blueprint`. En varias pantallas del diseño (los botones de rol del
ingreso, el botón de entrar, las acciones del capataz) las marcas están escritas
dentro de elementos que **no** llevan esa clase, por lo que en el diseño
original no se ven.

Se implementó **la intención y no el error**: el componente `Blueprint` acepta
una propiedad `as` que permite renderizarlo como `button` o como enlace, de modo
que esos elementos reciban la clase y las marcas se dibujen donde correspondía.

### Formato de importes

Dos funciones, según el espacio disponible:

- `pesos(12980000)` → `"$ 12.980.000"` — importe completo.
- `millones(12980000)` → `"$13,0 M"` — abreviado, para las cifras protagonistas.

Ambas usan el separador de miles y la coma decimal argentinos.

---

## 4. Pantallas implementadas

Seis pantallas, dos experiencias según el rol.

| # | Pantalla | Ruta | Rol |
| --- | --- | --- | --- |
| 1 | Ingreso con selección de rol | `/` | — |
| 2 | Tablero | `/tablero` | Dueño |
| 3 | Detalle de obra | `/obras` | Dueño |
| 4 | Obra del día | `/obra` | Capataz |
| 5 | Hitos | `/obra/hitos` | Capataz |
| 6 | Materiales (pedir / recibir) | `/obra/materiales` | Capataz |

### Dos experiencias, no una adaptada

El Dueño trabaja en pantallas de información densa desde la computadora: barra
de navegación horizontal, tablas, gráficos, importes. El Capataz ve una sola
obra y dos accesos, en bloques de 96 px, sin menús ni información financiera.

No es la misma pantalla achicada: son dos diseños distintos para dos trabajos
distintos. Responde directamente al relevamiento, donde quedó claro que una
herramienta que complica el trabajo en obra simplemente no se usa.

### El cruce que justifica el sistema

El detalle de obra pone juntas tres informaciones que hoy la empresa tiene
separadas:

1. **En qué se fue la plata** — gráfico de torta por rubro.
2. **Cuánto se gastó contra lo presupuestado** — barras con la marca vertical
   del tope de cada rubro. La trama diagonal representa lo previsto; el relleno
   sólido, lo realmente gastado.
3. **Cuánto avanzó la obra de verdad** — línea de hitos completados.

Cruzar avance financiero contra avance físico es lo que permite detectar la
situación más costosa que marca el relevamiento: una obra que gastó mucho sin
avanzar.

### Estado compartido entre pantallas

`src/datos/DemoProvider.jsx` sostiene el estado que atraviesa pantallas. Existe
por una razón concreta: **si el capataz tilda un hito desde el celular, el
avance cambia también en el detalle de obra y en el tablero del dueño.** Ese
dato no puede vivir dentro de una pantalla.

También guarda la obra seleccionada, que es lo que vincula el tablero con el
detalle: se toca una barra del gráfico y el detalle pasa a ser el de esa obra.

---

## 5. Datos de muestra y plan de conexión

**Ninguna pantalla está conectada al backend todavía.** Todo lo que muestran sale
de `src/datos/demo.js`, aislado a propósito en un solo archivo.

| Dato de muestra | Se reemplaza por | Módulo |
| --- | --- | --- |
| `OBRAS` | `GET /api/obras` | Obras |
| `RUBROS` | `GET /api/rubros` | Presupuestación |
| `gasto` por rubro | `GET /api/gastos?obra=` | Gastos |
| `HITOS` | `GET /api/hitos?obra=` | Seguimiento de Obras |
| `CATALOGO` | `GET /api/materiales` | Materiales |
| `cobrar` | `GET /api/cuotas?obra=` | Cobros |
| Selección de rol | `POST /api/sesion` | Usuarios / Accesos |

Los puntos de conexión están marcados en el código con
`// TODO: … al desarrollar módulo …`.

### Sobre el ingreso

La pantalla de ingreso **no autentica nada**. El rol se elige tocando un botón y
no hay validación alguna. El login real —usuario, contraseña con hash BCrypt,
token JWT y control de permisos por rol en el backend— se implementa en los
módulos 13 y 14, que van al final del desarrollo. Hasta entonces, cualquiera que
abra la aplicación entra a cualquier pantalla.

---

## 6. Desvíos y pendientes respecto del informe

### Nueve módulos sin entrada en la navegación

El diseño resuelve la navegación de los cinco módulos que el dueño usa a diario
(Tablero, Obras, Pedidos, Presupuestos, Cobranzas), pero el sistema tiene
catorce. **Estos no tienen hoy desde dónde entrar:**

Clientes · Materiales · Proveedores · Personal · Portfolio Web · Usuarios ·
Accesos

Gastos y Seguimiento sí están, pero embebidos dentro del detalle de obra en
lugar de como módulos propios.

**Hay que resolverlo antes de desarrollar el módulo Clientes**, que es el
primero del orden de desarrollo y hoy no tendría dónde ubicarse. La salida más
probable es un menú secundario de administración y catálogos.

### Datos de muestra en otra región

Las direcciones de muestra son de Rosario, Funes y Roldán, y el usuario figura
como "M. Granica". El relevamiento sitúa a Granica SRL en CABA y Gran Buenos
Aires, y el interlocutor es Ricardo Sciarretta. Se mantuvieron los datos tal
como vinieron del diseño; se corrigen solos al cargar los datos reales.

### La barra lateral fue reemplazada

El paso 2 había dejado un marco con barra lateral de navegación. El diseño usa
navegación horizontal superior, que devuelve todo el ancho al contenido —
razonable en pantallas con tablas y gráficos. Se reemplazó.

---

## 7. Verificación realizada

| Comprobación | Resultado |
| --- | --- |
| `npm run lint` (oxlint) | sin advertencias |
| `npm run build` | 51 módulos, sin errores |
| Renderizado de las 6 pantallas | verificado con capturas en Chrome headless |
| Cálculos del semáforo | coinciden con los del diseño en las 5 obras |

### Errores detectados y corregidos durante la verificación

1. **`color-mix()` en atributo SVG.** Los círculos de referencia del gráfico de
   torta usaban `stroke="color-mix(…)"` como atributo. El soporte de `color-mix`
   como atributo de presentación es menos confiable que como propiedad CSS; se
   movió a una clase de la hoja de estilos.
2. **Reloj en formato de 12 horas.** `toLocaleTimeString('es-AR')` devuelve
   `"07:40 p. m."`. Además de no ser como se dice la hora acá, es lo bastante
   largo como para comprometer el ancho en un celular. Se forzó `hour12: false`.
3. **Texto de estado partido en dos líneas.** El ancho fijo de 96 px que traía
   el diseño no alcanzaba para "EN PRESUPUESTO" en versalitas. Se llevó a 116 px
   con `white-space: nowrap`.
4. **Recarga en caliente rota.** El archivo del contexto exportaba a la vez un
   componente y un hook, lo que impide el *fast refresh* de Vite. Se separó en
   `contextoDemo.js` (contexto y hook) y `DemoProvider.jsx` (componente).

---

## 8. Estado

| Paso | Estado |
| --- | --- |
| 0 — Repositorio y documentación base | Completado |
| 1 — Configuración base del backend | Completado |
| 2 — Configuración base del frontend | Completado |
| 3 — Sistema de diseño y pantallas del diseño importado | Completado |
| Previo a módulos — Ubicar los 9 módulos sin entrada en la navegación | **Pendiente** |
| 4 — Módulo Clientes (primero de punta a punta) | Pendiente |
