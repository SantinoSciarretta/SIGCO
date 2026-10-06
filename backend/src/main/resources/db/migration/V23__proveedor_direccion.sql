-- ---------------------------------------------------------------------------
-- V23 — Dirección del proveedor
-- ---------------------------------------------------------------------------
--
-- Pedido de Santino: poder cargar dónde está cada corralón (calle y número,
-- localidad) junto con sus datos de contacto. Sirve para ubicarlo y para ir a
-- retirar material.
--
-- Es opcional, y por eso la columna admite vacío: los proveedores que ya
-- estaban cargados no la tienen, y no todos los corralones con los que se
-- trabaja por teléfono tienen una dirección conocida. El criterio para elegir
-- a quién pedirle sigue siendo la zona de cobertura, que sí es obligatoria.

ALTER TABLE proveedor ADD COLUMN direccion VARCHAR(200);

COMMENT ON COLUMN proveedor.direccion IS
    'Dirección del corralón (calle, número y localidad). Opcional.';
