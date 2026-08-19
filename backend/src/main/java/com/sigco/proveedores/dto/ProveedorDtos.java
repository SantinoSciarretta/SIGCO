package com.sigco.proveedores.dto;

import com.sigco.proveedores.Cotizacion;
import com.sigco.proveedores.ObservacionProveedor;
import com.sigco.proveedores.Proveedor;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Objetos de entrada y salida del modulo Proveedores.
 */
public final class ProveedorDtos {

    private ProveedorDtos() {
    }

    // ---------- Entrada ----------

    /** Alta o edicion de un proveedor. */
    public record ProveedorSolicitud(

            @NotBlank(message = "El nombre del proveedor es obligatorio")
            @Size(max = 150, message = "El nombre no puede superar los 150 caracteres")
            String nombreProveedor,

            // Obligatoria: es el criterio con el que el dueño elige a quien
            // pedirle al aprobar un pedido.
            @NotBlank(message = "La zona de cobertura es obligatoria")
            @Size(max = 100, message = "La zona no puede superar los 100 caracteres")
            String zonaCobertura,

            @Size(max = 30, message = "El telefono no puede superar los 30 caracteres")
            String telefonoContacto,

            @Email(message = "El correo electronico no tiene un formato valido")
            @Size(max = 100, message = "El correo no puede superar los 100 caracteres")
            String emailContacto) {
    }

    /** Registro de una cotizacion. La fecha la pone el sistema. */
    public record CotizacionSolicitud(

            @NotNull(message = "La cotizacion tiene que corresponder a un material")
            Long idMaterial,

            @NotNull(message = "El precio cotizado es obligatorio")
            @PositiveOrZero(message = "El precio no puede ser negativo")
            BigDecimal precioCotizado) {
    }

    /** Registro de una observacion de comportamiento. */
    public record ObservacionSolicitud(

            @NotBlank(message = "La descripcion de la observacion es obligatoria")
            @Size(max = 300, message = "La descripcion no puede superar los 300 caracteres")
            String descripcion,

            /** Pedido que la origino. Opcional hasta que exista el modulo Compras. */
            Long idPedido) {
    }

    /** Activa o desactiva un proveedor. */
    public record CambioEstadoProveedor(

            @NotBlank(message = "El estado es obligatorio")
            @Pattern(regexp = "Activo|Inactivo",
                     message = "El estado debe ser 'Activo' o 'Inactivo'")
            String estado) {
    }

    // ---------- Salida ----------

    /** Un proveedor tal como sale de la API. */
    public record ProveedorRespuesta(
            Long idProveedor,
            String nombreProveedor,
            String zonaCobertura,
            String telefonoContacto,
            String emailContacto,
            String estado,
            LocalDateTime fechaAlta,
            long cantidadCotizaciones,
            long cantidadObservaciones) {

        public static ProveedorRespuesta desde(Proveedor p, long cotizaciones, long observaciones) {
            return new ProveedorRespuesta(
                    p.getIdProveedor(), p.getNombreProveedor(), p.getZonaCobertura(),
                    p.getTelefonoContacto(), p.getEmailContacto(), p.getEstado(),
                    p.getFechaAlta(), cotizaciones, observaciones);
        }
    }

    /** Una cotizacion, con su proveedor y su material. */
    public record CotizacionRespuesta(
            Long idCotizacion,
            Long idProveedor,
            String nombreProveedor,
            String zonaCobertura,
            Long idMaterial,
            String nombreMaterial,
            String unidadMedida,
            BigDecimal precioCotizado,
            LocalDateTime fechaCotizacion) {

        public static CotizacionRespuesta desde(Cotizacion c) {
            return new CotizacionRespuesta(
                    c.getIdCotizacion(),
                    c.getProveedor().getIdProveedor(),
                    c.getProveedor().getNombreProveedor(),
                    c.getProveedor().getZonaCobertura(),
                    c.getMaterial().getIdMaterial(),
                    c.getMaterial().getNombreMaterial(),
                    c.getMaterial().getUnidadMedida(),
                    c.getPrecioCotizado(),
                    c.getFechaCotizacion());
        }
    }

    /** Una observacion de comportamiento. */
    public record ObservacionRespuesta(
            Long idObservacion,
            Long idProveedor,
            Long idPedido,
            String descripcion,
            LocalDateTime fecha) {

        public static ObservacionRespuesta desde(ObservacionProveedor o) {
            return new ObservacionRespuesta(
                    o.getIdObservacion(),
                    o.getProveedor().getIdProveedor(),
                    o.getIdPedido(),
                    o.getDescripcion(),
                    o.getFecha());
        }
    }
}
