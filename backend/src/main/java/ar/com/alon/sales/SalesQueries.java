// Consulta ventas del comercio actual para mostrarlos en pantalla.
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

    /** Recibe y conserva las dependencias necesarias para el historial de ventas. */
    public SalesQueries(JdbcTemplate jdbc, RequestIdentity identity, Clock clock) {
        this.jdbc = jdbc;
        this.identity = identity;
        this.clock = clock;
        this.pages = new ReadPage(jdbc);
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public Map<String, Object> receipt(long id) {
        var sale =
                jdbc.queryForMap(
                        """
            SELECT v.id,v.numero,v.fecha,v.tipo_venta,v.medio_pago,v.subtotal,v.descuento,v.total,v.estado,
                   c.nombre AS cliente,c.codigo AS cliente_codigo,c.telefono AS cliente_telefono,
                   b.nombre AS negocio,b.slogan,b.telefono_tienda
            FROM ventas v JOIN clientes c ON c.id=v.cliente_id AND c.comercio_id=v.comercio_id
            JOIN comercios b ON b.id=v.comercio_id WHERE v.id=? AND v.comercio_id=?
            """,
                        id,
                        identity.storeId());
        sale.put(
                "lineas",
                jdbc.queryForList(
                        """
            SELECT d.renglon,a.codigo,a.nombre,d.cantidad,d.precio_unitario,d.descuento_pct,d.total_linea,a.unidad
            FROM venta_detalle d JOIN ventas v ON v.id=d.venta_id
            JOIN articulos a ON a.id=d.articulo_id AND a.comercio_id=v.comercio_id
            WHERE d.venta_id=? AND v.comercio_id=? ORDER BY d.renglon
            """,
                        id,
                        identity.storeId()));
        return sale;
    }

    /** Consulta ventas del comercio con cliente, importes y estado. */
    public List<Map<String, Object>> sales(LocalDate date, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(200, Math.max(1, size));
        LocalDate salesDate = date == null ? LocalDate.now(clock) : date;
        pages.publishTotal(
                "SELECT id FROM ventas WHERE comercio_id=? AND fecha=?",
                identity.storeId(),
                Date.valueOf(salesDate));
        return jdbc.queryForList(
                """
                SELECT v.id, v.numero, v.fecha, DATE_FORMAT(v.fecha_hora, '%Y-%m-%d %H:%i:%s') AS fecha_hora, c.nombre AS cliente, v.tipo_venta, v.medio_pago,
                       v.tipo_comprobante, v.total, v.estado, v.origen, v.pedido_tienda_id
                FROM ventas v JOIN clientes c ON c.id = v.cliente_id
                WHERE v.comercio_id = ? AND v.fecha = ? ORDER BY v.fecha_hora DESC, v.id DESC LIMIT ? OFFSET ?
                """,
                identity.storeId(), Date.valueOf(salesDate), safeSize, (long) safePage * safeSize);
    }
}
