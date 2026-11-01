package ar.com.alon.accounting;

import ar.com.alon.security.RequestIdentity;
import ar.com.alon.shared.commands.CommandExecutor;
import jakarta.validation.Valid;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@RequestMapping("/api/v1")
public class AccountingController {
    private final AccountingReport reports;
    private final AccountingService service;
    private final FiscalService fiscal;
    private final CommandExecutor commands;
    private final RequestIdentity identity;

    public AccountingController(
            AccountingReport reports,
            AccountingService service,
            FiscalService fiscal,
            CommandExecutor commands,
            RequestIdentity identity) {
        this.reports = reports;
        this.service = service;
        this.fiscal = fiscal;
        this.commands = commands;
        this.identity = identity;
    }

    @GetMapping("/accounting/options")
    public Map<String, Object> options() {
        var config = reports.settings();
        return Map.of(
                "controlPeriodos",
                config.get("control_periodos"),
                "cargaImpuestos",
                config.get("carga_impuestos"));
    }

    @GetMapping("/accounting/settings")
    public Map<String, Object> settings() {
        identity.require("contabilidad");
        return reports.settings();
    }

    @GetMapping("/accounting/report")
    public Map<String, Object> report(@RequestParam String month) {
        return reports.report(month);
    }

    @GetMapping("/accounting/history")
    public List<Map<String, Object>> history(@RequestParam String month) {
        return service.history(month);
    }

    @GetMapping("/accounting/closures/{id}")
    public Map<String, Object> closure(@PathVariable long id) {
        return reports.closed(id);
    }

    @PutMapping("/operations/accounting/settings")
    public Map<String, Object> settings(@Valid @RequestBody AccountingService.Settings input) {
        return commands.execute("accountingSettings", input, () -> service.settings(input));
    }

    @PostMapping("/operations/accounting/open")
    public Map<String, Object> open(@Valid @RequestBody AccountingService.Change input) {
        return commands.execute("openAccountingPeriod", input, () -> service.open(input));
    }

    @PostMapping("/operations/accounting/close")
    public Map<String, Object> close(@Valid @RequestBody AccountingService.Change input) {
        return commands.execute("closeAccountingPeriod", input, () -> service.close(input));
    }

    @GetMapping("/accounting/documents/{id}")
    public Map<String, Object> document(@PathVariable long id) {
        identity.require("contabilidad");
        return fiscal.document(id);
    }

    @PostMapping("/operations/accounting/documents")
    public Map<String, Object> create(@Valid @RequestBody FiscalInput input) {
        identity.require("contabilidad");
        return commands.execute("createFiscalDocument", input, () -> fiscal.save(null, input));
    }

    @PutMapping("/operations/accounting/documents/{id}")
    public Map<String, Object> update(
            @PathVariable long id, @Valid @RequestBody FiscalInput input) {
        identity.require("contabilidad");
        return commands.execute(
                "updateFiscalDocument", List.of(id, input), () -> fiscal.save(id, input));
    }

    @PostMapping("/operations/purchases/{id}/fiscal")
    public Map<String, Object> purchase(
            @PathVariable long id, @Valid @RequestBody FiscalInput input) {
        return linked(id, "COMPRA", input);
    }

    @PostMapping("/operations/sales/{id}/fiscal")
    public Map<String, Object> sale(@PathVariable long id, @Valid @RequestBody FiscalInput input) {
        return linked(id, "VENTA", input);
    }

    @PostMapping("/operations/cash/expenses/{id}/fiscal")
    public Map<String, Object> expense(
            @PathVariable long id, @Valid @RequestBody FiscalInput input) {
        return linked(id, "GASTO", input);
    }

    private Map<String, Object> linked(long id, String type, FiscalInput input) {
        if (!type.equals(input.clase()) || !Long.valueOf(id).equals(input.operacionId()))
            throw new IllegalArgumentException(
                    "El comprobante no corresponde a la operación indicada.");
        return commands.execute(
                "createLinkedFiscalDocument",
                List.of(type, id, input),
                () -> fiscal.save(null, input));
    }

    @GetMapping("/accounting/export")
    public ResponseEntity<StreamingResponseBody> export(
            @RequestParam String month, @RequestParam(required = false) Long closureId) {
        Map<String, Object> report =
                closureId == null ? reports.report(month) : reports.closed(closureId);
        String selected = String.valueOf(report.get("mes"));
        var business = (Map<?, ?>) report.get("configuracion");
        var identification = new LinkedHashMap<String, Object>();
        identification.put("nombre_comercio", business.get("nombre"));
        identification.put("cuit", business.get("cuit_contable"));
        identification.put("condicion_iva", business.get("condicion_iva"));
        identification.put("mes", report.get("mes"));
        identification.put("fuente", report.get("fuente"));
        identification.put("revision", report.get("revision"));
        identification.put("generado_en", report.get("generado_en"));
        StreamingResponseBody body =
                output -> {
                    try (var zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
                        for (String section :
                                List.of(
                                        "identificacion",
                                        "resumen",
                                        "ventas",
                                        "compras",
                                        "gastos",
                                        "caja",
                                        "fiscales",
                                        "alicuotas")) {
                            zip.putNextEntry(new ZipEntry(section + ".csv"));
                            zip.write(new byte[] {(byte) 0xef, (byte) 0xbb, (byte) 0xbf});
                            Object value =
                                    section.equals("identificacion")
                                            ? List.of(identification)
                                            : report.get(section);
                            List<Map<String, Object>> rows = new ArrayList<>();
                            if (value instanceof Map<?, ?> map) {
                                for (var entry : map.entrySet())
                                    rows.add(
                                            Map.of(
                                                    "concepto",
                                                    entry.getKey(),
                                                    "valor",
                                                    entry.getValue()));
                            } else if (value instanceof List<?> list) {
                                for (Object row : list) {
                                    if (row instanceof Map<?, ?> map) {
                                        var copy = new LinkedHashMap<String, Object>();
                                        map.forEach((k, v) -> copy.put(String.valueOf(k), v));
                                        rows.add(copy);
                                    }
                                }
                            }
                            var headers = new LinkedHashSet<String>();
                            rows.forEach(r -> headers.addAll(r.keySet()));
                            writeCsv(zip, new ArrayList<>(headers));
                            for (var row : rows)
                                writeCsv(zip, headers.stream().map(row::get).toList());
                            zip.closeEntry();
                        }
                        zip.putNextEntry(new ZipEntry("LEEME.txt"));
                        zip.write(
                                ("Informe mensual para el contador · "
                                                + selected
                                                + "\nFuente: "
                                                + report.get("fuente")
                                                + "\nGenerado: "
                                                + report.get("generado_en")
                                                + "\n\n"
                                                + String.join(
                                                        "\n", (List<String>) report.get("notas")))
                                        .getBytes(StandardCharsets.UTF_8));
                        zip.closeEntry();
                    }
                };
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/zip"))
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename("Alon-contabilidad-" + selected + ".zip")
                                .build()
                                .toString())
                .body(body);
    }

    static void writeCsv(OutputStream output, List<?> values) throws IOException {
        var escaped = new ArrayList<String>();
        for (Object value : values) {
            String text = value == null ? "" : value.toString();
            if (!(value instanceof Number)
                    && !text.isEmpty()
                    && "=+-@\t\r"
                                    .indexOf(
                                            text.stripLeading().isEmpty()
                                                    ? text.charAt(0)
                                                    : text.stripLeading().charAt(0))
                            >= 0) text = "'" + text;
            escaped.add("\"" + text.replace("\"", "\"\"") + "\"");
        }
        output.write((String.join(";", escaped) + "\r\n").getBytes(StandardCharsets.UTF_8));
    }
}
