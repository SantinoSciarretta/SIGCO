-- ============================================================================
--  V5 · Módulo Materiales
--
--  Catálogo único de materiales. Reemplaza los nombres escritos a mano que hoy
--  cambian de una obra a otra: "cemento", "Cemento CP40", "bolsa cemento".
--
--  Lo consumen Presupuestación (al cargar ítems) y Compras (al armar pedidos).
--  Que exista una sola lista es lo que evita que cada módulo mantenga la suya
--  y terminen desincronizadas.
-- ============================================================================

CREATE TABLE material (
    id_material      BIGSERIAL     PRIMARY KEY,
    nombre_material  VARCHAR(150)  NOT NULL,

    -- El rubro sale del mismo catálogo que usa Presupuestación, no de una lista
    -- propia. Es lo que mantiene la coherencia entre los dos módulos.
    id_rubro         BIGINT        NOT NULL,

    -- Obligatoria: de ella depende cómo se interpreta la cantidad que se carga
    -- en un pedido o en un ítem de presupuesto. "5" no significa nada si no se
    -- sabe si son bolsas, metros o litros.
    unidad_medida    VARCHAR(20)   NOT NULL,

    estado           VARCHAR(10)   NOT NULL,
    fecha_alta       TIMESTAMP     NOT NULL,

    CONSTRAINT fk_material_rubro
        FOREIGN KEY (id_rubro) REFERENCES rubro (id_rubro),

    CONSTRAINT ck_material_estado
        CHECK (estado IN ('Activo', 'Inactivo'))
);

-- La unidad de medida NO lleva restricción de conjunto cerrado, a diferencia
-- del estado. El informe la enumera como "Unidad / Bolsa / Metro cuadrado /
-- Metro lineal / Litro, entre otras": es una lista abierta, y encerrarla en un
-- CHECK obligaría a una migración cada vez que aparezca un material que se mide
-- distinto. La aplicación ofrece las habituales y admite escribir otra.

-- Mismo criterio que en subrubro: el nombre es único DENTRO de su rubro.
-- "Membrana" puede existir en Techos y en Impermeabilizaciones, pero no dos
-- veces en el mismo rubro.
CREATE UNIQUE INDEX ux_material_nombre_por_rubro
    ON material (id_rubro, LOWER(nombre_material));

-- El listado filtra por rubro y busca por nombre sin distinguir mayúsculas.
CREATE INDEX idx_material_rubro        ON material (id_rubro);
CREATE INDEX idx_material_nombre_lower ON material (LOWER(nombre_material));

COMMENT ON TABLE  material IS 'Catálogo único de materiales, compartido por Presupuestación y Compras.';
COMMENT ON COLUMN material.unidad_medida IS 'Lista abierta: bolsa, m², ml, litro, unidad, entre otras.';
