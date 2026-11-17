package ar.com.alon.security;

import static org.assertj.core.api.Assertions.*;
import java.nio.file.*;
import java.time.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InstanceTermsTest {
    @TempDir Path folder;
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-16T15:00:00Z"), ZoneId.of("America/Argentina/Buenos_Aires"));

    @Test
    void trialExpiresAtTheBoundaryAndActiveHasNoDeadline() throws Exception {
        Path file = folder.resolve("service-state");
        Files.writeString(file, "PRUEBA");
        Files.writeString(folder.resolve("service-trial-until"), "2026-11-16T12:00:00");
        assertThat(InstanceTerms.state(file.toString(), clock)).isEqualTo("PRUEBA_VENCIDA");
        assertThat(InstanceTerms.status(file.toString(), clock)).containsEntry("active", false);
        Files.writeString(file, "ACTIVO");
        assertThat(InstanceTerms.state(file.toString(), clock)).isEqualTo("ACTIVO");
        assertThat(InstanceTerms.status(file.toString(), clock)).containsEntry("active", true).containsEntry("managed", false);
    }

    @Test
    void missingTrialDeadlineFailsClosedAndUpcomingTrialWarns() throws Exception {
        Path file = folder.resolve("service-state");
        Files.writeString(file, "PRUEBA");
        assertThat(InstanceTerms.status(file.toString(), clock)).containsEntry("active", false);
        Files.writeString(folder.resolve("service-trial-until"), "2026-11-20T00:00:00");
        assertThat(InstanceTerms.status(file.toString(), clock)).containsEntry("active", true).containsEntry("expiringSoon", true);
    }
}
