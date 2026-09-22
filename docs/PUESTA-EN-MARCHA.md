# Puesta en marcha de SIGCO

> Los pasos para publicar el sistema y dejarlo funcionando. El código está
> terminado; lo que sigue son cuentas y configuración, y tiene que hacerlo
> Santino porque exige iniciar sesión con sus datos.
>
> Calculá **una hora y media** la primera vez. Se puede hacer en varias
> sentadas: entre paso y paso no se pierde nada.
>
> La explicación técnica de cada decisión está en
> `docs/desarrollo/17-despliegue.md`. Acá están los pasos.

---

## Antes de empezar

Necesitás tres cuentas gratuitas, todas con "Continuar con GitHub":

| Plataforma | Para qué | Dónde |
| --- | --- | --- |
| **Supabase** | La base de datos y las fotos | supabase.com |
| **Railway** | El backend (Java) | railway.app |
| **Vercel** | El frontend (la pantalla) | vercel.com |

Y el repositorio ya está en GitHub, así que las tres lo van a encontrar solas.

**El orden importa.** Supabase primero porque las otras dos necesitan sus datos,
y Vercel antes del último paso de Railway porque Railway necesita el dominio que
Vercel asigna.

---

## Paso 1 — Supabase (la base y las fotos)

### 1.1 Crear el proyecto

Entrá a supabase.com, **New project**, ponele `sigco`.

Elegí una contraseña para la base y **guardala en un lugar seguro ahora mismo**:
Supabase no la vuelve a mostrar y la vas a necesitar en el paso 2.

Elegí la región más cercana (San Pablo o Virginia del Norte). Tarda un par de
minutos en crearse.

### 1.1 bis — Si el proyecto ya existe

El proyecto de SIGCO **ya está creado**: se llama `SIGCO`, su id es
`gpauxpuhbmwlswcinfmb` y está en `us-west-2` (Oregon). Saltate el paso de arriba
y seguí desde 1.2.

**Si el panel dice que está pausado**, tocá *Resume project* y esperá unos
minutos. No se pierde nada: los datos, los backups y los archivos quedan
intactos.

> **Por qué se pausó, y por qué importa más adelante.** El plan gratuito de
> Supabase pausa el proyecto tras una semana sin actividad. Mientras SIGCO sea
> un proyecto de facultad no molesta: se reanuda y listo.
>
> Pero si Ricardo empieza a usarlo de verdad, significa que después de una
> semana tranquila **el sistema deja de responder hasta que alguien entre al
> panel de Supabase a reanudarlo** — y el capataz que intenta cargar un remito
> solo ve un error que no puede resolver.
>
> La única forma de evitarlo es el plan Pro (unos 25 dólares por mes). No hace
> falta decidirlo para avanzar, pero conviene que Ricardo sepa que ese costo
> existe antes de apoyar la operación de la empresa en el sistema.

### 1.2 Copiar la cadena de conexión

Botón **`Connect`**, arriba de todo, al lado del nombre de la rama. Se abre un
panel con pestañas según el tipo de conexión: elegí la de **JDBC**.

> Supabase movió esto de lugar. Antes estaba en *Project Settings → Database*,
> y ese menú **ya no existe**: dentro de Project Settings hoy solo hay General,
> Infrastructure, Integrations, API Keys, JWT Keys, Log Drains y Add-ons. Si
> alguna guía te manda ahí, está desactualizada.

Te va a quedar algo así:

```
jdbc:postgresql://db.abcdefghijk.supabase.co:5432/postgres
```

Guardala. Es el `DB_URL` del paso 2.

**Si no anotaste la contraseña de la base** (o el proyecto es viejo y no te
acordás), no hace falta rehacer nada: barra lateral izquierda → ícono de
**Database** → *Settings* → *Reset database password*. Generá una nueva y
guardala.

### 1.3 Crear los dos buckets

En *Storage → New bucket*, creá **dos**, y prestá atención a la visibilidad
porque es lo que separa lo que ve un desconocido de lo que no:

| Nombre | Público | Qué guarda |
| --- | --- | --- |
| `sigco-publico` | **Sí, marcalo** | Las fotos del portfolio: la vidriera la ve cualquiera |
| `sigco-privado` | **No** | Remitos y comprobantes: documentación interna de la obra |

Los nombres tienen que ser exactamente esos.

### 1.4 Copiar la clave de servicio

*Project Settings → **API Keys***. Ahí hay dos claves y **tenés que copiar la
`service_role`**, no la `anon`. Si la pantalla las separa en solapas, la
`service_role` está entre las claves *legacy*.

> La `anon` no puede escribir en un bucket privado: si usás esa, todo va a
> andar hasta que un capataz intente subir la primera foto de un remito.

**No la publiques en ningún lado.** Esa clave puede leer y escribir todo.

---

## Paso 2 — Railway (el backend)

### 2.1 Crear el servicio

*New Project → Deploy from GitHub repo → SIGCO*.

Después, en *Settings → Root Directory*, escribí `backend`. Railway va a
encontrar el `Dockerfile` solo.

### 2.2 Generar la clave de los tokens

Antes de cargar las variables necesitás una clave secreta. En la terminal:

```bash
openssl rand -base64 48
```

Si no tenés `openssl`, sirve cualquier texto aleatorio **de más de 32
caracteres**.

> Esta clave es la que firma las sesiones. Si se filtra, cualquiera puede
> fabricarse un token válido y entrar como Ricardo. **Tiene que ser distinta de
> la de desarrollo** y no puede estar en el repositorio. Con menos de 32
> caracteres la aplicación directamente no arranca, que es preferible a firmar
> con una clave débil.

### 2.3 Cargar las variables

En *Variables*, una por una:

```
SPRING_PROFILES_ACTIVE=prod
DB_URL=            (el JDBC del paso 1.2)
DB_USER=postgres
DB_PASSWORD=       (la contraseña del paso 1.1)
JWT_SECRETO=       (lo que generaste recién)
ALMACENAMIENTO_TIPO=supabase
SUPABASE_URL=      (https://abcdefghijk.supabase.co — sin el "db." y sin el puerto)
SUPABASE_SERVICE_KEY=   (la clave service_role del paso 1.4)
FRONTEND_URL=https://ejemplo.vercel.app
```

`FRONTEND_URL` todavía no lo sabés: poné cualquier cosa por ahora y lo
corregimos en el paso 4. Es el último eslabón.

`PORT` no lo cargues: lo pone Railway y el backend ya lo lee.

### 2.4 Esperar y mirar los registros

El primer despliegue tarda unos minutos porque descarga las dependencias de
Maven. Cuando termine, abrí *Deployments → View logs* y buscá:

```
Successfully applied 14 migrations
Started SigcoBackendApplication
```

**Ese "14 migrations" es la señal de que la base quedó armada sola**: las 28
tablas, los tres roles, los 28 permisos y la cuenta de Ricardo. No tenés que
crear ninguna tabla a mano.

### 2.5 Copiar el dominio

En *Settings → Networking → Generate Domain*. Te va a dar algo como
`sigco-backend-production.up.railway.app`.

Probalo en el navegador:

```
https://tu-dominio.up.railway.app/api/estado
```

Tiene que contestar que la base está conectada. **Si esto no anda, no sigas**:
revisá primero los registros de Railway.

---

## Paso 3 — Vercel (la pantalla)

### 3.1 Crear el proyecto

*Add New → Project → SIGCO*. En **Root Directory** elegí `frontend`.

### 3.2 Una sola variable

En *Environment Variables*:

```
VITE_API_URL=https://tu-dominio.up.railway.app/api
```

Con `/api` al final, y sin barra después.

> Ojo con esta: Vite reemplaza la variable **al compilar**, no al ejecutar. Si
> después la cambiás en el panel, hay que volver a desplegar para que tenga
> efecto. Es la causa más común de "cambié la variable y sigue igual".

### 3.3 Desplegar y copiar el dominio

Dale a *Deploy*. Al terminar te da un dominio tipo `sigco.vercel.app`.
Copialo.

---

## Paso 4 — Cerrar el círculo

Volvé a Railway y corregí la variable que dejaste pendiente:

```
FRONTEND_URL=https://sigco.vercel.app
```

(el dominio real de Vercel, sin barra al final).

Railway vuelve a desplegar solo.

> **Por qué importa:** ese es el **único** origen desde el que el backend acepta
> llamadas. Si no coincide exactamente, el navegador bloquea todas las
> peticiones y la pantalla queda en blanco **sin ningún mensaje de error
> visible**. Si publicaste todo y la pantalla no muestra nada, empezá por acá.

---

## Paso 5 — El primer ingreso

Abrí tu dominio de Vercel y entrá:

```
usuario: ricardo
contraseña: granica2026
```

**El sistema te va a exigir cambiar la contraseña antes de dejarte hacer nada
más**, y eso es a propósito: esa contraseña está escrita en el repositorio, así
que la conoce cualquiera que mire el código. Hasta que elijas una propia,
ninguna otra pantalla se habilita.

Elegí una de verdad y anotala donde guardes las importantes: **el sistema no
puede recuperártela**, se guarda cifrada con un hash que no se puede revertir.
Si se pierde, hay que entrar a la base a mano.

Después creá las cuentas de los capataces desde *Usuarios*. A cada uno le
ponés una contraseña inicial y **el sistema le va a exigir cambiarla** la
primera vez que entre, por el mismo motivo: mientras vos la sepas, no es una
contraseña.

---

## Paso 6 — Probar que todo anda

Media hora, y te ahorra descubrir un problema con Ricardo mirando.

| Probá | Tiene que pasar |
| --- | --- |
| Entrar y cambiar la contraseña | Te deja pasar recién después de cambiarla |
| Crear un cliente y una obra | Se guardan y aparecen en la lista |
| Cargar un gasto **con foto del comprobante** | La foto se ve al abrir el gasto |
| Ver esa foto sin haber entrado (ventana privada) | **NO** se tiene que ver |
| Publicar una obra en el portfolio con fotos | Se ven en `/obras-realizadas` |
| Abrir `/obras-realizadas` sin entrar | **SÍ** se tiene que ver |
| Entrar como capataz general | El tablero **no** muestra ganancia ni saldo por cobrar |
| Errar la contraseña cinco veces seguidas | Avisa que la cuenta quedó bloqueada 15 minutos |

Las dos pruebas de fotos son las más importantes, porque son lo único que
**nunca se pudo probar contra Supabase real** desde acá: no había cuenta.
`AlmacenSupabase` está escrito y falla al arrancar si le faltan credenciales,
pero la subida de verdad recién se ejercita ahora.

Si una foto no sube, revisá que los buckets se llamen exactamente
`sigco-publico` y `sigco-privado`, y que la clave sea la `service_role`.

---

## Si algo no anda

| Síntoma | Causa más probable |
| --- | --- |
| La pantalla queda en blanco | `FRONTEND_URL` en Railway no coincide con el dominio de Vercel |
| "No se pudo conectar con el servidor" | `VITE_API_URL` mal, o sin `/api`, o falta redesplegar Vercel |
| El backend no arranca | Mirá los registros: casi siempre es `DB_URL`, `DB_PASSWORD` o un `JWT_SECRETO` de menos de 32 caracteres |
| Entra pero no sube fotos | La clave es la `anon` y no la `service_role`, o los buckets tienen otro nombre |
| Entra y todo da error 403 | Falta cambiar la contraseña: es la obligación del primer ingreso |

Los registros de Railway (*Deployments → View logs*) dicen casi siempre qué
pasó. En producción el detalle del error **no** se le manda al navegador —a
propósito, porque revelaría nombres de tablas— así que el log del servidor es
donde hay que mirar.

---

## Después, con Ricardo

Publicar el sistema no es ponerlo en uso. Lo que queda:

1. **Cargar los datos reales**: rubros, subrubros, materiales y proveedores.
   Sin el catálogo cargado, Presupuestación y Compras no tienen de dónde elegir.
   Es una sentada con Ricardo, no es código.

2. **Resolver una duda del informe**: si el subrubro de un gasto es obligatorio
   o no. El informe se contradice y hoy está implementado como **opcional**.
   Preguntale a Ricardo si al cargar un gasto siempre sabe el subrubro.

3. **El manual de usuario** (Etapa 4). Ahora sí se puede hacer, porque todas las
   pantallas existen y se pueden capturar. El insumo son los `.md` de
   `docs/desarrollo/`.
