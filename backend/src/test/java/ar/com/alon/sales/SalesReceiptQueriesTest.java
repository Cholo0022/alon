package ar.com.alon.sales;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import ar.com.alon.security.RequestIdentity;
import java.time.Clock;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

class SalesReceiptQueriesTest {
    @Test
    void headerAndLinesAreScopedToTheCurrentBusinessWithoutExposingCosts() {
        var jdbc = mock(JdbcTemplate.class);
        var identity = mock(RequestIdentity.class);
        when(identity.storeId()).thenReturn(2L);
        when(jdbc.queryForMap(anyString(), eq(7L), eq(2L)))
                .thenReturn(new LinkedHashMap<>(Map.of("id", 7L)));
        assertThat(new SalesQueries(jdbc, identity, Clock.systemUTC()).receipt(7))
                .containsEntry("lineas", List.of());
        var calls =
                mockingDetails(jdbc).getInvocations().stream()
                        .filter(call -> call.getMethod().getName().startsWith("queryFor"))
                        .toList();
        assertThat(calls).hasSize(2);
        for (var call : calls) {
            assertThat(call.<String>getArgument(0))
                    .contains("v.comercio_id=?")
                    .doesNotContain("costo");
            assertThat((Long) call.getArgument(1)).isEqualTo(7L);
            assertThat((Long) call.getArgument(2)).isEqualTo(2L);
        }
    }

    @Test
    void missingOrForeignSaleNeverLoadsItsLines() {
        var jdbc = mock(JdbcTemplate.class);
        var identity = mock(RequestIdentity.class);
        when(identity.storeId()).thenReturn(2L);
        when(jdbc.queryForMap(anyString(), eq(7L), eq(2L)))
                .thenThrow(new EmptyResultDataAccessException(1));
        assertThatThrownBy(() -> new SalesQueries(jdbc, identity, Clock.systemUTC()).receipt(7))
                .isInstanceOf(EmptyResultDataAccessException.class);
        verify(jdbc, never()).queryForList(anyString(), any(Object[].class));
    }
}
