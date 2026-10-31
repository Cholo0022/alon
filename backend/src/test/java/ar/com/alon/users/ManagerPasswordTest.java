package ar.com.alon.users;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import ar.com.alon.security.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class ManagerPasswordTest {
    /** Comprueba el límite de ocho caracteres en alta y edición sin reducir el de los empleados. */
    @Test
    void managerAcceptsEightButRejectsSevenOnCreateAndUpdate() {
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
                                                "12345678",
                                                "DUENO",
                                                List.of("ventas"))))
                .doesNotThrowAnyException();
        assertThatThrownBy(
                        () ->
                                users.createUser(
                                        new UserService.UserInput(
                                                "gerente",
                                                "Gerente",
                                                "1234567",
                                                "DUENO",
                                                List.of("ventas"))))
                .hasMessageContaining("8 y 128");
        assertThatCode(
                        () ->
                                users.updateUser(
                                        2,
                                        new UserService.UserUpdateInput(
                                                "gerente",
                                                "Gerente",
                                                "abcdefgh",
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
                                                "abcdefg",
                                                "DUENO",
                                                List.of("ventas"))))
                .hasMessageContaining("8 y 128");
        assertThatThrownBy(
                        () ->
                                users.createUser(
                                        new UserService.UserInput(
                                                "empleado",
                                                "Empleado",
                                                "12345678",
                                                "PERSONALIZADO",
                                                List.of("ventas"))))
                .hasMessageContaining("12 y 128");
        verify(hashes).hash("12345678");
        verify(hashes).hash("abcdefgh");
        verify(hashes, never()).hash("1234567");
    }

    /** El formulario HTTP de alta debe aceptar ocho caracteres y rechazar siete. */
    @Test
    void managerRequestValidationMatchesTheEightCharacterPolicy() {
        try (var factory = jakarta.validation.Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertThat(
                            validator.validate(
                                    new PlatformAdministration.ManagerInput(
                                            "gerente",
                                            "Gerente",
                                            "12345678",
                                            "gerente@example.test",
                                            List.of("ventas"),
                                            1)))
                    .isEmpty();
            assertThat(
                            validator.validate(
                                    new PlatformAdministration.ManagerInput(
                                            "gerente",
                                            "Gerente",
                                            "1234567",
                                            "gerente@example.test",
                                            List.of("ventas"),
                                            1)))
                    .isNotEmpty();
        }
    }
}
