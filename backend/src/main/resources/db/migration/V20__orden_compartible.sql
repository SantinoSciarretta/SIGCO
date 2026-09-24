-- ---------------------------------------------------------------------------
-- V20 — El link compartible de la orden de pedido
-- ---------------------------------------------------------------------------
--
-- Pedido de Ricardo: que al enviar el pedido se le pueda mandar al corralón por
-- WhatsApp, directamente con el PDF.
--
-- El link de WhatsApp (wa.me) NO puede adjuntar archivos: solo lleva texto. Así
-- que el PDF viaja como un enlace dentro del mensaje, y para eso tiene que
-- poder abrirse SIN estar logueado — el corralón no tiene cuenta en SIGCO ni va
-- a tenerla.
--
-- ---------------------------------------------------------------------------
--  Por qué un token y no el id del pedido
-- ---------------------------------------------------------------------------
--
-- Una URL pública con el id (/ordenes/47) se recorre cambiando el número: quien
-- reciba una orden vería todas las demás, de todas las obras y todos los
-- proveedores. El token es aleatorio y largo, así que no se adivina ni se
-- enumera.
--
-- Igual conviene saber qué se expone: la orden lleva materiales, cantidades,
-- precios acordados y dónde entregar. NO lleva presupuesto, gasto, ganancia ni
-- el nombre del cliente — se diseñó así desde el principio justamente porque es
-- un documento que sale de la empresa hacia afuera.
--
-- ---------------------------------------------------------------------------
--  Por qué vence, y por qué se puede borrar
-- ---------------------------------------------------------------------------
--
-- Un link sin vencimiento queda vivo para siempre en un chat de WhatsApp que se
-- reenvía. Con vencimiento, el día que esa conversación termine en otro lado el
-- link ya no sirve.
--
-- Y al ser una columna y no un token firmado, se puede ANULAR: poner el token
-- en NULL corta el acceso al instante. Con un token firmado habría que esperar
-- a que venza. Si el pedido se le mandó al corralón equivocado, esperar no es
-- una opción.

ALTER TABLE pedido
    ADD COLUMN token_orden VARCHAR(64),
    ADD COLUMN token_orden_vence TIMESTAMP;

-- Único para que dos pedidos no puedan compartir token. Parcial porque la
-- enorme mayoría de los pedidos no tiene link generado, y NULL no choca contra
-- NULL en un índice único de todos modos: el WHERE lo deja explícito y hace el
-- índice mucho más chico.
CREATE UNIQUE INDEX ux_pedido_token_orden
    ON pedido (token_orden)
    WHERE token_orden IS NOT NULL;

COMMENT ON COLUMN pedido.token_orden IS
    'Token aleatorio del link público de la orden. NULL = no hay link vigente.';
COMMENT ON COLUMN pedido.token_orden_vence IS
    'Hasta cuándo sirve el link. Pasada esa fecha, el PDF deja de abrirse.';
