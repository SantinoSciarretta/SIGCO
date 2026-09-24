package com.sigco.compras;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * La conversión de un teléfono argentino al formato de un link de WhatsApp.
 *
 * Es una clase chica con muchos casos, y son justamente los casos los que
 * importan: en la agenda de Granica el mismo celular está escrito de cuatro
 * maneras distintas, y todas tienen que terminar en el mismo número.
 *
 * El número de referencia en todos los tests es el mismo: 11 4567-8900 de CABA,
 * que en formato internacional es 5491145678900.
 */
class NumeroDeWhatsAppTest {

    private static final String ESPERADO = "5491145678900";

    @Nested
    @DisplayName("Las formas en que está escrito el mismo celular")
    class MismoNumero {

        @Test
        @DisplayName("Ya en formato internacional, se deja como está")
        void internacional() {
            assertThat(NumeroDeWhatsApp.normalizar("+54 9 11 4567-8900")).contains(ESPERADO);
            assertThat(NumeroDeWhatsApp.normalizar("5491145678900")).contains(ESPERADO);
        }

        @Test
        @DisplayName("Con el prefijo de salida 00 en lugar del +")
        void conCeroCero() {
            assertThat(NumeroDeWhatsApp.normalizar("005491145678900")).contains(ESPERADO);
        }

        /**
         * El caso que más aparece escrito a mano: el 15 del celular marcado
         * localmente. En formato internacional ese 15 no va — se reemplaza por
         * el 9 después del 54.
         */
        @Test
        @DisplayName("Con el 15 del celular, que se reemplaza por el 9")
        void conElQuince() {
            assertThat(NumeroDeWhatsApp.normalizar("011 15 4567-8900")).contains(ESPERADO);
            assertThat(NumeroDeWhatsApp.normalizar("11 15 4567 8900")).contains(ESPERADO);
            assertThat(NumeroDeWhatsApp.normalizar("(011) 15-4567-8900")).contains(ESPERADO);
        }

        @Test
        @DisplayName("Pelado, sin 0 y sin 15")
        void soloAreaYAbonado() {
            assertThat(NumeroDeWhatsApp.normalizar("1145678900")).contains(ESPERADO);
            assertThat(NumeroDeWhatsApp.normalizar("11 4567-8900")).contains(ESPERADO);
        }

        @Test
        @DisplayName("Con el 0 de larga distancia adelante")
        void conElCeroNacional() {
            assertThat(NumeroDeWhatsApp.normalizar("011 4567-8900")).contains(ESPERADO);
        }

        @Test
        @DisplayName("Con el 9 pero sin el código de país")
        void conNueveSinPais() {
            assertThat(NumeroDeWhatsApp.normalizar("9 11 4567-8900")).contains(ESPERADO);
        }

        @Test
        @DisplayName("Con el 54 pero sin el 9, se le agrega")
        void conPaisSinNueve() {
            // Sin ese 9, WhatsApp no encuentra el celular argentino.
            assertThat(NumeroDeWhatsApp.normalizar("+54 11 4567-8900")).contains(ESPERADO);
        }
    }

    @Nested
    @DisplayName("Códigos de área de distinto largo")
    class OtrasAreas {

        @Test
        @DisplayName("Área de tres dígitos: Córdoba")
        void areaDeTres() {
            // 351 + 7 dígitos, con el 15 en el medio.
            assertThat(NumeroDeWhatsApp.normalizar("0351 15 345-6789")).contains("5493513456789");
        }

        @Test
        @DisplayName("Área de cuatro dígitos")
        void areaDeCuatro() {
            // 2494 + 6 dígitos, con el 15 en el medio.
            assertThat(NumeroDeWhatsApp.normalizar("02494 15 456789")).contains("5492494456789");
        }
    }

    /**
     * Lo más importante de la clase: cuando no se entiende el número, NO se
     * inventa uno probable. Si adivináramos mal, el sistema abriría una
     * conversación con un desconocido y le mandaría el pedido de una obra.
     */
    @Nested
    @DisplayName("Cuando no se entiende, no adivina")
    class NoAdivina {

        @Test
        @DisplayName("Vacío o nulo")
        void vacio() {
            assertThat(NumeroDeWhatsApp.normalizar(null)).isEmpty();
            assertThat(NumeroDeWhatsApp.normalizar("")).isEmpty();
            assertThat(NumeroDeWhatsApp.normalizar("   ")).isEmpty();
        }

        @Test
        @DisplayName("Sin dígitos")
        void sinDigitos() {
            assertThat(NumeroDeWhatsApp.normalizar("preguntar por Jorge")).isEmpty();
        }

        @Test
        @DisplayName("Demasiado corto para ser un teléfono")
        void muyCorto() {
            assertThat(NumeroDeWhatsApp.normalizar("4567-8900")).isEmpty();
            assertThat(NumeroDeWhatsApp.normalizar("123")).isEmpty();
        }

        @Test
        @DisplayName("Demasiado largo")
        void muyLargo() {
            assertThat(NumeroDeWhatsApp.normalizar("11 4567 8900 1234 5678")).isEmpty();
        }

        @Test
        @DisplayName("Doce dígitos pero sin un 15 donde correspondería")
        void doceDigitosSinQuince() {
            // No se puede saber qué sobra: mejor pedir que lo corrijan.
            assertThat(NumeroDeWhatsApp.normalizar("112233445566")).isEmpty();
        }

        @Test
        @DisplayName("De otro país, que no sabemos interpretar")
        void otroPais() {
            // +55 es Brasil. No se toca: se devuelve vacío y se avisa.
            assertThat(NumeroDeWhatsApp.normalizar("+55 11 91234-5678")).isEmpty();
        }
    }
}
