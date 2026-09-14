package com.sigco.almacenamiento;

import com.sigco.common.exception.RecursoNoEncontradoException;
import com.sigco.common.exception.ReglaDeNegocioException;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * Guarda los archivos en Supabase Storage.
 *
 * Es la implementacion de PRODUCCION, la que define la Propuesta Tecnica. Se
 * activa cuando `sigco.almacenamiento.tipo` vale `supabase`; en desarrollo la
 * propiedad dice `local` y esta clase ni siquiera se instancia.
 *
 * ------------------------------------------------------------------
 *  Por que no se usa el SDK de Supabase
 * ------------------------------------------------------------------
 *
 * Supabase Storage es una API REST comun, y lo que hace falta de ella son tres
 * llamadas: subir, bajar y borrar. El cliente HTTP que ya trae Java alcanza.
 *
 * Sumar el SDK traeria una dependencia mas para mantener y una capa de
 * abstraccion sobre otra abstraccion —esta misma interfaz—, sin resolver nada
 * que estas treinta lineas no resuelvan.
 *
 * ------------------------------------------------------------------
 *  Dos buckets, no uno
 * ------------------------------------------------------------------
 *
 * En Supabase hay que crear DOS buckets, y la separacion es la misma que la del
 * ambito de esta interfaz:
 *
 *   sigco-publico  — marcado como publico en Supabase. Las fotos del portfolio,
 *                    que ve cualquier visitante de la vidriera.
 *   sigco-privado  — privado. Remitos y comprobantes: documentacion interna de
 *                    la obra, que solo ve quien entro al sistema.
 *
 * Los archivos igual se sirven a traves del backend y no con la URL de Supabase
 * directamente. Asi el permiso lo decide el sistema y no la configuracion del
 * bucket, y el dia que se cambie de proveedor las referencias guardadas en la
 * base siguen sirviendo.
 */
@Component
@ConditionalOnProperty(name = "sigco.almacenamiento.tipo", havingValue = "supabase")
public class AlmacenSupabase implements AlmacenDeArchivos {

    private static final Duration ESPERA = Duration.ofSeconds(30);

    private final String urlBase;
    private final String claveDeServicio;
    private final ValidadorDeArchivos validador;
    private final HttpClient cliente;

    public AlmacenSupabase(@Value("${sigco.almacenamiento.supabase.url}") String urlBase,
                           @Value("${sigco.almacenamiento.supabase.clave}") String claveDeServicio,
                           ValidadorDeArchivos validador) {
        // Sin la barra final, para no terminar armando URLs con "//".
        this.urlBase = urlBase.endsWith("/") ? urlBase.substring(0, urlBase.length() - 1) : urlBase;
        this.claveDeServicio = claveDeServicio;
        this.validador = validador;
        this.cliente = HttpClient.newBuilder().connectTimeout(ESPERA).build();
    }

    @Override
    public String guardar(MultipartFile archivo, String carpeta, Ambito ambito) {
        String extension = validador.validar(archivo);
        String nombre = UUID.randomUUID().toString().replace("-", "") + extension;
        String ruta = carpeta + "/" + nombre;

        try {
            HttpRequest peticion = HttpRequest.newBuilder(URI.create(objeto(ambito, ruta)))
                    .header("Authorization", "Bearer " + claveDeServicio)
                    .header("Content-Type", archivo.getContentType())
                    // Sin esto, subir dos veces la misma ruta da error en lugar
                    // de reemplazar. No deberia pasar (el nombre es aleatorio),
                    // pero un reintento no tiene por que fallar.
                    .header("x-upsert", "true")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(archivo.getBytes()))
                    .timeout(ESPERA)
                    .build();

            HttpResponse<String> respuesta = cliente.send(
                    peticion, HttpResponse.BodyHandlers.ofString());

            if (respuesta.statusCode() >= 300) {
                throw new ReglaDeNegocioException(
                        "No se pudo subir el archivo. Probá de nuevo en un momento.");
            }
        } catch (IOException e) {
            throw new ReglaDeNegocioException(
                    "No se pudo subir el archivo: no hay conexión con el almacenamiento.");
        } catch (InterruptedException e) {
            // Restaurar la marca de interrupcion: tragarla deja al hilo sin
            // enterarse de que alguien le pidio que pare.
            Thread.currentThread().interrupt();
            throw new ReglaDeNegocioException("La subida del archivo se interrumpió.");
        }

        // La referencia que se guarda en la base lleva el ambito adelante, igual
        // que en la implementacion local: es lo que permite cambiar de una a
        // otra sin reescribir las filas ya guardadas.
        return ambito.getPrefijo() + "/" + ruta;
    }

    @Override
    public Resource leer(String referencia) {
        Ambito ambito = Ambito.de(referencia);
        String ruta = sinPrefijo(referencia, ambito);

        try {
            HttpRequest peticion = HttpRequest.newBuilder(URI.create(objeto(ambito, ruta)))
                    .header("Authorization", "Bearer " + claveDeServicio)
                    .GET()
                    .timeout(ESPERA)
                    .build();

            HttpResponse<byte[]> respuesta = cliente.send(
                    peticion, HttpResponse.BodyHandlers.ofByteArray());

            if (respuesta.statusCode() == 404) {
                throw new RecursoNoEncontradoException("Archivo", 0L);
            }
            if (respuesta.statusCode() >= 300) {
                throw new ReglaDeNegocioException("No se pudo leer el archivo.");
            }
            return new ByteArrayResource(respuesta.body());

        } catch (IOException e) {
            throw new ReglaDeNegocioException(
                    "No se pudo leer el archivo: no hay conexión con el almacenamiento.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ReglaDeNegocioException("La lectura del archivo se interrumpió.");
        }
    }

    @Override
    public void borrar(String referencia) {
        Ambito ambito = Ambito.de(referencia);
        try {
            HttpRequest peticion = HttpRequest.newBuilder(
                            URI.create(objeto(ambito, sinPrefijo(referencia, ambito))))
                    .header("Authorization", "Bearer " + claveDeServicio)
                    .DELETE()
                    .timeout(ESPERA)
                    .build();
            cliente.send(peticion, HttpResponse.BodyHandlers.discarding());
        } catch (IOException e) {
            // Igual que en la implementacion local: que no se pueda borrar el
            // archivo no debe impedir borrar la fila. Queda un archivo huerfano,
            // que es un problema menor que una imagen que sigue en la vidriera.
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** URL del objeto dentro del bucket que corresponde al ambito. */
    private String objeto(Ambito ambito, String ruta) {
        return urlBase + "/storage/v1/object/sigco-" + ambito.getPrefijo() + "/" + ruta;
    }

    private String sinPrefijo(String referencia, Ambito ambito) {
        String prefijo = ambito.getPrefijo() + "/";
        return referencia.startsWith(prefijo)
                ? referencia.substring(prefijo.length()) : referencia;
    }
}
