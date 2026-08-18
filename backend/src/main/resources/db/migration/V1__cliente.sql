-- ============================================================================
--  V1 · Módulo Clientes
--
--  Primera tabla del sistema. Los nombres de tabla y columnas salen tal cual
--  del Diccionario de Datos del informe: la base ES el diccionario.
--
--  Un cliente tiene muchas obras; la clave foránea vive del lado de obra, así
--  que esta tabla no referencia a ninguna otra y puede crearse primero.
-- ============================================================================

CREATE TABLE cliente (
    id_cliente            BIGSERIAL     PRIMARY KEY,

    -- Único campo obligatorio del alta según el informe: el resto de los datos
    -- de contacto no siempre están disponibles cuando llega la consulta.
    nombre_apellido       VARCHAR(150)  NOT NULL,

    telefono_contacto     VARCHAR(30),
    email_contacto        VARCHAR(100),

    -- Todos los clientes de Granica llegan por referido. Guardar de dónde
    -- vienen permite saber qué canal trae más trabajo.
    origen_recomendacion  VARCHAR(20),
    recomendado_por       VARCHAR(150),

    estado                VARCHAR(10)   NOT NULL,
    fecha_alta            TIMESTAMP     NOT NULL,

    -- Los estados y orígenes son conjuntos cerrados. Declararlos como
    -- restricción de la base garantiza que no entre un valor inválido ni
    -- siquiera por una carga manual o un script externo, no solo a través
    -- de la aplicación.
    CONSTRAINT ck_cliente_estado
        CHECK (estado IN ('Activo', 'Inactivo')),

    CONSTRAINT ck_cliente_origen
        CHECK (origen_recomendacion IS NULL
               OR origen_recomendacion IN ('Cliente anterior', 'Arquitecto', 'Otro'))
);

-- La búsqueda del listado es por nombre y sin distinguir mayúsculas. El índice
-- se crea sobre la misma expresión que usa la consulta (LOWER del nombre),
-- porque un índice sobre la columna cruda no serviría para esa búsqueda.
CREATE INDEX idx_cliente_nombre_lower ON cliente (LOWER(nombre_apellido));

COMMENT ON TABLE  cliente IS 'Personas y empresas que contratan a Granica SRL.';
COMMENT ON COLUMN cliente.origen_recomendacion IS 'Cliente anterior / Arquitecto / Otro.';
COMMENT ON COLUMN cliente.recomendado_por IS 'Nombre de quien hizo la recomendación.';
