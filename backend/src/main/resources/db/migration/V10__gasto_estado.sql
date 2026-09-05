-- ============================================================================
--  V10 — Estados del gasto
-- ============================================================================
--
--  V8 creó gasto con estado IN ('Registrado', 'Anulado'). Al desarrollar el
--  módulo se contrastó contra el informe, que en la tabla "Campos del
--  Formulario de Gasto" enumera explícitamente: "Estado_Gasto | Lista
--  desplegable | Confirmado / Anulado".
--
--  Se corrige el conjunto para que coincida con el informe. La tabla está
--  vacía, así que el cambio no arrastra datos.
-- ============================================================================

ALTER TABLE gasto DROP CONSTRAINT ck_gasto_estado;

ALTER TABLE gasto
    ADD CONSTRAINT ck_gasto_estado
        CHECK (estado IN ('Confirmado', 'Anulado'));

-- Conjunto cerrado del tipo de gasto, que V8 no había restringido.
-- El informe lo enumera: Material / Mano de Obra / Gasto Hormiga / Otro.
-- "Gasto Hormiga" es el caso que motiva el módulo: los gastos chicos que hoy
-- se pierden y que hay que poder medir por separado sin sacarlos de la
-- comparación contra el presupuesto del rubro.
ALTER TABLE gasto
    ADD CONSTRAINT ck_gasto_tipo
        CHECK (tipo_gasto IN ('Material', 'Mano de Obra', 'Gasto Hormiga', 'Otro'));
