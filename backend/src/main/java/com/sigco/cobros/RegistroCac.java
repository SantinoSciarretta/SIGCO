package com.sigco.cobros;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Actualizacion mensual por CAC.
 *
 * Es una tabla de referencia: no pertenece a ninguna obra en particular, sirve a
 * todas. Por eso no tiene ninguna clave foranea.
 *
 * ------------------------------------------------------------------
 *  Guarda el COEFICIENTE, no el nivel del indice
 * ------------------------------------------------------------------
 *
 * El numero es por cuanto se multiplican las cuotas pendientes: 1,4 significa
 * que una cuota de $1.000 pasa a $1.400, y un 1 exacto deja todo igual.
 *
 * Antes guardaba el nivel del indice publicado por la Camara —del orden de
 * 1.200 puntos— y el coeficiente se sacaba dividiendo un mes por el anterior.
 * Es lo correcto para ese indice, pero no es lo que el dueño carga: el escribe
 * directamente cuanto quiere actualizar. Con dos meses cargados como 0,1 y 1,6
 * la division daba 16, y las cuotas se multiplicaban por dieciseis (V21).
 *
 * El valor se carga A MANO, como decidio el alcance del proyecto: la
 * importacion automatica desde la CAC queda explicitamente fuera de esta
 * version.
 */
@Entity
@Table(name = "registro_cac")
public class RegistroCac {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_cac")
    private Long idCac;

    /**
     * Mes al que corresponde el indice.
     *
     * Se normaliza al dia 1: el indice es mensual, y guardarlo con dias
     * distintos haria que "el CAC de septiembre" pudiera existir dos veces.
     */
    @Column(name = "mes_correspondiente", nullable = false)
    private LocalDate mesCorrespondiente;

    /** Por cuanto se multiplican las cuotas pendientes. 1,4 = +40%. */
    @Column(name = "coeficiente", nullable = false, precision = 8, scale = 4)
    private BigDecimal coeficiente;

    @Column(name = "fecha_carga", nullable = false)
    private LocalDateTime fechaCarga;

    protected RegistroCac() {
    }

    public RegistroCac(LocalDate mesCorrespondiente, BigDecimal coeficiente) {
        this.mesCorrespondiente = mesCorrespondiente.withDayOfMonth(1);
        this.coeficiente = coeficiente;
        this.fechaCarga = LocalDateTime.now();
    }

    public void corregirValor(BigDecimal coeficiente) {
        this.coeficiente = coeficiente;
    }

    public Long getIdCac() {
        return idCac;
    }

    public LocalDate getMesCorrespondiente() {
        return mesCorrespondiente;
    }

    public BigDecimal getCoeficiente() {
        return coeficiente;
    }

    public LocalDateTime getFechaCarga() {
        return fechaCarga;
    }
}
