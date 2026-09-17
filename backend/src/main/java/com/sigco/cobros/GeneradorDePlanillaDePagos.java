package com.sigco.cobros;

import static com.sigco.documentos.EstiloPdf.DESTACADO;
import static com.sigco.documentos.EstiloPdf.ETIQUETA;
import static com.sigco.documentos.EstiloPdf.FECHA;
import static com.sigco.documentos.EstiloPdf.ROJO;
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
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.sigco.obras.Obra;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * La planilla de pagos que se le entrega al cliente.
 *
 * Es un pendiente que el informe pide explicitamente para el modulo Cobros, y
 * reemplaza a la planilla que hoy la empresa arma a mano y actualiza cada vez
 * que el cliente pregunta cuanto debe.
 *
 * ------------------------------------------------------------------
 *  Que muestra y que no
 * ------------------------------------------------------------------
 *
 * Muestra el plan completo: cada cuota, su vencimiento, su estado y el saldo.
 * NO muestra nada de la obra que no sea el plan de pago —ni gastos, ni
 * ganancia, ni avance—, porque este documento sale de la empresa hacia afuera.
 *
 * El anticipo aparece como "Anticipo" y no como "Cuota 0". El numero cero es
 * una decision del modelo de datos, util adentro del sistema porque permite
 * sacar el saldo de una sola consulta; para el cliente, es el anticipo.
 */
@Component
public class GeneradorDePlanillaDePagos {

    private static final Font ROJO_CHICO =
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, ROJO);

    /**
     * Arma la planilla.
     *
     * @param obra   con su cliente ya cargado
     * @param cuotas el plan completo, ordenado por numero
     */
    public byte[] generar(Obra obra, List<Cuota> cuotas) {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        Document documento = new Document(PageSize.A4, 48, 48, 48, 56);

        try {
            PdfWriter.getInstance(documento, salida);
            documento.open();

            membrete(documento);
            escribirDatos(documento, obra, cuotas);
            escribirCuotas(documento, cuotas);
            escribirSaldo(documento, cuotas);
            escribirPie(documento);

            documento.close();
        } catch (DocumentException e) {
            // Se convierte a excepcion no comprobada para que el manejador
            // global la traduzca a un 500 con el formato de error del sistema.
            throw new IllegalStateException("No se pudo generar la planilla de pagos", e);
        }

        return salida.toByteArray();
    }

    // ------------------------------------------------------------------

    private void escribirDatos(Document documento, Obra obra, List<Cuota> cuotas)
            throws DocumentException {

        Paragraph titulo = new Paragraph("PLAN DE PAGO", ETIQUETA);
        titulo.setSpacingBefore(14);
        documento.add(titulo);

        PdfPTable datos = new PdfPTable(2);
        datos.setWidthPercentage(100);
        datos.setSpacingBefore(6);
        datos.setSpacingAfter(16);

        datos.addCell(dato("Cliente", obra.getCliente().getNombreApellido()));
        datos.addCell(dato("Emitida", LocalDate.now().format(FECHA)));
        datos.addCell(dato("Obra", obra.getDireccionObra()));
        datos.addCell(dato("Cuotas", cantidadDeCuotas(cuotas)));

        documento.add(datos);
    }

    private void escribirCuotas(Document documento, List<Cuota> cuotas)
            throws DocumentException {

        PdfPTable tabla = new PdfPTable(4);
        tabla.setWidthPercentage(100);
        tabla.setWidths(new float[]{2.2f, 1.6f, 1.6f, 1.8f});
        tabla.setSpacingBefore(4);

        tabla.addCell(encabezado("Concepto", Element.ALIGN_LEFT));
        tabla.addCell(encabezado("Vence", Element.ALIGN_LEFT));
        tabla.addCell(encabezado("Estado", Element.ALIGN_LEFT));
        tabla.addCell(encabezado("Importe", Element.ALIGN_RIGHT));

        for (Cuota cuota : cuotas) {
            // El anticipo es la cuota cero en el modelo, pero para el cliente
            // es "el anticipo": el numero cero no le dice nada.
            String concepto = cuota.esAnticipo()
                    ? "Anticipo"
                    : "Cuota " + cuota.getNumeroCuota();

            tabla.addCell(celda(concepto, TEXTO, Element.ALIGN_LEFT));
            tabla.addCell(celda(cuota.getFechaVencimiento().format(FECHA),
                    TEXTO, Element.ALIGN_LEFT));
            tabla.addCell(celda(estado(cuota), fuenteDeEstado(cuota), Element.ALIGN_LEFT));
            tabla.addCell(importe(cuota.getMontoCuota(),
                    cuota.estaAbonada() ? TEXTO_CHICO : DESTACADO));
        }

        documento.add(tabla);
    }

    /**
     * El estado de cada cuota, en los terminos del cliente.
     *
     * Una cuota abonada lleva la fecha en que se pago: es lo que el cliente
     * busca cuando revisa la planilla, y es lo que hoy se resuelve por
     * WhatsApp preguntando "¿te llegó lo del martes?".
     */
    private String estado(Cuota cuota) {
        if (cuota.estaAbonada()) {
            return "Pagada " + (cuota.getFechaPago() != null
                    ? cuota.getFechaPago().format(FECHA) : "");
        }
        return cuota.estaVencida() ? "Vencida" : "Pendiente";
    }

    private Font fuenteDeEstado(Cuota cuota) {
        return cuota.estaVencida() ? ROJO_CHICO : TEXTO_CHICO;
    }

    private void escribirSaldo(Document documento, List<Cuota> cuotas)
            throws DocumentException {

        BigDecimal total = sumar(cuotas, c -> true);
        BigDecimal cobrado = sumar(cuotas, Cuota::estaAbonada);
        BigDecimal saldo = total.subtract(cobrado);

        PdfPTable resumen = new PdfPTable(2);
        resumen.setWidthPercentage(52);
        resumen.setHorizontalAlignment(Element.ALIGN_RIGHT);
        resumen.setSpacingBefore(16);

        resumen.addCell(celda("Total del plan", TEXTO_CHICO, Element.ALIGN_LEFT));
        resumen.addCell(importe(total, TEXTO));
        resumen.addCell(celda("Cobrado", TEXTO_CHICO, Element.ALIGN_LEFT));
        resumen.addCell(importe(cobrado, TEXTO));
        resumen.addCell(celda("Saldo pendiente", ETIQUETA, Element.ALIGN_LEFT));
        resumen.addCell(importe(saldo, TOTAL));

        documento.add(resumen);
    }

    private void escribirPie(Document documento) throws DocumentException {
        Paragraph pie = new Paragraph(
                "Los montos de las cuotas pendientes pueden actualizarse por el índice "
                + "de la Cámara Argentina de la Construcción (CAC), según lo acordado. "
                + "Las cuotas ya abonadas no se reajustan.", TEXTO_CHICO);
        pie.setSpacingBefore(26);
        documento.add(pie);
    }

    // ------------------------------------------------------------------

    private String cantidadDeCuotas(List<Cuota> cuotas) {
        long sinContarElAnticipo = cuotas.stream().filter(c -> !c.esAnticipo()).count();
        boolean hayAnticipo = cuotas.stream().anyMatch(Cuota::esAnticipo);
        return (hayAnticipo ? "Anticipo + " : "") + sinContarElAnticipo + " cuotas";
    }

    private BigDecimal sumar(List<Cuota> cuotas, java.util.function.Predicate<Cuota> filtro) {
        return cuotas.stream()
                .filter(filtro)
                .map(Cuota::getMontoCuota)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Nombre del archivo que ve el cliente al bajarlo. */
    public String nombreDeArchivo(Obra obra) {
        String direccion = obra.getDireccionObra()
                .replaceAll("[^a-zA-Z0-9]+", "-")
                .replaceAll("(^-|-$)", "")
                .toLowerCase(java.util.Locale.ROOT);
        return "plan-de-pago-" + direccion + ".pdf";
    }
}
