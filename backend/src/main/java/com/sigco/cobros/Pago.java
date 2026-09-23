package com.sigco.cobros;

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
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Un pago recibido a cuenta de una cuota.
 *
 * ------------------------------------------------------------------
 *  Alcance nuevo, no una correccion
 * ------------------------------------------------------------------
 *
 * El informe no pide pagos parciales: define la cuota con estados Pendiente,
 * Abonada y Vencida, o sea que se cobra entera o no se cobra. Esta entidad se
 * agrega a pedido de Santino para poder registrar cuando un cliente paga una
 * cuota en partes, que es algo que pasa en la practica.
 *
 * ------------------------------------------------------------------
 *  Un pago es un hecho, no un numero
 * ------------------------------------------------------------------
 *
 * Alcanzaria con una columna "monto_pagado" en la cuota para saber cuanto se
 * pago. No alcanzaria para nada mas: se perderia cuando entro cada parte, por
 * que medio y con que comprobante — justo lo que hace falta cuando el cliente
 * pregunta si una transferencia quedo registrada.
 *
 * Es el mismo criterio con el que Gastos no guarda un total por rubro sino cada
 * gasto: el total siempre se puede sumar, pero el detalle, una vez perdido, no
 * se recupera.
 */
@Entity
@Table(name = "pago")
public class Pago {

    public static final String MEDIO_TRANSFERENCIA = "Transferencia";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_pago")
    private Long idPago;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_cuota", nullable = false)
    private Cuota cuota;

    @Column(name = "monto", nullable = false, precision = 14, scale = 2)
    private BigDecimal monto;

    /** Cuando el cliente pago, que no es cuando se cargo en el sistema. */
    @Column(name = "fecha_pago", nullable = false)
    private LocalDate fechaPago;

    @Column(name = "medio_pago", nullable = false, length = 15)
    private String medioPago;

    @Column(name = "comprobante_emitido", length = 20)
    private String comprobanteEmitido;

    /**
     * Quien lo cargo.
     *
     * Se guarda el id y no la entidad Usuario: es el mismo criterio que usan
     * gasto e hito, y evita arrastrar el modulo Usuarios adentro de Cobros para
     * un dato que solo se muestra.
     */
    @Column(name = "id_usuario_registro")
    private Long idUsuarioRegistro;

    @Column(name = "fecha_carga", nullable = false)
    private LocalDateTime fechaCarga;

    protected Pago() {
    }

    public Pago(Cuota cuota, BigDecimal monto, LocalDate fechaPago,
                String medioPago, String comprobanteEmitido, Long idUsuarioRegistro) {
        this.cuota = cuota;
        this.monto = monto;
        this.fechaPago = fechaPago;
        this.medioPago = medioPago;
        this.comprobanteEmitido = comprobanteEmitido;
        this.idUsuarioRegistro = idUsuarioRegistro;
        this.fechaCarga = LocalDateTime.now();
    }

    public Long getIdPago() {
        return idPago;
    }

    public Cuota getCuota() {
        return cuota;
    }

    public BigDecimal getMonto() {
        return monto;
    }

    public LocalDate getFechaPago() {
        return fechaPago;
    }

    public String getMedioPago() {
        return medioPago;
    }

    public String getComprobanteEmitido() {
        return comprobanteEmitido;
    }

    public Long getIdUsuarioRegistro() {
        return idUsuarioRegistro;
    }

    public LocalDateTime getFechaCarga() {
        return fechaCarga;
    }
}
