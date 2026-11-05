package ar.com.alon.security;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class LicenseExpiryTest {
    private final Clock clock =
            Clock.fixed(
                    Instant.parse("2026-11-04T15:00:00Z"),
                    ZoneId.of("America/Argentina/Buenos_Aires"));

    private Map<String, Object> license(Object expiry, boolean managed, String role) {
        var jdbc = mock(JdbcTemplate.class);
        var row = new HashMap<String, Object>();
        row.put("licencia_controlada", managed);
        row.put("licencia_hasta", expiry);
        when(jdbc.queryForMap(anyString(), eq(1L))).thenReturn(row);
        var service = new AccessSecurityService(jdbc, null, null, null, clock, null, false, false);
        return service.license(Map.of("comercio_id", 1L, "rol", role));
    }

    @Test
    void warnsFromTenCalendarDaysUntilExpiryForLocalDatesAndJdbcTimestamps() {
        for (int days : new int[] {11, 10, 1, 0, -1}) {
            var date = LocalDateTime.of(2026, 11, 4, 23, 59).plusDays(days);
            for (Object expiry :
                    List.of(date, Timestamp.from(date.atZone(clock.getZone()).toInstant()))) {
                var result = license(expiry, true, "DUENO");
                assertThat(result.get("daysRemaining")).isEqualTo((long) days);
                assertThat(result.get("expiringSoon")).isEqualTo(days >= 0 && days <= 10);
                assertThat(result.get("expirationDate")).isEqualTo(date.toLocalDate().toString());
                assertThat(result.get("active")).isEqualTo(days >= 0);
            }
        }
    }

    @Test
    void doesNotWarnForUnmanagedMissingOrExpiredLicensesEvenForSuperuser() {
        assertThat(license(null, true, "DUENO"))
                .containsEntry("expiringSoon", false)
                .containsEntry("active", false);
        assertThat(license(LocalDateTime.of(2026, 11, 5, 12, 0), false, "DUENO"))
                .containsEntry("expiringSoon", false)
                .containsEntry("active", true);
        assertThat(license(LocalDateTime.of(2026, 11, 3, 12, 0), true, "SUPER"))
                .containsEntry("expiringSoon", false)
                .containsEntry("active", true);
    }
}
