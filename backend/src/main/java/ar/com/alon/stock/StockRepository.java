// Consulta el stock con bloqueo y registra cada movimiento para evitar cambios simultáneos
// incompatibles.
package ar.com.alon.stock;

import ar.com.alon.security.RequestIdentity;
import java.math.*;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class StockRepository {
    private final JdbcTemplate jdbc;
    private final RequestIdentity identity;
    private final Clock clock;

    public StockRepository(JdbcTemplate jdbc, RequestIdentity identity, Clock clock) {
        this.jdbc = jdbc;
        this.identity = identity;
        this.clock = clock;
    }

    public Map<String, Object> lockArticle(long id) {
        return jdbc.queryForMap(
                "SELECT id,nombre,stock,costo,flete_unitario,flete_porcentaje,margen_porcentaje,redondeo_precio,precio_venta FROM articulos WHERE id=? AND comercio_id=? AND estado='ACTIVO' FOR UPDATE",
                id,
                identity.storeId());
    }

    public void addStockMovement(
            long articleId,
            String type,
            BigDecimal quantity,
            String reference,
            String description) {
        jdbc.update(
                "INSERT INTO movimientos_stock (comercio_id,articulo_id,tipo,cantidad,referencia,descripcion,usuario_id,fecha_hora) VALUES (?,?,?,?,?,?,?,?)",
                identity.storeId(),
                articleId,
                type,
                quantity,
                reference,
                description,
                identity.userId(),
                Timestamp.valueOf(java.time.LocalDateTime.now(clock)));
    }
}
