package ar.com.alon.articles;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import ar.com.alon.security.RequestIdentity;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class ArticleBarcodeTest {
    @Test
    void exactBarcodeIsParameterizedAndScopedToTheStoreForBothPermissions() {
        for (boolean costs : new boolean[] {false, true}) {
            var jdbc = mock(JdbcTemplate.class);
            var identity = mock(RequestIdentity.class);
            when(identity.storeId()).thenReturn(9L);
            when(identity.permits("costos")).thenReturn(costs);
            new ArticleQueries(jdbc, identity, Clock.systemUTC())
                    .articles("", false, "", "", "00123");
            var call =
                    mockingDetails(jdbc).getInvocations().stream()
                            .filter(
                                    invocation ->
                                            invocation.getMethod().getName().equals("queryForList"))
                            .findFirst()
                            .orElseThrow();
            String sql = call.getArgument(0);
            assertThat(sql).contains("a.comercio_id", "a.codigo=?").doesNotContain("00123");
            assertThat(call.getArguments()).contains(9L, "ACTIVO", "00123");
            assertThat(sql.contains("a.costo")).isEqualTo(costs);
        }
    }
}
