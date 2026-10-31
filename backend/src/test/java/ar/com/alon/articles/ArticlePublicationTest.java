package ar.com.alon.articles;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import ar.com.alon.articles.dto.ArticleInput;
import ar.com.alon.security.RequestIdentity;
import ar.com.alon.shared.audit.AuditLog;
import ar.com.alon.stock.StockRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class ArticlePublicationTest {
    @Test
    void publishesAnArticleWithoutReplacingItsInactiveSupplier() {
        var jdbc = mock(JdbcTemplate.class);
        var identity = mock(RequestIdentity.class);
        var audit = mock(AuditLog.class);
        when(identity.storeId()).thenReturn(1L);
        when(identity.permits("compras")).thenReturn(true);
        var current =
                Map.<String, Object>of("proveedor_principal_id", 7L, "stock", new BigDecimal("10"));
        when(jdbc.queryForMap(
                        "SELECT * FROM articulos WHERE id=? AND comercio_id=? FOR UPDATE", 1L, 1L))
                .thenReturn(current);
        when(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM rubros WHERE id=? AND comercio_id=?",
                        Integer.class,
                        1L,
                        1L))
                .thenReturn(1);
        when(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM proveedores WHERE id=? AND comercio_id=?",
                        Integer.class,
                        7L,
                        1L))
                .thenReturn(1);
        var service =
                new ArticleService(
                        jdbc, identity, Clock.systemUTC(), audit, mock(StockRepository.class));
        assertThatCode(() -> service.updateArticle(1, input(7))).doesNotThrowAnyException();
        var update =
                mockingDetails(jdbc).getInvocations().stream()
                        .filter(
                                call ->
                                        call.getMethod().getName().equals("update")
                                                && call.<String>getArgument(0)
                                                        .startsWith("UPDATE articulos SET codigo="))
                        .findFirst()
                        .orElseThrow();
        assertThat((Boolean) update.getArgument(11)).isTrue();
        verify(jdbc, never())
                .queryForObject(
                        "SELECT COUNT(*) FROM proveedores WHERE id=? AND comercio_id=? AND estado='ACTIVO'",
                        Integer.class,
                        7L,
                        1L);
    }

    @Test
    void rejectsAReplacementInactiveSupplierAndIdentifiesTheField() {
        var jdbc = mock(JdbcTemplate.class);
        var identity = mock(RequestIdentity.class);
        when(identity.storeId()).thenReturn(1L);
        when(identity.permits("compras")).thenReturn(true);
        when(jdbc.queryForMap(
                        "SELECT * FROM articulos WHERE id=? AND comercio_id=? FOR UPDATE", 1L, 1L))
                .thenReturn(Map.of("proveedor_principal_id", 7L));
        when(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM rubros WHERE id=? AND comercio_id=?",
                        Integer.class,
                        1L,
                        1L))
                .thenReturn(1);
        var service =
                new ArticleService(
                        jdbc,
                        identity,
                        Clock.systemUTC(),
                        mock(AuditLog.class),
                        mock(StockRepository.class));
        assertThatThrownBy(() -> service.updateArticle(1, input(8)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("El proveedor")
                .hasMessageContaining("inactivo");
        assertThat(
                        mockingDetails(jdbc).getInvocations().stream()
                                .noneMatch(call -> call.getMethod().getName().equals("update")))
                .isTrue();
    }

    private ArticleInput input(long supplier) {
        return new ArticleInput(
                "A1",
                "Artículo",
                1,
                supplier,
                "UNIDAD",
                new BigDecimal("100"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                false,
                false,
                BigDecimal.ZERO,
                true,
                new BigDecimal("10"),
                BigDecimal.ZERO,
                new BigDecimal("120"),
                null,
                true);
    }
}
