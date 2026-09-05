package com.sigco.compras.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

/** Datos que entran al modulo Compras. */
public final class PedidoDtos {

    private PedidoDtos() {
    }

    /**
     * Alta de un pedido.
     *
     * No lleva proveedor: el informe establece que lo elige el dueño al aprobar.
     * Que no exista el campo es mas fuerte que validarlo, porque no hay forma de
     * enviarlo aunque alguien lo intente desde fuera del frontend.
     */
    public record NuevoPedido(

            @NotNull(message = "La obra es obligatoria")
            Long idObra,

            /** El informe: "No se puede generar un pedido sin al menos un material". */
            @NotEmpty(message = "El pedido tiene que llevar al menos un material")
            @Valid
            List<LineaSolicitud> materiales) {
    }

    /** Una linea del pedido: material del catalogo y cuanto. */
    public record LineaSolicitud(

            @NotNull(message = "El material es obligatorio")
            Long idMaterial,

            @NotNull(message = "La cantidad es obligatoria")
            @DecimalMin(value = "0.01", message = "La cantidad tiene que ser mayor a cero")
            BigDecimal cantidad) {
    }

    /**
     * Aprobacion: el dueño elige proveedor y confirma los precios.
     *
     * Los precios vienen aca y no en el alta porque en el alta no se conocen:
     * quien arma el pedido en la obra sabe que faltan 20 bolsas de cemento, no
     * a cuanto las vende el corralon.
     */
    public record Aprobacion(

            @NotNull(message = "Hay que elegir el proveedor al aprobar")
            Long idProveedor,

            @NotEmpty(message = "Hay que confirmar el precio de cada material")
            @Valid
            List<PrecioLinea> precios) {
    }

    public record PrecioLinea(

            @NotNull(message = "El material es obligatorio")
            Long idMaterial,

            @NotNull(message = "El precio unitario es obligatorio")
            @DecimalMin(value = "0.00", message = "El precio no puede ser negativo")
            BigDecimal precioUnitario) {
    }

    /**
     * Confirmacion de recepcion en obra.
     *
     * La foto del remito es obligatoria: es la regla del informe y es lo que
     * reemplaza al papel que hoy se pierde. La nota solo se completa si hubo
     * diferencias, y su presencia es la que define el estado final.
     */
    public record Recepcion(

            @NotNull(message = "La foto del remito es obligatoria")
            @Size(max = 255, message = "La referencia de la foto no puede superar los 255 caracteres")
            String fotoRemito,

            @Size(max = 300, message = "La nota no puede superar los 300 caracteres")
            String notaDiferencia) {
    }

    /** Anulacion de un pedido que finalmente no se concreta. */
    public record Anulacion(

            @NotNull(message = "El motivo de anulación es obligatorio")
            @Size(min = 1, max = 200, message = "El motivo no puede superar los 200 caracteres")
            String motivo) {
    }
}
