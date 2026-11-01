package ar.com.alon.users;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import ar.com.alon.security.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class BackupDelegationTest {
    @Test
    void managerCannotGrantBackupWithoutHoldingIt() {
        var identity = mock(RequestIdentity.class);
        when(identity.user()).thenReturn(Map.of("rol", "DUENO"));
        var hashes = mock(PasswordHasher.class);
        when(hashes.hash(anyString())).thenReturn("hash");
        var jdbc = mock(JdbcTemplate.class);
        when(identity.storeId()).thenReturn(1L);
        when(jdbc.queryForObject(
                        "SELECT id FROM usuarios WHERE comercio_id=? AND usuario=?",
                        Long.class,
                        1L,
                        "empleado"))
                .thenReturn(3L);
        var service = new UserService(jdbc, hashes, identity, 1, false);
        assertThat(UserService.ALL_PERMISSIONS).contains("backup");
        assertThatThrownBy(
                        () ->
                                service.createUser(
                                        new UserService.UserInput(
                                                "empleado",
                                                "Empleado",
                                                "Password123456!",
                                                "PERSONALIZADO",
                                                List.of("backup"))))
                .hasMessageContaining("No podés otorgar");
        when(identity.permits("backup")).thenReturn(true);
        assertThatCode(
                        () ->
                                service.createUser(
                                        new UserService.UserInput(
                                                "empleado",
                                                "Empleado",
                                                "Password123456!",
                                                "PERSONALIZADO",
                                                List.of("backup"))))
                .doesNotThrowAnyException();
        verify(jdbc)
                .update(
                        "INSERT INTO usuarios(comercio_id,usuario,nombre,password_hash,rol,permisos) VALUES(?,?,?,?,?,?)",
                        1L,
                        "empleado",
                        "Empleado",
                        "hash",
                        "PERSONALIZADO",
                        "backup");
    }
}
