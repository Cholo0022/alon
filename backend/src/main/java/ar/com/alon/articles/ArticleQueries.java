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

    public List<Map<String, Object>> grouped(
            boolean inactive, String category, String query, int page, int size) {
        if (category.length() > 90
                || query.length() > 160
                || page < 0
                || page > 1000000
                || size < 1
                || size > 200) throw new IllegalArgumentException("La búsqueda no es válida.");
        String base =
                "WITH products AS (SELECT a.*,r.nombre AS rubro,"
                        + ApparelGroups.keySql("a")
                        + " AS product_key FROM articulos a JOIN rubros r ON r.id=a.rubro_id AND r.comercio_id=a.comercio_id WHERE a.comercio_id=? AND a.estado=?)";
        String matching =
                ", matched AS (SELECT product_key,MIN(id) id,SUM(stock) stock_total,COUNT(*) variant_count FROM products GROUP BY product_key HAVING (?='' OR MAX(LOCATE(?,CONCAT(codigo,' ',nombre,' ',modelo,' ',marca,' ',talle,' ',color,' ',rubro))>0)>0) AND (?='' OR MAX(rubro=?)>0)) ";
        pages.publishTotal(
                base + matching + "SELECT id FROM matched",
                identity.storeId(),
                inactive ? "INACTIVO" : "ACTIVO",
                query,
                query,
                category,
                category);
        var result =
                jdbc.queryForList(
                        base
                                + matching
                                + "SELECT p.*,m.stock_total,m.variant_count FROM matched m JOIN products p ON p.id=m.id ORDER BY p.nombre,p.id LIMIT ? OFFSET ?",
                        identity.storeId(),
                        inactive ? "INACTIVO" : "ACTIVO",
                        query,
                        query,
                        category,
                        category,
                        size,
                        (long) page * size);
        if (result.isEmpty()) return result;
        var args = new java.util.ArrayList<Object>();
        args.add(identity.storeId());
        args.add(inactive ? "INACTIVO" : "ACTIVO");
        result.forEach(row -> args.add(row.get("product_key")));
        var variants =
                jdbc.queryForList(
                        base
                                + " SELECT * FROM products WHERE product_key IN ("
                                + String.join(
                                        ",", java.util.Collections.nCopies(result.size(), "?"))
                                + ") ORDER BY id",
                        args.toArray());
        ArticlePhotoQueries.attach(jdbc, identity.storeId(), variants);
        var byGroup = new java.util.HashMap<String, List<Map<String, Object>>>();
        for (var row : variants)
            byGroup.computeIfAbsent(
                            String.valueOf(row.get("product_key")),
                            ignored -> new java.util.ArrayList<>())
                    .add(row);
        for (var row : result) {
            var choices = byGroup.get(String.valueOf(row.get("product_key")));
            row.put("photos", choices.getFirst().get("photos"));
            if (!"ARTICULO".equals(row.get("tipo_producto"))) {
                row.put("nombre_publico", ApparelGroups.name(row));
                row.put("variants", choices);
            }
            hideCosts(row);
            choices.forEach(this::hideCosts);
        }
        return result;
    }

    public Map<String, Object> group(long id) {
        var snapshot = ApparelGroups.load(jdbc, identity.storeId(), id, false);
        var base = new java.util.LinkedHashMap<String, Object>(snapshot.base());
        base.put("nombre_publico", ApparelGroups.name(base));
        base.put(
                "photos",
                snapshot.rows().stream()
                        .filter(row -> ((Number) row.get("id")).longValue() == id)
                        .findFirst()
                        .orElseThrow()
                        .get("photos"));
        hideCosts(base);
        snapshot.rows().forEach(this::hideCosts);
        return Map.of("product", base, "variants", snapshot.rows(), "token", snapshot.token());
    }

    private void hideCosts(Map<String, Object> row) {
        if (!identity.permits("costos") && !identity.permits("compras"))
            for (var key :
                    List.of("costo", "flete_unitario", "flete_porcentaje", "margen_porcentaje"))
                row.remove(key);
    }

    public List<Map<String, Object>> variants(long id) {
        return pages.query(
                "a.codigo,a.talle,a.color",
                """
            SELECT a.id,a.codigo,a.talle,a.color,a.stock,a.estado FROM articulos a JOIN articulos base
            ON base.comercio_id=a.comercio_id AND base.tipo_producto=a.tipo_producto AND base.modelo=a.modelo
            AND base.marca=a.marca AND base.sistema_talle=a.sistema_talle AND base.publico=a.publico
            AND base.temporada=a.temporada AND base.material=a.material
            WHERE base.comercio_id=? AND base.id=? AND base.tipo_producto IN ('INDUMENTARIA','CALZADO')
            ORDER BY a.color,a.talle,a.id
            """,
                identity.storeId(),
                id);
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
