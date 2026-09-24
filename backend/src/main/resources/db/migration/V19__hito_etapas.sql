-- ---------------------------------------------------------------------------
-- V19 — Las etapas de la obra: rubro y duración
-- ---------------------------------------------------------------------------
--
-- Pedido de Ricardo al probar el sistema:
--
--   "que haya una sección que permita cargar las etapas de cada obra y que eso
--    alimente directamente al seguimiento de esa obra, que pueda cargar un ítem
--    de algo que se tenga que hacer por ejemplo demolición de una pared, que
--    ponga el rubro al que pertenece, cuántas semanas o días cree que va a
--    tardar, y que eso calcule el porcentaje que representaría en avance en
--    cuanto a la duración de la obra, y que pueda ordenarlos por cuál va
--    primero"
--
-- Eso ES el hito que ya existe, cargado de otra manera. Hoy el hito tiene
-- nombre, ponderación y orden, y la ponderación se escribe a mano cuidando que
-- el conjunto sume 100. Lo que Ricardo pide es no escribirla: cargar cuánto
-- dura cada etapa y que el porcentaje salga de ahí.
--
-- Por eso esto NO crea una tabla `etapa` nueva. Una tabla aparte significaría
-- dos fuentes de avance para la misma obra, y en algún momento se
-- contradirían: la sección de etapas diría 60% y la de seguimiento 45%.
-- Duplicar el concepto es exactamente lo que el sistema viene a evitar.
--
-- Las dos columnas son NULLABLE a propósito: los hitos ya cargados no tienen
-- rubro ni duración, y obligarlos retroactivamente significaría inventarles un
-- dato. Un hito cargado a la vieja usanza —con su ponderación escrita a mano—
-- sigue siendo válido.

ALTER TABLE hito
    ADD COLUMN id_rubro BIGINT,
    ADD COLUMN duracion_dias INTEGER;

ALTER TABLE hito
    ADD CONSTRAINT fk_hito_rubro
        FOREIGN KEY (id_rubro) REFERENCES rubro (id_rubro);

-- Una etapa de cero días no representaría nada del avance, y una de más de
-- tres años no es una etapa. El tope existe para atajar el error de tipeo
-- —escribir 300 donde iban 30— que desbalancearía todas las demás.
ALTER TABLE hito
    ADD CONSTRAINT ck_hito_duracion
        CHECK (duracion_dias IS NULL OR (duracion_dias > 0 AND duracion_dias <= 1095));

-- Se consulta al armar el balance por rubro del avance.
CREATE INDEX ix_hito_rubro ON hito (id_rubro);

COMMENT ON COLUMN hito.id_rubro IS
    'Rubro al que pertenece la etapa. Nullable: los hitos cargados por ponderación directa no lo tienen.';
COMMENT ON COLUMN hito.duracion_dias IS
    'Días que se estima que lleva la etapa. La ponderación se deriva de esto cuando se carga por duración.';
