package com.sigco.compras;

import java.util.Optional;

/**
 * Convierte un telefono argentino al formato que necesita un link de WhatsApp.
 *
 * ------------------------------------------------------------------
 *  Por que hace falta
 * ------------------------------------------------------------------
 *
 * Un link wa.me necesita el numero internacional completo, solo digitos, sin
 * el "+" ni espacios ni guiones: 5491145678900.
 *
 * En la agenda de Granica los telefonos estan como los escribio alguien a mano:
 * "11 4567-8900", "(011) 15-4567-8900", "+54 9 11 4567 8900". Los tres son el
 * mismo numero y ninguno sirve tal cual.
 *
 * ------------------------------------------------------------------
 *  El 15, que es la parte complicada
 * ------------------------------------------------------------------
 *
 * En Argentina un celular marcado localmente lleva un 15 adelante del abonado,
 * pero en formato internacional ese 15 NO va: se reemplaza por un 9 despues del
 * 54. O sea que "011 15 4567 8900" y "+54 9 11 4567 8900" son el mismo telefono
 * escrito de dos maneras que no se parecen en nada.
 *
 * Sacar el 15 exige saber donde termina el codigo de area, que puede tener 2,
 * 3 o 4 digitos (11 para CABA y GBA, 351 para Cordoba, 2494 para un pueblo).
 * Se prueba en ese orden y funciona porque ningun codigo de area argentino
 * empieza con 15.
 *
 * ------------------------------------------------------------------
 *  Que pasa cuando no se entiende
 * ------------------------------------------------------------------
 *
 * Devuelve vacio. NO inventa un numero probable.
 *
 * Es la decision importante de esta clase: si adivinaramos mal, el sistema
 * abriria una conversacion de WhatsApp con un desconocido y le mandaria el
 * pedido de una obra. Un aviso de "revisa el telefono del proveedor" es
 * infinitamente mejor que eso.
 */
public final class NumeroDeWhatsApp {

    private static final String ARGENTINA = "54";

    /** El 9 que marca "celular" en el formato internacional argentino. */
    private static final String MOVIL = "9";

    /** Codigo de area + abonado, sin pais y sin el 0 de larga distancia. */
    private static final int DIGITOS_NACIONALES = 10;

    /** Lo mismo, pero con el 15 adelante del abonado. */
    private static final int DIGITOS_CON_QUINCE = 12;

    /** Largos de codigo de area que existen en Argentina. */
    private static final int[] LARGOS_DE_AREA = {2, 3, 4};

    private NumeroDeWhatsApp() {
    }

    public static Optional<String> normalizar(String crudo) {
        if (crudo == null || crudo.isBlank()) {
            return Optional.empty();
        }

        String digitos = crudo.replaceAll("\\D", "");

        // Prefijo de salida internacional: "00 54 ..." es lo mismo que "+54...".
        if (digitos.startsWith("00")) {
            digitos = digitos.substring(2);
        }

        if (digitos.startsWith(ARGENTINA)) {
            return conPaisPuesto(digitos.substring(ARGENTINA.length()));
        }

        // El 0 de larga distancia nacional no viaja al formato internacional.
        if (digitos.startsWith("0")) {
            digitos = digitos.substring(1);
        }

        return desdeNumeroNacional(digitos);
    }

    /**
     * El numero ya traia el 54 adelante.
     *
     * Si ademas trae el 9, esta completo y no se toca. Si no lo trae, se
     * agrega: sin ese 9 WhatsApp no encuentra el celular.
     */
    private static Optional<String> conPaisPuesto(String resto) {
        if (resto.startsWith(MOVIL) && resto.length() == DIGITOS_NACIONALES + 1) {
            return Optional.of(ARGENTINA + resto);
        }
        if (resto.length() == DIGITOS_NACIONALES) {
            return Optional.of(ARGENTINA + MOVIL + resto);
        }
        return Optional.empty();
    }

    /** El numero sin pais: puede venir con el 9, con el 15, o pelado. */
    private static Optional<String> desdeNumeroNacional(String digitos) {
        // Alguien escribio el 9 pero se olvido el pais.
        if (digitos.startsWith(MOVIL) && digitos.length() == DIGITOS_NACIONALES + 1) {
            return Optional.of(ARGENTINA + digitos);
        }

        if (digitos.length() == DIGITOS_CON_QUINCE) {
            return sinElQuince(digitos).map(n -> ARGENTINA + MOVIL + n);
        }

        if (digitos.length() == DIGITOS_NACIONALES) {
            return Optional.of(ARGENTINA + MOVIL + digitos);
        }

        return Optional.empty();
    }

    /**
     * Saca el 15 que va entre el codigo de area y el abonado.
     *
     * Se prueban los tres largos de area posibles en orden. No hay ambiguedad
     * porque ningun codigo de area argentino empieza con 15, asi que el primer
     * "15" que aparece en una de esas posiciones es el prefijo de celular.
     */
    private static Optional<String> sinElQuince(String digitos) {
        for (int largoDeArea : LARGOS_DE_AREA) {
            if (digitos.startsWith("15", largoDeArea)) {
                return Optional.of(digitos.substring(0, largoDeArea)
                                   + digitos.substring(largoDeArea + 2));
            }
        }
        return Optional.empty();
    }
}
