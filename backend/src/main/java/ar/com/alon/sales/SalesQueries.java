package ar.com.alon.sales;

import ar.com.alon.security.RequestIdentity;
import ar.com.alon.shared.persistence.ReadPage;
import java.sql.Date;
import java.time.*;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SalesQueries {
    private final JdbcTemplate jdbc;
    private final RequestIdentity identity;
    private final Clock clock;
    private final ReadPage pages;

    public SalesQueries(JdbcTemplate jdbc, RequestIdentity identity, Clock clock) {
        this.jdbc = jdbc;
        this.identity = identity;
        this.clock = clock;
        this.pages = new ReadPage(jdbc);
    }

    public List<Map<String, Object>> sales(LocalDate date, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(200, Math.max(1, size));
        LocalDate salesDate = date == null ? LocalDate.now(clock) : date;
        return jdbc.queryForList(
                """
                SELECT v.id, v.numero, v.fecha, DATE_FORMAT(v.fecha_hora, '%Y-%m-%d %H:%i:%s') AS fecha_hora, c.nombre AS cliente, v.tipo_venta, v.medio_pago,
                       v.tipo_comprobante, v.total, v.estado
                FROM ventas v JOIN clientes c ON c.id = v.cliente_id
                WHERE v.comercio_id = ? AND v.fecha = ? ORDER BY v.fecha_hora DESC, v.id DESC LIMIT ? OFFSET ?
                """,
                identity.storeId(), Date.valueOf(salesDate), safeSize, (long) safePage * safeSize);
    }
}
