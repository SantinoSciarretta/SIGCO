# Estado del proyecto — 17/09/2026

> Este archivo es el punto de partida de cada sesión nueva. Dice dónde está el
> proyecto hoy, qué falta y qué hay que saber antes de tocar nada.
>
> El contexto completo del sistema está en `CLAUDE.md` (se lee solo) y el
> detalle de cada módulo en `docs/desarrollo/`.

---

## Dónde está el proyecto

**Los 14 módulos están desarrollados de punta a punta**: base de datos, lógica
de negocio, API, pantalla, tests y documentación. La seguridad está activa sobre
todo el sistema.

- **248 tests** automáticos, sin fallos
- **28 tablas**, 13 migraciones de Flyway aplicadas
- Último commit: `1f0b492`

### Cómo entrar

```
usuario: ricardo      contraseña: granica2026     (Dueño)
usuario: martin       contraseña: general2026     (Capataz General)
usuario: jorge        contraseña: capataz2026     (Capataz de Obra)
```

La primera la crea la migración `V13`. **Hay que cambiarla**: está escrita en el
repositorio. Las otras dos se crearon para probar los roles.

### Cómo levantarlo

```bash
cd backend  && ./mvnw.cmd -o spring-boot:run      # http://localhost:8080
cd frontend && npm run dev                        # http://localhost:5173
```

---

## Lo que falta para poder decir "terminado"

### 1. Publicarlo (lo único que bloquea el uso real)

Está **todo preparado** —`backend/Dockerfile`, `frontend/vercel.json`,
`backend/.env.example`, perfil `prod` verificado— pero **nada está publicado**:
las tres plataformas necesitan las cuentas de Santino.

Los pasos están en `docs/desarrollo/17-despliegue.md`. Resumen:

1. **Supabase**: proyecto + dos buckets (`sigco-publico`, `sigco-privado`)
2. **Railway**: apuntar a `backend/`, cargar las variables de entorno
3. **Vercel**: apuntar a `frontend/`, cargar `VITE_API_URL`
4. Copiar el dominio de Vercel a `FRONTEND_URL` en Railway

**No verificado:** la imagen de Docker nunca se construyó (no hay Docker en esta
máquina) y `AlmacenSupabase` nunca se probó contra Supabase real.

### 2. Manual de usuario

Entregable de la Etapa 4. Ahora sí se puede hacer: todas las pantallas son
reales y se pueden capturar. El insumo son los `.md` de `docs/desarrollo/`.

### 3. Cargar los datos reales de Granica

Rubros, subrubros, materiales y proveedores. Es con Ricardo, no es código.

### 4. Una decisión abierta

**¿`gasto.id_subrubro` es obligatorio?** El informe se contradice: la sección de
validaciones dice que sí, la tabla de campos y el Diccionario dicen que no. Está
implementado **opcional**. Hay que resolverlo con Ricardo.

---

## Pendientes menores (ninguno bloquea)

| Qué | Dónde está anotado |
| --- | --- |
| Achicar las imágenes antes de subirlas (una foto pesa 3–4 MB y se ve a 160 px) | `16-almacenamiento.md` |
| Limpiar archivos huérfanos (subidos y nunca guardados) | `16-almacenamiento.md` |
| Renovar el token antes de que venza (hoy a las 8 h hay que volver a entrar) | `15-usuarios-accesos.md` |
| Auditar más acciones (hoy solo Usuarios, Accesos e ingreso) | `15-usuarios-accesos.md` |
| Bloqueo tras varios intentos fallidos de login | `15-usuarios-accesos.md` |
| Tablero reducido para el Capataz General (hoy ve el mismo) | `14-dashboard.md` |
| Generar el plan de cobro solo al aprobar el definitivo | `12-cobros.md` |
| Reordenar las fotos del portfolio arrastrando | `13-portfolio.md` |

---

## Lo que hay que saber antes de tocar el código

### Tres trampas del entorno que ya costaron tiempo

1. **El IDE compila dentro de `target/` y Maven salta lo ya compilado.** Si el
   backend falla con `Unresolved compilation problem`, borrar
   `backend/target/classes` y `backend/target/test-classes` y recompilar. Pasó
   dos veces.
2. **Los heredocs de Bash fallan con contenido Java o Python largo.** Usar la
   herramienta Write, que es el camino confiable en este proyecto.
3. **`\1` dentro de una cadena Python normal es un escape octal**, no una
   referencia de regex. Usar cadenas `r'...'` al generar código con `re.sub`.

### Decisiones que NO hay que revertir

- **`DELETE /api/presupuestos/{id}` admite incluso los aprobados.** Es un desvío
  del informe pedido explícitamente por Santino para poder probar. Está
  documentado en `CLAUDE.md` y en `05-presupuestacion-presupuestos.md` §8b.
- **El Capataz de Obra NO marca hitos.** El diseño original lo mostraba
  haciéndolo, pero la matriz del informe le da Seguimiento en "Consulta". Manda
  la matriz. Quien marca es el Capataz General.
- **Una excepción no sirve como control de flujo cruzando un límite
  transaccional.** Lanzar dentro de un `@Transactional` marca la transacción
  como rollback-only y atraparla afuera no lo deshace. Por eso existen
  `porcentajeConsumidoOCero()` y `resumenOVacio()` en `GastoService`.
- **Ningún módulo recalcula lo que calcula otro.** El semáforo lo da Gastos, el
  avance Seguimiento, el saldo Cobros. El Tablero, la ficha de obra y los PDF
  los consumen; no los recalculan.

### Cómo se verifica acá

Los tests unitarios usan mocks y **no simulan** Jackson, Hibernate ni Spring
Security. Varios errores de este proyecto aparecieron solo al ejercitar el
sistema de verdad. Por eso cada módulo se verifica además:

- por HTTP con `curl` o un script de Python contra el backend real
- en el navegador con Puppeteer (`puppeteer-core` está en el scratchpad)

---

## Lo hecho en las últimas sesiones (11 al 17/09)

| Commit | Qué |
| --- | --- |
| `1c5f3d2` | Dashboard, Usuarios y Accesos — los tres módulos que faltaban |
| `efb7eb2` | Pantallas del capataz contra el backend real, y el alcance por obra |
| `c171c0b` | Almacenamiento de archivos: la foto del remito ya es una foto |
| `606044c` | Ficha de obra: donde convergen todos los módulos |
| `6e5ed9c` | Despliegue preparado para Vercel, Railway y Supabase |
| `d140115` | Vidriera pública |
| `46f124b` | Ficha de cliente con su historial |
| `37607a9` | Planilla de pagos y reporte de gastos en PDF |

**Todo empujado a GitHub** el 17/09/2026 (`2c0cc8b..1f0b492`). La rama `main`
local y `origin/main` están sincronizadas.
