-- =============================================================================
--  V15 — Version de sesion: poder cortar una sesion a distancia
--
--  Cierra dos huecos que quedaron al agregar la renovacion deslizante del token
--  en V14 y que aparecieron en la auditoria previa a la entrega:
--
--    1. Un token robado, usado cada tanto, se renovaba indefinidamente. Antes
--       de la renovacion vencia a las ocho horas si o si; despues, nunca.
--    2. No habia forma de cerrar una sesion ya abierta. Si a Ricardo le robaban
--       el telefono, la unica salida era dar de baja la cuenta entera.
--
--  El tope absoluto de la sesion NO necesita base: viaja dentro del token, como
--  el instante del ingreso. Lo que si necesita base es esto: un numero que
--  permita invalidar de golpe los tokens ya emitidos.
-- =============================================================================


-- -----------------------------------------------------------------------------
--  Version de sesion
--
--  Un contador que se incrementa cada vez que hay que dejar sin efecto las
--  sesiones abiertas de esa cuenta. El numero viaja dentro del token, y
--  FiltroJwt lo compara contra este valor en cada peticion: si no coinciden, el
--  token es de antes del corte y no vale mas.
--
--  Por que un contador y no una lista de tokens revocados: una lista hay que
--  guardarla, consultarla y limpiarla, y crece sin limite. Este numero se
--  compara contra el usuario que el filtro YA consulta en cada peticion —lo
--  hace desde el modulo Accesos, para que un permiso revocado se aplique en el
--  acto—, asi que no agrega ni una consulta.
--
--  Arranca en 1 y no en 0 para que un token sin el claim (emitido antes de esta
--  migracion) no coincida por defecto: esos tokens tienen que caducar.
-- -----------------------------------------------------------------------------
ALTER TABLE usuario ADD COLUMN version_sesion INTEGER NOT NULL DEFAULT 1;


-- -----------------------------------------------------------------------------
--  Los tokens emitidos antes de esta migracion dejan de valer
--
--  Se incrementa el contador de todas las cuentas existentes. Es deliberado:
--  cualquier token que ande dando vueltas se emitio sin el claim de version y
--  sin el del instante de ingreso, asi que no tiene tope y no se puede cortar.
--  Obligar a volver a entrar una vez es el precio de cerrar el hueco.
-- -----------------------------------------------------------------------------
UPDATE usuario SET version_sesion = version_sesion + 1;
