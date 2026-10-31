// Define y valida los datos necesarios para crear o modificar un cliente.
package ar.com.alon.customers.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record CustomerInput(
        @NotBlank @Size(max = 150) String name,
        @Size(max = 40) String phone,
        @Email @Size(max = 160) String email,
        BigDecimal creditLimit) {}
