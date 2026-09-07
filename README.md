# SIGCO — Sistema Integral de Gestión de Obras

Sistema de gestión web desarrollado a medida para **Granica SRL**, empresa de
construcción civil, refacción y decoración de locales de CABA y Gran Buenos Aires.

Proyecto Integrador Profesional — Ingeniería Informática, 2026.
Autor: Santino Sciarretta.

El sistema centraliza en una sola herramienta procesos que hoy la empresa maneja
con planillas de Excel, documentos de Word y mensajes de WhatsApp: presupuestación,
control de gastos contra lo presupuestado, pedidos de materiales, seguimiento del
avance de obra y cobros.

---

## Diagramas

| Documento | Contiene |
| --- | --- |
| **[Diccionario de Datos](docs/diagramas/diccionario-de-datos.md)** | Las 28 tablas con sus 180 columnas: tipo, obligatoriedad, claves y qué significa cada campo. Incluye el diagrama entidad-relación |
| **[Modelo de datos](docs/diagramas/modelo-de-datos.md)** | Mapa por módulos y el detalle de cada área con todos los campos |
| **[Proceso de Presupuestación](docs/diagramas/proceso-presupuestacion.md)** | Casos de uso, actividad, estados y secuencia del proceso core del sistema |

El entidad-relación completo, con el nombre de cada tabla en la cabecera y las
flechas de las claves foráneas, está en
[`img/entidad-relacion-tablas.png`](docs/diagramas/img/entidad-relacion-tablas.png)
(hay una versión vectorial `.svg` al lado, para imprimir sin que se pixele).

Los diagramas en Mermaid se renderizan al abrir esos archivos; las imágenes
sueltas en PNG están en [`docs/diagramas/img/`](docs/diagramas/img/).

> **El diagrama y el diccionario se generan leyendo la base real**, con los
> scripts de [`docs/diagramas/scripts/`](docs/diagramas/scripts/). No se
> escriben a mano, así que no pueden quedar desactualizados respecto del
> esquema: si una migración agrega una columna, aparece sola al regenerarlos.

---

## Stack tecnológico

| Capa | Tecnología |
| --- | --- |
| Backend | Java 17, Spring Boot, Spring Data JPA (Hibernate), Spring Security, OpenPDF |
| Frontend | React, Vite, React Router, Axios |
| Base de datos | PostgreSQL |
| Migraciones | Flyway |
| Almacenamiento de imágenes | Supabase Storage |
| Deploy | Frontend en Vercel · Backend en Railway · Base y archivos en Supabase |

Frontend y backend son dos aplicaciones independientes que se comunican
exclusivamente por una API REST con respuestas en JSON. Toda la lógica de negocio
y las validaciones se ejecutan en el backend.

---

## Estructura del repositorio

```
.
├── backend/            Aplicación Spring Boot (API REST)
├── frontend/           Aplicación React + Vite (SPA)
├── docs/
│   ├── informe-sigco.md        Informe consolidado del proyecto
│   ├── desarrollo/             Documentación técnica por módulo
│   └── diagramas/              Modelo de datos y diagramas de proceso
└── CLAUDE.md           Contexto del proyecto para el asistente de desarrollo
```

---

## Requisitos previos

| Herramienta | Versión | Cómo verificar |
| --- | --- | --- |
| JDK | 17 | `java -version` |
| Node.js | 20 o superior | `node --version` |
| PostgreSQL | 17 | `psql --version` |
| Git | cualquiera reciente | `git --version` |

**Maven no hace falta instalarlo.** El backend incluye el Maven Wrapper
(`mvnw.cmd`), que descarga por su cuenta la versión de Maven que el proyecto
necesita. Esto garantiza que el proyecto compile igual en cualquier máquina.

---

## Puesta en marcha

### 1. Base de datos

Crear la base de desarrollo (una sola vez):

```bash
psql -U postgres -c "CREATE DATABASE sigco_dev;"
```

Las tablas **no se crean a mano**: las genera Flyway al arrancar el backend,
ejecutando en orden las migraciones de `backend/src/main/resources/db/migration/`.

### 2. Backend

```bash
cd backend
cp .env.example .env      # completar con las credenciales locales
./mvnw spring-boot:run    # en Windows: .\mvnw.cmd spring-boot:run
```

La API queda disponible en `http://localhost:8080`.

### 3. Frontend

```bash
cd frontend
npm install
npm run dev
```

La aplicación queda disponible en `http://localhost:5173`.

### Tests

```bash
cd backend
./mvnw test               # en Windows: .\mvnw.cmd test
```

---

## Documentación

- **`docs/informe-sigco.md`** — informe consolidado: relevamiento, propuesta
  técnica con los 14 módulos y diccionario de datos. Es la fuente de verdad
  sobre las reglas de negocio.
- **`docs/desarrollo/`** — un documento por módulo, escrito a medida que se
  desarrolla, con lo que se construyó, las decisiones tomadas y sus motivos.

---

## Estado del desarrollo

**11 de 14 módulos** desarrollados de punta a punta (base de datos, lógica de
negocio, API y pantalla). Las 28 tablas del modelo están creadas.

| # | Módulo | Estado |
| --- | --- | --- |
| 0 | Base del proyecto y sistema de diseño | ✅ Terminado |
| 1 | Clientes | ✅ Terminado |
| 2 | Obras | ✅ Terminado |
| 3 | Presupuestación | ✅ Terminado |
| 4 | Materiales | ✅ Terminado |
| 5 | Proveedores | ✅ Terminado |
| 6 | Compras | ✅ Terminado |
| 7 | Gastos | ✅ Terminado |
| 8 | Personal | ✅ Terminado |
| 9 | Seguimiento de Obras | ✅ Terminado |
| 10 | Cobros | ✅ Terminado |
| 11 | Portfolio Web | ✅ Terminado |
| 12 | Dashboard | Siguiente |
| 13 | Usuarios | Pendiente |
| 14 | Accesos | Pendiente |

El orden sigue las dependencias de datos, no la numeración del informe. Usuarios
y Accesos van últimos por decisión de método: se desarrolla toda la lógica de
negocio primero y la capa de seguridad se activa al final sobre todo lo
construido. **Hasta entonces el sistema no tiene autenticación.**

201 tests automáticos, sin fallos.
