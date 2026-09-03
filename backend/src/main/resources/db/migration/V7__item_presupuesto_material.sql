-- ============================================================================
--  V7 — Vínculo entre el ítem de presupuesto y el catálogo de Materiales
-- ============================================================================
--
--  POR QUÉ EXISTE ESTA MIGRACIÓN
--
--  El Diccionario de Datos define item_presupuesto sin columna id_material,
--  pero la prosa del informe describe que los ítems se eligen del catálogo de
--  Materiales. Es una inconsistencia entre los dos documentos, y sin esta
--  columna el vínculo no puede existir: la descripción del ítem queda como
--  texto libre y se repite el problema que Materiales viene a resolver, que es
--  que "cemento", "Cemento CP40" y "bolsa cemento" sean tres cosas distintas.
--
--  El Diccionario se corrige con esta columna. Justificación completa en
--  docs/desarrollo/05-presupuestacion-presupuestos.md.
--
--  POR QUÉ ES NULLABLE
--
--  No todo ítem de un presupuesto es un material. "Mano de obra de albañilería"
--  o "Dirección de obra" son ítems legítimos que no salen del catálogo. Hacer
--  la columna obligatoria forzaría a inventar materiales falsos para poder
--  cargarlos.
--
--  Cuando el ítem sí refiere a un material, la descripción se sigue guardando:
--  es lo que se imprime en el PDF del cliente y puede necesitar más detalle que
--  el nombre del catálogo ("Cemento CP40 para carpeta del baño").
-- ============================================================================

ALTER TABLE item_presupuesto
    ADD COLUMN id_material BIGINT;

ALTER TABLE item_presupuesto
    ADD CONSTRAINT fk_item_material
        FOREIGN KEY (id_material) REFERENCES material (id_material);

-- Para responder "en qué presupuestos se usó este material", que es la consulta
-- que habilita el vínculo. Parcial: la mayoría de los ítems no tienen material
-- y no tiene sentido indexar esas filas.
CREATE INDEX idx_item_material
    ON item_presupuesto (id_material)
    WHERE id_material IS NOT NULL;
