package ar.com.alon.model;

import java.util.List;
import java.util.Map;
import java.sql.Date;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class DemoDataModel {
    private static final long DEMO_STORE_ID = 1L;
    private final JdbcTemplate jdbc;

    public DemoDataModel(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Map<String, Object> summary() {
        return jdbc.queryForMap("""
                SELECT
                  (SELECT COUNT(*) FROM articulos WHERE comercio_id = ?) AS articulos,
                  (SELECT COUNT(*) FROM clientes WHERE comercio_id = ?) AS clientes,
                  (SELECT COUNT(*) FROM proveedores WHERE comercio_id = ?) AS proveedores,
                  (SELECT COUNT(*) FROM ventas WHERE comercio_id = ?) AS ventas,
                  (SELECT COUNT(DISTINCT fecha) FROM ventas WHERE comercio_id = ?) AS dias,
                  (SELECT COALESCE(SUM(total), 0) FROM ventas WHERE comercio_id = ?) AS facturacion,
                  (SELECT COALESCE(SUM(total - costo_total), 0) FROM ventas WHERE comercio_id = ?) AS margen_bruto,
                  (SELECT COUNT(*) FROM compras WHERE comercio_id = ?) AS compras,
                  (SELECT COUNT(*) FROM cajas WHERE comercio_id = ?) AS cierres_caja,
                  (SELECT COALESCE(SUM(importe), 0) FROM gastos WHERE comercio_id = ?) AS gastos,
                  (SELECT COUNT(*) FROM vw_stock_bajo WHERE comercio_id = ?) AS stock_bajo
                """, DEMO_STORE_ID, DEMO_STORE_ID, DEMO_STORE_ID, DEMO_STORE_ID,
                DEMO_STORE_ID, DEMO_STORE_ID, DEMO_STORE_ID, DEMO_STORE_ID,
                DEMO_STORE_ID, DEMO_STORE_ID, DEMO_STORE_ID);
    }

    public List<Map<String, Object>> articles(String role, boolean inactive) {
        if (role != null && !"DUENO".equals(role)) return jdbc.queryForList("SELECT id,codigo,nombre,stock,unidad,precio_venta FROM articulos WHERE comercio_id=? AND estado=? ORDER BY nombre,id", DEMO_STORE_ID, inactive ? "INACTIVO" : "ACTIVO");
        return jdbc.queryForList("""
                SELECT a.id, a.codigo, a.nombre, a.rubro_id, a.proveedor_principal_id, r.nombre AS rubro, a.stock, a.stock_minimo,
                       a.unidad, a.costo, a.flete_unitario, a.flete_porcentaje, a.margen_porcentaje, a.redondeo_precio,
                       a.precio_venta, p.codigo AS proveedor
                FROM articulos a
                JOIN rubros r ON r.id = a.rubro_id
                JOIN proveedores p ON p.id = a.proveedor_principal_id
                WHERE a.comercio_id = ? AND a.estado=? ORDER BY a.nombre, a.id
                """, DEMO_STORE_ID, inactive ? "INACTIVO" : "ACTIVO");
    }

    public List<Map<String, Object>> stockMovements() {
        return jdbc.queryForList("""
                SELECT m.id, a.codigo, a.nombre AS articulo, m.tipo, m.fecha_hora, m.cantidad, m.referencia, m.descripcion
                FROM movimientos_stock m JOIN articulos a ON a.id = m.articulo_id
                WHERE m.comercio_id = ? ORDER BY m.fecha_hora DESC, m.id DESC LIMIT 200
                """, DEMO_STORE_ID);
    }

    public List<Map<String, Object>> categories() {
        return jdbc.queryForList("SELECT id, codigo, nombre FROM rubros WHERE comercio_id = ? ORDER BY nombre", DEMO_STORE_ID);
    }

    public List<Map<String, Object>> customers() {
        return jdbc.queryForList("""
                SELECT id, codigo, nombre, telefono, email, saldo_cuenta_corriente, limite_credito, estado
                FROM clientes WHERE comercio_id = ? ORDER BY id
                """, DEMO_STORE_ID);
    }

    public List<Map<String, Object>> suppliers() {
        return jdbc.queryForList("""
                SELECT id, codigo, nombre, cuit, especialidad, telefono, email, saldo_actual AS saldo_pendiente, estado
                FROM proveedores WHERE comercio_id = ? ORDER BY id
                """, DEMO_STORE_ID);
    }

    public List<Map<String, Object>> storeOrders() {
        return jdbc.queryForList("""
                SELECT id, nombre_cliente, contacto, detalle, total_estimado, estado, creado_en
                FROM pedidos_tienda WHERE comercio_id = ? ORDER BY creado_en DESC LIMIT 200
                """, DEMO_STORE_ID);
    }

    public Map<String, Object> cashStatus(LocalDate requestedDate) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT estado,numero_sesion,saldo_inicial FROM cajas WHERE comercio_id=? AND fecha=? ORDER BY (estado='ABIERTA') DESC, id DESC LIMIT 1", DEMO_STORE_ID, Date.valueOf(requestedDate));
        if (rows.isEmpty()) return Map.of("estado", "SIN_ABRIR", "fecha", requestedDate.toString());
        return Map.of("estado", rows.getFirst().get("estado"), "fecha", requestedDate.toString(), "numero_sesion", rows.getFirst().get("numero_sesion"), "saldo_inicial", rows.getFirst().get("saldo_inicial"));
    }

    public List<Map<String, Object>> sales(
            LocalDate date, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(200, Math.max(1, size));
        LocalDate salesDate = date == null ? LocalDate.now(ZoneId.of("America/Argentina/Buenos_Aires")) : date;
        return jdbc.queryForList("""
                SELECT v.numero, v.fecha, DATE_FORMAT(v.fecha_hora, '%Y-%m-%d %H:%i:%s') AS fecha_hora, c.nombre AS cliente, v.tipo_venta, v.medio_pago,
                       v.tipo_comprobante, v.total
                FROM ventas v JOIN clientes c ON c.id = v.cliente_id
                WHERE v.comercio_id = ? AND v.fecha = ? ORDER BY v.fecha_hora DESC, v.id DESC LIMIT ? OFFSET ?
                """, DEMO_STORE_ID, Date.valueOf(salesDate), safeSize, safePage * safeSize);
    }

    public List<Map<String, Object>> purchases() {
        return jdbc.queryForList("""
                SELECT c.numero_documento, c.tipo_documento, c.fecha, p.codigo AS proveedor_codigo,
                       p.nombre AS proveedor, COUNT(d.articulo_id) AS articulos,
                       c.subtotal, c.gastos_adicionales, c.total, c.pagado, c.saldo, c.estado
                FROM compras c JOIN proveedores p ON p.id = c.proveedor_id
                LEFT JOIN compra_detalle d ON d.compra_id = c.id
                WHERE c.comercio_id = ?
                GROUP BY c.id, c.numero_documento, c.tipo_documento, c.fecha, p.codigo, p.nombre,
                         c.subtotal, c.gastos_adicionales, c.total, c.pagado, c.saldo, c.estado
                ORDER BY c.fecha, c.id
                """, DEMO_STORE_ID);
    }


    public List<Map<String, Object>> dailySales() {
        return jdbc.queryForList("SELECT * FROM vw_ventas_diarias WHERE comercio_id = ? ORDER BY fecha", DEMO_STORE_ID);
    }


    public List<Map<String, Object>> monthlySales() {
        return jdbc.queryForList("SELECT * FROM vw_ventas_mensuales WHERE comercio_id = ? ORDER BY mes", DEMO_STORE_ID);
    }


    public List<Map<String, Object>> paymentMethods() {
        return jdbc.queryForList("""
                SELECT medio_pago, COUNT(*) operaciones, SUM(total) importe
                FROM ventas WHERE comercio_id = ? GROUP BY medio_pago ORDER BY importe DESC
                """, DEMO_STORE_ID);
    }


    public List<Map<String, Object>> topCustomers() {
        return jdbc.queryForList("""
                SELECT codigo, nombre, compras, total_consumido, saldo_pendiente, limite_credito
                FROM vw_ranking_clientes WHERE comercio_id = ? ORDER BY total_consumido DESC LIMIT 10
                """, DEMO_STORE_ID);
    }


    public List<Map<String, Object>> topArticles() {
        return jdbc.queryForList("""
                SELECT codigo, nombre, rubro, unidades_vendidas, importe_vendido, costo_vendido, margen_bruto
                FROM vw_articulos_mas_vendidos WHERE comercio_id = ? ORDER BY unidades_vendidas DESC LIMIT 10
                """, DEMO_STORE_ID);
    }


    public List<Map<String, Object>> purchasesByArticle() {
        return jdbc.queryForList("""
                SELECT codigo, articulo, proveedor_codigo, proveedor, compras, unidades_compradas, importe
                FROM vw_compras_por_articulo WHERE comercio_id = ? ORDER BY importe DESC
                """, DEMO_STORE_ID);
    }


    public List<Map<String, Object>> supplierBalances() {
        return jdbc.queryForList("""
                SELECT codigo, nombre, especialidad, saldo_pendiente, estado
                FROM vw_saldos_proveedores WHERE comercio_id = ? ORDER BY saldo_pendiente DESC
                """, DEMO_STORE_ID);
    }


    public List<Map<String, Object>> cash() {
        return jdbc.queryForList("SELECT * FROM vw_cierre_caja WHERE comercio_id = ? ORDER BY fecha", DEMO_STORE_ID);
    }


    public List<Map<String, Object>> expenses() {
        return jdbc.queryForList("SELECT * FROM vw_gastos_diarios WHERE comercio_id = ? ORDER BY fecha, categoria", DEMO_STORE_ID);
    }


    public List<Map<String, Object>> lowStock() {
        return jdbc.queryForList("SELECT * FROM vw_stock_bajo WHERE comercio_id = ? ORDER BY stock", DEMO_STORE_ID);
    }
}
