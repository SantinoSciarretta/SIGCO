# Estado del proyecto — 24/09/2026

> Este archivo es el punto de partida de cada sesión nueva. Dice dónde está el
> proyecto hoy, qué falta y qué hay que saber antes de tocar nada.
>
> El contexto completo del sistema está en `CLAUDE.md` (se lee solo) y el
> detalle de cada módulo en `docs/desarrollo/`.

---

## Dónde está el proyecto

**Los 14 módulos están desarrollados de punta a punta**: base de datos, lógica
de negocio, API, pantalla, tests y documentación. La seguridad está activa sobre
todo el sistema y endurecida para salir a internet.

- **371 tests** automáticos, sin fallos
- **29 tablas**, 20 migraciones de Flyway aplicadas
- **Auditoría completa hecha** el 22–23/09: ver `docs/desarrollo/19-auditoria.md`
- **Los ocho cambios que pidió Ricardo al probarlo están hechos** (23–24/09):
  ver `docs/desarrollo/20-pedidos-de-ricardo.md`
- **La orden de pedido se le manda al corralón por WhatsApp** (24/09):
  ver `docs/desarrollo/21-whatsapp-al-corralon.md`
- **Los cambios a incorporar a la Propuesta Técnica están listados** en
  `docs/CAMBIOS-PARA-LA-PROPUESTA-TECNICA.md`: veinte puntos con qué dice hoy el
  documento, qué hace el sistema y por qué. Es el insumo para actualizar la
  entrega de la cátedra.
- **El código está terminado.** Lo único que falta para usarlo es publicarlo, y
  eso necesita tus cuentas: ver `docs/PUESTA-EN-MARCHA.md`

### Cómo entrar

```
usuario: ricardo      contraseña: granica2026     (Dueño)
usuario: martin       contraseña: general2026     (Capataz General)
usuario: jorge        contraseña: obraPilar7742   (Capataz de Obra)
```

**El sistema te va a obligar a cambiarla apenas entres**, y eso es a propósito:
esa contraseña está escrita en el repositorio, así que la conoce cualquiera que
vea el código. Hasta que elijas una propia, ninguna otra pantalla se habilita.

Y no la vas a poder volver a poner: desde la auditoría, la política rechaza las
contraseñas previsibles, y `granica2026` contiene el nombre de la empresa. El
mínimo es de 10 caracteres.

### Cómo levantarlo

```bash
cd backend  && ./mvnw.cmd -o spring-boot:run      # http://localhost:8080
cd frontend && npm run dev                        # http://localhost:5173
```

---

## Lo que falta para poder decir "terminado"

### 1. Publicarlo — lo único que bloquea el uso real

Está **todo preparado** —`backend/Dockerfile`, `frontend/vercel.json`,
`backend/.env.example`, perfil `prod` verificado— pero **nada está publicado**:
las tres plataformas necesitan tus cuentas.

**Los pasos, con los valores exactos a copiar, están en
`docs/PUESTA-EN-MARCHA.md`.** La referencia técnica más detallada sigue en
`docs/desarrollo/17-despliegue.md`.

**No verificado:** la imagen de Docker nunca se construyó (no hay Docker en esta
máquina) y `AlmacenSupabase` nunca se probó contra Supabase real.

### 2. Manual de usuario

Entregable de la Etapa 4. Ahora sí se puede hacer: todas las pantallas son
reales y se pueden capturar. El insumo son los `.md` de `docs/desarrollo/`.

### 3. Cargar los datos reales de Granica

Rubros, subrubros, materiales y proveedores. Es con Ricardo, no es código.

### 4. ~~Decisiones de Ricardo~~ — RESUELTAS el 23/09

Las tres quedaron cerradas, y las tres **confirman lo que el sistema ya hacía**.
No hubo que cambiar código:

| Pregunta | Respuesta |
| --- | --- |
| ¿El subrubro de un gasto es obligatorio? | **No, opcional** |
| ¿El Capataz General registra gastos? | **No, solo los consulta** |
| ¿Se quedan los pagos parciales? | **Sí**: no siempre se paga la cuota entera |

El detalle y el porqué de cada una, en `docs/PREGUNTAS-PARA-RICARDO.md`.

---

## Pendientes menores (ninguno bloquea)

| Qué | Dónde está anotado |
| --- | --- |
| Limpiar los huérfanos que quedan al cerrar la pestaña de golpe (los demás ya se borran) | `18-cierre-y-endurecimiento.md` §9 |
| Que las tarjetas del tablero lleven a la obra filtrada y no al módulo entero | `14-dashboard.md` |
| Estado de cuenta por cliente, cruzando obras y cobros | `12-cobros.md` |
| Varias fotos por remito (el informe no las pide) | `16-almacenamiento.md` |
| Consultas agregadas en el tablero si algún día hay muchas obras | `14-dashboard.md` |
| Sin límite de peticiones fuera del login | `19-auditoria.md` §9 |
| Poder cargar gastos sin poder anularlos (hoy `gastos.editar` habilita las dos) | `19-auditoria.md` §9 |

---

## Lo que hay que saber antes de tocar el código

### Tres trampas del entorno que ya costaron tiempo

1. **El IDE compila dentro de `target/` y Maven salta lo ya compilado.** Si el
   backend falla con `Unresolved compilation problem` o con un
   `ClassNotFoundException` de una clase que existe, borrar
   `backend/target/classes` y `backend/target/test-classes` y recompilar. Pasó
   cuatro veces.
2. **Los heredocs de Bash fallan con contenido Java o Python largo.** Usar la
   herramienta Write, que es el camino confiable en este proyecto.
3. **`\1` dentro de una cadena Python normal es un escape octal**, no una
   referencia de regex. Usar cadenas `r'...'` al generar código con `re.sub`.

### Una trampa del stack

**Este proyecto usa Jackson 3**, que en Spring Boot 4 vive en `tools.jackson` y
ya no en `com.fasterxml.jackson`. La versión 2 sigue apareciendo en el árbol de
dependencias porque la arrastra JJWT, pero no es la que usa la API. Importar del
paquete viejo compila en el IDE y falla al construir.

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
  `porcentajeConsumidoOCero()` y `resumenOVacio()` en `GastoService`, y por eso
  `RegistroDeIntentos` es una clase aparte con `REQUIRES_NEW`: si contara los
  intentos fallidos dentro de la transacción del login, el contador se
  desharía con la excepción y **la cuenta no se bloquearía nunca**.
- **El bloqueo por intentos se verifica ANTES de comparar la contraseña.** Si se
  verificara después, quien prueba contraseñas podría seguir probándolas durante
  el bloqueo y el sistema le confirmaría cuál acertó. El test
  `cuentaBloqueadaNoEntraNiConLaCorrecta` usa la contraseña correcta a propósito
  para fijar ese orden.
- **Ningún módulo recalcula lo que calcula otro.** El semáforo lo da Gastos, el
  avance Seguimiento, el saldo Cobros. El Tablero, la ficha de obra y los PDF
  los consumen; no los recalculan.
- **El plan de cobro NO se genera solo al aprobar el definitivo.** Falta la
  fecha del primer vencimiento, que se pacta con el cliente: generarlo sería
  inventar un compromiso de pago. El tablero lo reclama como pendiente. Ver
  `18-cierre-y-endurecimiento.md` §8.
- **El tablero reducido se arma completo y se recorta al final, en un solo
  punto.** Repartir el filtro por el armado haría que agregar un campo nuevo y
  olvidarse de una rama filtrara mal sin que nadie lo note.
- **La renovación del token CONSERVA el instante del ingreso, no lo corre.** Si
  al renovar se grabara el instante actual, el tope de 24 h no llegaría nunca y
  un token robado se renovaría para siempre. Lo fija el test
  `renovarConservaElInicio`.
- **El estado de una cuota se DERIVA de sus pagos, nadie lo marca.** Así no
  puede existir una cuota "Abonada" con saldo pendiente. Y el CAC se aplica solo
  sobre el saldo impago: encarecer lo ya pagado sería cobrarlo dos veces.
- **La política de contraseñas NO exige mayúsculas, números ni símbolos.** Esa
  regla empuja a todos a la misma contraseña previsible y termina anotada en un
  papel. Lo que se exige es largo, y se rechazan las previsibles.

### Cómo se verifica acá

Los tests unitarios usan mocks y **no simulan** Jackson, Hibernate, Flyway ni
Spring Security. Varios errores de este proyecto aparecieron solo al ejercitar el
sistema de verdad. Por eso cada módulo se verifica además:

- por HTTP con `curl` o un script de Python contra el backend real
- en el navegador con Puppeteer (`puppeteer-core` está en el scratchpad)

**Un 403 no prueba lo que uno cree.** Al verificar el aislamiento entre obras,
las peticiones del capataz daban 403 y el test pasaba — pero por el cambio
obligatorio de contraseña, no por el alcance. Hay que mirar el mensaje del error,
no solo el código.

**Trampa de Puppeteer en este proyecto:** el CSS pone los botones en mayúsculas,
y `innerText` devuelve el texto **ya transformado**. Buscar un botón por
`includes('Registrar pago')` no lo encuentra: hay que comparar sin distinguir
mayúsculas. Ya generó un falso positivo una vez.

---

## Lo hecho en las últimas sesiones (11 al 23/09)

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
| `96cd9b7` | Auditoría: tope de sesión, corte al cambiar contraseña, política de contraseñas |
| `b6fad42` | Auditoría: pagos parciales de cuotas (alcance nuevo, no pedido por el informe) |
| `63f8213` | Auditoría: código muerto fuera y tres reglas del informe completadas |
| `97ca017` | Endurece el ingreso: bloqueo por intentos, cambio obligatorio de contraseña, renovación del token, auditoría de aprobaciones |
| `ad8ca16` | Tablero reducido del capataz, compresión de fotos, borrado de huérfanos, reordenar el portfolio |
| `96cd9b7` | Auditoría: tope de sesión, corte al cambiar contraseña, política de contraseñas |
| `b6fad42` | Auditoría: pagos parciales de cuotas (alcance nuevo, no pedido por el informe) |
| `63f8213` | Auditoría: código muerto fuera y tres reglas del informe completadas |
| `c65380a` | Primeros cuatro cambios pedidos por Ricardo al probar el sistema |
| `083f8fe` | Presupuesto por planilla, con la mano de obra como rubro propio |
| `b0ce370` | Los presupuestos se entran por la obra, no por el listado plano |
| `1948d9a` | Cierre de obra con balance, y etapas que alimentan el Seguimiento |
| (esta sesión) | La orden de pedido se le manda al corralón por WhatsApp |

Los commits hasta `63f8213` están en GitHub. **Los de los pedidos de Ricardo y el
de WhatsApp todavía no se empujaron**: `git push` cuando quieras subirlos.
