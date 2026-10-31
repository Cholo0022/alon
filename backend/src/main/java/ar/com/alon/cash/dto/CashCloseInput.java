// Define y valida los datos necesarios para cerrar una caja.
package ar.com.alon.cash.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;

public record CashCloseInput(LocalDate date, BigDecimal countedCash) {}
