package com.sigco.seguimiento;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Plantilla de etapas tipicas, reutilizable entre obras parecidas.
 *
 * El informe la pide para no rearmar desde cero los hitos de cada obra: la
 * mayoria de las reformas pasan por las mismas etapas (demolicion, estructura,
 * instalaciones, terminaciones) y lo unico que cambia son los pesos.
 */
@Entity
@Table(name = "plantilla_hito")
public class PlantillaHito {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_plantilla")
    private Long idPlantilla;

    @Column(name = "nombre_plantilla", nullable = false, length = 100)
    private String nombrePlantilla;

    @Column(name = "fecha_creacion", nullable = false)
    private LocalDateTime fechaCreacion;

    @OneToMany(mappedBy = "plantilla", fetch = FetchType.LAZY,
               cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PlantillaHitoDetalle> detalles = new ArrayList<>();

    protected PlantillaHito() {
    }

    public PlantillaHito(String nombrePlantilla) {
        this.nombrePlantilla = nombrePlantilla;
        this.fechaCreacion = LocalDateTime.now();
    }

    public void agregarDetalle(PlantillaHitoDetalle detalle) {
        this.detalles.add(detalle);
    }

    /**
     * Suma de las ponderaciones de la plantilla.
     *
     * Tiene que dar 100 por el mismo motivo que en una obra: la plantilla es el
     * punto de partida de los hitos, y si no cierra arrastra el problema a
     * todas las obras que la usen.
     */
    public BigDecimal sumaPonderaciones() {
        return detalles.stream()
                .map(PlantillaHitoDetalle::getPonderacion)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public Long getIdPlantilla() {
        return idPlantilla;
    }

    public String getNombrePlantilla() {
        return nombrePlantilla;
    }

    public LocalDateTime getFechaCreacion() {
        return fechaCreacion;
    }

    public List<PlantillaHitoDetalle> getDetalles() {
        return detalles;
    }
}
