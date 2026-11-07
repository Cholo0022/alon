// Consulta resumen del negocio del comercio actual para mostrarlos en pantalla.
package ar.com.alon.dashboard;

import ar.com.alon.security.RequestIdentity;
import ar.com.alon.shared.persistence.ReadPage;
import java.time.*;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class DashboardQueries {
    private final JdbcTemplate jdbc;
    private final RequestIdentity identity;
    private final Clock clock;
    private final ReadPage pages;

    /**
     * Recibe y conserva las dependencias necesarias para los indicadores agregados del comercio.
     */
    public DashboardQueries(JdbcTemplate jdbc, RequestIdentity identity, Clock clock) {
        this.jdbc = jdbc;
        this.identity = identity;
        this.clock = clock;
        this.pages = new ReadPage(jdbc);
    }

    /** Consulta indicadores de ventas, stock, caja y cuentas del panel inicial del comercio. */
    public Map<String, Object> summary() {
        LocalDate start =
                LocalDate.now(clock.withZone(ZoneId.of("America/Argentina/Buenos_Aires")))
                        .with(
                                java.time.temporal.TemporalAdjusters.previousOrSame(
                                        DayOfWeek.MONDAY));
        LocalDate end = start.plusWeeks(1);
        Map<String, Object> result =
                jdbc.queryForMap(
                        """
                SELECT
                  (SELECT COUNT(*) FROM articulos WHERE comercio_id = ?) AS articulos,
                  (SELECT COUNT(*) FROM clientes WHERE comercio_id = ?) AS clientes,
                  (SELECT COUNT(*) FROM proveedores WHERE comercio_id = ?) AS proveedores,
                  (SELECT COUNT(*) FROM ventas WHERE comercio_id = ? AND estado='CONFIRMADA' AND fecha >= ? AND fecha < ?) AS ventas,
                  (SELECT COUNT(DISTINCT fecha) FROM ventas WHERE comercio_id = ? AND estado='CONFIRMADA' AND fecha >= ? AND fecha < ?) AS dias,
                  (SELECT COALESCE(SUM(total), 0) FROM ventas WHERE comercio_id = ? AND estado='CONFIRMADA' AND fecha >= ? AND fecha < ?) AS facturacion,
                  (SELECT COALESCE(SUM(total - costo_total), 0) FROM ventas WHERE comercio_id = ? AND estado='CONFIRMADA' AND fecha >= ? AND fecha < ?) AS margen_bruto,
                  (SELECT COUNT(*) FROM compras WHERE comercio_id = ? AND fecha >= ? AND fecha < ?) AS compras,
                  (SELECT COUNT(*) FROM cajas WHERE comercio_id = ? AND fecha >= ? AND fecha < ?) AS cierres_caja,
                  (SELECT COALESCE(SUM(importe), 0) FROM gastos WHERE comercio_id = ? AND fecha >= ? AND fecha < ?) AS gastos,
                  (SELECT COUNT(*) FROM vw_stock_bajo WHERE comercio_id = ?) AS stock_bajo
                """,
                        identity.storeId(),
                        identity.storeId(),
                        identity.storeId(),
                        identity.storeId(),
                        start,
                        end,
                        identity.storeId(),
                        start,
                        end,
                        identity.storeId(),
                        start,
                        end,
                        identity.storeId(),
                        start,
                        end,
                        identity.storeId(),
                        start,
                        end,
                        identity.storeId(),
                        start,
                        end,
                        identity.storeId(),
                        start,
                        end,
                        identity.storeId());
        if (!identity.permits("informes")) result.remove("margen_bruto");
        result.put("semana_desde", start.toString());
        result.put("semana_hasta", end.minusDays(1).toString());
        return result;
    }
}
