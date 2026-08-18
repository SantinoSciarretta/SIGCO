# Módulo NN — Nombre del módulo

> Plantilla base para documentar cada módulo. Se completa **al terminar de
> desarrollar el módulo**, describiendo lo que efectivamente quedó construido,
> no lo que se planeaba construir.
>
> Estos documentos son el insumo con el que se arma el manual de usuario de la
> Etapa 4, por eso todos siguen la misma estructura.

**Fecha de desarrollo:** DD/MM/AAAA
**Depende de los módulos:** …
**Referencia en el informe:** `docs/informe-sigco.md`, sección "Módulo N – …"

---

## 1. Qué resuelve este módulo

Dos o tres párrafos que expliquen qué problema real de Granica SRL ataca el
módulo, tomado del relevamiento. Sirve para que, leyendo solo este documento,
se entienda por qué el módulo existe y no solo qué hace.

---

## 2. Modelo de datos

Tablas que introduce o modifica el módulo, con sus claves y relaciones.

| Tabla | PK | FK | Descripción |
| --- | --- | --- | --- |
| | | | |

**Migración Flyway:** `VN__nombre.sql`

Decisiones de modelado que no sean evidentes leyendo el SQL (por qué una columna
quedó nullable, por qué se agregó un índice, por qué una restricción de unicidad).

---

## 3. Entidades y relaciones JPA

Cómo quedó mapeado el modelo en Java: entidades, tipo de relación
(`@OneToMany`, `@ManyToOne`, `@ManyToMany`), estrategia de carga elegida
(`LAZY` / `EAGER`) y el motivo de cada elección.

---

## 4. Endpoints REST

| Método | Ruta | Qué hace | Devuelve |
| --- | --- | --- | --- |
| GET | `/api/…` | | |
| POST | `/api/…` | | |
| PUT | `/api/…` | | |

Códigos de estado que devuelve cada uno ante error, y qué DTO viaja en cada
sentido.

---

## 5. Validaciones y reglas de negocio

Cada regla del informe implementada, y **dónde vive**. La distinción importa:
la validación del backend es la que manda; la del frontend existe solo para dar
respuesta inmediata al usuario.

| Regla de negocio | Dónde se valida | Cómo |
| --- | --- | --- |
| | Backend (service) | |
| | Backend (DTO, Bean Validation) | |
| | Backend + Frontend (UX) | |

---

## 6. Pantallas

Vistas construidas, qué muestra cada una y desde dónde se llega. Indicar cuáles
están pensadas para usarse desde el celular en obra.

| Pantalla | Ruta | Descripción |
| --- | --- | --- |
| | `/…` | |

Componentes base reutilizados del sistema de diseño, y cualquier componente
nuevo que este módulo haya tenido que crear.

---

## 7. Tests

| Test | Qué verifica |
| --- | --- |
| | |

Resultado de `mvn test` al cerrar el módulo, y errores que los tests detectaron
durante el desarrollo (esto último es evidencia útil para la defensa).

---

## 8. Decisiones tomadas y por qué

Las decisiones de diseño que se tomaron durante el desarrollo, con su
justificación. Es la sección más importante del documento de cara a la defensa:
es donde se explica por qué el módulo quedó así y no de otra manera.

---

## 9. Desvíos respecto del informe

Cualquier diferencia entre lo que el informe describía y lo que finalmente se
implementó, con el motivo. Si no hubo desvíos, dejarlo escrito explícitamente.

---

## 10. Pendientes

Lo que queda por resolver en este módulo y en qué momento se retoma. En
particular, los puntos marcados con `// TODO: vincular a usuario real al
integrar módulo Accesos`, que se resuelven recién en el módulo 14.
