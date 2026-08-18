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
│   └── desarrollo/             Documentación técnica por módulo
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

| # | Módulo | Estado |
| --- | --- | --- |
| 0 | Base del proyecto y sistema de diseño | En curso |
| 1 | Clientes | Pendiente |
| 2 | Obras | Pendiente |
| 3 | Presupuestación | Pendiente |
| 4 | Materiales | Pendiente |
| 5 | Proveedores | Pendiente |
| 6 | Compras | Pendiente |
| 7 | Gastos | Pendiente |
| 8 | Personal | Pendiente |
| 9 | Seguimiento de Obras | Pendiente |
| 10 | Cobros | Pendiente |
| 11 | Portfolio Web | Pendiente |
| 12 | Dashboard | Pendiente |
| 13 | Usuarios | Pendiente |
| 14 | Accesos | Pendiente |
