package ar.com.alon.purchases;

import static org.assertj.core.api.Assertions.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class PurchaseValuationTest {
    @Test
    void allocatesEveryCentWithoutNegativeRemainders() {
        var lines =
                IntStream.range(0, 200)
                        .mapToObj(
                                i -> new PurchaseValuation.Line(i, BigDecimal.ONE, BigDecimal.ONE))
                        .toList();
        var allocated = PurchaseValuation.allocate(lines, new BigDecimal("1.00"));
        assertThat(allocated)
                .allSatisfy(
                        line -> assertThat(line.value()).isGreaterThanOrEqualTo(BigDecimal.ONE));
        assertThat(
                        allocated.stream()
                                .map(PurchaseValuation.ValuedLine::value)
                                .reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo("201.00");
    }

    @Test
    void handlesZeroCostPurchasesAndRevaluesUnchangedQuantities() {
        var allocated =
                PurchaseValuation.allocate(
                        List.of(
                                new PurchaseValuation.Line(1, new BigDecimal("2"), BigDecimal.ZERO),
                                new PurchaseValuation.Line(2, BigDecimal.ONE, BigDecimal.ZERO)),
                        new BigDecimal("3"));
        assertThat(allocated.getFirst().value()).isEqualByComparingTo("2");
        assertThat(
                        PurchaseValuation.revisedCost(
                                new BigDecimal("10"),
                                new BigDecimal("100"),
                                new BigDecimal("10"),
                                new BigDecimal("1000"),
                                new BigDecimal("10"),
                                new BigDecimal("2000")))
                .isEqualByComparingTo("200");
    }

    @Test
    void refusesToRewriteConsumedInventory() {
        assertThatThrownBy(
                        () ->
                                PurchaseValuation.revisedCost(
                                        BigDecimal.ONE,
                                        BigDecimal.TEN,
                                        BigDecimal.TEN,
                                        new BigDecimal("100"),
                                        BigDecimal.TEN,
                                        new BigDecimal("200")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
