// Define y valida los datos necesarios para crear o modificar un artículo.
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
        Boolean roundPrice,
        BigDecimal initialStock,
        BigDecimal minimumStock,
        BigDecimal salePrice,
        @Size(max = 4)
                java.util.List<
                                @jakarta.validation.constraints.NotNull @jakarta.validation.Valid
                                ArticlePhotoInput>
                        photos,
        boolean publishOnWeb) {
    /** Aplica redondeo de precio por defecto cuando el formulario no especifica esa preferencia. */
    public ArticleInput {
        if (roundPrice == null) roundPrice = true;
    }
}
