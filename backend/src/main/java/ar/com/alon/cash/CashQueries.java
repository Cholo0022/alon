// Consulta caja y movimientos de dinero del comercio actual para mostrarlos en pantalla.
package ar.com.alon.cash;

import ar.com.alon.security.RequestIdentity;
import ar.com.alon.shared.persistence.ReadPage;
import java.sql.Date;
import java.time.*;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CashQueries {
    private final JdbcTemplate jdbc;
    private final RequestIdentity identity;
    private final Clock clock;
    private final ReadPage pages;

    public CashQueries(JdbcTemplate jdbc, RequestIdentity identity, Clock clock) {
        this.jdbc = jdbc;
        this.identity = identity;
        this.clock = clock;
        this.pages = new ReadPage(jdbc);
    }

    public Map<String, Object> cashStatus(LocalDate requestedDate) {
        List<Map<String, Object>> rows =
                jdbc.queryForList(
                        "SELECT estado,numero_sesion,saldo_inicial FROM cajas WHERE comercio_id=? AND fecha=? ORDER BY (estado='ABIERTA') DESC, id DESC LIMIT 1",
                        identity.storeId(),
                        Date.valueOf(requestedDate));
        if (rows.isEmpty()) return Map.of("estado", "SIN_ABRIR", "fecha", requestedDate.toString());
        return Map.of(
                "estado",
                rows.getFirst().get("estado"),
                "fecha",
                requestedDate.toString(),
                "numero_sesion",
                rows.getFirst().get("numero_sesion"),
                "saldo_inicial",
                rows.getFirst().get("saldo_inicial"));
    }

    public List<Map<String, Object>> cash() {
        return pages.query(
                "fecha",
                "SELECT * FROM vw_cierre_caja WHERE comercio_id = ? ORDER BY fecha",
                identity.storeId());
    }
}
