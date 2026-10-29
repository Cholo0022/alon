package ar.com.alon.shared.validation;

import static org.assertj.core.api.Assertions.*;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class DecimalRulesTest {
    @Test
    void preservesFractionalStockAndRejectsSubprecisionQuantities() {
        assertThat(DecimalRules.quantity(new BigDecimal("1.234"), true, "cantidad"))
                .isEqualByComparingTo("1.234");
        assertThatThrownBy(() -> DecimalRules.quantity(new BigDecimal("0.0001"), true, "cantidad"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DecimalRules.quantity(BigDecimal.ZERO, true, "cantidad"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DecimalRules.money(new BigDecimal("1.001"), "importe"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
