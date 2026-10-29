package ar.com.alon.shared.validation;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class DecimalRules {
    private DecimalRules() {}

    public static BigDecimal quantity(BigDecimal value, boolean strictlyPositive, String message) {
        if (value == null || value.signum() < 0) throw new IllegalArgumentException(message);
        final BigDecimal normalized;
        try {
            normalized = value.setScale(3, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("La cantidad admite hasta tres decimales.");
        }
        if (normalized.precision() > 15 || strictlyPositive && normalized.signum() == 0)
            throw new IllegalArgumentException(message);
        return normalized;
    }

    public static BigDecimal money(BigDecimal value, String message) {
        if (value == null || value.signum() < 0) throw new IllegalArgumentException(message);
        final BigDecimal normalized;
        try {
            normalized = value.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("El importe admite hasta dos decimales.");
        }
        if (normalized.precision() > 15)
            throw new IllegalArgumentException("El importe supera el máximo permitido.");
        return normalized;
    }
}
