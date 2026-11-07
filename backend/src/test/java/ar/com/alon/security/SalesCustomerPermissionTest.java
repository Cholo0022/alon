package ar.com.alon.security;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import ar.com.alon.users.UserService;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;

class SalesCustomerPermissionTest {
    @Test
    void salesCanCreateButCannotManageExistingCustomers() throws Exception {
        for (String permissions : new String[] {"ventas", "inicio", "clientes"}) {
            var user =
                    Map.<String, Object>of(
                            "id",
                            2L,
                            "comercio_id",
                            1L,
                            "rol",
                            "PERSONALIZADO",
                            "permisos",
                            permissions);
            var auth = mock(UserService.class);
            when(auth.sessionUser(anyMap())).thenReturn(user);
            var security = mock(AccessSecurityService.class);
            when(security.validSession(anyMap(), any())).thenReturn(true);
            when(security.license(anyMap())).thenReturn(Map.of("active", true));
            for (String operation :
                    new String[] {
                        "POST /api/v1/operations/customers",
                        "PUT /api/v1/operations/customers/1",
                        "POST /api/v1/operations/customers/1/deactivate",
                        "GET /api/v1/alon/customers"
                    }) {
                var parts = operation.split(" ");
                var req = new MockHttpServletRequest(parts[0], parts[1]);
                req.getSession().setAttribute("alonUser", user);
                var response = new MockHttpServletResponse();
                var chain = new MockFilterChain();
                new PermissionFilter(auth, security).doFilter(req, response, chain);
                boolean allowed =
                        permissions.equals("clientes")
                                || (permissions.equals("ventas")
                                        && operation.equals("POST /api/v1/operations/customers"));
                assertThat(response.getStatus())
                        .as(permissions + " " + operation)
                        .isEqualTo(allowed ? 200 : 403);
                assertThat(chain.getRequest() != null).isEqualTo(allowed);
            }
        }
    }
}
