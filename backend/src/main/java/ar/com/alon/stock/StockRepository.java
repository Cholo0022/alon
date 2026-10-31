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

    /**
     * Recibe y conserva las dependencias necesarias para los bloqueos de artículos y el registro de
     * movimientos.
     */
    public StockRepository(JdbcTemplate jdbc, RequestIdentity identity, Clock clock) {
        this.jdbc = jdbc;
        this.identity = identity;
        this.clock = clock;
    }

    /**
     * Obtiene y bloquea un artículo del comercio para consultar o modificar su stock de forma
     * consistente.
     */
    public Map<String, Object> lockArticle(long id) {
        return jdbc.queryForMap(
                "SELECT id,nombre,stock,costo,flete_unitario,flete_porcentaje,margen_porcentaje,redondeo_precio,precio_venta FROM articulos WHERE id=? AND comercio_id=? AND estado='ACTIVO' FOR UPDATE",
                id,
                identity.storeId());
    }

    /**
     * Registra una variación de stock con tipo, cantidad, referencia, descripción, fecha y usuario
     * responsable.
     */
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
