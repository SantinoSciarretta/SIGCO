package com.sigco.compras;

import static com.sigco.documentos.EstiloPdf.DESTACADO;
import static com.sigco.documentos.EstiloPdf.ETIQUETA;
import static com.sigco.documentos.EstiloPdf.FECHA;
import static com.sigco.documentos.EstiloPdf.TEXTO;
import static com.sigco.documentos.EstiloPdf.TEXTO_CHICO;
import static com.sigco.documentos.EstiloPdf.TOTAL;
import static com.sigco.documentos.EstiloPdf.celda;
import static com.sigco.documentos.EstiloPdf.dato;
import static com.sigco.documentos.EstiloPdf.encabezado;
import static com.sigco.documentos.EstiloPdf.importe;
import static com.sigco.documentos.EstiloPdf.membrete;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * La orden de pedido que se le manda al corralon o al proveedor.
 *
 * Pedido de Ricardo al probar el sistema: hasta ahora el pedido se aprobaba
 * dentro del sistema y despues habia que escribirle al proveedor por WhatsApp
 * copiando los materiales a mano. Eso es justo lo que el modulo Compras vino a
 * reemplazar, y quedaba a medias: el circuito quedaba registrado, pero el
 * mensaje al proveedor se seguia armando aparte.
 *
 * ------------------------------------------------------------------
 *  Que lleva y que NO lleva
 * ------------------------------------------------------------------
 *
 * Este documento sale de la empresa hacia afuera, asi que lleva lo que el
 * proveedor necesita para preparar el pedido y nada mas: que materiales, cuanto
 * de cada uno, a donde entregarlos y con quien coordinar.
 *
 * NO lleva el presupuesto de la obra, ni el gasto acumulado, ni la ganancia, ni
 * el nombre del cliente. Es el mismo criterio con el que la planilla de pagos
 * solo muestra el plan de cobro, y el mismo con el que la vidriera del
 * portfolio no muestra datos del cliente.
 *
 * Los precios SI van, porque son los que el dueño acordo con ese proveedor al
 * aprobar el pedido: mandarlos escritos es lo que evita la discusion posterior
 * sobre cuanto se habia pactado.
 */
@Component
public class GeneradorDeOrdenDePedido {

    /**
     * Arma la orden.
     *
     * @param pedido con su obra, su proveedor y sus materiales ya cargados
     */
    public byte[] generar(Pedido pedido) {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        Document documento = new Document(PageSize.A4, 48, 48, 48, 56);

        try {
            PdfWriter.getInstance(documento, salida);
            documento.open();

            membrete(documento);
            escribirDatos(documento, pedido);
            escribirMateriales(documento, pedido);
            escribirPie(documento);

            documento.close();
        } catch (DocumentException e) {
            // Se convierte a excepcion no comprobada para que el manejador
            // global la traduzca a un 500 con el formato de error del sistema.
            throw new IllegalStateException("No se pudo generar la orden de pedido", e);
        }

        return salida.toByteArray();
    }

    // ------------------------------------------------------------------

    private void escribirDatos(Document documento, Pedido pedido) throws DocumentException {
        Paragraph titulo = new Paragraph("ORDEN DE PEDIDO N° " + pedido.getIdPedido(), ETIQUETA);
        titulo.setSpacingBefore(14);
        documento.add(titulo);

        PdfPTable datos = new PdfPTable(2);
        datos.setWidthPercentage(100);
        datos.setSpacingBefore(6);
        datos.setSpacingAfter(16);

        datos.addCell(dato("Proveedor", pedido.getProveedor() != null
                ? pedido.getProveedor().getNombreProveedor() : "—"));
        datos.addCell(dato("Fecha", LocalDate.now().format(FECHA)));

        // La direccion de la obra es el dato operativo del documento: es a donde
        // hay que entregar el material.
        datos.addCell(dato("Entregar en", pedido.getObra().getDireccionObra()));
        datos.addCell(dato("Estado", pedido.getEstado()));

        documento.add(datos);
    }

    private void escribirMateriales(Document documento, Pedido pedido) throws DocumentException {
        PdfPTable tabla = new PdfPTable(new float[] { 44, 14, 20, 22 });
        tabla.setWidthPercentage(100);
        tabla.setSpacingAfter(14);

        tabla.addCell(encabezado("Material", Element.ALIGN_LEFT));
        tabla.addCell(encabezado("Cantidad", Element.ALIGN_RIGHT));
        tabla.addCell(encabezado("Precio unit.", Element.ALIGN_RIGHT));
        tabla.addCell(encabezado("Subtotal", Element.ALIGN_RIGHT));

        BigDecimal total = BigDecimal.ZERO;

        for (PedidoMaterial linea : pedido.getMateriales()) {
            tabla.addCell(celda(linea.getMaterial().getNombreMaterial(),
                    TEXTO, Element.ALIGN_LEFT));

            // La unidad va pegada a la cantidad: "12 bolsas" se lee de un
            // vistazo, y sin ella el proveedor tiene que adivinar.
            tabla.addCell(celda(
                    linea.getCantidad().stripTrailingZeros().toPlainString()
                            + " " + linea.getMaterial().getUnidadMedida(),
                    TEXTO, Element.ALIGN_RIGHT));

            BigDecimal precio = linea.getPrecioUnitario();
            if (precio == null) {
                // Pedido todavia sin aprobar: no hay precio acordado. Se deja en
                // blanco en lugar de poner cero, que se leeria como "gratis".
                tabla.addCell(celda("—", TEXTO_CHICO, Element.ALIGN_RIGHT));
                tabla.addCell(celda("—", TEXTO_CHICO, Element.ALIGN_RIGHT));
            } else {
                BigDecimal subtotal = precio.multiply(linea.getCantidad());
                total = total.add(subtotal);
                tabla.addCell(importe(precio, TEXTO));
                tabla.addCell(importe(subtotal, TEXTO));
            }
        }

        documento.add(tabla);

        if (total.signum() > 0) {
            PdfPTable resumen = new PdfPTable(2);
            resumen.setWidthPercentage(46);
            resumen.setHorizontalAlignment(Element.ALIGN_RIGHT);
            resumen.addCell(celda("TOTAL", DESTACADO, Element.ALIGN_LEFT));
            resumen.addCell(importe(total, TOTAL));
            documento.add(resumen);
        }
    }

    private void escribirPie(Document documento) throws DocumentException {
        Paragraph pie = new Paragraph(
                "Coordinar la entrega con el encargado de obra antes de despachar. "
                + "Ante cualquier diferencia entre esta orden y lo entregado, "
                + "avisar antes de dejar el material.", TEXTO_CHICO);
        pie.setSpacingBefore(26);
        documento.add(pie);
    }

    /** Nombre del archivo: identifica el pedido y el proveedor sin abrirlo. */
    public String nombreDeArchivo(Pedido pedido) {
        String proveedor = pedido.getProveedor() != null
                ? pedido.getProveedor().getNombreProveedor() : "sin-proveedor";

        return "pedido-" + pedido.getIdPedido() + "-" + limpiar(proveedor) + ".pdf";
    }

    private String limpiar(String texto) {
        return texto.replaceAll("[^a-zA-Z0-9]+", "-")
                .replaceAll("(^-|-$)", "")
                .toLowerCase(Locale.ROOT);
    }
}
