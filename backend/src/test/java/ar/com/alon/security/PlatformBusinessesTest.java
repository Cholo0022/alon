package ar.com.alon.security;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

class PlatformBusinessesTest {
    @Test
    void registersAndReturnsLinksFromTheCommercialName() {
        var jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class)).thenReturn(7L);
        var identity =
                new RequestIdentity() {
                    @Override
                    public Map<String, Object> user() {
                        return Map.of("rol", "SUPER", "id", 1L);
                    }
                };
        var input =
                new PlatformBusinesses.Business(
                        "Las Trés L",
                        "",
                        "",
                        "",
                        "",
                        "",
                        "",
                        "",
                        "Inicial",
                        java.math.BigDecimal.ZERO,
                        "PRUEBA",
                        java.time.LocalDate.of(2026, 11, 14),
                        null,
                        "",
                        "",
                        "",
                        null,
                        "");
        var result = new PlatformBusinesses(jdbc, identity).create(input);
        assertThat(result)
                .containsEntry("managementUrl", "http://127.0.0.1:20007/las-tres-l")
                .containsEntry("shopUrl", "http://127.0.0.1:20007/tienda/las-tres-l");
        verify(jdbc)
                .update(
                        "UPDATE plataforma_negocios SET url_slug=?,gestion_url=?,tienda_url=? WHERE id=?",
                        "las-tres-l",
                        "http://127.0.0.1:20007/las-tres-l",
                        "http://127.0.0.1:20007/tienda/las-tres-l",
                        7L);
    }

    @Test
    void rejectsBusinessManagersBeforeReadingOrWritingTheRegistry() {
        var jdbc = new JdbcTemplate();
        var identity =
                new RequestIdentity() {
                    @Override
                    public Map<String, Object> user() {
                        return Map.of("rol", "DUENO");
                    }
                };
        var controller = new PlatformBusinesses(jdbc, identity);
        assertThatThrownBy(controller::report).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> controller.create(null))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> controller.update(1, null))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> controller.movement(1, null))
                .isInstanceOf(ResponseStatusException.class);
    }
}
