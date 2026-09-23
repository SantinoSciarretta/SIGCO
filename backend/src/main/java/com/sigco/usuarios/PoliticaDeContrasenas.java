package com.sigco.usuarios;

import com.sigco.common.exception.ReglaDeNegocioException;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Rechaza las contrasenas que no protegen nada.
 *
 * ------------------------------------------------------------------
 *  Por que no alcanza con exigir un largo minimo
 * ------------------------------------------------------------------
 *
 * El largo lo valida el DTO con @Size, y esa es la primera barrera. Pero una
 * contrasena de diez caracteres puede ser "granica2026", "1234567890" o el
 * propio nombre de usuario repetido, y ninguna de esas resiste treinta
 * intentos de alguien que conozca la empresa.
 *
 * Importa especialmente por el momento en que se usa: el sistema OBLIGA a
 * cambiar la contrasena en el primer ingreso, y es justo cuando a cualquiera le
 * tienta poner lo primero que se le ocurra para salir del paso.
 *
 * ------------------------------------------------------------------
 *  Por que NO se exigen mayusculas, numeros y simbolos
 * ------------------------------------------------------------------
 *
 * Es la regla mas comun y la que peor funciona: empuja a todo el mundo a la
 * misma contrasena previsible —una mayuscula al principio, un numero y un signo
 * al final— y termina anotada en un papel al lado de la computadora. "Granica
 * 2026!" cumple todas las reglas de complejidad y es exactamente la primera que
 * probaria alguien que conoce la empresa.
 *
 * Lo que de verdad ayuda es lo que se hace aca: pedir largo y descartar las
 * previsibles. Es el criterio que recomiendan hoy las guias de seguridad.
 */
@Component
public class PoliticaDeContrasenas {

    /**
     * Las que no se aceptan nunca.
     *
     * No pretende ser una lista completa —para eso haria falta un diccionario de
     * millones de entradas— sino cubrir lo que alguien de esta empresa pondria
     * al apuro: lo obvio universal y el nombre de la empresa.
     *
     * Solo palabras ESPECIFICAS. Las genericas del rubro no entran, y la primera
     * version se equivoco en esto: tenia "obra" y "constructora", y con eso
     * rechazaba "obraPilar7742", que es una contrasena perfectamente razonable.
     * En una constructora, "obra" aparece en cualquier cosa que alguien elija.
     * Una lista que rechaza contrasenas buenas empuja a la gente a inventar
     * peores, que es justo lo contrario de lo que se busca.
     */
    private static final List<String> PROHIBIDAS = List.of(
            "contrasena", "contraseña", "password", "123456", "1234567890",
            "qwerty", "admin", "usuario", "granica", "sigco");

    /**
     * Valida la contrasena elegida.
     *
     * @param contrasena    la elegida, en claro
     * @param nombreUsuario para rechazar que la contrasena sea el propio nombre
     */
    public void validar(String contrasena, String nombreUsuario) {
        String normalizada = normalizar(contrasena);

        // Todo un mismo caracter: "aaaaaaaaaa" pasa cualquier largo minimo.
        if (normalizada.chars().distinct().count() < 4) {
            throw new ReglaDeNegocioException(
                    "Esa contraseña repite siempre los mismos caracteres. "
                    + "Elegí una con más variedad.");
        }

        // Una secuencia del teclado o de numeros seguidos.
        if (esSecuencia(normalizada)) {
            throw new ReglaDeNegocioException(
                    "Esa contraseña es una secuencia de teclas seguidas. "
                    + "Es de las primeras que se prueban.");
        }

        for (String prohibida : PROHIBIDAS) {
            if (normalizada.contains(prohibida)) {
                throw new ReglaDeNegocioException(
                        "Esa contraseña es demasiado previsible: contiene \""
                        + prohibida + "\". Elegí otra.");
            }
        }

        // El propio nombre de usuario. Es publico dentro del sistema, asi que
        // usarlo de contrasena equivale a no tener ninguna.
        if (nombreUsuario != null && !nombreUsuario.isBlank()) {
            String usuario = normalizar(nombreUsuario);
            if (usuario.length() >= 3 && normalizada.contains(usuario)) {
                throw new ReglaDeNegocioException(
                        "La contraseña no puede contener tu nombre de usuario.");
            }
        }
    }

    /**
     * Pasa todo a minusculas y saca los acentos.
     *
     * Sin esto, "Granica2026" y "gránica2026" esquivarian la lista de
     * prohibidas escribiendo distinto la misma palabra.
     */
    private String normalizar(String texto) {
        return Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .toLowerCase(Locale.ROOT);
    }

    /** Detecta "123456", "abcdef" y similares: cada caracter sigue al anterior. */
    private boolean esSecuencia(String texto) {
        if (texto.length() < 4) {
            return false;
        }
        boolean ascendente = true;
        boolean descendente = true;

        for (int i = 1; i < texto.length(); i++) {
            int salto = texto.charAt(i) - texto.charAt(i - 1);
            if (salto != 1) {
                ascendente = false;
            }
            if (salto != -1) {
                descendente = false;
            }
        }
        return ascendente || descendente;
    }
}
