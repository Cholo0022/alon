// Prepara y aplica aumentos por rubro después de comprobar que la vista previa sigue vigente.
package ar.com.alon.articles;

import ar.com.alon.security.RequestIdentity;
import ar.com.alon.shared.audit.AuditLog;
import ar.com.alon.shared.validation.DecimalRules;
import jakarta.validation.constraints.*;
import java.math.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CategoryPriceService {
    private final JdbcTemplate jdbc;
    private final RequestIdentity identity;
    private final AuditLog audit;

    /**
     * Recibe y conserva las dependencias necesarias para los aumentos por rubro con control de
     * concurrencia y auditoría.
     */
    public CategoryPriceService(JdbcTemplate jdbc, RequestIdentity identity, AuditLog audit) {
        this.jdbc = jdbc;
        this.identity = identity;
        this.audit = audit;
    }

    public record Input(
            @Positive long categoryId,
            @NotNull @DecimalMin("0.01") @DecimalMax("10000") @Digits(integer = 5, fraction = 2)
                    BigDecimal percent,
            @Pattern(regexp = "[a-f0-9]{64}") String expectedHash) {}

    /**
     * Valida el porcentaje de aumento, lo aplica al precio actual y respeta el redondeo individual
     * y la precisión monetaria.
     */
    public static BigDecimal increasedPrice(BigDecimal price, BigDecimal percent, boolean round) {
        if (percent == null
                || percent.compareTo(new BigDecimal("0.01")) < 0
                || percent.compareTo(new BigDecimal("10000")) > 0
                || percent.stripTrailingZeros().scale() > 2)
            throw new IllegalArgumentException(
                    "Ingresá un aumento entre 0,01 y 10.000%, con hasta dos decimales.");
        var result =
                price.multiply(BigDecimal.ONE.add(percent.movePointLeft(2)))
                        .setScale(2, RoundingMode.HALF_UP);
        if (round && result.signum() > 0)
            result =
                    result.divide(BigDecimal.TEN, 0, RoundingMode.CEILING)
                            .multiply(new BigDecimal("10.00"));
        return DecimalRules.money(result, "El precio supera el máximo permitido.");
    }

    /**
     * Valida permisos y rubro y prepara los precios anteriores y nuevos de sus artículos activos;
     * puede bloquearlos al confirmar.
     */
    private List<Map<String, Object>> changes(Input input, boolean lock) {
        identity.require("articulos");
        if (!identity.permits("compras")) identity.require("costos");
        var category =
                jdbc.queryForList(
                        "SELECT id FROM rubros WHERE comercio_id=? AND id=?",
                        identity.storeId(),
                        input.categoryId());
        if (category.isEmpty())
            throw new IllegalArgumentException("Elegí un rubro de tu comercio.");
        var rows =
                jdbc.queryForList(
                        "SELECT id,nombre,precio_venta,redondeo_precio FROM articulos WHERE comercio_id=? AND rubro_id=? AND estado='ACTIVO' ORDER BY id"
                                + (lock ? " FOR UPDATE" : ""),
                        identity.storeId(),
                        input.categoryId());
        var result = new ArrayList<Map<String, Object>>();
        for (var row : rows) {
            var price = new BigDecimal(row.get("precio_venta").toString());
            var flag = row.get("redondeo_precio");
            var rounded =
                    Boolean.TRUE.equals(flag)
                            || flag instanceof Number number && number.intValue() != 0;
            result.add(
                    Map.of(
                            "id",
                            row.get("id"),
                            "name",
                            row.get("nombre"),
                            "previousPrice",
                            price,
                            "newPrice",
                            increasedPrice(price, input.percent(), rounded),
                            "round",
                            rounded));
        }
        if (result.isEmpty())
            throw new IllegalArgumentException(
                    "El rubro no tiene artículos activos para modificar.");
        return result;
    }

    /**
     * Calcula una huella de rubro, porcentaje y precios para detectar cambios entre la vista previa
     * y la aplicación del aumento.
     */
    private String hash(Input input, List<Map<String, Object>> rows) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            digest.update(
                    (identity.storeId()
                                    + ":"
                                    + input.categoryId()
                                    + ":"
                                    + input.percent().stripTrailingZeros().toPlainString())
                            .getBytes(StandardCharsets.UTF_8));
            for (var row : rows)
                digest.update(
                        (":"
                                        + row.get("id")
                                        + ":"
                                        + row.get("previousPrice")
                                        + ":"
                                        + row.get("newPrice")
                                        + ":"
                                        + row.get("round"))
                                .getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    /**
     * Devuelve cantidad de artículos, hasta diez ejemplos y la huella con la que deberá confirmarse
     * el aumento.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> preview(Input input) {
        var rows = changes(input, false);
        return Map.of(
                "count",
                rows.size(),
                "sample",
                rows.stream().limit(10).toList(),
                "expectedHash",
                hash(input, rows));
    }

    /**
     * Verifica que la simulación siga vigente, actualiza los precios de venta del rubro y audita
     * cada cambio en una transacción.
     */
    @Transactional
    public Map<String, Object> apply(Input input) {
        jdbc.queryForObject(
                "SELECT id FROM comercios WHERE id=? FOR UPDATE", Long.class, identity.storeId());
        var rows = changes(input, true);
        // Se exige revisar otra vez si los artículos cambiaron después de mostrar la vista previa.
        if (input.expectedHash() == null || !input.expectedHash().equals(hash(input, rows)))
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Los artículos o sus precios cambiaron. Revisá nuevamente la vista previa.");
        for (var row : rows) {
            jdbc.update(
                    "UPDATE articulos SET precio_venta=? WHERE comercio_id=? AND id=?",
                    row.get("newPrice"),
                    identity.storeId(),
                    row.get("id"));
            audit.record(
                    "articulo",
                    ((Number) row.get("id")).longValue(),
                    "AUMENTO_RUBRO",
                    Map.of("precio_venta", row.get("previousPrice")),
                    Map.of(
                            "precio_venta",
                            row.get("newPrice"),
                            "porcentaje",
                            input.percent(),
                            "rubro_id",
                            input.categoryId()));
        }
        return Map.of("updated", rows.size(), "percent", input.percent());
    }
}
