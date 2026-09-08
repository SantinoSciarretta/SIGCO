# -*- coding: utf-8 -*-
"""Descripciones de negocio del modelo de datos de SIGCO.

Es lo unico del diccionario que NO sale de la base: el tipo de una columna vive
en el esquema, pero que significa no. Lo usan `generar-diccionario.py` y
`generar-dbml.py`, para que los dos digan lo mismo.

- GENERICAS: campos que se repiten igual en varias tablas (estado, fecha_alta...).
- D: descripciones especificas, por (tabla, columna). Ganan sobre GENERICAS.
- TABLA: que hace cada tabla, en una linea.
- MODULOS: a que modulo del sistema pertenece cada tabla.
"""
from collections import OrderedDict


# Campos que se repiten igual en muchas tablas. Se definen una sola vez.
GENERICAS = {
    'estado': 'Estado del registro.',
    'fecha_alta': 'Momento en que se dio de alta en el sistema.',
    'fecha_creacion': 'Momento en que se creó el registro.',
    'nombre_apellido': 'Nombre y apellido completo.',
    'telefono_contacto': 'Teléfono de contacto.',
    'email_contacto': 'Correo electrónico de contacto.',
    'descripcion': 'Texto libre descriptivo.',
    'orden': 'Posición dentro de la secuencia.',
    'motivo_anulacion': 'Motivo por el que se anuló. Obligatorio al anular.',
    'id_obra': 'Obra a la que pertenece.',
    'id_rubro': 'Rubro al que corresponde.',
    'id_subrubro': 'Subrubro al que corresponde.',
    'id_cliente': 'Cliente al que pertenece.',
    'id_proveedor': 'Proveedor asociado.',
    'id_material': 'Material del catálogo.',
    'id_operario': 'Operario al que corresponde.',
    'id_pedido': 'Pedido de materiales asociado.',
    'id_usuario_registro': 'Usuario que cargó el registro. *Pendiente hasta el módulo Accesos.*',
}

D = {
    # ---------------------------------------------------------------- CLIENTES
    ('cliente', 'id_cliente'): 'Identificador del cliente.',
    ('cliente', 'origen_recomendacion'): 'Por qué canal llegó. Todos los clientes llegan por referido.',
    ('cliente', 'recomendado_por'): 'Quién lo recomendó, en texto libre.',
    ('cliente', 'estado'): 'Activo / Inactivo. Un cliente no se elimina: se desactiva, para conservar sus obras.',

    # ------------------------------------------------------------------- OBRAS
    ('obra', 'id_obra'): 'Identificador de la obra. Es la entidad núcleo del sistema.',
    ('obra', 'direccion_obra'): 'Dirección donde se ejecuta el trabajo.',
    ('obra', 'tipo_inmueble'): 'Casa / Departamento / Local.',
    ('obra', 'tipo_obra'): 'Construcción o Reforma. Determina el circuito de presupuestación y queda bloqueado apenas existe un anteproyecto o definitivo.',
    ('obra', 'fecha_inicio_real'): 'Fecha real de arranque. No se carga hasta que el presupuesto definitivo está aprobado.',
    ('obra', 'fecha_fin_estimada'): 'Fecha estimada de finalización.',
    ('obra', 'notas'): 'Observaciones generales de la obra.',
    ('obra', 'estado'): 'En presupuestación → En ejecución → Finalizada / Cancelada. Cambia solo al aprobarse el definitivo y al completarse el último hito.',
    ('obra', 'motivo_cancelacion'): 'Por qué se canceló. Una obra no se elimina, solo se cancela.',

    # -------------------------------------------------------- PRESUPUESTACIÓN
    ('rubro', 'id_rubro'): 'Identificador del rubro.',
    ('rubro', 'nombre_rubro'): 'Nombre del rubro (Albañilería, Electricidad, Plomería…).',
    ('rubro', 'estado'): 'Un rubro ya usado en un presupuesto no se elimina: se desactiva.',
    ('subrubro', 'id_subrubro'): 'Identificador del subrubro.',
    ('subrubro', 'id_rubro'): 'Rubro al que pertenece. Un subrubro pertenece a un único rubro.',
    ('subrubro', 'nombre_subrubro'): 'Nombre del subrubro.',
    ('subrubro', 'estado'): 'Igual que en rubro: se desactiva, no se borra.',

    ('presupuesto', 'id_presupuesto'): 'Identificador del presupuesto.',
    ('presupuesto', 'tipo_presupuesto'): 'Cotización inicial / Anteproyecto / Definitivo / Adicional. Son las tres instancias reales del proceso, más los adicionales.',
    ('presupuesto', 'id_presupuesto_base'): 'Presupuesto del que deriva (autorreferencia). Así el definitivo toma el anteproyecto como base **sin sobreescribirlo**: quedan los dos.',
    ('presupuesto', 'version'): 'Número de versión dentro de la cadena de revisiones.',
    ('presupuesto', 'estado'): 'Borrador / Enviado / Aprobado / Rechazado. Solo el dueño aprueba.',
    ('presupuesto', 'metros_cuadrados'): 'Metros cuadrados. Solo se usa en la cotización inicial (m² × valor/m²).',
    ('presupuesto', 'valor_por_m2'): 'Valor del metro cuadrado usado en la cotización inicial.',
    ('presupuesto', 'total_presupuesto'): 'Total. En el definitivo sale de sumar los ítems; en la cotización inicial, de m² × valor/m².',
    ('presupuesto', 'anticipo_porcentaje'): 'Porcentaje que se cobra como anticipo. Es la cuota cero del plan de cobro.',
    ('presupuesto', 'cantidad_cuotas'): 'Cantidad de cuotas del plan, sin contar el anticipo.',
    ('presupuesto', 'plazo_estimado_obra'): 'Plazo estimado, en texto (por ejemplo "4 meses").',

    ('item_presupuesto', 'id_item'): 'Identificador del ítem.',
    ('item_presupuesto', 'id_presupuesto'): 'Presupuesto al que pertenece el ítem.',
    ('item_presupuesto', 'id_material'): 'Material del catálogo, si el ítem es un material. **Columna agregada en `V7`**, ausente en el Diccionario original: sin ella la descripción quedaba como texto libre y se reproducía el problema que Materiales viene a resolver. Es opcional porque no todo ítem es un material (mano de obra, dirección de obra).',
    ('item_presupuesto', 'descripcion'): 'Qué se cotiza. Es lo único de esta tabla que ve el cliente en el PDF.',
    ('item_presupuesto', 'unidad_medida'): 'Unidad en que se mide (m², bolsa, unidad, jornal…).',
    ('item_presupuesto', 'cantidad'): 'Cantidad cotizada.',
    ('item_presupuesto', 'valor_unitario'): 'Precio unitario. **Es información interna: no aparece en el PDF del cliente**, que muestra solo subtotales por rubro.',
    ('item_presupuesto', 'subtotal'): 'cantidad × valor_unitario. Se guarda calculado para que el total del presupuesto no dependa de recalcularlo en cada consulta.',

    # -------------------------------------------------------------- MATERIALES
    ('material', 'id_material'): 'Identificador del material.',
    ('material', 'nombre_material'): 'Nombre del material. Reemplaza los nombres escritos a mano, distintos en cada obra.',
    ('material', 'id_rubro'): 'Rubro al que pertenece el material.',
    ('material', 'unidad_medida'): 'Unidad de compra (bolsa, m², unidad…). Lista abierta a propósito.',
    ('material', 'estado'): 'Un material usado en presupuestos o pedidos no se elimina: se desactiva.',

    # ------------------------------------------------------------- PROVEEDORES
    ('proveedor', 'id_proveedor'): 'Identificador del proveedor.',
    ('proveedor', 'nombre_proveedor'): 'Nombre del corralón o proveedor.',
    ('proveedor', 'zona_cobertura'): 'Zona geográfica donde entrega. Es el criterio con el que hoy se elige a quién pedirle.',
    ('cotizacion', 'id_cotizacion'): 'Identificador de la cotización.',
    ('cotizacion', 'precio_cotizado'): 'Precio que cotizó ese proveedor para ese material.',
    ('cotizacion', 'fecha_cotizacion'): 'Cuándo se registró. Se guarda el historial completo: no se pisa la cotización anterior.',
    ('observacion_proveedor', 'id_observacion'): 'Identificador de la observación.',
    ('observacion_proveedor', 'id_pedido'): 'Pedido que originó la observación, si la hubo.',
    ('observacion_proveedor', 'descripcion'): 'Comportamiento observado: demoras, faltantes, diferencias de precio.',
    ('observacion_proveedor', 'fecha'): 'Cuándo se registró la observación.',

    # ----------------------------------------------------------------- COMPRAS
    ('pedido', 'id_pedido'): 'Identificador del pedido. Reemplaza el pedido por WhatsApp, que no dejaba registro.',
    ('pedido', 'id_proveedor'): 'Proveedor al que se le compra. **Es opcional al crear el pedido**: el dueño elige el proveedor recién al aprobarlo.',
    ('pedido', 'id_usuario_solicita'): 'Quién pidió (normalmente el capataz). *Pendiente hasta Accesos.*',
    ('pedido', 'id_usuario_recibe'): 'Quién confirmó la recepción en obra. *Pendiente hasta Accesos.*',
    ('pedido', 'estado'): 'Pendiente de Aprobación → Enviado al Proveedor → Recibido Completo / Recibido con Diferencias / Anulado.',
    ('pedido', 'foto_remito'): 'Referencia de la foto del remito en Supabase Storage. La base guarda la URL, nunca la imagen.',
    ('pedido', 'nota_diferencia'): 'Qué faltó o llegó distinto. Obligatoria si hubo diferencias, y es lo que determina el estado de recepción.',
    ('pedido', 'fecha_solicitud'): 'Cuándo se generó el pedido.',
    ('pedido', 'fecha_aprobacion'): 'Cuándo lo aprobó el dueño. La aprobación no es delegable.',
    ('pedido', 'fecha_recepcion'): 'Cuándo se confirmó la recepción en obra.',
    ('pedido_material', 'cantidad'): 'Cantidad pedida de ese material.',
    ('pedido_material', 'precio_unitario'): 'Precio unitario acordado. **Agregado en `V9`**: sin él no había de dónde sacar el monto del gasto que se genera al recibir el pedido.',

    # ------------------------------------------------------------------ GASTOS
    ('gasto', 'id_gasto'): 'Identificador del gasto.',
    ('gasto', 'tipo_gasto'): 'Material / Mano de Obra / **Gasto Hormiga** / Otro. El Gasto Hormiga son los gastos chicos que hoy se pierden: es el problema central que el módulo resuelve.',
    ('gasto', 'monto'): 'Importe del gasto.',
    ('gasto', 'fecha_gasto'): 'Cuándo se hizo el gasto.',
    ('gasto', 'fecha_carga'): 'Cuándo se cargó en el sistema. Puede ser posterior a `fecha_gasto`.',
    ('gasto', 'id_pedido'): 'Pedido que lo originó, si el gasto vino de una recepción de materiales.',
    ('gasto', 'id_operario'): 'Operario al que corresponde, si es mano de obra.',
    ('gasto', 'comprobante_adjunto'): 'Referencia del comprobante en Supabase Storage.',
    ('gasto', 'estado'): 'Confirmado / Anulado. Un gasto no se elimina: se anula con motivo, para no romper la comparación contra el presupuesto.',

    # ---------------------------------------------------------------- PERSONAL
    ('operario', 'id_operario'): 'Identificador del operario. Distinto de `usuario`: acá se registran todos, trabajen o no con el sistema.',
    ('operario', 'estado'): 'Al pasar a Inactivo se lo desvincula de las obras activas, pero se conserva su historial.',
    ('operario_obra', 'fecha_asignacion'): 'Desde cuándo trabaja en esa obra.',
    ('operario_obra', 'fecha_desasignacion'): 'Hasta cuándo. **Agregada en `V11`**: desasignar borrando la fila destruía el historial que el informe pide conservar.',
    ('inasistencia', 'id_inasistencia'): 'Identificador de la inasistencia.',
    ('inasistencia', 'fecha_falta'): 'Día que faltó.',
    ('inasistencia', 'motivo'): 'Motivo de la falta. **No es obligatorio**: muchas veces no se sabe.',

    # ------------------------------------------------------------- SEGUIMIENTO
    ('hito', 'id_hito'): 'Identificador del hito.',
    ('hito', 'nombre_hito'): 'Nombre del hito (Demolición, Instalación eléctrica, Terminaciones…).',
    ('hito', 'ponderacion'): 'Cuánto pesa este hito en el avance total. La suma de los hitos de una obra debe dar 100.',
    ('hito', 'orden'): 'Orden de ejecución dentro de la obra.',
    ('hito', 'estado'): 'Pendiente / Completado. El porcentaje de avance sale de los hitos completados por su ponderación.',
    ('hito', 'fecha_cumplimiento'): 'Cuándo se completó. Obligatoria al marcarlo como Completado.',
    ('hito', 'observacion'): 'Comentario del capataz al completar el hito.',
    ('hito', 'id_usuario_completa'): 'Quién lo marcó como completado. *Pendiente hasta Accesos.*',
    ('plantilla_hito', 'id_plantilla'): 'Identificador de la plantilla.',
    ('plantilla_hito', 'nombre_plantilla'): 'Nombre de la plantilla, reutilizable en obras similares.',
    ('plantilla_hito_detalle', 'id_detalle'): 'Identificador de la línea de la plantilla.',
    ('plantilla_hito_detalle', 'id_plantilla'): 'Plantilla a la que pertenece.',
    ('plantilla_hito_detalle', 'nombre_hito'): 'Nombre del hito que se va a crear al aplicar la plantilla.',
    ('plantilla_hito_detalle', 'ponderacion'): 'Ponderación con la que se crea el hito.',

    # ------------------------------------------------------------------ COBROS
    ('cuota', 'id_cuota'): 'Identificador de la cuota.',
    ('cuota', 'numero_cuota'): '**El anticipo es la cuota cero.** Vive en la misma tabla que el resto para que el saldo salga de una sola consulta.',
    ('cuota', 'monto_cuota'): 'Importe de la cuota. Se recalcula al aplicar el índice CAC, solo si está pendiente.',
    ('cuota', 'fecha_vencimiento'): 'Cuándo vence. Las cuotas son quincenales.',
    ('cuota', 'estado'): 'Pendiente / Abonada / Vencida. **Vencida se deriva del calendario**, nadie la marca a mano.',
    ('cuota', 'fecha_pago'): 'Cuándo se cobró. Obligatoria al marcarla Abonada.',
    ('cuota', 'medio_pago'): 'Transferencia / Efectivo / Cheque.',
    ('cuota', 'comprobante_emitido'): 'Qué se le entregó al cliente: Mensaje / Recibo / Planilla.',
    ('cuota', 'motivo_anulacion'): 'Motivo al anular un pago. Lo que se anula es **el pago**: la cuota vuelve a Pendiente y se sigue debiendo.',
    ('registro_cac', 'id_cac'): 'Identificador del registro del índice.',
    ('registro_cac', 'mes_correspondiente'): 'Mes al que corresponde el índice.',
    ('registro_cac', 'valor_indice'): 'Valor del índice CAC. Es un número absoluto: el ajuste sale de dividirlo por el del mes anterior. Se carga a mano.',
    ('registro_cac', 'fecha_carga'): 'Cuándo se cargó el valor.',

    # --------------------------------------------------------------- PORTFOLIO
    ('publicacion_portfolio', 'id_publicacion'): 'Identificador de la publicación.',
    ('publicacion_portfolio', 'id_obra'): 'Obra que se publica. Solo obras finalizadas, y una obra no puede tener dos publicaciones.',
    ('publicacion_portfolio', 'tipo_trabajo'): 'Cómo se agrupa en la vidriera (Construcción, Refacción, Decoración de local…). Lista abierta.',
    ('publicacion_portfolio', 'estado'): 'Publicada / Despublicada. La publicación nace despublicada: primero se cargan las fotos.',
    ('publicacion_portfolio', 'fecha_publicacion'): 'Cuándo se publicó.',
    ('imagen_portfolio', 'id_imagen'): 'Identificador de la imagen.',
    ('imagen_portfolio', 'id_publicacion'): 'Publicación a la que pertenece.',
    ('imagen_portfolio', 'url_imagen'): 'Referencia de la imagen en Supabase Storage.',
    ('imagen_portfolio', 'orden'): 'Orden en que se muestra en la galería.',

    # ------------------------------------------------------- USUARIOS Y ACCESOS
    ('usuario', 'id_usuario'): 'Identificador de la cuenta de acceso.',
    ('usuario', 'nombre_usuario'): 'Nombre con el que ingresa al sistema.',
    ('usuario', 'contrasena_hash'): 'Contraseña cifrada con BCrypt (hash + sal). **Nunca se guarda en texto plano.**',
    ('usuario', 'id_rol'): 'Rol que determina qué puede hacer.',
    ('usuario', 'id_operario'): 'Vínculo opcional con el registro de Personal, para que un capataz confirme recepciones desde el celular.',
    ('usuario', 'estado'): 'Activo / Inactivo. Una cuenta no se elimina: se desactiva, para conservar la auditoría.',
    ('usuario', 'ultima_fecha_acceso'): 'Último ingreso al sistema.',
    ('rol', 'id_rol'): 'Identificador del rol.',
    ('rol', 'nombre_rol'): 'Dueño / Capataz General / Capataz de Obra.',
    ('rol', 'descripcion'): 'Qué alcance tiene el rol.',
    ('permiso', 'id_permiso'): 'Identificador del permiso.',
    ('permiso', 'nombre_permiso'): 'Acción concreta que habilita.',
    ('permiso', 'modulo'): 'Módulo sobre el que aplica.',
    ('permiso', 'descripcion'): 'Qué habilita hacer.',
    ('registro_auditoria', 'id_auditoria'): 'Identificador del registro de auditoría.',
    ('registro_auditoria', 'id_usuario'): 'Quién hizo la acción.',
    ('registro_auditoria', 'accion_realizada'): 'Qué hizo.',
    ('registro_auditoria', 'modulo_afectado'): 'Sobre qué módulo.',
    ('registro_auditoria', 'fecha_hora'): 'Cuándo. Solo se auditan las acciones sensibles.',
}

# Que hace cada tabla, en una linea.
TABLA = {
    'cliente': 'Clientes de la empresa, con el origen de la recomendación por la que llegaron.',
    'obra': 'Entidad núcleo del sistema. Todo lo demás cuelga de una obra.',
    'rubro': 'Catálogo de rubros de trabajo. Es el eje sobre el que se comparan presupuesto y gastos.',
    'subrubro': 'Desagregación de un rubro. Pertenece a un único rubro.',
    'presupuesto': 'Cabecera del presupuesto, en cualquiera de sus tres instancias.',
    'item_presupuesto': 'Renglones del presupuesto definitivo, con su precio unitario interno.',
    'material': 'Catálogo único de materiales, del que eligen Presupuestación y Compras.',
    'proveedor': 'Corralones y proveedores, organizados por zona de cobertura.',
    'cotizacion': 'Historial de precios cotizados por proveedor y material.',
    'observacion_proveedor': 'Comportamiento observado de un proveedor: demoras, faltantes, diferencias.',
    'pedido': 'Pedido de materiales: el circuito que reemplaza al WhatsApp.',
    'pedido_material': 'Qué materiales y en qué cantidad tiene cada pedido.',
    'gasto': 'Cada gasto de la obra, clasificado por rubro y tipo, para comparar contra lo presupuestado.',
    'operario': 'Operarios de la empresa, trabajen o no con el sistema.',
    'operario_obra': 'Qué operarios están asignados a qué obras, y desde cuándo hasta cuándo.',
    'inasistencia': 'Faltas de un operario en una obra determinada.',
    'hito': 'Hitos ponderados de una obra. De acá sale el porcentaje de avance físico.',
    'plantilla_hito': 'Plantillas de hitos reutilizables para obras similares.',
    'plantilla_hito_detalle': 'Hitos que compone cada plantilla.',
    'cuota': 'Plan de cobro de la obra. El anticipo es la cuota cero.',
    'registro_cac': 'Índice CAC por mes, cargado a mano, para actualizar el saldo.',
    'publicacion_portfolio': 'Obra terminada publicada en la vidriera.',
    'imagen_portfolio': 'Fotos de una publicación.',
    'usuario': 'Cuentas de acceso al sistema.',
    'rol': 'Roles del sistema.',
    'permiso': 'Permisos individuales que se agrupan en roles.',
    'rol_permiso': 'Qué permisos tiene cada rol (relación muchos a muchos).',
    'registro_auditoria': 'Traza de las acciones sensibles: quién, qué, dónde y cuándo.',
}

MODULOS = OrderedDict([
    ('Clientes', ['cliente']),
    ('Obras', ['obra']),
    ('Presupuestación', ['rubro', 'subrubro', 'presupuesto', 'item_presupuesto']),
    ('Materiales', ['material']),
    ('Proveedores', ['proveedor', 'cotizacion', 'observacion_proveedor']),
    ('Compras', ['pedido', 'pedido_material']),
    ('Gastos', ['gasto']),
    ('Personal', ['operario', 'operario_obra', 'inasistencia']),
    ('Seguimiento de Obras', ['hito', 'plantilla_hito', 'plantilla_hito_detalle']),
    ('Cobros', ['cuota', 'registro_cac']),
    ('Portfolio Web', ['publicacion_portfolio', 'imagen_portfolio']),
    ('Usuarios y Accesos', ['usuario', 'rol', 'permiso', 'rol_permiso', 'registro_auditoria']),
])
