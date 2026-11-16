package ar.com.alon.security;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import ar.com.alon.users.UserService;
import jakarta.servlet.http.Cookie;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.*;

/** Regresión del flujo denunciado: empleado, Volver, gerente y empleado en el mismo navegador. */
class BrowserSwitchFlowTest {
    private final Map<String, Object> manager = user(1L, "DUENO");
    private final Map<String, Object> employee = user(2L, "VENDEDOR");
    private String state = "NONE", browserId = "";
    private boolean deviceBlocked;
    private final String token = "a".repeat(43);
    private final AccessSecurityService service;

    BrowserSwitchFlowTest() {
        var jdbc =
                mock(
                        JdbcTemplate.class,
                        invocation -> {
                            String method = invocation.getMethod().getName();
                            if (invocation.getArguments().length == 0)
                                return Answers.RETURNS_DEFAULTS.answer(invocation);
                            String sql = invocation.getArgument(0);
                            if (method.equals("queryForMap"))
                                return ((Number) invocation.getArgument(2)).longValue() == 1
                                        ? manager
                                        : employee;
                            if (method.equals("queryForObject")) return 0;
                            if (method.equals("queryForList")) {
                                if (sql.startsWith("SELECT * FROM security_browsers")
                                        && Set.of("PENDIENTE", "APROBADO").contains(state))
                                    return List.of(
                                            Map.of(
                                                    "id",
                                                    browserId,
                                                    "estado",
                                                    state,
                                                    "ip",
                                                    "192.0.2.10",
                                                    "navegador",
                                                    "Chrome-test"));
                                if (sql.contains("b.estado='PENDIENTE'")
                                        && state.equals("PENDIENTE"))
                                    return List.of(
                                            Map.of(
                                                    "id",
                                                    browserId,
                                                    "usuario",
                                                    "vendedor",
                                                    "referencia",
                                                    "TEST1234"));
                                return List.of();
                            }
                            if (method.equals("update")) {
                                if (sql.startsWith("INSERT IGNORE INTO security_browser_blocks"))
                                    deviceBlocked = true;
                                if (sql.startsWith("DELETE d FROM security_browser_blocks"))
                                    deviceBlocked = false;
                                if (sql.startsWith("INSERT INTO security_browsers")) {
                                    browserId = invocation.getArgument(1);
                                    state = "PENDIENTE";
                                }
                                if (sql.contains("SET estado='CANCELADO'")) state = "CANCELADO";
                                if (sql.startsWith("UPDATE security_browsers b JOIN")
                                        && sql.contains("b.token_hash=?")) {
                                    boolean sameContext =
                                            "hash-token".equals(invocation.getArgument(5))
                                                    && "192.0.2.10"
                                                            .equals(invocation.getArgument(6))
                                                    && "Chrome-test"
                                                            .equals(invocation.getArgument(7));
                                    if (sameContext
                                            && (!deviceBlocked
                                                    || !sql.contains("security_browser_blocks"))
                                            && Set.of("PENDIENTE", "CANCELADO").contains(state)) {
                                        state = "APROBADO";
                                        return 1;
                                    }
                                    return 0;
                                }
                                if (sql.startsWith("UPDATE security_browsers b JOIN")
                                        && sql.contains("SET b.estado=?")
                                        && Set.of("PENDIENTE", "APROBADO").contains(state)) {
                                    state = invocation.getArgument(1);
                                    return 1;
                                }
                                return 1;
                            }
                            return Answers.RETURNS_DEFAULTS.answer(invocation);
                        });
        var crypto = mock(TotpSecurity.class);
        when(crypto.token()).thenReturn(token);
        when(crypto.hash(token)).thenReturn("hash-token");
        var identity = mock(RequestIdentity.class);
        when(identity.user()).thenReturn(manager);
        service =
                new AccessSecurityService(
                        jdbc,
                        mock(UserService.class),
                        identity,
                        crypto,
                        Clock.fixed(Instant.parse("2026-11-14T15:00:00Z"), ZoneOffset.UTC),
                        mock(SecurityNotifications.class),
                        false,
                        true);
    }

    private static Map<String, Object> user(long id, String role) {
        return Map.of(
                "id",
                id,
                "comercio_id",
                1L,
                "rol",
                role,
                "activo",
                true,
                "auth_version",
                1L,
                "usuario",
                "user" + id,
                "security_email",
                "manager@example.test");
    }

    private MockHttpServletRequest request(Cookie... cookies) {
        var request = new MockHttpServletRequest();
        request.setRemoteAddr("192.0.2.10");
        request.addHeader("User-Agent", "Chrome-test");
        request.setCookies(cookies);
        return request;
    }

    private Cookie startEmployeeRequest(MockHttpServletRequest request) {
        var response = new MockHttpServletResponse();
        service.begin(employee, request, response);
        String[] cookie = response.getHeader("Set-Cookie").split(";", 2)[0].split("=", 2);
        return new Cookie(cookie[0], cookie[1]);
    }

    @Test
    void returningToLoginThenEnteringAsManagerAutomaticallyAllowsOnlyThisBrowser() {
        var employeeRequest = request();
        Cookie proof = startEmployeeRequest(employeeRequest);
        assertThat(state).isEqualTo("PENDIENTE");
        service.cancel(employeeRequest);
        assertThat(state).isEqualTo("PENDIENTE");
        assertThat(employeeRequest.getSession().getAttribute("alonPending")).isNull();
        var managerRequest = request(proof);
        managerRequest.setSession((MockHttpSession) employeeRequest.getSession());
        service.begin(manager, managerRequest, new MockHttpServletResponse());
        assertThat(state).isEqualTo("APROBADO");
        var nextEmployeeRequest = request(proof);
        nextEmployeeRequest.setSession((MockHttpSession) managerRequest.getSession());
        service.begin(employee, nextEmployeeRequest, new MockHttpServletResponse());
        assertThat(
                        ((Map<?, ?>) nextEmployeeRequest.getSession().getAttribute("alonUser"))
                                .get("id"))
                .isEqualTo(2L);
        assertThat(nextEmployeeRequest.getSession().getAttribute("alonPending")).isNull();
    }

    @Test
    void requestsFromAnotherBrowserRemainVisibleForManualPermission() {
        var remoteEmployee = request();
        Cookie proof = startEmployeeRequest(remoteEmployee);
        service.cancel(remoteEmployee);
        var managerRequest = request();
        service.begin(manager, managerRequest, new MockHttpServletResponse());
        assertThat(state).isEqualTo("PENDIENTE");
        assertThat((List<?>) service.dashboard().get("requests")).hasSize(1);
        service.decide(browserId, "approve", managerRequest);
        assertThat(state).isEqualTo("APROBADO");
        var nextEmployee = request(proof);
        service.begin(employee, nextEmployee, new MockHttpServletResponse());
        assertThat(nextEmployee.getSession().getAttribute("alonUser")).isNotNull();
    }

    @Test
    void previousBackButtonCancellationsCanBeConfirmedButRevocationsCannot() {
        Cookie proof = startEmployeeRequest(request());
        state = "CANCELADO";
        service.begin(manager, request(proof), new MockHttpServletResponse());
        assertThat(state).isEqualTo("APROBADO");
        state = "REVOCADO";
        service.begin(manager, request(proof), new MockHttpServletResponse());
        assertThat(state).isEqualTo("REVOCADO");
    }

    @Test
    void aDifferentIpCannotAutomaticallyApproveEvenWithTheCookie() {
        Cookie proof = startEmployeeRequest(request());
        var managerRequest = request(proof);
        managerRequest.setRemoteAddr("192.0.2.11");
        service.begin(manager, managerRequest, new MockHttpServletResponse());
        assertThat(state).isEqualTo("PENDIENTE");
    }

    @Test
    void revocationSurvivesANewPendingRequestAndNeedsThePermitButton() {
        Cookie first = startEmployeeRequest(request());
        service.begin(manager, request(first), new MockHttpServletResponse());
        service.decide(browserId, "revoke", request(first));
        assertThat(deviceBlocked).isTrue();
        assertThat(state).isEqualTo("REVOCADO");
        Cookie retried = startEmployeeRequest(request(first));
        assertThat(state).isEqualTo("PENDIENTE");
        service.begin(manager, request(retried), new MockHttpServletResponse());
        assertThat(state).isEqualTo("PENDIENTE");
        assertThat((List<?>) service.dashboard().get("requests")).hasSize(1);
        service.decide(browserId, "approve", request(retried));
        assertThat(state).isEqualTo("APROBADO");
        assertThat(deviceBlocked).isFalse();
    }
}
