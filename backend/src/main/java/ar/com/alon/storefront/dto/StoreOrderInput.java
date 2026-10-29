package ar.com.alon.storefront.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record StoreOrderInput(
        @NotBlank @Size(max = 150) String customerName,
        @NotBlank @Size(max = 100) String contact,
        @NotBlank @Size(max = 500) String details,
        BigDecimal estimatedTotal) {}
