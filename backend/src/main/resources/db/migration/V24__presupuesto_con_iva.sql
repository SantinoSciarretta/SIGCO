-- ---------------------------------------------------------------------------
-- V24 — El total del presupuesto incluye el IVA
-- ---------------------------------------------------------------------------
--
-- Pedido de Santino (05/10/2026): el total del presupuesto es la suma de los
-- ítems multiplicada por 1,21, porque es lo que paga el cliente con IVA.
--
-- Desde ahora el sistema calcula así cada total nuevo. Esta migración pone al
-- día los presupuestos que todavía están en BORRADOR, que son los únicos que se
-- siguen editando.
--
-- Los enviados, aprobados y rechazados NO se tocan, a propósito: su total es el
-- número que se le mostró al cliente, y en los aprobados es además la base del
-- plan de cobro ya generado. Cambiarlo dejaría cuotas que no cierran contra el
-- total. Esos presupuestos muestran IVA en cero, porque su total no lo incluía.
--
-- La columna no se renombra ni se agrega otra: total_presupuesto sigue siendo
-- "lo que paga el cliente", y el subtotal sin IVA se calcula de los ítems.

UPDATE presupuesto p
SET total_presupuesto = ROUND(
        COALESCE((SELECT SUM(i.subtotal) FROM item_presupuesto i
                  WHERE i.id_presupuesto = p.id_presupuesto), 0) * 1.21, 2)
WHERE p.estado = 'Borrador'
  AND p.tipo_presupuesto <> 'Cotización inicial';

UPDATE presupuesto
SET total_presupuesto = ROUND(metros_cuadrados * valor_por_m2 * 1.21, 2)
WHERE estado = 'Borrador'
  AND tipo_presupuesto = 'Cotización inicial'
  AND metros_cuadrados IS NOT NULL
  AND valor_por_m2 IS NOT NULL;
