package ar.com.alon.purchases;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import ar.com.alon.cash.CashLedger;
import ar.com.alon.purchases.dto.*;
import ar.com.alon.security.RequestIdentity;
import ar.com.alon.shared.audit.AuditLog;
import ar.com.alon.stock.StockRepository;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class PurchaseStockTest {
    @Test
    void stockAndCostDecisionsAreIndependentAndLegacyRequestsStillAddStock() {
        for (Boolean updateStock : Arrays.asList(false, true, null)) {
            for (boolean updateCost : new boolean[] {false, true}) {
                var jdbc = mock(JdbcTemplate.class);
                var identity = mock(RequestIdentity.class);
                var stock = mock(StockRepository.class);
                when(identity.storeId()).thenReturn(1L);
                when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class)))
                        .thenReturn(1);
                when(stock.lockArticle(1L))
                        .thenReturn(
                                Map.of(
                                        "stock",
                                        new BigDecimal("10"),
                                        "costo",
                                        new BigDecimal("50"),
                                        "flete_porcentaje",
                                        BigDecimal.ZERO,
                                        "margen_porcentaje",
                                        BigDecimal.ZERO,
                                        "redondeo_precio",
                                        false));
                var headers = new ArrayList<Object[]>();
                var service =
                        new PurchaseService(
                                jdbc,
                                identity,
                                Clock.systemUTC(),
                                mock(AuditLog.class),
                                stock,
                                mock(CashLedger.class)) {
                            @Override
                            protected long insert(String sql, Object... args) {
                                if (sql.startsWith("INSERT INTO compras ")) headers.add(args);
                                return 1L;
                            }
                        };
                service.createPurchase(
                        new PurchaseInput(
                                1,
                                "FACTURA",
                                "F1",
                                LocalDate.now(),
                                List.of(
                                        new PurchaseLineInput(
                                                1, new BigDecimal("2"), new BigDecimal("75"))),
                                BigDecimal.ZERO,
                                BigDecimal.ZERO,
                                "CUENTA_CORRIENTE",
                                null,
                                updateCost,
                                updateStock));
                boolean expectedStock = !Boolean.FALSE.equals(updateStock);
                assertThat(headers.getFirst()[13]).isEqualTo(expectedStock);
                var update =
                        mockingDetails(jdbc).getInvocations().stream()
                                .filter(
                                        call ->
                                                call.getMethod().getName().equals("update")
                                                        && call.<String>getArgument(0)
                                                                .startsWith("UPDATE articulos"))
                                .findFirst()
                                .orElseThrow();
                assertThat((BigDecimal) update.getArgument(1))
                        .isEqualByComparingTo(expectedStock ? "2" : "0");
                assertThat(update.<String>getArgument(0).contains("costo=?")).isEqualTo(updateCost);
                verify(stock, times(expectedStock ? 1 : 0))
                        .addStockMovement(eq(1L), eq("COMPRA"), any(), eq("F1"), anyString());
            }
        }
    }
}
