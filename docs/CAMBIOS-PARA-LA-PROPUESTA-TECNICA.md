# Cambios a incorporar en la Propuesta Técnica

**Estado al 24/09/2026.** Este documento lista todo lo que el sistema construido
hace distinto de lo que dice la Propuesta Técnica entregada, para poder
actualizar ese documento y que describa el sistema real.

**Cómo leerlo.** Cada punto dice qué dice hoy la Propuesta, qué hace el sistema
y por qué se hizo así. La columna *Dónde* indica qué sección del documento hay
que tocar.

Ninguno de estos cambios contradice el **relevamiento**: todos salieron de
procesos que el relevamiento ya describía, o de pruebas del sistema con el
dueño. Donde algo sí se aparta de una decisión anterior, está marcado como
**desvío** y explicado.

---

## Resumen

| # | Cambio | Tipo | Dónde |
| --- | --- | --- | --- |
| 1 | Tabla `pago`: cobro de una cuota en varias veces | Alcance nuevo | Diccionario, Módulo Cobros |
| 2 | `item_presupuesto.id_material` | Campo nuevo | Diccionario, Módulo Presupuestación |
| 3 | `obra.fecha_inicio_estimada` y `meses_estimados` | Campos nuevos | Diccionario, Módulo Obras |
| 4 | `rubro.es_mano_de_obra` | Campo nuevo | Diccionario, Módulo Presupuestación |
| 5 | `hito.id_rubro` y `duracion_dias` | Campos nuevos | Diccionario, Módulo Seguimiento |
| 6 | `pedido.token_orden` y `token_orden_vence` | Campos nuevos | Diccionario, Módulo Compras |
| 7 | Campos de seguridad en `usuario` | Campos nuevos | Diccionario, Módulo Usuarios |
| 8 | `gasto.estado` y `motivo_anulacion`, `cuota.motivo_anulacion`, `operario_obra.fecha_desasignacion` | Campos nuevos | Diccionario |
| 9 | Carga del presupuesto por planilla | Cambio de interfaz | Módulo Presupuestación |
| 10 | Presupuestos agrupados por obra | Cambio de interfaz | Módulo Presupuestación |
| 11 | Balance de cierre de obra | Funcionalidad nueva | Módulo Dashboard u Obras |
| 12 | Etapas por duración | Cambio de interfaz | Módulo Seguimiento |
| 13 | Orden de pedido en PDF | Funcionalidad nueva | Módulo Compras |
| 14 | Envío de la orden por WhatsApp | Funcionalidad nueva | Módulo Compras, Alcance |
| 15 | Pedidos solo de obras en ejecución | Regla nueva | Módulo Compras |
| 16 | Filtro por obra en el Tablero | Cambio de interfaz | Módulo Dashboard |
| 17 | Endurecimiento del ingreso | Reglas nuevas | Módulo Usuarios, Seguridad |
| 18 | Baja de presupuestos | **Desvío** | Módulo Presupuestación |
| 19 | Dos contradicciones del informe, resueltas | Aclaración | Módulos Gastos y Accesos |
| 20 | Dos reglas que estaban mal implementadas | Corrección | Módulo Obras |

**Totales que cambian en el documento:** de 28 a **29 tablas**; 20 migraciones de
base de datos; 371 tests automáticos.

---

## 1. Cobro de una cuota en varias veces (tabla `pago`)

**Dice la Propuesta:** la tabla `cuota` tiene `fecha_pago`, `medio_pago` y
`comprobante_emitido`, y estados Pendiente / Abonada / Vencida. Una cuota se
cobra de una sola vez.

**Hace el sistema:** una cuota se puede cobrar en partes. Cada pago es una fila
de la tabla **`pago`** (nueva), con su monto, fecha, medio y comprobante.

**Por qué.** Lo pidió Ricardo al revisar el módulo: *"no siempre se paga la
cuota entera en un pago"*. Con los campos en `cuota` solo se puede guardar el
último pago, así que el anterior se pierde.

**Lo que hay que explicar en el documento:**

- **El estado de la cuota se DERIVA de sus pagos, nadie lo marca.** Sin pagos es
  Pendiente; con saldo, **Parcial** (estado nuevo); sin saldo, Abonada. Una
  cuota parcial vencida sigue Vencida, porque el resto se sigue debiendo.
- **El índice CAC se aplica solo sobre el saldo impago**
  (`totalPagado + saldo × coeficiente`). Actualizar lo ya cobrado sería
  cobrarlo dos veces.

**Tabla nueva:** `pago` (`id_pago` PK, `id_cuota` FK, `monto`, `fecha_pago`,
`medio_pago`, `comprobante_emitido`, `id_usuario_registro` FK, `fecha_carga`).

---

## 2. `item_presupuesto.id_material`

**Dice la Propuesta:** el ítem del presupuesto tiene `descripcion` como texto
libre. La prosa, en cambio, describe que los ítems se eligen del catálogo de
Materiales.

**Hace el sistema:** el ítem tiene una FK **opcional** al material.

**Por qué.** Sin la columna, ese vínculo no puede existir y la descripción queda
como texto escrito a mano — que es exactamente el problema que el módulo
Materiales viene a resolver. Es nullable porque no todo ítem es un material
(mano de obra, dirección de obra). El material debe pertenecer al rubro del ítem
y no puede estar inactivo.

---

## 3. Plazo de obra: `fecha_inicio_estimada` y `meses_estimados`

**Dice la Propuesta:** `obra` tiene `fecha_fin_estimada`, que se carga a mano.

**Hace el sistema:** se cargan la fecha de inicio estimada y la cantidad de
meses, y **la fecha de fin se calcula**.

**Por qué.** Lo pidió Ricardo. Y la razón de fondo para que sea un resultado y
no un campo: con los tres editables por separado, nada impide guardar "empieza
en marzo, dura 6 meses, termina en julio", y a partir de ahí no se sabe cuál de
los tres es el dato bueno.

Al registrar el inicio real, el plazo se recalcula desde esa fecha: si la obra
arrancó dos semanas tarde, termina dos semanas más tarde.

---

## 4. `rubro.es_mano_de_obra`

**Dice la Propuesta:** el rubro tiene nombre y estado.

**Hace el sistema:** un rubro puede estar marcado como el de mano de obra, y esa
marca cambia cómo se presupuesta (ver punto 9).

**Por qué es una marca y no el nombre.** Reconocer el rubro por cómo se llama se
rompería el día que alguien lo renombre a "Mano de obra y jornales". Un índice
único parcial impide que haya dos marcados.

---

## 5. `hito.id_rubro` y `hito.duracion_dias`

**Dice la Propuesta:** el hito tiene nombre, ponderación y orden.

**Hace el sistema:** además puede tener el rubro al que pertenece y cuántos días
se estima que lleva. Los dos campos son opcionales (ver punto 12).

---

## 6. `pedido.token_orden` y `token_orden_vence`

**Dice la Propuesta:** nada — el pedido no se comparte hacia afuera.

**Hace el sistema:** el PDF de la orden se puede abrir con un link público, para
mandárselo al corralón (ver punto 14).

---

## 7. Campos de seguridad en `usuario`

**Dice la Propuesta:** `usuario` tiene nombre, hash, rol, operario, estado,
último acceso y fecha de alta.

**Hace el sistema:** además `motivo_baja`, `intentos_fallidos`,
`bloqueado_hasta`, `debe_cambiar_contrasena` y `version_sesion`. Cada uno
sostiene una regla del punto 17.

---

## 8. Otros campos agregados durante el desarrollo

| Tabla | Campo | Para qué |
| --- | --- | --- |
| `gasto` | `estado`, `motivo_anulacion` | Un gasto no se elimina: se anula con motivo, igual que el resto del sistema |
| `cuota` | `motivo_anulacion` | Lo mismo para una cuota |
| `operario_obra` | `fecha_desasignacion` | Conservar el historial al desvincular un operario de una obra |

---

## 9. El presupuesto se carga por planilla

**Dice la Propuesta:** los ítems del presupuesto se agregan de a uno.

**Hace el sistema:** al elegir un rubro aparecen **todos los materiales de ese
rubro del catálogo**, con su unidad, y se completa cantidad y precio en las
filas que vayan. Las vacías no se cargan.

**Por qué.** Lo pidió Ricardo al probar el sistema. Con veinte materiales, el
alta de a uno son veinte vueltas por el mismo formulario; y peor, hay que
**acordarse** de qué materiales lleva el rubro, porque la pantalla no los
muestra hasta que uno los busca.

**Lo que hay que explicar:** guardar **reemplaza** los ítems de ese rubro, no
aplica cambios sueltos. La planilla llega entera y el resultado tiene que ser
exactamente lo que el usuario ve; con cambios sueltos habría que llevar la
cuenta de qué fila se vació, y un descuido ahí deja ítems que no se ven pero
suman al total.

**La mano de obra es un rubro propio.** Su planilla no lista materiales sino
**los otros rubros**, para cargar de una sola vez cuánto sale la mano de obra de
cada especialidad. Los ítems que genera pertenecen al rubro Mano de obra, así
ese total queda junto.

El alta de a uno sigue existiendo para lo que no está en el catálogo.

**La mano de obra se carga de las dos formas:** jornales por valor del jornal, o
el total directo. En la práctica a veces se saben los jornales y a veces el
proveedor pasa un precio cerrado por el trabajo. El total directo se guarda como
una cantidad de 1 con unidad `global`, así que **no hace falta ningún campo
nuevo**: el presupuesto ya guarda cantidad por valor unitario.

---

## 10. Los presupuestos se entran por la obra

**Dice la Propuesta:** el listado de presupuestos muestra una fila por
presupuesto.

**Hace el sistema:** una fila por **obra**, y al abrirla, sus instancias
(cotización inicial, anteproyecto, definitivo) en bloques separados con sus
versiones.

**Por qué.** El circuito tiene tres instancias por obra, así que el listado
plano crecía al triple de las obras y reconstruir la negociación de una
significaba buscar sus filas entre las demás.

**Un concepto nuevo para el documento: el presupuesto *vigente*.** Una obra
puede tener cinco presupuestos y solo uno gobierna: el que se cobra, el que
Gastos usa de referencia y el que la puso en ejecución. Es el definitivo
aprobado si existe; si no, el último del circuito que siga en pie — un
presupuesto rechazado no representa a la obra. **Esa elección la hace el
backend**, para que el Tablero y esta vista no puedan mostrar totales distintos
de la misma obra.

---

## 11. Balance de cierre de obra

**Dice la Propuesta:** nada. El resultado económico de la obra se mira en
Gastos.

**Hace el sistema:** una pantalla de cierre con tres números y lo que queda
abierto.

**Por qué tres números y no uno** — esto es lo que conviene explicar en el
documento, porque es la decisión de fondo:

| Número | Cuenta | Qué dice |
| --- | --- | --- |
| Ganancia estimada | presupuestado − gastado | Lo que deja **si** el cliente termina de pagar. Es una proyección. |
| Resultado de caja | cobrado − gastado | La plata que de verdad entró menos la que salió. |
| Falta cobrar | plan − cobrado | Lo que separa a los dos anteriores. |

Una obra puede tener buena ganancia estimada y caja negativa sin que haya nada
mal: se compró material que todavía no se cobró. Un solo número escondería eso.

Además, **dar la obra por terminada con hitos pendientes exige confirmación**:
al pasar a Finalizada los hitos quedan bloqueados y no se puede cargar más
avance.

---

## 12. Las etapas se cargan por duración

**Dice la Propuesta:** los hitos se cargan con su ponderación, y la suma tiene
que dar 100.

**Hace el sistema:** además se pueden cargar por duración — qué hay que hacer,
de qué rubro es y cuántos días lleva — y **el porcentaje se deriva**.

**Por qué.** Lo pidió Ricardo. Escribir las ponderaciones a mano obliga a hacer
una cuenta que nadie quiere hacer, y es de donde salen los planes que suman 97 o
103. Cargando la duración, el número sale solo y además es más honesto: una
etapa de tres semanas pesa más que una de dos días sin que nadie lo estime.

**Hay que dejar claro que NO hay una tabla `etapa`.** Son los mismos hitos. Una
tabla aparte daría dos fuentes de avance para la misma obra y en algún momento
se contradirían: la sección de etapas diría 60% y Seguimiento 45%.

**El redondeo.** Tres etapas de un día dan 33,33 cada una y suman 99,99. Como la
regla exige exactamente 100, el centésimo que falta se le suma a la **etapa más
larga**, que es donde menos se nota.

Las dos formas de carga conviven y son endpoints separados: o se escriben las
ponderaciones o se derivan.

---

## 13. Orden de pedido en PDF

**Dice la Propuesta:** el módulo Compras genera el pedido; el PDF con membrete
está previsto solo para Presupuestación.

**Hace el sistema:** el pedido se baja en PDF con el membrete de la empresa.

**Qué lleva y qué no.** Materiales, cantidades con unidad, precios acordados y
dónde entregar. **NO lleva** presupuesto de la obra, gasto, ganancia ni el
nombre del cliente: es un documento que sale de la empresa hacia afuera, el
mismo criterio que el PDF del presupuesto, donde los valores unitarios no
aparecen.

---

## 14. La orden se le manda al corralón por WhatsApp

**Dice la Propuesta (Límites del alcance):** *"Integraciones externas
automáticas: API de WhatsApp…"* quedan fuera de esta versión.

**Hace el sistema:** un botón que abre WhatsApp con el chat del corralón y el
pedido ya escrito.

**Esto NO contradice el límite de alcance, y conviene decirlo explícitamente en
el documento.** No es la API de WhatsApp: es un link `wa.me`, la misma
tecnología que un `mailto:`. **SIGCO no manda el mensaje** — lo manda una
persona, desde su propio teléfono, apretando enviar. La API oficial de Meta
sigue fuera del alcance.

**Por qué no la API oficial,** que es la pregunta que va a aparecer en la
defensa:

- Exige un número **dedicado**, que deja de funcionar como WhatsApp normal. El
  dueño tendría que abandonar el número que los corralones tienen agendado.
- Exige verificación de empresa ante Meta, plantillas de mensaje aprobadas por
  Meta, y pago por conversación.
- Las librerías no oficiales hacen lo mismo pero violan los términos de
  servicio, con riesgo de bloqueo del número por el que pasa toda la operación
  de la empresa.

**Lo que sí hay que documentar como limitación:** un link de WhatsApp solo lleva
texto, **no puede adjuntar archivos**. Por eso el mensaje lleva el pedido
escrito completo —cada material con cantidad, precio y subtotal, más el total— y
el PDF va como enlace público, que el corralón abre sin cuenta.

**Ese enlace** usa un token aleatorio y no el id del pedido (con el id, quien
reciba una orden vería todas las demás cambiando el número), vence a los 30 días
y se puede cortar al instante.

**El permiso es `compras.aprobar`, no `compras.editar`.** El informe dice que
enviarle el pedido al proveedor es del dueño y no se delega; con el permiso más
débil, un capataz podría mandar una orden por su cuenta.

**El teléfono se normaliza al formato internacional, y si no se entiende no se
adivina.** El 15 del celular argentino se reemplaza por un 9 después del 54, y
eso exige saber dónde termina el código de área. Cuando no se puede interpretar,
el sistema avisa en lugar de inventar: adivinar mal abriría una conversación con
un desconocido para mandarle el pedido de una obra.

---

## 15. Los pedidos, solo de obras en ejecución

**Dice la Propuesta:** el circuito de compras necesita una obra.

**Hace el sistema:** la obra tiene que estar **en ejecución**.

**Por qué.** Mientras se presupuesta no se compra nada: el presupuesto puede no
aprobarse, y ese pedido generaría un gasto contra una obra que quizás nunca
arranca — el tipo de gasto que después no se sabe a qué imputar.

---

## 16. Filtro por obra en el Tablero

El Tablero consolidaba todas las obras juntas. Ahora tiene un selector que acota
los desvíos, las cuotas y los pedidos a una sola, para la conversación concreta:
*"estoy por llamar al cliente de Cabildo, ¿cómo viene esa obra?"*.

---

## 17. Endurecimiento del ingreso

**Dice la Propuesta (Seguridad):** login con JWT, contraseñas con BCrypt,
control de acceso por rol en el backend, validación de entradas, HTTPS,
auditoría.

**Hace el sistema:** todo eso, más seis reglas que salieron de la auditoría
previa a la entrega. Conviene incorporarlas porque el sistema va a manejar datos
reales en una dirección pública:

1. **Cinco intentos fallidos bloquean la cuenta quince minutos.** El bloqueo se
   verifica **antes** de comparar la contraseña: si se verificara después, quien
   prueba contraseñas seguiría probándolas durante el bloqueo y el sistema le
   confirmaría cuál acertó. El contador vive en la base y no en memoria, porque
   si no reiniciar el servidor sería la forma de saltearlo.
2. **Toda cuenta nace obligada a cambiar su contraseña,** y también cuando el
   dueño se la resetea a otro: en los dos casos hay una contraseña que conocen
   dos personas.
3. **El token se renueva mientras se usa, con un tope absoluto de 24 h** desde
   el ingreso. Quien trabaja no se cae a media tarde; un token robado no se
   renueva para siempre.
4. **Cambiar o resetear una contraseña corta las sesiones abiertas** de esa
   cuenta. Es lo que permite cerrar una sesión a distancia sin dar de baja la
   cuenta.
5. **La contraseña exige 10 caracteres y no puede ser previsible.** No se exigen
   mayúsculas, números ni símbolos **a propósito**: esa regla empuja a todos a
   la misma contraseña previsible y termina anotada en un papel.
6. **Los permisos no viajan dentro del token:** se leen de la base en cada
   petición, así un cambio en Accesos se aplica en la petición siguiente.

También conviene dejar asentado que **`compras.aprobar` es un permiso propio y
solo lo tiene el Dueño**: es lo que hace cumplir la regla de que la aprobación
del pedido es indelegable, en lugar de confiar en que nadie se lo asigne.

---

## 18. Baja de presupuestos — **desvío explícito**

**Dice la Propuesta:** *"No se elimina un presupuesto, solo se marca
Rechazado."*

**Hace el sistema:** existe `DELETE /api/presupuestos/{id}`, que admite incluso
los aprobados.

**Por qué, y cómo presentarlo.** Se implementó a pedido del desarrollador para
poder probar sin arrastrar registros. La regla del informe **sigue siendo el
criterio de uso recomendado** —así lo dice la pantalla de confirmación— pero el
impedimento técnico se levantó a propósito.

Es el único punto de esta lista donde el sistema hace algo que el informe
prohíbe. Hay dos formas honestas de resolverlo en el documento: declararlo como
desvío justificado, o dejarlo como una función de administración. Lo que no
corresponde es que el documento siga diciendo que no se puede.

---

## 19. Dos contradicciones del informe, ya resueltas

El informe se contradecía a sí mismo en dos puntos. Ricardo los resolvió el
23/09 y la Propuesta debería quedar consistente:

1. **¿El subrubro del gasto es obligatorio?** Validaciones decía que sí; la
   tabla de campos y el Diccionario decían opcional. **Queda opcional.**
2. **¿El Capataz General registra gastos?** El módulo Gastos decía que sí; la
   matriz de permisos le da Consulta. **Manda la matriz: no registra gastos.**

---

## 20. Dos reglas que estaban mal implementadas

No cambian el documento —el documento estaba bien— pero conviene saber que se
corrigieron, porque son reglas que la Propuesta enuncia:

1. **`tipo_obra` se bloquea "apenas existe un presupuesto de anteproyecto o
   definitivo".** Estaba bloqueado siempre, así que una obra con el tipo
   equivocado había que cancelarla y rehacerla. Ahora se comprueba contra los
   presupuestos, como dice el informe.
2. **`fecha_inicio_real` no se carga hasta que el definitivo esté aprobado.** Se
   comprobaba contra el *estado de la obra*, que no es equivalente: una obra
   pasada a ejecución a mano admitía la fecha sin definitivo aprobado. Ahora se
   comprueba contra el presupuesto.

---

## Lo que NO cambió

Vale la pena decirlo, porque es la mayor parte del documento:

- Los **14 módulos** son los mismos, con los mismos circuitos.
- Los **tres roles** y la **matriz de permisos** son exactamente los del
  informe. La migración que crea los permisos coincide punto por punto.
- El **stack** es el declarado: Java 17, Spring Boot, JPA, PostgreSQL, React,
  Vite, OpenPDF, Supabase Storage, y el despliegue en Vercel / Railway /
  Supabase.
- Las **reglas de negocio** de cada módulo se implementaron como están
  enunciadas, con las excepciones de los puntos 18, 19 y 20.
- Los **límites del alcance** siguen vigentes: no hay app móvil nativa, no hay
  importación automática del CAC, no hay liquidación de sueldos ni contabilidad,
  y la API de WhatsApp sigue afuera (ver punto 14).

---

## De dónde sacar el detalle

Cada cambio está documentado con su justificación completa en `docs/desarrollo/`:

| Tema | Archivo |
| --- | --- |
| Planilla, agrupado por obra, balance, etapas, plazo, PDF de pedido | `20-pedidos-de-ricardo.md` |
| WhatsApp al corralón | `21-whatsapp-al-corralon.md` |
| Pagos parciales, endurecimiento del ingreso, contradicciones | `18-cierre-y-endurecimiento.md`, `19-auditoria.md` |
| Cada módulo en detalle | `02-clientes.md` … `17-despliegue.md` |
