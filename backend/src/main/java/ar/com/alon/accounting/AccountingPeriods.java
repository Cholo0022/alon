package ar.com.alon.accounting;

import ar.com.alon.shared.web.BusinessConflict;
import java.sql.Date;
import java.time.*;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Comparte el bloqueo del comercio con comprobantes y cierres; evita altas concurrentes después de
 * cerrar.
 */
public final class AccountingPeriods {
    private AccountingPeriods() {}

    public static void check(JdbcTemplate jdbc, long store, LocalDate date) {
        jdbc.queryForObject("SELECT id FROM comercios WHERE id=? FOR UPDATE", Long.class, store);
        var rows =
                jdbc.queryForList(
                        "SELECT c.control_periodos,p.estado FROM comercios c LEFT JOIN periodos_contables p ON p.comercio_id=c.id AND p.mes=? WHERE c.id=?",
                        Date.valueOf(date.withDayOfMonth(1)),
                        store);
        if (rows.isEmpty()) return;
        var row = rows.getFirst();
        String state = String.valueOf(row.get("estado"));
        if (state.equals("CERRADO"))
            throw new BusinessConflict(
                    "El período "
                            + YearMonth.from(date)
                            + " está cerrado. El gerente debe reabrirlo antes de modificar o cargar comprobantes.");
        Object enabled = row.get("control_periodos");
        if ((Boolean.TRUE.equals(enabled) || enabled instanceof Number n && n.intValue() != 0)
                && !state.equals("ABIERTO"))
            throw new BusinessConflict(
                    "El período "
                            + YearMonth.from(date)
                            + " no está abierto. Abrilo desde Contabilidad antes de cargar comprobantes.");
    }

    public static void checkLinked(JdbcTemplate jdbc, long store, String type, long id) {
        jdbc.queryForObject("SELECT id FROM comercios WHERE id=? FOR UPDATE", Long.class, store);
        var periods =
                jdbc.queryForList(
                        "SELECT DISTINCT periodo FROM comprobantes_contables WHERE comercio_id=? AND clase=? AND operacion_id=?",
                        store,
                        type,
                        id);
        for (var period : periods)
            check(jdbc, store, Date.valueOf(period.get("periodo").toString()).toLocalDate());
    }

    public static LocalDate month(String value) {
        try {
            return YearMonth.parse(value).atDay(1);
        } catch (Exception e) {
            throw new IllegalArgumentException("Elegí un mes con formato aaaa-mm.");
        }
    }
}
