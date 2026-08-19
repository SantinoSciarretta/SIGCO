-- ============================================================================
--  V4 · Presupuestos e ítems (módulo Presupuestación, parte B)
--
--  Cubre las tres instancias reales del proceso que releva el informe:
--
--    Cotización inicial  → m² × valor por m², sin ítems
--    Anteproyecto        → por rubro, solo en reformas
--    Definitivo          → por rubro, subrubro e ítem
--    Adicional           → cambio menor sobre un definitivo ya aprobado
--
--  El versionado es real: cuando el definitivo se arma a partir del
--  anteproyecto, el anteproyecto NO se sobrescribe. Quedan los dos como
--  registros independientes, vinculados por id_presupuesto_base.
-- ============================================================================

CREATE TABLE presupuesto (
    id_presupuesto       BIGSERIAL     PRIMARY KEY,

    -- No hay presupuesto sin obra. Es la primera regla del módulo.
    id_obra              BIGINT        NOT NULL,

    tipo_presupuesto     VARCHAR(20)   NOT NULL,

    -- Autorreferencia: apunta al presupuesto que se usó como punto de partida.
    -- Es lo que permite reconstruir la cadena completa de versiones de una obra
    -- sin duplicar información.
    id_presupuesto_base  BIGINT,

    -- Número de versión dentro del mismo tipo y la misma obra.
    version              INTEGER       NOT NULL,

    estado               VARCHAR(15)   NOT NULL,

    -- Solo se usan en la cotización inicial.
    metros_cuadrados     NUMERIC(10,2),
    valor_por_m2         NUMERIC(12,2),

    -- Suma de los subtotales de los ítems, o m² × valor/m² en la cotización.
    -- NUMERIC y no punto flotante: es plata.
    total_presupuesto    NUMERIC(14,2),

    -- Plan de pago que después consume el módulo Cobros.
    anticipo_porcentaje  NUMERIC(5,2),
    cantidad_cuotas      INTEGER,

    plazo_estimado_obra  VARCHAR(100),
    fecha_creacion       TIMESTAMP     NOT NULL,

    CONSTRAINT fk_presupuesto_obra
        FOREIGN KEY (id_obra) REFERENCES obra (id_obra),

    CONSTRAINT fk_presupuesto_base
        FOREIGN KEY (id_presupuesto_base) REFERENCES presupuesto (id_presupuesto),

    CONSTRAINT ck_presupuesto_tipo
        CHECK (tipo_presupuesto IN ('Cotización inicial', 'Anteproyecto', 'Definitivo', 'Adicional')),

    CONSTRAINT ck_presupuesto_estado
        CHECK (estado IN ('Borrador', 'Enviado', 'Aprobado', 'Rechazado')),

    CONSTRAINT ck_presupuesto_version
        CHECK (version >= 1),

    -- Un anticipo fuera de 0..100 no es un porcentaje.
    CONSTRAINT ck_presupuesto_anticipo
        CHECK (anticipo_porcentaje IS NULL
               OR (anticipo_porcentaje >= 0 AND anticipo_porcentaje <= 100)),

    CONSTRAINT ck_presupuesto_cuotas
        CHECK (cantidad_cuotas IS NULL OR cantidad_cuotas >= 0),

    -- Un presupuesto no puede ser su propia base.
    CONSTRAINT ck_presupuesto_base_distinta
        CHECK (id_presupuesto_base IS NULL OR id_presupuesto_base <> id_presupuesto)
);

CREATE INDEX idx_presupuesto_obra   ON presupuesto (id_obra);
CREATE INDEX idx_presupuesto_estado ON presupuesto (estado);
CREATE INDEX idx_presupuesto_base   ON presupuesto (id_presupuesto_base);


CREATE TABLE item_presupuesto (
    id_item          BIGSERIAL     PRIMARY KEY,
    id_presupuesto   BIGINT        NOT NULL,

    -- El rubro es obligatorio; el subrubro no, porque el anteproyecto se carga
    -- solo a nivel de rubro y recién el definitivo baja a subrubro.
    id_rubro         BIGINT        NOT NULL,
    id_subrubro      BIGINT,

    descripcion      VARCHAR(250)  NOT NULL,
    unidad_medida    VARCHAR(20)   NOT NULL,
    cantidad         NUMERIC(12,2) NOT NULL,

    -- Precio unitario: dato interno. El informe es explícito en que no aparece
    -- en el PDF que recibe el cliente, donde solo se muestra el subtotal por
    -- rubro.
    valor_unitario   NUMERIC(12,2) NOT NULL,

    -- cantidad × valor_unitario. Se guarda calculado y no se recalcula al leer,
    -- para que un presupuesto aprobado conserve exactamente el número que se le
    -- mostró al cliente aunque después cambie algo.
    subtotal         NUMERIC(14,2) NOT NULL,

    CONSTRAINT fk_item_presupuesto
        FOREIGN KEY (id_presupuesto) REFERENCES presupuesto (id_presupuesto),

    CONSTRAINT fk_item_rubro
        FOREIGN KEY (id_rubro) REFERENCES rubro (id_rubro),

    CONSTRAINT fk_item_subrubro
        FOREIGN KEY (id_subrubro) REFERENCES subrubro (id_subrubro),

    CONSTRAINT ck_item_cantidad
        CHECK (cantidad > 0),

    CONSTRAINT ck_item_valor_unitario
        CHECK (valor_unitario >= 0)
);

CREATE INDEX idx_item_presupuesto ON item_presupuesto (id_presupuesto);
CREATE INDEX idx_item_rubro       ON item_presupuesto (id_rubro);

COMMENT ON TABLE  presupuesto IS 'Versiones de presupuesto de una obra. No se eliminan: se marcan Rechazado.';
COMMENT ON COLUMN presupuesto.id_presupuesto_base IS 'Presupuesto usado como punto de partida. Habilita el versionado.';
COMMENT ON COLUMN item_presupuesto.valor_unitario IS 'Interno: no se muestra en el PDF del cliente.';
