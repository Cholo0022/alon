package ar.com.alon.purchases;

import ar.com.alon.security.RequestIdentity;
import ar.com.alon.shared.persistence.ReadPage;
import java.time.*;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PurchaseQueries {
    private final JdbcTemplate jdbc;
    private final RequestIdentity identity;
    private final Clock clock;
    private final ReadPage pages;

    public PurchaseQueries(JdbcTemplate jdbc, RequestIdentity identity, Clock clock) {
        this.jdbc = jdbc;
        this.identity = identity;
        this.clock = clock;
        this.pages = new ReadPage(jdbc);
    }

    public List<Map<String, Object>> purchases() {
        return pages.query(
                "c.numero_documento,p.nombre,p.codigo",
                """
                SELECT c.id, c.proveedor_id, c.numero_documento, c.tipo_documento, c.fecha, p.codigo AS proveedor_codigo,
                       p.nombre AS proveedor, COUNT(d.articulo_id) AS articulos,
                       c.subtotal, c.gastos_adicionales, c.total, c.pagado, c.saldo, c.estado
                FROM compras c JOIN proveedores p ON p.id = c.proveedor_id
                LEFT JOIN compra_detalle d ON d.compra_id = c.id
                WHERE c.comercio_id = ?
                GROUP BY c.id, c.numero_documento, c.tipo_documento, c.fecha, p.codigo, p.nombre,
                         c.subtotal, c.gastos_adicionales, c.total, c.pagado, c.saldo, c.estado
                ORDER BY c.fecha, c.id
                """,
                identity.storeId());
    }

    public Map<String, Object> purchaseDetails(long id) {
        Map<String, Object> purchase =
                jdbc.queryForMap(
                        """
                SELECT c.id,c.proveedor_id,c.numero_documento,c.tipo_documento,c.fecha,c.subtotal,c.gastos_adicionales,
                       c.total,c.pagado,c.saldo,c.estado,p.nombre AS proveedor
                FROM compras c JOIN proveedores p ON p.id=c.proveedor_id
                WHERE c.id=? AND c.comercio_id=?
                """,
                        id,
                        identity.storeId());
        purchase.put(
                "lineas",
                jdbc.queryForList(
                        """
                SELECT d.renglon,d.articulo_id,a.codigo,a.nombre AS articulo,d.cantidad,d.costo_unitario,d.total_linea
                FROM compra_detalle d JOIN articulos a ON a.id=d.articulo_id
                WHERE d.compra_id=? ORDER BY d.renglon
                """,
                        id));
        return purchase;
    }
}
