-- ---------------------------------------------------------------------------
-- V21 — El CAC se carga como coeficiente, no como nivel de índice
-- ---------------------------------------------------------------------------
--
-- Lo reportó Ricardo: "el índice CAC está multiplicando mal (…) ahora pusimos
-- 1.6 y las cuotas aumentaron una barbaridad".
--
-- ---------------------------------------------------------------------------
--  Qué estaba pasando
-- ---------------------------------------------------------------------------
--
-- La columna guardaba el NIVEL del índice (el número que publica la Cámara
-- Argentina de la Construcción, del orden de 1.200 puntos) y el sistema sacaba
-- el coeficiente dividiendo un mes por el anterior. Eso es correcto para el
-- índice publicado, pero no es lo que Ricardo carga: él escribe directamente
-- cuánto quiere actualizar.
--
-- Con dos meses cargados como 0,1 y 1,6, la división daba 16: las cuotas se
-- multiplicaban por dieciséis.
--
-- ---------------------------------------------------------------------------
--  Qué pasa a guardar
-- ---------------------------------------------------------------------------
--
-- El COEFICIENTE del mes, que es el número por el que se multiplican las
-- cuotas: 1,4 significa que una cuota de $1.000 pasa a $1.400. Un 1 exacto
-- deja todo igual.
--
-- La columna se renombra a `coeficiente` a propósito. Se podría haber dejado
-- `valor_indice` cambiándole el significado, pero fue justamente esa distancia
-- entre el nombre y el contenido la que produjo el error: el campo decía
-- "índice" y esperaba un nivel, y quien lo cargaba escribía un multiplicador.
--
-- ---------------------------------------------------------------------------
--  Por qué se borran los valores cargados
-- ---------------------------------------------------------------------------
--
-- Los que había son niveles de índice, no coeficientes, y no se pueden
-- reinterpretar: un 0,1 leído como coeficiente significaría bajar las cuotas
-- a la décima parte. Dejarlos sería peor que no tenerlos.
--
-- Se pueden borrar sin riesgo porque el sistema todavía no está publicado y
-- los únicos registros son de prueba. Si en algún momento hubiera datos
-- reales, esta migración tendría que convertirlos y no vaciarlos.

DELETE FROM registro_cac;

ALTER TABLE registro_cac RENAME COLUMN valor_indice TO coeficiente;

-- Un coeficiente de 0 anularía las cuotas y uno negativo no significa nada. El
-- tope de 10 no es una regla de negocio sino una red: atrapa el error de cargar
-- el nivel del índice publicado (1.234,5) creyendo que es el multiplicador, que
-- es exactamente el error que motivó esta migración.
ALTER TABLE registro_cac
    ADD CONSTRAINT ck_cac_coeficiente CHECK (coeficiente > 0 AND coeficiente <= 10);

COMMENT ON COLUMN registro_cac.coeficiente IS
    'Por cuánto se multiplican las cuotas pendientes de ese mes. 1,4 = +40%. Un 1 deja todo igual.';
