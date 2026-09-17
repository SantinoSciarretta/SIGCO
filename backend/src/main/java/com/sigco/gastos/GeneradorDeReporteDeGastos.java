package com.sigco.gastos;

import static com.sigco.documentos.EstiloPdf.ACERO;
import static com.sigco.documentos.EstiloPdf.AMARILLO;
import static com.sigco.documentos.EstiloPdf.DESTACADO;
import static com.sigco.documentos.EstiloPdf.ETIQUETA;
import static com.sigco.documentos.EstiloPdf.FECHA;
import static com.sigco.documentos.EstiloPdf.ROJO;
import static com.sigco.documentos.EstiloPdf.TEXTO;
import static com.sigco.documentos.EstiloPdf.TEXTO_CHICO;
import static com.sigco.documentos.EstiloPdf.TOTAL;
import static com.sigco.documentos.EstiloPdf.VERDE;
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
import com.sigco.gastos.dto.EstadoFinanciero;
import com.sigco.gastos.dto.EstadoFinanciero.RubroFinanciero;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Reporte de gastos de una obra, en PDF.
 *
 * Es un pendiente que el informe pide para el modulo Gastos. Reemplaza al
 * momento en que el dueño arma a mano un resumen para revisar como viene la
 * obra, o para mostrarselo a alguien.
 *
 * ------------------------------------------------------------------
 *  Este documento es INTERNO
 * ------------------------------------------------------------------
 *
 * A diferencia del presupuesto y la planilla de pagos, este no sale de la
 * empresa: muestra la ganancia estimada y el detalle de lo que se gasto, que es
 * exactamente lo que el cliente no tiene por que ver.
 *
 * Por eso lleva una marca que lo dice. No la hace segura —quien tiene el PDF lo
 * puede mandar igual—, pero evita que alguien lo reenvie sin darse cuenta de lo
 * que esta mandando.
 */
@Component
public class GeneradorDeReporteDeGastos {

    private static final Font MARCA_INTERNA =
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, ROJO);

    private final GastoService gastoService;

    public GeneradorDeReporteDeGastos(GastoService gastoService) {
        this.gastoService = gastoService;
    }

    public byte[] generar(Long idObra) {
        // El estado financiero lo calcula Gastos, no este generador: el
        // semaforo y los totales tienen que ser los MISMOS que muestra la
        // pantalla. Si el PDF los recalculara, tarde o temprano dirian cosas
        // distintas y nadie sabria cual creer.
        EstadoFinanciero estado = gastoService.estadoFinanciero(idObra);
        List<GastoRespuestaLigera> gastos = listarGastos(idObra);

        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        Document documento = new Document(PageSize.A4, 48, 48, 48, 56);

        try {
            PdfWriter.getInstance(documento, salida);
            documento.open();

            membrete(documento);
            escribirDatos(documento, estado);
            escribirResumen(documento, estado);
            escribirRubros(documento, estado);
            escribirDetalle(documento, gastos);
            escribirPie(documento);

            documento.close();
        } catch (DocumentException e) {
            throw new IllegalStateException("No se pudo generar el reporte de gastos", e);
        }

        return salida.toByteArray();
    }

    // ------------------------------------------------------------------

    private void escribirDatos(Document documento, EstadoFinanciero estado)
            throws DocumentException {

        Paragraph titulo = new Paragraph("REPORTE DE GASTOS", ETIQUETA);
        titulo.setSpacingBefore(14);
        documento.add(titulo);

        Paragraph interno = new Paragraph("DOCUMENTO INTERNO · NO ENVIAR AL CLIENTE",
                MARCA_INTERNA);
        interno.setSpacingBefore(3);
        documento.add(interno);

        PdfPTable datos = new PdfPTable(2);
        datos.setWidthPercentage(100);
        datos.setSpacingBefore(10);
        datos.setSpacingAfter(14);

        datos.addCell(dato("Obra", estado.direccionObra()));
        datos.addCell(dato("Emitido", LocalDate.now().format(FECHA)));
        datos.addCell(dato("Estado", estado.estadoObra()));
        datos.addCell(dato("Consumido del presupuesto",
                estado.porcentajeConsumido() + "% · " + estado.semaforoGeneral()));

        documento.add(datos);
    }

    private void escribirResumen(Document documento, EstadoFinanciero estado)
            throws DocumentException {

        PdfPTable resumen = new PdfPTable(2);
        resumen.setWidthPercentage(100);
        resumen.setSpacingBefore(4);
        resumen.setSpacingAfter(18);

        resumen.addCell(celda("Presupuestado", TEXTO_CHICO, Element.ALIGN_LEFT));
        resumen.addCell(importe(estado.totalPresupuestado(), TEXTO));
        resumen.addCell(celda("Gastado", TEXTO_CHICO, Element.ALIGN_LEFT));
        resumen.addCell(importe(estado.totalGastado(), TEXTO));

        // La ganancia en rojo cuando es negativa. Es el numero que decide si la
        // obra sirvio o no, y tiene que saltar a la vista.
        boolean pierde = estado.gananciaEstimada().signum() < 0;
        resumen.addCell(celda("Ganancia estimada", ETIQUETA, Element.ALIGN_LEFT));
        resumen.addCell(importe(estado.gananciaEstimada(),
                pierde ? FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, ROJO) : TOTAL));

        if (estado.totalGastoHormiga().signum() > 0) {
            resumen.addCell(celda("De eso, gastos hormiga", TEXTO_CHICO, Element.ALIGN_LEFT));
            resumen.addCell(importe(estado.totalGastoHormiga(), TEXTO_CHICO));
        }

        documento.add(resumen);
    }

    private void escribirRubros(Document documento, EstadoFinanciero estado)
            throws DocumentException {

        Paragraph titulo = new Paragraph("POR RUBRO", ETIQUETA);
        titulo.setSpacingBefore(6);
        documento.add(titulo);

        PdfPTable tabla = new PdfPTable(5);
        tabla.setWidthPercentage(100);
        tabla.setWidths(new float[]{2.4f, 1.6f, 1.6f, 1.6f, 1.4f});
        tabla.setSpacingBefore(6);
        tabla.setSpacingAfter(18);

        tabla.addCell(encabezado("Rubro", Element.ALIGN_LEFT));
        tabla.addCell(encabezado("Presupuestado", Element.ALIGN_RIGHT));
        tabla.addCell(encabezado("Gastado", Element.ALIGN_RIGHT));
        tabla.addCell(encabezado("Diferencia", Element.ALIGN_RIGHT));
        tabla.addCell(encabezado("Estado", Element.ALIGN_LEFT));

        for (RubroFinanciero rubro : estado.rubros()) {
            tabla.addCell(celda(rubro.nombreRubro(), TEXTO, Element.ALIGN_LEFT));
            tabla.addCell(importe(rubro.presupuestado(), TEXTO_CHICO));
            tabla.addCell(importe(rubro.gastado(), DESTACADO));
            tabla.addCell(importe(rubro.diferencia(),
                    rubro.diferencia().signum() < 0
                            ? FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, ROJO)
                            : TEXTO_CHICO));
            // El estado va ESCRITO y no solo en color: un PDF se imprime, y en
            // blanco y negro el color no dice nada.
            tabla.addCell(celda(rubro.semaforo(),
                    FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9,
                            colorDeSemaforo(rubro.semaforo())),
                    Element.ALIGN_LEFT));
        }

        documento.add(tabla);
    }

    /**
     * El detalle de cada gasto.
     *
     * Es la parte que responde "¿en qué se fue la plata?", que es la pregunta
     * que el relevamiento marca como imposible de contestar hoy.
     */
    private void escribirDetalle(Document documento, List<GastoRespuestaLigera> gastos)
            throws DocumentException {

        Paragraph titulo = new Paragraph("DETALLE", ETIQUETA);
        documento.add(titulo);

        if (gastos.isEmpty()) {
            Paragraph vacio = new Paragraph("Esta obra todavía no tiene gastos cargados.",
                    TEXTO_CHICO);
            vacio.setSpacingBefore(8);
            documento.add(vacio);
            return;
        }

        PdfPTable tabla = new PdfPTable(5);
        tabla.setWidthPercentage(100);
        tabla.setWidths(new float[]{1.3f, 1.6f, 1.5f, 3.2f, 1.6f});
        tabla.setSpacingBefore(6);

        tabla.addCell(encabezado("Fecha", Element.ALIGN_LEFT));
        tabla.addCell(encabezado("Rubro", Element.ALIGN_LEFT));
        tabla.addCell(encabezado("Tipo", Element.ALIGN_LEFT));
        tabla.addCell(encabezado("Descripción", Element.ALIGN_LEFT));
        tabla.addCell(encabezado("Importe", Element.ALIGN_RIGHT));

        for (GastoRespuestaLigera g : gastos) {
            tabla.addCell(celda(g.fecha().format(FECHA), TEXTO_CHICO, Element.ALIGN_LEFT));
            tabla.addCell(celda(g.rubro(), TEXTO_CHICO, Element.ALIGN_LEFT));
            tabla.addCell(celda(g.tipo(), TEXTO_CHICO, Element.ALIGN_LEFT));
            tabla.addCell(celda(g.descripcion(), TEXTO_CHICO, Element.ALIGN_LEFT));
            tabla.addCell(importe(g.monto(), TEXTO));
        }

        documento.add(tabla);
    }

    private void escribirPie(Document documento) throws DocumentException {
        Paragraph pie = new Paragraph(
                "Solo se incluyen los gastos confirmados. Los anulados quedan registrados "
                + "en el sistema con su motivo, pero no suman al total.", TEXTO_CHICO);
        pie.setSpacingBefore(24);
        documento.add(pie);
    }

    // ------------------------------------------------------------------

    private Color colorDeSemaforo(String semaforo) {
        return switch (semaforo) {
            case GastoService.SEMAFORO_ROJO -> ROJO;
            case GastoService.SEMAFORO_AMARILLO -> AMARILLO;
            case GastoService.SEMAFORO_VERDE -> VERDE;
            default -> ACERO;
        };
    }

    /** Lo que el reporte necesita de cada gasto, y nada mas. */
    public record GastoRespuestaLigera(
            LocalDate fecha, String rubro, String tipo,
            String descripcion, BigDecimal monto) {
    }

    private List<GastoRespuestaLigera> listarGastos(Long idObra) {
        // Solo los confirmados: los anulados no suman al total y mezclarlos en
        // el detalle haria que las lineas no cierren con el resumen de arriba.
        return gastoService.listar(idObra, null, null, Gasto.ESTADO_CONFIRMADO, null, null)
                .stream()
                .map(g -> new GastoRespuestaLigera(
                        g.fechaGasto(), g.nombreRubro(), g.tipoGasto(),
                        g.descripcion() != null ? g.descripcion() : "—", g.monto()))
                .toList();
    }

    /** Nombre del archivo al bajarlo. */
    public String nombreDeArchivo(EstadoFinanciero estado) {
        String direccion = estado.direccionObra()
                .replaceAll("[^a-zA-Z0-9]+", "-")
                .replaceAll("(^-|-$)", "")
                .toLowerCase(java.util.Locale.ROOT);
        return "gastos-" + direccion + ".pdf";
    }
}
