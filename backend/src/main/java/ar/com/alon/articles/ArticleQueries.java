package ar.com.alon.articles;

import ar.com.alon.security.RequestIdentity;
import ar.com.alon.shared.persistence.ReadPage;
import java.time.*;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ArticleQueries {
    private final JdbcTemplate jdbc;
    private final RequestIdentity identity;
    private final Clock clock;
    private final ReadPage pages;

    public ArticleQueries(JdbcTemplate jdbc, RequestIdentity identity, Clock clock) {
        this.jdbc = jdbc;
        this.identity = identity;
        this.clock = clock;
        this.pages = new ReadPage(jdbc);
    }

    public List<Map<String, Object>> articles(String role, boolean inactive) {
        return articles(role, inactive, "");
    }

    public List<Map<String, Object>> articles(String role, boolean inactive, String category) {
        if (category.length() > 90)
            throw new IllegalArgumentException("El rubro solicitado no es válido.");
        if (!identity.permits("costos") && !identity.permits("compras"))
            return pages.query(
                    "a.codigo,a.nombre,r.nombre",
                    "SELECT a.id,a.codigo,a.nombre,a.stock,a.unidad,a.precio_venta,r.nombre AS rubro FROM articulos a JOIN rubros r ON r.id=a.rubro_id WHERE a.comercio_id=? AND a.estado=? AND (?='' OR r.nombre=?) ORDER BY a.nombre,a.id",
                    identity.storeId(),
                    inactive ? "INACTIVO" : "ACTIVO",
                    category,
                    category);
        return pages.query(
                "a.codigo,a.nombre,r.nombre",
                """
                SELECT a.id, a.codigo, a.nombre, a.rubro_id, a.proveedor_principal_id, r.nombre AS rubro, a.stock, a.stock_minimo,
                       a.unidad, a.costo, a.flete_unitario, a.flete_porcentaje, a.margen_porcentaje, a.redondeo_precio,
                       a.precio_venta, p.codigo AS proveedor
                FROM articulos a
                JOIN rubros r ON r.id = a.rubro_id
                JOIN proveedores p ON p.id = a.proveedor_principal_id
                WHERE a.comercio_id = ? AND a.estado=? AND (?='' OR r.nombre=?) ORDER BY a.nombre, a.id
                """,
                identity.storeId(),
                inactive ? "INACTIVO" : "ACTIVO",
                category,
                category);
    }
}
