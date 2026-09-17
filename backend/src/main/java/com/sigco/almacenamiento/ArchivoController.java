package com.sigco.almacenamiento;

import com.sigco.almacenamiento.AlmacenDeArchivos.Ambito;
import com.sigco.common.exception.ReglaDeNegocioException;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Subida y lectura de archivos.
 *
 * Es transversal a tres modulos —Compras sube el remito, Gastos el comprobante
 * y Portfolio las fotos— y por eso vive aparte y no dentro de ninguno.
 *
 * ------------------------------------------------------------------
 *  Dos rutas de lectura, y la diferencia importa
 * ------------------------------------------------------------------
 *
 *   GET /api/archivos/publico/**  — abierto. Las fotos del portfolio: la
 *                                   vidriera la ve cualquier visitante, asi que
 *                                   sus imagenes tambien tienen que verse sin
 *                                   estar autenticado.
 *   GET /api/archivos/privado/**  — requiere sesion. El remito de un pedido y
 *                                   el comprobante de un gasto son
 *                                   documentacion interna de la obra.
 *
 * La separacion esta en la RUTA y no en un parametro a proposito: asi la
 * configuracion de seguridad puede abrir una y cerrar la otra con una linea, en
 * lugar de tener que mirar el contenido de cada peticion.
 *
 * Es el mismo criterio con el que Portfolio separo `/api/vidriera` de
 * `/api/portfolio` desde el primer dia.
 */
@RestController
public class ArchivoController {

    /**
     * Donde puede guardar cada modulo.
     *
     * Lista cerrada y no texto libre: si la carpeta llegara suelta desde la
     * peticion, cualquiera podria inventar una, y una carpeta "publico/remitos"
     * dejaria los remitos a la vista de cualquiera.
     */
    private static final Map<String, Ambito> CARPETAS = Map.of(
            "remitos", Ambito.PRIVADO,
            "comprobantes", Ambito.PRIVADO,
            "portfolio", Ambito.PUBLICO);

    private final AlmacenDeArchivos almacen;
    private final ValidadorDeArchivos validador;

    /** Para no borrar un archivo que algun registro este usando. */
    private final ArchivosEnUso enUso;

    public ArchivoController(AlmacenDeArchivos almacen, ValidadorDeArchivos validador,
                             ArchivosEnUso enUso) {
        this.almacen = almacen;
        this.validador = validador;
        this.enUso = enUso;
    }

    /**
     * POST /api/archivos — sube un archivo y devuelve su referencia.
     *
     * Devuelve la referencia, no guarda nada en ninguna tabla: quien sube la
     * foto del remito la manda despues en la recepcion del pedido, que es donde
     * el modulo Compras aplica sus reglas.
     *
     * Separar las dos cosas evita un problema concreto: si la subida guardara
     * la referencia en el pedido, una foto subida por error quedaria pegada al
     * pedido aunque la recepcion nunca se confirme.
     */
    @PostMapping("/api/archivos")
    @PreAuthorize("isAuthenticated()")
    public Map<String, String> subir(@RequestParam MultipartFile archivo,
                                     @RequestParam String carpeta) {
        Ambito ambito = CARPETAS.get(carpeta);
        if (ambito == null) {
            throw new ReglaDeNegocioException(
                    "Carpeta no válida. Las admitidas son: " + CARPETAS.keySet());
        }

        String referencia = almacen.guardar(archivo, carpeta, ambito);
        return Map.of("referencia", referencia);
    }

    /**
     * DELETE /api/archivos — borra un archivo que no usa nadie.
     *
     * Existe por el otro lado del POST de arriba: como el archivo se sube antes
     * de guardar el formulario, quien sube una foto y despues se arrepiente deja
     * un archivo que no referencia ningun registro. Sin esto, cada foto
     * descartada se queda ocupando lugar para siempre.
     *
     * La regla que lo vuelve seguro esta en ArchivosEnUso: solo borra si NINGUNA
     * tabla apunta a esa referencia. Un remito ya confirmado o una imagen del
     * portfolio no se pueden borrar por aca, aunque alguien mande su referencia
     * a proposito. Borrarlas es tarea de su modulo, que ademas limpia el
     * registro que las nombra.
     *
     * La referencia va como parametro y no en la ruta porque contiene barras
     * ("privado/remitos/a1b2.jpg") y una ruta con barras adentro se vuelve
     * ambigua de mapear.
     */
    @DeleteMapping("/api/archivos")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> borrar(@RequestParam String referencia) {
        if (enUso.estaEnUso(referencia)) {
            throw new ReglaDeNegocioException(
                    "Ese archivo está adjunto a un registro del sistema. "
                    + "Para quitarlo hay que editar el registro que lo usa.");
        }

        almacen.borrar(referencia);
        return ResponseEntity.noContent().build();
    }

    /** GET /api/archivos/publico/** — imagenes del portfolio, sin autenticacion. */
    @GetMapping("/api/archivos/publico/{*ruta}")
    public ResponseEntity<Resource> publico(@PathVariable String ruta) {
        return servir(Ambito.PUBLICO.getPrefijo() + ruta);
    }

    /** GET /api/archivos/privado/** — remitos y comprobantes, con sesion. */
    @GetMapping("/api/archivos/privado/{*ruta}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Resource> privado(@PathVariable String ruta) {
        return servir(Ambito.PRIVADO.getPrefijo() + ruta);
    }

    private ResponseEntity<Resource> servir(String referencia) {
        Resource recurso = almacen.leer(referencia);

        return ResponseEntity.ok()
                .header("Content-Type", validador.tipoDe(referencia))
                // El nombre del archivo es un identificador al azar que no se
                // reutiliza: una vez subido, su contenido no cambia nunca. Por
                // eso el navegador lo puede guardar sin volver a preguntar.
                .cacheControl(CacheControl.maxAge(java.time.Duration.ofDays(30)).cachePublic())
                // inline y no attachment: se muestra en la pantalla, no se baja.
                .header("Content-Disposition", "inline")
                .body(recurso);
    }

    /** GET /api/archivos/carpetas — las carpetas validas. Para el frontend. */
    @GetMapping("/api/archivos/carpetas")
    @PreAuthorize("isAuthenticated()")
    public List<String> carpetas() {
        return CARPETAS.keySet().stream().sorted().toList();
    }
}
