-- =============================================================================
--  V25 — El rubro "Mano de obra" queda creado y marcado
--
--  Pedido de Santino (05/10/2026): que el sistema ya traiga el rubro de mano de
--  obra, sin tener que crearlo y marcarlo a mano desde el catálogo.
--
--  Sus "ítems" (una fila por cada rubro y subrubro) NO se guardan acá: la
--  planilla los arma en el momento a partir del catálogo. Así, un rubro o
--  subrubro que se agregue mañana aparece solo en la planilla de mano de obra,
--  sin tener que acordarse de sumarlo también ahí.
--
--  La migración no pisa nada que ya exista:
--    1. Si ya hay un rubro marcado como mano de obra, se respeta ese.
--    2. Si no, y hay un rubro que se llama "Mano de obra" (con o sin
--       mayúsculas o tildes), se marca ese en lugar de crear otro.
--    3. Si no hay ninguno, se crea.
--  Al final el rubro marcado queda activo, porque un rubro inactivo no se puede
--  elegir al presupuestar.
-- =============================================================================


-- 2. Marcar el que ya existe con ese nombre, si no hay ninguno marcado.
UPDATE rubro SET es_mano_de_obra = TRUE
WHERE NOT EXISTS (SELECT 1 FROM rubro WHERE es_mano_de_obra = TRUE)
  AND id_rubro = (
      SELECT id_rubro FROM rubro
      WHERE LOWER(TRANSLATE(nombre_rubro, 'áéíóúÁÉÍÓÚ', 'aeiouAEIOU')) = 'mano de obra'
      ORDER BY id_rubro
      LIMIT 1
  );


-- 3. Crearlo si sigue sin haber ninguno. El segundo NOT EXISTS evita chocar
--    con el índice de nombre único si hubiera uno con ese nombre ya marcado de
--    otra forma (no debería pasar después del paso 2, pero no cuesta nada).
INSERT INTO rubro (nombre_rubro, estado, es_mano_de_obra)
SELECT 'Mano de obra', 'Activo', TRUE
WHERE NOT EXISTS (SELECT 1 FROM rubro WHERE es_mano_de_obra = TRUE)
  AND NOT EXISTS (SELECT 1 FROM rubro WHERE LOWER(nombre_rubro) = 'mano de obra');


-- El rubro de mano de obra tiene que poder elegirse.
UPDATE rubro SET estado = 'Activo' WHERE es_mano_de_obra = TRUE;
