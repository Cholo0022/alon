package ar.com.alon.users;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import ar.com.alon.security.PasswordHasher;
import ar.com.alon.security.RequestIdentity;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

// Comprueba que las cuentas locales de ejemplo no reemplacen usuarios existentes.
class LocalAccountsTest {
    @Test
    void normalStartupDoesNotCreateExampleAccounts() {
        var jdbc = mock(JdbcTemplate.class);
        new UserService(jdbc, mock(PasswordHasher.class), mock(RequestIdentity.class), 1, false)
                .seedLocalAccounts();
        verifyNoInteractions(jdbc);
    }

    @Test
    void existingAccountsArePreservedInTheLocalProfile() {
        var jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM usuarios WHERE comercio_id=?", Integer.class, 1L))
                .thenReturn(1);
        new UserService(jdbc, mock(PasswordHasher.class), mock(RequestIdentity.class), 1, true)
                .seedLocalAccounts();
        assertThat(
                        mockingDetails(jdbc).getInvocations().stream()
                                .noneMatch(call -> call.getMethod().getName().equals("update")))
                .isTrue();
    }

    @Test
    void anEmptyLocalInstallationUsesTheAlonAccountNames() {
        var jdbc = mock(JdbcTemplate.class);
        var passwords = mock(PasswordHasher.class);
        when(passwords.hash(anyString())).thenReturn("protected-password");
        when(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM usuarios WHERE comercio_id=?", Integer.class, 1L))
                .thenReturn(0);
        when(jdbc.queryForObject(
                        "SELECT id FROM usuarios WHERE comercio_id=? AND usuario=?",
                        Long.class,
                        1L,
                        "alon_pruebas"))
                .thenReturn(1L);
        when(jdbc.queryForObject(
                        "SELECT id FROM usuarios WHERE comercio_id=? AND usuario=?",
                        Long.class,
                        1L,
                        "vendedor1"))
                .thenReturn(2L);
        new UserService(jdbc, passwords, mock(RequestIdentity.class), 1, true).seedLocalAccounts();
        var usernames =
                mockingDetails(jdbc).getInvocations().stream()
                        .filter(call -> call.getMethod().getName().equals("update"))
                        .map(call -> call.<String>getArgument(2))
                        .toList();
        assertThat(usernames).containsExactly("alon_pruebas", "vendedor1");
        verify(passwords, times(2)).hash("CambiarAlon-2026!");
    }
}
