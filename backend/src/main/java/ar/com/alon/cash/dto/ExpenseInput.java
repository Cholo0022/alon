package ar.com.alon.cash.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;

public record ExpenseInput(
        LocalDate date,
        @NotBlank @Size(max = 50) String category,
        @NotBlank @Size(max = 180) String description,
        @Size(max = 30) String paymentMethod,
        BigDecimal amount) {}
