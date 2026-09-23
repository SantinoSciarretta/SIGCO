-- =============================================================================
--  V16 — Pagos parciales de cuotas
--
--  ADVERTENCIA para quien lea esto despues: es alcance NUEVO, no una correccion.
--  El informe no pide pagos parciales. El Diccionario define "cuota" con estados
--  Pendiente / Abonada / Vencida, y hasta aca el sistema cumplia exactamente eso:
--  una cuota se cobraba entera o no se cobraba.
--
--  Se agrega a pedido de Santino en la auditoria previa a la entrega, para poder
--  registrar cuando un cliente paga una cuota en partes. Conviene que Ricardo
--  sepa que el sistema hace algo que el informe no describe.
--
--  ------------------------------------------------------------------
--   Por que una tabla y no una columna "monto_pagado" en cuota
--  ------------------------------------------------------------------
--
--  Una columna alcanzaria para saber CUANTO se pago, y no serviria para nada
--  mas. Se perderia cuando se pago cada parte, por que medio y con que
--  comprobante, que es justo lo que hace falta cuando el cliente pregunta "¿esa
--  transferencia de marzo la tenes registrada?".
--
--  Con una tabla, cada pago es un hecho con su fecha y su medio, y el total sale
--  de sumarlos. Es el mismo criterio con el que Gastos no guarda un total por
--  rubro sino cada gasto.
-- =============================================================================


-- -----------------------------------------------------------------------------
--  1. Los pagos
-- -----------------------------------------------------------------------------
CREATE TABLE pago (
    id_pago             BIGSERIAL     PRIMARY KEY,
    id_cuota            BIGINT        NOT NULL,
    monto               NUMERIC(14,2) NOT NULL,
    fecha_pago          DATE          NOT NULL,
    medio_pago          VARCHAR(15)   NOT NULL,
    comprobante_emitido VARCHAR(20),
    -- Quien lo cargo. Un cobro es de las pocas acciones del sistema que afirman
    -- un hecho del mundo real y que no se puede verificar mirando otra pantalla.
    id_usuario_registro BIGINT,
    fecha_carga         TIMESTAMP     NOT NULL,

    CONSTRAINT fk_pago_cuota
        FOREIGN KEY (id_cuota) REFERENCES cuota (id_cuota),
    CONSTRAINT fk_pago_usuario
        FOREIGN KEY (id_usuario_registro) REFERENCES usuario (id_usuario),

    -- Un pago de cero o negativo no es un pago. Que no supere el saldo se
    -- verifica en el servicio: depende de los otros pagos de la misma cuota y
    -- una restriccion de tabla no puede mirar otras filas.
    CONSTRAINT ck_pago_monto
        CHECK (monto > 0),
    CONSTRAINT ck_pago_medio
        CHECK (medio_pago IN ('Transferencia', 'Efectivo', 'Cheque'))
);

CREATE INDEX idx_pago_cuota ON pago (id_cuota);
CREATE INDEX idx_pago_fecha ON pago (fecha_pago);


-- -----------------------------------------------------------------------------
--  2. El estado "Parcial"
--
--  Una cuota con algo pagado pero no todo. No es Pendiente —ya entro plata— ni
--  Abonada —todavia se debe—, y sin un estado propio esa diferencia no se ve en
--  ninguna pantalla.
-- -----------------------------------------------------------------------------
ALTER TABLE cuota DROP CONSTRAINT ck_cuota_estado;

ALTER TABLE cuota ADD CONSTRAINT ck_cuota_estado
    CHECK (estado IN ('Pendiente', 'Parcial', 'Abonada', 'Vencida'));


-- -----------------------------------------------------------------------------
--  3. Los pagos que ya estaban registrados
--
--  ESTE ES EL PASO QUE NO SE PUEDE SALTEAR. Hasta ahora el pago vivia en la
--  propia cuota (fecha_pago, medio_pago, comprobante_emitido). Si la tabla nueva
--  arrancara vacia, todas las cuotas ya cobradas pasarian a figurar impagas: el
--  sistema le diria a Ricardo que tiene que reclamar plata que ya cobro.
--
--  Cada cuota Abonada genera un pago por su monto total, conservando su fecha y
--  su medio. El usuario queda en null: no se guardaba quien la habia cobrado.
--
--  medio_pago es NOT NULL en la tabla nueva y en cuota era opcional, asi que se
--  completa con 'Transferencia' cuando falta. Es el medio mas frecuente de la
--  empresa y la alternativa —rechazar la migracion— dejaria el sistema sin
--  arrancar por un dato historico incompleto.
-- -----------------------------------------------------------------------------
INSERT INTO pago (id_cuota, monto, fecha_pago, medio_pago, comprobante_emitido,
                  id_usuario_registro, fecha_carga)
SELECT c.id_cuota,
       c.monto_cuota,
       c.fecha_pago,
       COALESCE(c.medio_pago, 'Transferencia'),
       c.comprobante_emitido,
       NULL,
       CURRENT_TIMESTAMP
FROM cuota c
WHERE c.estado = 'Abonada'
  AND c.fecha_pago IS NOT NULL;


-- -----------------------------------------------------------------------------
--  4. Las columnas de pago quedan en cuota, y es deliberado
--
--  fecha_pago, medio_pago y comprobante_emitido siguen existiendo, ahora como
--  reflejo del ULTIMO pago recibido. No se eliminan por dos motivos:
--
--    1. La restriccion ck_cuota_fecha_pago las usa: una cuota Abonada tiene que
--       tener fecha de pago, y esa regla es del informe.
--    2. El Diccionario de Datos que se entrega a la catedra las describe. Sacar
--       columnas documentadas obligaria a explicar por que el sistema no
--       coincide con su propio diccionario.
--
--  Quien quiera el detalle mira la tabla pago; quien quiera "cuando se termino
--  de pagar esto", mira la cuota.
-- -----------------------------------------------------------------------------
