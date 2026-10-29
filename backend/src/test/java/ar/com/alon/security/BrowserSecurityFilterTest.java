package ar.com.alon.security;

import static org.assertj.core.api.Assertions.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class BrowserSecurityFilterTest {
    @Test
    void refusesForgeryAndAcceptsTheSessionToken() throws Exception {
        var filter = new BrowserSecurityFilter();
        var request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        var rejected = new MockHttpServletResponse();
        filter.doFilter(request, rejected, new MockFilterChain());
        assertThat(rejected.getStatus()).isEqualTo(403);
        request.addHeader("X-CSRF-Token", BrowserSecurityFilter.token(request));
        var accepted = new MockHttpServletResponse();
        filter.doFilter(request, accepted, new MockFilterChain());
        assertThat(accepted.getStatus()).isEqualTo(200);
        assertThat(accepted.getHeader("Cache-Control")).isEqualTo("no-store");
    }

    @Test
    void boundsLoginAttempts() {
        var throttle = new LoginThrottle(Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
        for (int i = 0; i < 10; i++) throttle.check("127.0.0.1");
        assertThatThrownBy(() -> throttle.check("127.0.0.1"))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThatCode(() -> throttle.check("127.0.0.2")).doesNotThrowAnyException();
    }
}
