// Define y valida los datos necesarios para indicar un artículo, su cantidad y su costo en una
// compra.
package ar.com.alon.purchases.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record PurchaseLineInput(
        @Positive long articleId, BigDecimal quantity, BigDecimal unitCost) {}
