// Define y valida los datos necesarios para registrar una compra.
package ar.com.alon.purchases.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record PurchaseInput(
        @Positive long supplierId,
        @Size(max = 20) String documentType,
        @NotBlank @Size(max = 50) String documentNumber,
        LocalDate date,
        @NotNull @Size(min = 1, max = 500) List<@NotNull @Valid PurchaseLineInput> lines,
        BigDecimal extraCosts,
        BigDecimal paid,
        @Size(max = 30) String paymentType,
        @Size(max = 30) String paymentMethod,
        Boolean updateCost) {
    /**
     * Mantiene compatible el constructor anterior de compras y deja sin especificar la nueva
     * decisión de costo para conservar su comportamiento histórico.
     */
    public PurchaseInput(
            long supplierId,
            String documentType,
            String documentNumber,
            LocalDate date,
            List<PurchaseLineInput> lines,
            BigDecimal extraCosts,
            BigDecimal paid,
            String paymentType,
            String paymentMethod) {
        this(
                supplierId,
                documentType,
                documentNumber,
                date,
                lines,
                extraCosts,
                paid,
                paymentType,
                paymentMethod,
                null);
    }
}
