# Almacenamiento de archivos

**Fecha de desarrollo:** 14/09/2026
**Atraviesa:** Compras (remitos), Gastos (comprobantes), Portfolio (fotos)
**Referencia en el informe:** "Supabase Storage para imágenes. La BD guarda solo la URL/referencia, nunca el binario."

---

## 1. Qué resuelve

Tres módulos adjuntan archivos, y hasta ahora los tres pedían que se escribiera
**a mano** una referencia de texto (`remitos/2026-09-03-corralon.jpg`). O sea:
el campo existía, la validación existía, pero no había archivo.

Eso rompía la promesa central del circuito de Compras: *"el capataz confirma la
recepción desde el celular con foto del remito"*. Sin la foto, el remito en
papel se sigue perdiendo, que es exactamente el problema del relevamiento.

---

## 2. La decisión de diseño

> **Una interfaz, dos implementaciones.**

La Propuesta Técnica define Supabase Storage. Atar el código a Supabase tenía
dos problemas concretos:

1. No se puede desarrollar ni probar sin una cuenta y conexión a internet.
2. El día que se cambie de proveedor hay que tocar los tres módulos que suben
   archivos.

`AlmacenDeArchivos` es la interfaz. Hay dos implementaciones:

| Implementación | Cuándo | Dónde guarda |
| --- | --- | --- |
| `AlmacenLocal` | Desarrollo (valor por defecto) | Una carpeta de la máquina |
| `AlmacenSupabase` | Producción | El bucket, por su API REST |

Cuál se usa lo decide **una propiedad de configuración, no el código**:

```properties
sigco.almacenamiento.tipo=${ALMACENAMIENTO_TIPO:local}
```

Cambiar de una a otra es cambiar el `.env`, sin recompilar nada. El módulo que
sube una foto no sabe dónde termina.

> `AlmacenLocal` **no sirve para producción**, y conviene tener claro por qué:
> Railway reinicia los contenedores y el disco se pierde con cada reinicio. Las
> fotos desaparecerían.

---

## 3. Público y privado: la separación es de negocio

No todos los archivos son iguales:

| Ámbito | Qué | Quién lo ve |
| --- | --- | --- |
| `publico/` | Fotos del portfolio | **Cualquiera.** La vidriera es pública por diseño |
| `privado/` | Remitos y comprobantes | Solo quien entró al sistema |

La separación está en la **ruta** y no en un parámetro, a propósito: así la
configuración de seguridad abre una y cierra la otra con una línea, en lugar de
tener que mirar el contenido de cada petición.

```java
.requestMatchers(HttpMethod.GET, "/api/archivos/publico/**").permitAll()
```

Es el mismo criterio con el que Portfolio separó `/api/vidriera` de
`/api/portfolio` desde el primer día. En Supabase se traduce directo: dos
buckets, `sigco-publico` (marcado público) y `sigco-privado`.

### Los archivos se sirven por el backend, no por la URL del almacenamiento

Podrían servirse directo desde Supabase con su URL pública. No se hace: así el
permiso lo decide el sistema y no la configuración del bucket, y el día que se
cambie de proveedor las referencias ya guardadas en la base siguen sirviendo.

---

## 4. Qué se acepta, y por qué no alcanza con la extensión

| Regla | Valor |
| --- | --- |
| Tipos | JPG, PNG, WEBP y PDF |
| Tamaño | 8 MB (una foto de celular ronda los 3 o 4) |
| Nombre | Se descarta: se genera uno aleatorio |

**Cambiarle el nombre a un archivo es gratis.** `virus.exe` pasa a `remito.jpg`
y la extensión deja de decir nada. Por eso se verifica el tipo declarado **y los
primeros bytes del archivo**, que son los que de verdad identifican el formato:

```
JPEG → FF D8 FF        PNG → 89 'P' 'N' 'G'
WEBP → 'R' 'I' 'F' 'F' PDF → '%' 'P' 'D' 'F'
```

Hay un test que sube un ejecutable renombrado a `.jpg` y verifica que se
rechace.

### El nombre del archivo no se usa

Se genera uno aleatorio. Dos motivos: dos personas pueden subir `remito.jpg` el
mismo día, y el nombre que elige el usuario puede contener `..` para escribir
fuera de la carpeta.

Esa segunda parte se corta además comparando la ruta ya resuelta contra la raíz
del almacenamiento — es el ataque de recorrido de rutas, y también tiene test.

---

## 5. Subir y guardar son dos pasos

`POST /api/archivos` sube el archivo y devuelve la referencia. **No guarda nada
en ninguna tabla.** Quien sube la foto del remito la manda después en la
recepción del pedido, que es donde Compras aplica sus reglas.

Separarlos evita un problema concreto: si la subida guardara la referencia en el
pedido, una foto subida por error quedaría pegada al pedido aunque la recepción
nunca se confirme.

---

## 6. Endpoints

| Método | Ruta | Quién |
| --- | --- | --- |
| POST | `/api/archivos` | Autenticado. Recibe `archivo` y `carpeta` |
| GET | `/api/archivos/publico/**` | **Abierto** |
| GET | `/api/archivos/privado/**` | Autenticado |
| GET | `/api/archivos/carpetas` | Autenticado. Las carpetas válidas |

Las carpetas son una **lista cerrada** (`remitos`, `comprobantes`,
`portfolio`), cada una con su ámbito. Si la carpeta llegara suelta desde la
petición, alguien podría inventar una, y una carpeta `publico/remitos` dejaría
los remitos a la vista de cualquiera.

---

## 7. En el frontend

Un solo componente, `SubirImagen`, usado en los cuatro lugares: la recepción de
pedidos del escritorio, la del celular, el comprobante de un gasto y las fotos
del portfolio. Está escrito una vez porque los cuatro necesitan lo mismo, y un
cambio —el tamaño máximo, el mensaje de error— tiene que valer para los cuatro.

```jsx
<SubirImagen carpeta="remitos" valor={fotoRemito} onSubida={setFotoRemito} />
```

### El detalle que importa en obra

```jsx
capture="environment"
```

En el celular, eso hace que el botón abra **directamente la cámara trasera** en
lugar del explorador de archivos. Es lo que convierte "adjuntar un archivo" en
"sacarle una foto al remito", que es lo que el capataz realmente hace parado al
lado del camión.

### Las filas viejas siguen funcionando

Hay publicaciones y pedidos cargados **antes** de que existiera la subida, con
un texto escrito a mano. Esas no se pueden mostrar como imagen —el archivo no
existe— y se muestran como texto. Lo resuelve `esArchivoSubido()`, que mira si
la referencia empieza con `publico/` o `privado/`.

---

## 8. Tests

**11 tests**, sobre 248 del sistema.

Se prueba la implementación **local** y no la de Supabase, a propósito: probar
la de Supabase exigiría una cuenta y conexión, y lo que se estaría probando
sería Supabase, no SIGCO. Lo que sí se verifica son las reglas que comparten las
dos, que son las que protegen al sistema.

| Caso | Resultado |
| --- | --- |
| Guarda y devuelve la referencia con su ámbito | ✅ |
| Dos archivos con el mismo nombre no se pisan | ✅ |
| Leer algo inexistente da 404, no 500 | ✅ |
| Borrar dos veces no falla | ✅ |
| Archivo vacío, tipo no admitido, más de 8 MB | Rechazados |
| **Ejecutable renombrado a .jpg** | Rechazado por la firma |
| **Referencia con `..`** | Rechazada |

### Verificación sobre el sistema real

| Caso | Resultado |
| --- | --- |
| `POST /api/archivos` sin token | `401` |
| Subir un remito | `privado/remitos/3efc30d4….jpg` |
| Leerlo sin token | `401` |
| Leerlo con token | `200`, 163 bytes, `image/jpeg` |
| Subir una foto de portfolio y leerla **sin token** | `200` — la vidriera es pública |
| Ejecutable renombrado | `"El archivo no es del tipo que dice ser."` |
| Carpeta inventada | `"Carpeta no válida. Las admitidas son: [portfolio, remitos, comprobantes]"` |

---

## 9. Para poner Supabase en producción

1. Crear dos buckets: `sigco-publico` (marcado **público**) y `sigco-privado`.
2. Configurar en Railway:

```
ALMACENAMIENTO_TIPO=supabase
SUPABASE_URL=https://xxxxx.supabase.co
SUPABASE_SERVICE_KEY=...
```

La clave es la **`service_role`** del proyecto, no la anónima: la anónima no
puede escribir en un bucket privado. Va como variable de entorno y **nunca en el
repositorio**.

No hay que tocar una línea de código ni migrar las referencias ya guardadas: la
referencia (`privado/remitos/a1b2.jpg`) es la misma en las dos implementaciones.

---

## 10. Pendientes

| Pendiente | Nota |
| --- | --- |
| ~~Achicar la imagen antes de subirla~~ | **Resuelto el 17/09/2026**: `comprimirImagen.js`, ver `18-cierre-y-endurecimiento.md` §9 |
| Limpiar archivos huérfanos | **Parcialmente resuelto el 17/09/2026**: `DELETE /api/archivos` borra los que nadie usa, y el frontend lo llama al quitar y al reemplazar. Queda afuera cerrar la pestaña de golpe |
| ~~Rotación según los datos EXIF~~ | **Resuelto el 17/09/2026**: lo resuelve la compresión, ver `18-cierre-y-endurecimiento.md` §9 |
| Varias fotos por remito | Hoy es una sola. El informe no pide más |
