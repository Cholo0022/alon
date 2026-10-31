// Define y valida los datos necesarios para registrar un pago a un proveedor.
package ar.com.alon.suppliers.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;

public record SupplierReceiptInput(
        @Positive long supplierId,
        LocalDate date,
        BigDecimal amount,
        @Size(max = 30) String paymentMethod) {}
