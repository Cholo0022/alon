package ar.com.alon.security;

import static org.assertj.core.api.Assertions.*;

import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.*;

class InstanceServiceFilterTest {
    @TempDir Path folder;

    private MockHttpServletResponse request(String state, String path) throws Exception {
        var file = folder.resolve("service-state");
        Files.writeString(file, state);
        Files.writeString(folder.resolve("service-trial-until"), "2099-12-31T00:00:00");
        var response = new MockHttpServletResponse();
        new InstanceServiceFilter(file.toString())
                .doFilter(new MockHttpServletRequest("GET", path), response, new MockFilterChain());
        return response;
    }

    @Test
    void blocksExistingManagementSessionsAndPublicShopForAllNonOperatingStates() throws Exception {
        for (String state : new String[] {"SUSPENDIDO", "BAJA", "PREPARACION"}) {
            assertThat(request(state, "/api/v1/operations/sales").getStatus()).isEqualTo(403);
            assertThat(request(state, "/api/v1/shop/products").getStatus()).isEqualTo(403);
        }
    }

    @Test
    void permitsTrialAndActiveAndRestoresAccessWithoutRecreatingData() throws Exception {
        assertThat(request("SUSPENDIDO", "/api/v1/shop/orders").getStatus()).isEqualTo(403);
        var blocked = request("BAJA", "/api/v1/auth/status");
        assertThat(blocked.getHeader("X-Alon-Service-Control")).isEqualTo("enabled");
        assertThat(blocked.getHeader("X-Alon-Service-State")).isEqualTo("BAJA");
        assertThat(request("ACTIVO", "/api/v1/shop/orders").getStatus()).isEqualTo(200);
        assertThat(request("PRUEBA", "/api/v1/operations/sales").getStatus()).isEqualTo(200);
    }

    @Test
    void keepsMonitoringAvailableAndFailsClosedWhenTheControlFileIsMissing() throws Exception {
        assertThat(request("BAJA", "/api/v1/health").getStatus()).isEqualTo(200);
        assertThat(request("BAJA", "/api/v1/health").getHeader("X-Alon-Service-State"))
                .isEqualTo("BAJA");
        var response = new MockHttpServletResponse();
        new InstanceServiceFilter(folder.resolve("missing").toString())
                .doFilter(
                        new MockHttpServletRequest("GET", "/api/v1/shop/orders"),
                        response,
                        new MockFilterChain());
        assertThat(response.getStatus()).isEqualTo(503);
        var central = new MockHttpServletResponse();
        new InstanceServiceFilter("")
                .doFilter(
                        new MockHttpServletRequest("GET", "/api/v1/super/businesses"),
                        central,
                        new MockFilterChain());
        assertThat(central.getStatus()).isEqualTo(200);
    }
}
