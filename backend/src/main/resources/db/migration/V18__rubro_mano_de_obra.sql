-- =============================================================================
--  V18 — El rubro de mano de obra
--
--  Pedido de Ricardo: "para mano de obra tendria que ser como su propio rubro,
--  que de todos los rubros y subrubros cargue el costo de mano de obra".
--
--  Es decir: un rubro que, al presupuestarlo, en lugar de listar materiales
--  lista los OTROS rubros, para cargar de una sola vez cuanto sale la mano de
--  obra de cada especialidad.
--
--  ------------------------------------------------------------------
--   Por que una marca y no reconocerlo por el nombre
--  ------------------------------------------------------------------
--
--  Lo mas facil seria buscar el rubro llamado "Mano de obra". Y se rompe el dia
--  que alguien lo renombra a "Mano de obra y jornales", o lo escribe sin tilde,
--  o crea uno nuevo por error: la planilla dejaria de comportarse distinto sin
--  que nadie entienda por que.
--
--  Con una marca explicita, el comportamiento no depende de como este escrito
--  el nombre, y el sistema puede ademas impedir que haya dos rubros marcados.
-- =============================================================================


ALTER TABLE rubro ADD COLUMN es_mano_de_obra BOOLEAN NOT NULL DEFAULT FALSE;


-- -----------------------------------------------------------------------------
--  Un solo rubro de mano de obra
--
--  Un indice unico PARCIAL: la restriccion solo aplica a las filas con la marca
--  en TRUE. Sin el "WHERE", un indice unico sobre un booleano permitiria un solo
--  rubro en todo el catalogo, que es lo contrario de lo que se busca.
--
--  Existe porque dos rubros marcados dejarian a la planilla sin saber cual es:
--  elegiria uno de los dos sin criterio, y el presupuesto saldria distinto segun
--  el orden en que se hayan cargado.
-- -----------------------------------------------------------------------------
CREATE UNIQUE INDEX ux_rubro_mano_de_obra ON rubro (es_mano_de_obra)
    WHERE es_mano_de_obra = TRUE;


-- -----------------------------------------------------------------------------
--  Si ya existe un rubro que se llama asi, queda marcado
--
--  Para las bases que ya tienen el catalogo cargado. Se compara sin distinguir
--  mayusculas ni acentos, y se marca UNO SOLO aunque hubiera varios parecidos:
--  el indice de arriba no admite mas.
-- -----------------------------------------------------------------------------
UPDATE rubro SET es_mano_de_obra = TRUE
WHERE id_rubro = (
    SELECT id_rubro FROM rubro
    WHERE LOWER(TRANSLATE(nombre_rubro, 'áéíóúÁÉÍÓÚ', 'aeiouAEIOU'))
          LIKE '%mano de obra%'
    ORDER BY id_rubro
    LIMIT 1
);
