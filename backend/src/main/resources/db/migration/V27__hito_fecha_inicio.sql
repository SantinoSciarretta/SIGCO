-- =============================================================================
--  V27 — Fecha de inicio de las etapas
--
--  Pedido de Santino (06/10/2026): hay tareas que se hacen en simultáneo (la
--  instalación eléctrica mientras se termina la albañilería, por ejemplo). Con
--  solo el orden y la duración, el plan las ponía una detrás de otra.
--
--  Con la fecha de inicio cada etapa dice cuándo arranca, y dos etapas pueden
--  solaparse. El orden ya no lo escribe nadie: sale de la fecha de inicio.
--
--  Es opcional: una etapa sin fecha arranca cuando termina la anterior, como
--  hasta ahora. Los hitos cargados antes de este cambio quedan sin fecha.
-- =============================================================================

ALTER TABLE hito ADD COLUMN fecha_inicio DATE;

COMMENT ON COLUMN hito.fecha_inicio IS
    'Cuándo arranca la etapa. Opcional. El orden de las etapas sale de esta fecha.';
