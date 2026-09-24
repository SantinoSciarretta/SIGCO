-- =============================================================================
--  V17 — Plazo estimado de la obra
--
--  Pedido de Ricardo al probar el sistema: al dar de alta una obra quiere cargar
--  cuando cree que arranca y cuantos meses cree que va a durar, y que el sistema
--  calcule solo la fecha tentativa de finalizacion.
--
--  ------------------------------------------------------------------
--   Por que hacen falta DOS columnas y no alcanzaba con las que habia
--  ------------------------------------------------------------------
--
--  La obra ya tiene fecha_inicio_real y fecha_fin_estimada, y ninguna de las dos
--  sirve para esto:
--
--    - fecha_inicio_real es cuando arrancaron los trabajos DE VERDAD, y el
--      informe prohibe cargarla hasta que el presupuesto definitivo este
--      aprobado. Al dar de alta la obra todavia no existe.
--    - fecha_fin_estimada es el resultado que se quiere calcular, no un dato de
--      entrada.
--
--  Falta la fecha en que se ESTIMA que va a empezar, que es un dato del momento
--  del alta y no compromete nada.
-- =============================================================================


-- -----------------------------------------------------------------------------
--  1. Cuando se estima que arranca
--
--  Distinta de fecha_inicio_real a proposito. Una es una intencion —"calculo
--  que empezamos en marzo"— y la otra un hecho —"el 4 de marzo se puso el
--  primer ladrillo". Mezclarlas haria que el sistema no pudiera decir despues
--  si la obra arranco cuando se pensaba.
-- -----------------------------------------------------------------------------
ALTER TABLE obra ADD COLUMN fecha_inicio_estimada DATE;


-- -----------------------------------------------------------------------------
--  2. Cuantos meses se estima que dura
--
--  En MESES y no en dias porque es como se habla en la obra: "esto son tres
--  meses". Pedir dias obligaria a hacer una cuenta que nadie hace de cabeza.
--
--  La fecha tentativa de fin sale de sumar estos meses a la fecha de inicio, y
--  NO se guarda aparte: se calcula y se escribe en fecha_fin_estimada, que ya
--  existe y que ya consumen Seguimiento (dias para el plazo, obra atrasada) y el
--  Tablero. Guardar el resultado en una columna nueva dejaria dos fechas de fin
--  que podrian contradecirse.
-- -----------------------------------------------------------------------------
ALTER TABLE obra ADD COLUMN meses_estimados INTEGER;

ALTER TABLE obra ADD CONSTRAINT ck_obra_meses_estimados
    CHECK (meses_estimados IS NULL OR (meses_estimados > 0 AND meses_estimados <= 120));


-- -----------------------------------------------------------------------------
--  3. Las obras que ya existen
--
--  Quedan con las dos columnas en NULL, y es lo correcto: no se puede inventar
--  cuando se estimo que arrancaban. Conservan su fecha_fin_estimada cargada a
--  mano, que sigue siendo valida. El calculo automatico aplica desde que alguien
--  complete los dos campos nuevos.
-- -----------------------------------------------------------------------------
