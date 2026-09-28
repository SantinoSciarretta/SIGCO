package com.sigco.documentos;

import static org.assertj.core.api.Assertions.assertThat;

import com.lowagie.text.Document;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Que el membrete salga con el logo, y no con el nombre en texto.
 *
 * El logo se carga del classpath, asi que este test comprueba algo que no se ve
 * mirando el codigo: que el PNG este empaquetado donde el codigo lo busca. Si
 * alguien mueve el archivo de resources, el membrete sigue compilando y sigue
 * emitiendo el PDF, solo que sin logo. Sin este test nadie se entera hasta que
 * un presupuesto le llega asi a un cliente.
 */
class MembreteTest {

    @Test
    void elLogoEstaEmpaquetadoYSeDibujaEnElPdf() throws Exception {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        Document documento = new Document(PageSize.A4, 48, 48, 48, 56);
        PdfWriter.getInstance(documento, salida);
        documento.open();
        EstiloPdf.membrete(documento);
        documento.add(new Paragraph("cuerpo del documento", EstiloPdf.TEXTO));
        documento.close();

        byte[] pdf = salida.toByteArray();
        String crudo = new String(pdf, java.nio.charset.StandardCharsets.ISO_8859_1);

        // Un PDF con una imagen adentro declara un XObject de subtipo Image.
        // Si el logo no se hubiera cargado, el membrete habria caido al texto y
        // este objeto no existiria.
        assertThat(crudo).contains("/Subtype/Image");

        // Y entonces el nombre NO va escrito como texto: lo dice el logo.
        assertThat(crudo).doesNotContain("GRÁNICA");

        // Se deja el PDF a mano para poder mirarlo cuando se toca el membrete.
        Path destino = Path.of(System.getProperty("java.io.tmpdir"), "sigco-membrete.pdf");
        Files.write(destino, pdf);
    }
}
