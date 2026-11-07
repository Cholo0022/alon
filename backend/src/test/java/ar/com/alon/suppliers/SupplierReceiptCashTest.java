package ar.com.alon.suppliers;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import ar.com.alon.cash.CashLedger;
import ar.com.alon.security.RequestIdentity;
import ar.com.alon.shared.audit.AuditLog;
import ar.com.alon.suppliers.dto.SupplierReceiptInput;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.support.KeyHolder;

class SupplierReceiptCashTest {
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void appliesDebtPaymentAndOnlyUsesCashWhenChosen(boolean useCash) {
        var jdbc = mock(JdbcTemplate.class);
        var identity = mock(RequestIdentity.class);
        var cash = mock(CashLedger.class);
        when(identity.storeId()).thenReturn(1L);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(), any())).thenReturn(1);
        when(jdbc.queryForMap(anyString(), anyLong(), anyLong()))
                .thenReturn(
                        Map.of(
                                "id",
                                2L,
                                "nombre",
                                "Proveedor",
                                "saldo_actual",
                                new BigDecimal("100")));
        when(jdbc.queryForList(anyString(), anyLong(), anyLong()))
                .thenReturn(
                        List.of(
                                Map.of(
                                        "id",
                                        3L,
                                        "saldo",
                                        new BigDecimal("100"),
                                        "pagado",
                                        BigDecimal.ZERO)));
        when(jdbc.update(any(PreparedStatementCreator.class), any(KeyHolder.class)))
                .thenAnswer(
                        call -> {
                            call.<KeyHolder>getArgument(1).getKeyList().add(Map.of("id", 4L));
                            return 1;
                        });
        var date = LocalDate.of(2026, 11, 6);
        when(cash.requireOpenCash(date)).thenReturn(7L);
        var service =
                new SupplierService(jdbc, identity, Clock.systemUTC(), mock(AuditLog.class), cash);
        var receipt =
                service.createSupplierReceipt(
                        new SupplierReceiptInput(
                                2L, date, new BigDecimal("40"), "EFECTIVO", useCash));
        assertThat((BigDecimal) receipt.get("saldo_restante")).isEqualByComparingTo("60");
        verify(jdbc)
                .update(
                        contains("UPDATE proveedores"),
                        eq(new BigDecimal("40.00")),
                        eq(2L),
                        eq(1L));
        if (useCash) {
            verify(cash).requireOpenCash(date);
            verify(cash)
                    .addCashMovement(
                            eq(7L),
                            eq("EGRESO"),
                            eq("PAGO_PROVEEDOR"),
                            eq("EFECTIVO"),
                            eq(date),
                            eq(new BigDecimal("40.00")),
                            anyString(),
                            anyString());
        } else {
            verifyNoInteractions(cash);
        }
    }
}
