-- ============================================================================
--  V2 · Módulo Obras
--
--  Entidad núcleo del sistema. Presupuestación, Gastos, Cobros, Seguimiento y
--  Personal necesitan una obra existente para operar, así que esta tabla es el
--  punto de entrada obligatorio del resto del modelo.
--
--  Los nombres y tipos salen tal cual del Diccionario de Datos.
-- ============================================================================

CREATE TABLE obra (
    id_obra              BIGSERIAL     PRIMARY KEY,

    -- No hay obra sin cliente: la restricción NOT NULL sobre la clave foránea
    -- es lo que hace imposible saltear esa regla, incluso desde fuera de la
    -- aplicación.
    id_cliente           BIGINT        NOT NULL,

    direccion_obra       VARCHAR(200)  NOT NULL,
    tipo_inmueble        VARCHAR(15)   NOT NULL,

    -- Determina el circuito que sigue Presupuestación: solo las reformas
    -- llevan etapa de anteproyecto.
    tipo_obra            VARCHAR(15)   NOT NULL,

    -- Se carga recién cuando arrancan los trabajos, una vez aprobado el
    -- presupuesto definitivo.
    fecha_inicio_real    DATE,

    -- Se toma del plazo indicado en el presupuesto definitivo.
    fecha_fin_estimada   DATE,

    notas                TEXT,
    estado               VARCHAR(20)   NOT NULL,
    motivo_cancelacion   VARCHAR(200),
    fecha_creacion       TIMESTAMP     NOT NULL,

    CONSTRAINT fk_obra_cliente
        FOREIGN KEY (id_cliente) REFERENCES cliente (id_cliente),

    CONSTRAINT ck_obra_tipo_inmueble
        CHECK (tipo_inmueble IN ('Casa', 'Departamento', 'Local')),

    CONSTRAINT ck_obra_tipo_obra
        CHECK (tipo_obra IN ('Construcción', 'Reforma')),

    CONSTRAINT ck_obra_estado
        CHECK (estado IN ('En presupuestación', 'En ejecución', 'Finalizada', 'Cancelada')),

    -- El informe pide dejar registrado por qué no se concretó un proyecto.
    -- Sin este motivo, meses después nadie recuerda si fue por precio, por
    -- plazos o porque el cliente cambió de planes.
    CONSTRAINT ck_obra_motivo_cancelacion
        CHECK (estado <> 'Cancelada' OR motivo_cancelacion IS NOT NULL),

    -- La fecha estimada de fin no puede ser anterior al inicio real.
    CONSTRAINT ck_obra_fechas
        CHECK (fecha_inicio_real IS NULL
               OR fecha_fin_estimada IS NULL
               OR fecha_fin_estimada >= fecha_inicio_real)
);

-- El listado de obras se filtra por cliente y por estado, y la ficha del
-- cliente trae todas sus obras. Ambas consultas se apoyan en estos índices.
CREATE INDEX idx_obra_cliente ON obra (id_cliente);
CREATE INDEX idx_obra_estado  ON obra (estado);

COMMENT ON TABLE  obra IS 'Proyectos que ejecuta Granica SRL. Entidad núcleo del sistema.';
COMMENT ON COLUMN obra.tipo_obra IS 'Construcción / Reforma. Define si corresponde etapa de anteproyecto.';
COMMENT ON COLUMN obra.estado IS 'En presupuestación / En ejecución / Finalizada / Cancelada.';
COMMENT ON COLUMN obra.motivo_cancelacion IS 'Obligatorio cuando el estado es Cancelada.';
