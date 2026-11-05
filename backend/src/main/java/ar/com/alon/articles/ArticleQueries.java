// Consulta artículos y fotos del comercio actual para mostrarlos en pantalla.
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

    /**
     * Recibe y conserva las dependencias necesarias para las consultas de catálogo con permisos y
     * paginación.
     */
    public ArticleQueries(JdbcTemplate jdbc, RequestIdentity identity, Clock clock) {
        this.jdbc = jdbc;
        this.identity = identity;
        this.clock = clock;
        this.pages = new ReadPage(jdbc);
    }

    /**
     * Consulta artículos del comercio, con búsqueda, paginación y datos visibles según los
     * permisos.
     */
    public List<Map<String, Object>> articles(String role, boolean inactive) {
        return articles(role, inactive, "");
    }

    /**
     * Consulta artículos del comercio, con búsqueda, paginación y datos visibles según los
     * permisos.
     */
    public List<Map<String, Object>> articles(String role, boolean inactive, String category) {
        return articles(role, inactive, category, "");
    }

    public List<Map<String, Object>> articles(
            String role, boolean inactive, String category, String kind) {
        return articles(role, inactive, category, kind, "");
    }

    public List<Map<String, Object>> articles(
            String role, boolean inactive, String category, String kind, String code) {
        if (code.length() > 160)
            throw new IllegalArgumentException("El código es demasiado largo.");
        if (!java.util.Set.of("", "ARTICULO", "VESTIMENTA").contains(kind))
            throw new IllegalArgumentException("El tipo de producto no es válido.");
        String kindSql =
                kind.equals("ARTICULO")
                        ? " AND a.tipo_producto='ARTICULO' "
                        : kind.equals("VESTIMENTA")
                                ? " AND a.tipo_producto IN ('INDUMENTARIA','CALZADO') "
                                : " ";
        if (!code.isEmpty()) kindSql += " AND a.codigo=? ";
        if (category.length() > 90)
            throw new IllegalArgumentException("El rubro solicitado no es válido.");
        if (!identity.permits("costos") && !identity.permits("compras"))
            return ArticlePhotoQueries.attach(
                    jdbc,
                    identity.storeId(),
                    pages.query(
                            "a.codigo,a.nombre,r.nombre",
                            "SELECT a.id,a.tipo_producto,a.modelo,a.marca,a.talle,a.sistema_talle,a.color,a.material,a.publico,a.temporada,a.descripcion,a.codigo,a.nombre,a.stock,a.unidad,a.precio_venta,a.imagen_clave,a.imagen_ilustrativa,r.nombre AS rubro FROM articulos a JOIN rubros r ON r.id=a.rubro_id WHERE a.comercio_id=? AND a.estado=? AND (?='' OR r.nombre=?) "
                                    + kindSql
                                    + " ORDER BY a.nombre,a.id",
                            parameters(inactive, category, code)));
        return ArticlePhotoQueries.attach(
                jdbc,
                identity.storeId(),
                pages.query(
                        "a.codigo,a.nombre,r.nombre",
                        """
                SELECT a.id, a.tipo_producto,a.modelo,a.marca,a.talle,a.sistema_talle,a.color,a.material,a.publico,a.temporada,a.descripcion, a.codigo, a.nombre, a.rubro_id, a.proveedor_principal_id, r.nombre AS rubro, a.stock, a.stock_minimo,
                       a.unidad, a.costo, a.flete_unitario, a.flete_porcentaje, a.margen_porcentaje, a.redondeo_precio, a.cargar_web,
                       a.precio_venta, a.imagen_clave, a.imagen_ilustrativa, p.codigo AS proveedor, p.nombre AS proveedor_nombre, p.estado AS proveedor_estado
                FROM articulos a
                JOIN rubros r ON r.id = a.rubro_id
                LEFT JOIN proveedores p ON p.id = a.proveedor_principal_id AND p.comercio_id=a.comercio_id
                WHERE a.comercio_id = ? AND a.estado=? AND (?='' OR r.nombre=?) %s ORDER BY a.nombre, a.id
                """
                                .formatted(kindSql),
                        parameters(inactive, category, code)));
    }

    private Object[] parameters(boolean inactive, String category, String code) {
        return code.isEmpty()
                ? new Object[] {
                    identity.storeId(), inactive ? "INACTIVO" : "ACTIVO", category, category
                }
                : new Object[] {
                    identity.storeId(), inactive ? "INACTIVO" : "ACTIVO", category, category, code
                };
    }
}
