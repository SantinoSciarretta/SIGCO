-- =============================================================================
--  V14 — Endurecimiento del ingreso
--
--  Dos defensas que el informe no pedia y que el sistema necesita antes de
--  quedar publicado en internet:
--
--    1. Limite de intentos fallidos. Sin esto, nada impide probar contrasenas
--       una tras otra contra /api/sesion hasta acertar.
--    2. Cambio obligatorio de contrasena en el primer ingreso. La cuenta que
--       crea V13 tiene la contrasena escrita en el repositorio: cualquiera que
--       vea el codigo la conoce.
--
--  Las tres columnas van en "usuario" y no en una tabla aparte porque son
--  atributos de la cuenta, no hechos historicos: interesa el estado actual, no
--  la lista de cada intento. Quien quiera la historia la tiene en
--  registro_auditoria.
-- =============================================================================


-- -----------------------------------------------------------------------------
--  1. Intentos fallidos consecutivos
--
--  Se guarda en la base y no en memoria a proposito. El backend es stateless
--  —lo dice ServicioJwt— para que Railway pueda reiniciarlo o correr dos copias
--  del servicio. Un contador en memoria se perderia en cada reinicio, y
--  reiniciar pasaria a ser la forma de saltear el bloqueo. Con dos copias
--  corriendo, cada una contaria por su lado y el limite real seria el doble.
--
--  El contador es CONSECUTIVO: un ingreso correcto lo vuelve a cero. No es un
--  total historico.
-- -----------------------------------------------------------------------------
ALTER TABLE usuario ADD COLUMN intentos_fallidos INTEGER NOT NULL DEFAULT 0;


-- -----------------------------------------------------------------------------
--  2. Hasta cuando esta bloqueada la cuenta
--
--  NULL significa "no bloqueada", que es el caso normal. Se guarda el instante
--  en que se libera y no un booleano "bloqueado" porque el bloqueo se vence
--  solo: con un booleano haria falta algo que corra cada tanto para
--  desbloquear, y ese algo puede no correr. Con una fecha, la cuenta se libera
--  sin que nadie haga nada — basta comparar contra el reloj.
-- -----------------------------------------------------------------------------
ALTER TABLE usuario ADD COLUMN bloqueado_hasta TIMESTAMP;


-- -----------------------------------------------------------------------------
--  3. Obligacion de cambiar la contrasena
--
--  Se activa en dos momentos: cuando el dueño crea una cuenta (el la elige, asi
--  que la conoce) y cuando el dueño resetea la de otro. En ambos casos hay una
--  contrasena que dos personas conocen, y eso deja de ser una contrasena.
--
--  Mientras esta en TRUE el backend rechaza toda peticion que no sea cambiarla.
--  No alcanza con que el frontend muestre la pantalla: la validacion que manda
--  es la del servidor.
-- -----------------------------------------------------------------------------
ALTER TABLE usuario ADD COLUMN debe_cambiar_contrasena BOOLEAN NOT NULL DEFAULT FALSE;


-- -----------------------------------------------------------------------------
--  4. La cuenta inicial queda obligada a cambiarla
--
--  V13 crea 'ricardo' con la contrasena 'granica2026' escrita en el archivo de
--  migracion, que esta versionado en GitHub. Es correcto para poner el sistema
--  en marcha —alguien tiene que poder entrar la primera vez— pero deja de serlo
--  apenas el sistema es alcanzable desde internet.
--
--  Esta linea convierte la recomendacion de "acordate de cambiarla" en algo que
--  el sistema exige. Se aplica a cualquier cuenta que exista al momento de
--  correr esta migracion, no solo a 'ricardo': las de prueba creadas a mano
--  (martin, jorge) tienen contrasenas igual de conocidas.
-- -----------------------------------------------------------------------------
UPDATE usuario SET debe_cambiar_contrasena = TRUE;
