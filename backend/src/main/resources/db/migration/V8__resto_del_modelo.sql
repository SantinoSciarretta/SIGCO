-- ============================================================================
--  V8 — Las 18 tablas restantes del Diccionario de Datos
-- ============================================================================
--
--  POR QUÉ ESTA MIGRACIÓN EXISTE
--
--  Hasta acá cada módulo creaba su propia tabla al desarrollarse (V1 a V7). Esta
--  migración adelanta el resto del modelo relacional para poder mostrar el
--  diagrama entidad-relación COMPLETO, que es un entregable de la cátedra.
--
--  No cambia el orden de desarrollo: los módulos se siguen construyendo uno por
--  uno. Lo que cambia es que la estructura ya está, y cada módulo va a encontrar
--  su tabla creada en lugar de crearla.
--
--  Las definiciones salen del Diccionario de Datos del informe, sin desvíos:
--  mismos nombres, mismos tipos, mismas claves.
--
--  SOBRE LAS CLAVES FORÁNEAS HACIA usuario
--
--  Seis columnas referencian a usuario (gasto.id_usuario_registro,
--  pedido.id_usuario_solicita, pedido.id_usuario_recibe, hito.id_usuario_completa,
--  inasistencia.id_usuario_registro, registro_auditoria.id_usuario). Como el
--  módulo Usuarios va último, quedan NULLABLE salvo en registro_auditoria: los
--  módulos anteriores se van a probar sin sesión iniciada y no hay a quién
--  atribuir la acción todavía.
--  TODO: al integrar Usuarios/Accesos, evaluar pasarlas a NOT NULL.
--
--  Los CHECK de conjunto cerrado y las reglas condicionales (por ejemplo, que
--  una cuota abonada tenga fecha de pago) se agregan acá porque son parte del
--  modelo: son las mismas reglas que el informe define para cada módulo.
-- ============================================================================


-- ============================================================================
--  Módulo Accesos
-- ============================================================================

CREATE TABLE rol (
    id_rol      BIGSERIAL     PRIMARY KEY,
    nombre_rol  VARCHAR(50)   NOT NULL,
    descripcion VARCHAR(200)
);

-- Igual que en rubro y proveedor: un UNIQUE común distingue mayúsculas y
-- dejaría entrar "Dueño" y "dueño" como roles distintos.
CREATE UNIQUE INDEX ux_rol_nombre_lower ON rol (LOWER(nombre_rol));


CREATE TABLE permiso (
    id_permiso     BIGSERIAL     PRIMARY KEY,
    nombre_permiso VARCHAR(100)  NOT NULL,
    modulo         VARCHAR(50)   NOT NULL,
    descripcion    VARCHAR(200)
);

CREATE UNIQUE INDEX ux_permiso_nombre_lower ON permiso (LOWER(nombre_permiso));
CREATE INDEX idx_permiso_modulo ON permiso (modulo);


-- Intermedia N:M. La clave primaria compuesta impide asignar dos veces el mismo
-- permiso al mismo rol sin necesidad de un control aparte.
CREATE TABLE rol_permiso (
    id_rol     BIGINT NOT NULL,
    id_permiso BIGINT NOT NULL,

    CONSTRAINT pk_rol_permiso PRIMARY KEY (id_rol, id_permiso),
    CONSTRAINT fk_rol_permiso_rol
        FOREIGN KEY (id_rol) REFERENCES rol (id_rol),
    CONSTRAINT fk_rol_permiso_permiso
        FOREIGN KEY (id_permiso) REFERENCES permiso (id_permiso)
);


-- ============================================================================
--  Módulo Personal — operario va antes que usuario, porque usuario lo referencia
-- ============================================================================

CREATE TABLE operario (
    id_operario       BIGSERIAL     PRIMARY KEY,
    nombre_apellido   VARCHAR(150)  NOT NULL,
    telefono_contacto VARCHAR(30),
    estado            VARCHAR(10)   NOT NULL,
    fecha_alta        TIMESTAMP     NOT NULL,

    CONSTRAINT ck_operario_estado
        CHECK (estado IN ('Activo', 'Inactivo'))
);


-- ============================================================================
--  Módulo Usuarios
-- ============================================================================

CREATE TABLE usuario (
    id_usuario          BIGSERIAL     PRIMARY KEY,
    nombre_usuario      VARCHAR(50)   NOT NULL,
    -- Hash BCrypt, nunca la contraseña en texto plano.
    contrasena_hash     VARCHAR(255)  NOT NULL,
    id_rol              BIGINT        NOT NULL,
    -- Vínculo opcional: no todo usuario es un operario (el dueño no lo es).
    id_operario         BIGINT,
    estado              VARCHAR(10)   NOT NULL,
    ultima_fecha_acceso TIMESTAMP,
    fecha_alta          TIMESTAMP     NOT NULL,

    CONSTRAINT fk_usuario_rol
        FOREIGN KEY (id_rol) REFERENCES rol (id_rol),
    CONSTRAINT fk_usuario_operario
        FOREIGN KEY (id_operario) REFERENCES operario (id_operario),
    CONSTRAINT ck_usuario_estado
        CHECK (estado IN ('Activo', 'Inactivo'))
);

CREATE UNIQUE INDEX ux_usuario_nombre_lower ON usuario (LOWER(nombre_usuario));
-- Un registro de personal no puede tener dos cuentas de acceso.
CREATE UNIQUE INDEX ux_usuario_operario
    ON usuario (id_operario) WHERE id_operario IS NOT NULL;


CREATE TABLE registro_auditoria (
    id_auditoria     BIGSERIAL     PRIMARY KEY,
    -- Acá sí es obligatorio: una auditoría sin autor no sirve para nada.
    id_usuario       BIGINT        NOT NULL,
    accion_realizada VARCHAR(150)  NOT NULL,
    modulo_afectado  VARCHAR(50)   NOT NULL,
    fecha_hora       TIMESTAMP     NOT NULL,

    CONSTRAINT fk_auditoria_usuario
        FOREIGN KEY (id_usuario) REFERENCES usuario (id_usuario)
);

CREATE INDEX idx_auditoria_usuario ON registro_auditoria (id_usuario);
CREATE INDEX idx_auditoria_fecha   ON registro_auditoria (fecha_hora);


-- ============================================================================
--  Módulo Personal (continuación)
-- ============================================================================

-- Intermedia N:M entre operario y obra.
CREATE TABLE operario_obra (
    id_operario      BIGINT NOT NULL,
    id_obra          BIGINT NOT NULL,
    fecha_asignacion DATE   NOT NULL,

    CONSTRAINT pk_operario_obra PRIMARY KEY (id_operario, id_obra),
    CONSTRAINT fk_operario_obra_operario
        FOREIGN KEY (id_operario) REFERENCES operario (id_operario),
    CONSTRAINT fk_operario_obra_obra
        FOREIGN KEY (id_obra) REFERENCES obra (id_obra)
);


CREATE TABLE inasistencia (
    id_inasistencia     BIGSERIAL     PRIMARY KEY,
    id_operario         BIGINT        NOT NULL,
    -- No se registra una inasistencia sin indicar la obra: es regla del informe.
    id_obra             BIGINT        NOT NULL,
    fecha_falta         DATE          NOT NULL,
    -- El motivo NO es obligatorio, lo aclara el informe.
    motivo              VARCHAR(200),
    id_usuario_registro BIGINT,

    CONSTRAINT fk_inasistencia_operario
        FOREIGN KEY (id_operario) REFERENCES operario (id_operario),
    CONSTRAINT fk_inasistencia_obra
        FOREIGN KEY (id_obra) REFERENCES obra (id_obra),
    CONSTRAINT fk_inasistencia_usuario
        FOREIGN KEY (id_usuario_registro) REFERENCES usuario (id_usuario)
);

-- Regla del informe: no dos inasistencias del mismo operario, misma fecha y obra.
-- Va como restricción de la base y no solo como validación en el servicio,
-- porque es la única forma de garantizarla ante dos cargas simultáneas.
CREATE UNIQUE INDEX ux_inasistencia_operario_obra_fecha
    ON inasistencia (id_operario, id_obra, fecha_falta);


-- ============================================================================
--  Módulo Compras
-- ============================================================================

CREATE TABLE pedido (
    id_pedido           BIGSERIAL     PRIMARY KEY,
    id_obra             BIGINT        NOT NULL,
    id_proveedor        BIGINT        NOT NULL,
    id_usuario_solicita BIGINT,
    id_usuario_recibe   BIGINT,
    estado              VARCHAR(25)   NOT NULL,
    -- Referencia al archivo en Supabase Storage, nunca el binario.
    foto_remito         VARCHAR(255),
    nota_diferencia     VARCHAR(300),
    fecha_solicitud     TIMESTAMP     NOT NULL,
    -- Se completan al avanzar el circuito, por eso son nulas al crear.
    fecha_aprobacion    TIMESTAMP,
    fecha_recepcion     TIMESTAMP,

    CONSTRAINT fk_pedido_obra
        FOREIGN KEY (id_obra) REFERENCES obra (id_obra),
    CONSTRAINT fk_pedido_proveedor
        FOREIGN KEY (id_proveedor) REFERENCES proveedor (id_proveedor),
    CONSTRAINT fk_pedido_usuario_solicita
        FOREIGN KEY (id_usuario_solicita) REFERENCES usuario (id_usuario),
    CONSTRAINT fk_pedido_usuario_recibe
        FOREIGN KEY (id_usuario_recibe) REFERENCES usuario (id_usuario),

    CONSTRAINT ck_pedido_estado
        CHECK (estado IN ('Pendiente de Aprobación', 'Enviado al Proveedor',
                          'Recibido Completo', 'Recibido con Diferencias', 'Anulado')),

    -- Regla del informe: si se recibió con diferencias, hay que decir cuáles.
    CONSTRAINT ck_pedido_nota_diferencia
        CHECK (estado <> 'Recibido con Diferencias' OR nota_diferencia IS NOT NULL)
);

CREATE INDEX idx_pedido_obra      ON pedido (id_obra);
CREATE INDEX idx_pedido_proveedor ON pedido (id_proveedor);
CREATE INDEX idx_pedido_estado    ON pedido (estado);


-- Intermedia N:M entre pedido y material, con la cantidad pedida.
CREATE TABLE pedido_material (
    id_pedido   BIGINT        NOT NULL,
    id_material BIGINT        NOT NULL,
    cantidad    NUMERIC(12,2) NOT NULL,

    CONSTRAINT pk_pedido_material PRIMARY KEY (id_pedido, id_material),
    CONSTRAINT fk_pedido_material_pedido
        FOREIGN KEY (id_pedido) REFERENCES pedido (id_pedido),
    CONSTRAINT fk_pedido_material_material
        FOREIGN KEY (id_material) REFERENCES material (id_material),
    CONSTRAINT ck_pedido_material_cantidad
        CHECK (cantidad > 0)
);


-- La clave foránea que quedó pendiente en V6: la tabla pedido no existía
-- todavía cuando se creó observacion_proveedor.
ALTER TABLE observacion_proveedor
    ADD CONSTRAINT fk_observacion_pedido
        FOREIGN KEY (id_pedido) REFERENCES pedido (id_pedido);


-- ============================================================================
--  Módulo Gastos
-- ============================================================================

CREATE TABLE gasto (
    id_gasto            BIGSERIAL     PRIMARY KEY,
    id_obra             BIGINT        NOT NULL,
    -- Rubro obligatorio: sin él no se puede comparar contra lo presupuestado,
    -- que es la razón de ser del módulo.
    id_rubro            BIGINT        NOT NULL,
    id_subrubro         BIGINT,
    tipo_gasto          VARCHAR(15)   NOT NULL,
    monto               NUMERIC(14,2) NOT NULL,
    -- Cuándo ocurrió el gasto, distinto de cuándo se cargó al sistema.
    fecha_gasto         DATE          NOT NULL,
    fecha_carga         TIMESTAMP     NOT NULL,
    -- Si el gasto vino de un pedido de materiales, queda vinculado.
    id_pedido           BIGINT,
    id_operario         BIGINT,
    descripcion         VARCHAR(250),
    comprobante_adjunto VARCHAR(255),
    id_usuario_registro BIGINT,
    estado              VARCHAR(12)   NOT NULL,
    motivo_anulacion    VARCHAR(200),

    CONSTRAINT fk_gasto_obra
        FOREIGN KEY (id_obra) REFERENCES obra (id_obra),
    CONSTRAINT fk_gasto_rubro
        FOREIGN KEY (id_rubro) REFERENCES rubro (id_rubro),
    CONSTRAINT fk_gasto_subrubro
        FOREIGN KEY (id_subrubro) REFERENCES subrubro (id_subrubro),
    CONSTRAINT fk_gasto_pedido
        FOREIGN KEY (id_pedido) REFERENCES pedido (id_pedido),
    CONSTRAINT fk_gasto_operario
        FOREIGN KEY (id_operario) REFERENCES operario (id_operario),
    CONSTRAINT fk_gasto_usuario
        FOREIGN KEY (id_usuario_registro) REFERENCES usuario (id_usuario),

    CONSTRAINT ck_gasto_monto
        CHECK (monto > 0),
    CONSTRAINT ck_gasto_estado
        CHECK (estado IN ('Registrado', 'Anulado')),
    -- Mismo criterio que en obra: no se anula sin dejar constancia de por qué.
    CONSTRAINT ck_gasto_motivo_anulacion
        CHECK (estado <> 'Anulado' OR motivo_anulacion IS NOT NULL)
);

-- El índice compuesto sirve a la consulta central del módulo: cuánto se lleva
-- gastado por rubro en una obra, que es la mitad del semáforo de desvío.
CREATE INDEX idx_gasto_obra_rubro ON gasto (id_obra, id_rubro);
CREATE INDEX idx_gasto_fecha      ON gasto (fecha_gasto);
CREATE INDEX idx_gasto_tipo       ON gasto (tipo_gasto);


-- ============================================================================
--  Módulo Seguimiento de Obras
-- ============================================================================

CREATE TABLE hito (
    id_hito             BIGSERIAL     PRIMARY KEY,
    id_obra             BIGINT        NOT NULL,
    nombre_hito         VARCHAR(150)  NOT NULL,
    -- Peso del hito en el avance total de la obra.
    ponderacion         NUMERIC(5,2)  NOT NULL,
    orden               INTEGER       NOT NULL,
    estado              VARCHAR(12)   NOT NULL,
    fecha_cumplimiento  DATE,
    observacion         VARCHAR(250),
    id_usuario_completa BIGINT,

    CONSTRAINT fk_hito_obra
        FOREIGN KEY (id_obra) REFERENCES obra (id_obra),
    CONSTRAINT fk_hito_usuario
        FOREIGN KEY (id_usuario_completa) REFERENCES usuario (id_usuario),

    CONSTRAINT ck_hito_ponderacion
        CHECK (ponderacion > 0 AND ponderacion <= 100),
    CONSTRAINT ck_hito_estado
        CHECK (estado IN ('Pendiente', 'En curso', 'Completado')),
    -- Regla del informe: la fecha de cumplimiento es obligatoria al completar.
    CONSTRAINT ck_hito_fecha_cumplimiento
        CHECK (estado <> 'Completado' OR fecha_cumplimiento IS NOT NULL)
);

CREATE INDEX idx_hito_obra ON hito (id_obra, orden);


CREATE TABLE plantilla_hito (
    id_plantilla     BIGSERIAL     PRIMARY KEY,
    nombre_plantilla VARCHAR(100)  NOT NULL,
    fecha_creacion   TIMESTAMP     NOT NULL
);

CREATE UNIQUE INDEX ux_plantilla_hito_nombre_lower
    ON plantilla_hito (LOWER(nombre_plantilla));


CREATE TABLE plantilla_hito_detalle (
    id_detalle   BIGSERIAL     PRIMARY KEY,
    id_plantilla BIGINT        NOT NULL,
    nombre_hito  VARCHAR(150)  NOT NULL,
    ponderacion  NUMERIC(5,2)  NOT NULL,
    orden        INTEGER       NOT NULL,

    CONSTRAINT fk_plantilla_detalle
        FOREIGN KEY (id_plantilla) REFERENCES plantilla_hito (id_plantilla),
    CONSTRAINT ck_plantilla_detalle_ponderacion
        CHECK (ponderacion > 0 AND ponderacion <= 100)
);

CREATE INDEX idx_plantilla_detalle ON plantilla_hito_detalle (id_plantilla, orden);


-- ============================================================================
--  Módulo Cobros
-- ============================================================================

CREATE TABLE cuota (
    id_cuota            BIGSERIAL     PRIMARY KEY,
    id_obra             BIGINT        NOT NULL,
    -- El anticipo es la cuota cero: por eso el mínimo es 0 y no 1.
    numero_cuota        INTEGER       NOT NULL,
    monto_cuota         NUMERIC(14,2) NOT NULL,
    fecha_vencimiento   DATE          NOT NULL,
    estado              VARCHAR(12)   NOT NULL,
    fecha_pago          DATE,
    medio_pago          VARCHAR(15),
    comprobante_emitido VARCHAR(20),

    CONSTRAINT fk_cuota_obra
        FOREIGN KEY (id_obra) REFERENCES obra (id_obra),

    CONSTRAINT ck_cuota_numero
        CHECK (numero_cuota >= 0),
    CONSTRAINT ck_cuota_monto
        CHECK (monto_cuota > 0),
    CONSTRAINT ck_cuota_estado
        CHECK (estado IN ('Pendiente', 'Abonada', 'Vencida')),
    -- Regla del informe: la fecha de pago es obligatoria al marcar abonada.
    CONSTRAINT ck_cuota_fecha_pago
        CHECK (estado <> 'Abonada' OR fecha_pago IS NOT NULL)
);

-- Una obra no puede tener dos veces la misma cuota.
CREATE UNIQUE INDEX ux_cuota_obra_numero ON cuota (id_obra, numero_cuota);
CREATE INDEX idx_cuota_vencimiento ON cuota (fecha_vencimiento);
CREATE INDEX idx_cuota_estado      ON cuota (estado);


CREATE TABLE registro_cac (
    id_cac              BIGSERIAL     PRIMARY KEY,
    -- Se guarda el primer día del mes; el índice es mensual.
    mes_correspondiente DATE          NOT NULL,
    valor_indice        NUMERIC(8,4)  NOT NULL,
    fecha_carga         TIMESTAMP     NOT NULL,

    CONSTRAINT ck_cac_valor
        CHECK (valor_indice > 0)
);

-- Un solo valor de índice por mes: cargarlo dos veces daría dos actualizaciones
-- distintas del mismo saldo.
CREATE UNIQUE INDEX ux_cac_mes ON registro_cac (mes_correspondiente);


-- ============================================================================
--  Módulo Portfolio Web
-- ============================================================================

CREATE TABLE publicacion_portfolio (
    id_publicacion    BIGSERIAL     PRIMARY KEY,
    id_obra           BIGINT        NOT NULL,
    tipo_trabajo      VARCHAR(30)   NOT NULL,
    estado            VARCHAR(15)   NOT NULL,
    fecha_publicacion TIMESTAMP     NOT NULL,

    CONSTRAINT fk_publicacion_obra
        FOREIGN KEY (id_obra) REFERENCES obra (id_obra),
    CONSTRAINT ck_publicacion_estado
        CHECK (estado IN ('Borrador', 'Publicada', 'Oculta'))
);

-- Una obra se publica una sola vez en el portfolio.
CREATE UNIQUE INDEX ux_publicacion_obra ON publicacion_portfolio (id_obra);


CREATE TABLE imagen_portfolio (
    id_imagen      BIGSERIAL     PRIMARY KEY,
    id_publicacion BIGINT        NOT NULL,
    -- URL en Supabase Storage. La base guarda la referencia, nunca el binario.
    url_imagen     VARCHAR(255)  NOT NULL,
    orden          INTEGER       NOT NULL,

    CONSTRAINT fk_imagen_publicacion
        FOREIGN KEY (id_publicacion) REFERENCES publicacion_portfolio (id_publicacion),
    CONSTRAINT ck_imagen_orden
        CHECK (orden >= 0)
);

CREATE INDEX idx_imagen_publicacion ON imagen_portfolio (id_publicacion, orden);
