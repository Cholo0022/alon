package ar.com.alon.accounting;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class AccountingRulesTest {
    private FiscalInput input(
            String letter, BigDecimal credit, BigDecimal total, List<FiscalInput.Iva> lines) {
        return new FiscalInput(
                "COMPRA",
                "FACTURA",
                letter,
                1,
                10,
                "Proveedor",
                "CUIT",
                "20123456786",
                LocalDate.of(2026, 10, 2),
                "2026-10",
                null,
                "",
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                credit,
                total,
                true,
                "",
                "",
                "",
                lines);
    }

    @Test
    void validatesCuitTotalsScaleRatesAndCreditWithoutAssumingFullVatCredit() {
        assertThat(FiscalInput.validCuit("30712345671")).isTrue();
        assertThat(FiscalInput.validCuit("20123456786")).isTrue();
        assertThat(FiscalInput.validCuit("20123456780")).isFalse();
        var lines =
                List.of(
                        new FiscalInput.Iva(
                                new BigDecimal("21"), new BigDecimal("100"), new BigDecimal("21")));
        assertThatCode(
                        () ->
                                input("A", new BigDecimal("15"), new BigDecimal("121"), lines)
                                        .validate())
                .doesNotThrowAnyException();
        assertThatThrownBy(
                        () ->
                                input("A", new BigDecimal("22"), new BigDecimal("121"), lines)
                                        .validate())
                .hasMessageContaining("crédito");
        assertThatThrownBy(
                        () ->
                                input("B", new BigDecimal("1"), new BigDecimal("121"), lines)
                                        .validate())
                .hasMessageContaining("crédito");
        assertThatThrownBy(
                        () -> input("A", BigDecimal.ZERO, new BigDecimal("120"), lines).validate())
                .hasMessageContaining("total");
        assertThatThrownBy(
                        () ->
                                input(
                                                "A",
                                                BigDecimal.ZERO,
                                                new BigDecimal("121"),
                                                List.of(lines.getFirst(), lines.getFirst()))
                                        .validate())
                .hasMessageContaining("repetida");
        assertThatThrownBy(
                        () ->
                                input("A", new BigDecimal("0.001"), new BigDecimal("121"), lines)
                                        .validate())
                .hasMessageContaining("decimales");
    }

    @Test
    void optionalPeriodsStayOptionalButAnExplicitCloseCannotBeBypassedByDisablingTheOption() {
        var jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), any(java.sql.Date.class), anyLong()))
                .thenReturn(List.of(Map.of("control_periodos", false, "estado", "")));
        assertThatCode(() -> AccountingPeriods.check(jdbc, 1, LocalDate.of(2026, 10, 2)))
                .doesNotThrowAnyException();
        when(jdbc.queryForList(anyString(), any(java.sql.Date.class), anyLong()))
                .thenReturn(List.of(Map.of("control_periodos", true, "estado", "")));
        assertThatThrownBy(() -> AccountingPeriods.check(jdbc, 1, LocalDate.of(2026, 10, 2)))
                .hasMessageContaining("no está abierto");
        when(jdbc.queryForList(anyString(), any(java.sql.Date.class), anyLong()))
                .thenReturn(List.of(Map.of("control_periodos", false, "estado", "CERRADO")));
        assertThatThrownBy(() -> AccountingPeriods.check(jdbc, 1, LocalDate.of(2026, 10, 2)))
                .hasMessageContaining("está cerrado");
        verify(jdbc, times(3))
                .queryForObject("SELECT id FROM comercios WHERE id=? FOR UPDATE", Long.class, 1L);
    }

    @Test
    void csvNeutralizesSpreadsheetFormulasAndPreservesNegativeNumericAmounts() throws Exception {
        var output = new java.io.ByteArrayOutputStream();
        AccountingController.writeCsv(
                output,
                List.of("=HYPERLINK(\"bad\")", new BigDecimal("-21.00"), "texto;con separador"));
        assertThat(output.toString(java.nio.charset.StandardCharsets.UTF_8))
                .contains("'=")
                .contains("\"-21.00\"")
                .contains("texto;con separador");
    }

    @Test
    void documentEntryPermissionDoesNotImplyPermissionToCertifyItsReview() {
        var identity = mock(ar.com.alon.security.RequestIdentity.class);
        when(identity.permits("compras")).thenReturn(true);
        var fiscal =
                new FiscalService(
                        mock(JdbcTemplate.class),
                        identity,
                        java.time.Clock.systemUTC(),
                        mock(ar.com.alon.shared.audit.AuditLog.class));
        var lines =
                List.of(
                        new FiscalInput.Iva(
                                new BigDecimal("21"), new BigDecimal("100"), new BigDecimal("21")));
        assertThatThrownBy(
                        () ->
                                fiscal.save(
                                        null,
                                        input("A", BigDecimal.ZERO, new BigDecimal("121"), lines)))
                .hasMessageContaining("Contabilidad");
    }

    @Test
    void exportedPackageIncludesBusinessIdentificationAndDoesNotPretendToBeAnArcaFile()
            throws Exception {
        var reports = mock(AccountingReport.class);
        var report = new LinkedHashMap<String, Object>();
        report.put("mes", "2026-10");
        report.put(
                "configuracion",
                Map.of(
                        "nombre",
                        "Negocio de prueba",
                        "cuit_contable",
                        "30712345671",
                        "condicion_iva",
                        "RESPONSABLE_INSCRIPTO"));
        report.put("fuente", "ACTUAL");
        report.put("generado_en", "2026-10-31T20:00:00");
        report.put("resumen", Map.of("iva_debito_controlado", new BigDecimal("21.00")));
        report.put("notas", List.of("Informe auxiliar. No es una declaración jurada."));
        for (String section :
                List.of("ventas", "compras", "gastos", "caja", "fiscales", "alicuotas"))
            report.put(section, List.of());
        when(reports.report("2026-10")).thenReturn(report);
        var controller =
                new AccountingController(
                        reports,
                        mock(AccountingService.class),
                        mock(FiscalService.class),
                        mock(ar.com.alon.shared.commands.CommandExecutor.class),
                        mock(ar.com.alon.security.RequestIdentity.class));
        var output = new java.io.ByteArrayOutputStream();
        controller.export("2026-10", null).getBody().writeTo(output);
        var names = new ArrayList<String>();
        try (var zip =
                new java.util.zip.ZipInputStream(
                        new java.io.ByteArrayInputStream(output.toByteArray()))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                names.add(entry.getName());
                String text =
                        new String(zip.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                if (entry.getName().equals("identificacion.csv"))
                    assertThat(text).contains("30712345671").contains("nombre_comercio");
            }
        }
        assertThat(names)
                .contains(
                        "identificacion.csv",
                        "resumen.csv",
                        "fiscales.csv",
                        "alicuotas.csv",
                        "LEEME.txt");
    }
}
