package ar.com.alon.security;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import ar.com.alon.users.UserService;
import jakarta.servlet.http.Cookie;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.*;
import org.springframework.web.server.ResponseStatusException;

class AutomaticUserBrowserTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final RequestIdentity identity = mock(RequestIdentity.class);
    private final TotpSecurity crypto = mock(TotpSecurity.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-14T15:00:00Z"), ZoneOffset.UTC);
    private final Map<String, Object> manager = user(1, "DUENO");
    private final Map<String, Object> employee = user(2, "VENDEDOR");
    private final String token = "a".repeat(43);
    private AccessSecurityService service;

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
                "user" + id);
    }

    @BeforeEach
    void setup() {
        when(identity.user()).thenReturn(manager);
        when(jdbc.queryForMap(anyString(), eq(1L), eq(1L))).thenReturn(manager);
        when(jdbc.queryForMap(anyString(), eq(1L), eq(2L))).thenReturn(employee);
        when(crypto.token()).thenReturn(token);
        when(crypto.hash(anyString())).thenAnswer(call -> "hash-" + call.<String>getArgument(0));
        when(jdbc.queryForObject(
                        startsWith("SELECT COUNT(*) FROM security_browsers WHERE comercio_id="),
                        eq(Integer.class),
                        eq(1L),
                        eq(2L),
                        any(java.sql.Timestamp.class)))
                .thenReturn(0);
        service =
                new AccessSecurityService(
                        jdbc,
                        mock(UserService.class),
                        identity,
                        crypto,
                        clock,
                        mock(SecurityNotifications.class),
                        false,
                        true);
    }

    private MockHttpServletRequest request() {
        var request = new MockHttpServletRequest();
        request.setRemoteAddr("192.0.2.10");
        request.addHeader("User-Agent", "Chrome-test");
        return request;
    }

    private void approved() {
        when(jdbc.queryForList(
                        startsWith("SELECT * FROM security_browsers"),
                        eq("hash-" + token),
                        eq(1L),
                        eq(2L),
                        eq(1L),
                        any(java.sql.Timestamp.class)))
                .thenReturn(
                        List.of(
                                Map.of(
                                        "id",
                                        "browser-one",
                                        "estado",
                                        "APROBADO",
                                        "ip",
                                        "192.0.2.10",
                                        "navegador",
                                        "Chrome-test")));
    }

    @Test
    void creationApprovesOnlyTheManagersCurrentContextAndIssuesAPrivateUserCookie() {
        var response = new MockHttpServletResponse();
        assertThat(service.allowCreatedUser(2L, request(), response)).isTrue();
        assertThat(response.getHeader("Set-Cookie"))
                .contains("ALON_BROWSER_2=" + token, "HttpOnly", "SameSite=Strict", "Path=/api/v1");
        var insert =
                mockingDetails(jdbc).getInvocations().stream()
                        .filter(
                                call ->
                                        call.getMethod().getName().equals("update")
                                                && call.<String>getArgument(0)
                                                        .startsWith(
                                                                "INSERT INTO security_browsers"))
                        .findFirst()
                        .orElseThrow();
        assertThat(insert.<String>getArgument(0)).contains("'APROBADO'");
        assertThat((Long) insert.getArgument(2)).isEqualTo(1L);
        assertThat((Long) insert.getArgument(3)).isEqualTo(2L);
        assertThat(insert.<String>getArgument(7)).isEqualTo("192.0.2.10");
        assertThat(insert.<String>getArgument(8)).isEqualTo("Chrome-test");
        assertThat((Long) insert.getArgument(11)).isEqualTo(1L);
    }

    @Test
    void anApprovedContextEntersWithoutWaitingEvenWhenAnotherUserHasACookie() {
        approved();
        var request = request();
        request.setCookies(
                new Cookie("ALON_BROWSER_3", "b".repeat(43)), new Cookie("ALON_BROWSER_2", token));
        service.begin(employee, request, new MockHttpServletResponse());
        assertThat(request.getSession().getAttribute("alonUser")).isNotNull();
        assertThat(request.getSession().getAttribute("alonPending")).isNull();
        verify(jdbc)
                .queryForList(
                        startsWith("SELECT * FROM security_browsers"),
                        eq("hash-" + token),
                        eq(1L),
                        eq(2L),
                        eq(1L),
                        any());
    }

    @Test
    void anotherBrowserRequiresPermissionEvenWithTheSameIpAndUserAgent() {
        var request = request();
        request.setCookies(new Cookie("ALON_BROWSER_3", token));
        service.begin(employee, request, new MockHttpServletResponse());
        assertThat(request.getSession().getAttribute("alonUser")).isNull();
        assertThat(request.getSession().getAttribute("alonPending")).isNotNull();
    }

    @Test
    void changedIpOrUserAgentCannotReuseAnApproval() {
        approved();
        for (boolean changeIp : new boolean[] {true, false}) {
            var request = request();
            request.setCookies(new Cookie("ALON_BROWSER_2", token));
            if (changeIp) request.setRemoteAddr("192.0.2.11");
            else {
                request.removeHeader("User-Agent");
                request.addHeader("User-Agent", "Other-browser");
            }
            service.begin(employee, request, new MockHttpServletResponse());
            assertThat(request.getSession().getAttribute("alonUser")).isNull();
            assertThat(request.getSession().getAttribute("alonPending")).isNotNull();
        }
    }

    @Test
    void activeSessionsStillCheckRevocationIpAndBrowserOnEveryRequest() {
        approved();
        var request = request();
        request.setCookies(new Cookie("ALON_BROWSER_2", token));
        service.begin(employee, request, new MockHttpServletResponse());
        when(jdbc.queryForObject(
                        startsWith("SELECT COUNT(*) FROM security_browsers WHERE id="),
                        eq(Integer.class),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any()))
                .thenAnswer(
                        call ->
                                "192.0.2.10".equals(call.getArgument(9))
                                                && "Chrome-test".equals(call.getArgument(10))
                                        ? 1
                                        : 0);
        assertThat(service.validSession(employee, request)).isTrue();
        request.setRemoteAddr("192.0.2.11");
        request.addHeader("X-Forwarded-For", "192.0.2.10");
        assertThat(service.validSession(employee, request)).isFalse();
        request.setRemoteAddr("192.0.2.10");
        when(jdbc.queryForObject(
                        startsWith("SELECT COUNT(*) FROM security_browsers WHERE id="),
                        eq(Integer.class),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any()))
                .thenReturn(0);
        assertThat(service.validSession(employee, request)).isFalse();
    }

    @Test
    void employeesCannotApproveThemselvesAndUnknownContextsAreNotTrusted() {
        when(identity.user()).thenReturn(employee);
        assertThatThrownBy(
                        () ->
                                service.allowCreatedUser(
                                        2L, request(), new MockHttpServletResponse()))
                .isInstanceOf(ResponseStatusException.class);
        when(identity.user()).thenReturn(manager);
        var request = request();
        request.removeHeader("User-Agent");
        assertThat(service.allowCreatedUser(2L, request, new MockHttpServletResponse())).isFalse();
        assertThat(mockingDetails(jdbc).getInvocations())
                .noneMatch(call -> call.getMethod().getName().equals("update"));
    }

    @Test
    void legacyApprovalsRemainUsableUntilTheirOwnExpirationOrRevocation() {
        approved();
        var request = request();
        request.setCookies(new Cookie("ALON_BROWSER", token));
        service.begin(employee, request, new MockHttpServletResponse());
        assertThat(request.getSession().getAttribute("alonUser")).isNotNull();
    }
}
