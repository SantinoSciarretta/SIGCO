-- ============================================================================
--  V6 · Módulo Proveedores
--
--  Reemplaza las cotizaciones que hoy se piden por WhatsApp y se pierden, y la
--  memoria del dueño sobre qué corralón cumple y cuál demora.
--
--  Tres tablas:
--    proveedor              · el corralón, con su zona de cobertura
--    cotizacion             · precio de un material informado por un proveedor
--    observacion_proveedor  · comportamiento (demoras, faltantes, diferencias)
-- ============================================================================

CREATE TABLE proveedor (
    id_proveedor       BIGSERIAL     PRIMARY KEY,
    nombre_proveedor   VARCHAR(150)  NOT NULL,

    -- Obligatoria: es el criterio principal con el que el dueño elige a quién
    -- pedirle, porque un corralón que no llega a la obra no sirve por más
    -- barato que sea.
    zona_cobertura     VARCHAR(100)  NOT NULL,

    telefono_contacto  VARCHAR(30),
    email_contacto     VARCHAR(100),
    estado             VARCHAR(10)   NOT NULL,
    fecha_alta         TIMESTAMP     NOT NULL,

    CONSTRAINT ck_proveedor_estado
        CHECK (estado IN ('Activo', 'Inactivo'))
);

-- El informe prohíbe dos proveedores con el mismo nombre Y la misma zona. La
-- combinación importa: una cadena de corralones puede tener sucursales con el
-- mismo nombre en zonas distintas, y son proveedores diferentes a efectos de
-- a quién pedirle.
CREATE UNIQUE INDEX ux_proveedor_nombre_zona
    ON proveedor (LOWER(nombre_proveedor), LOWER(zona_cobertura));

CREATE INDEX idx_proveedor_zona ON proveedor (LOWER(zona_cobertura));


CREATE TABLE cotizacion (
    id_cotizacion     BIGSERIAL      PRIMARY KEY,
    id_proveedor      BIGINT         NOT NULL,
    id_material       BIGINT         NOT NULL,

    -- NUMERIC y no punto flotante: es plata.
    precio_cotizado   NUMERIC(12,2)  NOT NULL,

    -- La pone el sistema al registrar. Es lo que permite distinguir una
    -- cotización de ayer de una de hace ocho meses al comparar precios.
    fecha_cotizacion  TIMESTAMP      NOT NULL,

    CONSTRAINT fk_cotizacion_proveedor
        FOREIGN KEY (id_proveedor) REFERENCES proveedor (id_proveedor),

    CONSTRAINT fk_cotizacion_material
        FOREIGN KEY (id_material) REFERENCES material (id_material),

    CONSTRAINT ck_cotizacion_precio
        CHECK (precio_cotizado >= 0)
);

-- El comparador busca por material y ordena por precio; la ficha del proveedor
-- busca por proveedor.
CREATE INDEX idx_cotizacion_material  ON cotizacion (id_material);
CREATE INDEX idx_cotizacion_proveedor ON cotizacion (id_proveedor);


CREATE TABLE observacion_proveedor (
    id_observacion  BIGSERIAL     PRIMARY KEY,
    id_proveedor    BIGINT        NOT NULL,

    -- Pedido que originó la observación. Queda SIN clave foránea por ahora:
    -- la tabla pedido se crea en el módulo Compras, que va después. La
    -- restricción se agrega en esa migración.
    -- TODO: agregar FK hacia pedido al desarrollar el módulo Compras.
    id_pedido       BIGINT,

    descripcion     VARCHAR(300)  NOT NULL,
    fecha           TIMESTAMP     NOT NULL,

    CONSTRAINT fk_observacion_proveedor
        FOREIGN KEY (id_proveedor) REFERENCES proveedor (id_proveedor)
);

CREATE INDEX idx_observacion_proveedor ON observacion_proveedor (id_proveedor);

COMMENT ON TABLE  proveedor IS 'Corralones y proveedores, con su zona de cobertura.';
COMMENT ON TABLE  cotizacion IS 'Precio de un material informado por un proveedor, con su fecha.';
COMMENT ON COLUMN observacion_proveedor.id_pedido IS 'Sin FK hasta que exista la tabla pedido (módulo Compras).';
