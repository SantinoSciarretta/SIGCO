-- =============================================================================
--  V13 — Usuarios y Accesos
--
--  Las cinco tablas (rol, permiso, rol_permiso, usuario, registro_auditoria) ya
--  existen desde V8. Esta migracion NO crea estructura: carga los datos con los
--  que el sistema arranca, y agrega la unica columna que faltaba.
--
--  Los roles y los permisos son datos de configuracion, no datos de operacion:
--  no los carga nadie desde una pantalla, vienen definidos con el sistema. Por
--  eso van en una migracion y no en un formulario.
-- =============================================================================


-- -----------------------------------------------------------------------------
--  1. Motivo de la baja de una cuenta
--
--  El informe dice que una cuenta no se elimina, se desactiva. No decia donde
--  anotar por que, y sin eso "Inactivo" no distingue una baja por renuncia de
--  una suspension por seguridad. Mismo criterio que en obra, gasto, pedido y
--  cuota.
-- -----------------------------------------------------------------------------
ALTER TABLE usuario ADD COLUMN motivo_baja VARCHAR(200);

ALTER TABLE usuario ADD CONSTRAINT ck_usuario_motivo_baja
    CHECK (estado <> 'Inactivo' OR motivo_baja IS NOT NULL);


-- -----------------------------------------------------------------------------
--  2. Roles
--
--  Son los tres del informe, ni uno mas. No hay pantalla para crear roles: un
--  rol nuevo implica decidir que puede hacer, y eso es una decision de diseño
--  del sistema, no una carga de datos.
-- -----------------------------------------------------------------------------
INSERT INTO rol (nombre_rol, descripcion) VALUES
    ('Dueño',           'Acceso total. Unico que aprueba presupuestos y pedidos, y el unico que ve la informacion financiera completa.'),
    ('Capataz General', 'Sigue todas las obras activas: marca hitos y genera pedidos. No ve informacion financiera ni presupuestos.'),
    ('Capataz de Obra', 'Solo su obra asignada: genera pedidos y confirma la recepcion de materiales desde el celular.');


-- -----------------------------------------------------------------------------
--  3. Permisos
--
--  Un permiso por modulo y accion: <modulo>.ver y <modulo>.editar.
--
--  La matriz del informe tiene tres niveles —Total, Consulta y sin acceso— y
--  se representan con dos permisos: Total es ver+editar, Consulta es solo ver,
--  y sin acceso es no tener ninguno de los dos. Un tercer permiso "total" seria
--  redundante y habria que mantener la coherencia entre los tres a mano.
-- -----------------------------------------------------------------------------
INSERT INTO permiso (nombre_permiso, modulo, descripcion) VALUES
    ('tablero.ver',           'Tablero',          'Ver el tablero consolidado'),
    ('obras.ver',             'Obras',            'Consultar obras'),
    ('obras.editar',          'Obras',            'Crear y modificar obras'),
    ('clientes.ver',          'Clientes',         'Consultar clientes'),
    ('clientes.editar',       'Clientes',         'Crear y modificar clientes'),
    ('presupuestos.ver',      'Presupuestacion',  'Consultar presupuestos'),
    ('presupuestos.editar',   'Presupuestacion',  'Crear, modificar y aprobar presupuestos'),
    ('materiales.ver',        'Materiales',       'Consultar el catalogo de materiales'),
    ('materiales.editar',     'Materiales',       'Administrar el catalogo de materiales'),
    ('proveedores.ver',       'Proveedores',      'Consultar proveedores y cotizaciones'),
    ('proveedores.editar',    'Proveedores',      'Administrar proveedores y cotizaciones'),
    ('compras.ver',           'Compras',          'Consultar pedidos de materiales'),
    ('compras.editar',        'Compras',          'Generar pedidos y confirmar recepciones'),
    ('compras.aprobar',       'Compras',          'Aprobar un pedido y enviarlo al proveedor'),
    ('gastos.ver',            'Gastos',           'Consultar gastos y desvios'),
    ('gastos.editar',         'Gastos',           'Registrar y anular gastos'),
    ('personal.ver',          'Personal',         'Consultar operarios e inasistencias'),
    ('personal.editar',       'Personal',         'Administrar operarios, asignaciones e inasistencias'),
    ('seguimiento.ver',       'Seguimiento',      'Consultar el avance de las obras'),
    ('seguimiento.editar',    'Seguimiento',      'Definir hitos y marcarlos como completados'),
    ('cobros.ver',            'Cobros',           'Consultar planes de cobro y saldos'),
    ('cobros.editar',         'Cobros',           'Generar planes, registrar pagos y aplicar el CAC'),
    ('portfolio.ver',         'Portfolio',        'Consultar el portfolio'),
    ('portfolio.editar',      'Portfolio',        'Administrar publicaciones e imagenes'),
    ('usuarios.ver',          'Usuarios',         'Consultar cuentas de acceso'),
    ('usuarios.editar',       'Usuarios',         'Crear cuentas, cambiar contrasenas y dar de baja'),
    ('accesos.ver',           'Accesos',          'Consultar roles, permisos y auditoria'),
    ('accesos.editar',        'Accesos',          'Modificar los permisos de cada rol');


-- -----------------------------------------------------------------------------
--  4. Que puede hacer cada rol
--
--  Transcripcion directa de la matriz del informe (seccion "Roles y matriz de
--  permisos"). Se escribe con SELECT y no con ids fijos porque los ids los
--  genera la base.
-- -----------------------------------------------------------------------------

-- El dueño puede todo. Es el unico rol al que se le asignan todos los permisos
-- de una, porque cualquier permiso nuevo que se agregue tiene que alcanzarlo.
INSERT INTO rol_permiso (id_rol, id_permiso)
SELECT r.id_rol, p.id_permiso
FROM rol r, permiso p
WHERE r.nombre_rol = 'Dueño';

-- Capataz General: ve las obras y el personal, y trabaja en compras y avance.
-- NO ve presupuestos, cobros ni clientes: es la linea que separa lo operativo
-- de lo financiero, y es lo que permite delegar sin abrir los numeros.
INSERT INTO rol_permiso (id_rol, id_permiso)
SELECT r.id_rol, p.id_permiso
FROM rol r, permiso p
WHERE r.nombre_rol = 'Capataz General'
  AND p.nombre_permiso IN (
      'tablero.ver',
      'obras.ver',
      'personal.ver',
      'proveedores.ver',
      'materiales.ver',
      'gastos.ver',
      'compras.ver', 'compras.editar',
      'seguimiento.ver', 'seguimiento.editar');

-- Capataz de Obra: el mas restringido. Genera pedidos y confirma recepciones
-- desde el celular, y consulta el avance de su obra. Nada mas.
INSERT INTO rol_permiso (id_rol, id_permiso)
SELECT r.id_rol, p.id_permiso
FROM rol r, permiso p
WHERE r.nombre_rol = 'Capataz de Obra'
  AND p.nombre_permiso IN (
      'obras.ver',
      'materiales.ver',
      'compras.ver', 'compras.editar',
      'seguimiento.ver');

-- Ningun capataz tiene 'compras.aprobar': la aprobacion del pedido es
-- indelegable y es el permiso que lo hace cumplir. Es regla textual del
-- informe, y ahora esta en la base y no solo en el codigo.


-- -----------------------------------------------------------------------------
--  5. Cuenta inicial del dueño
--
--  Sin esta cuenta el sistema queda inaccesible: hace falta estar autenticado
--  para administrar usuarios, y para eso hay que poder entrar.
--
--  La contrasena viaja como hash BCrypt, nunca en texto plano — ni siquiera en
--  la migracion. El hash corresponde a "granica2026", que es una contrasena de
--  puesta en marcha y DEBE cambiarse desde la pantalla de Usuarios en el primer
--  ingreso.
-- -----------------------------------------------------------------------------
INSERT INTO usuario (nombre_usuario, contrasena_hash, id_rol, estado, fecha_alta)
SELECT 'ricardo',
       '$2a$10$tkjCddnDQUxZlBCs/MDcQOQU1lyZ3bDLrDbP0u2CGEJnVZGr4Ga1m',
       r.id_rol,
       'Activo',
       CURRENT_TIMESTAMP
FROM rol r
WHERE r.nombre_rol = 'Dueño';


-- -----------------------------------------------------------------------------
--  6. Indices de la auditoria
--
--  La auditoria se consulta siempre por fecha (que paso ultimamente) o por
--  usuario (que hizo esta persona). Sin indices, esas dos consultas recorren
--  toda la tabla, que es la que mas crece del sistema.
-- -----------------------------------------------------------------------------
CREATE INDEX ix_auditoria_fecha   ON registro_auditoria (fecha_hora DESC);
CREATE INDEX ix_auditoria_usuario ON registro_auditoria (id_usuario, fecha_hora DESC);
