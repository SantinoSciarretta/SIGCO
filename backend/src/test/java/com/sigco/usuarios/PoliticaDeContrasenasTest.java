package com.sigco.usuarios;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sigco.common.exception.ReglaDeNegocioException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests de la politica de contrasenas.
 *
 * Lo que se prueba no es "la contrasena tiene diez caracteres" —de eso se
 * encarga @Size en el DTO— sino lo que el largo no filtra: las contrasenas
 * previsibles, que son las que alguien pone al apuro cuando el sistema lo
 * obliga a cambiarla en el primer ingreso.
 */
class PoliticaDeContrasenasTest {

    private final PoliticaDeContrasenas politica = new PoliticaDeContrasenas();

    @Test
    @DisplayName("Una contraseña común y corriente se acepta")
    void aceptaUnaRazonable() {
        assertThatCode(() -> politica.validar("melonVerde47", "ricardo"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Rechaza las que nombran a la empresa")
    void rechazaLasDeLaEmpresa() {
        assertThatThrownBy(() -> politica.validar("granica2026", "ricardo"))
                .isInstanceOf(ReglaDeNegocioException.class)
                .hasMessageContaining("previsible");

        assertThatThrownBy(() -> politica.validar("sigco123456", "ricardo"))
                .isInstanceOf(ReglaDeNegocioException.class);
    }

    /**
     * Sin normalizar acentos y mayusculas, escribir "Gránica" esquivaria la
     * lista escribiendo distinto la misma palabra.
     */
    @Test
    @DisplayName("La lista no se esquiva con mayúsculas ni acentos")
    void noSeEsquivaConAcentos() {
        assertThatThrownBy(() -> politica.validar("GRÁNICA2026", "ricardo"))
                .isInstanceOf(ReglaDeNegocioException.class);
    }

    @Test
    @DisplayName("Rechaza la contraseña que contiene el propio nombre de usuario")
    void rechazaElNombreDeUsuario() {
        assertThatThrownBy(() -> politica.validar("ricardo2026", "ricardo"))
                .isInstanceOf(ReglaDeNegocioException.class)
                .hasMessageContaining("nombre de usuario");
    }

    @Test
    @DisplayName("Rechaza secuencias del teclado")
    void rechazaSecuencias() {
        // Una secuencia que NO esta en la lista de prohibidas: asi se verifica
        // que la detecta el analisis de secuencia y no la coincidencia literal.
        assertThatThrownBy(() -> politica.validar("defghijklm", "ricardo"))
                .isInstanceOf(ReglaDeNegocioException.class)
                .hasMessageContaining("secuencia");

        // "1234567890" cae antes en la lista de prohibidas, con otro mensaje.
        // Lo que importa es que no pase.
        assertThatThrownBy(() -> politica.validar("1234567890", "ricardo"))
                .isInstanceOf(ReglaDeNegocioException.class);
    }

    @Test
    @DisplayName("Rechaza repetir siempre los mismos caracteres")
    void rechazaRepeticiones() {
        assertThatThrownBy(() -> politica.validar("aaaaaaaaaa", "ricardo"))
                .isInstanceOf(ReglaDeNegocioException.class)
                .hasMessageContaining("mismos caracteres");

        assertThatThrownBy(() -> politica.validar("ababababab", "ricardo"))
                .isInstanceOf(ReglaDeNegocioException.class);
    }

    @Test
    @DisplayName("Un nombre de usuario muy corto no bloquea contraseñas legítimas")
    void nombreCortoNoBloquea() {
        // Con un usuario de dos letras, exigir que la contrasena no lo contenga
        // dejaria afuera casi cualquier palabra. Por eso solo se compara a
        // partir de tres caracteres.
        assertThatCode(() -> politica.validar("melonVerde47", "jo"))
                .doesNotThrowAnyException();
    }
}
