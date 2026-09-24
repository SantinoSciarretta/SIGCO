package com.sigco.compras.dto;

import java.time.LocalDateTime;

/**
 * Todo lo necesario para mandarle la orden al corralon por WhatsApp.
 *
 * ------------------------------------------------------------------
 *  Por que el link y no un envio automatico
 * ------------------------------------------------------------------
 *
 * SIGCO no manda el mensaje: arma el link y lo abre. El envio lo hace Ricardo
 * apretando "Enviar" en su propio WhatsApp, desde su propio numero, que es el
 * que los corralones ya tienen agendado.
 *
 * Mandarlo de verdad requeriria la API oficial de Meta, que exige un numero
 * dedicado —Ricardo perderia el suyo como WhatsApp normal—, verificacion de la
 * empresa, plantillas aprobadas y pago por conversacion. La Propuesta Tecnica,
 * ademas, deja esa integracion explicitamente fuera del alcance de esta
 * version.
 *
 * ------------------------------------------------------------------
 *  Por que el PDF va como link y no adjunto
 * ------------------------------------------------------------------
 *
 * Un link wa.me solo puede llevar texto. No hay forma de adjuntarle un archivo.
 * Asi que el PDF viaja como enlace, y ese enlace tiene que abrirse sin login
 * porque el corralon no tiene cuenta en SIGCO.
 */
public record EnvioPorWhatsApp(

        Long idPedido,
        String nombreProveedor,

        /** El telefono tal como esta cargado en Proveedores. */
        String telefonoCargado,

        /**
         * El mismo telefono en el formato que necesita WhatsApp, o null si no
         * se pudo interpretar.
         *
         * Se devuelve para que la pantalla lo MUESTRE antes de abrir el chat.
         * Es la ultima oportunidad de darse cuenta de que el numero esta mal
         * antes de mandarle el pedido de una obra a un desconocido.
         */
        String telefonoParaWhatsApp,

        /** El texto del mensaje, para poder leerlo antes de mandarlo. */
        String mensaje,

        /**
         * El link que abre WhatsApp con el chat y el mensaje listos, o null si
         * el telefono no se pudo interpretar.
         */
        String urlWhatsApp,

        /** El link publico del PDF de la orden. */
        String urlOrden,

        /** Hasta cuando sirve ese link. */
        LocalDateTime vence,

        /**
         * Que hay que arreglar, si hay algo. null cuando esta todo bien.
         *
         * Va como texto y no como codigo porque el unico consumidor es una
         * pantalla que lo muestra tal cual.
         */
        String aviso) {
}
