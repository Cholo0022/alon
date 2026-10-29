package ar.com.alon.reporting;

import ar.com.alon.security.RequestIdentity;
import ar.com.alon.shared.persistence.ReadPage;
import java.time.*;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ReportQueries {
    private final JdbcTemplate jdbc;
    private final RequestIdentity identity;
    private final Clock clock;
    private final ReadPage pages;

    public ReportQueries(JdbcTemplate jdbc, RequestIdentity identity, Clock clock) {
        this.jdbc = jdbc;
        this.identity = identity;
        this.clock = clock;
        this.pages = new ReadPage(jdbc);
    }

    public List<Map<String, Object>> dailySales() {
        return pages.query(
                "fecha",
                "SELECT * FROM vw_ventas_diarias WHERE comercio_id = ? ORDER BY fecha",
                identity.storeId());
    }

    public List<Map<String, Object>> monthlySales() {
        return pages.query(
                "mes",
                "SELECT * FROM vw_ventas_mensuales WHERE comercio_id = ? ORDER BY mes",
                identity.storeId());
    }

    public List<Map<String, Object>> paymentMethods() {
        return pages.query(
                "medio_pago",
                """
                SELECT medio_pago, COUNT(*) operaciones, SUM(total) importe
                FROM ventas WHERE comercio_id = ? AND estado='CONFIRMADA' GROUP BY medio_pago ORDER BY importe DESC
                """,
                identity.storeId());
    }

    public List<Map<String, Object>> topCustomers() {
        return jdbc.queryForList(
                """
                SELECT codigo, nombre, compras, total_consumido, saldo_pendiente, limite_credito
                FROM vw_ranking_clientes WHERE comercio_id = ? ORDER BY total_consumido DESC LIMIT 10
                """,
                identity.storeId());
    }

    public List<Map<String, Object>> topArticles() {
        return jdbc.queryForList(
                """
                SELECT codigo, nombre, rubro, unidades_vendidas, importe_vendido, costo_vendido, margen_bruto
                FROM vw_articulos_mas_vendidos WHERE comercio_id = ? ORDER BY unidades_vendidas DESC LIMIT 10
                """,
                identity.storeId());
    }

    public List<Map<String, Object>> purchasesByArticle() {
        return pages.query(
                "codigo,articulo,proveedor",
                """
                SELECT codigo, articulo, proveedor_codigo, proveedor, compras, unidades_compradas, importe
                FROM vw_compras_por_articulo WHERE comercio_id = ? ORDER BY importe DESC
                """,
                identity.storeId());
    }

    public List<Map<String, Object>> supplierBalances() {
        return pages.query(
                "codigo,nombre",
                """
                SELECT codigo, nombre, especialidad, saldo_pendiente, estado
                FROM vw_saldos_proveedores WHERE comercio_id = ? ORDER BY saldo_pendiente DESC
                """,
                identity.storeId());
    }

    public List<Map<String, Object>> expenses() {
        return pages.query(
                "fecha,categoria",
                "SELECT * FROM vw_gastos_diarios WHERE comercio_id = ? ORDER BY fecha, categoria",
                identity.storeId());
    }

    public List<Map<String, Object>> lowStock() {
        return pages.query(
                "codigo,nombre",
                "SELECT * FROM vw_stock_bajo WHERE comercio_id = ? ORDER BY stock",
                identity.storeId());
    }
}
