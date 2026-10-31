// Valida la precisión de cantidades e importes antes de guardarlos.
package ar.com.alon.shared.validation;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class DecimalRules {

    /** Impide instanciar esta clase de cálculos estáticos. */
    private DecimalRules() {}

    /**
     * Valida cantidades de hasta tres decimales y quince dígitos, exigiendo valor positivo cuando
     * lo solicita la operación.
     */
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

    /**
     * Valida importes no negativos de hasta dos decimales y quince dígitos sin redondearlos
     * silenciosamente.
     */
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
