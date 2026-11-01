package ar.com.alon.accounting;

import ar.com.alon.shared.validation.DecimalRules;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.*;
import java.time.LocalDate;
import java.util.*;

public record FiscalInput(
        @NotBlank String clase,
        @NotBlank String tipo,
        @NotBlank String letra,
        int puntoVenta,
        long numero,
        @NotBlank @Size(max = 150) String tercero,
        @NotBlank String documentoTipo,
        @NotNull @Size(max = 20) String documento,
        @NotNull LocalDate fecha,
        @NotBlank String periodo,
        Long operacionId,
        @NotNull @Size(max = 100) String comprobanteAsociado,
        @NotNull BigDecimal exento,
        @NotNull BigDecimal noGravado,
        @NotNull BigDecimal otrosTributos,
        @NotNull BigDecimal percepcionIva,
        @NotNull BigDecimal percepcionIibb,
        @NotNull BigDecimal creditoComputable,
        @NotNull BigDecimal total,
        boolean controlado,
        @NotNull String autorizacionTipo,
        @NotNull @Size(max = 14) String autorizacionCodigo,
        @NotNull @Size(max = 500) String observaciones,
        @NotNull @Size(max = 6) List<@NotNull @Valid Iva> alicuotas) {
    public record Iva(
            @NotNull BigDecimal alicuota, @NotNull BigDecimal neto, @NotNull BigDecimal iva) {}

    public BigDecimal ivaTotal() {
        return alicuotas.stream().map(Iva::iva).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public boolean creditNote() {
        return tipo.equals("NOTA_CREDITO");
    }

    public static boolean validCuit(String value) {
        if (value == null || !value.matches("[0-9]{11}")) return false;
        int[] weights = {5, 4, 3, 2, 7, 6, 5, 4, 3, 2};
        int sum = 0;
        for (int i = 0; i < 10; i++) sum += (value.charAt(i) - '0') * weights[i];
        int digit = 11 - sum % 11;
        if (digit == 11) digit = 0;
        return digit < 10 && digit == value.charAt(10) - '0';
    }

    private static BigDecimal money(BigDecimal value) {
        return DecimalRules.money(
                value, "Los importes deben ser positivos o cero y tener hasta dos decimales.");
    }

    public void validate() {
        if (!Set.of("VENTA", "COMPRA", "GASTO").contains(clase)
                || !Set.of("FACTURA", "NOTA_CREDITO", "NOTA_DEBITO", "INTERNO_NO_FISCAL")
                        .contains(tipo))
            throw new IllegalArgumentException("Clase o tipo de comprobante inválido.");
        boolean internal = tipo.equals("INTERNO_NO_FISCAL");
        if (internal && !clase.equals("GASTO"))
            throw new IllegalArgumentException(
                    "El registro no fiscal solo está disponible para gastos sin crédito fiscal.");
        if (!(internal ? letra.equals("X") : Set.of("A", "B", "C").contains(letra)))
            throw new IllegalArgumentException("Elegí la letra del comprobante (A, B o C).");
        if (puntoVenta < (internal ? 0 : 1)
                || puntoVenta > 99999
                || numero < 1
                || numero > 99999999)
            throw new IllegalArgumentException(
                    "Revisá el punto de venta y el número de comprobante.");
        if (!Set.of("CUIT", "DNI", "CONSUMIDOR_FINAL", "SIN_IDENTIFICAR").contains(documentoTipo))
            throw new IllegalArgumentException("Tipo de documento inválido.");
        if (documentoTipo.equals("CUIT") && !validCuit(documento))
            throw new IllegalArgumentException("La CUIT del tercero no es válida.");
        if (documentoTipo.equals("DNI") && !documento.matches("[0-9]{7,8}"))
            throw new IllegalArgumentException("El DNI debe tener 7 u 8 dígitos.");
        if (!clase.equals("VENTA") && !internal && !documentoTipo.equals("CUIT"))
            throw new IllegalArgumentException(
                    "Las facturas de compra y gastos requieren la CUIT del emisor.");
        if (!tipo.equals("FACTURA") && !internal && comprobanteAsociado.isBlank())
            throw new IllegalArgumentException(
                    "Las notas de crédito y débito requieren el comprobante asociado.");
        var seen = new HashSet<BigDecimal>();
        BigDecimal sum =
                money(exento)
                        .add(money(noGravado))
                        .add(money(otrosTributos))
                        .add(money(percepcionIva))
                        .add(money(percepcionIibb));
        for (Iva line : alicuotas) {
            var rate = line.alicuota().stripTrailingZeros();
            if (!Set.of("0", "2.5", "5", "10.5", "21", "27").contains(rate.toPlainString())
                    || !seen.add(rate))
                throw new IllegalArgumentException("Alícuota no admitida o repetida.");
            money(line.neto());
            money(line.iva());
            if (line.neto().signum() == 0 && line.iva().signum() != 0
                    || rate.signum() == 0 && line.iva().signum() != 0)
                throw new IllegalArgumentException(
                        "El IVA no coincide con una base gravada válida.");
            BigDecimal expected =
                    line.neto()
                            .multiply(rate)
                            .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
            if (controlado
                    && expected.subtract(line.iva()).abs().compareTo(new BigDecimal("0.02")) > 0
                    && observaciones.trim().length() < 10)
                throw new IllegalArgumentException(
                        "La diferencia de IVA requiere una observación antes de marcar el comprobante controlado.");
            sum = sum.add(line.neto()).add(line.iva());
        }
        if (money(total).signum() == 0 || sum.compareTo(total) != 0)
            throw new IllegalArgumentException(
                    "El total del comprobante debe coincidir exactamente con netos, IVA, exentos, no gravados y otros tributos.");
        money(creditoComputable);
        if (creditoComputable.compareTo(ivaTotal()) > 0
                || (clase.equals("VENTA") || !letra.equals("A")) && creditoComputable.signum() != 0)
            throw new IllegalArgumentException(
                    "El crédito computable debe ser cero en ventas y comprobantes B, C o no fiscales, y no puede superar el IVA registrado.");
        if ((letra.equals("C") || internal) && ivaTotal().signum() != 0)
            throw new IllegalArgumentException(
                    "Este tipo de comprobante no admite IVA discriminado.");
        if (!Set.of("", "CAE", "CAI", "CAEA").contains(autorizacionTipo)
                || (!autorizacionTipo.isEmpty() && !autorizacionCodigo.matches("[0-9]{14}"))
                || autorizacionTipo.isEmpty() && !autorizacionCodigo.isEmpty())
            throw new IllegalArgumentException("Revisá el tipo y los 14 dígitos de autorización.");
        if (operacionId != null && operacionId <= 0)
            throw new IllegalArgumentException("Operación vinculada inválida.");
    }
}
