package ar.com.alon.dashboard;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import ar.com.alon.security.RequestIdentity;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class DashboardWeekTest {
    @Test
    void sundayBelongsToPreviousMondayAndMondayStartsNewWeek() {
        for (String instant : List.of("2026-11-02T02:59:59Z", "2026-11-02T03:00:00Z")) {
            var jdbc = mock(JdbcTemplate.class);
            var identity = mock(RequestIdentity.class);
            when(jdbc.queryForMap(anyString(), any(Object[].class))).thenReturn(new HashMap<>());
            var result =
                    new DashboardQueries(
                                    jdbc,
                                    identity,
                                    Clock.fixed(Instant.parse(instant), ZoneOffset.UTC))
                            .summary();
            boolean sunday = instant.contains("02:59");
            assertThat(result).containsEntry("semana_desde", sunday ? "2026-10-26" : "2026-11-02");
            assertThat(result).containsEntry("semana_hasta", sunday ? "2026-11-01" : "2026-11-08");
            var call = mockingDetails(jdbc).getInvocations().iterator().next();
            Object[] args = (Object[]) call.getRawArguments()[1];
            assertThat(args).hasSize(25);
            for (int i = 4; i < 24; i += 3) {
                assertThat(args[i])
                        .isEqualTo(LocalDate.parse(result.get("semana_desde").toString()));
                assertThat(args[i + 1])
                        .isEqualTo(
                                LocalDate.parse(result.get("semana_hasta").toString()).plusDays(1));
            }
        }
    }
}
