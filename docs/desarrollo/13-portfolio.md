# Módulo 13 — Portfolio Web

**Fecha de desarrollo:** 05/09/2026
**Depende de:** Obras (solo las finalizadas), Seguimiento (que es quien las finaliza)
**Referencia en el informe:** sección "Módulo 11 – Portfolio Web"

---

## 1. Qué resuelve

Las fotos de las obras terminadas están hoy **dispersas y sin ordenar en el
teléfono del dueño**, lo que las hace difíciles de encontrar cuando un cliente
las pide.

El módulo las organiza en galerías por obra, clasificadas por tipo de trabajo, y
las publica en una vidriera accesible desde el navegador.

> **No capta clientes nuevos.** Es respaldo visual de la trayectoria para
> clientes que llegan por recomendación y todavía están evaluando. Por decisión
> explícita del dueño, que trabaja únicamente con referidos, **la vidriera no
> tiene formulario de contacto ni ningún canal de consulta comercial**.

---

## 2. La privacidad vive en el tipo, no en una validación

Es la decisión más importante del módulo.

El informe manda: *"No se muestran en la vidriera datos del cliente ni de la
ubicación exacta de la obra, únicamente las imágenes y el tipo de trabajo."*

Hay dos DTO de salida distintos:

| DTO | Quién lo ve | Qué expone |
| --- | --- | --- |
| `PublicacionRespuesta` | El dueño, en el panel | Obra, dirección, cliente, tipo, estado, imágenes |
| `VidrieraRespuesta` | **Cualquier visitante** | Tipo de trabajo, fecha e imágenes. **Nada más** |

`VidrieraRespuesta` **no tiene** campos de cliente, dirección ni id de obra. No
existen.

> Una validación se puede olvidar; un campo que no existe, no. Que la
> restricción viva en el tipo hace imposible filtrar esos datos por accidente al
> armar una respuesta.

Hay un test que lo verifica por reflexión sobre los componentes del record, para
que si alguien agrega un campo de más el test falle.

---

## 3. Rutas separadas desde el primer día

```
/api/vidriera   → pública, solo lectura, sin datos del cliente
/api/portfolio  → administración del dueño
```

La separación no es cosmética: cuando se active Spring Security, `/api/vidriera`
queda abierta y `/api/portfolio` detrás del login. Tenerlas separadas ahora evita
desarmar rutas después.

El filtro por estado `Publicada` va **en la consulta del repositorio**, no en el
código que la usa, para que una obra despublicada no llegue a la vista pública
por un descuido al armar la respuesta.

---

## 4. Decisiones

### La publicación nace despublicada

Se cargan las fotos primero y se publica después, cuando el dueño ve que la
galería quedó bien. Publicarla al crearla mostraría **una obra sin fotos** en la
vidriera.

### No se publica una galería vacía

Una tarjeta sin imágenes es peor que no mostrar la obra. El sistema lo rechaza
con un `409`.

### Despublicar conserva las imágenes

Regla textual del informe: *"Una obra despublicada conserva sus imágenes
cargadas, para poder volver a publicarla más adelante sin recargarlas"*.

### Acá SÍ se elimina, a diferencia del resto del sistema

Quitar una foto la borra de verdad. Es la única excepción a la regla de "nada se
elimina", y tiene una razón: **una foto no es el registro de algo que pasó, es
material de difusión**. Si el dueño no la quiere mostrar, no hay nada que
conservar. La obra y su historial no se tocan.

La única restricción: no se puede dejar sin imágenes una publicación activa. Hay
que despublicarla antes.

### El tipo de trabajo es una lista abierta

El informe dice *"Construcción / Refacción / Decoración de local, **entre
otros**"*: es una lista de ejemplos, no un conjunto cerrado. Encerrarla
obligaría a una migración de base cada vez que la empresa quiera mostrar un tipo
nuevo.

En pantalla se sugieren los tres habituales con un `datalist`, pero el campo
acepta cualquier valor. Es el mismo criterio que en `material.unidad_medida`.

---

## 5. Ajuste al modelo (`V12`)

`V8` permitía `Borrador`, `Publicada` y `Oculta`. El informe enumera dos:
**Publicada / Despublicada**. Y el circuito confirma que son dos: *"la despublica
sin necesidad de borrar las imágenes"* — una obra despublicada conserva sus
fotos, así que el estado despublicado **es** el borrador. Un tercer estado no
agrega nada.

---

## 6. Endpoints

| Método | Ruta | Qué hace |
| --- | --- | --- |
| GET | `/api/vidriera?tipo=` | **Pública.** Obras publicadas, sin datos del cliente |
| GET | `/api/vidriera/tipos` | Tipos con obras publicadas, para el filtro |
| GET | `/api/portfolio` | Panel del dueño: todas, publicadas o no |
| POST | `/api/portfolio` | Crear publicación de una obra finalizada |
| PUT | `/api/portfolio/{id}` | Cambiar el tipo de trabajo |
| PATCH | `/api/portfolio/{id}/publicacion` | Publicar |
| PATCH | `/api/portfolio/{id}/despublicacion` | Sacar de la vidriera |
| POST | `/api/portfolio/{id}/imagenes` | Agregar una foto |
| DELETE | `/api/portfolio/{id}/imagenes/{idImagen}` | Quitar una foto |

**No hay endpoint de contacto**, y no lo va a haber: es una decisión de negocio,
no una funcionalidad pendiente.

---

## 7. Validaciones

| Regla del informe | Dónde se valida |
| --- | --- |
| Solo obras Finalizadas | `PortfolioService.crear()` |
| La vidriera no expone datos del cliente | El DTO público no los tiene |
| Despublicar conserva las imágenes | `despublicar()` solo cambia el estado |
| Sin canales de contacto | No existe el endpoint |

**Reglas agregadas:** una obra no puede tener dos publicaciones (índice único de
`V8`), no se publica una galería vacía, y no se vacía una publicación activa.

---

## 8. Pantallas

| Pantalla | Dónde |
| --- | --- |
| Panel de administración | `/portfolio` |
| Nueva publicación | modal |
| Carga de imágenes | formulario en línea por publicación |

Arriba de todo hay un aviso que aclara qué ve el visitante y qué no: **lo que se
ve en el panel no es lo que se ve en la vidriera**. Es información que el dueño
necesita tener presente al decidir qué publica.

Las miniaturas muestran hoy la referencia del archivo, porque la integración con
Supabase Storage está pendiente. Cuando se conecte, ese bloque pasa a ser un
`<img>` sin tocar el resto.

---

## 9. Tests

**8 tests** de este módulo, sobre 201 del sistema.

Cubren: solo obras finalizadas, que nazca despublicada, que no se publique
vacía, que despublicar conserve las fotos, el orden de la galería, que no se
vacíe una publicación activa, y —el más importante— que **`VidrieraRespuesta` no
tenga campos de cliente ni dirección**, verificado por reflexión.

### Verificación manual

| Caso | Resultado |
| --- | --- |
| Publicar sin imágenes | `409` |
| Cargar 3 imágenes | `200` |
| Publicar con imágenes | `200`, estado Publicada |
| Vidriera pública | Expone solo `idPublicacion`, `tipoTrabajo`, `fechaPublicacion`, `imagenes` |
| Despublicar | `200`, **las 3 imágenes se conservan** |
| Vidriera después | 0 obras |

---

## 10. Pendientes

| Pendiente | Se resuelve en |
| --- | --- |
| Subida real de imágenes | **Resuelto el 14/09/2026**, ver `16-almacenamiento.md` |
| Vista pública con diseño de galería | **Resuelta el 14/09/2026**: `/obras-realizadas`, ver abajo |
| Restringir la administración al rol dueño | **Resuelto**: `portfolio.ver` / `portfolio.editar`, solo el Dueño |
| Reordenar las imágenes arrastrando | Hoy se agregan al final |


---

## La vidriera pública (14/09/2026)

`/obras-realizadas` es **la única pantalla del sistema que ve alguien de
afuera**. El informe la pide así: *"Vista pública de la vidriera, accesible
desde un navegador, sin formularios de contacto ni datos de acceso."*

Vive **fuera** de `RutaProtegida` y fuera del marco de la aplicación: no tiene
login, ni menú, ni barra de navegación. Es la contraparte de `/api/vidriera`,
que es una de las tres rutas abiertas del backend.

### Lo que no tiene, y no es un olvido

| Qué falta | Por qué |
| --- | --- |
| Formulario de contacto, teléfono, correo | Decisión explícita del dueño: trabaja solo con referidos. El portfolio es respaldo visual para quien ya llegó por recomendación |
| Nombre del cliente, dirección de la obra | **El backend no los manda.** `VidrieraRespuesta` no tiene esos campos, y un test lo verifica por reflexión |
| Login | La vidriera es pública por diseño |

Se verificó en el navegador, con una ventana **sin ninguna sesión guardada**:
la página carga, las tres fotos se sirven desde el almacenamiento, y una
búsqueda en todo el texto de la página no encuentra ni el nombre del cliente, ni
la dirección, ni ningún dato de contacto.

### Detalles

- **Los tipos de trabajo del filtro salen de lo publicado**, no de una lista
  fija. El informe dice "construcción, refacción, decoración de local, **entre
  otros**": encerrarlos obligaría a tocar el código cada vez que aparece uno.
- **El filtro solo aparece si hay más de un tipo.** Un filtro con una sola
  opción es ruido.
- **Se muestra el año, no la fecha exacta.** A un visitante la fecha precisa no
  le dice nada; el año sí, porque habla de trayectoria.
- **La foto ampliada se cierra tocando en cualquier lado**: en un celular no hay
  lugar para apuntarle a una cruz chica.
- **Las fotos usan `loading="lazy"`**: una galería de obras puede tener muchas y
  no tiene sentido bajarlas todas antes de que alguien llegue a mirarlas.
