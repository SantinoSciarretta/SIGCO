package com.sigco.almacenamiento;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

/**
 * Donde se guardan las imagenes del sistema.
 *
 * ------------------------------------------------------------------
 *  Que guarda la base y que guarda esto
 * ------------------------------------------------------------------
 *
 * La base guarda SOLO la referencia al archivo: `pedido.foto_remito`,
 * `gasto.comprobante_adjunto` e `imagen_portfolio.url_imagen` son VARCHAR(255).
 * El binario nunca entra a PostgreSQL. Es lo que dice la Propuesta Tecnica y
 * tiene un motivo practico: las copias de seguridad de la base pasarian de unos
 * megas a varios gigas, y cada consulta que trajera la fila traeria la foto.
 *
 * ------------------------------------------------------------------
 *  Por que una interfaz con dos implementaciones
 * ------------------------------------------------------------------
 *
 * La Propuesta Tecnica define Supabase Storage. Pero atar el codigo a Supabase
 * tiene dos problemas concretos: no se puede desarrollar ni probar sin una
 * cuenta y conexion a internet, y el dia que se cambie de proveedor hay que
 * tocar los tres modulos que suben archivos.
 *
 * Con esta interfaz, el modulo que sube una foto no sabe donde termina:
 *
 *   AlmacenLocal    — desarrollo. Guarda en una carpeta de la maquina.
 *   AlmacenSupabase — produccion. Sube al bucket por su API REST.
 *
 * Cual de las dos se usa lo decide una propiedad de configuracion, no el
 * codigo. Cambiar de una a otra es cambiar el `.env`, sin recompilar nada.
 *
 * ------------------------------------------------------------------
 *  Publico y privado
 * ------------------------------------------------------------------
 *
 * No todos los archivos son iguales y la diferencia es de negocio, no tecnica:
 *
 *   - Las fotos del portfolio las tiene que ver CUALQUIERA: la vidriera es
 *     publica por diseño.
 *   - El remito de un pedido y el comprobante de un gasto son documentacion
 *     interna de la obra. Los ve quien entro al sistema, nadie mas.
 *
 * Por eso cada archivo se guarda en un ambito, y el ambito decide si hace falta
 * estar autenticado para leerlo. Es el mismo modelo de buckets publicos y
 * privados que usa Supabase, que es lo que permite que la implementacion de
 * produccion sea una traduccion directa.
 */
public interface AlmacenDeArchivos {

    /**
     * Guarda un archivo y devuelve la referencia que se escribe en la base.
     *
     * La referencia NO es una ruta del disco ni una URL completa: es un
     * identificador propio del sistema ("privado/remitos/a1b2c3.jpg"). Asi la
     * misma fila sirve con cualquiera de las dos implementaciones, y migrar de
     * local a Supabase no obliga a reescribir las referencias ya guardadas.
     *
     * @param archivo  lo que llego en la peticion
     * @param carpeta  agrupacion dentro del ambito: "remitos", "comprobantes"...
     * @param ambito   quien puede leerlo despues
     */
    String guardar(MultipartFile archivo, String carpeta, Ambito ambito);

    /** Lee un archivo por su referencia. */
    Resource leer(String referencia);

    /**
     * Borra un archivo.
     *
     * Solo lo usa el Portfolio, que es el unico modulo del sistema donde se
     * elimina de verdad: una foto no es el registro de algo que paso, es
     * material de difusion. Un remito o un comprobante no se borran nunca.
     */
    void borrar(String referencia);

    /** Quien puede leer un archivo. */
    enum Ambito {

        /** Lo ve cualquiera, sin autenticarse. Las fotos del portfolio. */
        PUBLICO("publico"),

        /** Solo quien entro al sistema. Remitos y comprobantes de gastos. */
        PRIVADO("privado");

        private final String prefijo;

        Ambito(String prefijo) {
            this.prefijo = prefijo;
        }

        public String getPrefijo() {
            return prefijo;
        }

        /** Deduce el ambito de una referencia ya guardada. */
        public static Ambito de(String referencia) {
            return referencia != null && referencia.startsWith(PUBLICO.prefijo + "/")
                    ? PUBLICO : PRIVADO;
        }
    }
}
