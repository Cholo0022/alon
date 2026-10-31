package ar.com.alon.security;

import static org.assertj.core.api.Assertions.*;

import ar.com.alon.storefront.ShopAccountService;
import org.junit.jupiter.api.Test;

class ShopPasswordPolicyTest {
    /** Comprueba los límites de las contraseñas de comprador, incluidos ocho dígitos numéricos. */
    @Test
    void registrationAcceptsEightAndRejectsSevenOrMoreThan128() {
        try (var factory = jakarta.validation.Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            for (String password : new String[] {"12345678", "abcdefgh", "x".repeat(128)})
                assertThat(validator.validate(input(password))).isEmpty();
            for (String password : new String[] {"1234567", "", "x".repeat(129)})
                assertThat(validator.validate(input(password)))
                        .anyMatch(error -> error.getPropertyPath().toString().equals("password"));
        }
    }

    /**
     * Construye una solicitud de registro con datos de comprador válidos para aislar la contraseña.
     */
    private ShopAccountService.Registration input(String password) {
        return new ShopAccountService.Registration(
                "Ana",
                "Prueba",
                "30111222",
                "Calle de prueba 123",
                "1000",
                "Buenos Aires",
                "Buenos Aires",
                "Argentina",
                "+5491100000000",
                password);
    }
}
