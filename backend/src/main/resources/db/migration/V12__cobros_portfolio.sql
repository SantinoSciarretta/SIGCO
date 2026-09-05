-- ============================================================================
--  V12 — Ajustes para Cobros y Portfolio Web
-- ============================================================================


-- ---------------------------------------------------------------------------
--  1. Anular el pago de una cuota, dejando constancia
-- ---------------------------------------------------------------------------
--  El informe pide: "Una cuota abonada no puede eliminarse, únicamente puede
--  anularse dejando un motivo, para conservar la trazabilidad del estado de
--  cuenta."
--
--  Lo que se anula es el PAGO, no la cuota: la cuota vuelve a Pendiente y sigue
--  debiéndose. El Diccionario no previó dónde guardar ese motivo.
--
--  Es el mismo criterio que en obra.motivo_cancelacion, gasto.motivo_anulacion
--  y pedido.motivo_anulacion: nada se da de baja sin decir por qué.

ALTER TABLE cuota ADD COLUMN motivo_anulacion VARCHAR(200);


-- ---------------------------------------------------------------------------
--  2. Estados de la publicación del portfolio
-- ---------------------------------------------------------------------------
--  V8 permitía 'Borrador', 'Publicada' y 'Oculta'. El informe, en la tabla
--  "Campos del Formulario de Portfolio", enumera dos:
--
--    "Estado_Publicacion | Lista desplegable | Publicada / Despublicada."
--
--  Y el circuito confirma que son dos estados y no tres: "Si en algún momento
--  quiere dejar de mostrar una obra, la despublica sin necesidad de borrar las
--  imágenes". Una obra despublicada conserva sus fotos, así que no hace falta
--  un tercer estado de borrador: eso ES el estado despublicado.

ALTER TABLE publicacion_portfolio DROP CONSTRAINT ck_publicacion_estado;

ALTER TABLE publicacion_portfolio
    ADD CONSTRAINT ck_publicacion_estado
        CHECK (estado IN ('Publicada', 'Despublicada'));


-- ---------------------------------------------------------------------------
--  3. Medio de pago y comprobante como conjuntos cerrados
-- ---------------------------------------------------------------------------
--  El informe los enumera en la tabla de campos de la cuota. V8 no los había
--  restringido.

ALTER TABLE cuota
    ADD CONSTRAINT ck_cuota_medio_pago
        CHECK (medio_pago IS NULL
               OR medio_pago IN ('Transferencia', 'Efectivo', 'Cheque'));

ALTER TABLE cuota
    ADD CONSTRAINT ck_cuota_comprobante
        CHECK (comprobante_emitido IS NULL
               OR comprobante_emitido IN ('Mensaje', 'Recibo', 'Planilla'));


-- ---------------------------------------------------------------------------
--  4. Tipo de trabajo del portfolio
-- ---------------------------------------------------------------------------
--  Se deja ABIERTO a propósito. El informe dice "Construcción / Refacción /
--  Decoración de local, ENTRE OTROS": es una lista de ejemplos, no un conjunto
--  cerrado. Encerrarla obligaría a una migración de base cada vez que la
--  empresa quiera mostrar un tipo de trabajo nuevo en la vidriera.
--
--  Es el mismo criterio que se aplicó a material.unidad_medida.
