-- ============================================================================
--  V9 — Correcciones sobre pedido, antes de desarrollar el módulo Compras
-- ============================================================================
--
--  V8 creó las 28 tablas a partir del Diccionario para poder mostrar el
--  diagrama completo. Al desarrollar Compras y contrastar contra el circuito
--  que describe el informe aparecieron tres faltantes. Las tablas están vacías,
--  así que los ALTER no arrastran datos.
-- ============================================================================


-- ---------------------------------------------------------------------------
--  1. El proveedor se elige al APROBAR, no al crear el pedido
-- ---------------------------------------------------------------------------
--  Circuito del informe: "Quien esté a cargo en la obra detecta que faltan
--  materiales y carga un pedido [...] El pedido queda en estado Pendiente de
--  Aprobación. El dueño revisa el pedido, lo aprueba y selecciona el proveedor
--  al que se lo va a enviar, tomándolo del registro de Proveedores según la
--  zona de la obra."
--
--  El capataz que arma el pedido no decide a quién comprarle: esa es una
--  decisión del dueño y llega después. Con NOT NULL el pedido no se podría
--  crear, porque en ese momento todavía no hay proveedor.

ALTER TABLE pedido ALTER COLUMN id_proveedor DROP NOT NULL;

-- Pero sí es obligatorio en cuanto el pedido sale de "Pendiente de Aprobación":
-- no se puede enviar ni recibir un pedido sin saber a quién se le compró.
ALTER TABLE pedido
    ADD CONSTRAINT ck_pedido_proveedor_al_aprobar
        CHECK (estado = 'Pendiente de Aprobación'
               OR estado = 'Anulado'
               OR id_proveedor IS NOT NULL);


-- ---------------------------------------------------------------------------
--  2. Motivo de anulación
-- ---------------------------------------------------------------------------
--  El informe pide: "Un pedido no puede eliminarse una vez aprobado, solo puede
--  quedar marcado como anulado si finalmente no se concreta, dejando un motivo."
--
--  El Diccionario no previó la columna. Es el mismo criterio que ya se aplica en
--  obra.motivo_cancelacion y gasto.motivo_anulacion: no se da de baja algo sin
--  dejar constancia de por qué.

ALTER TABLE pedido ADD COLUMN motivo_anulacion VARCHAR(200);

ALTER TABLE pedido
    ADD CONSTRAINT ck_pedido_motivo_anulacion
        CHECK (estado <> 'Anulado' OR motivo_anulacion IS NOT NULL);


-- ---------------------------------------------------------------------------
--  3. Precio unitario de cada material del pedido
-- ---------------------------------------------------------------------------
--  El informe dice: "Al confirmar la recepción, el gasto generado
--  automáticamente toma como monto el valor cargado en el pedido."
--
--  Pero ni pedido ni pedido_material tenían precio, así que ese monto no existía
--  en ningún lado y la integración Compras -> Gastos no podía implementarse.
--
--  Es NULL al crear el pedido, por el mismo motivo que el proveedor: el capataz
--  pide "20 bolsas de cemento" sin saber a cuánto. El precio se completa al
--  aprobar, cuando ya se eligió el proveedor, y el sistema lo propone a partir
--  de la última cotización de ese proveedor para ese material.

ALTER TABLE pedido_material ADD COLUMN precio_unitario NUMERIC(12,2);

ALTER TABLE pedido_material
    ADD CONSTRAINT ck_pedido_material_precio
        CHECK (precio_unitario IS NULL OR precio_unitario >= 0);
