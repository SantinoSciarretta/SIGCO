-- ============================================================================
--  V11 — Ajustes para Personal y Seguimiento de Obras
-- ============================================================================


-- ---------------------------------------------------------------------------
--  1. Desvincular a un operario sin borrar su historial
-- ---------------------------------------------------------------------------
--  El informe pide dos cosas que, con el modelo de V8, se contradecían:
--
--    "Al marcar un operario como Inactivo, se lo desvincula automáticamente de
--     las obras activas, PERO SE CONSERVA su historial de inasistencias y de
--     OBRAS ANTERIORES."
--
--  operario_obra solo tenía fecha_asignacion. Desvincular significaba borrar la
--  fila, y con eso se perdía justamente el historial de obras que el informe
--  manda conservar. Tampoco se podría después responder "en qué obras trabajó
--  este operario", que es la pregunta que la ficha tiene que contestar.
--
--  Con fecha_desasignacion la baja es un dato, no un borrado: la fila queda y
--  se sabe desde cuándo dejó de estar en esa obra. Una asignación vigente es la
--  que tiene la fecha en NULL.

ALTER TABLE operario_obra ADD COLUMN fecha_desasignacion DATE;

ALTER TABLE operario_obra
    ADD CONSTRAINT ck_operario_obra_fechas
        CHECK (fecha_desasignacion IS NULL
               OR fecha_desasignacion >= fecha_asignacion);


-- ---------------------------------------------------------------------------
--  2. Estados del hito
-- ---------------------------------------------------------------------------
--  V8 permitía 'Pendiente', 'En curso' y 'Completado'. El informe, en la tabla
--  "Campos del Formulario de Hito", enumera solo dos:
--
--    "Estado_Hito | Lista desplegable | Pendiente / Completado."
--
--  Y hay un motivo de fondo para que sean dos y no tres: el avance físico se
--  calcula "únicamente sobre los hitos completados y su ponderación, sin
--  estimaciones intermedias de hitos en curso, para que el valor sea siempre
--  objetivo". Un estado "En curso" invitaría justamente a la estimación
--  intermedia que el informe quiere evitar.

ALTER TABLE hito DROP CONSTRAINT ck_hito_estado;

ALTER TABLE hito
    ADD CONSTRAINT ck_hito_estado
        CHECK (estado IN ('Pendiente', 'Completado'));


-- ---------------------------------------------------------------------------
--  3. Un hito no se repite dentro de la misma obra
-- ---------------------------------------------------------------------------
--  Dos hitos con el mismo nombre en una obra son casi con seguridad una carga
--  duplicada, y romperían el cálculo del avance sumando dos veces la misma
--  ponderación.

CREATE UNIQUE INDEX ux_hito_obra_nombre
    ON hito (id_obra, LOWER(nombre_hito));

-- Y tampoco se repite el orden: define la secuencia prevista de la obra.
CREATE UNIQUE INDEX ux_hito_obra_orden ON hito (id_obra, orden);
