package ar.com.alon.users;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import ar.com.alon.security.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

class UserDeletionTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final RequestIdentity identity = mock(RequestIdentity.class);
    private final UserService service =
            new UserService(jdbc, mock(PasswordHasher.class), identity, 1, false);

    UserDeletionTest() {
        when(identity.storeId()).thenReturn(1L);
        when(identity.userId()).thenReturn(1L);
        when(identity.user()).thenReturn(Map.of("rol", "DUENO"));
        when(jdbc.queryForMap(anyString(), eq(2L), eq(1L)))
                .thenReturn(Map.of("id", 2L, "rol", "VENDEDOR", "activo", true));
        when(jdbc.queryForList(contains("information_schema")))
                .thenReturn(
                        List.of(
                                Map.of(
                                        "table_name",
                                        "security_events",
                                        "column_name",
                                        "usuario_id"),
                                Map.of(
                                        "table_name",
                                        "movimientos_stock",
                                        "column_name",
                                        "usuario_id")));
    }

    @Test
    void removesAnUnusedAccountEvenIfItHasLoginEvents() {
        when(jdbc.queryForObject(startsWith("SELECT EXISTS"), eq(Boolean.class), eq(2L)))
                .thenReturn(false);
        service.deleteUser(2L);
        verify(jdbc).update("DELETE FROM usuarios WHERE id=? AND comercio_id=?", 2L, 1L);
        verify(jdbc)
                .update(
                        "DELETE FROM security_browser_blocks WHERE comercio_id=? AND usuario_id=?",
                        1L,
                        2L);
        verify(jdbc, never())
                .queryForObject(contains("FROM `security_events`"), eq(Boolean.class), anyLong());
    }

    @Test
    void refusesDeletionWhenThereAreMovementsAndDoesNotDeleteTheirHistory() {
        when(jdbc.queryForObject(startsWith("SELECT EXISTS"), eq(Boolean.class), eq(2L)))
                .thenReturn(true);
        assertThatThrownBy(() -> service.deleteUser(2L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Desactivá");
        verify(jdbc, never()).update(startsWith("DELETE"), any(Object[].class));
    }

    @Test
    void aManagerCannotRemoveAnAdministrator() {
        when(jdbc.queryForMap(anyString(), eq(2L), eq(1L)))
                .thenReturn(Map.of("id", 2L, "rol", "SUPER", "activo", true));
        assertThatThrownBy(() -> service.deleteUser(2L))
                .isInstanceOf(ResponseStatusException.class);
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }
}
