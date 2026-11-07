// Consulta informes del negocio del comercio actual para mostrarlos en pantalla.
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

    /**
     * Recibe y conserva las dependencias necesarias para las consultas agregadas de los informes.
     */
    public ReportQueries(JdbcTemplate jdbc, RequestIdentity identity, Clock clock) {
        this.jdbc = jdbc;
        this.identity = identity;
        this.clock = clock;
        this.pages = new ReadPage(jdbc);
    }

    /** Consulta ventas y ganancia bruta agrupadas por día, con descuentos y ticket promedio. */
    public List<Map<String, Object>> dailySales() {
        return dailySales(null, null);
    }

    public List<Map<String, Object>> dailySales(String from, String to) {
        var period = period(from, to);
        return pages.query(
                "fecha",
                "SELECT * FROM vw_ventas_diarias WHERE comercio_id = ? AND fecha >= ? AND fecha < ? ORDER BY fecha",
                identity.storeId(),
                period.start(),
                period.end());
    }

    /** Consulta totales de ventas, costo y ganancia bruta agrupados por mes. */
    public List<Map<String, Object>> monthlySales() {
        return monthlySales(null, null);
    }

    public List<Map<String, Object>> monthlySales(String from, String to) {
        var period = period(from, to);
        return pages.query(
                "mes",
                "SELECT * FROM vw_ventas_mensuales WHERE comercio_id = ? AND mes >= ? AND mes < ? ORDER BY mes",
                identity.storeId(),
                period.start().toString().substring(0, 7),
                period.end().toString().substring(0, 7));
    }

    /** Consulta importes y operaciones de ventas agrupados por medio de pago. */
    public List<Map<String, Object>> paymentMethods() {
        return paymentMethods(null, null);
    }

    public List<Map<String, Object>> paymentMethods(String from, String to) {
        var period = period(from, to);
        return pages.query(
                "medio_pago",
                """
                SELECT medio_pago, COUNT(*) operaciones, SUM(total) importe
                FROM ventas WHERE comercio_id = ? AND estado='CONFIRMADA' AND fecha >= ? AND fecha < ? GROUP BY medio_pago ORDER BY importe DESC
                """,
                identity.storeId(),
                period.start(),
                period.end());
    }

    /** Consulta ranking de clientes por consumo junto con deuda y límite de crédito. */
    public List<Map<String, Object>> topCustomers() {
        return topCustomers(null, null);
    }

    public List<Map<String, Object>> topCustomers(String from, String to) {
        var period = period(from, to);
        return jdbc.queryForList(
                """
                SELECT c.codigo, c.nombre, COUNT(v.id) compras, SUM(v.total) total_consumido,
                c.saldo_cuenta_corriente saldo_pendiente, c.limite_credito
                FROM clientes c JOIN ventas v ON v.cliente_id=c.id AND v.comercio_id=c.comercio_id
                WHERE c.comercio_id = ? AND v.fecha >= ? AND v.fecha < ? AND v.estado='CONFIRMADA'
                GROUP BY c.id, c.codigo, c.nombre, c.saldo_cuenta_corriente, c.limite_credito
                ORDER BY total_consumido DESC LIMIT 10
                """,
                identity.storeId(),
                period.start(),
                period.end());
    }

    /** Consulta artículos más vendidos con unidades, importe y margen bruto. */
    public List<Map<String, Object>> topArticles() {
        return topArticles(null, null);
    }

    public List<Map<String, Object>> topArticles(String from, String to) {
        var period = period(from, to);
        return jdbc.queryForList(
                """
                SELECT a.codigo, a.nombre, r.nombre rubro, SUM(d.cantidad) unidades_vendidas,
                SUM(d.total_linea) importe_vendido, SUM(d.cantidad*d.costo_unitario) costo_vendido,
                SUM(d.total_linea-d.cantidad*d.costo_unitario) margen_bruto
                FROM venta_detalle d JOIN ventas v ON v.id=d.venta_id
                JOIN articulos a ON a.id=d.articulo_id JOIN rubros r ON r.id=a.rubro_id
                WHERE v.comercio_id = ? AND v.fecha >= ? AND v.fecha < ? AND v.estado='CONFIRMADA'
                GROUP BY a.id, a.codigo, a.nombre, r.nombre ORDER BY unidades_vendidas DESC LIMIT 10
                """,
                identity.storeId(),
                period.start(),
                period.end());
    }

    /** Consulta compras agrupadas por artículo y proveedor con unidades e importes. */
    public List<Map<String, Object>> purchasesByArticle() {
        return purchasesByArticle(null, null);
    }

    public List<Map<String, Object>> purchasesByArticle(String from, String to) {
        var period = period(from, to);
        return pages.query(
                "a.codigo,a.nombre,p.nombre",
                """
                SELECT a.codigo, a.nombre articulo, p.codigo proveedor_codigo, p.nombre proveedor,
                COUNT(DISTINCT c.id) compras, SUM(d.cantidad) unidades_compradas, SUM(d.total_linea) importe
                FROM compra_detalle d JOIN compras c ON c.id=d.compra_id
                JOIN articulos a ON a.id=d.articulo_id JOIN proveedores p ON p.id=c.proveedor_id
                WHERE c.comercio_id = ? AND c.fecha >= ? AND c.fecha < ?
                GROUP BY a.codigo, a.nombre, p.codigo, p.nombre ORDER BY importe DESC
                """,
                identity.storeId(),
                period.start(),
                period.end());
    }

    /** Consulta proveedores del comercio con sus saldos pendientes. */
    public List<Map<String, Object>> supplierBalances() {
        return pages.query(
                "codigo,nombre",
                """
                SELECT codigo, nombre, especialidad, saldo_pendiente, estado
                FROM vw_saldos_proveedores WHERE comercio_id = ? ORDER BY saldo_pendiente DESC
                """,
                identity.storeId());
    }

    /** Consulta gastos del comercio agrupados por día y categoría. */
    public List<Map<String, Object>> expenses() {
        return expenses(null, null);
    }

    public List<Map<String, Object>> expenses(String from, String to) {
        var period = period(from, to);
        return pages.query(
                "fecha,categoria",
                "SELECT * FROM vw_gastos_diarios WHERE comercio_id = ? AND fecha >= ? AND fecha < ? ORDER BY fecha, categoria",
                identity.storeId(),
                period.start(),
                period.end());
    }

    /** Consulta artículos cuyo stock está por debajo del mínimo configurado. */
    public List<Map<String, Object>> lowStock() {
        return pages.query(
                "codigo,nombre",
                "SELECT * FROM vw_stock_bajo WHERE comercio_id = ? ORDER BY stock",
                identity.storeId());
    }

    record Period(LocalDate start, LocalDate end) {}

    Period period(String from, String to) {
        try {
            YearMonth current =
                    YearMonth.now(clock.withZone(ZoneId.of("America/Argentina/Buenos_Aires")));
            YearMonth start = from == null ? current : YearMonth.parse(from);
            YearMonth end = to == null ? start : YearMonth.parse(to);
            if (start.isAfter(end))
                throw new IllegalArgumentException(
                        "El mes inicial no puede ser posterior al final.");
            return new Period(start.atDay(1), end.plusMonths(1).atDay(1));
        } catch (DateTimeException e) {
            throw new IllegalArgumentException("Seleccioná meses válidos con formato AAAA-MM.");
        }
    }
}
