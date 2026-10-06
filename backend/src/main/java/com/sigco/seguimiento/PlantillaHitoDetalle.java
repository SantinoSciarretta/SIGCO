package com.sigco.seguimiento;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/** Una etapa dentro de una plantilla de hitos. */
@Entity
@Table(name = "plantilla_hito_detalle")
public class PlantillaHitoDetalle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_detalle")
    private Long idDetalle;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_plantilla", nullable = false)
    private PlantillaHito plantilla;

    @Column(name = "nombre_hito", nullable = false, length = 150)
    private String nombreHito;

    @Column(name = "ponderacion", nullable = false, precision = 5, scale = 2)
    private BigDecimal ponderacion;

    @Column(name = "orden", nullable = false)
    private Integer orden;

    /**
     * Constructor vacío que exige la base de datos (JPA) para poder armar el
     * objeto al leerlo. No se usa desde el código.
     */
    protected PlantillaHitoDetalle() {
    }

    /**
     * Crea una etapa de la plantilla con su nombre, su ponderación sugerida y
     * su orden.
     */
    public PlantillaHitoDetalle(PlantillaHito plantilla, String nombreHito,
                                BigDecimal ponderacion, Integer orden) {
        this.plantilla = plantilla;
        this.nombreHito = nombreHito;
        this.ponderacion = ponderacion;
        this.orden = orden;
    }

    /**
     * Métodos de lectura: devuelven los datos guardados de la etapa de la
     * plantilla. Solo leen, no modifican nada.
     */
    public Long getIdDetalle() {
        return idDetalle;
    }

    public PlantillaHito getPlantilla() {
        return plantilla;
    }

    public String getNombreHito() {
        return nombreHito;
    }

    public BigDecimal getPonderacion() {
        return ponderacion;
    }

    public Integer getOrden() {
        return orden;
    }
}
