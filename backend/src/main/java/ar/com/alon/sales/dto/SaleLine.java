// Define y valida los datos necesarios para indicar un artículo y su cantidad en una venta.
package ar.com.alon.sales.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record SaleLine(@Positive long articleId, BigDecimal quantity, BigDecimal discountPercent) {}
