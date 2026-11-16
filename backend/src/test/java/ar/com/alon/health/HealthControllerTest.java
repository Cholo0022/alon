package ar.com.alon.health;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HealthControllerTest {
    private final HealthController controller = new HealthController();

    @Test
    void returnsServiceStatusAndTimestamp() {
        var http = new org.springframework.mock.web.MockHttpServletResponse();
        Map<String, String> response = controller.health(http);
        assertThat(http.getHeader("X-Alon-Instance")).isEqualTo("central");

        assertThat(response).containsEntry("status", "ok").containsEntry("service", "alon-api");
        assertThat(Instant.parse(response.get("time"))).isNotNull();
    }
}
