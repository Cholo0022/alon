package ar.com.alon.articles.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

/** Datos comunes de una prenda y cantidades independientes por talle y color. */
public record PurchaseApparelInput(
        @NotNull @Valid ArticleInput product,
        @NotNull @Size(min = 1, max = 100) List<@NotNull @Valid Variant> variants) {
    public record Variant(
            @NotBlank @Size(max = 30) String code,
            @NotBlank @Size(max = 30) String size,
            @NotBlank @Size(max = 60) String color,
            @Min(0) @Max(1000000000) int quantity) {}
}
