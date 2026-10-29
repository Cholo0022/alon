package ar.com.alon.purchases.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record PurchaseLineInput(
        @Positive long articleId, BigDecimal quantity, BigDecimal unitCost) {}
