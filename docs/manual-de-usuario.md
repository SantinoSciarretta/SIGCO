# **Manual de Usuario** {#manual-de-usuario}

> **Nota para quien arma el documento.** Cada bloque marcado como **[ESPACIO PARA CAPTURA N]** es un lugar reservado para una imagen. Debajo de cada uno hay una indicación en cursiva que dice de qué pantalla sacarla, con qué usuario y qué datos conviene que se vean. Esa indicación se borra al pegar la imagen y se deja solo el epígrafe ("Figura N..."). Al final del manual está el **Índice de capturas**, con la lista completa para sacarlas todas de una vez. Donde dice **[DIRECCIÓN DEL SISTEMA]** va la dirección web con la que se entra al sistema publicado.

## **Presentación** {#presentación}

Este manual explica cómo usar SIGCO, el Sistema Integral de Gestión de Obras desarrollado para Granica SRL. Está pensado para las personas que lo van a usar todos los días: el dueño, el capataz general y los capataces de obra. No hace falta ningún conocimiento técnico para seguirlo. Cada sección describe una pantalla del sistema, para qué sirve, qué se ve en ella y cómo se hace cada tarea paso a paso.

El manual sigue el mismo orden de módulos que se usó a lo largo del informe, empezando por las cosas que se hacen una sola vez (entrar al sistema, cargar los catálogos) y siguiendo con el trabajo de todos los días (obras, presupuestos, pedidos, gastos, avance y cobros). Al final hay un apartado con los mensajes que puede mostrar el sistema y cómo resolverlos, preguntas frecuentes y un glosario de los términos que se usan.

### Cómo leer este manual {#cómo-leer-este-manual}

* Los nombres de botones, menús y campos se escriben entre comillas y tal como aparecen en la pantalla, por ejemplo "Nueva obra".
* Los pasos de cada tarea están numerados y conviene seguirlos en ese orden.
* Los recuadros "Tené en cuenta" explican reglas del sistema que conviene conocer para no llevarse sorpresas, por ejemplo por qué un botón no aparece.
* No todos los usuarios ven todas las pantallas. Cada sección aclara qué rol puede usarla. Si una pantalla no aparece en tu menú, es porque tu rol no tiene permiso para verla.

## **Requisitos y acceso** {#requisitos-y-acceso}

SIGCO funciona desde el navegador, sin instalar ningún programa.

* **En la computadora:** cualquier navegador actualizado (Chrome, Firefox, Edge o Safari) y conexión a internet.
* **En el celular:** el navegador del teléfono (Chrome en Android, Safari en iPhone) con datos móviles o WiFi. Las pantallas del capataz de obra están pensadas especialmente para el celular.
* **Dirección de ingreso:** [DIRECCIÓN DEL SISTEMA]. Conviene guardarla como favorito en la computadora y, en el celular, agregarla a la pantalla de inicio desde el menú del navegador ("Agregar a pantalla de inicio"), así se abre como si fuera una aplicación.

Cada persona entra con su propio usuario y contraseña. Las cuentas las crea el dueño desde el módulo Usuarios. No se comparten cuentas: el sistema registra quién hizo cada cosa, y si dos personas usan la misma cuenta ese registro deja de servir.

## **Conceptos básicos** {#conceptos-básicos}

Antes de recorrer las pantallas conviene tener claros algunos conceptos que se repiten en todo el sistema.

### Los tres roles {#los-tres-roles}

Cada cuenta tiene asignado un rol, que define qué pantallas ve y qué puede hacer en cada una.

* **Dueño:** acceso a todo el sistema. Es el único que crea y aprueba presupuestos, registra cobros, aprueba pedidos de materiales, carga gastos y administra las cuentas de acceso.
* **Capataz General:** ve el estado de todas las obras en ejecución, el avance, los pedidos, los materiales, los proveedores, el personal y los gastos (estos últimos solo para consultar). Puede marcar hitos como completados y generar pedidos de materiales. No ve presupuestos, cobros, clientes ni la información financiera reservada al dueño.
* **Capataz de Obra:** el rol más acotado. Trabaja desde el celular y solo con la obra o las obras en las que está asignado. Pide materiales, confirma la recepción de los pedidos con la foto del remito y consulta el avance de su obra.

### El recorrido de una obra {#el-recorrido-de-una-obra}

Todo en SIGCO gira alrededor de la obra. El recorrido normal es el siguiente:

1. Se crea la obra, asociada a un cliente. Queda **En presupuestación**.
2. Se arma la cotización inicial y, si es una reforma, el anteproyecto.
3. Se arma el presupuesto definitivo. Cuando el cliente lo aprueba, la obra pasa sola a **En ejecución**.
4. Durante la ejecución se piden materiales, se cargan los gastos, se marca el avance por hitos y se cobran las cuotas.
5. Cuando se completa el último hito, la obra pasa sola a **Finalizada**. A partir de ahí puede publicarse en el portfolio.
6. En cualquier momento, si el proyecto no avanza, la obra se puede pasar a **Cancelada** indicando el motivo.

Muchas pantallas dependen de este estado. Por ejemplo, solo se pueden cargar gastos o pedir materiales para una obra **En ejecución**, y solo se pueden publicar en el portfolio obras **Finalizadas**.

### Nada se borra {#nada-se-borra}

Salvo muy pocas excepciones, en SIGCO los registros no se eliminan. Un cliente, un material o un proveedor que ya no se usa se **desactiva**. Un gasto o un pago cargado por error se **anula** dejando el motivo. Un pedido que no se concreta se **anula**. Así siempre queda el historial completo de lo que pasó en cada obra, que es justamente lo que hoy se pierde entre planillas y mensajes.

## **Primeros pasos** {#primeros-pasos}

### Ingresar al sistema {#ingresar-al-sistema}

1. Abrí el navegador y entrá a [DIRECCIÓN DEL SISTEMA].
2. Escribí tu "Usuario" y tu "Contraseña".
3. Tocá el botón de ingreso.
4. Si sos el dueño o el capataz general, el sistema te lleva al **Tablero**. Si sos capataz de obra, te lleva a la pantalla de tu obra, pensada para el celular.

**[ESPACIO PARA CAPTURA 1]**
*Figura 1. Pantalla de ingreso al sistema.*
*Cómo sacarla: abrir [DIRECCIÓN DEL SISTEMA] en una ventana de incógnito de la computadora, sin haber ingresado. Que se vean los campos "Usuario" y "Contraseña" vacíos.*

> **Tené en cuenta.** Si escribís mal el usuario o la contraseña, el sistema responde "Usuario o contraseña incorrectos." sin aclarar cuál de los dos está mal. Es a propósito: así nadie puede averiguar qué usuarios existen probando nombres. Después de cinco intentos fallidos seguidos, la cuenta queda bloqueada quince minutos y el sistema avisa "Demasiados intentos fallidos. Probá de nuevo en X minutos.".

### Primer ingreso: elegir una contraseña propia {#primer-ingreso}

La primera vez que entrás con una cuenta nueva, o después de que el dueño te restableció la contraseña, el sistema te lleva directo a la pantalla **Mi cuenta** con el aviso "Antes de empezar, elegí una contraseña propia". Hasta que la cambies no se habilita ninguna otra pantalla. Esto es así porque esa contraseña te la dio otra persona y la conocen dos.

1. En "Contraseña actual" escribí la contraseña que te dieron.
2. En los dos campos de contraseña nueva escribí la que elijas, igual en los dos.
3. Tocá "Cambiar contraseña".

La contraseña nueva tiene que cumplir estas condiciones:

* Tener al menos diez caracteres.
* No ser previsible: el sistema rechaza las que mencionan a la empresa, las secuencias de teclado (como "1234567890" o "qwertyuiop"), las que repiten siempre el mismo carácter y las que contienen tu nombre de usuario.

No hace falta usar mayúsculas, números ni símbolos. Una frase corta que te resulte fácil de recordar, como "el galpon de la esquina", es más segura y más fácil de recordar que una palabra con símbolos.

**[ESPACIO PARA CAPTURA 2]**
*Figura 2. Cambio obligatorio de contraseña en el primer ingreso.*
*Cómo sacarla: crear una cuenta de prueba desde Usuarios, cerrar sesión e ingresar con esa cuenta. Capturar la pantalla Mi cuenta con el aviso "Antes de empezar, elegí una contraseña propia".*

### Cómo está organizada la pantalla {#cómo-está-organizada-la-pantalla}

En la computadora, todas las pantallas comparten una barra azul en la parte de arriba:

* **A la izquierda**, el logo de Granica.
* **En el centro**, el menú con los módulos que tu rol puede usar: Tablero, Obras, Clientes, Materiales, Proveedores, Pedidos, Gastos, Avance, Personal, Presupuestos, Cobranzas y Portfolio. El módulo que estás viendo aparece resaltado.
* **A la derecha**, tu rol, tu nombre de usuario y un círculo con tus iniciales. Al tocarlo se abre un menú con "Usuarios" y "Accesos" (solo para el dueño), "Mi contraseña" y "Cerrar sesión".

**[ESPACIO PARA CAPTURA 3]**
*Figura 3. Barra de navegación con el menú de usuario abierto.*
*Cómo sacarla: ingresar como Dueño, tocar el círculo con las iniciales arriba a la derecha y capturar la barra completa con el menú desplegado.*

### Cambiar la contraseña {#cambiar-la-contraseña}

1. Tocá el círculo con tus iniciales y elegí "Mi contraseña".
2. Escribí tu contraseña actual y dos veces la nueva.
3. Tocá "Cambiar contraseña".

Al cambiarla, el sistema cierra cualquier otra sesión que tuvieras abierta en otro dispositivo, pero no la que estás usando.

### Cerrar sesión {#cerrar-sesión}

Tocá el círculo con tus iniciales y elegí "Cerrar sesión". Conviene hacerlo siempre que uses una computadora compartida.

Si dejás el sistema abierto, la sesión dura ocho horas, una jornada de trabajo. Mientras lo estés usando, el sistema la renueva solo para que no te saque a mitad de la tarde, pero a las veinticuatro horas de haber entrado siempre hay que volver a ingresar. Si la sesión vence, el sistema te lleva a la pantalla de ingreso con el aviso "Tu sesión no es válida o venció. Ingresá de nuevo.".

### Si olvidaste la contraseña {#si-olvidaste-la-contraseña}

Nadie puede ver tu contraseña, ni siquiera el dueño, porque se guarda cifrada. Si la olvidaste, pedile al dueño que te la restablezca desde Usuarios. Te va a dar una contraseña provisoria y al entrar vas a tener que elegir una nueva.

## **Puesta en marcha: qué cargar primero** {#puesta-en-marcha}

Algunos datos se usan en todo el sistema y conviene cargarlos antes de empezar a trabajar con obras. Este es el orden recomendado:

1. **Rubros y subrubros** (desde Presupuestos, "Ver catálogo de rubros"). Son la clasificación con la que se arman los presupuestos y se ordenan los gastos. El rubro "Mano de obra" ya viene creado en el sistema, así que no hace falta agregarlo.
2. **Materiales.** Cada material pertenece a un rubro, por eso van después.
3. **Proveedores**, con su zona y su teléfono. El teléfono es el que se usa para mandarles los pedidos por WhatsApp.
4. **Clientes** actuales.
5. **Operarios**, en Personal.
6. **Cuentas de acceso** para el capataz general y los capataces de obra, en Usuarios. Si un capataz también es operario, vinculá su cuenta con su registro de Personal.

Con esto cargado ya se pueden crear obras y empezar a presupuestar.

## **Módulo 1: Tablero** {#módulo-1-tablero}

### Para qué sirve {#tablero-para-qué-sirve}

El Tablero es la pantalla de entrada del sistema. Reúne en un solo lugar lo que antes había que ir a buscar obra por obra: cuánto se gastó contra lo presupuestado, cuánto hay por cobrar, cómo viene el avance y qué está esperando una decisión del dueño. No se carga nada desde acá. Cada cosa que se muestra lleva a la pantalla donde se resuelve.

**Quién lo usa:** el dueño ve el tablero completo. El capataz general ve una versión reducida, con el avance de las obras y los pedidos, sin los montos de dinero.

### Qué se ve en la pantalla {#tablero-qué-se-ve}

* **Los números de arriba:** obras en ejecución, obras excedidas (que gastaron más de lo previsto en algún rubro), obras desfasadas (que gastan más rápido de lo que avanzan), lo que hay por cobrar esta semana, la ganancia estimada y los pedidos pendientes.
* **Esperando una decisión tuya:** la lista de cosas que solo el dueño puede destrabar, ordenadas por urgencia. Por ejemplo, un pedido esperando aprobación, un presupuesto enviado sin respuesta del cliente, una obra en ejecución que todavía no tiene plan de cobro o una cuota vencida. Al tocar "Ver" en cualquiera de ellas, el sistema abre la pantalla correspondiente con esa obra ya elegida.
* **Presupuestado vs. gastado por obra:** un gráfico de barras que compara, para cada obra, lo presupuestado contra lo gastado a hoy, en millones de pesos. El color de cada barra indica si la obra está en presupuesto, cerca del límite o excedida.
* **Obras en ejecución:** una tabla con cada obra, su avance físico contra su avance financiero, la ganancia estimada y lo que falta cobrar. Las obras que necesitan atención aparecen primero, y las que pasaron su fecha estimada de fin muestran el aviso "atrasada".

Arriba a la derecha hay un selector "Todas las obras" que permite ver el tablero de una sola obra.

**[ESPACIO PARA CAPTURA 4]**
*Figura 4. Tablero del dueño.*
*Cómo sacarla: ingresar como Dueño con al menos tres obras en ejecución que tengan presupuesto aprobado, gastos y cuotas cargadas, y al menos un pedido pendiente de aprobación. Capturar la pantalla completa (si no entra, hacer dos capturas: la parte de arriba con los números y la lista de pendientes, y la parte de abajo con el gráfico y la tabla).*

**[ESPACIO PARA CAPTURA 5]**
*Figura 5. Tablero reducido del capataz general.*
*Cómo sacarla: ingresar con una cuenta de Capataz General y capturar el tablero. Tiene que verse que los montos de dinero no aparecen.*

### Tené en cuenta {#tablero-tené-en-cuenta}

* Si no hay obras en ejecución, el tablero lo avisa: una obra pasa a ejecución cuando se aprueba su presupuesto definitivo.
* Los números se calculan en el momento en que se abre la pantalla. Si alguien carga un gasto mientras lo estás mirando, recargá la página para verlo.

## **Módulo 2: Obras** {#módulo-2-obras}

### Para qué sirve {#obras-para-qué-sirve}

Es el registro de todas las obras de la empresa, desde que llega la consulta del cliente hasta que el proyecto termina o se cancela. Toda la información de los demás módulos (presupuestos, gastos, pedidos, avance, cobros y personal) cuelga de una obra.

**Quién lo usa:** el dueño carga y modifica obras. El capataz general las consulta. El capataz de obra ve solo las suyas.

### El listado de obras {#obras-listado}

Al entrar a "Obras" aparece la tabla con todas las obras: dirección, cliente, tipo, fechas y estado. Las obras en ejecución aparecen primero. Arriba hay un buscador por dirección o cliente y dos filtros, "Todo estado" y "Todo tipo", que se pueden combinar.

**[ESPACIO PARA CAPTURA 6]**
*Figura 6. Listado de obras.*
*Cómo sacarla: ingresar como Dueño, entrar a Obras con al menos cuatro obras en estados distintos (en presupuestación, en ejecución, finalizada y cancelada).*

### Crear una obra {#obras-crear}

1. Tocá "Nueva obra".
2. En "Cliente" elegí el cliente de la lista. Si es la primera vez que trabaja con la empresa, tocá "Es nuevo", escribí su nombre y, si lo tenés, su teléfono, y tocá "Guardar cliente". Queda cargado en Clientes y elegido para la obra sin salir del formulario.
3. Completá la "Dirección de la obra", el "Tipo de inmueble" (Casa, Departamento o Local) y el "Tipo de obra" (Construcción o Reforma).
4. Si ya lo sabés, cargá el "Comienzo estimado" y la "Duración estimada" en meses. Con esos dos datos el sistema calcula solo la finalización tentativa y la muestra debajo.
5. Si hace falta, escribí en "Notas" cualquier particularidad del proyecto, por ejemplo restricciones de horario o de acceso al edificio.
6. Tocá "Crear obra".

La obra queda en estado **En presupuestación**.

**[ESPACIO PARA CAPTURA 7]**
*Figura 7. Formulario de alta de obra.*
*Cómo sacarla: tocar "Nueva obra", completar el cliente, la dirección, el tipo de inmueble, el tipo de obra, el comienzo estimado y la duración, de modo que se vea la "Finalización tentativa" calculada.*

**[ESPACIO PARA CAPTURA 8]**
*Figura 8. Alta rápida de un cliente desde el formulario de la obra.*
*Cómo sacarla: en "Nueva obra", tocar "Es nuevo" al lado de Cliente y capturar con el nombre del cliente nuevo escrito.*

> **Tené en cuenta.** El tipo de obra define el circuito de presupuestación: una construcción nueva no tiene anteproyecto, y una reforma puede tenerlo antes del definitivo. Por eso el tipo de obra solo se puede corregir mientras la obra no tiene presupuestos de anteproyecto o definitivo.

### Editar una obra {#obras-editar}

En el listado, tocá "Editar" en la fila de la obra. Se pueden corregir la dirección, el tipo de inmueble, las fechas, la duración y las notas. Una vez aprobado el presupuesto definitivo se habilita también la fecha de "Inicio real". Si cargás el inicio real, el sistema recalcula la fecha estimada de fin a partir de esa fecha.

### Cambiar el estado de una obra {#obras-cambiar-estado}

La mayoría de los cambios de estado los hace el sistema solo: la obra pasa a "En ejecución" al aprobarse el presupuesto definitivo y a "Finalizada" al completarse el último hito. Para los casos en que hay que hacerlo a mano:

1. En el listado, tocá "Cambiar estado" en la fila de la obra.
2. Elegí el "Nuevo estado".
3. Según el estado elegido, el sistema pide algún dato más:
   * **En ejecución:** la "Fecha de inicio de los trabajos".
   * **Cancelada:** el "Motivo de la cancelación", que es obligatorio. Si la obra ya tiene el presupuesto definitivo aprobado, además hay que tildar la confirmación de que se quiere interrumpir una obra en marcha.
   * **Finalizada:** si quedan hitos sin completar, hay que tildar la confirmación de que la obra terminó igual. Al cerrarla, sus hitos se bloquean y ya no se puede cargar avance.
4. Tocá "Confirmar cambio".

**[ESPACIO PARA CAPTURA 9]**
*Figura 9. Cambio de estado a Cancelada.*
*Cómo sacarla: en una obra en ejecución, tocar "Cambiar estado", elegir "Cancelada" y escribir un motivo, de modo que se vea también la casilla de confirmación de obra en marcha. No confirmar.*

> **Tené en cuenta.** Una obra no se puede eliminar, solo cancelar, para no perder los presupuestos, gastos o pagos que ya tenga. Finalizar una obra no cancela lo que falte cobrar: el plan de cobro sigue vigente.

### La ficha de la obra {#obras-ficha}

Al tocar la dirección de una obra en el listado se abre su ficha, que reúne todo lo de ese proyecto:

* **Los números principales:** presupuestado, gastado, avance físico y lo que falta cobrar.
* **Desglose del gasto:** cuánto se gastó por tipo, incluido el total en gastos hormiga.
* **Gastado contra presupuestado, por rubro:** una barra por rubro con una línea que marca el tope. El color indica si está bien, al límite o excedido.
* **Hitos de obra:** el avance por etapas.
* **Documentos:** la "Planilla de pagos", para entregarle al cliente, y el "Reporte de gastos", de uso interno porque incluye la ganancia estimada. Los dos se abren en una pestaña nueva en PDF.
* **Todo lo de esta obra:** accesos directos a Presupuestos, Gastos, Pedidos, Avance, Cobranzas, Personal y Balance. Cada uno abre ese módulo con esta obra ya elegida.

**[ESPACIO PARA CAPTURA 10]**
*Figura 10. Ficha de una obra en ejecución.*
*Cómo sacarla: ingresar como Dueño y abrir una obra en ejecución que tenga presupuesto aprobado, gastos en varios rubros (alguno en amarillo o rojo), hitos y cuotas. Capturar la ficha completa, en dos partes si no entra.*

### El balance de la obra {#obras-balance}

Desde la ficha, el acceso "Balance" abre el cierre económico de la obra. Muestra tres números que conviene no confundir:

* **Ganancia estimada:** lo presupuestado menos lo gastado. Es una proyección: dice cuánto debería dejar la obra si se cobra todo.
* **Resultado de caja:** lo cobrado menos lo gastado. Es la plata real que entró y salió hasta hoy.
* **Falta cobrar:** el saldo de cuotas que todavía se deben.

Debajo se explica de dónde sale cada número y se muestra el detalle rubro por rubro, que es lo que conviene mirar al presupuestar la próxima obra parecida. En "Queda abierto" se listan las cosas pendientes, como cuotas sin cobrar o hitos sin completar.

**[ESPACIO PARA CAPTURA 11]**
*Figura 11. Balance de una obra.*
*Cómo sacarla: desde la ficha de una obra con gastos y algún pago registrado, tocar "Balance" y capturar las tres tarjetas y la tabla por rubro.*

### Botones y acciones disponibles {#obras-botones}

| Botón / Acción | Qué hace |
| :---- | :---- |
| Nueva obra | Abre el formulario para dar de alta una obra. |
| Es nuevo | Da de alta un cliente sin salir del formulario de la obra. |
| Editar | Permite corregir los datos de una obra. |
| Cambiar estado | Pasa la obra a En ejecución, Finalizada o Cancelada, pidiendo los datos de cada caso. |
| Planilla de pagos | Abre en PDF la planilla de cuotas para entregarle al cliente. |
| Reporte de gastos | Abre en PDF el estado financiero de la obra, de uso interno. |
| Balance | Abre el cierre económico de la obra. |

## **Módulo 3: Clientes** {#módulo-3-clientes}

### Para qué sirve {#clientes-para-qué-sirve}

Es la lista única de clientes de la empresa, con los datos de contacto, cómo llegó cada uno y el historial de todas sus obras.

**Quién lo usa:** solo el dueño.

### Cargar un cliente {#clientes-cargar}

1. En "Clientes", tocá "Nuevo cliente".
2. Escribí el nombre y apellido o la razón social (es el único dato obligatorio), el teléfono y el correo si los tenés.
3. En "Cómo llegó" elegí si vino recomendado por un cliente anterior, por un arquitecto o de otra forma, y escribí quién lo recomendó.
4. Tocá "Dar de alta".

**[ESPACIO PARA CAPTURA 12]**
*Figura 12. Listado de clientes.*
*Cómo sacarla: ingresar como Dueño, entrar a Clientes con al menos cinco clientes cargados, algunos con obras y con distintos orígenes.*

### Ver la ficha de un cliente {#clientes-ficha}

Al tocar el nombre de un cliente se abre su ficha, con los datos de contacto, cómo llegó y sus obras separadas en activas, finalizadas y canceladas. Desde ahí se puede abrir cualquiera de sus obras.

**[ESPACIO PARA CAPTURA 13]**
*Figura 13. Ficha de un cliente con su historial de obras.*
*Cómo sacarla: abrir un cliente que tenga al menos una obra activa y una finalizada.*

### Editar o desactivar un cliente {#clientes-editar}

En el listado, "Editar" permite corregir sus datos y "Desactivar" lo marca como inactivo. Un cliente no se elimina nunca: se desactiva y conserva todo su historial. Si vuelve a encargar una obra, se lo activa de nuevo con "Activar".

## **Módulo 4: Presupuestos** {#módulo-4-presupuestos}

### Para qué sirve {#presupuestos-para-qué-sirve}

Reemplaza el armado de presupuestos desde cero en Excel o Word. Cubre las tres instancias reales del proceso: la cotización inicial, el anteproyecto (solo en reformas) y el presupuesto definitivo, más los adicionales por cambios menores. Cada versión queda guardada por separado, y el PDF que se le manda al cliente sale del sistema con el membrete de la empresa.

**Quién lo usa:** solo el dueño.

### El catálogo de rubros {#presupuestos-catálogo}

Antes de presupuestar hace falta el catálogo de rubros y subrubros, que es la clasificación con la que se arman los presupuestos y se ordenan los gastos. Se carga una sola vez y se completa cuando hace falta.

1. En "Presupuestos", tocá "Ver catálogo de rubros".
2. Tocá "Nuevo rubro", escribí el nombre (por ejemplo "Albañilería") y tocá "Crear".
3. Para sumar un subrubro, tocá "Agregar subrubro" en el rubro correspondiente (por ejemplo "Demolición" dentro de "Albañilería").
4. "Editar" permite cambiarle el nombre a un rubro, y "Desactivar" lo saca de la lista para presupuestos nuevos sin afectar a los que ya lo usan.

**El rubro de mano de obra.** El sistema ya trae creado el rubro "Mano de obra", marcado como tal. Si alguna vez querés que sea otro, editalo y tildá la casilla "Es el rubro de mano de obra". Su planilla funciona distinto (ver más abajo) y en el catálogo se lo reconoce por la leyenda "Rubro de mano de obra". Solo puede haber uno: si marcás otro, el anterior pierde la marca.

**[ESPACIO PARA CAPTURA 14]**
*Figura 14. Catálogo de rubros y subrubros.*
*Cómo sacarla: entrar a Presupuestos, "Ver catálogo de rubros", con al menos cuatro rubros con subrubros, uno de ellos marcado como rubro de mano de obra.*

### El listado de presupuestos {#presupuestos-listado}

Al entrar a "Presupuestos" se ve una fila por obra, con las instancias que tiene (cotización, anteproyecto, definitivo, adicionales), cuál de ellas está vigente y su total. Al tocar "Ver las instancias" se abre la lista de todas las versiones de esa obra, con su estado, la fecha y el total, y la indicación de cuál es la vigente.

**[ESPACIO PARA CAPTURA 15]**
*Figura 15. Listado de presupuestos agrupado por obra.*
*Cómo sacarla: con al menos tres obras presupuestadas, una de ellas reforma con cotización, anteproyecto y definitivo.*

**[ESPACIO PARA CAPTURA 16]**
*Figura 16. Instancias de presupuesto de una obra.*
*Cómo sacarla: tocar "Ver las instancias" en la obra reforma de la figura anterior.*

### Crear la cotización inicial {#presupuestos-cotización}

La cotización inicial es el precio estimativo que se le pasa al cliente en el primer contacto, calculado por metro cuadrado.

1. Tocá "Nuevo presupuesto".
2. Elegí la obra. Solo aparecen las obras en presupuestación.
3. En el tipo elegí "Cotización inicial".
4. Cargá los "Metros cuadrados" y el "Valor por m²". El sistema calcula el precio estimativo.
5. Tocá el botón para crearlo. El plazo estimado se toma solo de la duración cargada en la obra. Solo si la obra no la tiene, el formulario te lo pide.

### Crear el anteproyecto o el definitivo {#presupuestos-crear}

1. Tocá "Nuevo presupuesto" y elegí la obra.
2. Elegí el tipo. En una construcción no hay anteproyecto. En una reforma se puede pasar por cotización y anteproyecto antes del definitivo, o ir directo a la instancia que corresponda: ninguna de las anteriores es obligatoria.
3. Tocá el botón para crearlo. Igual que en la cotización, el plazo se toma de la obra. Se abre el detalle del presupuesto, en estado Borrador, listo para cargar.

Para armar el definitivo **a partir del anteproyecto**, sin volver a cargar todo, abrí el anteproyecto y usá "Usar como base" (ver más abajo).

### Cargar el presupuesto por rubros {#presupuestos-planilla}

Esta es la forma principal de cargar un presupuesto.

1. En el detalle del presupuesto, en "Presupuestar un rubro", tocá el rubro que querés cargar.
2. Debajo se despliega la planilla del rubro con **todos los materiales del catálogo** de ese rubro, cada uno con su unidad.
3. Completá la cantidad y el precio unitario solo en las filas que lleva la obra. Las filas que dejes vacías o con cantidad 0 no se cargan. A medida que escribís se ve el subtotal de cada fila y el total del rubro.
4. Tocá "Guardar el rubro".
5. Repetí con cada rubro.

Si volvés a abrir un rubro ya cargado, la planilla aparece con lo que cargaste, para corregirlo. Al guardar, lo que había en ese rubro se reemplaza por lo que quedó en la planilla.

> **El total incluye IVA.** Debajo de los subtotales por rubro se ven el subtotal, el IVA del 21% y el total con IVA, que es lo que paga el cliente. El anticipo y las cuotas se calculan sobre ese total. Los presupuestos que ya estaban enviados o aprobados antes de este cambio conservan su total original y muestran el IVA en cero.

**[ESPACIO PARA CAPTURA 17]**
*Figura 17. Planilla de un rubro de materiales.*
*Cómo sacarla: en un presupuesto definitivo en borrador, tocar un rubro con al menos seis materiales en el catálogo y completar cantidad y precio en tres o cuatro filas, dejando otras vacías.*

**La mano de obra.** Al tocar el rubro de mano de obra, la planilla no lista materiales sino **cada rubro con sus subrubros**, una fila por cada uno (por ejemplo "Albañilería, Demolición" y "Albañilería, Colocación"). En cada fila se escribe solo el total de mano de obra de ese trabajo. Las filas que dejes vacías o en 0 no aparecen en el presupuesto.

**[ESPACIO PARA CAPTURA 18]**
*Figura 18. Planilla de mano de obra.*
*Cómo sacarla: en el mismo presupuesto, tocar el rubro de mano de obra y completar el total de tres o cuatro filas, dejando las demás vacías.*

### Agregar un ítem suelto {#presupuestos-ítem}

Para algo que no está en el catálogo (por ejemplo la dirección de obra o un trabajo puntual):

1. Tocá "Agregar ítem".
2. Elegí el rubro y, si corresponde, el subrubro y el material del catálogo. El material es opcional.
3. Escribí la descripción, la cantidad, la unidad y el valor unitario.
4. Tocá "Guardar".

En la tabla de ítems, "Editar" y "Quitar" permiten corregir o sacar un ítem mientras el presupuesto está en borrador.

> **Tené en cuenta.** El valor unitario es interno: no aparece en el PDF que recibe el cliente. El cliente solo ve el subtotal de cada rubro, que es lo que se muestra en la sección "Subtotales por rubro".

### Definir el plan de pago {#presupuestos-plan}

1. Tocá "Plan de pago".
2. Cargá el "Anticipo (%)", la "Cantidad de cuotas" y el "Plazo estimado de obra".
3. El sistema muestra cuánto es el anticipo y cuánto cada cuota. Tocá el botón para guardar.

El módulo Cobranzas toma estos valores para generar el plan de cuotas de la obra cuando se aprueba el definitivo.

**[ESPACIO PARA CAPTURA 19]**
*Figura 19. Detalle de un presupuesto con ítems y subtotales por rubro.*
*Cómo sacarla: abrir un definitivo en borrador con tres rubros cargados y el plan de pago definido. Capturar la barra de botones, la tabla de ítems y los subtotales.*

### Ver el PDF {#presupuestos-pdf}

Tocá "Ver PDF". El documento se abre en una pestaña nueva tal como lo va a recibir el cliente, con el membrete de Granica, los subtotales por rubro, el subtotal, el IVA del 21%, el total con IVA y la forma de pago. Desde ahí se puede descargar o imprimir para mandarlo.

**[ESPACIO PARA CAPTURA 20]**
*Figura 20. PDF del presupuesto para el cliente.*
*Cómo sacarla: tocar "Ver PDF" en el presupuesto de la figura anterior y capturar la primera página.*

### Cambiar el estado {#presupuestos-estado}

Un presupuesto pasa por los estados Borrador, Enviado, Aprobado o Rechazado. Los botones "Marcar enviado", "Marcar aprobado" y "Marcar rechazado" aparecen según el estado en que está.

1. Cuando le mandás el PDF al cliente, tocá "Marcar enviado".
2. Cuando el cliente responde, tocá "Marcar aprobado" o "Marcar rechazado".

Al aprobar el presupuesto **definitivo**, la obra pasa sola a "En ejecución" y quedan habilitados los gastos, los pedidos, el avance y los cobros.

> **Tené en cuenta.** Solo un presupuesto en borrador se puede modificar. Una vez enviado, para hacer cambios hay que generar una versión nueva con "Usar como base". Así queda registrada cada versión que vio el cliente.

### Usar como base {#presupuestos-base}

"Usar como base" crea un presupuesto nuevo con una copia de los ítems del actual, sin modificar el original. Los dos quedan vinculados. Se usa para:

* Armar el **definitivo** a partir del anteproyecto.
* Generar un **adicional** cuando el cliente pide un cambio menor sobre un presupuesto aprobado.
* Hacer una **versión nueva** después de enviado.

1. Tocá "Usar como base".
2. En "Nueva instancia" elegí Anteproyecto, Definitivo o Adicional.
3. Tocá "Generar". El sistema avisa que se creó y ofrece "Abrirlo".

### Eliminar un presupuesto {#presupuestos-eliminar}

Lo recomendado es no eliminar presupuestos: si quedó sin efecto, conviene marcarlo como rechazado y conservar el registro. Igual, para corregir una carga equivocada, desde la lista de instancias de la obra se puede tocar "Eliminar". El sistema pide confirmación, avisa si es el definitivo aprobado (en ese caso la obra vuelve a "En presupuestación") y deja constancia en la auditoría. No se puede eliminar un presupuesto que se usó como base de otro.

### Botones y acciones disponibles {#presupuestos-botones}

| Botón / Acción | Qué hace |
| :---- | :---- |
| Ver catálogo de rubros | Abre el catálogo de rubros y subrubros. |
| Nuevo presupuesto | Crea una cotización inicial, un anteproyecto o un definitivo para una obra en presupuestación. |
| Presupuestar un rubro | Despliega la planilla del rubro con sus materiales para cargar cantidades y precios. |
| Guardar el rubro | Guarda la planilla del rubro. |
| Agregar ítem | Suma un ítem que no está en el catálogo. |
| Plan de pago | Define el anticipo, las cuotas y el plazo. |
| Marcar enviado / aprobado / rechazado | Cambia el estado del presupuesto. |
| Usar como base | Crea una versión nueva, un definitivo o un adicional copiando los ítems. |
| Ver PDF | Abre el documento para el cliente. |
| Eliminar | Borra el presupuesto de forma definitiva, con confirmación. |

## **Módulo 5: Materiales** {#módulo-5-materiales}

### Para qué sirve {#materiales-para-qué-sirve}

Es el catálogo único de materiales. De acá eligen tanto los presupuestos como los pedidos, así un mismo material se llama siempre igual en todas las obras.

**Quién lo usa:** el dueño lo carga. Los capataces lo consultan al pedir materiales.

### Cargar un material {#materiales-cargar}

1. En "Materiales", tocá "Nuevo material".
2. Escribí el nombre (por ejemplo "Cemento CP40").
3. Elegí el rubro al que pertenece, del catálogo de Presupuestos.
4. Escribí la unidad de medida (por ejemplo "bolsa 50 kg"). Es lo que define cómo se interpreta la cantidad en los pedidos y presupuestos.
5. Tocá "Guardar".

El listado se puede buscar por nombre y filtrar por rubro y por estado. "Editar" permite corregir un material y "Desactivar" lo saca de la lista para nuevos presupuestos y pedidos, sin afectar a los que ya lo usan.

**[ESPACIO PARA CAPTURA 21]**
*Figura 21. Catálogo de materiales.*
*Cómo sacarla: entrar a Materiales con al menos diez materiales de tres rubros distintos, uno de ellos inactivo.*

> **Tené en cuenta.** No puede haber dos materiales con el mismo nombre dentro del mismo rubro. Tampoco se puede cargar un material en un rubro que esté desactivado.

## **Módulo 6: Proveedores** {#módulo-6-proveedores}

### Para qué sirve {#proveedores-para-qué-sirve}

Registra los corralones y proveedores por zona, con el historial de precios que fueron dando y las observaciones sobre cómo se comportaron (demoras, faltantes, material roto). Reemplaza las cotizaciones que se piden por WhatsApp y se pierden.

**Quién lo usa:** el dueño lo carga. El capataz general lo consulta.

### Cargar un proveedor {#proveedores-cargar}

1. En "Proveedores", tocá "Nuevo proveedor".
2. Escribí el nombre (por ejemplo "Corralón San Martín") y la zona de cobertura (por ejemplo "Zona Norte, CABA"). La zona es el criterio principal para elegir a quién pedirle.
3. Si la conocés, escribí la "Dirección" del corralón (por ejemplo "Av. San Martín 1234, Vicente López"). Es opcional y sirve para ubicarlo o para ir a retirar material.
4. Cargá el teléfono y el correo. El teléfono es el que se usa para mandarle los pedidos por WhatsApp, así que conviene que sea el celular del corralón.
5. Tocá "Guardar".

**[ESPACIO PARA CAPTURA 22]**
*Figura 22. Listado de proveedores.*
*Cómo sacarla: entrar a Proveedores con al menos cuatro proveedores de zonas distintas, con cotizaciones y observaciones cargadas.*

### Registrar cotizaciones y observaciones {#proveedores-ficha}

1. En el listado, tocá "Ver ficha" en el proveedor.
2. En la parte de **Cotizaciones**, elegí el material, escribí el precio que te dio y tocá "Registrar". La fecha la pone el sistema, para saber después qué tan vieja es cada cotización.
3. En la parte de **Comportamiento**, escribí la observación (por ejemplo "Demoró una semana la entrega de hierro") y registrala.

**[ESPACIO PARA CAPTURA 23]**
*Figura 23. Ficha de un proveedor con cotizaciones y observaciones.*
*Cómo sacarla: abrir la ficha de un proveedor con al menos tres cotizaciones y dos observaciones.*

### Comparar precios {#proveedores-comparar}

1. Tocá "Comparar precios".
2. Elegí el material.
3. El sistema muestra la última cotización de cada proveedor activo, de menor a mayor precio. Si alguna es muy vieja, lo avisa, porque con la variación de precios conviene pedirla de nuevo antes de decidir.

**[ESPACIO PARA CAPTURA 24]**
*Figura 24. Comparador de precios de un material.*
*Cómo sacarla: tocar "Comparar precios" y elegir un material cotizado por al menos tres proveedores.*

> **Tené en cuenta.** Un proveedor no se elimina: se desactiva y conserva su historial. Un proveedor inactivo no se puede elegir al aprobar un pedido.

## **Módulo 7: Pedidos de materiales** {#módulo-7-pedidos}

### Para qué sirve {#pedidos-para-qué-sirve}

Formaliza el circuito de pedido, aprobación y recepción de materiales, que antes pasaba por WhatsApp sin dejar registro. Cada pedido recorre estos estados:

1. **Pendiente de Aprobación:** el capataz lo cargó y espera al dueño.
2. **Enviado al Proveedor:** el dueño lo aprobó, eligió el corralón y confirmó los precios.
3. **Recibido Completo** o **Recibido con Diferencias:** llegó a la obra y alguien confirmó la recepción con la foto del remito.
4. **Anulado:** no se concretó.

**Quién lo usa:** los capataces cargan pedidos y confirman recepciones (el capataz de obra, desde el celular, ver el apartado "Uso desde el celular"). Solo el dueño aprueba y envía.

Este circuito es para los materiales de corralón y plomería. Los materiales de instalaciones los sigue gestionando el dueño de forma directa y no pasan por acá.

### El listado de pedidos {#pedidos-listado}

En "Pedidos" se ven todos los pedidos con su obra, proveedor, cantidad de ítems, total, fecha y estado. Se pueden filtrar por obra y por estado. Los botones de cada fila cambian según el estado del pedido.

**[ESPACIO PARA CAPTURA 25]**
*Figura 25. Listado de pedidos en distintos estados.*
*Cómo sacarla: ingresar como Dueño con al menos un pedido en cada estado (pendiente, enviado, recibido completo, recibido con diferencias y anulado).*

### Cargar un pedido desde la computadora {#pedidos-cargar}

1. Tocá "Nuevo pedido".
2. Elegí la obra. Solo se puede pedir para obras en ejecución.
3. Elegí un material y escribí la cantidad. Con "Agregar material" sumás más líneas.
4. Tocá "Enviar a aprobación".

No hace falta elegir proveedor ni poner precios: eso lo define el dueño al aprobar.

### Aprobar y enviar un pedido (dueño) {#pedidos-aprobar}

1. En el pedido pendiente, tocá "Aprobar".
2. Elegí el corralón. La zona es el criterio principal: un corralón que no llega a la obra no sirve por más barato que sea.
3. El sistema completa el precio de cada material con la última cotización de ese proveedor, marcada como "última cotización". Revisalos y corregí los que hagan falta.
4. Controlá el total del pedido y tocá "Aprobar y enviar".

El pedido pasa a "Enviado al Proveedor".

**[ESPACIO PARA CAPTURA 26]**
*Figura 26. Aprobación de un pedido con precios sugeridos.*
*Cómo sacarla: tocar "Aprobar" en un pedido pendiente y elegir un proveedor que tenga cotizaciones de esos materiales, de modo que se vean los precios sugeridos y el total.*

### Mandarle la orden al corralón {#pedidos-orden}

Una vez aprobado, hay dos formas de hacerle llegar el pedido al proveedor:

* **Orden PDF:** abre la orden de pedido en PDF, con los materiales, las cantidades, los precios acordados y la dirección de entrega. No incluye ningún dato interno de la empresa (ni el presupuesto, ni la ganancia, ni el cliente).
* **WhatsApp:** prepara el mensaje para mandarlo desde tu propio teléfono.
  1. Tocá "WhatsApp".
  2. El sistema muestra a qué número se le va a escribir, el mensaje con el pedido completo y el enlace a la orden en PDF.
  3. Revisá que el número sea el del corralón. Si no lo es, corregilo en Proveedores antes de mandar.
  4. Tocá "Abrir WhatsApp". Se abre WhatsApp con el mensaje ya escrito y solo queda tocar Enviar.

El enlace de la orden lo abre el corralón sin cuenta ni contraseña, vence a los treinta días y se puede cortar antes si hace falta.

**[ESPACIO PARA CAPTURA 27]**
*Figura 27. Preparación del envío por WhatsApp.*
*Cómo sacarla: tocar "WhatsApp" en un pedido enviado cuyo proveedor tenga un celular bien cargado, y capturar la ventana con el número, el mensaje y el enlace.*

**[ESPACIO PARA CAPTURA 28]**
*Figura 28. Orden de pedido en PDF.*
*Cómo sacarla: tocar "Orden PDF" en el mismo pedido y capturar el documento.*

> **Tené en cuenta.** Si el teléfono del proveedor está mal cargado o no se puede interpretar como un celular, el sistema no arma el enlace de WhatsApp y lo avisa, en lugar de adivinar el número. Así se evita mandarle el pedido de una obra a un desconocido.

### Confirmar la recepción desde la computadora {#pedidos-recibir}

1. En el pedido enviado, tocá "Confirmar recepción".
2. Subí la foto del remito. Es obligatoria: reemplaza al remito en papel, que es lo que antes se perdía.
3. Si faltó algo o llegó distinto, escribilo en "¿Faltó algo o llegó distinto?". Si llegó todo, dejalo vacío.
4. Tocá "Confirmar recepción".

El pedido queda como "Recibido Completo" o "Recibido con Diferencias" según lo que hayas escrito, y **el gasto de los materiales se carga solo en la obra**, agrupado por rubro, sin tener que cargarlo de nuevo en Gastos.

### Anular un pedido {#pedidos-anular}

Si un pedido no se concreta, tocá "Anular", escribí el motivo (por ejemplo "El proveedor no tenía stock") y confirmá. No se puede anular un pedido que ya llegó a la obra.

### Botones y acciones disponibles {#pedidos-botones}

| Botón / Acción | Qué hace |
| :---- | :---- |
| Nuevo pedido | Carga un pedido de materiales para una obra en ejecución. |
| Aprobar | Solo para el dueño: elige el proveedor, confirma precios y envía el pedido. |
| Orden PDF | Abre la orden de pedido para el corralón. |
| WhatsApp | Prepara el mensaje con el pedido para mandarlo desde el teléfono del dueño. |
| Confirmar recepción | Registra la llegada del material con la foto del remito y las diferencias, si las hubo. |
| Anular | Anula un pedido que no se concreta, con motivo. |

## **Módulo 8: Gastos** {#módulo-8-gastos}

### Para qué sirve {#gastos-para-qué-sirve}

Registra cada gasto de la obra una sola vez y lo compara en el momento contra lo presupuestado en cada rubro. Reemplaza la doble carga de anotar en el Excel del celular y pasarlo el fin de semana a la planilla de la computadora.

**Quién lo usa:** solo el dueño carga y anula gastos. El capataz general los consulta.

### El panel de una obra {#gastos-panel}

1. En "Gastos", elegí la obra en "Elegir obra…".
2. Arriba aparece el estado financiero: lo presupuestado, lo gastado y el porcentaje consumido, la ganancia estimada (o la pérdida, si se gastó de más) y el total en gastos hormiga.
3. Debajo, la tabla "Presupuestado contra gastado, por rubro", con un semáforo para cada rubro:
   * **Verde:** se gastó menos del 90% de lo presupuestado.
   * **Amarillo:** entre el 90% y el 100%.
   * **Rojo:** se superó lo presupuestado.
   * **Sin presupuesto:** se está gastando en un rubro que nadie previó.
4. Al final, la lista de gastos registrados, que se puede filtrar por tipo.

**[ESPACIO PARA CAPTURA 29]**
*Figura 29. Panel de gastos de una obra con el semáforo por rubro.*
*Cómo sacarla: elegir una obra en ejecución con gastos en al menos cuatro rubros, de modo que haya rubros en verde, amarillo y rojo, y algún gasto hormiga.*

### Cargar un gasto {#gastos-cargar}

1. Con la obra elegida, tocá "Nuevo gasto".
2. Elegí el rubro (es contra el rubro que se compara con el presupuesto) y, si querés, el subrubro.
3. Elegí el tipo: Material, Mano de Obra, Gasto Hormiga u Otro.
4. Escribí el monto y la fecha en que ocurrió el gasto, que no tiene por qué ser la de hoy.
5. Si querés, agregá una descripción y subí la foto del comprobante. Es opcional, pero es lo que después respalda el gasto ante una duda.
6. Tocá "Registrar gasto".

El semáforo y la ganancia estimada se actualizan en el momento.

**[ESPACIO PARA CAPTURA 30]**
*Figura 30. Formulario de carga de un gasto.*
*Cómo sacarla: tocar "Nuevo gasto" y completar rubro, tipo "Gasto Hormiga", monto, fecha y descripción.*

> **Tené en cuenta.** Solo se pueden cargar gastos de obras en ejecución con presupuesto definitivo aprobado: sin presupuesto no hay contra qué comparar. Los gastos de materiales pedidos por el sistema no se cargan a mano, se generan solos al confirmar la recepción del pedido.

### Anular un gasto {#gastos-anular}

Si un gasto se cargó mal (por ejemplo, por duplicado), tocá "Anular", escribí el motivo y confirmá. El gasto no se elimina: deja de sumar en la comparación contra el presupuesto, pero sigue registrado con su motivo. Si el dato era otro, cargá un gasto nuevo con el valor correcto.

### El reporte de gastos {#gastos-reporte}

Desde la ficha de la obra, en "Documentos", el "Reporte de gastos" genera un PDF con el estado financiero completo: presupuestado, gastado y semáforo por rubro, y la lista de gastos confirmados. Es de uso interno porque incluye la ganancia estimada.

## **Módulo 9: Personal** {#módulo-9-personal}

### Para qué sirve {#personal-para-qué-sirve}

Registra a los operarios de la empresa, en qué obras trabaja cada uno y sus faltas. El objetivo no es controlar horarios sino que el dueño pueda detectar faltas reiteradas y decidir si corresponde hablar con alguien.

**Quién lo usa:** el dueño carga y modifica. El capataz general consulta.

### Cargar un operario {#personal-cargar}

1. En "Personal", tocá "Nuevo operario".
2. Escribí el nombre y, si lo tenés, el teléfono.
3. Guardalo. Las obras se asignan después, desde su ficha.

El listado muestra cada operario con sus obras, la cantidad de faltas y su estado, y se puede filtrar por obra y por estado.

**[ESPACIO PARA CAPTURA 31]**
*Figura 31. Listado de operarios con sus faltas.*
*Cómo sacarla: entrar a Personal con al menos cinco operarios asignados a distintas obras, alguno con varias faltas.*

### La ficha del operario {#personal-ficha}

Al tocar "Ver ficha" se abre la ficha con:

* **Obras:** las obras en las que trabaja y en las que trabajó (estas últimas en gris). Para asignarlo a otra obra, elegila en "Asignar a una obra…" y tocá "Asignar". Para sacarlo de una obra, tocá la cruz al lado de esa obra. La asignación no se borra, queda en el historial.
* **Registrar una falta:** elegí la obra, la fecha y, si se sabe, el motivo, y tocá "Registrar". El motivo no es obligatorio porque muchas veces no se sabe en el momento.
* **Historial de faltas:** todas las faltas registradas. En las que no tienen motivo aparece "Agregar motivo", para completarlo cuando el operario avisa.

**[ESPACIO PARA CAPTURA 32]**
*Figura 32. Ficha de un operario.*
*Cómo sacarla: abrir la ficha de un operario asignado a dos obras, con una obra anterior y al menos tres faltas, una sin motivo.*

> **Tené en cuenta.** Solo se puede registrar una falta en una obra donde el operario está o estuvo asignado, y no se pueden cargar dos faltas del mismo operario en la misma obra y la misma fecha. Al desactivar un operario se cierran sus asignaciones vigentes, pero se conserva todo su historial.

## **Módulo 10: Avance de obra** {#módulo-10-avance}

### Para qué sirve {#avance-para-qué-sirve}

Mide el avance físico de cada obra por hitos (las etapas de la obra) y lo compara con el avance financiero, es decir, con cuánto del presupuesto ya se gastó. Así se detectan las obras que gastaron mucho sin avanzar en la misma proporción. Reemplaza al cronograma que se armaba al principio y no se actualizaba.

**Quién lo usa:** el dueño y el capataz general definen los hitos y los marcan como completados. El capataz de obra consulta el avance de su obra.

### Definir las etapas de la obra {#avance-etapas}

Los hitos se definen una vez que la obra está en ejecución. Hay dos formas de hacerlo.

**Por duración (recomendada).** No hace falta calcular porcentajes:

1. En "Avance", elegí la obra y tocá "Cargar etapas".
2. Para cada etapa escribí qué hay que hacer (por ejemplo "Demolición de una pared"), de qué rubro es y cuánto lleva, en días o semanas.
3. Con "Agregar etapa" sumás más.
4. Tocá "Guardar las etapas".

El sistema reparte el 100% del avance según la duración de cada etapa: una etapa de tres semanas pesa más que una de dos días sin que tengas que calcularlo.

**[ESPACIO PARA CAPTURA 33]**
*Figura 33. Carga de etapas por duración.*
*Cómo sacarla: en una obra en ejecución sin hitos, tocar "Cargar etapas" y completar cinco etapas con rubros y duraciones distintas, de modo que se vea la columna "Pesa".*

**Por porcentaje.** Si preferís asignar el peso a mano:

1. Tocá "Definir por %".
2. Escribí cada etapa con su ponderación, es decir, qué porcentaje del total representa.
3. La suma tiene que dar exactamente 100%. El sistema la muestra mientras cargás.
4. Tocá "Guardar hitos".

En esa misma pantalla se puede partir de una plantilla de etapas típicas, si hay alguna guardada.

> **Tené en cuenta.** El plan de hitos se puede rehacer mientras ninguno esté completado. Una vez que se completó el primero, cambiarlo borraría ese registro, por eso el sistema no lo permite.

### Marcar un hito como completado {#avance-completar}

1. En la tabla de hitos, tocá "Completar" en la etapa terminada.
2. Indicá la fecha en que se terminó. Es obligatoria, porque sin ella no se puede saber si la obra viene en plazo.
3. Si querés, escribí una observación (por ejemplo "Se demoró por lluvia").
4. Tocá "Marcar completado".

Si hay etapas anteriores sin completar, el sistema avisa. Si la etapa realmente se adelantó, tildá la confirmación para completarla igual.

Si un hito se marcó por error, "Reabrir" lo vuelve a dejar pendiente. Al completar el último hito, la obra pasa sola a "Finalizada".

### Leer el avance {#avance-leer}

Arriba de la tabla de hitos se ven dos barras:

* **Avance físico:** la suma del peso de los hitos completados.
* **Avance financiero:** qué porcentaje del presupuesto aprobado ya se gastó.

Si el avance financiero supera al físico en más de quince puntos, el sistema muestra el aviso "Se está gastando más rápido de lo que se avanza".

**[ESPACIO PARA CAPTURA 34]**
*Figura 34. Panel de avance con las dos barras y la tabla de hitos.*
*Cómo sacarla: elegir una obra con la mitad de los hitos completados y un gasto mayor al avance, para que aparezca el aviso de desfasaje.*

## **Módulo 11: Cobranzas** {#módulo-11-cobranzas}

### Para qué sirve {#cobranzas-para-qué-sirve}

Lleva el cobro de cada obra: el anticipo, las cuotas quincenales y la actualización del saldo por el índice CAC. Reemplaza la planilla por obra y la planilla resumen que se actualizaban a mano, y avisa solo cuándo vence o venció una cuota.

**Quién lo usa:** solo el dueño.

### Generar el plan de cobro {#cobranzas-generar}

Cuando se aprueba el presupuesto definitivo de una obra:

1. En "Cobranzas", elegí la obra.
2. Tocá "Generar plan de cobro".

El sistema arma el plan con el anticipo (la cuota cero) y las cuotas quincenales, tomando el porcentaje de anticipo, la cantidad de cuotas y el total del presupuesto aprobado. El primer vencimiento queda a quince días de la fecha en que se genera el plan. Mientras el plan no se genera, la obra aparece en el Tablero como pendiente.

### El plan de cobro de una obra {#cobranzas-plan}

Al elegir una obra se ve:

* Arriba, el total del plan, lo cobrado, lo que resta cobrar y el próximo vencimiento.
* La tabla de cuotas, con el número, el monto, el vencimiento, el estado y lo pagado de cada una.

Cada cuota puede estar:

* **Pendiente:** no entró nada y no venció.
* **Parcial:** entró una parte y queda saldo.
* **Abonada:** se cobró entera.
* **Vencida:** pasó la fecha y todavía se debe algo. Una cuota pagada en parte que pasó su fecha sigue figurando como vencida, porque el resto se sigue debiendo.

El estado no se elige a mano: el sistema lo calcula a partir de los pagos y del calendario.

**[ESPACIO PARA CAPTURA 35]**
*Figura 35. Plan de cobro de una obra.*
*Cómo sacarla: elegir una obra con el anticipo abonado, una cuota parcial, una vencida y el resto pendientes.*

### Registrar un pago {#cobranzas-pago}

1. En la cuota que pagó el cliente, tocá "Registrar pago".
2. El sistema propone el saldo completo de la cuota. Si el cliente pagó solo una parte, cambiá el importe: la cuota queda como pagada en parte y el resto sigue figurando como deuda.
3. Indicá la fecha de pago, el medio (Transferencia, Efectivo o Cheque) y el comprobante que le diste (Mensaje, Recibo o Planilla), si corresponde.
4. Tocá "Registrar pago".

**[ESPACIO PARA CAPTURA 36]**
*Figura 36. Registro de un pago parcial.*
*Cómo sacarla: tocar "Registrar pago" en una cuota pendiente y cambiar el monto por uno menor al saldo, sin confirmar.*

> **Tené en cuenta.** No se puede registrar un pago mayor al saldo de la cuota. Si el cliente paga fuera de término, el monto de la cuota no cambia: la empresa no cobra recargos.

### Anular un pago {#cobranzas-anular}

Si un pago se cargó por error o, por ejemplo, rebotó un cheque, tocá "Anular pago" en la cuota y escribí el motivo. Se anulan los pagos de esa cuota, que vuelve a deberse entera. Los pagos no se borran: quedan registrados como anulados, con su motivo, para conservar el historial del estado de cuenta.

### Actualizar las cuotas por CAC {#cobranzas-cac}

Una vez por mes, el saldo pendiente se actualiza por el índice de la Cámara Argentina de la Construcción.

1. Tocá "Actualizar por CAC".
2. En "Cargar la actualización del mes" elegí el mes y escribí **por cuánto se multiplican las cuotas**. Por ejemplo, 1,04 sube un 4%: una cuota de $1.000 pasa a $1.040. Un 1 exacto deja todo igual. Tocá "Guardar".
3. El sistema muestra la vista previa: el saldo pendiente actual, el saldo actualizado y cuántas cuotas se recalculan. Revisá el número. Si no cierra, el coeficiente está mal cargado.
4. Tocá "Aplicar actualización".

**[ESPACIO PARA CAPTURA 37]**
*Figura 37. Vista previa de la actualización por CAC.*
*Cómo sacarla: cargar un coeficiente de prueba (por ejemplo 1,04) en una obra con cuotas pendientes y capturar la vista previa antes de aplicar.*

> **Tené en cuenta.** Se carga el **coeficiente**, no el valor del índice que publica la Cámara. El sistema rechaza valores mayores a diez, que es la forma de atrapar ese error. La actualización se aplica solo sobre lo que falta cobrar: las cuotas abonadas no se tocan, y en una cuota pagada en parte solo se actualiza el saldo. El mismo coeficiente no se puede aplicar dos veces a la misma obra.

### Cuánto resta cobrar de cada obra {#cobranzas-resumen}

Al final de la pantalla está la tabla "Cuánto resta cobrar de cada obra", con el total, lo cobrado, lo que resta, el próximo vencimiento y la cantidad de cuotas vencidas de cada obra. Las obras con cuotas vencidas aparecen primero. Reemplaza a la planilla resumen que se actualizaba a mano.

**[ESPACIO PARA CAPTURA 38]**
*Figura 38. Resumen de cobros de todas las obras.*
*Cómo sacarla: con al menos tres obras con plan de cobro, una de ellas con cuotas vencidas, capturar la tabla del final de Cobranzas.*

### La planilla de pagos para el cliente {#cobranzas-planilla}

Desde la ficha de la obra, en "Documentos", la "Planilla de pagos" genera el PDF que se le entrega al cliente con las cuotas, los vencimientos, lo pagado y el saldo. Conviene generarla después de cada pago o actualización por CAC.

**[ESPACIO PARA CAPTURA 39]**
*Figura 39. Planilla de pagos en PDF.*
*Cómo sacarla: desde la ficha de una obra con pagos registrados, tocar "Planilla de pagos" y capturar el documento.*

## **Módulo 12: Portfolio** {#módulo-12-portfolio}

### Para qué sirve {#portfolio-para-qué-sirve}

Es una vidriera con fotos de las obras terminadas, organizada por tipo de trabajo, para mostrarle a los clientes que llegan por recomendación. La vidriera muestra solo las fotos y el tipo de trabajo: no muestra el nombre del cliente ni la dirección, y no tiene formulario de contacto, porque la empresa trabaja únicamente por recomendación.

**Quién lo usa:** el dueño la administra. La vidriera pública la puede ver cualquiera que tenga la dirección, sin usuario ni contraseña.

### Publicar una obra {#portfolio-publicar}

1. En "Portfolio", tocá "Publicar una obra".
2. Elegí la obra. Solo aparecen obras finalizadas.
3. Escribí el tipo de trabajo (por ejemplo "Refacción"). Es lo que agrupa la vidriera y podés escribir uno nuevo.
4. Tocá "Crear publicación". Se crea sin publicar, para cargar primero las fotos.
5. Tocá "Agregar una foto de la obra" y subí las fotos una por una. Desde el celular, el botón abre directamente la galería o la cámara.
6. Arrastrá las fotos para ordenarlas. La primera es la portada de la obra en la vidriera.
7. Tocá "Publicar en la vidriera".

Para sacar una obra de la vidriera, tocá "Despublicar". Las fotos quedan guardadas para volver a publicarla sin cargarlas de nuevo.

**[ESPACIO PARA CAPTURA 40]**
*Figura 40. Administración del portfolio.*
*Cómo sacarla: con al menos dos obras finalizadas publicadas, una de ellas con cuatro fotos, capturar la pantalla de Portfolio mostrando la galería con la portada marcada.*

### La vidriera pública {#portfolio-vidriera}

La vidriera se abre desde "Ver la vidriera" o directamente en [DIRECCIÓN DEL SISTEMA]/obras-realizadas. Esa es la dirección que se le pasa al cliente referido. Muestra las obras publicadas, se puede filtrar por tipo de trabajo y al tocar una foto se ve en grande.

**[ESPACIO PARA CAPTURA 41]**
*Figura 41. Vidriera pública de obras realizadas.*
*Cómo sacarla: abrir [DIRECCIÓN DEL SISTEMA]/obras-realizadas en una ventana de incógnito (sin haber ingresado), con al menos tres obras publicadas de dos tipos distintos. Si se puede, sacar también una captura desde el celular.*

## **Módulo 13: Usuarios** {#módulo-13-usuarios}

### Para qué sirve {#usuarios-para-qué-sirve}

Administra las cuentas de las personas que entran al sistema. Es distinto de Personal: en Personal están todos los operarios, trabajen o no con el sistema, y acá solo quienes tienen usuario y contraseña.

**Quién lo usa:** solo el dueño. Se entra desde el círculo con las iniciales, opción "Usuarios".

### Crear una cuenta {#usuarios-crear}

1. Tocá "Nueva cuenta".
2. Escribí el nombre de usuario, sin espacios ni acentos (por ejemplo "jorge"). Es lo que la persona va a escribir para entrar.
3. Escribí una contraseña inicial de al menos diez caracteres.
4. Elegí el rol: Dueño, Capataz General o Capataz de Obra.
5. Si la persona también es operario, elegilo en "Vincular con un operario". Sirve para que quien confirma una recepción desde el celular quede identificado como esa persona de Personal.
6. Tocá "Crear cuenta".

Pasale el usuario y la contraseña inicial por otro medio. En su primer ingreso el sistema le va a pedir que elija una propia.

**[ESPACIO PARA CAPTURA 42]**
*Figura 42. Listado de cuentas de acceso.*
*Cómo sacarla: ingresar como Dueño, entrar a Usuarios con al menos una cuenta de cada rol y una dada de baja.*

**[ESPACIO PARA CAPTURA 43]**
*Figura 43. Alta de una cuenta nueva.*
*Cómo sacarla: tocar "Nueva cuenta" y completar usuario, rol Capataz de Obra y operario vinculado, sin escribir la contraseña real.*

### Otras acciones sobre una cuenta {#usuarios-acciones}

* **Cambiar el rol:** desde el selector de rol en la fila de la cuenta.
* **Contraseña:** asigna una contraseña provisoria cuando alguien se olvidó la suya. La persona la va a tener que cambiar al entrar, y se cierran las sesiones que tuviera abiertas.
* **Dar de baja:** deshabilita la cuenta indicando el motivo (por ejemplo "Dejó la empresa"). La persona deja de poder entrar en el acto, aunque tenga una sesión abierta. La cuenta no se elimina: su historial y su registro en la auditoría se conservan.
* **Reactivar:** vuelve a habilitar una cuenta dada de baja.

> **Tené en cuenta.** Nadie puede darse de baja a sí mismo, y el sistema no permite quedarse sin ninguna cuenta activa con rol de Dueño, ni dando de baja ni cambiando el rol, porque nadie podría volver a administrar el sistema.

## **Módulo 14: Accesos y auditoría** {#módulo-14-accesos}

### Para qué sirve {#accesos-para-qué-sirve}

Define qué puede hacer cada rol y muestra el registro de las acciones importantes. Es lo que permite delegar tareas a los capataces sin que vean la información financiera.

**Quién lo usa:** solo el dueño. Se entra desde el círculo con las iniciales, opción "Accesos".

### Permisos por rol {#accesos-permisos}

La tabla "Permisos por rol" muestra, para cada rol, una casilla por permiso. Cada módulo tiene un permiso para ver y otro para editar.

1. Marcá o desmarcá las casillas del rol.
2. Tocá "Guardar" en la fila de ese rol.

El cambio se aplica en el momento a todas las cuentas que tienen ese rol, sin que tengan que volver a entrar.

**[ESPACIO PARA CAPTURA 44]**
*Figura 44. Matriz de permisos por rol.*
*Cómo sacarla: ingresar como Dueño y entrar a Accesos. Capturar la tabla de permisos con los tres roles.*

> **Tené en cuenta.** Hay cambios que el sistema rechaza aunque se marque la casilla: quitarle al rol Dueño la administración de accesos o de usuarios (nadie podría volver a entrar a corregirlo) y darle a un capataz el permiso de aprobar pedidos o cualquier permiso de Presupuestos o de Cobranzas, que son decisiones indelegables del dueño.

### Auditoría {#accesos-auditoría}

Debajo de los permisos está la lista "Últimas acciones sensibles", con cuándo se hizo cada acción, qué usuario la hizo y qué fue. Se registran las acciones que importan, no cada consulta:

* Ingresos al sistema y bloqueos de cuenta.
* Alta, baja, reactivación y cambios de rol y de contraseña de las cuentas.
* Cambios de permisos.
* Aprobación y eliminación de presupuestos.
* Aprobación y anulación de pedidos.
* Anulación de gastos.
* Cancelación de obras.
* Registro y anulación de cobros, y actualizaciones por CAC.

Ningún registro se puede editar ni borrar desde el sistema.

**[ESPACIO PARA CAPTURA 45]**
*Figura 45. Registro de auditoría.*
*Cómo sacarla: después de haber hecho varias de las acciones de este manual, capturar la lista "Últimas acciones sensibles" con al menos ocho registros de distintos tipos.*

## **Uso desde el celular (Capataz de Obra)** {#uso-desde-el-celular}

El capataz de obra trabaja desde el celular, con pantallas simples y botones grandes pensados para usarse en la obra.

### La pantalla de inicio {#celular-inicio}

Al entrar, si el capataz está asignado a más de una obra, el sistema pregunta "¿Dónde estás hoy?" y muestra sus obras para elegir. Si tiene una sola, la elige solo. La próxima vez recuerda la última obra elegida, y con "Cambiar de obra" se elige otra.

La pantalla de la obra muestra:

* El avance de la obra y, si corresponde, el aviso de que pasó su fecha estimada de fin.
* Botones grandes para "Pedir materiales" y para ver el avance de la obra.
* "Últimos movimientos": los pedidos recientes y en qué anda cada uno (esperando aprobación, en camino, recibido).

**[ESPACIO PARA CAPTURA 46]**
*Figura 46. Pantalla de inicio del capataz de obra en el celular.*
*Cómo sacarla: ingresar desde el celular (o desde la computadora con el modo de vista de celular del navegador) con una cuenta de Capataz de Obra asignada a una obra con hitos y pedidos. Capturar la pantalla completa.*

> **Tené en cuenta.** Si el capataz no está asignado a ninguna obra, el sistema muestra "No tenés ninguna obra asignada". Lo resuelve el dueño asignándolo a una obra desde Personal, vinculando antes su cuenta con su registro de operario en Usuarios.

### Pedir materiales {#celular-pedir}

1. Tocá "Pedir materiales". Se abre la pestaña "Pedir" con el catálogo.
2. En el catálogo, sumá unidades de cada material que necesitás con los botones de más y menos.
3. Revisá la lista "En el pedido".
4. Tocá "Enviar pedido". El pedido queda esperando la aprobación de la oficina antes de salir del corralón.

**[ESPACIO PARA CAPTURA 47]**
*Figura 47. Pedido de materiales desde el celular.*
*Cómo sacarla: con la cuenta de capataz de obra, entrar a pedir materiales y sumar tres materiales distintos antes de enviar.*

### Recibir un pedido {#celular-recibir}

1. Cuando llega el camión, entrá a "Pedir materiales" y tocá la pestaña "Recibir".
2. Elegí cuál de los pedidos llegó. Solo aparecen los que la oficina ya aprobó y envió al proveedor.
3. Sacá la foto del remito. El botón abre directamente la cámara del celular.
4. Respondé "¿Hubo diferencias?": "Vino todo" o "Faltó algo". Si faltó algo, escribí qué (por ejemplo "faltaron 4 bolsas, 2 rotas").
5. Tocá "Confirmar recepción".

Al confirmar, el gasto de los materiales se carga solo en la obra.

**[ESPACIO PARA CAPTURA 48]**
*Figura 48. Confirmación de recepción con foto del remito desde el celular.*
*Cómo sacarla: con un pedido en estado "Enviado al Proveedor", entrar a la pestaña "Recibir", elegirlo, subir una foto de un remito de prueba y marcar "Faltó algo" con una nota, sin confirmar.*

### Ver el avance de la obra {#celular-avance}

Desde el inicio, el acceso al avance muestra los hitos de la obra con su estado. El capataz de obra los consulta, y los marca el capataz general o el dueño. Si la cuenta tiene permiso para marcarlos, alcanza con tocar el hito para completarlo con la fecha del día, y la oficina lo ve al instante.

**[ESPACIO PARA CAPTURA 49]**
*Figura 49. Avance de la obra desde el celular.*
*Cómo sacarla: con la cuenta de capataz de obra, entrar al avance de una obra con algunos hitos completados.*

## **Qué puede hacer cada rol** {#qué-puede-hacer-cada-rol}

La siguiente tabla resume el acceso de cada rol a cada módulo. **Total** significa ver y modificar, **Consulta** significa solo ver, y **Sin acceso** significa que el módulo no aparece en su menú.

| Módulo | Dueño | Capataz General | Capataz de Obra |
| :---- | :---- | :---- | :---- |
| Tablero | Total | Consulta (sin montos) | Sin acceso |
| Obras | Total | Consulta | Consulta (sus obras) |
| Clientes | Total | Sin acceso | Sin acceso |
| Presupuestos | Total | Sin acceso | Sin acceso |
| Materiales | Total | Consulta | Consulta |
| Proveedores | Total | Consulta | Sin acceso |
| Pedidos | Total | Carga y recepción | Carga y recepción (sus obras) |
| Gastos | Total | Consulta | Sin acceso |
| Personal | Total | Consulta | Sin acceso |
| Avance | Total | Total | Consulta (sus obras) |
| Cobranzas | Total | Sin acceso | Sin acceso |
| Portfolio | Total | Sin acceso | Sin acceso |
| Usuarios | Total | Sin acceso | Sin acceso |
| Accesos | Total | Sin acceso | Sin acceso |

La aprobación de pedidos, los presupuestos y los cobros son decisiones exclusivas del dueño y no se pueden delegar a otro rol.

## **Mensajes frecuentes y cómo resolverlos** {#mensajes-frecuentes}

| Mensaje del sistema | Qué significa | Qué hacer |
| :---- | :---- | :---- |
| "Usuario o contraseña incorrectos." | Alguno de los dos datos está mal. | Revisá mayúsculas y que no haya espacios. Si la olvidaste, pedile al dueño que la restablezca. |
| "Demasiados intentos fallidos. Probá de nuevo en X minutos." | Se probaron cinco contraseñas incorrectas seguidas. | Esperá los minutos indicados y volvé a intentar con la contraseña correcta. |
| "Tu sesión no es válida o venció. Ingresá de nuevo." | Pasaron ocho horas sin uso o veinticuatro desde el ingreso, o se cambió la contraseña desde otro lugar. | Volvé a ingresar. |
| "Tenés que cambiar tu contraseña antes de usar el sistema." | La cuenta es nueva o el dueño restableció la contraseña. | Elegí una contraseña propia en Mi cuenta. |
| "La obra está en presupuestación y solo se cargan gastos de obras en ejecución." | Todavía no se aprobó el presupuesto definitivo. | Aprobá el definitivo en Presupuestos. La obra pasa sola a ejecución. |
| "La obra todavía está en presupuestación: los pedidos se habilitan cuando se aprueba el presupuesto y la obra arranca." | No se compra material para una obra que todavía se está cotizando. | Esperá a que se apruebe el definitivo. |
| "Falta completar ..., que va antes." | Se intentó completar un hito con etapas anteriores pendientes. | Completá primero las anteriores o, si esta se adelantó de verdad, tildá la confirmación. |
| "Las ponderaciones suman ..." | En la carga por porcentaje, los hitos no suman 100%. | Corregí los porcentajes o usá "Cargar etapas", que los calcula solo. |
| "El pago (...) supera el saldo de la cuota (...)." | Se quiso registrar más de lo que se debe en esa cuota. | Registrá el saldo exacto y el excedente a cuenta de la cuota siguiente. |
| "El coeficiente del ... ya se aplicó a esta obra." | Ese mes ya se actualizó. | Para volver a actualizar, cargá el coeficiente del mes siguiente. |
| "... es indelegable: solo el rol Dueño puede tenerlo." | Se intentó darle a un capataz un permiso reservado al dueño. | Desmarcá esa casilla y guardá de nuevo. |
| "El rol Dueño no puede quedarse sin el permiso para ..." | Se intentó quitarle al dueño la administración de accesos o usuarios. | Volvé a marcar la casilla. |

## **Preguntas frecuentes** {#preguntas-frecuentes}

**¿Puedo borrar algo que cargué mal?**
En general no se borra, se corrige o se anula dejando el motivo. Un gasto se anula y se carga de nuevo. Un pago se anula. Un pedido se anula. Un presupuesto enviado se reemplaza por una versión nueva con "Usar como base". Así queda el historial de todo lo que pasó.

**¿Por qué no aparece un módulo en mi menú?**
Porque tu rol no tiene permiso para usarlo. Si necesitás acceso, hablalo con el dueño, que lo puede ajustar desde Accesos (salvo Presupuestos, Cobranzas y la aprobación de pedidos, que son exclusivos del dueño).

**¿Por qué no puedo editar un presupuesto?**
Porque ya no está en borrador. Una vez enviado al cliente, cualquier cambio se hace en una versión nueva con "Usar como base", para que quede registro de cada versión que vio el cliente.

**¿Tengo que cargar el gasto de los materiales que llegaron?**
No, si el pedido pasó por el sistema. Al confirmar la recepción, el gasto se genera solo en la obra con los precios acordados al aprobar.

**¿El sistema manda el WhatsApp solo?**
No. El sistema arma el mensaje y abre WhatsApp en tu teléfono con todo escrito, pero el mensaje lo mandás vos desde tu número.

**¿Qué pasa si el cliente paga una cuota en dos veces?**
Registrás cada pago por separado. La cuota queda como "Parcial" hasta que se completa, y en ese momento pasa sola a "Abonada".

**¿Cada cuánto actualizo por CAC?**
Una vez por mes, cuando se conoce la variación. Se carga el coeficiente del mes y se aplica obra por obra, revisando la vista previa antes de confirmar.

**¿Los clientes pueden ver algo del sistema?**
Solo la vidriera de obras realizadas, que no muestra nombres ni direcciones. Además reciben los documentos que el dueño les manda: el PDF del presupuesto y la planilla de pagos.

## **Glosario** {#glosario}

| Término | Significado |
| :---- | :---- |
| Anticipo | El primer pago de la obra. En el sistema es la cuota número cero. |
| Anteproyecto | Presupuesto general por rubro de una reforma, armado con los planos del arquitecto. Solo existe en reformas. |
| Avance físico | Porcentaje de la obra efectivamente construido, según los hitos completados. |
| Avance financiero | Porcentaje del presupuesto aprobado que ya se gastó. |
| Coeficiente CAC | Número por el que se multiplican las cuotas pendientes para actualizarlas. 1,04 equivale a un aumento del 4%. |
| Cotización inicial | Precio estimativo que se le pasa al cliente en el primer contacto, calculado por metro cuadrado. |
| Desfasaje | Diferencia entre el avance financiero y el físico. Si se gasta más rápido de lo que se avanza, el sistema lo avisa. |
| Gasto hormiga | Gasto chico e imprevisto que antes no quedaba registrado. Se identifica aparte pero suma igual al rubro. |
| Hito | Etapa de la obra cuyo cumplimiento suma un porcentaje al avance. |
| Ponderación | Peso de un hito en el avance total de la obra. La suma de todos da 100%. |
| Presupuesto adicional | Presupuesto complementario por un cambio menor sobre uno ya aprobado. |
| Presupuesto definitivo | Presupuesto detallado por rubro, subrubro e ítem. Al aprobarse, la obra pasa a ejecución. |
| Rubro / Subrubro | Clasificación de los trabajos y gastos (por ejemplo Albañilería / Demolición). |
| Semáforo | Indicador de color de cada rubro según cuánto de lo presupuestado se gastó. |
| Vidriera | Página pública del portfolio con las obras terminadas. |

## **Índice de capturas** {#índice-de-capturas}

> **Nota para quien arma el documento.** Esta sección no va en la versión final que se entrega. Sirve para sacar todas las capturas de una sola vez.

**Antes de empezar, prepará estos datos de ejemplo** (con datos ficticios, nunca con datos reales de clientes, porque el manual se entrega):

* Cuatro rubros con subrubros, uno marcado como rubro de mano de obra, y unos diez materiales repartidos entre ellos.
* Cuatro proveedores de zonas distintas, con celular cargado, cotizaciones y observaciones.
* Cinco clientes con distintos orígenes.
* Cuatro obras: una en presupuestación, dos en ejecución (una reforma y una construcción) y una finalizada. Una quinta cancelada.
* En la reforma en ejecución: cotización inicial, anteproyecto y definitivo aprobado, con plan de pago, gastos en varios rubros (alguno en amarillo y otro en rojo, y un gasto hormiga), hitos con la mitad completados, plan de cobro con el anticipo pagado, una cuota parcial y una vencida.
* Pedidos en todos los estados (pendiente, enviado, recibido completo, recibido con diferencias, anulado).
* Cinco operarios asignados, con faltas.
* Cuentas de prueba: una de Capataz General y una de Capataz de Obra vinculada a un operario asignado a la obra en ejecución.
* La obra finalizada publicada en el portfolio con cuatro fotos.

**Recomendaciones generales:** sacar las capturas de computadora con la ventana del navegador maximizada y sin barras de favoritos. Para las del celular, usar el teléfono o la vista de celular de las herramientas de desarrollo del navegador (tecla F12, ícono de celular). Recortar cada captura a la zona que se explica.

| N° | Pantalla | Usuario | Qué tiene que verse |
| :---- | :---- | :---- | :---- |
| 1 | Ingreso | Sin sesión | Formulario de usuario y contraseña. |
| 2 | Mi cuenta (primer ingreso) | Cuenta nueva | Aviso "Antes de empezar, elegí una contraseña propia". |
| 3 | Barra superior | Dueño | Menú de usuario desplegado. |
| 4 | Tablero | Dueño | Números, pendientes, gráfico y tabla de obras. |
| 5 | Tablero | Capataz General | Versión sin montos. |
| 6 | Obras, listado | Dueño | Obras en los cuatro estados. |
| 7 | Nueva obra | Dueño | Finalización tentativa calculada. |
| 8 | Nueva obra, cliente nuevo | Dueño | Alta rápida de cliente. |
| 9 | Cambiar estado | Dueño | Cancelación con motivo y confirmación. |
| 10 | Ficha de obra | Dueño | Números, rubros, hitos, documentos y accesos. |
| 11 | Balance de obra | Dueño | Tres tarjetas y tabla por rubro. |
| 12 | Clientes, listado | Dueño | Clientes con obras y orígenes. |
| 13 | Ficha de cliente | Dueño | Obras activas y finalizadas. |
| 14 | Catálogo de rubros | Dueño | Rubros, subrubros y rubro de mano de obra. |
| 15 | Presupuestos, listado | Dueño | Una fila por obra con instancias. |
| 16 | Instancias de una obra | Dueño | Versiones con estado y vigente. |
| 17 | Planilla de rubro | Dueño | Filas completas y vacías. |
| 18 | Planilla de mano de obra | Dueño | Rubros, subrubros y total de cada uno. |
| 19 | Detalle de presupuesto | Dueño | Ítems, subtotales y botones. |
| 20 | PDF del presupuesto | Dueño | Primera página con membrete. |
| 21 | Materiales | Dueño | Catálogo con un material inactivo. |
| 22 | Proveedores, listado | Dueño | Proveedores con conteos. |
| 23 | Ficha de proveedor | Dueño | Cotizaciones y observaciones. |
| 24 | Comparar precios | Dueño | Tres proveedores ordenados por precio. |
| 25 | Pedidos, listado | Dueño | Pedidos en todos los estados. |
| 26 | Aprobar pedido | Dueño | Precios sugeridos y total. |
| 27 | Enviar por WhatsApp | Dueño | Número, mensaje y enlace. |
| 28 | Orden PDF | Dueño | Orden de pedido. |
| 29 | Gastos, panel | Dueño | Semáforo con tres colores. |
| 30 | Nuevo gasto | Dueño | Gasto hormiga completo. |
| 31 | Personal, listado | Dueño | Operarios con faltas. |
| 32 | Ficha de operario | Dueño | Obras actuales y anteriores, faltas. |
| 33 | Cargar etapas | Dueño | Cinco etapas con su peso. |
| 34 | Avance | Dueño | Dos barras y aviso de desfasaje. |
| 35 | Cobranzas, plan | Dueño | Cuotas abonada, parcial, vencida y pendientes. |
| 36 | Registrar pago | Dueño | Monto parcial. |
| 37 | Actualizar por CAC | Dueño | Vista previa antes de aplicar. |
| 38 | Cobranzas, resumen | Dueño | Tabla de todas las obras. |
| 39 | Planilla de pagos PDF | Dueño | Documento para el cliente. |
| 40 | Portfolio | Dueño | Galería con portada. |
| 41 | Vidriera pública | Sin sesión | Obras publicadas de dos tipos. |
| 42 | Usuarios, listado | Dueño | Cuentas de los tres roles y una de baja. |
| 43 | Nueva cuenta | Dueño | Rol y operario vinculado. |
| 44 | Accesos, permisos | Dueño | Matriz de los tres roles. |
| 45 | Auditoría | Dueño | Ocho registros de distintos tipos. |
| 46 | Inicio celular | Capataz de Obra | Avance, botones y movimientos. |
| 47 | Pedir materiales | Capataz de Obra | Tres materiales en el pedido. |
| 48 | Recibir pedido | Capataz de Obra | Foto de remito y diferencia. |
| 49 | Avance celular | Capataz de Obra | Hitos de la obra. |
