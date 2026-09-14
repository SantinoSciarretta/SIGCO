package com.sigco.almacenamiento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sigco.almacenamiento.AlmacenDeArchivos.Ambito;
import com.sigco.common.exception.RecursoNoEncontradoException;
import com.sigco.common.exception.ReglaDeNegocioException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

/**
 * Tests del almacenamiento de archivos.
 *
 * Se prueba la implementacion LOCAL y no la de Supabase, y es a proposito:
 * probar la de Supabase exigiria una cuenta y conexion a internet, y lo que se
 * estaria probando seria Supabase, no SIGCO.
 *
 * Lo que si vale la pena verificar son las reglas que comparten las dos, porque
 * son las que protegen al sistema:
 *
 *   - que un archivo que no es una imagen no entre
 *   - que renombrar un .exe a .jpg no alcance para colarlo
 *   - que una referencia con ".." no pueda leer archivos fuera de la carpeta
 */
class AlmacenLocalTest {

    @TempDir
    Path carpeta;

    private AlmacenLocal almacen;

    @BeforeEach
    void prepararAlmacen() {
        almacen = new AlmacenLocal(carpeta.toString(), new ValidadorDeArchivos());
    }

    /** Los primeros bytes de un JPEG de verdad. */
    private static MockMultipartFile jpeg(String nombre) {
        byte[] contenido = new byte[] { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x11, 0x22 };
        return new MockMultipartFile("archivo", nombre, "image/jpeg", contenido);
    }

    // ------------------------------------------------------------------
    //  Guardar y leer
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Guarda el archivo y devuelve una referencia con su ámbito")
    void guardaYDevuelveReferencia() throws IOException {
        String referencia = almacen.guardar(jpeg("remito.jpg"), "remitos", Ambito.PRIVADO);

        assertThat(referencia).startsWith("privado/remitos/").endsWith(".jpg");
        assertThat(Files.exists(carpeta.resolve(referencia))).isTrue();
        assertThat(almacen.leer(referencia).exists()).isTrue();
    }

    /**
     * El nombre original no se usa: dos personas pueden subir "remito.jpg" el
     * mismo dia, y el nombre que elige el usuario puede traer sorpresas.
     */
    @Test
    @DisplayName("Dos archivos con el mismo nombre no se pisan")
    void nombresDistintosAunqueElOriginalSeaIgual() {
        String uno = almacen.guardar(jpeg("remito.jpg"), "remitos", Ambito.PRIVADO);
        String otro = almacen.guardar(jpeg("remito.jpg"), "remitos", Ambito.PRIVADO);

        assertThat(uno).isNotEqualTo(otro);
        assertThat(almacen.leer(uno).exists()).isTrue();
        assertThat(almacen.leer(otro).exists()).isTrue();
    }

    @Test
    @DisplayName("El ámbito público queda en su propia carpeta")
    void elAmbitoPublicoSeSepara() {
        String referencia = almacen.guardar(jpeg("obra.jpg"), "portfolio", Ambito.PUBLICO);

        assertThat(referencia).startsWith("publico/portfolio/");
        assertThat(Ambito.de(referencia)).isEqualTo(Ambito.PUBLICO);
    }

    @Test
    @DisplayName("Leer algo que no existe da 404 y no 500")
    void leerInexistente() {
        assertThatThrownBy(() -> almacen.leer("privado/remitos/no-existe.jpg"))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    @DisplayName("Borrar quita el archivo")
    void borrar() {
        String referencia = almacen.guardar(jpeg("foto.jpg"), "portfolio", Ambito.PUBLICO);
        almacen.borrar(referencia);

        assertThat(Files.exists(carpeta.resolve(referencia))).isFalse();
    }

    /** Borrar algo que ya no está no debe fallar: el resultado buscado ya se dio. */
    @Test
    @DisplayName("Borrar dos veces no falla")
    void borrarDosVeces() {
        String referencia = almacen.guardar(jpeg("foto.jpg"), "portfolio", Ambito.PUBLICO);
        almacen.borrar(referencia);
        almacen.borrar(referencia);
    }

    // ------------------------------------------------------------------
    //  Lo que no entra
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Un archivo vacío se rechaza")
    void archivoVacio() {
        MockMultipartFile vacio = new MockMultipartFile(
                "archivo", "nada.jpg", "image/jpeg", new byte[0]);

        assertThatThrownBy(() -> almacen.guardar(vacio, "remitos", Ambito.PRIVADO))
                .isInstanceOf(ReglaDeNegocioException.class);
    }

    @Test
    @DisplayName("Un tipo que no es imagen ni PDF se rechaza")
    void tipoNoAdmitido() {
        MockMultipartFile ejecutable = new MockMultipartFile(
                "archivo", "programa.exe", "application/x-msdownload", new byte[] { 'M', 'Z' });

        assertThatThrownBy(() -> almacen.guardar(ejecutable, "remitos", Ambito.PRIVADO))
                .isInstanceOf(ReglaDeNegocioException.class)
                .hasMessageContaining("Solo se aceptan fotos");
    }

    /**
     * La prueba que justifica mirar los primeros bytes: cambiarle el nombre y el
     * tipo declarado a un archivo es gratis, y la extension deja de decir nada.
     */
    @Test
    @DisplayName("Un ejecutable renombrado a .jpg se rechaza igual")
    void ejecutableDisfrazadoDeImagen() {
        MockMultipartFile disfrazado = new MockMultipartFile(
                "archivo", "remito.jpg", "image/jpeg", new byte[] { 'M', 'Z', 0x00, 0x00 });

        assertThatThrownBy(() -> almacen.guardar(disfrazado, "remitos", Ambito.PRIVADO))
                .isInstanceOf(ReglaDeNegocioException.class)
                .hasMessageContaining("no es del tipo que dice ser");
    }

    @Test
    @DisplayName("Un archivo de más de 8 MB se rechaza")
    void demasiadoGrande() {
        byte[] enorme = new byte[(int) ValidadorDeArchivos.TAMANO_MAXIMO + 1];
        enorme[0] = (byte) 0xFF;
        enorme[1] = (byte) 0xD8;
        enorme[2] = (byte) 0xFF;
        MockMultipartFile grande = new MockMultipartFile(
                "archivo", "gigante.jpg", "image/jpeg", enorme);

        assertThatThrownBy(() -> almacen.guardar(grande, "remitos", Ambito.PRIVADO))
                .isInstanceOf(ReglaDeNegocioException.class)
                .hasMessageContaining("8 MB");
    }

    /**
     * El ataque de recorrido de rutas. Sin la comprobacion, esta referencia
     * leeria cualquier archivo del servidor.
     */
    @Test
    @DisplayName("Una referencia con .. no puede salir de la carpeta")
    void noSePuedeSalirDeLaCarpeta() {
        assertThatThrownBy(() -> almacen.leer("../../../etc/passwd"))
                .isInstanceOf(ReglaDeNegocioException.class)
                .hasMessageContaining("inválida");

        assertThatThrownBy(() -> almacen.leer("privado/../../secreto.txt"))
                .isInstanceOf(ReglaDeNegocioException.class);
    }
}
