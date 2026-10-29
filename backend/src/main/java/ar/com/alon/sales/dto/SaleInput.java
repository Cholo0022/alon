package ar.com.alon.sales.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.List;

public record SaleInput(
        @Positive long customerId,
        LocalDate date,
        @Size(max = 30) String saleType,
        @Size(max = 30) String paymentMethod,
        @NotNull @Size(min = 1, max = 500) List<@NotNull @Valid SaleLine> lines) {}
