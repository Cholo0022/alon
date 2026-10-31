// Añade las fotos a los artículos consultados sin repetir una consulta por cada foto.
package ar.com.alon.articles;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;

public final class ArticlePhotoQueries {
    private ArticlePhotoQueries() {}

    /** Consulta las fotos de toda la página de artículos en una sola operación. */
    public static List<Map<String, Object>> attach(
            JdbcTemplate jdbc, long store, List<Map<String, Object>> articles) {
        if (articles.isEmpty()) return articles;
        var args = new ArrayList<Object>();
        args.add(store);
        articles.forEach(a -> args.add(a.get("id")));
        var photos =
                jdbc.queryForList(
                        "SELECT articulo_id,clave AS photo_key,ilustrativa FROM articulo_imagenes WHERE comercio_id=? AND articulo_id IN ("
                                + String.join(",", Collections.nCopies(articles.size(), "?"))
                                + ") ORDER BY articulo_id,posicion",
                        args.toArray());
        Map<Long, List<Map<String, Object>>> grouped = new HashMap<>();
        for (var photo : photos)
            grouped.computeIfAbsent(
                            ((Number) photo.get("articulo_id")).longValue(),
                            ignored -> new ArrayList<>())
                    .add(
                            Map.of(
                                    "key",
                                    photo.get("photo_key"),
                                    "illustrative",
                                    photo.get("ilustrativa")));
        articles.forEach(
                a ->
                        a.put(
                                "photos",
                                grouped.getOrDefault(
                                        ((Number) a.get("id")).longValue(), List.of())));
        return articles;
    }
}
