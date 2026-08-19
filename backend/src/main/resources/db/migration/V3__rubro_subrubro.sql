-- ============================================================================
--  V3 · Catalogo de rubros y subrubros (modulo Presupuestacion)
--
--  Es el paso 1 del circuito de Presupuestacion, y el informe aclara que no se
--  repite por cada obra: el catalogo se mantiene una vez y se reutiliza.
--
--  Lo consumen tambien Materiales (cada material pertenece a un rubro) y
--  Gastos (cada gasto se clasifica por rubro y subrubro), asi que es la
--  clasificacion compartida de todo el sistema. Que exista una sola lista es
--  lo que permite comparar lo gastado contra lo presupuestado: si cada modulo
--  tuviera la suya, los nombres no coincidirian y la comparacion no cerraria.
-- ============================================================================

CREATE TABLE rubro (
    id_rubro      BIGSERIAL     PRIMARY KEY,
    nombre_rubro  VARCHAR(100)  NOT NULL,
    estado        VARCHAR(10)   NOT NULL,

    CONSTRAINT ck_rubro_estado
        CHECK (estado IN ('Activo', 'Inactivo'))
);

-- El informe prohibe dos rubros con el mismo nombre, para no terminar con el
-- gasto de un mismo trabajo repartido entre dos etiquetas distintas.
--
-- El indice es sobre LOWER(nombre) y no sobre la columna: un UNIQUE comun
-- distingue mayusculas, asi que dejaria pasar "Albanileria" y "albanileria"
-- como si fueran rubros diferentes.
CREATE UNIQUE INDEX ux_rubro_nombre_lower ON rubro (LOWER(nombre_rubro));


CREATE TABLE subrubro (
    id_subrubro      BIGSERIAL     PRIMARY KEY,

    -- Un subrubro pertenece a un unico rubro. La clave foranea NOT NULL es lo
    -- que hace cumplir esa regla del informe fuera de la aplicacion tambien.
    id_rubro         BIGINT        NOT NULL,

    nombre_subrubro  VARCHAR(100)  NOT NULL,
    estado           VARCHAR(10)   NOT NULL,

    CONSTRAINT fk_subrubro_rubro
        FOREIGN KEY (id_rubro) REFERENCES rubro (id_rubro),

    CONSTRAINT ck_subrubro_estado
        CHECK (estado IN ('Activo', 'Inactivo'))
);

-- El nombre del subrubro es unico DENTRO de su rubro, no en todo el sistema:
-- "Demolicion" puede existir en Albanileria y tambien en Plomeria, y son
-- trabajos distintos. Lo que no puede haber es dos "Demolicion" dentro del
-- mismo rubro.
CREATE UNIQUE INDEX ux_subrubro_nombre_por_rubro
    ON subrubro (id_rubro, LOWER(nombre_subrubro));

CREATE INDEX idx_subrubro_rubro ON subrubro (id_rubro);

COMMENT ON TABLE  rubro    IS 'Clasificacion de trabajos usada por Presupuestacion, Gastos y Materiales.';
COMMENT ON TABLE  subrubro IS 'Subdivision de un rubro. Pertenece a un unico rubro.';
