package com.sigco.almacenamiento;

import com.sigco.common.exception.RecursoNoEncontradoException;
import com.sigco.common.exception.ReglaDeNegocioException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * Guarda los archivos en una carpeta de la maquina.
 *
 * Es la implementacion de DESARROLLO. Existe para poder trabajar y probar el
 * circuito completo —sacar la foto del remito, verla en la ficha del pedido—
 * sin una cuenta de Supabase ni conexion a internet.
 *
 * Se activa cuando `sigco.almacenamiento.tipo` vale `local`, que es el valor
 * por defecto. En produccion la propiedad dice `supabase` y esta clase ni
 * siquiera se instancia.
 *
 * NO sirve para produccion, y conviene tener claro por que: Railway reinicia
 * los contenedores y el disco se pierde con cada reinicio. Las fotos
 * desaparecerian. Por eso en produccion va Supabase.
 */
@Component
@ConditionalOnProperty(name = "sigco.almacenamiento.tipo", havingValue = "local",
                       matchIfMissing = true)
public class AlmacenLocal implements AlmacenDeArchivos {

    private final Path raiz;
    private final ValidadorDeArchivos validador;

    public AlmacenLocal(@Value("${sigco.almacenamiento.carpeta:archivos}") String carpeta,
                        ValidadorDeArchivos validador) {
        this.raiz = Path.of(carpeta).toAbsolutePath().normalize();
        this.validador = validador;
        try {
            Files.createDirectories(raiz);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "No se pudo crear la carpeta de archivos: " + raiz, e);
        }
    }

    @Override
    public String guardar(MultipartFile archivo, String carpeta, Ambito ambito) {
        String extension = validador.validar(archivo);

        // Nombre al azar y no el original. Dos motivos: dos personas pueden
        // subir "remito.jpg" el mismo dia, y el nombre que elige el usuario
        // puede contener ".." para escribir fuera de la carpeta.
        String nombre = UUID.randomUUID().toString().replace("-", "") + extension;
        String referencia = ambito.getPrefijo() + "/" + carpeta + "/" + nombre;

        Path destino = raiz.resolve(referencia).normalize();
        exigirDentroDeLaRaiz(destino);

        try {
            Files.createDirectories(destino.getParent());
            try (var entrada = archivo.getInputStream()) {
                Files.copy(entrada, destino, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new ReglaDeNegocioException(
                    "No se pudo guardar el archivo. Probá de nuevo.");
        }

        return referencia;
    }

    @Override
    public Resource leer(String referencia) {
        Path archivo = raiz.resolve(referencia).normalize();
        exigirDentroDeLaRaiz(archivo);

        if (!Files.isReadable(archivo)) {
            throw new RecursoNoEncontradoException("Archivo", 0L);
        }
        // FileSystemResource y no PathResource: esa ultima quedo obsoleta en
        // Spring 7 y esta marcada para eliminarse.
        return new FileSystemResource(archivo);
    }

    @Override
    public void borrar(String referencia) {
        Path archivo = raiz.resolve(referencia).normalize();
        exigirDentroDeLaRaiz(archivo);
        try {
            Files.deleteIfExists(archivo);
        } catch (IOException e) {
            // Que no se pueda borrar el archivo no debe impedir borrar la fila:
            // queda un archivo huerfano, que es un problema mucho menor que una
            // imagen que sigue apareciendo en la vidriera.
        }
    }

    /**
     * Impide salir de la carpeta de archivos.
     *
     * Sin esto, una referencia como "../../etc/passwd" leeria cualquier archivo
     * del servidor. Es el ataque de recorrido de rutas, y se corta comparando
     * la ruta ya resuelta contra la raiz: si no empieza con ella, se sale.
     */
    private void exigirDentroDeLaRaiz(Path ruta) {
        if (!ruta.startsWith(raiz)) {
            throw new ReglaDeNegocioException("Referencia de archivo inválida.");
        }
    }
}
