package ar.com.alon.articles;

import static org.assertj.core.api.Assertions.*;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class CategoryPriceServiceTest {
    @Test
    void increasesSalePricesAndRespectsRounding() {
        assertThat(
                        CategoryPriceService.increasedPrice(
                                new BigDecimal("105"), new BigDecimal("10"), false))
                .isEqualByComparingTo("115.50");
        assertThat(
                        CategoryPriceService.increasedPrice(
                                new BigDecimal("105"), new BigDecimal("10"), true))
                .isEqualByComparingTo("120");
        assertThat(
                        CategoryPriceService.increasedPrice(
                                new BigDecimal("100"), new BigDecimal("10"), true))
                .isEqualByComparingTo("110");
    }

    @Test
    void refusesInvalidPercentagesAndOverflow() {
        for (String percent : new String[] {"0", "-1", "0.001", "10001"})
            assertThatThrownBy(
                            () ->
                                    CategoryPriceService.increasedPrice(
                                            BigDecimal.TEN, new BigDecimal(percent), false))
                    .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () ->
                                CategoryPriceService.increasedPrice(
                                        new BigDecimal("9999999999999.99"), BigDecimal.TEN, false))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
