package ar.com.alon.security;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import ar.com.alon.storefront.ShopController;
import ar.com.alon.storefront.ShopService;
import ar.com.alon.users.AuthController;
import ar.com.alon.users.UserService;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class InstanceServiceFilterTest {
    @TempDir Path folder;

    private MockHttpServletResponse request(String state, String path) throws Exception {
        var file = folder.resolve("service-state");
        Files.writeString(file, state);
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
    void blocksRealControllerRoutesBeforeExistingSessionsOrPublicRequestsReachServices()
            throws Exception {
        var file = folder.resolve("routes-state");
        var users = mock(UserService.class);
        var shop = mock(ShopService.class);
        var mvc =
                MockMvcBuilders.standaloneSetup(
                                new AuthController(
                                        users,
                                        mock(LoginThrottle.class),
                                        mock(AccessSecurityService.class)),
                                new ShopController(shop, mock(LoginThrottle.class)))
                        .addFilters(new InstanceServiceFilter(file.toString()))
                        .build();
        var session = new MockHttpSession();
        session.setAttribute("alonUser", java.util.Map.of("id", 1L, "rol", "DUENO"));
        for (String state : new String[] {"SUSPENDIDO", "BAJA", "PREPARACION"}) {
            Files.writeString(file, state);
            mvc.perform(get("/api/v1/shop/catalog")).andExpect(status().isForbidden());
            mvc.perform(post("/api/v1/shop/orders").contentType("application/json").content("{}"))
                    .andExpect(status().isForbidden());
            mvc.perform(delete("/api/v1/auth/users/2").session(session))
                    .andExpect(status().isForbidden());
            mvc.perform(
                            patch("/api/v1/auth/users/2/active")
                                    .session(session)
                                    .contentType("application/json")
                                    .content("{\"active\":false}"))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(users, shop);
        Files.writeString(file, "ACTIVO");
        mvc.perform(get("/api/v1/shop/catalog")).andExpect(status().isOk());
        mvc.perform(delete("/api/v1/auth/users/2").session(session)).andExpect(status().isOk());
        mvc.perform(
                        patch("/api/v1/auth/users/2/active")
                                .session(session)
                                .contentType("application/json")
                                .content("{\"active\":false}"))
                .andExpect(status().isOk());
        verify(shop).catalog("", "", 0);
        verify(users).deleteUser(2L);
        verify(users).setActive(2L, false, 1L);
    }

    @Test
    void keepsMonitoringAvailableAndFailsClosedWhenTheControlFileIsMissing() throws Exception {
        assertThat(request("BAJA", "/api/v1/health").getStatus()).isEqualTo(200);
        assertThat(request("BAJA", "/api/v1/health").getHeader("X-Alon-Service-State"))
                .isEqualTo("BAJA");
        assertThat(request("BAJA", "/api/v1/health").getHeader("Cache-Control"))
                .isEqualTo("no-store");
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
