package ar.com.alon.articles.dto;

import jakarta.validation.constraints.*;

/** Cada variante vendible identifica un talle y color con existencias propias. */
public record ApparelInput(
        @NotBlank @Pattern(regexp = "INDUMENTARIA|CALZADO") String kind,
        @NotBlank @Size(max = 80) String model,
        @NotBlank @Size(max = 80) String brand,
        @NotBlank @Size(max = 30) String size,
        @NotBlank @Size(max = 30) String sizeSystem,
        @NotBlank @Size(max = 60) String color,
        @NotBlank @Size(max = 120) String material,
        @NotBlank @Size(max = 40) String audience,
        @NotBlank @Size(max = 40) String season,
        @NotBlank @Size(max = 4000) String description) {}
