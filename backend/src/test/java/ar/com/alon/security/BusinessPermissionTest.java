package ar.com.alon.security;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import ar.com.alon.users.UserService;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;

class BusinessPermissionTest {
    @Test
    void staffCanReadBrandButCannotChangeTheBusiness() throws Exception {
        var user =
                Map.<String, Object>of(
                        "id",
                        1L,
                        "comercio_id",
                        1L,
                        "rol",
                        "VENDEDOR",
                        "permisos",
                        "articulos,costos,negocio");
        var auth = mock(UserService.class);
        when(auth.sessionUser(anyMap())).thenReturn(user);
        var security = mock(AccessSecurityService.class);
        when(security.validSession(anyMap(), any())).thenReturn(true);
        when(security.license(anyMap())).thenReturn(Map.of("active", true));
        var filter = new PermissionFilter(auth, security);
        for (var method : new String[] {"GET", "PUT"}) {
            var req =
                    new MockHttpServletRequest(
                            method,
                            method.equals("GET")
                                    ? "/api/v1/business"
                                    : "/api/v1/operations/business");
            req.getSession().setAttribute("alonUser", user);
            var response = new MockHttpServletResponse();
            filter.doFilter(req, response, new MockFilterChain());
            assertThat(response.getStatus()).isEqualTo(method.equals("GET") ? 200 : 403);
        }
    }

    @Test
    void shopBrandRemainsPublicWithoutStaffAccess() throws Exception {
        var filter =
                new PermissionFilter(mock(UserService.class), mock(AccessSecurityService.class));
        var response = new MockHttpServletResponse();
        filter.doFilter(
                new MockHttpServletRequest("GET", "/api/v1/shop/business"),
                response,
                new MockFilterChain());
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void managerCannotBypassSelectedModulesOrActivateLicenses() throws Exception {
        var user =
                Map.<String, Object>of(
                        "id", 2L, "comercio_id", 1L, "rol", "DUENO", "permisos", "ventas");
        var auth = mock(UserService.class);
        when(auth.sessionUser(anyMap())).thenReturn(user);
        var security = mock(AccessSecurityService.class);
        when(security.validSession(anyMap(), any())).thenReturn(true);
        when(security.license(anyMap())).thenReturn(Map.of("active", true));
        var filter = new PermissionFilter(auth, security);
        for (var path :
                new String[] {
                    "/api/v1/alon/sales",
                    "/api/v1/alon/purchases",
                    "/api/v1/super/licenses/activate",
                    "/api/v1/auth/security/dashboard"
                }) {
            var request = new MockHttpServletRequest("GET", path);
            request.getSession().setAttribute("alonUser", user);
            var response = new MockHttpServletResponse();
            filter.doFilter(request, response, new MockFilterChain());
            assertThat(response.getStatus())
                    .as(path)
                    .isEqualTo(path.endsWith("/sales") ? 200 : 403);
        }
        when(security.license(anyMap())).thenReturn(Map.of("active", false));
        var request = new MockHttpServletRequest("GET", "/api/v1/alon/sales");
        request.getSession().setAttribute("alonUser", user);
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("licencia");
    }
}
