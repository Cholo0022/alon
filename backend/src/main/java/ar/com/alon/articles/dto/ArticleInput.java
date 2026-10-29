package ar.com.alon.articles.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record ArticleInput(
        @NotBlank @Size(max = 30) String code,
        @NotBlank @Size(max = 180) String name,
        @Positive long categoryId,
        @Positive long supplierId,
        @Size(max = 30) String unit,
        BigDecimal cost,
        BigDecimal freightPerUnit,
        BigDecimal freightPercent,
        boolean freightByValue,
        boolean freightByPercent,
        BigDecimal marginPercent,
        boolean roundPrice,
        BigDecimal initialStock,
        BigDecimal minimumStock,
        BigDecimal salePrice) {}
