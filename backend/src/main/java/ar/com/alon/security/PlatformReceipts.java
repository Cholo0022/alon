package ar.com.alon.security;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Recibos administrativos del servicio Alon, independientes de la caja de cada comercio. */
@RestController
@RequestMapping("/api/v1/super/businesses/{businessId}")
@Transactional
public class PlatformReceipts {
    private final JdbcTemplate jdbc;
    private final RequestIdentity identity;

    public PlatformReceipts(JdbcTemplate jdbc, RequestIdentity identity) {
        this.jdbc = jdbc;
        this.identity = identity;
    }

    private void authorize() {
        if (!"SUPER".equals(identity.user().get("rol"))) throw error(HttpStatus.FORBIDDEN, "Solo Alon puede administrar los cobros del servicio.");
    }

    @GetMapping("/account")
    public Map<String, Object> account(@PathVariable long businessId) {
        authorize();
        return Map.of("receipts", jdbc.queryForList("SELECT * FROM plataforma_recibos WHERE negocio_id=? ORDER BY id DESC", businessId),
                "audit", jdbc.queryForList("SELECT accion,detalle,fecha,usuario_id FROM plataforma_auditoria WHERE negocio_id=? ORDER BY id DESC", businessId));
    }

    @PostMapping("/receipts")
    public Map<String, Object> issue(@PathVariable long businessId, @Valid @RequestBody Receipt input) {
        authorize();
        var rows = jdbc.queryForList("SELECT abono FROM plataforma_negocios WHERE id=? FOR UPDATE", businessId);
        if (rows.isEmpty()) throw error(HttpStatus.NOT_FOUND, "No se encontró el cliente.");
        String fingerprint = fingerprint(businessId, input);
        var previous = jdbc.queryForList("SELECT id,huella FROM plataforma_recibos WHERE solicitud=?", input.requestId());
        if (!previous.isEmpty()) {
            if (!fingerprint.equals(previous.getFirst().get("huella"))) throw error(HttpStatus.CONFLICT, "La referencia ya corresponde a otro recibo. Consultá el historial.");
            return Map.of("id", previous.getFirst().get("id"), "replayed", true);
        }
        YearMonth start = parsePeriod(input.period());
        if (input.months() < 1 || input.months() > 120) throw error(HttpStatus.BAD_REQUEST, "Elegí entre 1 y 120 meses.");
        BigDecimal discount = input.discountPercent();
        if (discount == null || discount.signum() < 0 || discount.compareTo(new BigDecimal("100")) > 0 || discount.scale() > 2)
            throw error(HttpStatus.BAD_REQUEST, "La bonificación debe estar entre 0 y 100, con hasta dos decimales.");
        for (int i = 0; i < input.months(); i++) {
            LocalDate period = start.plusMonths(i).atDay(1);
            if (jdbc.queryForObject("SELECT COUNT(*) FROM plataforma_periodos_cobrados WHERE negocio_id=? AND periodo=?", Long.class, businessId, period) > 0)
                throw error(HttpStatus.CONFLICT, "El período " + YearMonth.from(period) + " ya está cobrado. Revisá el recibo existente.");
        }
        BigDecimal unit = (BigDecimal) rows.getFirst().get("abono");
        BigDecimal subtotal = unit.multiply(BigDecimal.valueOf(input.months())).setScale(2, RoundingMode.HALF_UP);
        BigDecimal total = discounted(subtotal, discount);
        jdbc.update("INSERT INTO plataforma_recibos(negocio_id,solicitud,huella,fecha,periodo_desde,meses,abono_unitario,bonificacion,subtotal,total,creado_por) VALUES(?,?,?,?,?,?,?,?,?,?,?)",
                businessId, input.requestId(), fingerprint, input.date(), start.atDay(1), input.months(), unit, discount, subtotal, total, identity.userId());
        long id = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        for (int i = 0; i < input.months(); i++)
            jdbc.update("INSERT INTO plataforma_periodos_cobrados(negocio_id,periodo,recibo_id) VALUES(?,?,?)", businessId, start.plusMonths(i).atDay(1), id);
        if (total.signum() > 0) {
            String concept = "Recibo Alon #" + id + " · " + start + " · " + input.months() + " meses";
            // Cargo neto del abono y su cobro: el recibo no genera crédito ficticio ni modifica el acceso.
            for (String type : List.of("CARGO", "COBRO"))
                jdbc.update("INSERT INTO plataforma_movimientos(negocio_id,tipo,fecha,importe,concepto,recibo_id) VALUES(?,?,?,?,?,?)", businessId, type, input.date(), total, concept, id);
        }
        audit(businessId, "RECIBO", "Recibo #" + id + " · " + input.months() + " meses · Bonificación " + discount + "% · Total " + total);
        return Map.of("id", id, "total", total, "replayed", false);
    }

    @PostMapping("/receipts/{receiptId}/void")
    public Map<String, Object> cancel(@PathVariable long businessId, @PathVariable long receiptId, @Valid @RequestBody Cancellation input) {
        authorize();
        jdbc.queryForObject("SELECT id FROM plataforma_negocios WHERE id=? FOR UPDATE", Long.class, businessId);
        var receipts = jdbc.queryForList("SELECT estado FROM plataforma_recibos WHERE negocio_id=? AND id=? FOR UPDATE", businessId, receiptId);
        if (receipts.isEmpty()) throw error(HttpStatus.NOT_FOUND, "No se encontró el recibo de este cliente.");
        if ("ANULADO".equals(receipts.getFirst().get("estado"))) return Map.of("ok", true);
        if (input.reason() == null || input.reason().isBlank()) throw error(HttpStatus.BAD_REQUEST, "Indicá el motivo de anulación.");
        jdbc.update("UPDATE plataforma_recibos SET estado='ANULADO',anulado_por=?,anulado_en=CURRENT_TIMESTAMP,motivo_anulacion=? WHERE negocio_id=? AND id=?", identity.userId(), input.reason().trim(), businessId, receiptId);
        jdbc.update("UPDATE plataforma_movimientos SET anulado=TRUE WHERE negocio_id=? AND recibo_id=?", businessId, receiptId);
        jdbc.update("DELETE FROM plataforma_periodos_cobrados WHERE negocio_id=? AND recibo_id=?", businessId, receiptId);
        audit(businessId, "ANULAR_RECIBO", "Recibo #" + receiptId + ". " + input.reason().trim());
        return Map.of("ok", true);
    }

    static BigDecimal discounted(BigDecimal subtotal, BigDecimal discount) {
        return subtotal.multiply(new BigDecimal("100").subtract(discount)).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
    }

    static YearMonth parsePeriod(String period) {
        try {
            return YearMonth.parse(period);
        } catch (RuntimeException failure) {
            throw error(HttpStatus.BAD_REQUEST, "Elegí el primer mes a cobrar.");
        }
    }

    private String fingerprint(long businessId, Receipt input) {
        try {
            String body = businessId + "|" + input.date() + "|" + input.period() + "|" + input.months() + "|" + input.discountPercent().stripTrailingZeros().toPlainString();
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private void audit(long businessId, String action, String detail) {
        jdbc.update("INSERT INTO plataforma_auditoria(usuario_id,negocio_id,accion,detalle) VALUES(?,?,?,?)", identity.userId(), businessId, action, detail);
    }

    private static ResponseStatusException error(HttpStatus status, String message) {
        return new ResponseStatusException(status, message);
    }

    public record Receipt(
            @NotBlank @Pattern(regexp = "[0-9a-fA-F-]{36}") String requestId,
            @NotNull LocalDate date,
            @NotBlank @Pattern(regexp = "[0-9]{4}-(0[1-9]|1[0-2])") String period,
            @Min(1) @Max(120) int months,
            @NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2) BigDecimal discountPercent) {}

    public record Cancellation(@NotBlank @Size(max = 300) String reason) {}
}
