package ar.com.alon.suppliers.dto;

import jakarta.validation.constraints.*;

public record SupplierInput(
        @NotBlank @Size(max = 150) String name,
        @Size(max = 13) String taxId,
        @Size(max = 90) String specialty,
        @Size(max = 40) String phone,
        @Email @Size(max = 160) String email) {}
