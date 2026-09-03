# Módulo 05 — Presupuestación · Parte B: presupuestos, versionado y PDF

**Fecha de desarrollo:** 19/08/2026
**Depende de los módulos:** Clientes, Obras, Presupuestación Parte A (catálogo)
**Referencia en el informe:** `docs/informe-sigco.md`, sección "Módulo 4 – Presupuestación"

Segunda mitad del módulo más complejo del sistema. Con esta parte, Presupuestación
queda completa.

---

## 1. Qué resuelve

Hoy cada presupuesto se arma desde cero en Excel o Word. Las versiones se pisan
entre sí, y cuando el cliente pregunta por algo que se habló hace dos meses, no
hay forma de saber qué decía la versión que aprobó. Es el problema más costoso
que identifica el relevamiento en este proceso.

El módulo convierte cada versión en un registro propio, con su tipo, su número
y su estado, y guarda de qué versión salió cada una.

---

## 2. Modelo de datos

| Tabla | PK | FK | Descripción |
| --- | --- | --- | --- |
| `presupuesto` | `id_presupuesto` | `id_obra`, **`id_presupuesto_base`** | Una versión de presupuesto |
| `item_presupuesto` | `id_item` | `id_presupuesto`, `id_rubro`, `id_subrubro` | Un trabajo puntual |

**Migración Flyway:** `V4__presupuesto.sql`

### La autorreferencia es el corazón del módulo

```sql
id_presupuesto_base  BIGINT REFERENCES presupuesto (id_presupuesto)
```

Un presupuesto apunta a otro **de su misma tabla**: el que se usó como punto de
partida. Siguiendo esa cadena se reconstruye la historia completa de una obra
sin duplicar información.

Se agregó una restricción que el Diccionario no pide pero que la tabla necesita:

```sql
CHECK (id_presupuesto_base IS NULL OR id_presupuesto_base <> id_presupuesto)
```

Un presupuesto no puede ser su propia base. Sin eso, un error de carga crearía
un ciclo y recorrer la cadena de versiones no terminaría nunca.

### Los importes son `NUMERIC`, nunca punto flotante

El informe lo pide explícitamente y acá se ve por qué: sumar veinte subtotales
en `double` acumula error de redondeo, y esto es lo que se le cobra a un
cliente. En Java son `BigDecimal`, con redondeo `HALF_UP` —el comercial, el que
espera cualquiera que revise la cuenta a mano.

---

## 2b. El vínculo con Materiales — corrección del Diccionario de Datos

**Migración:** `V7__item_presupuesto_material.sql`

### El problema: los dos documentos no coincidían

- **La prosa del informe** describe que los ítems del presupuesto se eligen del
  catálogo de Materiales. Ése es el problema que el módulo Materiales existe
  para resolver: que "cemento", "Cemento CP40" y "bolsa cemento" dejen de ser
  tres cosas distintas escritas a mano en cada obra.
- **El Diccionario de Datos** define `item_presupuesto` sin columna
  `id_material`, así que el vínculo no podía existir.

Como el Diccionario manda para el esquema, la tabla salió sin la relación y la
descripción del ítem quedaba como texto libre — reproduciendo exactamente el
problema que había que eliminar. **El Diccionario se corrige con esta columna.**

### Por qué es opcional

No todo ítem de un presupuesto es un material. *"Mano de obra de albañilería"* o
*"Dirección de obra"* son ítems legítimos que no salen del catálogo. Hacerla
obligatoria forzaría a inventar materiales falsos para poder cargarlos.

### La descripción se sigue guardando

Elegir un material **no reemplaza** la descripción, la sugiere. La descripción
es lo que se imprime en el PDF del cliente y suele necesitar más detalle que el
nombre del catálogo: *"Cemento CP40 para la carpeta del baño"*. Lo que queda
vinculado es el material; el texto es lo que se ve.

### Reglas

| Regla | Respuesta |
| --- | --- |
| El material tiene que pertenecer al rubro del ítem | `409` |
| No se puede usar un material inactivo | `409` |
| El material no existe | `404` |
| Sin material | válido |

La primera es la misma regla que ya se aplicaba al subrubro y por el mismo
motivo: si un ítem de Albañilería pudiera referir a un material de Plomería, el
agrupamiento por rubro dejaría de significar algo y Gastos compararía contra un
presupuesto mal clasificado.

Los ítems ya cargados que referencian un material que después se desactiva **no
se tocan**: son historia. La restricción aplica solo al alta y la edición.

### Al duplicar, el material se copia

`PresupuestoService.duplicar` copia `id_material` junto con el resto del ítem.
Sin eso, el definitivo generado desde un anteproyecto perdería el vínculo y
volvería a ser texto libre.

### En pantalla

El modal del ítem tiene un selector **Material del catálogo**, filtrado por el
rubro elegido y deshabilitado hasta que haya rubro. Al elegir uno se completan
descripción (solo si estaba vacía) y unidad de medida, ambas editables. En la
tabla de ítems, los que están vinculados muestran el nombre del catálogo debajo
de la descripción.

### Verificación manual

| Caso | Resultado |
| --- | --- |
| Ítem con material del rubro correcto | `200`, `idMaterial` y `nombreMaterial` en la respuesta, descripción intacta |
| Ítem sin material ("Mano de obra") | `200`, `idMaterial` nulo |
| Material de otro rubro (Caño corrugado/Electricidad en ítem de Albañilería) | `409` con el rubro real en el mensaje |
| Material inactivo, con su rubro correcto | `409` "está inactivo en el catálogo" |
| Material inexistente | `404` |

---

## 3. Las tres instancias del proceso

```
Cotización inicial ──► Anteproyecto (solo reformas) ──► Definitivo
                                                            │
                                                            └──► Adicional
```

| Instancia | Cómo se calcula | Nivel de detalle |
| --- | --- | --- |
| Cotización inicial | m² × valor de referencia | Sin ítems |
| Anteproyecto | Suma de ítems | Por rubro |
| Definitivo | Suma de ítems | Por rubro, subrubro e ítem |
| Adicional | Suma de ítems | Cambio menor sobre un definitivo aprobado |

### Reglas de circuito

| Regla del informe | Implementación |
| --- | --- |
| No hay presupuesto sin obra | FK `NOT NULL` + `404` si la obra no existe |
| En construcción nueva no se habilita anteproyecto | `409`: el `tipo_obra` lo determina |
| No hay definitivo de reforma sin anteproyecto previo | `409` si la obra no tiene ninguno |
| El adicional se genera sobre un definitivo aprobado | `409` si no hay ninguno aprobado |
| El definitivo toma el anteproyecto como base sin sobrescribirlo | Operación **duplicar** |

---

## 4. El versionado, que es lo central

`POST /api/presupuestos/{id}/duplicar` crea un presupuesto **nuevo** con una
**copia** de los ítems del original, y los deja vinculados por
`id_presupuesto_base`.

Los ítems se copian uno por uno, no se reutilizan: son registros nuevos que
después se pueden modificar sin tocar el presupuesto del que salieron.

Verificado en la prueba manual: dupliqué el anteproyecto #2 como definitivo, el
nuevo quedó con `base=#2` y los dos ítems copiados, **y el anteproyecto siguió
intacto** con sus mismos ítems y su tipo original.

La misma operación cubre el otro caso del informe: reutilizar un presupuesto de
otra obra como plantilla, indicando la obra destino.

---

## 5. Ciclo de negociación

```
Borrador ──► Enviado ──► Aprobado / Rechazado
    └────────────────────► Rechazado
```

**Un presupuesto solo se modifica en Borrador.** Una vez enviado al cliente
queda congelado; si hay cambios, se genera una versión nueva. Ésa es
exactamente la disciplina que hoy no existe y que hace que las versiones se
pisen en Excel.

| Regla | Implementación |
| --- | --- |
| No se elimina un presupuesto, se marca Rechazado | No existe DELETE de presupuestos |
| Aprobar el definitivo pone la obra "En ejecución" | El servicio encadena el cambio |
| No se aprueba un definitivo sin cliente válido | Garantizado por el modelo: `obra.id_cliente` es `NOT NULL` |
| Solo el dueño aprueba | **Pendiente**, marcado con `// TODO` para el módulo Accesos |

### Una regla que agregué

**Una obra no puede tener dos presupuestos definitivos aprobados.**

El informe no lo dice con esas palabras, pero sí insiste en que tiene que quedar
claro cuál versión aprobó el cliente. Con dos aprobados esa pregunta no tendría
respuesta, y además Gastos no sabría contra cuál comparar el gasto real. Los
cambios posteriores se cargan como Adicional, que es justamente para lo que el
informe lo define.

### Sí hay baja de ítems, y no es una contradicción

Existe `DELETE` de ítems pero no de presupuestos. Un ítem solo se puede quitar
mientras el presupuesto está en **Borrador**, es decir antes de que el cliente
lo haya visto. La regla de "nada se elimina" habla del presupuesto como
documento de la negociación.

---

## 6. Cálculos

**El subtotal nunca se recibe del cliente HTTP**: se deriva siempre de
`cantidad × valor_unitario` dentro de la entidad. Si llegara como dato, un
pedido mal armado podría guardar un total que no se corresponde con sus partes.

**El total se guarda calculado, no se deriva al leer.** Así un presupuesto
aprobado conserva exactamente el número que se le mostró al cliente, aunque
después cambie cualquier otra cosa.

**Los subtotales por rubro sí se calculan al leer**, y no se guardan: son una
suma de datos que ya están, y guardarlos obligaría a mantenerlos sincronizados
con cada cambio de ítem.

### El plan de pago

El informe pide que anticipo más cuotas cierren al 100%. Con este modelo eso se
cumple por construcción: el anticipo es un porcentaje del total y las cuotas
reparten exactamente el saldo. Lo que sí se rechaza son los dos casos que **no**
cierran:

- Anticipo parcial **sin cuotas** → parte del total quedaría sin forma de cobrarse.
- Anticipo del 100% **con cuotas** → no queda saldo para dividir.

Un anticipo del 100% sin cuotas sí es válido: es el pago contado.

---

## 7. El PDF

`GET /api/presupuestos/{id}/pdf` devuelve el documento con el membrete de la
empresa, listo para enviar.

### La regla que gobierna todo este archivo

**El precio unitario de cada ítem NO aparece.** El informe lo dice de forma
explícita, y es como la empresa trabaja hoy: al cliente se le muestra qué
incluye cada rubro y cuánto sale ese rubro, nunca el desglose por unidad. Ese
dato es interno, sirve para armar el presupuesto y después controlar el gasto.

El generador usa los ítems para dos cosas: listar las **descripciones** de los
trabajos incluidos y **sumar** el subtotal de cada rubro.

### Cómo lo verifiqué

No alcanza con leer el código. Descomprimí los flujos del PDF generado —el texto
va comprimido con Flate— y extraje todos los importes impresos:

```
importes impresos: ['$ 294.000,00', '$ 98.000,00', '$ 980.000,00', '$ 980.000,00']
                     anticipo        cuota           subtotal        total
```

Los valores unitarios cargados eran $12.500 y $8.000, y las cantidades 40 y 60.
**Ninguno aparece en el documento.**

> Nota metodológica: mi primer chequeo dio un falso positivo. Buscaba la
> subcadena `8.000`, que aparece dentro de `$ 98.000,00` (el monto de la cuota).
> Corregido a buscar el importe completo con formato, dio limpio. Vale como
> recordatorio de que una verificación mal armada puede inventar un problema
> que no existe.

### La elección de la librería

**OpenPDF y no iText**, por licencia: iText pasó a AGPL, que obliga a publicar
el código de cualquier sistema que la use. OpenPDF es su bifurcación bajo
LGPL/MPL, sin esa restricción. El informe ya contemplaba cualquiera de las dos.

### Un problema de versiones que costó encontrar

La primera versión que puse fue OpenPDF **2.2.2**, la última estable. El
proyecto **compiló sin errores** y falló recién al arrancar:

```
UnsupportedClassVersionError: com/lowagie/text/DocumentException
has been compiled by a more recent version of the Java Runtime
(class file version 65.0), this version of the Java Runtime only
recognizes class file versions up to 61.0
```

Desde la 2.1.0, OpenPDF se compila para **Java 21** (class file 65) y este
proyecto usa **Java 17** (61), que es lo que declara la Propuesta Técnica.

Revisé el bytecode de cada versión y fijé la **2.0.5**, la última compilada para
Java 17. El `pom.xml` lleva el motivo escrito arriba de la dependencia, con un
"NO ACTUALIZAR sin subir antes la versión de Java", porque es un error que
vuelve a aparecer solo si alguien actualiza sin mirar.

**La lección general:** que un proyecto compile no garantiza que arranque. Una
dependencia puede exigir una versión de Java más nueva que la del proyecto, y
eso recién se ve al ejecutar.

### El PDF no se guarda

Se regenera a partir de los datos cada vez que se lo pide. Guardarlo obligaría a
regenerarlo ante cualquier cambio y a resolver dónde almacenarlo, sin ninguna
ventaja: pesa menos de 10 KB y se arma en milisegundos.

---

## 8. Endpoints

| Método | Ruta | Qué hace |
| --- | --- | --- |
| GET | `/api/presupuestos` | Listado con filtros `obra`, `tipo`, `estado` |
| GET | `/api/presupuestos/{id}` | Detalle con ítems y subtotales por rubro |
| POST | `/api/presupuestos` | Alta, nace en Borrador |
| POST | `/api/presupuestos/{id}/duplicar` | Versión nueva con copia de los ítems |
| POST | `/api/presupuestos/{id}/items` | Agregar ítem |
| PUT | `/api/presupuestos/{id}/items/{idItem}` | Editar ítem |
| DELETE | `/api/presupuestos/{id}/items/{idItem}` | Quitar ítem (solo en Borrador) |
| PUT | `/api/presupuestos/{id}/plan-de-pago` | Anticipo y cuotas |
| PATCH | `/api/presupuestos/{id}/estado` | Enviar / aprobar / rechazar |
| GET | `/api/presupuestos/{id}/pdf` | Documento para el cliente |
| DELETE | `/api/presupuestos/{id}` | Baja definitiva, incluidos los aprobados — ver sección 8b |

---

## 8b. Baja de presupuestos — desvío respecto del informe

**Esto se aparta de lo que dice el informe y hay que poder justificarlo.**

La sección 8 del informe establece: *"No se elimina un presupuesto, solo se
marca Rechazado"*. El sistema expone `DELETE /api/presupuestos/{id}` y **admite
también los aprobados**.

### Por qué

Se pidió expresamente para poder probar el circuito completo sin arrastrar
registros. Marcar Rechazado no alcanza: deja los presupuestos de prueba
mezclados en el listado y hace que "Rechazado" signifique dos cosas distintas
—*el cliente lo rechazó* y *esto era una prueba*—, lo que ensucia más que la
baja.

Es una decisión consciente sobre una regla del informe, no un descuido. La
regla sigue vigente como **criterio de uso** (para dar de baja algo que quedó
sin efecto, lo correcto es Rechazado, y así lo dice la pantalla de
confirmación); lo que se levantó es el impedimento técnico.

### El único bloqueo que queda no es una regla, es integridad referencial

| Caso | Respuesta |
| --- | --- |
| Otro presupuesto lo tiene como base | `409` |
| No existe | `404` |
| Cualquier otro, incluido `Aprobado` | `204` |

Si otro presupuesto tiene a éste como base, borrarlo cortaría la cadena de
versionado y el derivado quedaría sin origen. **La clave foránea de la base lo
rechazaría igual**: el control en el servicio existe para devolver un `409` con
un mensaje que dice qué hay que borrar primero, en lugar de un error de
restricción de PostgreSQL.

Para borrar una cadena hay que ir del derivado hacia el origen: primero el
definitivo, después el anteproyecto del que salió.

### El efecto sobre la obra, que es lo que había que resolver

Aprobar un presupuesto definitivo **pone la obra en "En ejecución"**. Eliminarlo
sin deshacer ese cambio dejaría una obra en ejecución sin ningún presupuesto
aprobado detrás: Gastos no tendría contra qué comparar, y el listado de obras
mostraría un estado que ningún registro justifica.

Por eso `PresupuestoService.deshacerEfectoSobreLaObra` revierte la obra a
"En presupuestación" y **borra `fecha_inicio_real`** (el informe condiciona esa
fecha a que exista un definitivo aprobado; sin la aprobación, pierde
fundamento). Para eso se agregó `Obra.volverAPresupuestacion()`, que no es una
transición del circuito normal y existe solo para este caso.

La reversión está acotada por tres condiciones:

| Condición | Por qué |
| --- | --- |
| Solo si el eliminado es **Definitivo** y está **Aprobado** | Es el único que pone la obra en ejecución |
| Solo si **no queda otro definitivo aprobado** en la obra | Si queda otro, la obra sigue justificada |
| Solo desde **"En ejecución"** | Una obra Finalizada o Cancelada llegó ahí por otro camino (el último hito, o una decisión del dueño) y no le corresponde a esta operación deshacerlo |

Los ítems se eliminan con el presupuesto: la relación es `cascade = ALL` con
`orphanRemoval`, porque un ítem no existe fuera de su presupuesto.

### En pantalla

El botón **Eliminar** aparece en todas las filas. La confirmación avisa que la
baja es definitiva y sugiere Rechazado como alternativa; cuando el presupuesto
es el definitivo aprobado, suma una advertencia explícita de que **la obra
vuelve a "En presupuestación" y pierde su fecha de inicio**. Es un efecto sobre
otro módulo que desde esa pantalla no se ve, así que conviene anticiparlo.

### Pendiente

`// TODO`: al desarrollar **Cobros**, decidir qué pasa con las cuotas ya
generadas cuando se elimina el definitivo que las originó. Con la regla actual
—que permite borrar aprobados— esto **deja de estar cubierto de forma
indirecta** y necesita una decisión explícita: bloquear la baja si hay cuotas
abonadas, o eliminarlas junto con el presupuesto.

### Verificación manual

| Caso | Resultado |
| --- | --- |
| `DELETE` de un anteproyecto que es base de un definitivo | `409` |
| `DELETE` de un id inexistente | `404` |
| `DELETE` de un borrador recién creado | `204`, y el `GET` siguiente da `404` |
| `DELETE` de un adicional con 1 ítem | `204`, ítems 5 → 4, huérfanos 0 |
| `DELETE` de un **definitivo aprobado** de la obra 4 | `204`; la obra pasó de "En ejecución" con fecha 2026-06-10 a **"En presupuestación" con fecha nula**; huérfanos 0 |

---

## 9. Pantallas

| Pantalla | Ruta |
| --- | --- |
| Listado de presupuestos | `/presupuestos` |
| Detalle y carga de ítems | `/presupuestos/:id` |

En el listado, cada fila muestra la **cadena de versiones**: `v2 ← #7` significa
"versión 2, generada a partir del presupuesto 7".

En el detalle, el desplegable de subrubro se limita a los del rubro elegido —la
misma regla que valida el backend—, y el subtotal del ítem se muestra calculado
pero **no es editable**: lo define el servidor.

El PDF se abre en una pestaña nueva con un enlace directo, no con Axios: el
documento lo arma el servidor y el navegador ya sabe mostrarlo. Queda anotado
que cuando exista la autenticación habrá que cambiarlo, porque un enlace directo
no manda el token.

---

## 10. Tests

**87 tests, 0 fallos.** De ellos, 25 son del circuito de presupuestación,
agrupados por tema con `@Nested`:

| Grupo | Casos |
| --- | --- |
| Circuito según el tipo de obra | 6 |
| Cotización inicial | 3 |
| Ítems y cálculo del total | 4 |
| Versionado | 2 |
| Ciclo de negociación | 5 |
| Plan de pago | 4 |
| Consulta inexistente | 1 |

### Verificación manual del circuito completo

| Caso | Resultado |
| --- | --- |
| Definitivo de reforma sin anteproyecto | `409` |
| Cotización inicial 85,50 m² × $420.000 | $35.910.000 |
| Subrubro de otro rubro | `409` "Desagües pertenece al rubro Plomería, no a Albañilería" |
| Duplicar anteproyecto como definitivo | Copia los ítems, deja `base=#2`, el original intacto |
| Plan 30% sin cuotas | `409` |
| Plan 30% + 7 cuotas sobre $980.000 | Anticipo $294.000, cuota $98.000 |
| Aprobar el definitivo | La obra pasó a "En ejecución" |
| Modificar un presupuesto aprobado | `409` |
| PDF | 9,7 KB, `%PDF-1.5`, sin valores unitarios |

---

## 11. Pendientes

| Pendiente | Se resuelve en |
| --- | --- |
| Restringir la aprobación al rol dueño (decisión no delegable) | Accesos |
| El PDF necesitará el token de autenticación | Accesos |
| Impedir desactivar un rubro que ya se usa en un presupuesto | Junto con Accesos o como mejora del catálogo |
| Logo real de la empresa en el membrete | Cuando Ricardo lo provea |
| Que Gastos compare contra el definitivo aprobado | Gastos |
| Que Cobros genere las cuotas desde el plan de pago | Cobros |
