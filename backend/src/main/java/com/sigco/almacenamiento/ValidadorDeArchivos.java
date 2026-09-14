package com.sigco.almacenamiento;

import com.sigco.common.exception.ReglaDeNegocioException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * Comprueba que lo que llega sea realmente una imagen aceptable.
 *
 * Vive aparte de las implementaciones del almacen porque la regla es la misma
 * guarde donde guarde: si estuviera dentro de cada una, habria que acordarse de
 * repetirla, y la de Supabase —que es la que corre en produccion— es
 * justamente la que menos se prueba.
 *
 * ------------------------------------------------------------------
 *  Por que no alcanza con mirar la extension
 * ------------------------------------------------------------------
 *
 * Cambiarle el nombre a un archivo es gratis: "virus.exe" pasa a
 * "remito.jpg" y la extension deja de decir nada. Por eso se verifica el tipo
 * declarado Y los primeros bytes del archivo, que son los que de verdad
 * identifican el formato y no se pueden renombrar.
 */
@Component
public class ValidadorDeArchivos {

    /**
     * 8 MB. Una foto de celular ronda los 3 o 4; el limite deja margen sin
     * permitir que alguien suba un video de media hora al servidor.
     */
    public static final long TAMANO_MAXIMO = 8L * 1024 * 1024;

    private static final List<String> TIPOS = List.of(
            "image/jpeg", "image/png", "image/webp", "application/pdf");

    /**
     * Firma de cada formato: los primeros bytes del archivo.
     *
     * Son los valores del estandar de cada formato. Un JPEG siempre empieza con
     * FF D8 FF, un PNG con 89 'P' 'N' 'G'. No se pueden falsificar cambiandole
     * el nombre al archivo.
     */
    private static final Map<String, byte[]> FIRMAS = Map.of(
            "image/jpeg", new byte[] { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF },
            "image/png", new byte[] { (byte) 0x89, 'P', 'N', 'G' },
            "image/webp", new byte[] { 'R', 'I', 'F', 'F' },
            "application/pdf", new byte[] { '%', 'P', 'D', 'F' });

    /** Valida y devuelve la extension que le corresponde. */
    public String validar(MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) {
            throw new ReglaDeNegocioException("No llegó ningún archivo.");
        }

        if (archivo.getSize() > TAMANO_MAXIMO) {
            throw new ReglaDeNegocioException(
                    "El archivo pesa más de 8 MB. Sacá la foto con menos calidad "
                    + "o achicala antes de subirla.");
        }

        String tipo = archivo.getContentType() != null
                ? archivo.getContentType().toLowerCase(Locale.ROOT) : "";

        if (!TIPOS.contains(tipo)) {
            throw new ReglaDeNegocioException(
                    "Solo se aceptan fotos (JPG, PNG o WEBP) y archivos PDF.");
        }

        if (!coincideLaFirma(archivo, tipo)) {
            throw new ReglaDeNegocioException(
                    "El archivo no es del tipo que dice ser.");
        }

        return extensionDe(tipo);
    }

    private boolean coincideLaFirma(MultipartFile archivo, String tipo) {
        byte[] esperada = FIRMAS.get(tipo);
        if (esperada == null) {
            return false;
        }
        try {
            byte[] inicio = new byte[esperada.length];
            int leidos = archivo.getInputStream().read(inicio);
            if (leidos < esperada.length) {
                return false;
            }
            for (int i = 0; i < esperada.length; i++) {
                if (inicio[i] != esperada[i]) {
                    return false;
                }
            }
            return true;
        } catch (java.io.IOException e) {
            // No poder leer el archivo es motivo suficiente para rechazarlo.
            return false;
        }
    }

    private String extensionDe(String tipo) {
        return switch (tipo) {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            case "application/pdf" -> ".pdf";
            default -> ".jpg";
        };
    }

    /** Tipo de contenido a partir de la referencia, para servir el archivo. */
    public String tipoDe(String referencia) {
        String r = referencia == null ? "" : referencia.toLowerCase(Locale.ROOT);
        if (r.endsWith(".png")) return "image/png";
        if (r.endsWith(".webp")) return "image/webp";
        if (r.endsWith(".pdf")) return "application/pdf";
        return "image/jpeg";
    }
}
