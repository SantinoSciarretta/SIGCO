package com.sigco.materiales.dto;

import com.sigco.materiales.Material;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

/**
 * Objetos de entrada y salida del modulo Materiales.
 */
public final class MaterialDtos {

    private MaterialDtos() {
    }

    /**
     * Alta o edicion de un material.
     *
     * El estado no esta aca: un material nace Activo y despues se activa o
     * desactiva con su propia operacion.
     */
    public record MaterialSolicitud(

            @NotBlank(message = "El nombre del material es obligatorio")
            @Size(max = 150, message = "El nombre no puede superar los 150 caracteres")
            String nombreMaterial,

            @NotNull(message = "El material tiene que pertenecer a un rubro")
            Long idRubro,

            /*
             * La unidad NO se valida contra un conjunto cerrado, a diferencia del
             * estado. El informe la enumera como "Unidad / Bolsa / Metro cuadrado
             * / Metro lineal / Litro, entre otras": es una lista abierta. La
             * pantalla ofrece las habituales y permite escribir otra.
             */
            @NotBlank(message = "La unidad de medida es obligatoria")
            @Size(max = 20, message = "La unidad no puede superar los 20 caracteres")
            String unidadMedida) {
    }

    /** Activa o desactiva un material. */
    public record CambioEstadoMaterial(

            @NotBlank(message = "El estado es obligatorio")
            @Pattern(regexp = "Activo|Inactivo",
                     message = "El estado debe ser 'Activo' o 'Inactivo'")
            String estado) {
    }

    /** Un material tal como sale de la API. */
    public record MaterialRespuesta(
            Long idMaterial,
            String nombreMaterial,
            Long idRubro,
            String nombreRubro,
            String estadoRubro,
            String unidadMedida,
            String estado,
            LocalDateTime fechaAlta) {

        /** Requiere que el rubro este cargado (consulta con JOIN FETCH). */
        public static MaterialRespuesta desde(Material material) {
            return new MaterialRespuesta(
                    material.getIdMaterial(),
                    material.getNombreMaterial(),
                    material.getRubro().getIdRubro(),
                    material.getRubro().getNombreRubro(),
                    material.getRubro().getEstado(),
                    material.getUnidadMedida(),
                    material.getEstado(),
                    material.getFechaAlta());
        }
    }
}
