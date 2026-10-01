-- ---------------------------------------------------------------------------
-- V22 — Anular un pago ya no borra la fila, y el CAC no se puede aplicar dos veces
-- ---------------------------------------------------------------------------
--
-- Dos huecos que aparecieron en la auditoría de código del 01/10/2026, ninguno
-- de los dos pedido por Ricardo ni documentado antes de ahora.
--
-- ---------------------------------------------------------------------------
--  1) Anular un pago hacía un DELETE real
-- ---------------------------------------------------------------------------
--
-- Cuota.anularPago() llamaba pagos.clear() sobre una colección mapeada con
-- orphanRemoval = true, así que Hibernate borraba físicamente las filas de
-- `pago`. Se perdía el detalle de cada pago (monto, fecha, medio, comprobante,
-- quién lo cargó) justo cuando más hace falta reconstruirlo: después de un
-- error. El resto del sistema nunca borra para anular —Gasto usa estado +
-- motivo_anulacion—, y Pago queda alineado a ese mismo criterio.
--
-- ---------------------------------------------------------------------------
--  2) El mismo CAC se podía aplicar dos veces a la misma obra
-- ---------------------------------------------------------------------------
--
-- aplicarCac() no registraba en ningún lado qué coeficiente ya se le había
-- aplicado a una obra. Dos clics (o un reintento de red) componían el ajuste
-- sobre un saldo que ya lo tenía: 1,4 aplicado dos veces multiplicaba el saldo
-- por 1,96, no por 1,4. Guardar en la obra el id del último CAC aplicado
-- alcanza para rechazar la repetición sin necesitar una tabla aparte.

ALTER TABLE pago
    ADD COLUMN anulado BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN motivo_anulacion VARCHAR(200);

COMMENT ON COLUMN pago.anulado IS
    'Si este pago fue anulado. No se borra la fila: se marca, igual que gasto.estado.';

ALTER TABLE obra
    ADD COLUMN id_ultimo_cac_aplicado BIGINT REFERENCES registro_cac (id_cac);

COMMENT ON COLUMN obra.id_ultimo_cac_aplicado IS
    'Id del último registro_cac ya aplicado a esta obra. Impide reaplicar el mismo coeficiente dos veces.';
