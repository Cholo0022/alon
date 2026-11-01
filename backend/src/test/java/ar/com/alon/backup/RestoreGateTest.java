package ar.com.alon.backup;

import static org.assertj.core.api.Assertions.*;

import java.nio.file.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.*;

class RestoreGateTest {
    @TempDir Path temp;

    @Test
    void blocksAllApiOperationsWhileRestoringAndReopensAfterwards() throws Exception {
        var gate = new RestoreGate(temp.toString());
        gate.begin();
        try {
            var response = new MockHttpServletResponse();
            gate.doFilter(
                    new MockHttpServletRequest("POST", "/api/v1/operations/sales"),
                    response,
                    new MockFilterChain());
            assertThat(response.getStatus()).isEqualTo(503);
        } finally {
            gate.end();
        }
        var response = new MockHttpServletResponse();
        gate.doFilter(
                new MockHttpServletRequest("GET", "/api/v1/alon/articles"),
                response,
                new MockFilterChain());
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void expiresExistingSessionsAfterRestore() throws Exception {
        var gate = new RestoreGate(temp.toString());
        var session = new MockHttpSession();
        var first = new MockHttpServletRequest("GET", "/api/v1/auth/status");
        first.setSession(session);
        gate.doFilter(first, new MockHttpServletResponse(), new MockFilterChain());
        gate.expireSessions();
        var second = new MockHttpServletRequest("GET", "/api/v1/auth/status");
        second.setSession(session);
        gate.doFilter(second, new MockHttpServletResponse(), new MockFilterChain());
        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    void interruptedRestoreKeepsTheSystemClosedAfterRestart() throws Exception {
        Files.writeString(temp.resolve(RestoreGate.MARKER), "pending");
        var gate = new RestoreGate(temp.toString());
        var response = new MockHttpServletResponse();
        gate.doFilter(
                new MockHttpServletRequest("GET", "/api/v1/alon/articles"),
                response,
                new MockFilterChain());
        assertThat(response.getStatus()).isEqualTo(503);
        assertThatThrownBy(gate::begin).hasMessageContaining("interrumpida");
    }

    @Test
    void waitsForAnExistingRequestToFinishBeforeRestore() throws Exception {
        var gate = new RestoreGate(temp.toString());
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var request =
                    workers.submit(
                            () -> {
                                try {
                                    gate.doFilter(
                                            new MockHttpServletRequest(
                                                    "POST", "/api/v1/operations/sales"),
                                            new MockHttpServletResponse(),
                                            (req, res) -> {
                                                entered.countDown();
                                                try {
                                                    if (!release.await(5, TimeUnit.SECONDS))
                                                        throw new IllegalStateException();
                                                } catch (InterruptedException e) {
                                                    throw new IllegalStateException(e);
                                                }
                                            });
                                } catch (Exception e) {
                                    throw new RuntimeException(e);
                                }
                            });
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            var restoring =
                    workers.submit(
                            () -> {
                                gate.begin();
                                try {
                                    return true;
                                } finally {
                                    gate.end();
                                }
                            });
            assertThatThrownBy(() -> restoring.get(100, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);
            release.countDown();
            request.get(5, TimeUnit.SECONDS);
            assertThat(restoring.get(5, TimeUnit.SECONDS)).isTrue();
        } finally {
            release.countDown();
        }
    }
}
