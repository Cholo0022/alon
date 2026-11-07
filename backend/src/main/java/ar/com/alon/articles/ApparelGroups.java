package ar.com.alon.articles;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;

/** Identidad de la prenda y fotografía de sus talles para evitar sobrescribir cambios recientes. */
public final class ApparelGroups {
    private ApparelGroups() {}

    public static String keySql(String a) {
        return "CASE WHEN "
                + a
                + ".tipo_producto IN ('INDUMENTARIA','CALZADO') THEN CONCAT('v:',SHA2(CAST(JSON_ARRAY("
                + a
                + ".tipo_producto,LOWER(TRIM("
                + a
                + ".modelo)),LOWER(TRIM("
                + a
                + ".marca)),LOWER(TRIM("
                + a
                + ".sistema_talle)),LOWER(TRIM("
                + a
                + ".material)),LOWER(TRIM("
                + a
                + ".publico)),LOWER(TRIM("
                + a
                + ".temporada))) AS CHAR),256)) ELSE CONCAT('a:',"
                + a
                + ".id) END";
    }

    public static String name(Map<String, Object> row) {
        String name = String.valueOf(row.get("nombre")),
                suffix = " · " + row.get("talle") + " · " + row.get("color");
        return name.endsWith(suffix) ? name.substring(0, name.length() - suffix.length()) : name;
    }

    public record Snapshot(
            Map<String, Object> base, List<Map<String, Object>> rows, String token) {}

    public static Snapshot load(JdbcTemplate jdbc, long store, long id, boolean lock) {
        var base =
                jdbc.queryForMap(
                        "SELECT * FROM articulos WHERE comercio_id=? AND id=?"
                                + (lock ? " FOR UPDATE" : ""),
                        store,
                        id);
        if (!Set.of("INDUMENTARIA", "CALZADO").contains(base.get("tipo_producto")))
            throw new IllegalArgumentException("Este artículo no tiene talles.");
        var rows =
                jdbc.queryForList(
                        "SELECT a.* FROM articulos a WHERE a.comercio_id=? AND a.tipo_producto=? AND a.modelo=? AND a.marca=? AND a.sistema_talle=? AND a.material=? AND a.publico=? AND a.temporada=? AND a.estado=? ORDER BY a.id"
                                + (lock ? " FOR UPDATE" : ""),
                        store,
                        base.get("tipo_producto"),
                        base.get("modelo"),
                        base.get("marca"),
                        base.get("sistema_talle"),
                        base.get("material"),
                        base.get("publico"),
                        base.get("temporada"),
                        base.get("estado"));
        ArticlePhotoQueries.attach(jdbc, store, rows);
        var stable = new ArrayList<Map<String, String>>();
        for (var row : rows) {
            var values = new TreeMap<String, String>();
            for (var key :
                    List.of(
                            "id",
                            "codigo",
                            "nombre",
                            "tipo_producto",
                            "modelo",
                            "marca",
                            "talle",
                            "sistema_talle",
                            "color",
                            "material",
                            "publico",
                            "temporada",
                            "descripcion",
                            "costo",
                            "precio_venta",
                            "flete_porcentaje",
                            "margen_porcentaje",
                            "redondeo_precio",
                            "cargar_web",
                            "stock",
                            "stock_minimo",
                            "estado",
                            "imagen_clave",
                            "imagen_ilustrativa",
                            "photos")) values.put(key, String.valueOf(row.get(key)));
            stable.add(values);
        }
        try {
            String serialized =
                    tools.jackson.databind.json.JsonMapper.builder()
                            .build()
                            .writeValueAsString(stable);
            String token =
                    HexFormat.of()
                            .formatHex(
                                    java.security.MessageDigest.getInstance("SHA-256")
                                            .digest(
                                                    serialized.getBytes(
                                                            java.nio.charset.StandardCharsets
                                                                    .UTF_8)));
            return new Snapshot(base, rows, token);
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
