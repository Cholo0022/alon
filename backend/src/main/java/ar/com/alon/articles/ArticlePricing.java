// Calcula el precio de venta a partir del costo, el flete, la ganancia y el redondeo.
package ar.com.alon.articles;

import java.math.*;

public final class ArticlePricing {

    /** Impide instanciar esta clase de cálculos estáticos. */
    private ArticlePricing() {}

    /**
     * Calcula el precio desde costo, flete fijo y porcentual y margen; aplica redondeo hacia arriba
     * a decenas cuando está habilitado.
     */
    public static BigDecimal calculateSalePrice(
            BigDecimal cost,
            BigDecimal freight,
            BigDecimal freightPercent,
            BigDecimal marginPercent,
            boolean roundUp) {
        BigDecimal landedCost =
                cost.add(freight)
                        .add(
                                cost.multiply(freightPercent)
                                        .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP));
        BigDecimal price =
                landedCost
                        .multiply(
                                BigDecimal.ONE.add(
                                        marginPercent.divide(
                                                new BigDecimal("100"), 6, RoundingMode.HALF_UP)))
                        .setScale(2, RoundingMode.HALF_UP);
        if (roundUp && price.signum() > 0)
            price =
                    price.divide(new BigDecimal("10"), 0, RoundingMode.CEILING)
                            .multiply(new BigDecimal("10.00"));
        return price;
    }
}
