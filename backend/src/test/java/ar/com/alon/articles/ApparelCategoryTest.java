package ar.com.alon.articles;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import ar.com.alon.articles.dto.*;
import ar.com.alon.security.RequestIdentity;
import ar.com.alon.shared.audit.AuditLog;
import ar.com.alon.stock.StockRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class ApparelCategoryTest {
    private ArticleInput product(long category) {
        return new ArticleInput(
                "A1",
                "Remera",
                category,
                0,
                "UNIDAD",
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                false,
                true,
                BigDecimal.ZERO,
                true,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                null,
                false,
                new ApparelInput(
                        "INDUMENTARIA",
                        "Remera",
                        "Sin marca",
                        "M",
                        "LETRAS",
                        "Negro",
                        "Algodón",
                        "UNISEX",
                        "TODO EL AÑO",
                        "Remera"));
    }

    @Test
    void allSizesUseTheSelectedCategory() {
        var captured = new ArrayList<Long>();
        var service =
                new ArticleService(
                        mock(JdbcTemplate.class),
                        mock(RequestIdentity.class),
                        Clock.systemUTC(),
                        mock(AuditLog.class),
                        mock(StockRepository.class)) {
                    @Override
                    public Map<String, Object> createArticle(ArticleInput input) {
                        captured.add(input.categoryId());
                        return Map.of("id", (long) captured.size());
                    }
                };
        service.createCatalogApparel(
                new PurchaseApparelInput(
                        product(12),
                        List.of(
                                new PurchaseApparelInput.Variant("A1", "M", "Negro", 0),
                                new PurchaseApparelInput.Variant("A2", "L", "Negro", 0))));
        assertThat(captured).containsExactly(12L, 12L);
    }

    @Test
    void missingAndForeignCategoriesCannotCreateApparel() {
        var jdbc = mock(JdbcTemplate.class);
        var identity = mock(RequestIdentity.class);
        when(identity.storeId()).thenReturn(1L);
        when(identity.permits("compras")).thenReturn(true);
        var service =
                new ArticleService(
                        jdbc,
                        identity,
                        Clock.systemUTC(),
                        mock(AuditLog.class),
                        mock(StockRepository.class));
        assertThatThrownBy(() -> service.createArticle(product(0)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("rubro");
        assertThatThrownBy(() -> service.createArticle(product(12)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("rubro");
        assertThat(mockingDetails(jdbc).getInvocations())
                .noneMatch(call -> call.getMethod().getName().equals("update"));
    }
}
