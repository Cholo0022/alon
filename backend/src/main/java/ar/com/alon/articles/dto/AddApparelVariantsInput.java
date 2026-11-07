package ar.com.alon.articles.dto;

import ar.com.alon.articles.dto.PurchaseApparelInput.Variant;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

public record AddApparelVariantsInput(
        @NotNull @Size(min = 1, max = 100) List<@NotNull @Valid Variant> variants) {}
