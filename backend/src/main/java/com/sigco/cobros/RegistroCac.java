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
 * Valor mensual del indice CAC (Camara Argentina de la Construccion).
 *
 * Es una tabla de referencia: no pertenece a ninguna obra en particular, sirve a
 * todas. Por eso no tiene ninguna clave foranea.
 *
 * El valor se carga A MANO, como decidio el alcance del proyecto: la
 * importacion automatica desde la CAC queda explicitamente fuera de esta
 * version. El dueño lo ingresa una vez por mes, cuando se publica.
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

    @Column(name = "valor_indice", nullable = false, precision = 8, scale = 4)
    private BigDecimal valorIndice;

    @Column(name = "fecha_carga", nullable = false)
    private LocalDateTime fechaCarga;

    protected RegistroCac() {
    }

    public RegistroCac(LocalDate mesCorrespondiente, BigDecimal valorIndice) {
        this.mesCorrespondiente = mesCorrespondiente.withDayOfMonth(1);
        this.valorIndice = valorIndice;
        this.fechaCarga = LocalDateTime.now();
    }

    public void corregirValor(BigDecimal valorIndice) {
        this.valorIndice = valorIndice;
    }

    public Long getIdCac() {
        return idCac;
    }

    public LocalDate getMesCorrespondiente() {
        return mesCorrespondiente;
    }

    public BigDecimal getValorIndice() {
        return valorIndice;
    }

    public LocalDateTime getFechaCarga() {
        return fechaCarga;
    }
}
