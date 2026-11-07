package ar.com.alon.shared.persistence;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import ar.com.alon.security.RequestIdentity;
import ar.com.alon.suppliers.SupplierQueries;
import java.time.Clock;
import java.util.*;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.*;
import org.springframework.web.context.request.*;

class ReadPageTotalTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @BeforeEach
    void bind() {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request, response));
    }

    @AfterEach
    void clear() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void countsFilteredRowsBeforeLimitWithTheSameSearchAndTenant() {
        request.setParameter("includeTotal", "true");
        request.setParameter("q", "tel_50%");
        request.setParameter("page", "1");
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class)))
                .thenReturn(205L);
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of());
        new ReadPage(jdbc)
                .query(
                        "nombre,telefono",
                        "SELECT id,nombre,telefono FROM clientes WHERE comercio_id=? ORDER BY nombre,id",
                        7L);
        var countSql = ArgumentCaptor.forClass(String.class);
        var countArgs = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).queryForObject(countSql.capture(), eq(Long.class), countArgs.capture());
        assertThat(countSql.getValue())
                .contains("SELECT COUNT(*) FROM (", "comercio_id=?", "ESCAPE '!'")
                .doesNotContain("LIMIT", "OFFSET");
        assertThat(countArgs.getValue()).containsExactly(7L, "%tel!_50!%%");
        verify(jdbc)
                .queryForList(
                        endsWith("LIMIT ? OFFSET ?"),
                        eq(new Object[] {7L, "%tel!_50!%%", 100, 100L}));
        assertThat(response.getHeader("X-Total-Count")).isEqualTo("205");
    }

    @Test
    void debtorPredicateIsAppliedInSqlBeforePaginationAndCount() {
        var identity = mock(RequestIdentity.class);
        when(identity.storeId()).thenReturn(1L);
        request.setParameter("includeTotal", "true");
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class)))
                .thenReturn(125L);
        new SupplierQueries(jdbc, identity, Clock.systemUTC()).suppliers(false, true);
        verify(jdbc)
                .queryForList(
                        contains("AND saldo_actual>0"), eq(new Object[] {1L, "ACTIVO", 100, 0L}));
        assertThat(response.getHeader("X-Total-Count")).isEqualTo("125");
    }

    @Test
    void doesNotAddACountQueryForCallersThatDidNotRequestIt() {
        new ReadPage(jdbc)
                .query("id", "SELECT id FROM clientes WHERE comercio_id=? ORDER BY id", 1L);
        verify(jdbc, never()).queryForObject(anyString(), eq(Long.class), any(Object[].class));
        assertThat(response.getHeader("X-Total-Count")).isNull();
    }
}
