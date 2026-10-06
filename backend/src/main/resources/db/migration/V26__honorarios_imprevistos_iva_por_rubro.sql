-- =============================================================================
--  V26 — Honorarios, imprevistos y la mano de obra sin IVA
--
--  Pedidos de Santino (05/10/2026):
--
--   * HONORARIOS: un porcentaje sobre el total de la obra (materiales, mano de
--     obra e imprevistos) que el sistema calcula solo.
--
--   * IMPREVISTOS: un rubro especial, como el de mano de obra. Su planilla lista
--     todos los rubros y en cada uno se pone un porcentaje, que se aplica sobre
--     el total de ESE rubro: sus materiales más su mano de obra.
--
--   * IVA: el 21% se aplica a todos los rubros MENOS a la mano de obra.
-- =============================================================================


-- -----------------------------------------------------------------------------
--  El rubro de imprevistos
--
--  Se reconoce por una marca, igual que el de mano de obra (ver V18): buscarlo
--  por el nombre se rompería el día que alguien lo renombre. Un índice único
--  parcial impide que haya dos.
-- -----------------------------------------------------------------------------
ALTER TABLE rubro ADD COLUMN es_imprevistos BOOLEAN NOT NULL DEFAULT FALSE;

CREATE UNIQUE INDEX ux_rubro_imprevistos ON rubro (es_imprevistos)
    WHERE es_imprevistos = TRUE;

-- Un rubro no puede ser las dos cosas a la vez.
ALTER TABLE rubro ADD CONSTRAINT ck_rubro_un_solo_rol_especial
    CHECK (NOT (es_mano_de_obra AND es_imprevistos));

-- Si ya hay uno que se llama "Imprevistos", se marca ese; si no, se crea.
UPDATE rubro SET es_imprevistos = TRUE
WHERE NOT EXISTS (SELECT 1 FROM rubro WHERE es_imprevistos = TRUE)
  AND es_mano_de_obra = FALSE
  AND id_rubro = (
      SELECT id_rubro FROM rubro
      WHERE LOWER(nombre_rubro) = 'imprevistos'
      ORDER BY id_rubro
      LIMIT 1
  );

INSERT INTO rubro (nombre_rubro, estado, es_mano_de_obra, es_imprevistos)
SELECT 'Imprevistos', 'Activo', FALSE, TRUE
WHERE NOT EXISTS (SELECT 1 FROM rubro WHERE es_imprevistos = TRUE)
  AND NOT EXISTS (SELECT 1 FROM rubro WHERE LOWER(nombre_rubro) = 'imprevistos');

UPDATE rubro SET estado = 'Activo' WHERE es_imprevistos = TRUE;


-- -----------------------------------------------------------------------------
--  A qué rubro se refiere un ítem de mano de obra o de imprevistos
--
--  La mano de obra de albañilería se guarda en el rubro "Mano de obra", así ese
--  total queda junto. Pero para calcular los imprevistos de Albañilería hace
--  falta saber que esa mano de obra es DE albañilería: eso dice esta columna.
--  Y en un ítem de imprevistos dice sobre qué rubro se calcula.
--
--  En los demás ítems queda vacía.
-- -----------------------------------------------------------------------------
ALTER TABLE item_presupuesto
    ADD COLUMN id_rubro_referido BIGINT REFERENCES rubro (id_rubro);

-- El porcentaje de un ítem de imprevistos. Se guarda el porcentaje y no solo
-- el monto para que, si después cambian los materiales del rubro, el imprevisto
-- se recalcule solo.
ALTER TABLE item_presupuesto
    ADD COLUMN porcentaje NUMERIC(5,2)
    CONSTRAINT ck_item_porcentaje CHECK (porcentaje >= 0 AND porcentaje <= 100);

-- La mano de obra ya cargada se describe "Albañilería / Demolición" o
-- "Albañilería": de ahí sale a qué rubro pertenece.
UPDATE item_presupuesto i
SET id_rubro_referido = r.id_rubro
FROM rubro r, rubro mo
WHERE i.id_rubro = mo.id_rubro
  AND mo.es_mano_de_obra = TRUE
  AND r.id_rubro <> mo.id_rubro
  AND (i.descripcion = r.nombre_rubro OR i.descripcion LIKE r.nombre_rubro || ' / %');


-- -----------------------------------------------------------------------------
--  Honorarios
-- -----------------------------------------------------------------------------
ALTER TABLE presupuesto
    ADD COLUMN honorarios_porcentaje NUMERIC(5,2)
    CONSTRAINT ck_presupuesto_honorarios
        CHECK (honorarios_porcentaje >= 0 AND honorarios_porcentaje <= 100);


-- -----------------------------------------------------------------------------
--  La mano de obra deja de llevar IVA
--
--  Como en V24, se recalculan SOLO los borradores. Los enviados y aprobados
--  conservan el total que vio el cliente, que además es la base de sus cuotas.
-- -----------------------------------------------------------------------------
UPDATE presupuesto p
SET total_presupuesto = ROUND(
        COALESCE((SELECT SUM(i.subtotal) FROM item_presupuesto i
                  JOIN rubro r ON r.id_rubro = i.id_rubro
                  WHERE i.id_presupuesto = p.id_presupuesto
                    AND r.es_mano_de_obra = FALSE), 0) * 1.21
      + COALESCE((SELECT SUM(i.subtotal) FROM item_presupuesto i
                  JOIN rubro r ON r.id_rubro = i.id_rubro
                  WHERE i.id_presupuesto = p.id_presupuesto
                    AND r.es_mano_de_obra = TRUE), 0), 2)
WHERE p.estado = 'Borrador'
  AND p.tipo_presupuesto <> 'Cotización inicial';
