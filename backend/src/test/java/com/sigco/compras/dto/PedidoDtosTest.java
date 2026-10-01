package com.sigco.compras.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.sigco.compras.dto.PedidoDtos.Recepcion;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * La foto del remito tenía @NotNull en vez de @NotBlank: un string vacío
 * ("") no es null, así que pasaba la validación igual, y un pedido podía
 * quedar "Recibido Completo" sin foto real, justo lo que esta validación
 * existe para impedir.
 */
class PedidoDtosTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void crearValidador() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void cerrarValidador() {
        factory.close();
    }

    @Test
    @DisplayName("Una foto de remito vacía se rechaza, no solo null")
    void rechazaFotoVacia() {
        Recepcion recepcion = new Recepcion("", null);

        Set<ConstraintViolation<Recepcion>> violaciones = validator.validate(recepcion);

        assertThat(violaciones).isNotEmpty();
        assertThat(violaciones)
                .anyMatch(v -> v.getPropertyPath().toString().equals("fotoRemito"));
    }

    @Test
    @DisplayName("Una foto de remito de solo espacios también se rechaza")
    void rechazaFotoDeSoloEspacios() {
        Recepcion recepcion = new Recepcion("   ", null);

        Set<ConstraintViolation<Recepcion>> violaciones = validator.validate(recepcion);

        assertThat(violaciones).anyMatch(v -> v.getPropertyPath().toString().equals("fotoRemito"));
    }

    @Test
    @DisplayName("Una foto de remito con contenido pasa la validación")
    void aceptaFotoConContenido() {
        Recepcion recepcion = new Recepcion("remitos/r-2026-10-01.jpg", null);

        Set<ConstraintViolation<Recepcion>> violaciones = validator.validate(recepcion);

        assertThat(violaciones).isEmpty();
    }
}
