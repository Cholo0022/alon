package ar.com.alon.users;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import ar.com.alon.security.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class ManagerPasswordTest {
    /** Comprueba el mínimo de cuatro caracteres en alta y edición, incluidos empleados. */
    @Test
    void managerAcceptsFourButRejectsThreeOnCreateAndUpdate() {
        var jdbc = mock(JdbcTemplate.class);
        var hashes = mock(PasswordHasher.class);
        var identity = mock(RequestIdentity.class);
        when(identity.user()).thenReturn(Map.of("rol", "SUPER"));
        when(identity.storeId()).thenReturn(1L);
        when(hashes.hash(anyString())).thenReturn("hash-de-prueba");
        when(jdbc.queryForObject(
                        "SELECT id FROM usuarios WHERE comercio_id=? AND usuario=?",
                        Long.class,
                        1L,
                        "gerente"))
                .thenReturn(2L);
        when(jdbc.queryForMap(
                        "SELECT id,usuario,nombre,rol,permisos,activo FROM usuarios WHERE id=? AND comercio_id=?",
                        2L,
                        1L))
                .thenReturn(
                        Map.of(
                                "id",
                                2L,
                                "usuario",
                                "gerente",
                                "nombre",
                                "Gerente",
                                "rol",
                                "DUENO",
                                "permisos",
                                "ventas",
                                "activo",
                                true));
        var users = new UserService(jdbc, hashes, identity, 1, false);
        assertThatCode(
                        () ->
                                users.createUser(
                                        new UserService.UserInput(
                                                "gerente",
                                                "Gerente",
                                                "1234",
                                                "DUENO",
                                                List.of("ventas"))))
                .doesNotThrowAnyException();
        assertThatThrownBy(
                        () ->
                                users.createUser(
                                        new UserService.UserInput(
                                                "gerente",
                                                "Gerente",
                                                "123",
                                                "DUENO",
                                                List.of("ventas"))))
                .hasMessageContaining("4 y 128");
        assertThatCode(
                        () ->
                                users.updateUser(
                                        2,
                                        new UserService.UserUpdateInput(
                                                "gerente",
                                                "Gerente",
                                                "abcd",
                                                "DUENO",
                                                List.of("ventas"))))
                .doesNotThrowAnyException();
        assertThatThrownBy(
                        () ->
                                users.updateUser(
                                        2,
                                        new UserService.UserUpdateInput(
                                                "gerente",
                                                "Gerente",
                                                "abc",
                                                "DUENO",
                                                List.of("ventas"))))
                .hasMessageContaining("4 y 128");
        assertThatThrownBy(
                        () ->
                                users.createUser(
                                        new UserService.UserInput(
                                                "empleado",
                                                "Empleado",
                                                "123",
                                                "PERSONALIZADO",
                                                List.of("ventas"))))
                .hasMessageContaining("4 y 128");
        verify(hashes).hash("1234");
        verify(hashes).hash("abcd");
        verify(hashes, never()).hash("123");
    }

    /** El formulario HTTP de alta debe aceptar cuatro caracteres y rechazar tres. */
    @Test
    void managerRequestValidationMatchesTheFourCharacterPolicy() {
        try (var factory = jakarta.validation.Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertThat(
                            validator.validate(
                                    new PlatformAdministration.ManagerInput(
                                            "gerente",
                                            "Gerente",
                                            "1234",
                                            "gerente@example.test",
                                            List.of("ventas"),
                                            1)))
                    .isEmpty();
            assertThat(
                            validator.validate(
                                    new PlatformAdministration.ManagerInput(
                                            "gerente",
                                            "Gerente",
                                            "123",
                                            "gerente@example.test",
                                            List.of("ventas"),
                                            1)))
                    .isNotEmpty();
        }
    }
}
