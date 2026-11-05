package ar.com.alon.security;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import ar.com.alon.shared.persistence.StoreJdbcSupport;
import ar.com.alon.users.UserService;
import java.time.*;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.*;

class AccountingAvailabilityTest {
    @Test
    void accountingEndpointsAreUnavailableEvenForSuperusers() throws Exception {
        for (String role : new String[] {"SUPER", "DUENO"}) {
            var user =
                    Map.<String, Object>of(
                            "id", 1L, "comercio_id", 1L, "rol", role, "permisos", "contabilidad");
            var auth = mock(UserService.class);
            var security = mock(AccessSecurityService.class);
            when(auth.sessionUser(anyMap())).thenReturn(user);
            when(security.validSession(anyMap(), any())).thenReturn(true);
            when(security.license(anyMap())).thenReturn(Map.of("active", true));
            for (String path :
                    new String[] {
                        "/api/v1/accounting/options",
                        "/api/v1/accounting/report",
                        "/api/v1/operations/accounting/settings"
                    }) {
                var request = new MockHttpServletRequest("GET", path);
                request.getSession().setAttribute("alonUser", user);
                var response = new MockHttpServletResponse();
                var chain = new MockFilterChain();
                new PermissionFilter(auth, security).doFilter(request, response, chain);
                assertThat(response.getStatus()).isEqualTo(404);
                assertThat(chain.getRequest()).isNull();
            }
        }
    }

    @Test
    void operationsDoNotConsultAccountingPeriodsInThisVersion() {
        var jdbc = mock(JdbcTemplate.class);
        var identity = new RequestIdentity();
        assertThat(identity.accountingEnabled()).isFalse();
        var support =
                new StoreJdbcSupport(jdbc, identity, Clock.systemUTC(), null) {
                    void validateDate() {
                        assertAccountingPeriod(LocalDate.of(2026, 11, 4));
                    }
                };
        support.validateDate();
        verifyNoInteractions(jdbc);
    }
}
