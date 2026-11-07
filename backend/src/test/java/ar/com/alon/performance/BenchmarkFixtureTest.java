package ar.com.alon.performance;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class BenchmarkFixtureTest {
    @Test
    void reusesTheDefaultStoreCreatedByMigrationsAndOnlyInsertsTheSecondStore() {
        var jdbc = mock(JdbcTemplate.class);
        String reuse =
                "UPDATE comercios SET codigo='PERF',nombre='Comercio de prueba' WHERE id=1 AND codigo='ALON'";
        when(jdbc.update(reuse)).thenReturn(1);
        CapacityBenchmarkTest.prepareBenchmarkStores(jdbc);
        var order = inOrder(jdbc);
        order.verify(jdbc).update(reuse);
        order.verify(jdbc)
                .update(
                        "INSERT INTO comercios(id,codigo,nombre) VALUES(2,'OTHER','Otro comercio')");
        verifyNoMoreInteractions(jdbc);
    }

    @Test
    void stopsIfTheFreshMigrationStateIsUnexpected() {
        var jdbc = mock(JdbcTemplate.class);
        assertThatIllegalStateException()
                .isThrownBy(() -> CapacityBenchmarkTest.prepareBenchmarkStores(jdbc));
        verify(jdbc, never()).update(startsWith("INSERT"));
    }
}
