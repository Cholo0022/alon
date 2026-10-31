// Consulta pedidos y compradores de la tienda web del comercio actual para mostrarlos en pantalla.
package ar.com.alon.storefront;

import ar.com.alon.security.RequestIdentity;
import ar.com.alon.shared.persistence.ReadPage;
import java.time.*;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class StoreOrderQueries {
    private final JdbcTemplate jdbc;
    private final RequestIdentity identity;
    private final Clock clock;
    private final ReadPage pages;

    public StoreOrderQueries(JdbcTemplate jdbc, RequestIdentity identity, Clock clock) {
        this.jdbc = jdbc;
        this.identity = identity;
        this.clock = clock;
        this.pages = new ReadPage(jdbc);
    }

    public List<Map<String, Object>> storeOrders() {
        return pages.query(
                "nombre_cliente,contacto,detalle",
                """
                SELECT id, nombre_cliente, contacto, detalle, total_estimado, estado, creado_en, medio_pago, venta_id
                FROM pedidos_tienda WHERE comercio_id = ? ORDER BY creado_en DESC
                """,
                identity.storeId());
    }

    public long pendingCount() {
        Long count =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM pedidos_tienda WHERE comercio_id=? AND estado='PENDIENTE_CONFIRMACION'",
                        Long.class,
                        identity.storeId());
        return count == null ? 0 : count;
    }
}
