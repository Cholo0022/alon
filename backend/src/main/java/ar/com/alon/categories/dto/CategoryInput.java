package ar.com.alon.categories.dto;

import jakarta.validation.constraints.*;

public record CategoryInput(@NotBlank @Size(max = 90) String name) {}
