# Cambios para adecuar el informe al sistema construido

Cada cambio dice dónde está, qué texto buscar y qué texto pegar en su lugar. El texto a buscar está escrito como se ve en el documento (sin las barras invertidas que agrega el Markdown, así que `Tipo\_Obra` aparece como `Tipo_Obra`). Los textos nuevos están redactados sin guiones medios ni punto y coma.

Hay tres tipos de cambio:

- **[Reemplazar]** cambiar un texto por otro.
- **[Agregar]** sumar un texto nuevo en el lugar indicado.
- **[Borrar]** sacar el texto porque describe algo que el sistema no tiene.

Al final de cada cambio va el **por qué**, para que sepas qué decisión del código lo motiva.

---

# PROPUESTA TÉCNICA

## Módulo 1 – Obras

### 1.1 [Reemplazar] Funcionalidades Principales, tercer punto

**Buscá:**
> Registro de la fecha real de inicio y de la fecha estimada de finalización, esta última tomada del presupuesto definitivo una vez aprobado.

**Pegá:**
> Carga de la fecha estimada de inicio y del plazo de obra en meses, a partir de los cuales el sistema calcula la fecha estimada de finalización, y registro de la fecha real de inicio una vez aprobado el presupuesto definitivo.

**Por qué:** desde la migración V17 la fecha de fin no sale del presupuesto. Se carga el plazo en la obra y el sistema calcula la fecha, para que el plazo y la fecha nunca se contradigan.

### 1.2 [Reemplazar] Circuito Descriptivo, paso 3

**Buscá:**
> 3. Si hace falta, agrega una nota con alguna aclaración del proyecto. La obra queda en estado "En presupuestación".

**Pegá:**
> 3. Si ya lo sabe, carga la fecha en la que estima arrancar y cuántos meses calcula que va a durar el trabajo. Con esos dos datos el sistema calcula solo la fecha estimada de finalización. Si hace falta, agrega además una nota con alguna aclaración del proyecto. La obra queda en estado "En presupuestación".

**Por qué:** mismo motivo que el 1.1.

### 1.3 [Reemplazar] Circuito Descriptivo, paso 5

**Buscá:**
> pudiendo dejar una breve nota del motivo (precio, tiempos, cambio de planes del cliente, entre otros).

**Pegá:**
> dejando indicado el motivo de forma obligatoria (precio, tiempos, cambio de planes del cliente, entre otros).

**Por qué:** el sistema no deja cancelar una obra sin motivo. "Pudiendo" lo presenta como opcional.

### 1.4 [Reemplazar] Circuito Descriptivo, paso 6

**Buscá:**
> mientras que la fecha de finalización estimada queda tomada del plazo indicado en ese presupuesto.

**Pegá:**
> y a partir de esa fecha el sistema vuelve a calcular la fecha estimada de finalización con el plazo en meses cargado en la obra.

**Por qué:** al registrar el inicio real, el cálculo se rehace desde esa fecha y no desde la estimada.

### 1.5 [Reemplazar] Circuito Descriptivo, paso 8

**Buscá:**
> 8. Cuando se completa el último hito cargado en Seguimiento de Obras, el sistema cambia el estado de la obra a "Finalizada" de forma automática.

**Pegá:**
> 8. Cuando se completa el último hito cargado en Seguimiento de Obras, el sistema cambia el estado de la obra a "Finalizada" de forma automática. Si el dueño necesita dar la obra por terminada antes de completar todos los hitos, puede hacerlo a mano, pero el sistema le pide una confirmación explícita, ya que a partir de ese momento los hitos pendientes quedan bloqueados y no se puede seguir cargando avance.

**Por qué:** existe la confirmación `confirmaHitosPendientes`. El informe no contemplaba el caso.

### 1.6 [Agregar] Proceso Integral Donde Interviene, al final de la lista

**Pegá como último punto:**
> * **Dashboard:** el balance de cierre de la obra (ganancia estimada, resultado de caja y saldo por cobrar) se consulta desde la ficha de la obra, aunque se calcula en el módulo Dashboard, porque necesita cruzar la información de Presupuestación, Gastos y Cobros.

**Por qué:** el balance vive en `GET /api/balance/{idObra}`, dentro de Dashboard. Si estuviera en Obras se formaría un ciclo, porque Gastos y Cobros ya dependen de Obras.

### 1.7 [Reemplazar] Validaciones y Lógica, cuarto punto

**Buscá:**
> La Fecha_Inicio_Real no puede cargarse hasta que el presupuesto definitivo no esté aprobado.

**Pegá:**
> La Fecha_Inicio_Real no puede cargarse hasta que exista un presupuesto definitivo aprobado para la obra. El control se hace contra el presupuesto y no contra el estado de la obra, para que una obra pasada a ejecución de forma manual no pueda saltearse esta regla.

**Por qué:** así está implementado desde el 23/09. Antes una obra pasada a ejecución a mano admitía la fecha sin definitivo aprobado.

### 1.8 [Reemplazar] Validaciones y Lógica, sexto punto

**Buscá:**
> El estado pasa automáticamente a "En ejecución" al aprobarse el presupuesto definitivo, y a "Finalizada" cuando Seguimiento de Obras registra el último hito pendiente.

**Pegá:**
> El estado pasa automáticamente a "En ejecución" al aprobarse el presupuesto definitivo, y a "Finalizada" cuando Seguimiento de Obras registra el último hito pendiente. Si el dueño finaliza la obra a mano con hitos todavía sin completar, el sistema le exige una confirmación explícita antes de hacerlo.
>
> Cuando la obra tiene cargados la fecha estimada de inicio y el plazo en meses, la Fecha_Fin_Estimada no se escribe a mano sino que la calcula el sistema, para que el plazo y la fecha de finalización nunca queden contradiciéndose. Si después se registra la fecha real de inicio, el cálculo se rehace desde esa fecha.

**Por qué:** combina 1.1 y 1.5 en las reglas del módulo. El segundo párrafo va como un punto nuevo de la lista.

### 1.9 [Reemplazar] Vistas de Interfaz, punto 4

**Buscá:**
> 4. **Historial de Estados:** dentro de la ficha, un registro cronológico simple que muestra los cambios de estado de la obra a lo largo del tiempo, junto con la fecha en que ocurrió cada cambio.

**Pegá:**
> 4. **Balance de Cierre:** vista accesible desde la ficha que muestra por separado la ganancia estimada de la obra (lo presupuestado menos lo gastado), el resultado de caja (lo cobrado menos lo gastado) y el saldo que todavía resta cobrar.

**Por qué:** no existe una tabla ni una pantalla de historial de estados. Lo que sí quedó registrado es la cancelación, que pasa por la auditoría. La pantalla que sí existe en la ficha es la del balance.

### 1.10 [Reemplazar] Campos del Formulario de Obra, fila Fecha_Fin_Estimada

**Buscá la fila:**
> | Fecha_Fin_Estimada | Fecha | Tomada del plazo de obra indicado en el presupuesto definitivo. |

**Pegá estas tres filas en su lugar:**
> | Fecha_Inicio_Estimada | Fecha | Fecha en la que se estima que arranca la obra. Se carga al darla de alta, a diferencia de la fecha real. |
> | Meses_Estimados | Numérico | Duración estimada de la obra, expresada en meses porque así se habla en la obra. |
> | Fecha_Fin_Estimada | Calculada | Surge de sumar los Meses_Estimados a la fecha real de inicio, o a la estimada si la obra todavía no arrancó. |

**Por qué:** columnas agregadas en V17.

### 1.11 [Reemplazar] Campos del Formulario de Obra, fila Motivo_Cancelacion

**Buscá:**
> | Motivo_Cancelacion | Texto libre  | Se habilita solo si el estado pasa a Cancelada. |

**Pegá:**
> | Motivo_Cancelacion | Texto libre (condicional) | Obligatorio al pasar la obra a Cancelada. |

### 1.12 [Reemplazar y Agregar] Botones y Acciones Disponibles

**Buscá:**
> | Editar Obra | Permite modificar dirección, tipo de inmueble, fechas o notas de una obra ya creada. |

**Pegá:**
> | Editar Obra | Permite modificar dirección, tipo de inmueble, fechas, plazo o notas de una obra ya creada. El tipo de obra también puede corregirse mientras la obra no tenga un presupuesto de anteproyecto o definitivo. |

**Agregá como última fila:**
> | Ver Balance de Cierre | Abre el balance de la obra con la ganancia estimada, el resultado de caja y el saldo por cobrar. |

**Por qué:** hasta el 23/09 el tipo de obra no se podía cambiar nunca. Hoy se corrige mientras no haya presupuestos que dependan de él.

### 1.13 [Reemplazar] Desarrollo y Tecnología Propuesta, primer párrafo

**Buscá:**
> se resuelve mediante eventos internos que disparan los módulos correspondientes, sin que el dueño tenga que actualizarlo manualmente en cada caso.

**Pegá:**
> se resuelve desde el servicio del módulo que produce el hecho: Presupuestación pone la obra en ejecución al aprobar el definitivo, y Seguimiento de Obras la finaliza al completarse el último hito. Así el dueño no tiene que actualizar el estado a mano en cada caso.

**Por qué:** en el código no hay eventos internos (no hay ningún `ApplicationEvent` ni `@EventListener`). Son llamadas directas entre servicios. Si en la defensa te preguntan por los eventos, no los vas a poder mostrar.

---

## Módulo 2 – Personal

### 2.1 [Reemplazar] Circuito Descriptivo, paso 3

**Buscá:**
> el dueño o el capataz general registra la inasistencia

**Pegá:**
> el dueño registra la inasistencia

**Por qué:** la matriz de permisos del propio informe le da al Capataz General solo consulta sobre Personal, y así está implementado (`personal.editar` es solo del Dueño). El informe se contradecía a sí mismo y ganó la matriz.

### 2.2 [Borrar] Proceso Integral Donde Interviene, último punto

**Borrá:**
> * **Dashboard:** puede mostrar un resumen de inasistencias recientes o de operarios con mayor cantidad de faltas no justificadas.

**Por qué:** el Dashboard no muestra inasistencias.

### 2.3 [Reemplazar] Vistas de Interfaz, punto 4

**Buscá:**
> 4. **Resumen de Inasistencias por Obra:** vista accesible desde la ficha de una obra, que muestra las faltas registradas durante su ejecución, útil para cruzar con el avance físico en Seguimiento de Obras.

**Pegá:**
> 4. **Personal por Obra:** desde la ficha de una obra se accede al listado de operarios ya filtrado por esa obra, con la cantidad de inasistencias de cada uno, útil para cruzar con el avance físico en Seguimiento de Obras.

**Por qué:** es lo que hace el acceso "Personal" de la ficha de obra, con el filtro `?obra=` que se arregló en el último commit.

### 2.4 [Reemplazar] Campos del Formulario de Inasistencia, fila Registrado_Por

**Buscá:**
> | Registrado_Por | Automático | Usuario que cargó la inasistencia (dueño o capataz general). |

**Pegá:**
> | Registrado_Por | Automático | Usuario que cargó la inasistencia. |

**Por qué:** mismo motivo que el 2.1.

---

## Módulo 3 – Clientes

### 3.1 [Reemplazar] Validaciones y Lógica, segundo punto

**Buscá:**
> No se permite eliminar un cliente que tenga al menos una obra asociada, para no perder la trazabilidad de proyectos anteriores; en ese caso solo puede marcarse como inactivo.

**Pegá:**
> Un cliente no se elimina nunca, tenga o no obras asociadas. Si deja de ser relevante se lo marca como inactivo, conservando su historial de obras.

**Por qué:** el sistema no tiene ninguna operación de borrado de clientes. La frase original daba a entender que un cliente sin obras sí se podía eliminar.

---

## Módulo 4 – Presupuestación

Es el módulo que más cambió. Te recomiendo además **acortar** las Vistas de Interfaz (cambio 4.7), que hoy describen tres pantallas que en el sistema son una sola.

### 4.1 [Agregar] Funcionalidades Principales, después del tercer punto

Después de "Elaboración del presupuesto definitivo, con estructura de rubro, subrubro e ítem, tomando como base el presupuesto del anteproyecto cuando corresponde." **pegá estos dos puntos:**

> * Carga de los ítems de cada rubro mediante una planilla: al elegir un rubro, el sistema lista todos los materiales del catálogo que pertenecen a ese rubro con su unidad de medida, y el dueño completa la cantidad y el precio solo en las filas que necesita. Las filas que quedan vacías no se cargan.
> * Carga de la mano de obra desde un rubro especial, cuya planilla no lista materiales sino una fila por cada rubro y subrubro del catálogo, por ejemplo albañilería demolición y albañilería colocación. En cada fila se indica solo el total de mano de obra de ese trabajo, y las que quedan en cero no aparecen en el presupuesto.

**Por qué:** la planilla y el rubro de mano de obra (V18) son pedidos de Ricardo al probar el sistema. Es la forma real de cargar un presupuesto hoy.

### 4.2 [Reemplazar] Circuito Descriptivo, paso 6

**Buscá:**
> 6. El dueño completa cada ítem con su descripción, unidad de medida y cantidad. El sistema calcula los subtotales por rubro y el total general a medida que se va cargando la información.

**Pegá:**
> 6. El dueño elige un rubro y completa su planilla, indicando la cantidad y el precio de los materiales que lleva la obra. Para lo que no figura en el catálogo puede agregar un ítem suelto con su propia descripción y unidad de medida. La mano de obra se carga desde su rubro propio, por especialidad. El sistema calcula los subtotales por rubro y el total general a medida que se va cargando la información.

### 4.3 [Reemplazar] Validaciones y Lógica, punto sobre eliminar presupuestos

**Buscá:**
> No se permite eliminar un presupuesto, únicamente marcarlo como rechazado, para conservar la trazabilidad completa de la negociación con el cliente.

**Pegá:**
> Como criterio de uso, un presupuesto que no prospera se marca como rechazado y no se elimina, para conservar la trazabilidad de la negociación con el cliente. De todos modos, el sistema permite eliminar un presupuesto de forma definitiva, incluso uno aprobado, para poder corregir cargas de prueba o errores sin arrastrar registros. Esa eliminación pide confirmación y queda siempre asentada en el registro de auditoría. Si el presupuesto eliminado era el definitivo aprobado que había puesto la obra en ejecución, la obra vuelve a "En presupuestación". No se puede eliminar un presupuesto que haya servido de base para otro, porque se cortaría la cadena de versiones.

**Por qué:** el `DELETE` de presupuestos es un desvío pedido a propósito (CLAUDE.md dice que no se revierte). Desde el último commit además queda auditado.

### 4.4 [Agregar] Validaciones y Lógica, al final de la lista

**Pegá estos cuatro puntos:**
> * Guardar la planilla de un rubro reemplaza todos los ítems que el presupuesto tenía en ese rubro por las filas completadas. Se resuelve así para que, si el dueño vacía una fila, no quede un ítem oculto sumando al total.
> * Solo un rubro del catálogo puede estar marcado como rubro de mano de obra. Se lo identifica por esa marca y no por su nombre, de modo que renombrarlo no rompe su funcionamiento.
> * Cuando un ítem refiere a un material del catálogo, ese material tiene que pertenecer al mismo rubro del ítem y no puede estar inactivo.
> * Cuando una obra tiene varias instancias de presupuesto, el sistema decide cuál la representa: el definitivo aprobado si existe, y si no, la última instancia del circuito que no haya sido rechazada. Esa elección se hace en el servidor para que el listado de presupuestos y el Dashboard muestren siempre el mismo total para la misma obra.

**Por qué:** son las reglas de la planilla, de V18, de V7 (`id_material`) y del listado por obra del 23/09.

### 4.4b [Reemplazar] Validaciones y Lógica, segundo punto (cambio del 05/10/2026)

**Buscá:**
> No se puede generar el presupuesto definitivo de una reforma sin que exista antes un presupuesto de anteproyecto para esa misma obra.

**Pegá:**
> Las instancias del circuito no son obligatorias: el presupuesto definitivo puede generarse sin anteproyecto ni cotización inicial, y el anteproyecto sin cotización inicial. En la práctica hay obras chicas que se presupuestan directo y clientes que llegan con los planos definitivos ya hechos, y exigir el recorrido completo obligaba a cargar instancias que no existieron. El circuito completo sigue disponible para las obras que lo recorren.

**Buscá en Circuito Descriptivo, paso 4:**
> Si la obra es una construcción nueva, este paso se salta y se pasa directamente al siguiente.

**Pegá:**
> Si la obra es una construcción nueva, este paso se salta y se pasa directamente al siguiente. En una reforma también puede saltearse cuando no hace falta.

**Buscá en Campos del Formulario de Presupuesto, fila Plazo_Estimado_Obra, el texto del cambio 4.6 y agregale al final:**
> Al crear el presupuesto se completa solo con la duración estimada cargada en la obra.

**Por qué:** pedido del 05/10/2026. El definitivo ya no exige anteproyecto, y el plazo se toma de la obra para no cargarlo dos veces.

### 4.4c [Agregar] Validaciones y Lógica, al final de la lista (cambio del 05/10/2026)

**Pegá estos dos puntos:**
> * Un ítem con cantidad cero, o una fila de mano de obra con total cero, no se incluye en el presupuesto.
> * El total del presupuesto se calcula como la suma de todos los ítems multiplicada por 1,21, porque se le presenta al cliente con el IVA incluido. El anticipo y las cuotas del plan de pago salen de ese total. En cambio, el control de gastos compara lo gastado contra el subtotal sin IVA, porque el IVA no es un costo de la obra. Los presupuestos que ya estaban enviados o aprobados al incorporarse esta regla conservan su total, que es el que vio el cliente y la base de sus cuotas.

**Buscá en Circuito Descriptivo, paso 6 (el texto del cambio 4.2):**
> El sistema calcula los subtotales por rubro y el total general a medida que se va cargando la información.

**Pegá:**
> El sistema calcula los subtotales por rubro, el subtotal general, el IVA del 21% y el total con IVA a medida que se va cargando la información.

**Buscá en Desarrollo y Tecnología Propuesta, donde se describe el PDF, y agregá al final:**
> Al pie de los subtotales por rubro el documento muestra el subtotal, el IVA del 21% y el total con IVA.

**Por qué:** pedido del 05/10/2026 (migración V24). El total con IVA es lo que efectivamente paga el cliente. La mano de obra se presupuesta por trabajo cerrado y no por jornales.

### 4.4d [Agregar y Reemplazar] Imprevistos, honorarios y mano de obra sin IVA (cambio del 05/10/2026)

**En Funcionalidades Principales, pegá estos dos puntos:**
> * Carga de los imprevistos desde un rubro especial, cuya planilla lista los rubros de la obra con el total de cada uno, sumando materiales y mano de obra. En cada rubro se indica un porcentaje y el sistema calcula el monto, que se actualiza solo cuando cambia el rubro.
> * Carga de los honorarios como un porcentaje sobre el total de la obra, que el sistema calcula y actualiza solo.

**En el cambio 4.4c, reemplazá el segundo punto por este:**
> * El total del presupuesto se compone del subtotal de la obra, que suma materiales, mano de obra e imprevistos, más los honorarios, más el IVA del 21%. El IVA se aplica a todos los conceptos salvo a la mano de obra. El anticipo y las cuotas del plan de pago salen de ese total. En cambio, el control de gastos compara lo gastado contra el subtotal de la obra, porque ni el IVA ni los honorarios son un costo de la obra. Los presupuestos que ya estaban enviados o aprobados al incorporarse esta regla conservan su total, que es el que vio el cliente y la base de sus cuotas.

**En Vistas de Interfaz, agregá:**
> * Panel de subtotales por rubro, visible al lado de la planilla mientras se carga un rubro, que se actualiza a medida que se escribe para ver cómo queda el presupuesto completo antes de guardar.

**En el Diccionario de Datos agregá estas filas:**
> | rubro.es_imprevistos | BOOLEAN |  | Marca el único rubro cuya planilla carga los imprevistos por porcentaje. Obligatorio. |
> | item_presupuesto.id_rubro_referido | BIGINT | FK | Rubro al que se refiere un ítem de mano de obra o de imprevistos. Opcional. |
> | item_presupuesto.porcentaje | NUMERIC(5,2) |  | Porcentaje de un ítem de imprevistos, entre 0 y 100. Opcional. |
> | presupuesto.honorarios_porcentaje | NUMERIC(5,2) |  | Porcentaje de honorarios sobre el total de la obra, entre 0 y 100. Opcional. |

**Por qué:** pedido del 05/10/2026 (migración V26). Los imprevistos y los honorarios se cargaban a mano y había que recalcularlos con cada cambio. La mano de obra no se factura con IVA.

### 4.4e [Agregar] El catálogo de rubros y los rubros especiales (cambio del 05/10/2026)

**En Vistas de Interfaz, agregá:**
> * Catálogo de Rubros y Subrubros: sección propia del menú principal, desde la que se cargan, editan y desactivan los rubros y sus subrubros en cualquier momento.

**En Validaciones y Lógica, agregá:**
> * El sistema trae creados los rubros especiales "Mano de obra" e "Imprevistos", cada uno identificado por una marca y no por su nombre, de modo que renombrarlos no rompe su funcionamiento. Solo puede existir un rubro de cada tipo, y un mismo rubro no puede ser los dos a la vez.

**Por qué:** antes el catálogo solo se alcanzaba desde un enlace dentro de Presupuestos y en la práctica no se encontraba. Los dos rubros especiales los crean las migraciones V25 y V26 para que no haya que configurarlos a mano.

### 4.5 [Reemplazar] Campos del Formulario de Presupuesto, fila Unidad_Medida

**Buscá:**
> | Unidad_Medida | Lista desplegable | Metros cuadrados / metros lineales / unidades. |

**Pegá:**
> | Unidad_Medida | Lista desplegable | m² / ml / unidad / global / jornal. La mano de obra se guarda siempre como global, por su total. |

**Por qué:** son los cinco valores reales (`presupuestosApi.js`). "global" es el total cerrado, que es como se carga la mano de obra desde el 05/10.

### 4.6 [Agregar y Reemplazar] Campos del Formulario de Presupuesto

**Después de la fila Subrubro, agregá:**
> | Material | Relación (opcional) | Material del catálogo al que refiere el ítem. Tiene que pertenecer al rubro del ítem. No todo ítem es un material, por ejemplo la mano de obra. |

**Buscá la fila:**
> | Plazo_Estimado_Obra | Texto | Duración estimada de la obra, utilizada además para completar la Fecha_Fin_Estimada en el módulo Obras. |

**Pegá:**
> | Plazo_Estimado_Obra | Texto | Duración estimada de la obra tal como se le informa al cliente en el presupuesto. La fecha estimada de finalización de la obra no se toma de acá sino del plazo cargado en el módulo Obras. |

**En "Campos del Rubro", agregá como última fila:**
> | Es_Mano_De_Obra | Marca (sí / no) | Indica el único rubro cuya planilla carga el total de mano de obra de cada rubro y subrubro. Solo un rubro puede tenerla. |

### 4.7 [Reemplazar y acortar] Vistas de Interfaz, la lista completa

**Buscá desde** "1. **Listado de Presupuestos por Obra:**" **hasta el final del punto 7** ("con indicación clara de cuál fue aprobada.") **y pegá en su lugar:**

> 1. **Listado de Presupuestos por Obra:** una fila por obra, con las instancias que tiene (cotización inicial, anteproyecto, definitivo y adicionales), cuál de ellas está vigente y su total. Al abrir una obra se ven todas sus versiones con su estado y fecha, indicando cuál fue aprobada.
> 2. **Formulario de Cotización Inicial:** pantalla simple con los metros cuadrados y el valor por metro cuadrado, que muestra el precio estimativo calculado al instante.
> 3. **Detalle del Presupuesto:** pantalla de trabajo común al anteproyecto, al definitivo y a los adicionales. Muestra los rubros cargados con sus subtotales y el total general, y debajo despliega la planilla del rubro que se elige, dentro de la misma página, sin abrir una ventana aparte que tape el presupuesto que se está armando. Desde acá se define también el plan de pago y se cambia el estado.
> 4. **Gestión de Catálogo de Rubros y Subrubros:** pantalla accesible desde Presupuestación donde el dueño puede ver el listado completo de rubros con sus subrubros anidados, dar de alta nuevos o desactivar los que ya no usa.
> 5. **Vista Previa del PDF:** el documento se abre en el navegador tal como lo va a recibir el cliente, sin los precios unitarios internos, para revisarlo antes de enviarlo.

**Por qué:** el sistema no tiene un formulario distinto para el anteproyecto y otro para el definitivo, es la misma pantalla de detalle. El historial de versiones está dentro del listado por obra. Pasás de siete puntos a cinco y la sección queda fiel a lo construido.

### 4.8 [Reemplazar y Agregar] Botones y Acciones Disponibles

**Buscá:**
> | Agregar Rubro / Subrubro / Ítem | Permite ir construyendo la estructura del presupuesto definitivo. |

**Pegá estas dos filas:**
> | Cargar Planilla de Rubro | Despliega la planilla del rubro elegido con los materiales del catálogo, para completar cantidades y precios. |
> | Agregar Ítem | Suma un ítem suelto que no figura en el catálogo, con su descripción y unidad de medida. |

**Agregá antes de "Nuevo Rubro":**
> | Eliminar Presupuesto | Borra el presupuesto de forma definitiva después de pedir confirmación, dejando el registro en la auditoría. Se desaconseja como uso habitual. |

### 4.9 [Reemplazar] Desarrollo y Tecnología Propuesta

**Buscá:**
> La generación del PDF se realiza del lado del servidor con una librería como iText u OpenPDF, tomando los datos del presupuesto aprobado y aplicando el formato con el membrete de la empresa.

**Pegá:**
> La generación del PDF se realiza del lado del servidor con la librería OpenPDF, tomando los datos del presupuesto y aplicando el formato con el membrete de la empresa.

**Buscá:**
> El frontend se desarrolla en React, con un formulario dinámico que permite agregar rubros, subrubros e ítems sin recargar la página.

**Pegá:**
> El frontend se desarrolla en React, con una planilla por rubro que se despliega dentro de la misma página del presupuesto y se guarda sin recargarla.

**Por qué:** se eligió OpenPDF y no iText (iText tiene licencia AGPL para uso comercial). El "como" dejaba la decisión abierta.

---

## Módulo 5 – Gastos

### 5.1 [Reemplazar] Funcionalidades Principales, último punto

**Buscá:**
> Consulta de reportes de gasto por obra, por rubro y por período de tiempo.

**Pegá:**
> Consulta del listado de gastos filtrado por obra, rubro, tipo de gasto y rango de fechas, y descarga en PDF del estado financiero de cada obra.

### 5.2 [Reemplazar] Validaciones y Lógica, tercer punto

**Buscá:**
> El subrubro es obligatorio en todo gasto, ya que el presupuesto definitivo contra el que se compara siempre está desglosado a ese nivel de detalle.

**Pegá:**
> El subrubro es opcional. La comparación contra el presupuesto y el semáforo se calculan por rubro, así que exigir el subrubro agregaría un paso más en cada carga sin cambiar el resultado de la comparación.

**Por qué:** el informe se contradecía: acá decía obligatorio y la tabla de campos y el Diccionario decían opcional. El código lo dejó opcional.

### 5.3 [Reemplazar] Validaciones y Lógica, último punto

**Buscá:**
> Solo los usuarios con rol de dueño o capataz general pueden registrar gastos, dado que se trata de información financiera definida como no delegable a capataces de obra.

**Pegá:**
> Solo el rol de dueño puede registrar o anular gastos, dado que se trata de información financiera. El capataz general puede consultarlos, tal como establece la matriz de permisos, y el capataz de obra no tiene acceso al módulo. La anulación de un gasto queda asentada en el registro de auditoría.

**Por qué:** otra contradicción interna. La matriz da al Capataz General solo consulta en Gastos, y así está en `V13`.

### 5.4 [Reemplazar] Vistas de Interfaz, punto 4

**Buscá:**
> 4. **Reporte de Gastos Hormiga:** vista que agrupa únicamente los gastos marcados como imprevistos, mostrando su impacto acumulado por obra.

**Pegá:**
> 4. **Gastos Hormiga:** el listado de gastos puede filtrarse por ese tipo, y el panel de estado financiero de cada obra muestra aparte el total acumulado en gastos hormiga, para medir su impacto sin mezclarlo con los gastos planificados.

**Por qué:** no hay una pantalla propia de gastos hormiga. Se resuelve con el filtro por tipo y con el total aparte del panel.

### 5.5 [Reemplazar] Botones y Acciones Disponibles, última fila

**Buscá:**
> | Exportar Reporte | Genera un reporte de gastos en PDF o Excel, según los filtros aplicados. |

**Pegá:**
> | Descargar Reporte de la Obra | Genera en PDF el estado financiero de una obra, con lo presupuestado, lo gastado y el semáforo de cada rubro. Es un documento interno que no se le entrega al cliente. |

**Por qué:** no hay exportación a Excel ni reporte según filtros. El PDF es por obra completa.

---

## Módulo 6 – Compras

Es el módulo que más alcance nuevo ganó (orden en PDF y pedido de cotización por WhatsApp). Los textos de esta sección ya incluyen el cambio del 05/10/2026: el corralón se elige al cargar el pedido, se le pide cotización por WhatsApp, y al aprobar los precios quedan como cotización y la compra como gasto.

### 6.1 [Agregar] Funcionalidades Principales, después de "Envío del pedido aprobado al proveedor seleccionado..."

**Pegá estos tres puntos:**
> * Pedido de cotización al corralón por WhatsApp desde el propio teléfono del dueño. El sistema arma el mensaje con los materiales y sus cantidades, sin precios ni enlaces, pero el mensaje lo manda el dueño.
> * Sugerencia de precios al aprobar, tomados de la última cotización registrada del corralón para cada material. Los precios que se aprueban quedan guardados como nueva cotización de ese corralón.
> * Cálculo del subtotal, del IVA del 21% y del total de cada pedido, con los precios cargados sin IVA.
> * Generación de la orden de pedido en PDF, con los materiales, las cantidades, los precios acordados y la dirección de entrega, sin ningún dato interno de la empresa como el presupuesto, el gasto o el cliente.

**Por qué:** alcance nuevo del 24/09 (V20), modificado el 05/10/2026: el WhatsApp pasó a usarse para pedir la cotización.

### 6.2 [Reemplazar] Circuito Descriptivo, paso 1

**Buscá:**
> El pedido queda en estado Pendiente de Aprobación.

(es la última oración del paso 1)

**Pegá:**
> El pedido se carga indicando también el corralón al que se le va a pedir la cotización, y queda en estado Pendiente de Aprobación. Solo se pueden pedir materiales para una obra que ya está en ejecución.

### 6.3 [Reemplazar] Circuito Descriptivo, paso 2

**Buscá:**
> 2. El dueño revisa el pedido, lo aprueba y selecciona el proveedor al que se lo va a enviar, tomándolo del registro de Proveedores según la zona de la obra. El pedido pasa a estado Enviado al Proveedor.

**Pegá:**
> 2. El dueño le pide cotización al corralón por WhatsApp desde su propio teléfono, con un mensaje que el sistema arma con los materiales y sus cantidades. Cuando el corralón contesta, el dueño carga el precio de cada material sin IVA y aprueba el pedido, que pasa a estado Enviado al Proveedor. En ese momento los precios quedan guardados como cotización del corralón y la compra se registra como gasto de la obra, agrupada por rubro y sin IVA.

### 6.4 [Reemplazar] Circuito Descriptivo, paso 5

**Buscá el paso 5 completo y reemplazalo por:**
> 5. Al confirmar la recepción, el pedido queda como Recibido Completo o Recibido con Diferencias. El gasto de la compra ya se había generado al aprobar, un gasto por cada rubro de los materiales, con el monto que surge de las cantidades y de los precios aprobados, sin que el dueño tenga que cargarlo por separado.

**Por qué:** desde el 05/10/2026 el gasto se genera al aprobar, que es cuando se conocen los precios y se compromete la compra. Se agrupa por rubro (`generarGastoDeLaCompra`), así el semáforo de cada rubro recibe lo suyo.

### 6.5 [Reemplazar] Circuito Descriptivo, paso 6

**Buscá:**
> 6. Si el pedido queda marcado con diferencias, el dueño recibe una notificación y gestiona el reclamo directamente con el proveedor, por fuera del sistema, dejando registrado el resultado en la nota del pedido.

**Pegá:**
> 6. Si el pedido queda marcado con diferencias, ese estado lo deja a la vista del dueño, que gestiona el reclamo directamente con el proveedor por fuera del sistema y puede dejar asentado lo ocurrido como una observación en la ficha del proveedor, vinculada a ese pedido.

**Por qué:** el sistema no manda notificaciones (están fuera de alcance). La nota la escribe quien recibe, no el dueño después.

### 6.6 [Reemplazar] Validaciones y Lógica, cuarto punto

**Buscá:**
> La confirmación de recepción requiere obligatoriamente la carga de una foto del remito, salvo que el pedido corresponda a materiales de instalaciones gestionados directamente por el dueño, en cuyo caso este circuito no aplica.

**Pegá:**
> La confirmación de recepción requiere obligatoriamente la carga de una foto del remito, y el sistema no acepta una referencia vacía en su lugar. Los materiales de instalaciones, que gestiona el dueño de forma directa, no pasan por este circuito y por eso no generan pedido.

**Por qué:** el arreglo del último commit (`@NotBlank`). La excepción de instalaciones no se modela con una regla: esos materiales directamente no entran a Compras.

### 6.7 [Reemplazar] Validaciones y Lógica, último punto

**Buscá:**
> Un pedido no puede eliminarse una vez aprobado, solo puede quedar marcado como anulado si finalmente no se concreta, dejando un motivo.

**Pegá:**
> Un pedido no se elimina nunca. Si finalmente no se concreta se lo marca como anulado dejando un motivo, siempre que el material todavía no haya llegado a la obra. La aprobación y la anulación de un pedido quedan asentadas en el registro de auditoría.

### 6.8 [Agregar] Validaciones y Lógica, al final de la lista

**Pegá estos tres puntos:**
> * Solo se pueden generar pedidos para obras en estado "En ejecución". Mientras la obra se está presupuestando no se compra nada, porque el presupuesto todavía puede no aprobarse y ese pedido generaría un gasto contra una obra que quizás nunca arranca.
> * Pedir la cotización por WhatsApp exige el permiso de aprobar pedidos, que solo tiene el dueño, porque escribirle al proveedor es parte de una decisión no delegable. Solo se puede pedir mientras el pedido espera la aprobación.
> * Los precios del pedido se cargan sin IVA. El gasto que genera la compra va sin IVA, igual que el presupuesto contra el que se compara, y el total del pedido suma el 21%.
> * Si se anula un pedido ya aprobado, el gasto que había generado también se anula.
> * Si el teléfono del proveedor no se puede interpretar como un número de celular válido, el sistema no arma el enlace de WhatsApp en lugar de adivinar el número, para no mandarle el pedido de una obra a un desconocido.

### 6.9 [Reemplazar] Vistas de Interfaz, punto 5

**Buscá:**
> 5. **Historial de Compras por Proveedor:** vista accesible desde la ficha de un proveedor, con todos los pedidos que se le realizaron a lo largo del tiempo.

**Pegá:**
> 5. **Orden de Pedido:** documento PDF de la orden, que se abre en el navegador para revisarlo antes de mandárselo al corralón.

**Por qué:** la ficha de proveedor no muestra pedidos (ver 8.1). La orden en PDF sí existe y no figuraba.

### 6.10 [Agregar] Campos del Formulario de Pedido, al final de la tabla

**Pegá estas filas:**
> | Proveedor | Lista desplegable | Corralón al que se le pide la cotización. Se elige al cargar el pedido. |
> | Precio_Unitario | Numérico | Precio sin IVA de cada material, según la cotización del corralón, cargado por el dueño al aprobar. Es el valor con el que se genera el gasto y queda guardado como cotización. |
> | Motivo_Anulacion | Texto libre (condicional) | Obligatorio al anular el pedido. |

### 6.11 [Reemplazar y Agregar] Botones y Acciones Disponibles

**Buscá:**
> | Rechazar Pedido | Devuelve el pedido a quien lo generó, sin enviarlo al proveedor. |

**Pegá estas dos filas:**
> | Pedir Cotización | Abre WhatsApp en el teléfono del dueño con el pedido de cotización ya escrito. Habilitado solo para el dueño. |
> | Descargar Orden | Genera la orden de pedido en PDF. |

**Por qué:** no existe "rechazar". Un pedido que no se aprueba se anula con motivo.

### 6.12 [Reemplazar y Agregar] Desarrollo y Tecnología Propuesta

**Buscá:**
> La generación automática del gasto al confirmar la recepción se resuelve mediante un evento interno que dispara la creación del registro correspondiente en la entidad gasto, reutilizando los datos de obra, rubro y monto ya cargados en el pedido.

**Pegá:**
> La generación automática del gasto se resuelve dentro de la misma operación que aprueba el pedido, reutilizando los datos de obra, rubro y precio ya cargados. En esa misma operación los precios aprobados se guardan como cotización del corralón. Así, si algo falla, no queda un pedido aprobado sin su gasto ni un gasto sin su pedido.

**Agregá un párrafo nuevo antes de "El frontend se construye en React":**
> El pedido de cotización por WhatsApp no utiliza la API oficial de Meta, que queda fuera del alcance de esta versión. El sistema arma un enlace de tipo wa.me, la misma tecnología que un enlace de correo, que abre WhatsApp en el teléfono del dueño con el mensaje ya escrito, y el envío lo hace él. El mensaje lleva los materiales y sus cantidades, sin precios ni enlaces.

---

## Módulo 7 – Materiales

### 7.1 [Reemplazar] Objetivo del Módulo, última oración

**Buscá:**
> Al tener un catálogo centralizado, el sistema puede, por ejemplo, saber cuánto cemento consumió la empresa en total durante un mes, algo que hoy no es posible calcular de forma directa.

**Pegá:**
> Al tener un catálogo centralizado, un mismo material se llama igual en todos los presupuestos y pedidos, que es la condición necesaria para cualquier análisis conjunto posterior, como saber cuánto cemento consumió la empresa en un período.

**Por qué:** ese cálculo de consumo no existe como pantalla. El catálogo lo hace posible, pero no está construido.

### 7.2 [Borrar] Funcionalidades Principales, último punto

**Borrá:**
> * Consulta del historial de uso de un material, mostrando en qué obras y pedidos fue solicitado.

### 7.3 [Reemplazar y Agregar] Validaciones y Lógica

**Buscá:**
> No se puede eliminar un material que ya esté siendo utilizado en al menos un presupuesto o un pedido, en ese caso, solo puede desactivarse.

**Pegá:**
> Un material no se elimina nunca, se desactiva. Así no se pierde la referencia en los presupuestos y pedidos anteriores donde ya fue utilizado.

**Agregá como último punto:**
> * No se puede dar de alta ni activar un material en un rubro que esté inactivo.

### 7.4 [Borrar] Vistas de Interfaz, punto 3

**Borrá:**
> 3. **Historial de Uso por Material:** vista de detalle accesible desde la ficha de un material, mostrando en qué presupuestos y pedidos fue utilizado.

### 7.5 [Borrar] Botones y Acciones Disponibles, última fila

**Borrá:**
> | Ver Historial de Uso | Muestra en qué obras y pedidos fue utilizado un material en particular. |

**Por qué de 7.2, 7.4 y 7.5:** no hay endpoint ni pantalla de historial de uso. Si preferís construirlo en vez de sacarlo del informe, avisame.

---

## Módulo 8 – Proveedores

### 8.1 [Reemplazar] Funcionalidades Principales, segundo y tercer punto

**Buscá:**
> * Registro de cotizaciones recibidas de un proveedor, asociadas a un material o a un conjunto de materiales.
> * Consulta del historial completo de pedidos realizados a cada proveedor, tomado directamente del módulo Compras.

**Pegá:**
> * Registro de cotizaciones recibidas de un proveedor, asociada cada una a un material del catálogo.
> * Ficha de cada proveedor con su historial de cotizaciones y las observaciones cargadas, cada una vinculada al pedido que la originó.

**Por qué:** cada cotización es de un material. La ficha no muestra el historial de pedidos (el propio código tiene un comentario "PENDIENTE" en `FichaProveedor.jsx` que quedó sin resolver).

### 8.2 [Reemplazar] Circuito Descriptivo, paso 3

**Buscá:**
> pudiendo consultar antes las últimas cotizaciones registradas y el historial de pedidos anteriores para tomar la decisión con información concreta.

**Pegá:**
> pudiendo consultar antes las últimas cotizaciones registradas para tomar la decisión con información concreta. Al elegir el proveedor, el sistema le propone como precio de cada material el de la última cotización de ese proveedor.

### 8.3 [Reemplazar] Validaciones y Lógica, segundo punto

**Buscá:**
> No se puede eliminar un proveedor que ya tenga pedidos o cotizaciones asociadas; en ese caso, solo puede desactivarse.

**Pegá:**
> Un proveedor no se elimina nunca, se desactiva, para no perder su historial de cotizaciones y observaciones. Un proveedor inactivo no puede elegirse al aprobar un pedido.

### 8.4 [Reemplazar] Vistas de Interfaz, puntos 2 y 4

**Buscá:**
> con los datos de contacto, el historial de pedidos realizados, las cotizaciones registradas y las observaciones cargadas.

**Pegá:**
> con los datos de contacto, las cotizaciones registradas y las observaciones cargadas.

**Buscá:**
> pantalla simple para cargar el precio recibido de un proveedor para uno o varios materiales.

**Pegá:**
> pantalla simple para cargar el precio recibido de un proveedor para un material.

### 8.5 [Reemplazar] Botones y Acciones Disponibles

**Buscá:**
> | Registrar Cotización | Carga un precio recibido de un proveedor para uno o varios materiales. |

**Pegá:**
> | Registrar Cotización | Carga un precio recibido de un proveedor para un material. |

### 8.5b [Agregar] Campos del Formulario de Proveedor, después de la fila Zona_Cobertura

**Pegá:**
> | Direccion | Texto (opcional) | Calle, número y localidad del corralón, para ubicarlo o ir a retirar material. |

**Por qué:** alcance nuevo (migración V23). El criterio para elegir proveedor sigue siendo la zona de cobertura.

### 8.6 [Reemplazar] Desarrollo y Tecnología Propuesta

**Buscá:**
> persistiendo las entidades proveedor y cotizacion sobre PostgreSQL, esta última con claves foráneas hacia proveedor y material.

**Pegá:**
> persistiendo las entidades proveedor, cotizacion y observacion_proveedor sobre PostgreSQL, con claves foráneas hacia proveedor, material y pedido.

**Buscá:**
> El frontend se construye en React, con la ficha de proveedor consumiendo de forma directa los pedidos ya registrados en Compras a través de la relación entre ambas entidades, sin necesidad de duplicar esa información.

**Pegá:**
> El frontend se construye en React, con la ficha de proveedor reuniendo su historial de cotizaciones y sus observaciones, y con el comparador de cotizaciones accesible desde cada material.

---

## Módulo 9 – Seguimiento de Obras

### 9.1 [Agregar] Funcionalidades Principales, después del segundo punto

**Pegá estos dos puntos:**
> * Carga del plan de obra por etapas, indicando para cada una qué hay que hacer, a qué rubro corresponde, cuándo arranca y cuántos días lleva. El sistema reparte el cien por ciento del avance en proporción a la duración de cada etapa, de modo que el dueño no tenga que calcular las ponderaciones a mano.
> * Planificación de tareas en simultáneo. Cada etapa puede llevar su fecha de inicio, de modo que dos etapas se superpongan en el tiempo, y el sistema ordena las etapas según esa fecha. Una etapa sin fecha arranca cuando termina la anterior.
> * Reapertura de un hito marcado como completado por error, siempre que la obra no esté finalizada.

**Por qué:** la carga por duración es V19, pedido de Ricardo. La fecha de inicio es V27, del 06/10/2026, porque en obra hay tareas que se hacen en paralelo. La reapertura existe (`/api/hitos/{id}/reapertura`) y no figuraba.

### 9.2 [Reemplazar] Circuito Descriptivo, paso 2

**Buscá:**
> 2. A cada hito le asigna una ponderación, que representa qué porcentaje del avance total de la obra significa completar esa etapa. La suma de las ponderaciones de todos los hitos debe representar el cien por ciento de la obra.

**Pegá:**
> 2. A cada hito le asigna una ponderación, que representa qué porcentaje del avance total de la obra significa completar esa etapa, y la suma de todas debe dar el cien por ciento. Como alternativa, puede cargar las etapas indicando solo cuántos días lleva cada una, y el sistema calcula las ponderaciones en proporción a esa duración.

### 9.3 [Reemplazar] Circuito Descriptivo, paso 7

**Buscá:**
> contra la fecha de finalización prevista en el presupuesto.

**Pegá:**
> contra la fecha de finalización estimada de la obra.

### 9.4 [Reemplazar] Validaciones y Lógica, sexto punto

**Buscá:**
> La alerta de desfasaje entre avance físico y financiero se dispara cuando el avance financiero supera al avance físico en un margen configurable (por ejemplo, más de quince puntos porcentuales de diferencia).

**Pegá:**
> La alerta de desfasaje entre avance físico y financiero se dispara cuando el avance financiero supera al avance físico en más de quince puntos porcentuales.

**Por qué:** el margen es fijo en el código (`MARGEN_DESFASAJE = 15`), no se configura desde ninguna pantalla.

### 9.5 [Agregar] Validaciones y Lógica, al final de la lista

**Pegá estos dos puntos:**
> * En la carga por duración, el sistema reparte cien puntos en proporción a los días de cada etapa. El centavo que puede sobrar por redondeo se le suma a la etapa más larga, que es donde menos se nota. Las etapas no se guardan en una tabla aparte sino que son los mismos hitos, para que la obra tenga una sola fuente de avance y no dos que en algún momento se contradigan.
> * No se puede redefinir el plan de hitos de una obra que ya tiene hitos completados, ya que se perdería el avance registrado.
> * El orden de las etapas no lo escribe el usuario: el sistema lo calcula a partir de la fecha de inicio de cada una. Una etapa sin fecha arranca el día siguiente a que termina la anterior, y la primera arranca en la fecha de inicio de la obra. Las duraciones se cuentan en días corridos.

### 9.6 [Reemplazar] Vistas de Interfaz, puntos 3 y 4

**Buscá:**
> 3. **Comparativa de Avance Físico y Financiero:** vista que grafica, para una obra, la evolución del avance físico contra el porcentaje de presupuesto ya gastado, destacando visualmente cuando hay un desfasaje entre ambos.
> 4. **Listado General de Obras en Ejecución:** tabla con todas las obras activas, mostrando su porcentaje de avance, el estado de plazos respecto de la fecha estimada y si presentan alguna alerta de desfasaje.

**Pegá:**
> 3. **Comparativa de Avance Físico y Financiero:** dentro del panel de avance, dos barras muestran lado a lado el avance físico y el porcentaje del presupuesto ya gastado en ese momento, destacando visualmente cuando hay un desfasaje entre ambos.
> 4. **Obras en Ejecución:** el Dashboard muestra todas las obras activas con su porcentaje de avance, si están atrasadas respecto de la fecha estimada y si presentan alerta de desfasaje.

**Por qué:** no se guarda la evolución en el tiempo, solo la foto del momento. El listado general vive en el Dashboard.

### 9.7 [Agregar] Campos del Formulario de Hito, antes de Completado_Por

**Pegá:**
> | Rubro_Asociado | Relación (opcional) | Rubro al que corresponde la etapa, cuando se carga por duración. |
> | Duracion_Dias | Numérico (opcional) | Días que lleva la etapa. A partir de este dato se calcula la ponderación. |
> | Fecha_Inicio | Fecha (opcional) | Día en que arranca la etapa. Permite que dos etapas se superpongan, y de ella sale el orden. Si se deja vacía, la etapa arranca cuando termina la anterior. |

### 9.8 [Reemplazar y Agregar] Botones y Acciones Disponibles

**Buscá:**
> | Editar Hito | Modifica el nombre, la ponderación o el orden de un hito no completado. |

**Pegá:**
> | Editar Hitos | Redefine el plan de hitos de la obra, mientras ninguno esté completado. |
> | Cargar Etapas por Duración | Define el plan indicando los días de cada etapa, y el sistema calcula las ponderaciones. |
> | Reabrir Hito | Deshace el cumplimiento de un hito marcado por error. |

**Buscá y borrá:**
> | Ver Comparativa | Abre la vista gráfica de avance físico contra avance financiero. |

**Por qué:** la comparativa está dentro del panel de avance, no es una pantalla aparte.

### 9.9 [Reemplazar] Desarrollo y Tecnología Propuesta, último párrafo

**Buscá:**
> y con un gráfico que representa la comparación entre avance físico y financiero para que el desfasaje se identifique de un vistazo.

**Pegá:**
> y con dos barras que representan el avance físico y el financiero para que el desfasaje se identifique de un vistazo.

---

## Módulo 10 – Cobros

### 10.1 [Reemplazar] Funcionalidades Principales, primer y tercer punto

**Buscá:**
> * Generación automática del plan de cobro de una obra a partir del anticipo y la cantidad de cuotas definidos en el presupuesto definitivo aprobado.

**Pegá:**
> * Generación del plan de cobro de una obra a partir del anticipo, la cantidad de cuotas y el total del presupuesto definitivo aprobado. El primer vencimiento queda a quince días de la fecha en que se genera el plan.

**Buscá:**
> * Actualización mensual del saldo pendiente mediante el índice CAC, recalculando el valor de las cuotas restantes.

**Pegá:**
> * Actualización mensual del saldo pendiente mediante el coeficiente CAC que carga el dueño, con una vista previa en pesos antes de confirmar.
> * Registro de pagos parciales: una cuota puede cobrarse en varias partes, y su estado se deriva de lo efectivamente pagado.

**Por qué:** el plan no se genera solo al aprobar: lo genera el dueño con un botón, cuando el cliente ya está al tanto del primer vencimiento, que el sistema fija a quince días. Los pagos parciales son alcance nuevo (V16).

### 10.2 [Reemplazar] Circuito Descriptivo, pasos 1 a 5

**Buscá desde** "1. Cuando se aprueba el presupuesto definitivo de una obra, el sistema genera automáticamente su plan de cobro" **hasta el final del paso 5** ("marca esa cuota como abonada.") **y pegá:**

> 1. Cuando se aprueba el presupuesto definitivo de una obra, el dueño genera su plan de cobro con un botón. El primer vencimiento queda a quince días de ese momento. El porcentaje de anticipo, la cantidad de cuotas y el total los toma el sistema del presupuesto aprobado. Mientras el plan no se genere, la obra aparece en el Dashboard como un pendiente.
> 2. El dueño registra el pago del anticipo cuando lo recibe, indicando la fecha, el monto y el medio de pago.
> 3. Las cuotas quincenales por el saldo restante quedan establecidas desde que se genera el plan, cada una con su fecha de vencimiento.
> 4. Una vez por mes, el dueño carga el coeficiente de actualización CAC, es decir, por cuánto se multiplican las cuotas pendientes (por ejemplo, 1,04 para un aumento del cuatro por ciento). Antes de aplicarlo, el sistema le muestra en pesos cuánto pasa a deberse, y recién al confirmar recalcula las cuotas que todavía no se cobraron.
> 5. Cada vez que el cliente paga, el dueño registra el pago, ya sea por la cuota entera o por una parte. El sistema actualiza el saldo y deriva el estado de la cuota a partir de lo cobrado: pendiente si no entró nada, parcial si todavía queda saldo y abonada cuando se completó.

**Por qué:** V16 (pagos parciales) y V21 (el CAC se carga como coeficiente y no como nivel del índice).

### 10.3 [Reemplazar] Validaciones y Lógica, tercer, cuarto y sexto punto

**Buscá:**
> El índice CAC se aplica sobre el saldo pendiente y recalcula únicamente las cuotas que todavía no fueron abonadas, sin modificar las ya cobradas.

**Pegá:**
> El coeficiente CAC se aplica únicamente sobre el saldo impago. Las cuotas abonadas no se tocan, y en una cuota pagada en parte se actualiza solo lo que falta cobrar, sin encarecer lo que el cliente ya pagó.

**Buscá:**
> No se puede registrar un pago por un monto que supere el saldo pendiente de la obra.

**Pegá:**
> No se puede registrar un pago por un monto que supere el saldo pendiente de la cuota.

**Buscá:**
> Una cuota abonada no puede eliminarse, únicamente puede anularse dejando un motivo, para conservar la trazabilidad del estado de cuenta.

**Pegá:**
> Un pago nunca se elimina. Anular la cobranza de una cuota marca todos sus pagos como anulados dejando un motivo, y la cuota vuelve a deberse entera. Los pagos anulados dejan de sumar al saldo, pero se conservan con su monto, su fecha y su medio de pago para mantener la trazabilidad del estado de cuenta.

**Por qué:** el último es el arreglo del commit de ayer (antes se borraban de la base).

### 10.4 [Agregar] Validaciones y Lógica, antes del último punto

**Pegá estos cinco puntos:**
> * Un mismo coeficiente no puede aplicarse dos veces a la misma obra. Para volver a actualizar el saldo hace falta cargar el coeficiente de un mes nuevo.
> * La vista previa de la actualización calcula exactamente lo mismo que se aplica después, de modo que el dueño confirma sobre el número real.
> * El coeficiente tiene que ser mayor que cero y no puede superar diez, lo que evita el error de cargar el nivel publicado del índice en lugar del multiplicador.
> * Una cuota pagada en parte que pasó su fecha de vencimiento sigue figurando como vencida, ya que el resto se sigue debiendo.
> * El registro de un pago, su anulación y la aplicación del CAC quedan asentados en el registro de auditoría.

**Por qué:** los tres primeros salen de V21 y del commit de ayer.

### 10.5 [Reemplazar] Vistas de Interfaz, puntos 1, 2 y 3

**Buscá:**
> con su fecha de vencimiento, su estado (pendiente, abonada, vencida) y el saldo restante actualizado.

**Pegá:**
> con su fecha de vencimiento, su estado (pendiente, parcial, abonada o vencida), el detalle de los pagos recibidos y el saldo restante actualizado.

**Buscá:**
> 2. **Formulario de Registro de Pago:** pantalla simple para cargar un pago recibido, con fecha, monto y medio de pago.

**Pegá:**
> 2. **Formulario de Registro de Pago:** pantalla simple para cargar un pago recibido, por el total de la cuota o por una parte, con fecha, monto y medio de pago.

**Buscá:**
> pantalla donde el dueño ingresa el valor del índice del mes y el sistema muestra cómo quedan recalculadas las cuotas pendientes antes de confirmar.

**Pegá:**
> pantalla donde el dueño ingresa el coeficiente del mes y el sistema muestra en pesos cuánto pasa a deberse antes de confirmar.

### 10.6 [Reemplazar] Campos del Formulario de Cuota

**Buscá:**
> | Estado_Cuota | Lista desplegable | Pendiente / Abonada / Vencida. |
> | Fecha_Pago | Fecha (condicional) | Fecha en que se registró el pago, obligatoria al marcar la cuota como abonada. |

**Pegá:**
> | Estado_Cuota | Calculado | Pendiente / Parcial / Abonada / Vencida. No se elige a mano: se deriva de los pagos registrados y del calendario. |
> | Fecha_Pago | Fecha | Fecha del último pago recibido. El detalle de cada pago se guarda en el registro de pagos. |

### 10.7 [Agregar] Tabla nueva, después de "Campos del Formulario de Cuota"

**Pegá este subtítulo y esta tabla:**

> ### Campos del Registro de Pago
>
> | Campo | Tipo | Detalle |
> | ----- | ----- | ----- |
> | ID_Pago | Automático | Generado por el sistema de forma secuencial y única. |
> | Cuota_Asociada | Relación (obligatorio) | Cuota a cuenta de la cual se recibe el pago. |
> | Monto | Numérico (obligatorio) | Importe recibido. No puede superar el saldo de la cuota. |
> | Fecha_Pago | Fecha (obligatorio) | Fecha en que el cliente pagó, que no siempre coincide con la de carga. |
> | Medio_Pago | Lista desplegable | Transferencia / Efectivo / Cheque. |
> | Comprobante_Emitido | Lista desplegable | Mensaje / Recibo / Planilla actualizada. |
> | Registrado_Por | Automático | Usuario que cargó el pago. |
> | Anulado | Marca (sí / no) | Indica si el pago fue anulado. Un pago anulado se conserva pero deja de sumar. |
> | Motivo_Anulacion | Texto libre (condicional) | Obligatorio al anular. |

### 10.8 [Reemplazar] Campos del Registro de Índice CAC

**Buscá:**
> | Mes_Correspondiente | Fecha | Mes al que corresponde el valor del índice. |
> | Valor_Indice | Numérico | Porcentaje de variación del índice CAC del mes. |

**Pegá:**
> | Mes_Correspondiente | Fecha | Mes al que corresponde el coeficiente. |
> | Coeficiente | Numérico | Por cuánto se multiplican las cuotas pendientes en ese mes. Un 1,04 representa un aumento del cuatro por ciento y un 1 deja todo igual. Debe ser mayor que cero y no superior a diez. |

**Por qué:** V21. El nombre "Valor_Indice" fue justamente lo que produjo el error que multiplicaba las cuotas por dieciséis.

### 10.9 [Reemplazar y Agregar] Botones y Acciones Disponibles

**Buscá:**
> | Aplicar Índice CAC | Ingresa el valor del índice del mes y recalcula las cuotas pendientes. |

**Pegá:**
> | Generar Plan de Cobro | Arma el plan de la obra a partir del presupuesto aprobado, con el primer vencimiento a quince días. |
> | Cargar Coeficiente CAC | Registra el coeficiente de actualización del mes. |
> | Aplicar Actualización CAC | Muestra la vista previa en pesos y, al confirmar, recalcula las cuotas pendientes de la obra. |

**Buscá:**
> | Anular Pago | Marca un pago como anulado, solicitando un motivo. |

**Pegá:**
> | Anular Cobranza | Marca como anulados los pagos de una cuota, solicitando un motivo. Los pagos no se borran. |

### 10.10 [Reemplazar] Desarrollo y Tecnología Propuesta, primer párrafo

**Buscá todo el párrafo que empieza con:**
> El backend se desarrolla con Spring Boot y Spring Data JPA, persistiendo las entidades cuota y registro_cac sobre PostgreSQL

**Pegá:**
> El backend se desarrolla con Spring Boot y Spring Data JPA, persistiendo las entidades cuota, pago y registro_cac sobre PostgreSQL, con clave foránea de cuota hacia obra y de pago hacia cuota. El total cobrado de una cuota no se guarda en una columna sino que se suma de sus pagos, para que el detalle y el total nunca puedan desincronizarse. La actualización por CAC multiplica el saldo impago de cada cuota pendiente por el coeficiente del mes. La obra guarda cuál fue el último coeficiente que se le aplicó, lo que impide aplicarlo dos veces, y cada aplicación queda en el registro de auditoría. El estado vencido de una cuota no lo marca un proceso programado sino que se deriva del calendario cada vez que se consulta, de modo que nunca queda desactualizado.

**Por qué:** no hay ninguna tarea programada (`@Scheduled`) en el código. El original hablaba de "redistribuir" el saldo, y lo que se hace es multiplicar cuota por cuota.

---

## Módulo 11 – Portfolio Web

### 11.1 [Agregar] Funcionalidades Principales, al final de la lista

**Pegá:**
> * Ordenamiento de las imágenes dentro de la galería de cada obra.

### 11.2 [Agregar] Validaciones y Lógica, al final de la lista

**Pegá estos tres puntos:**
> * Cada obra puede tener una sola publicación en el portfolio.
> * No se puede publicar una obra sin al menos una imagen cargada, ni quitar la única imagen de una publicación activa.
> * A diferencia del resto del sistema, una imagen del portfolio sí puede borrarse, porque no es el registro de algo que ocurrió sino material de difusión.

### 11.3 [Reemplazar] Desarrollo y Tecnología Propuesta, primer párrafo

**Buscá:**
> persistiendo la entidad publicacion_portfolio sobre PostgreSQL, con clave foránea hacia obra.

**Pegá:**
> persistiendo las entidades publicacion_portfolio e imagen_portfolio sobre PostgreSQL, la primera con clave foránea hacia obra y la segunda hacia la publicación, ya que una obra publicada puede tener varias fotos.

---

## Módulo 12 – Dashboard

### 12.1 [Reemplazar] Funcionalidades Principales, la lista completa

**Buscá la lista desde** "Vista consolidada de todas las obras activas con su porcentaje de avance físico." **hasta** "para detectar obras afectadas por faltas reiteradas." **y pegá:**

> * Vista consolidada de todas las obras activas con su porcentaje de avance físico, su avance financiero y si están atrasadas respecto de la fecha estimada.
> * Totales de las obras en ejecución: lo presupuestado, lo gastado, la ganancia estimada y el saldo por cobrar.
> * Resumen de las obras que presentan algún rubro con desvío de presupuesto, según el semáforo del módulo Gastos.
> * Monto de las cuotas que vencen en los próximos siete días y alertas de cuotas vencidas, tomados del módulo Cobros.
> * Lista de pendientes que solo el dueño puede destrabar (presupuestos enviados sin respuesta, pedidos esperando aprobación o recepción, obras sin plan de cobro y cuotas vencidas), ordenada por urgencia y por tiempo de espera.
> * Balance de cierre de cada obra con tres valores separados: la ganancia estimada (presupuestado menos gastado), el resultado de caja (cobrado menos gastado) y el saldo por cobrar.

**Por qué:** no existe el balance por mes o trimestre ni el resumen de inasistencias. El balance que sí existe es por obra, con tres números.

### 12.2 [Reemplazar] Circuito Descriptivo, pasos 5 y 6

**Buscá:**
> 5. Si quiere una mirada más financiera, consulta el balance del mes o del trimestre, que le muestra cuánto entró por cobros y cuánto se fue en gastos en ese período.

**Pegá:**
> 5. Si quiere una mirada más financiera sobre una obra puntual, abre su balance, que separa la ganancia estimada, el resultado de caja y lo que todavía resta cobrar. Son tres números distintos y se muestran por separado, porque confundirlos puede hacer que una obra parezca rentable cuando todavía no lo es.

**Buscá:**
> 6. Desde cada resumen puede acceder directamente al módulo correspondiente para trabajar en detalle sobre lo que necesite (por ejemplo, aprobar un pedido o registrar un pago).

**Pegá:**
> 6. Desde cada pendiente puede acceder directamente al módulo correspondiente, con la obra ya seleccionada, para trabajar en detalle sobre lo que necesite (por ejemplo, aprobar un pedido o registrar un pago).

### 12.3 [Reemplazar y Borrar] Proceso Integral Donde Interviene

**Buscá:**
> provee el estado de los semáforos de desvío presupuestario por obra y por rubro, y los datos para el balance del período.

**Pegá:**
> provee el estado de los semáforos de desvío presupuestario por obra y por rubro, y el total gastado para el balance de cada obra.

**Borrá:**
> * **Personal:** resume las inasistencias recientes que podrían estar afectando el avance de alguna obra.

### 12.4 [Reemplazar] Validaciones y Lógica, tercer punto

**Buscá:**
> El balance por período se calcula cruzando los cobros registrados contra los gastos confirmados dentro del rango de fechas seleccionado, sin incluir gastos ni cobros anulados.

**Pegá:**
> El balance de cada obra se calcula sin incluir gastos ni pagos anulados, y exige permiso de consulta tanto sobre Gastos como sobre Cobros. Con uno solo de los dos, un rol que no debe ver información financiera podría deducirla por esta vía.

### 12.5 [Reemplazar] Vistas de Interfaz, punto 2

**Buscá:**
> 2. **Resumen Financiero por Período:** sección donde el dueño selecciona un mes o un trimestre y ve el balance entre lo cobrado y lo gastado, con el resultado del período.

**Pegá:**
> 2. **Balance de Obra:** vista accesible desde la ficha de cada obra que muestra por separado la ganancia estimada, el resultado de caja y el saldo por cobrar.

### 12.6 [Reemplazar y Borrar] Campos que Consolida

**Buscá:**
> | Balance del período | Gastos y Cobros | Cruce entre lo cobrado y lo gastado en el rango seleccionado. |

**Pegá:**
> | Balance de obra | Presupuestación, Gastos y Cobros | Ganancia estimada, resultado de caja y saldo por cobrar de cada obra. |
> | Totales de obras en ejecución | Presupuestación, Gastos y Cobros | Presupuestado, gastado, ganancia estimada y saldo por cobrar del conjunto. |

**Borrá:**
> | Inasistencias recientes | Personal | Faltas registradas en los últimos días por obra. |

### 12.7 [Reemplazar] Botones y Acciones Disponibles

**Buscá:**
> | Ir a Cobros de la Semana | Abre el módulo Cobros filtrado por las cuotas próximas a vencer. |

**Pegá:**
> | Ir al Pendiente | Abre la pantalla donde se resuelve cada pendiente, con la obra correspondiente ya seleccionada. |

**Buscá:**
> | Seleccionar Período | Cambia el rango de fechas del balance financiero (mes o trimestre). |

**Pegá:**
> | Filtrar por Obra | Reduce el panel a una sola obra. |
> | Ver Balance de Obra | Abre el balance de cierre de la obra seleccionada. |

**Buscá:**
> | Ver Presupuestos Enviados | Redirige a Presupuestación, a los presupuestos esperando respuesta. |

**Pegá:**
> | Ver Presupuesto Enviado | Abre directamente el presupuesto que espera respuesta del cliente. |

### 12.8 [Reemplazar] Desarrollo y Tecnología Propuesta, segundo párrafo

**Buscá todo el párrafo que empieza con:**
> El frontend se construye en React, organizando la información en tarjetas independientes que se cargan de forma progresiva

**Pegá:**
> El frontend se construye en React, organizando la información en secciones que llegan completas en una sola consulta al servidor, para que todos los números correspondan al mismo instante. Cuando quien consulta no es el dueño, el servidor envía vacíos los datos financieros, de modo que esa información ni siquiera viaja al navegador.

**Por qué:** es una sola llamada (`GET /api/tablero`), no tarjetas que cargan de a una. Y el filtrado lo hace el servidor (`reducirSiCorresponde`).

---

## Módulo 13 – Usuarios

Te recomiendo **reemplazar entera** la lista de Validaciones (cambio 13.3). Los puntos genéricos de hoy ya no aportan frente a la política real.

### 13.1 [Reemplazar] Funcionalidades Principales

**Buscá:**
> * Edición de los datos de una cuenta y restablecimiento de contraseña.
> * Activación o desactivación de una cuenta, sin necesidad de eliminarla, para conservar el registro de quién operó el sistema.
> * Listado de las cuentas existentes, con filtro por rol o por estado.

**Pegá:**
> * Cambio del rol y del vínculo con Personal de una cuenta, y restablecimiento de contraseña.
> * Baja de una cuenta con motivo obligatorio y posibilidad de reactivarla, sin necesidad de eliminarla, para conservar el registro de quién operó el sistema.
> * Listado de las cuentas existentes, con su rol, su estado y su última fecha de acceso.

**Por qué:** no hay filtros en el listado ni se edita el nombre de usuario.

### 13.2 [Reemplazar] Circuito Descriptivo, pasos 1 y 5

**Buscá:**
> cargando su nombre de usuario, una contraseña inicial y el rol que va a cumplir.

**Pegá:**
> cargando su nombre de usuario, una contraseña inicial y el rol que va a cumplir. En su primer ingreso, la persona está obligada a reemplazar esa contraseña por una propia.

**Buscá:**
> 5. Ante el olvido de una contraseña, el dueño puede restablecerla, generando una nueva que el usuario deberá cambiar en su siguiente ingreso.

**Pegá:**
> 5. Ante el olvido de una contraseña, el dueño puede restablecerla asignando una provisoria, que el usuario está obligado a cambiar en su siguiente ingreso. Al restablecerla se cierran las sesiones que esa cuenta tuviera abiertas.

### 13.3 [Reemplazar] Validaciones y Lógica, la lista completa

**Buscá desde** "No pueden existir dos cuentas con el mismo nombre de usuario." **hasta** "que es el único que puede crear, editar o desactivar usuarios." **y pegá:**

> * No pueden existir dos cuentas con el mismo nombre de usuario, sin distinguir mayúsculas de minúsculas.
> * La contraseña se guarda como hash, con una sal aleatoria distinta para cada una, nunca en texto plano.
> * La contraseña debe tener al menos diez caracteres y no puede ser previsible: se rechazan las que nombran a la empresa, las secuencias de teclado, las que repiten un mismo carácter y las que contienen el nombre de usuario. No se exigen mayúsculas, números ni símbolos, porque esa exigencia empuja a todos hacia contraseñas parecidas que terminan anotadas en un papel.
> * Toda cuenta nace obligada a cambiar su contraseña en el primer ingreso, y lo mismo pasa cuando el dueño se la restablece, ya que en los dos casos hay una contraseña que conocen dos personas. Hasta que la cambie, el sistema no le permite hacer ninguna otra operación.
> * Cambiar la propia contraseña exige escribir la actual. Cambiar o restablecer una contraseña cierra las sesiones abiertas de esa cuenta.
> * Una cuenta nunca se elimina. Se da de baja indicando un motivo, conservando su historial y su auditoría, y se puede reactivar más adelante.
> * No se puede dejar el sistema sin ninguna cuenta activa con rol de dueño, ni dando de baja una cuenta ni cambiándole el rol, y nadie puede darse de baja a sí mismo.
> * El vínculo con un registro de Personal es opcional, dado que no todos los usuarios del sistema son operarios (el dueño, por ejemplo, no forma parte del personal de obra).
> * La gestión de cuentas queda restringida al rol de dueño. El alta, la baja, la reactivación y los cambios de rol y de contraseña quedan asentados en el registro de auditoría.

### 13.4 [Agregar] Vistas de Interfaz, punto 4

**Pegá al final de la lista:**
> 4. **Cambio de Contraseña:** pantalla que el sistema muestra obligatoriamente en el primer ingreso o después de un restablecimiento, y a la que cualquier usuario puede volver para cambiar la suya.

### 13.5 [Reemplazar y Agregar] Campos del Formulario de Usuario

**Buscá:**
> | Contrasena | Texto (obligatorio) | Almacenada siempre encriptada. |

**Pegá:**
> | Contrasena | Texto (obligatorio) | Mínimo diez caracteres y no previsible. Se guarda como hash, nunca en texto plano. |

**Agregá después de Estado_Usuario:**
> | Motivo_Baja | Texto libre (condicional) | Obligatorio al dar de baja la cuenta. |

### 13.6 [Reemplazar y Borrar] Botones y Acciones Disponibles

**Buscá:**
> | Editar Usuario | Permite modificar el nombre, el rol o el vínculo con Personal de una cuenta. |
> | Restablecer Contraseña | Genera una nueva contraseña para el usuario. |
> | Desactivar Usuario | Deshabilita el acceso de una cuenta sin eliminar su historial. |
> | Buscar Usuario | Filtra el listado por rol o por estado. |

**Pegá:**
> | Cambiar Rol | Modifica el rol de una cuenta, siempre que no deje el sistema sin un dueño activo. |
> | Vincular Personal | Asocia la cuenta a un operario registrado en Personal. |
> | Restablecer Contraseña | Asigna una contraseña provisoria que el usuario debe cambiar al ingresar. |
> | Dar de Baja | Deshabilita el acceso de una cuenta, con motivo obligatorio, sin eliminar su historial. |
> | Reactivar Usuario | Vuelve a habilitar una cuenta dada de baja. |

### 13.7 [Reemplazar] Desarrollo y Tecnología Propuesta, primer párrafo

**Buscá:**
> El manejo de la autenticación se resuelve con Spring Security, que valida las credenciales al ingresar y mantiene la sesión del usuario durante su uso del sistema. Las contraseñas se almacenan encriptadas mediante un algoritmo de hash, de modo que ni siquiera desde la base de datos puedan leerse en texto plano.

**Pegá:**
> El manejo de la autenticación se resuelve con Spring Security mediante un token firmado (JWT) que el backend emite al ingresar y valida en cada petición. Las contraseñas se almacenan con BCrypt, un algoritmo de hash con sal aleatoria, de modo que ni siquiera desde la base de datos puedan leerse en texto plano. La entidad no expone el hash hacia afuera, así que no puede filtrarse por error en una respuesta ni en un registro.

---

## Módulo 14 – Accesos

### 14.1 [Reemplazar] Validaciones y Lógica, primer y tercer punto

**Buscá:**
> El rol de dueño tiene acceso total al sistema y sus permisos no pueden restringirse, ya que es quien concentra las decisiones finales de la empresa.

**Pegá:**
> El rol de dueño parte con acceso total al sistema. Nunca se le pueden quitar los permisos para administrar accesos y usuarios, ya que de lo contrario nadie podría volver a entrar a la pantalla que lo corrige.

**Buscá:**
> Las acciones definidas como no delegables (creación y aprobación de presupuestos, registro de cobros y pagos, aprobación de pedidos de materiales) quedan reservadas exclusivamente al rol de dueño y no pueden asignarse a otros roles.

**Pegá:**
> Las acciones definidas como no delegables (creación y aprobación de presupuestos, registro de cobros y pagos, aprobación de pedidos de materiales) quedan reservadas exclusivamente al rol de dueño, y el sistema rechaza asignarlas a otro rol aunque se marque la casilla desde la pantalla de permisos. Esto alcanza a la aprobación de pedidos y a todos los permisos de Presupuestación y de Cobros, tanto de consulta como de edición.

**Por qué:** la primera frase prometía algo que el código no hace (y CLAUDE.md lo dice así). La segunda ahora sí es cierta gracias al arreglo de ayer en `AccesosService`.

### 14.2 [Agregar] Validaciones y Lógica, al final de la lista

**Pegá estos dos puntos:**
> * Los permisos no viajan dentro del token de sesión sino que se leen de la base en cada petición, de modo que un cambio en los permisos de un rol se aplica en la petición siguiente.
> * El frontend arma el menú y las pantallas según los permisos del usuario, pero eso es solo presentación. La validación que manda es la del servidor.

### 14.3 [Reemplazar] Botones y Acciones Disponibles, última fila

**Buscá:**
> | Filtrar Auditoría | Busca en el log por usuario, módulo o rango de fechas. |

**Pegá:**
> | Filtrar Auditoría | Busca en el log por usuario o por módulo. Muestra las últimas doscientas acciones. |

**Por qué:** no hay filtro por fechas, y el tope de doscientas está en `AccesosService`.

### 14.4 [Reemplazar] Desarrollo y Tecnología Propuesta, segundo párrafo

**Buscá:**
> El log de auditoría se alimenta de forma automática mediante interceptores que registran las acciones sensibles al momento de ejecutarse, sin depender de que cada módulo lo haga por su cuenta, lo que garantiza que ninguna acción crítica quede sin registrar.

**Pegá:**
> El log de auditoría se alimenta desde cada servicio, en el punto exacto donde se ejecuta la acción sensible, mediante un componente común de auditoría. Se eligió así y no un mecanismo que intercepte todas las peticiones, porque cada registro lleva el detalle propio de la acción (el monto cobrado, el motivo de una anulación, el estado del presupuesto eliminado), que un mecanismo genérico no conoce. Las acciones registradas son el ingreso al sistema, el bloqueo de una cuenta, el alta, la baja, la reactivación y los cambios de rol y de contraseña de las cuentas, los cambios de permisos, la aprobación y la eliminación de presupuestos, la aprobación y la anulación de pedidos, la anulación de gastos, la cancelación de obras, y el registro y la anulación de cobros junto con la aplicación del CAC.

**Por qué:** no hay interceptores en el código. Si lo defendés como está escrito hoy, no lo vas a poder mostrar.

---

## Aspectos Técnicos Generales del Sistema

### G.1 [Reemplazar] Tecnologías Seleccionadas, punto OpenPDF

**Buscá:**
> **OpenPDF:** biblioteca para generar los documentos PDF de los presupuestos, sin restricciones de licencia para uso comercial.

**Pegá:**
> **OpenPDF:** biblioteca para generar los documentos PDF del sistema (el presupuesto para el cliente, la orden de pedido para el corralón, la planilla de pagos y el reporte interno de gastos), sin restricciones de licencia para uso comercial.

### G.2 [Reemplazar] Seguridad del Sistema, punto Autenticación

**Buscá:**
> el backend genera un token de autenticación firmado que identifica al usuario y su rol, y que el frontend adjunta a cada petición posterior. El backend valida ese token antes de ejecutar cualquier acción.

**Pegá:**
> el backend genera un token de autenticación firmado que identifica al usuario y su rol, y que el frontend adjunta a cada petición posterior. El backend valida ese token antes de ejecutar cualquier acción, y lee los permisos de la base en cada petición en lugar de confiar en lo que diga el token.

### G.3 [Reemplazar] Seguridad del Sistema, punto Log de auditoría

**Buscá:**
> quedan registradas de forma automática e inalterable

**Pegá:**
> quedan registradas en el momento de ejecutarse y de forma inalterable

### G.4 [Reemplazar] Seguridad del Sistema, punto Expiración de sesión

**Buscá todo el punto que empieza con:**
> **Expiración de sesión:** el token de autenticación tiene una vigencia limitada.

**Pegá estos dos puntos:**
> * **Expiración de sesión:** el token de autenticación vence a las ocho horas, el equivalente a una jornada de trabajo. Mientras el usuario sigue operando, el sistema lo renueva de forma automática en las últimas dos horas, para que nadie se quede afuera a mitad de la tarde. La renovación tiene un tope absoluto de veinticuatro horas desde el ingreso, a partir del cual hay que volver a iniciar sesión, de modo que un token robado no pueda mantenerse vivo indefinidamente. Al vencer, el sistema redirige al login sin importar en qué pantalla se encuentre el usuario.
> * **Bloqueo por intentos fallidos:** cinco intentos fallidos seguidos bloquean la cuenta durante quince minutos. El bloqueo se verifica antes de comparar la contraseña, y el mensaje de error y el tiempo de respuesta son los mismos para un usuario inexistente que para una contraseña incorrecta, de modo que no pueda averiguarse qué cuentas existen.

### G.5 [Reemplazar] Infraestructura y Almacenamiento, último párrafo

**Buscá todo el párrafo que empieza con:**
> El flujo de carga de imágenes está pensado para no sobrecargar el backend

**Pegá:**
> Las imágenes se suben a través del backend, que controla el tipo y el tamaño de cada archivo antes de guardarlo en Supabase Storage, y en la base de datos solo se guarda la referencia a esa imagen. El almacenamiento se divide en dos espacios: uno público para las fotos del portfolio y otro privado para los remitos y comprobantes, que son documentación interna de la obra. Los archivos se sirven también a través del backend, de modo que quién puede ver cada uno lo decide el sistema y no la configuración del almacenamiento, y si algún día se cambia de proveedor, las referencias guardadas siguen sirviendo.

**Por qué:** el original decía que el archivo iba directo del celular a Supabase sin pasar por el servidor. En el código pasa por `POST /api/archivos` y se valida en `ValidadorDeArchivos`.

### G.6 [Reemplazar] Limitaciones Asumidas, primer punto

**Buscá:**
> No se contempla la integración automática con servicios externos como la API de WhatsApp, el índice CAC del INDEC o servicios de correo; acciones como el ingreso del índice CAC se realizan de forma manual en esta versión.

**Pegá:**
> No se contempla la integración automática con servicios externos como la API de WhatsApp, la publicación del índice de la Cámara Argentina de la Construcción o servicios de correo. Acciones como el ingreso del coeficiente CAC se realizan de forma manual en esta versión. El envío de la orden de pedido al corralón por WhatsApp no es una integración de este tipo: el sistema solo arma un enlace que abre WhatsApp en el teléfono del dueño con el mensaje escrito, y el envío lo hace él.

**Por qué:** el índice CAC lo publica la Cámara Argentina de la Construcción, no el INDEC. Y la aclaración de WhatsApp evita que en la defensa parezca que se rompió el alcance.

---

# DICCIONARIO DE DATOS

### D.1 [Reemplazar] Tabla permiso, fila nombre_permiso

**Buscá:**
> | nombre_permiso | VARCHAR(100) |  | Nombre de la acción habilitada (ej. "aprobar_presupuesto"). Obligatorio y único. |

**Pegá:**
> | nombre_permiso | VARCHAR(100) |  | Nombre de la acción habilitada, con el formato módulo.acción (ej. "compras.aprobar", "gastos.ver"). Obligatorio y único. |

### D.2 [Agregar] Tabla usuario, al final

**Pegá estas filas:**
> | motivo_baja | VARCHAR(200) |  | Motivo por el que se dio de baja la cuenta, obligatorio si el estado es Inactivo. Una cuenta no se elimina. |
> | intentos_fallidos | INTEGER |  | Cantidad de intentos de ingreso fallidos seguidos. Se guarda en la base y no en memoria para que reiniciar el servidor no sea una forma de saltear el bloqueo. Obligatorio. |
> | bloqueado_hasta | TIMESTAMP |  | Momento hasta el que la cuenta queda bloqueada después de cinco intentos fallidos. |
> | debe_cambiar_contrasena | BOOLEAN |  | Indica que la cuenta tiene que cambiar su contraseña antes de poder operar. Vale verdadero al crear la cuenta y al restablecer la contraseña. Obligatorio. |
> | version_sesion | INTEGER |  | Número que se incrementa al cambiar o restablecer la contraseña. Un token emitido con una versión anterior deja de valer, lo que permite cortar sesiones abiertas sin dar de baja la cuenta. Obligatorio. |

**Por qué:** columnas de V13, V14 y V15.

### D.3 [Reemplazar y Agregar] Tabla obra

**Buscá:**
> | fecha_fin_estimada | DATE |  | Fecha estimada de finalización, tomada del presupuesto definitivo. |

**Pegá:**
> | fecha_inicio_estimada | DATE |  | Fecha en la que se estima que arranca la obra. |
> | meses_estimados | INTEGER |  | Duración estimada de la obra en meses. |
> | fecha_fin_estimada | DATE |  | Fecha estimada de finalización, calculada sumando meses_estimados a la fecha real de inicio, o a la estimada si la obra todavía no arrancó. |

**Agregá al final de la tabla:**
> | id_ultimo_cac_aplicado | BIGINT | FK | Último registro de CAC aplicado a las cuotas de la obra. Referencia a registro_cac. Impide aplicar el mismo coeficiente dos veces. |

**Por qué:** V17 y V22.

### D.4 [Agregar] Tabla rubro, al final

**Pegá:**
> | es_mano_de_obra | BOOLEAN |  | Marca el único rubro cuya planilla carga el total de mano de obra de cada rubro y subrubro. Un índice único impide que haya dos. Obligatorio. |

### D.5 [Reemplazar] Tabla presupuesto, fila plazo_estimado_obra

**Buscá:**
> | plazo_estimado_obra | VARCHAR(100) |  | Duración estimada de la obra. |

**Pegá:**
> | plazo_estimado_obra | VARCHAR(100) |  | Duración estimada de la obra tal como se informa al cliente en el presupuesto. |

### D.6 [Reemplazar] Tabla item_presupuesto, fila unidad_medida

**Buscá:**
> | unidad_medida | VARCHAR(20) |  | Unidad (metros cuadrados / metros lineales / unidades). Obligatorio. |

**Pegá:**
> | unidad_medida | VARCHAR(20) |  | Unidad (m² / ml / unidad / global / jornal). La mano de obra se guarda como global. Obligatorio. |

### D.6b [Agregar] Tabla proveedor, después de la fila zona_cobertura

**Pegá:**
> | direccion | VARCHAR(200) |  | Dirección del corralón (calle, número y localidad). Opcional. |

**Por qué:** V23.

### D.7 [Agregar] Tabla pedido, al final

**Pegá:**
> | token_orden | VARCHAR(64) |  | Código aleatorio que identifica el enlace público de la orden en PDF. Único entre los pedidos que lo tienen. Se vacía para cortar el enlace. |
> | token_orden_vence | TIMESTAMP |  | Momento en que vence el enlace público de la orden, a los treinta días de generado. |

**Por qué:** V20.

### D.8 [Agregar] Tabla hito, al final

**Pegá:**
> | id_rubro | BIGINT | FK | Rubro al que corresponde la etapa, cuando se carga por duración. Referencia a rubro. |
> | duracion_dias | INTEGER |  | Días que lleva la etapa. A partir de este dato se calcula la ponderación. |
> | fecha_inicio | DATE |  | Día en que arranca la etapa. De ella sale el orden de las etapas, y permite que dos se superpongan. Opcional. |

**Por qué:** V19.

### D.9 [Reemplazar] Tabla cuota, filas estado, fecha_pago y motivo_anulacion

**Buscá:**
> | estado | VARCHAR(12) |  | Estado (Pendiente / Abonada / Vencida). Obligatorio. |
> | fecha_pago | DATE |  | Fecha en que se registró el pago, obligatoria al marcar como abonada. |

**Pegá:**
> | estado | VARCHAR(12) |  | Estado (Pendiente / Parcial / Abonada / Vencida). Se deriva de los pagos registrados y del calendario. Obligatorio. |
> | fecha_pago | DATE |  | Fecha del último pago recibido. El detalle de cada pago está en la tabla pago. |

**Buscá:**
> | motivo_anulacion | VARCHAR(200) |  | Motivo por el que se anuló el pago, obligatorio al anular. La cuota vuelve a Pendiente y se sigue debiendo: lo que se anula es el pago, no la cuota. |

**Pegá:**
> | motivo_anulacion | VARCHAR(200) |  | Motivo de la última anulación de la cobranza de la cuota. La cuota vuelve a deberse entera: lo que se anula son los pagos, no la cuota. |

### D.10 [Agregar] Tabla nueva pago, después de la tabla cuota

**Pegá:**

> Tabla: pago
>
> Registra cada pago recibido a cuenta de una cuota, lo que permite cobrar una cuota en varias partes.
>
> | Campo | Tipo | Clave | Descripción |
> | ----- | ----- | ----- | ----- |
> | id_pago | BIGSERIAL | PK | Identificador único del pago. |
> | id_cuota | BIGINT | FK | Cuota a cuenta de la cual se recibe el pago. Referencia a cuota. Obligatorio. |
> | monto | NUMERIC(14,2) |  | Importe recibido. Mayor que cero y no superior al saldo de la cuota. Obligatorio. |
> | fecha_pago | DATE |  | Fecha en que el cliente pagó. Obligatorio. |
> | medio_pago | VARCHAR(15) |  | Medio de pago (Transferencia / Efectivo / Cheque). Obligatorio. |
> | comprobante_emitido | VARCHAR(20) |  | Tipo de comprobante entregado (Mensaje / Recibo / Planilla actualizada). |
> | id_usuario_registro | BIGINT | FK | Usuario que cargó el pago. Referencia a usuario. |
> | fecha_carga | TIMESTAMP |  | Fecha en que se registró el pago en el sistema. Obligatorio. |
> | anulado | BOOLEAN |  | Indica si el pago fue anulado. Un pago anulado no se borra, pero deja de sumar al saldo. Obligatorio. |
> | motivo_anulacion | VARCHAR(200) |  | Motivo por el que se anuló el pago. |

**Por qué:** V16 y V22.

### D.11 [Reemplazar] Tabla registro_cac

**Buscá:**
> | mes_correspondiente | DATE |  | Mes al que corresponde el valor del índice. Obligatorio. |
> | valor_indice | NUMERIC(8,4) |  | Porcentaje de variación del índice CAC del mes. Obligatorio. |

**Pegá:**
> | mes_correspondiente | DATE |  | Mes al que corresponde el coeficiente, normalizado al día uno. Obligatorio. |
> | coeficiente | NUMERIC(8,4) |  | Por cuánto se multiplican las cuotas pendientes en ese mes (1,04 = aumento del cuatro por ciento). Mayor que cero y no superior a diez. Obligatorio. |

**Por qué:** V21.

---

# Resumen de lo que conviene acortar

- **Presupuestación, Vistas de Interfaz:** de siete puntos a cinco (cambio 4.7). Tres pantallas que el informe describe por separado son una sola en el sistema.
- **Usuarios, Validaciones:** reemplazo completo (cambio 13.3). Los puntos genéricos sobre "contraseña encriptada" y "rol obligatorio" quedan absorbidos en reglas más precisas.
- **Dashboard, Funcionalidades:** se reescribe la lista (cambio 12.1) sacando dos funciones que no existen.

# Cosas que se sacaron del informe y que podrías construir en lugar de borrarlas

Si para la defensa preferís que el sistema haga lo que el informe prometía, en vez de sacarlo del texto, estas son las cinco funciones que hoy no existen:

1. Historial de estados de la obra (1.9).
2. Historial de uso por material (7.2, 7.4, 7.5).
3. Historial de pedidos en la ficha del proveedor (8.1, 8.4). Es el más barato: el backend ya filtra pedidos por proveedor.
4. Balance general por mes o trimestre en el Dashboard (12.1, 12.5).
5. Exportación de gastos a Excel según filtros (5.5).
