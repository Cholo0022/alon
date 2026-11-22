// Define y valida los datos necesarios para indicar un artículo y su cantidad en una venta.
package ar.com.alon.sales.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record SaleLine(
        @Positive Long articleId,
        BigDecimal quantity,
        BigDecimal discountPercent,
        @Size(max = 180) String description,
        BigDecimal unitPrice) {
    public SaleLine(long articleId, BigDecimal quantity, BigDecimal discountPercent) {
        this(articleId, quantity, discountPercent, null, null);
    }
}
