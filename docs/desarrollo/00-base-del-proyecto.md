# 00 — Base del proyecto

> Documento de arranque del desarrollo (Etapa 3). Registra cómo quedó armado el
> repositorio, qué herramientas se usan y, sobre todo, **por qué se eligió cada
> una**. No documenta ningún módulo funcional: es el piso sobre el que se apoyan
> los 14 módulos.

**Fecha:** 18/08/2026

---

## 1. Entorno de desarrollo verificado

Herramientas instaladas en la máquina de desarrollo al iniciar la Etapa 3:

| Herramienta | Versión | Uso |
| --- | --- | --- |
| Eclipse Temurin JDK | 17.0.20 | Lenguaje del backend |
| Node.js | 22.17.1 | Entorno de ejecución del frontend |
| npm | 10.9.2 | Gestor de paquetes del frontend |
| PostgreSQL | 17.11 | Base de datos (local, para desarrollo) |
| pgAdmin | 4 | Inspección visual de la base durante el desarrollo |
| Git | 2.49.0 | Control de versiones |

**Maven no está instalado y no hace falta.** El proyecto usa el Maven Wrapper
(`mvnw.cmd`), un script versionado dentro del repositorio que descarga la versión
exacta de Maven que el proyecto necesita. Esto asegura que el backend compile
igual en esta máquina, en la de un tercero y en el servidor de despliegue
(Railway), sin depender de qué versión de Maven tenga instalada cada uno.

**JDK 17 y no una versión posterior:** es la versión declarada en la Propuesta
Técnica, es una versión con soporte extendido (LTS), y es el mínimo que exige
Spring Boot 3.x. En la máquina convive con un JDK 11 preexistente; la variable
de entorno `JAVA_HOME` apunta al 17.

---

## 2. Estructura del repositorio

Se optó por un **repositorio único (monorepo)** con dos carpetas de aplicación:

```
.
├── backend/            Spring Boot (API REST)
├── frontend/           React + Vite (SPA)
├── docs/
│   ├── informe-sigco.md
│   └── desarrollo/
├── CLAUDE.md
├── README.md
└── .gitignore
```

### Por qué un repositorio único y no dos separados

La Propuesta Técnica define frontend y backend como aplicaciones desacopladas,
que se comunican solo por API REST. Esa separación es **de ejecución**: cada una
corre por su cuenta y puede modificarse sin tocar la otra. Pero desacoplado en
ejecución no obliga a estar separado en el control de versiones.

Un repositorio único aporta tres ventajas concretas a este proyecto:

1. **Un solo historial de commits**, que refleja el avance real del desarrollo
   y sirve como evidencia del trabajo para la cátedra.
2. **Coherencia por módulo.** El desarrollo es vertical: cada módulo se hace de
   punta a punta (entidad, servicio, controlador y pantalla). Con un solo
   repositorio, ese trabajo queda en un commit coherente en lugar de partido
   entre dos historiales que hay que correlacionar a mano.
3. **No complica el despliegue.** Tanto Vercel como Railway permiten indicar el
   subdirectorio raíz del proyecto: Vercel apunta a `frontend/`, Railway a
   `backend/`.

### Organización interna del backend: por módulo funcional

Los paquetes Java se organizan **por módulo de negocio**, no por capa técnica:

```
com.sigco
├── common/                  Código transversal a todos los módulos
│   ├── config/              CORS, configuración general
│   └── exception/           Manejo centralizado de errores
└── clientes/                Un paquete por módulo del sistema
    ├── Cliente.java             (entidad JPA)
    ├── ClienteRepository.java
    ├── ClienteService.java
    ├── ClienteController.java
    └── dto/
```

La alternativa habitual es agrupar por capa (`entity/`, `service/`,
`controller/`). Con 28 tablas, eso produce tres carpetas de decenas de archivos
cada una, y trabajar sobre un módulo obliga a saltar entre las tres. Agrupando
por módulo, todo lo de Clientes está junto: se abre una carpeta y se ve el
módulo completo. Además acompaña la forma de trabajo definida para el proyecto
—un módulo entero por vez— y hace evidente dónde va cada archivo nuevo.

El frontend replica la misma idea: `src/modules/clientes/` es el espejo de
`com.sigco.clientes`, y lo transversal (componentes base, estilos, cliente HTTP)
vive fuera de los módulos porque lo comparten los 14.

---

## 3. Decisiones técnicas y su justificación

### 3.1 Base de datos local en desarrollo, Supabase en producción

El desarrollo se hace contra un **PostgreSQL 17 instalado localmente**; Supabase
queda para producción.

Supabase *es* PostgreSQL alojado en la nube: mismo motor, mismo SQL, mismas
migraciones. Por eso desarrollar en local no contradice la Propuesta Técnica, y
el pasaje a Supabase se resuelve cambiando una variable de entorno, sin tocar
código.

Las ventajas de trabajar en local son prácticas: se puede desarrollar sin
conexión a internet, no hay latencia de red en cada consulta, no se consume la
cuota del proyecto de Supabase con datos de prueba, y la base se puede borrar y
recrear cuantas veces haga falta sin ningún riesgo. Desarrollar directamente
contra la base de producción implicaría todo lo contrario.

Se eligió la versión **17** porque es la que ejecutan los proyectos nuevos de
Supabase: desarrollar sobre la misma versión mayor que producción evita
diferencias de comportamiento al desplegar.

### 3.2 Migraciones con Flyway y validación de esquema

La base de datos se construye con **migraciones SQL versionadas** en
`backend/src/main/resources/db/migration/`, ejecutadas por Flyway al arrancar la
aplicación. Cada cambio de esquema es un archivo numerado (`V1__cliente.sql`,
`V2__obra.sql`, …). Un archivo ya aplicado nunca se edita: si algo debe
cambiar, se agrega una migración nueva.

Hibernate se configura con:

```properties
spring.jpa.hibernate.ddl-auto=validate
```

Es decir, **Hibernate no crea ni modifica tablas**: solo verifica que las
entidades Java coincidan con el esquema real y, si no coinciden, impide que la
aplicación arranque indicando exactamente qué campo difiere.

Las razones de esta decisión:

1. **El Diccionario de Datos es un entregable del proyecto.** Las 28 tablas
   están definidas con nombres y tipos exactos. Si Hibernate generara el
   esquema, esos nombres y tipos quedarían a criterio del framework y el código
   dejaría de coincidir con la documentación entregada. Escribiendo el SQL a
   mano, la base es literalmente el diccionario.
2. **Verificación automática y continua.** `validate` transforma cada arranque
   de la aplicación en un chequeo de que el código sigue fiel al modelo
   documentado.
3. **Mismo esquema en todos los entornos.** Las mismas migraciones se ejecutan
   en la base local y en Supabase, eliminando las diferencias entre entornos.
4. **Seguridad en producción.** La alternativa (`ddl-auto=update`) nunca corrige
   ni elimina nada, solo agrega, y ante cambios ambiguos actúa de forma
   impredecible sobre datos reales.

El costo es escribir el SQL manualmente. Con el diccionario ya definido eso es
transcripción, no diseño, y obliga a declarar explícitamente las restricciones
que el modelo exige (`NOT NULL` en los campos obligatorios, `UNIQUE` en
`nombre_rubro`, índices sobre las columnas de búsqueda frecuente) que la
generación automática no infiere.

### 3.3 Dependencias incorporadas a medida que se necesitan

El backend arranca con el mínimo necesario: Spring Web, Spring Data JPA,
Validation, el driver de PostgreSQL, Flyway y las dependencias de test.

Dos dependencias previstas en la Propuesta Técnica se incorporan más adelante,
cuando entra el módulo que las usa:

- **Spring Security** — se suma con el módulo 14 (Accesos). Incorporarla desde
  el inicio bloquearía todos los endpoints por defecto, obligando a
  neutralizarla durante todo el desarrollo de los módulos de negocio.
- **OpenPDF** — se suma con el módulo 4 (Presupuestación), único módulo que
  genera documentos PDF.

Así el archivo de dependencias refleja la construcción real del sistema.

### 3.4 Sin Lombok

Se decidió **no utilizar Lombok**, la librería que genera automáticamente
getters, setters y constructores a partir de anotaciones.

Ahorra escritura, pero introduce una diferencia entre el código que se lee y el
que efectivamente se compila. Dado que un objetivo explícito del proyecto es
poder explicar y defender cada parte del código, se prefirió el código Java
explícito. No tiene ningún impacto sobre el funcionamiento ni el rendimiento del
sistema: es una decisión de legibilidad.

### 3.5 Patrón DTO en la capa de API

Los controladores no exponen directamente las entidades JPA: devuelven objetos
de transferencia (DTO), implementados como `record` de Java 17.

Tres motivos concretos de este sistema:

1. **Hay datos que no deben salir.** El campo `valor_unitario` de
   `item_presupuesto` es información interna que, según el informe, no puede
   llegar al cliente. Serializando la entidad directamente se filtraría sin que
   nadie lo note.
2. **Las relaciones bidireccionales generan ciclos.** Un cliente tiene obras y
   cada obra referencia a su cliente; serializar la entidad produce una
   recursión infinita.
3. **Desacopla la API del modelo de datos**, que es exactamente la arquitectura
   que plantea la Propuesta Técnica.

### 3.6 Frontend en JavaScript, sin librería de componentes

- **JavaScript y no TypeScript.** La Propuesta Técnica especifica React, Vite,
  React Router y Axios, sin TypeScript. El tipado estático fuerte, donde
  realmente importa en este sistema (importes monetarios, reglas de negocio),
  ya está garantizado por Java en el backend.
- **Sin librería de componentes** (Material UI, Bootstrap y similares). Cada una
  impone una identidad visual reconocible, y adaptarla para que el sistema no
  parezca una plantilla cuesta más que construir los componentes propios. Los
  componentes escritos a medida son, además, componentes que se pueden explicar.
- **CSS Modules + variables CSS (design tokens).** La paleta, la tipografía y
  los espaciados se definen una única vez en `frontend/src/styles/tokens.css`.
  Modificar un token se propaga automáticamente a los 14 módulos, que es
  justamente lo que se busca del sistema de diseño.

### 3.7 Una única instancia de Axios

Todas las llamadas al backend pasan por una sola instancia configurada en
`frontend/src/api/client.js`.

Es una decisión tomada pensando en el módulo 14: cuando exista la autenticación,
el token JWT se adjunta a cada petición en **un solo** interceptor, y la
expiración de sesión (respuesta 401 → volver al login) se maneja también en un
solo lugar. Si cada componente usara Axios por su cuenta, habría que modificar
los 14 módulos para incorporar la seguridad.

---

## 4. Credenciales y variables de entorno

Ninguna credencial se versiona. Las configuraciones referencian variables de
entorno:

```properties
spring.datasource.url=${DB_URL}
spring.datasource.username=${DB_USER}
spring.datasource.password=${DB_PASSWORD}
```

En desarrollo, los valores viven en un archivo `.env` excluido del repositorio
por `.gitignore`. Se versiona un `.env.example` con los nombres de las variables
y sin sus valores, para documentar qué hay que configurar. En producción, las
plataformas de despliegue (Railway, Vercel) las inyectan desde su propio panel.

Es coherente con la sección de seguridad de la Propuesta Técnica: una contraseña
de base de datos publicada en el repositorio la contradiría por completo.

---

## 5. Documentación del desarrollo

`docs/desarrollo/` contiene un documento por módulo, escrito al terminarlo,
siguiendo la estructura fija de `_plantilla-modulo.md`.

Mantener la misma estructura en los 14 documentos no es una formalidad: estos
documentos son el insumo del manual de usuario de la Etapa 4. Si cada uno
tuviera una estructura distinta, armar el manual implicaría rehacer el trabajo;
con una estructura común, es consolidar lo ya escrito.

---

## 6. Backend: configuración base implementada

### 6.1 Versión de Spring Boot

Se desarrolla sobre **Spring Boot 4.1.0**, la versión vigente al iniciar la
Etapa 3. Requiere Java 17 como mínimo, por lo que respeta la versión de Java
declarada en la Propuesta Técnica. Spring Initializr ya no ofrece la rama 3.x,
cuyo soporte gratuito finalizó a mediados de 2026.

Diferencias relevantes respecto de Spring Boot 3.x, útiles al consultar
documentación o ejemplos escritos para la versión anterior:

- El starter web se llama `spring-boot-starter-webmvc` (antes `spring-boot-starter-web`).
- Las dependencias de test están divididas por área (`...-webmvc-test`,
  `...-data-jpa-test`, `...-validation-test`, `...-flyway-test`) en lugar del
  único `spring-boot-starter-test`.
- Incorpora Spring Framework 7, Hibernate 7.4 y Spring Security 7, esta última
  relevante recién en el módulo 14.

### 6.2 Estructura generada

```
backend/
├── pom.xml
├── mvnw.cmd                    Maven Wrapper (descarga Maven 3.9.16)
├── .env                        credenciales locales, NO versionado
├── .env.example                plantilla versionada, sin valores
└── src/main/
    ├── java/com/sigco/
    │   ├── SigcoBackendApplication.java
    │   └── common/
    │       ├── EstadoController.java
    │       ├── config/CorsConfig.java
    │       └── exception/
    │           ├── RecursoNoEncontradoException.java
    │           ├── ReglaDeNegocioException.java
    │           ├── RespuestaError.java
    │           └── ManejadorGlobalDeErrores.java
    └── resources/
        ├── application.properties
        ├── application-dev.properties
        ├── application-prod.properties
        └── db/migration/
```

### 6.3 Carga de credenciales sin exponerlas

`application.properties` incluye:

```properties
spring.config.import=optional:file:.env[.properties]
```

Spring lee el archivo `.env` como una fuente de propiedades más, de modo que
`${DB_PASSWORD}` se resuelve sin que ningún valor real quede escrito en un
archivo versionado. El prefijo `optional:` es necesario porque en producción no
existe ese archivo: allí las variables las inyecta Railway desde su panel, y sin
ese prefijo la aplicación se negaría a arrancar por no encontrarlo.

### 6.4 Manejo centralizado de errores

`ManejadorGlobalDeErrores` es un `@RestControllerAdvice` que **extiende
`ResponseEntityExceptionHandler`**, la clase base de Spring.

La herencia no es decorativa: durante las pruebas se detectó que un
`@ExceptionHandler(Exception.class)` usado como red de contención interceptaba
también las excepciones propias de Spring MVC (URL inexistente, método HTTP no
permitido, JSON mal formado), que ya traen su código HTTP correcto, y las
degradaba todas a 500. Extendiendo la clase base, esos casos los resuelve Spring
con su código correspondiente, y el manejador propio se ocupa únicamente de:

| Situación | Código HTTP | Excepción |
| --- | --- | --- |
| Registro inexistente | 404 | `RecursoNoEncontradoException` |
| Regla de negocio violada | 409 | `ReglaDeNegocioException` |
| Datos con formato inválido | 400 | `MethodArgumentNotValidException` |
| Error no previsto | 500 | cualquier otra |

La distinción entre 400 y 409 es deliberada: un 400 significa que los datos
están mal escritos (falta un campo obligatorio, un texto excede el largo
permitido) y lo detecta Bean Validation; un 409 significa que los datos son
correctos pero la operación contradice una regla de Granica SRL, como eliminar
un cliente que tiene obras asociadas. Son problemas distintos y el frontend debe
poder reaccionar de manera distinta a cada uno.

Se redefine además `handleExceptionInternal` para que los errores que resuelve
Spring salgan con el mismo formato `RespuestaError` que los propios. De esa
forma **todos** los errores de la API tienen una única estructura y el frontend
los interpreta en un solo lugar.

### 6.5 Endpoint de diagnóstico

`GET /api/estado` no pertenece a ningún módulo funcional. Devuelve el estado de
la API y de la conexión con la base de datos:

```json
{"aplicacion":"SIGCO","api":"operativa","baseDatos":"conectada","momento":"..."}
```

Sirve para verificar que el frontend alcanza al backend sin problemas de CORS y,
tras el despliegue, para confirmar que el servicio levantó correctamente.

### 6.6 Verificación realizada

| Comprobación | Resultado |
| --- | --- |
| Compilación (`mvnw compile`) | BUILD SUCCESS |
| Arranque de la aplicación | Iniciada en 3,1 s, perfil `dev` activo |
| Conexión a la base | `jdbc:postgresql://localhost:5432/sigco_dev` (PostgreSQL 17.11) |
| Flyway | Creó `flyway_schema_history`; sin migraciones aún |
| `GET /api/estado` | 200, `baseDatos: conectada` |
| URL inexistente | 404 con formato `RespuestaError` |
| Método HTTP no permitido | 405 con formato `RespuestaError` |
| Tests (`mvnw test`) | 1 test, 0 fallos, BUILD SUCCESS |

---

## 7. Estado

| Paso | Estado |
| --- | --- |
| 0 — Repositorio, estructura y documentación base | Completado |
| 1 — Configuración base del backend | Completado |
| 2 — Configuración base del frontend | Pendiente |
| 3 — Sistema de diseño (paleta y componentes base) | Pendiente |
| 4 — Módulo Clientes | Pendiente |
