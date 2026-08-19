package com.sigco.presupuestacion;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Acceso a datos de los items de presupuesto.
 *
 * Los items se guardan y se borran junto con su presupuesto (cascade en la
 * entidad), asi que este repositorio se usa solo para buscar un item puntual
 * al editarlo o quitarlo.
 */
public interface ItemPresupuestoRepository extends JpaRepository<ItemPresupuesto, Long> {
}
