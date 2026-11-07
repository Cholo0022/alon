package ar.com.alon.reporting;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import ar.com.alon.security.RequestIdentity;
import java.time.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class ReportPeriodTest {
    private final ReportQueries reports =
            new ReportQueries(
                    mock(JdbcTemplate.class),
                    mock(RequestIdentity.class),
                    Clock.fixed(Instant.parse("2026-11-01T01:00:00Z"), ZoneOffset.UTC));

    @Test
    void defaultsToCurrentMonthInArgentina() {
        var period = reports.period(null, null);
        assertThat(period.start()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(period.end()).isEqualTo(LocalDate.of(2026, 11, 1));
    }

    @Test
    void includesBothMonthsAcrossYearAndLeapDay() {
        var period = reports.period("2023-12", "2024-02");
        assertThat(period.start()).isEqualTo(LocalDate.of(2023, 12, 1));
        assertThat(period.end()).isEqualTo(LocalDate.of(2024, 3, 1));
    }

    @Test
    void rejectsReversedOrMalformedPeriods() {
        assertThatIllegalArgumentException().isThrownBy(() -> reports.period("2026-12", "2026-11"));
        assertThatIllegalArgumentException().isThrownBy(() -> reports.period("2026-13", null));
    }
}
