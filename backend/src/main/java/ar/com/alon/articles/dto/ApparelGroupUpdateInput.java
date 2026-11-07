package ar.com.alon.articles.dto;

import ar.com.alon.articles.dto.PurchaseApparelInput.Variant;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;

public record ApparelGroupUpdateInput(
        @NotBlank @Pattern(regexp = "[0-9a-f]{64}") String token,
        @NotNull @Size(min = 1, max = 2000) List<@NotNull @Valid Line> lines,
        @Valid Common common,
        @Size(max = 100) List<@NotNull @Valid Variant> newVariants) {
    public record Line(
            @Positive long id,
            @NotNull @DecimalMin("0") @Digits(integer = 12, fraction = 0) BigDecimal stock,
            @Size(max = 30) String code) {}

    public record Common(
            @Size(min = 1, max = 80) String name,
            @DecimalMin("0") @Digits(integer = 13, fraction = 2) BigDecimal cost,
            @DecimalMin("0") @Digits(integer = 13, fraction = 2) BigDecimal salePrice,
            @Size(min = 1, max = 80) String model,
            @Size(min = 1, max = 80) String brand,
            @Size(min = 1, max = 120) String material,
            @Size(min = 1, max = 30) String sizeSystem,
            @Size(min = 1, max = 40) String audience,
            @Size(min = 1, max = 40) String season,
            @Size(min = 1, max = 4000) String description,
            Boolean publishOnWeb,
            @Size(max = 4) List<@NotNull @Valid ArticlePhotoInput> photos) {}
}
