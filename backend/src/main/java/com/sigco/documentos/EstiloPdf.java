package com.sigco.documentos;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import java.awt.Color;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;

/**
 * Lo que comparten todos los documentos en PDF del sistema.
 *
 * Hay tres: el presupuesto que se le manda al cliente, la planilla de pagos y
 * el reporte de gastos. Los tres llevan el mismo membrete, los mismos colores y
 * la misma tipografia, porque los tres salen de la misma empresa.
 *
 * Esta clase existe para que eso sea cierto sin depender de que alguien se
 * acuerde: si el membrete viviera copiado en cada generador, el dia que cambie
 * el nombre de la empresa habria tres lugares para cambiarlo, y el que se
 * olvide va a ser el que menos se mira.
 *
 * Los colores son los mismos del sistema de diseño del frontend
 * (frontend/src/styles/tokens.css): el PDF y la pantalla tienen que verse como
 * la misma cosa.
 */
public final class EstiloPdf {

    private EstiloPdf() {
    }

    public static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    // Paleta, tomada de tokens.css.
    public static final Color ACERO = new Color(0x1d, 0x2d, 0x3d);
    public static final Color GRIS_LINEA = new Color(0xd4, 0xd4, 0xd7);
    public static final Color GRIS_TEXTO = new Color(0x5d, 0x5d, 0x60);
    public static final Color ROJO = new Color(0xa8, 0x3e, 0x32);
    public static final Color VERDE = new Color(0x3f, 0x7d, 0x63);
    public static final Color AMARILLO = new Color(0xc0, 0x8a, 0x2e);

    public static final Font TITULO = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 22, ACERO);
    public static final Font SUBTITULO = FontFactory.getFont(FontFactory.HELVETICA, 9, GRIS_TEXTO);
    public static final Font ETIQUETA = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, GRIS_TEXTO);
    public static final Font TEXTO = FontFactory.getFont(FontFactory.HELVETICA, 10, Color.BLACK);
    public static final Font TEXTO_CHICO = FontFactory.getFont(FontFactory.HELVETICA, 9, GRIS_TEXTO);
    public static final Font DESTACADO = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, Color.BLACK);
    public static final Font TOTAL = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, ACERO);

    /** Membrete de la empresa. El mismo en los tres documentos. */
    public static void membrete(Document documento) throws DocumentException {
        documento.add(new Paragraph("GRANICA SRL", TITULO));

        Paragraph rubro = new Paragraph(
                "Construcción civil, refacción y decoración de locales", SUBTITULO);
        rubro.setSpacingAfter(14);
        documento.add(rubro);

        documento.add(linea());
    }

    /** Linea horizontal fina, del ancho de la hoja. */
    public static Paragraph linea() {
        return new Paragraph(new Chunk(
                new com.lowagie.text.pdf.draw.LineSeparator(0.6f, 100, GRIS_LINEA, 0, -2)));
    }

    /** Una celda con su etiqueta arriba y el valor abajo, sin bordes. */
    public static PdfPCell dato(String etiqueta, String valor) {
        PdfPCell celda = new PdfPCell();
        celda.setBorder(com.lowagie.text.Rectangle.NO_BORDER);
        celda.setPaddingBottom(6);
        celda.addElement(new Paragraph(etiqueta.toUpperCase(), ETIQUETA));
        celda.addElement(new Paragraph(valor == null ? "—" : valor, TEXTO));
        return celda;
    }

    /** Encabezado de columna: texto chico con una linea debajo. */
    public static PdfPCell encabezado(String texto, int alineacion) {
        PdfPCell celda = new PdfPCell(new Phrase(texto, ETIQUETA));
        celda.setBorder(com.lowagie.text.Rectangle.BOTTOM);
        celda.setBorderColor(ACERO);
        celda.setHorizontalAlignment(alineacion);
        celda.setPadding(6);
        return celda;
    }

    /** Celda de texto de una fila de tabla. */
    public static PdfPCell celda(String texto, Font fuente, int alineacion) {
        PdfPCell c = new PdfPCell(new Phrase(texto == null ? "—" : texto, fuente));
        c.setBorder(com.lowagie.text.Rectangle.BOTTOM);
        c.setBorderColor(GRIS_LINEA);
        c.setHorizontalAlignment(alineacion);
        c.setVerticalAlignment(Element.ALIGN_TOP);
        c.setPadding(7);
        return c;
    }

    /** Celda de importe: siempre alineada a la derecha. */
    public static PdfPCell importe(BigDecimal monto, Font fuente) {
        return celda(pesos(monto), fuente, Element.ALIGN_RIGHT);
    }

    /**
     * Importe con separador de miles argentino.
     *
     * Locale explicito y no el del servidor: en Railway el sistema corre en
     * ingles y los miles saldrian con coma, que aca se lee como el separador
     * decimal. Un documento que le llega al cliente con "$ 1,000" donde dice
     * mil pesos es un problema.
     */
    public static String pesos(BigDecimal monto) {
        if (monto == null) {
            return "$ 0";
        }
        return String.format(java.util.Locale.forLanguageTag("es-AR"), "$ %,.2f", monto);
    }
}
