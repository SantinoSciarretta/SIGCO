package com.sigco.presupuestacion;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Genera el PDF del presupuesto que se le envia al cliente.
 *
 * REGLA CENTRAL DE ESTE ARCHIVO: el precio unitario de cada item NO aparece.
 * El informe lo dice de forma explicita, y es como la empresa trabaja hoy: al
 * cliente se le muestra que incluye cada rubro y cuanto sale ese rubro, nunca
 * el desglose de cuanto cuesta cada cosa por unidad. Ese dato es interno y
 * sirve para armar el presupuesto y despues controlar el gasto.
 *
 * Por eso este generador toma los items solo para dos cosas: listar las
 * descripciones de los trabajos incluidos, y sumar el subtotal de cada rubro.
 * En ningun lugar imprime valorUnitario ni cantidad por precio.
 *
 * El documento se arma en memoria y se devuelve como arreglo de bytes: los PDF
 * de un presupuesto pesan unos pocos kilobytes y no hace falta escribirlos en
 * disco ni guardarlos, porque se regeneran a partir de los datos cuando se los
 * pide.
 */
@Component
public class GeneradorDePdf {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private static final Color ACERO = new Color(0x1d, 0x2d, 0x3d);
    private static final Color GRIS_LINEA = new Color(0xd4, 0xd4, 0xd7);
    private static final Color GRIS_TEXTO = new Color(0x5d, 0x5d, 0x60);

    private static final Font TITULO = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 22, ACERO);
    private static final Font SUBTITULO = FontFactory.getFont(FontFactory.HELVETICA, 9, GRIS_TEXTO);
    private static final Font ETIQUETA = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, GRIS_TEXTO);
    private static final Font TEXTO = FontFactory.getFont(FontFactory.HELVETICA, 10, Color.BLACK);
    private static final Font TEXTO_CHICO = FontFactory.getFont(FontFactory.HELVETICA, 9, GRIS_TEXTO);
    private static final Font RUBRO = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, Color.BLACK);
    private static final Font TOTAL = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, ACERO);

    /**
     * Arma el PDF de un presupuesto.
     *
     * @param presupuesto con su obra, cliente e items ya cargados
     */
    public byte[] generar(Presupuesto presupuesto) {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        Document documento = new Document(PageSize.A4, 48, 48, 48, 56);

        try {
            PdfWriter.getInstance(documento, salida);
            documento.open();

            escribirMembrete(documento);
            escribirDatosDeObra(documento, presupuesto);
            escribirRubros(documento, presupuesto);
            escribirTotal(documento, presupuesto);
            escribirPlanDePago(documento, presupuesto);
            escribirPie(documento, presupuesto);

            documento.close();
        } catch (DocumentException e) {
            // Se convierte a excepcion no comprobada para que el manejador
            // global la traduzca a un 500 con el formato de error del sistema.
            throw new IllegalStateException("No se pudo generar el PDF del presupuesto", e);
        }

        return salida.toByteArray();
    }

    // ------------------------------------------------------------------

    /** Membrete de la empresa. */
    private void escribirMembrete(Document documento) throws DocumentException {
        Paragraph marca = new Paragraph("GRANICA SRL", TITULO);
        documento.add(marca);

        Paragraph rubroEmpresa = new Paragraph(
                "Construcción civil, refacción y decoración de locales", SUBTITULO);
        rubroEmpresa.setSpacingAfter(14);
        documento.add(rubroEmpresa);

        documento.add(linea());
    }

    /** Datos del proyecto: cliente, direccion, instancia y fecha. */
    private void escribirDatosDeObra(Document documento, Presupuesto p) throws DocumentException {
        Paragraph titulo = new Paragraph("PRESUPUESTO", ETIQUETA);
        titulo.setSpacingBefore(14);
        documento.add(titulo);

        PdfPTable datos = new PdfPTable(2);
        datos.setWidthPercentage(100);
        datos.setWidths(new float[]{1f, 1f});
        datos.setSpacingBefore(6);
        datos.setSpacingAfter(16);

        datos.addCell(dato("Cliente", p.getObra().getCliente().getNombreApellido()));
        datos.addCell(dato("Fecha", p.getFechaCreacion().format(FECHA)));
        datos.addCell(dato("Obra", p.getObra().getDireccionObra()));
        datos.addCell(dato("Instancia", p.getTipoPresupuesto() + " · versión " + p.getVersion()));

        if (p.getPlazoEstimadoObra() != null) {
            datos.addCell(dato("Plazo estimado", p.getPlazoEstimadoObra()));
            datos.addCell(dato("Tipo de obra", p.getObra().getTipoObra()));
        }

        documento.add(datos);
    }

    /**
     * Detalle por rubro.
     *
     * De cada rubro se muestra que trabajos incluye y cuanto sale el rubro
     * completo. Los precios por item no se imprimen.
     */
    private void escribirRubros(Document documento, Presupuesto p) throws DocumentException {
        if (p.esCotizacionInicial()) {
            escribirCotizacionInicial(documento, p);
            return;
        }

        PdfPTable tabla = new PdfPTable(2);
        tabla.setWidthPercentage(100);
        tabla.setWidths(new float[]{3.2f, 1f});

        tabla.addCell(encabezado("DETALLE DE TRABAJOS", Element.ALIGN_LEFT));
        tabla.addCell(encabezado("SUBTOTAL", Element.ALIGN_RIGHT));

        for (Map.Entry<String, DatosDeRubro> entrada : agruparPorRubro(p).entrySet()) {
            DatosDeRubro rubro = entrada.getValue();

            PdfPCell celdaRubro = new PdfPCell();
            celdaRubro.setBorder(com.lowagie.text.Rectangle.BOTTOM);
            celdaRubro.setBorderColor(GRIS_LINEA);
            celdaRubro.setPadding(8);
            celdaRubro.addElement(new Paragraph(entrada.getKey(), RUBRO));

            // Los trabajos incluidos, sin ningun numero al lado.
            for (String trabajo : rubro.trabajos) {
                Paragraph linea = new Paragraph("· " + trabajo, TEXTO_CHICO);
                linea.setIndentationLeft(8);
                celdaRubro.addElement(linea);
            }

            tabla.addCell(celdaRubro);
            tabla.addCell(importe(rubro.subtotal, TEXTO));
        }

        documento.add(tabla);
    }

    /** La cotizacion inicial no lleva items: es un precio estimativo. */
    private void escribirCotizacionInicial(Document documento, Presupuesto p)
            throws DocumentException {

        Paragraph texto = new Paragraph(
                "Precio estimativo calculado sobre "
                + p.getMetrosCuadrados() + " m² de superficie a intervenir.", TEXTO);
        texto.setSpacingAfter(10);
        documento.add(texto);

        Paragraph aclaracion = new Paragraph(
                "Se trata de una estimación preliminar, sujeta a la elaboración del "
                + "presupuesto detallado.", TEXTO_CHICO);
        aclaracion.setSpacingAfter(10);
        documento.add(aclaracion);
    }

    private void escribirTotal(Document documento, Presupuesto p) throws DocumentException {
        PdfPTable tabla = new PdfPTable(2);
        tabla.setWidthPercentage(100);
        tabla.setWidths(new float[]{3.2f, 1f});
        tabla.setSpacingBefore(4);

        PdfPCell etiqueta = new PdfPCell(new Phrase("TOTAL", TOTAL));
        etiqueta.setBorder(com.lowagie.text.Rectangle.NO_BORDER);
        etiqueta.setHorizontalAlignment(Element.ALIGN_RIGHT);
        etiqueta.setPadding(8);

        PdfPCell valor = importe(p.getTotalPresupuesto(), TOTAL);
        valor.setBorder(com.lowagie.text.Rectangle.NO_BORDER);

        tabla.addCell(etiqueta);
        tabla.addCell(valor);
        documento.add(tabla);
    }

    private void escribirPlanDePago(Document documento, Presupuesto p) throws DocumentException {
        if (p.getAnticipoPorcentaje() == null) {
            return;
        }

        documento.add(linea());

        Paragraph titulo = new Paragraph("FORMA DE PAGO", ETIQUETA);
        titulo.setSpacingBefore(14);
        titulo.setSpacingAfter(6);
        documento.add(titulo);

        Paragraph anticipo = new Paragraph(
                "Anticipo del " + porcentaje(p.getAnticipoPorcentaje()) + ": "
                + pesos(p.calcularAnticipo()), TEXTO);
        documento.add(anticipo);

        if (p.getCantidadCuotas() != null && p.getCantidadCuotas() > 0) {
            documento.add(new Paragraph(
                    "Saldo en " + p.getCantidadCuotas() + " cuotas de "
                    + pesos(p.calcularMontoDeCuota()) + ".", TEXTO));
        }
    }

    private void escribirPie(Document documento, Presupuesto p) throws DocumentException {
        Paragraph pie = new Paragraph(
                "Presupuesto " + p.getTipoPresupuesto().toLowerCase()
                + " N° " + p.getIdPresupuesto() + " · versión " + p.getVersion()
                + " · emitido el " + p.getFechaCreacion().format(FECHA)
                + ". Los valores están sujetos a la variación de costos de materiales "
                + "y mano de obra.", TEXTO_CHICO);
        pie.setSpacingBefore(24);
        documento.add(pie);
    }

    // ------------------------------------------------------------------

    /** Trabajos y subtotal acumulado de un rubro. */
    private static final class DatosDeRubro {
        private final List<String> trabajos = new java.util.ArrayList<>();
        private BigDecimal subtotal = BigDecimal.ZERO;
    }

    /**
     * Agrupa los items por rubro conservando el orden alfabetico.
     *
     * LinkedHashMap para que el orden de insercion se respete: los items ya
     * vienen ordenados de la consulta, y un HashMap comun los desordenaria.
     */
    private Map<String, DatosDeRubro> agruparPorRubro(Presupuesto p) {
        Map<String, DatosDeRubro> porRubro = new LinkedHashMap<>();

        p.getItems().stream()
                .sorted(java.util.Comparator.comparing(i -> i.getRubro().getNombreRubro()))
                .forEach(item -> {
                    DatosDeRubro datos = porRubro.computeIfAbsent(
                            item.getRubro().getNombreRubro(), clave -> new DatosDeRubro());
                    datos.trabajos.add(item.getDescripcion());
                    datos.subtotal = datos.subtotal.add(item.getSubtotal());
                });

        return porRubro;
    }

    private PdfPCell dato(String etiqueta, String valor) {
        PdfPCell celda = new PdfPCell();
        celda.setBorder(com.lowagie.text.Rectangle.NO_BORDER);
        celda.setPaddingBottom(6);
        celda.addElement(new Paragraph(etiqueta.toUpperCase(), ETIQUETA));
        celda.addElement(new Paragraph(valor, TEXTO));
        return celda;
    }

    private PdfPCell encabezado(String texto, int alineacion) {
        PdfPCell celda = new PdfPCell(new Phrase(texto, ETIQUETA));
        celda.setBorder(com.lowagie.text.Rectangle.BOTTOM);
        celda.setBorderColor(ACERO);
        celda.setHorizontalAlignment(alineacion);
        celda.setPadding(6);
        return celda;
    }

    private PdfPCell importe(BigDecimal monto, Font fuente) {
        PdfPCell celda = new PdfPCell(new Phrase(pesos(monto), fuente));
        celda.setBorder(com.lowagie.text.Rectangle.BOTTOM);
        celda.setBorderColor(GRIS_LINEA);
        celda.setHorizontalAlignment(Element.ALIGN_RIGHT);
        celda.setVerticalAlignment(Element.ALIGN_TOP);
        celda.setPadding(8);
        return celda;
    }

    private Paragraph linea() {
        Paragraph p = new Paragraph(new Chunk(
                new com.lowagie.text.pdf.draw.LineSeparator(0.6f, 100, GRIS_LINEA, Element.ALIGN_CENTER, 0)));
        p.setSpacingBefore(4);
        return p;
    }

    /**
     * Porcentaje con coma decimal.
     *
     * BigDecimal.toString() devuelve "30.00" con punto, que en un documento
     * que lee un cliente argentino se ve mal.
     */
    private String porcentaje(BigDecimal valor) {
        return String.format(java.util.Locale.forLanguageTag("es-AR"), "%,.2f%%", valor);
    }

    /** Importe con separador de miles argentino. */
    private String pesos(BigDecimal monto) {
        if (monto == null) {
            return "-";
        }
        return "$ " + String.format(java.util.Locale.forLanguageTag("es-AR"), "%,.2f", monto);
    }
}
