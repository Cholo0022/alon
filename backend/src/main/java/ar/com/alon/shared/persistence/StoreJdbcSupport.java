package ar.com.alon.shared.persistence;

import ar.com.alon.security.RequestIdentity;
import ar.com.alon.shared.audit.AuditLog;
import ar.com.alon.shared.validation.DecimalRules;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Clock;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

/** Shared JDBC primitives; business flows live in services for each module. */
public abstract class StoreJdbcSupport {
    protected final JdbcTemplate jdbc;
    protected final RequestIdentity identity;
    protected final Clock clock;
    protected final AuditLog audit;

    protected StoreJdbcSupport(
            JdbcTemplate jdbc, RequestIdentity identity, Clock clock, AuditLog audit) {
        this.jdbc = jdbc;
        this.identity = identity;
        this.clock = clock;
        this.audit = audit;
    }

    protected void assertBelongs(String table, long id) {
        if (id <= 0) throw new IllegalArgumentException("Seleccioná un registro válido.");
        String activeClause = table.equals("rubros") ? "" : " AND estado='ACTIVO'";
        Integer count =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM "
                                + table
                                + " WHERE id=? AND comercio_id=?"
                                + activeClause,
                        Integer.class,
                        id,
                        identity.storeId());
        if (count == null || count == 0)
            throw new IllegalArgumentException(
                    "El registro seleccionado no existe o está inactivo.");
    }

    protected long insert(String sql, Object... args) {
        KeyHolder holder = new GeneratedKeyHolder();
        jdbc.update(
                connection -> {
                    PreparedStatement statement =
                            connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
                    for (int i = 0; i < args.length; i++) statement.setObject(i + 1, args[i]);
                    return statement;
                },
                holder);
        Number key = holder.getKey();
        if (key == null)
            throw new IllegalStateException("No se pudo generar el identificador del registro.");
        return key.longValue();
    }

    protected static String value(String value) {
        return value == null ? "" : value.trim();
    }

    protected static String required(String value, String message) {
        String result = value(value);
        if (result.isBlank()) throw new IllegalArgumentException(message);
        return result;
    }

    protected static BigDecimal positive(BigDecimal value, String message) {
        BigDecimal result = nonNegative(value, message);
        if (result.signum() == 0) throw new IllegalArgumentException(message);
        return result;
    }

    protected static BigDecimal positiveQuantity(BigDecimal value, String message) {
        if (value == null || value.signum() <= 0) throw new IllegalArgumentException(message);
        return DecimalRules.quantity(value, true, message);
    }

    protected static BigDecimal nonNegative(BigDecimal value, String message) {
        return DecimalRules.money(value, message);
    }

    protected static String enumValue(String value, String fallback, String... allowed) {
        String normalized =
                value == null || value.isBlank()
                        ? fallback
                        : value.trim().toUpperCase(java.util.Locale.ROOT).replace(' ', '_');
        for (String candidate : allowed) if (candidate.equals(normalized)) return normalized;
        throw new IllegalArgumentException("El valor seleccionado no es válido.");
    }
}
