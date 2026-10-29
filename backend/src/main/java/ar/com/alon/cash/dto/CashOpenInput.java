package ar.com.alon.cash.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;

public record CashOpenInput(LocalDate date, BigDecimal openingBalance) {}
