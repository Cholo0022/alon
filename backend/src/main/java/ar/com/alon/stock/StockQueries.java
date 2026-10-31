// Consulta existencias y movimientos de stock del comercio actual para mostrarlos en pantalla.
package ar.com.alon.stock;

import ar.com.alon.security.RequestIdentity;
import ar.com.alon.shared.persistence.ReadPage;
import java.time.*;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class StockQueries {
    private final JdbcTemplate jdbc;
    private final RequestIdentity identity;
    private final Clock clock;
    private final ReadPage pages;

    /** Recibe y conserva las dependencias necesarias para el historial de movimientos de stock. */
    public StockQueries(JdbcTemplate jdbc, RequestIdentity identity, Clock clock) {
        this.jdbc = jdbc;
        this.identity = identity;
        this.clock = clock;
        this.pages = new ReadPage(jdbc);
    }

    /** Consulta movimientos de stock del comercio con artículo, referencia y autor. */
    public List<Map<String, Object>> stockMovements() {
        return pages.query(
                "a.codigo,a.nombre,m.referencia",
                """
                SELECT m.id, a.codigo, a.nombre AS articulo, m.tipo, m.fecha_hora, m.cantidad, m.referencia, m.descripcion
                FROM movimientos_stock m JOIN articulos a ON a.id = m.articulo_id
                WHERE m.comercio_id = ? ORDER BY m.fecha_hora DESC, m.id DESC
                """,
                identity.storeId());
    }
}
