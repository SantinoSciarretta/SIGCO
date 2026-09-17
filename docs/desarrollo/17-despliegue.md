# Despliegue

**Fecha:** 14/09/2026
**Plataformas:** Vercel (frontend) · Railway (backend) · Supabase (base y archivos)

---

## 1. Por qué tres plataformas

Es lo que define la Propuesta Técnica, y responde a la arquitectura del sistema:
frontend y backend son **dos aplicaciones independientes** que se hablan solo
por API REST. Nada obliga a que vivan en el mismo lugar, y separarlas permite
usar la plataforma que mejor sirve para cada una.

| Qué | Dónde | Por qué |
| --- | --- | --- |
| Frontend | Vercel | Publica archivos estáticos: un `dist/` con HTML, JS y CSS |
| Backend | Railway | Corre un proceso Java permanente |
| Base y archivos | Supabase | PostgreSQL administrado + almacenamiento de imágenes |

Las tres tienen plan gratuito suficiente para una empresa de este tamaño.

---

## 2. Lo que está preparado en el repositorio

| Archivo | Para qué |
| --- | --- |
| `backend/Dockerfile` | Cómo construir y ejecutar el backend |
| `backend/.dockerignore` | Qué NO entra a la imagen |
| `backend/.env.example` | La plantilla de variables, con qué es cada una |
| `frontend/vercel.json` | Build y ruteo de la SPA |
| `frontend/.env.production` | El nombre de la variable del backend |
| `application-prod.properties` | La configuración que cambia en producción |

### El Dockerfile va en dos etapas

Para **compilar** hace falta Maven y el JDK completo, unos 400 MB de
herramientas. Para **ejecutar** alcanza con el runtime de Java. Con una sola
etapa, todo eso viajaría a producción en cada despliegue.

La imagen final ronda los 180 MB contra los ~500 MB de la de construcción.

Tres detalles que no son decorativos:

- **El `pom.xml` se copia antes que el código.** Docker reutiliza el resultado
  de cada paso mientras lo que entra no cambie: así un cambio en el código no
  vuelve a descargar las dependencias, que es lo que más tarda.
- **Corre como usuario sin privilegios.** Por defecto los contenedores corren
  como root, y si alguien lograra ejecutar código dentro, lo haría con todos los
  permisos.
- **`exec java`** y no `java` a secas, para que Java sea el proceso principal.
  Sin eso queda debajo de un shell y las señales de apagado que manda Railway no
  le llegan: el contenedor se mata de golpe en vez de cerrar bien las conexiones.

---

## 3. Los pasos

### A. Supabase — base de datos y archivos

1. Crear un proyecto.
2. **Base:** *Project Settings → Database → Connection string → JDBC*. Guardar
   esa cadena.
3. **Almacenamiento:** crear **dos** buckets en *Storage*:

   | Bucket | Visibilidad | Qué guarda |
   | --- | --- | --- |
   | `sigco-publico` | **Público** | Fotos del portfolio: la vidriera la ve cualquiera |
   | `sigco-privado` | Privado | Remitos y comprobantes: documentación interna |

4. **Clave:** *Project Settings → API → `service_role`*. **No la anónima**: la
   anónima no puede escribir en un bucket privado.

> Las tablas **no hay que crearlas a mano**. Flyway ejecuta las 14 migraciones
> al primer arranque del backend y deja el esquema completo, con los roles, los
> permisos y la cuenta inicial.

### B. Railway — backend

1. Crear un proyecto desde el repositorio de GitHub y apuntar a la carpeta
   `backend/`. Railway detecta el `Dockerfile` solo.
2. Cargar las variables de entorno:

```
SPRING_PROFILES_ACTIVE=prod
DB_URL=jdbc:postgresql://db.xxxxx.supabase.co:5432/postgres
DB_USER=postgres
DB_PASSWORD=...
JWT_SECRETO=...                    # generar: openssl rand -base64 48
FRONTEND_URL=https://sigco.vercel.app
ALMACENAMIENTO_TIPO=supabase
SUPABASE_URL=https://xxxxx.supabase.co
SUPABASE_SERVICE_KEY=...
```

3. Railway asigna `PORT` solo; el backend ya lo lee.

> **`JWT_SECRETO` tiene que ser distinto del de desarrollo y aleatorio.** Si se
> filtrara, cualquiera podría fabricar un token válido y entrar como quien
> quisiera. Mínimo 32 caracteres: HMAC-SHA256 los exige y con menos la
> aplicación no arranca.

### C. Vercel — frontend

1. Crear el proyecto desde el mismo repositorio, con la carpeta raíz en
   `frontend/`.
2. Una sola variable, en *Settings → Environment Variables*:

```
VITE_API_URL=https://sigco-backend.up.railway.app/api
```

3. Publicar.

> Vite reemplaza esa variable **al compilar**, no al ejecutar: cambiarla en el
> panel necesita un despliegue nuevo para tener efecto.

### D. Cerrar el círculo

`FRONTEND_URL` en Railway tiene que coincidir con el dominio real de Vercel. Es
el **único origen** que el backend atiende: si no coincide, el navegador bloquea
todas las llamadas y la pantalla queda vacía sin decir por qué.

Como cada plataforma asigna su dominio al crear el proyecto, el orden es:
publicar Vercel → copiar su dominio → ponerlo en Railway.

### E. El primer ingreso

```
usuario: ricardo
contraseña: granica2026
```

**El sistema obliga a cambiarla** *(desde el 17/09/2026)*: hasta que el titular
elija una contraseña propia, ninguna otra pantalla se habilita y el backend
rechaza cualquier otra petición. Está escrita en la migración `V13`, así que es
pública para cualquiera que lea el repositorio. Ver
`18-cierre-y-endurecimiento.md`, sección 3.

---

## 4. Qué cambia entre desarrollo y producción

| | Desarrollo | Producción |
| --- | --- | --- |
| Perfil | `dev` | `prod` |
| Log de SQL | Sí | **No**: llena los logs y expone datos de la empresa |
| Detalle del error al cliente | Sí | **No**: un mensaje de excepción revela nombres de tablas |
| CORS | `localhost:5173` | Solo el dominio de Vercel |
| Archivos | Carpeta local | Supabase Storage |
| Conexiones a la base | 10 | **5**: el plan gratuito de Supabase admite pocas simultáneas |
| Puerto | 8080 | El que asigne Railway en `PORT` |

### El disco de Railway no sirve para las fotos

Railway reinicia los contenedores, y el disco se pierde con cada reinicio: las
fotos desaparecerían. Por eso en producción el almacenamiento es Supabase y no
el disco.

---

## 5. Qué se verificó y qué no

Esto es importante para no afirmar de más.

### Verificado

Se generó el `.jar` y se arrancó **con el perfil `prod`**, apuntando a la base
local:

| Caso | Resultado |
| --- | --- |
| Arranca con el perfil `prod` | ✅ |
| Escucha en el puerto de `PORT` (8099 en la prueba) | ✅ |
| `GET /api/estado` responde con la base conectada | ✅ |
| CORS desde el origen de producción | `200` |
| CORS desde cualquier otro origen | `403` |
| El error no expone el detalle interno | ✅ |
| Login | ✅, 28 permisos |

**Esa prueba encontró un error real**: `application-prod.properties` fijaba
`sigco.almacenamiento.tipo=supabase` sin poder sobreescribirse, así que el
backend no arrancaba sin credenciales de Supabase ni siquiera para probar el
resto. Ahora el tipo se puede sobreescribir por variable, y `AlmacenSupabase`
falla **al arrancar** si le faltan la URL o la clave, en lugar de fallar cuando
el capataz saca la primera foto.

### NO verificado

- **La imagen de Docker no se construyó**: no hay Docker instalado en esta
  máquina. El `Dockerfile` está escrito y es estándar, pero no se ejecutó.
- **Nada se publicó todavía.** Las tres plataformas necesitan las cuentas de
  Santino: no hay proyecto en Vercel, ni en Railway, ni en Supabase.
- **`AlmacenSupabase` no se probó contra Supabase real**, por lo mismo.

---

## 6. Después de publicar, revisar

| Qué | Cómo |
| --- | --- |
| Que Flyway haya corrido | `GET /api/estado` dice "conectada" y el login funciona |
| Que las 14 migraciones estén | `SELECT version, description FROM flyway_schema_history` |
| Que la subida de fotos ande | Cargar un gasto con comprobante y verlo |
| Que la vidriera se vea sin login | Abrir `/api/vidriera` en una ventana privada |
| Cambiar la contraseña inicial | Usuarios → Contraseña |
