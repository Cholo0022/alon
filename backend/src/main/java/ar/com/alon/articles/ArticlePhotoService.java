package ar.com.alon.articles;

import ar.com.alon.articles.dto.ArticlePhotoInput;
import ar.com.alon.security.RequestIdentity;
import ar.com.alon.shared.audit.AuditLog;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ArticlePhotoService {
    private final JdbcTemplate jdbc;
    private final RequestIdentity identity;
    private final ArticleImageStorage storage;
    private final AuditLog audit;

    public ArticlePhotoService(
            JdbcTemplate jdbc,
            RequestIdentity identity,
            ArticleImageStorage storage,
            AuditLog audit) {
        this.jdbc = jdbc;
        this.identity = identity;
        this.storage = storage;
        this.audit = audit;
    }

    public List<Map<String, Object>> list(long id) {
        identity.require("articulos");
        jdbc.queryForObject(
                "SELECT id FROM articulos WHERE comercio_id=? AND id=?",
                Long.class,
                identity.storeId(),
                id);
        return jdbc
                .queryForList(
                        "SELECT clave AS photo_key,ilustrativa FROM articulo_imagenes WHERE comercio_id=? AND articulo_id=? ORDER BY posicion",
                        identity.storeId(),
                        id)
                .stream()
                .map(p -> Map.of("key", p.get("photo_key"), "illustrative", p.get("ilustrativa")))
                .toList();
    }

    @Transactional
    public List<Map<String, Object>> replace(long id, List<ArticlePhotoInput> input) {
        if (input == null) return List.of(); // Old clients leave existing images untouched.
        identity.require("articulos");
        if (input.size() > 4)
            throw new IllegalArgumentException("Cada artículo admite hasta cuatro fotos.");
        jdbc.queryForObject(
                "SELECT id FROM comercios WHERE id=? FOR UPDATE", Long.class, identity.storeId());
        jdbc.queryForObject(
                "SELECT id FROM articulos WHERE comercio_id=? AND id=? FOR UPDATE",
                Long.class,
                identity.storeId(),
                id);
        var before = list(id);
        Set<String> current = new HashSet<>();
        before.forEach(p -> current.add(String.valueOf(p.get("key"))));
        var after = new ArrayList<Map<String, Object>>();
        var used = new HashSet<String>();
        for (var photo : input) {
            if (photo == null) throw new IllegalArgumentException("La foto no es válida.");
            boolean key = photo.key() != null && !photo.key().isBlank(),
                    data = photo.data() != null && !photo.data().isBlank();
            if (key == data)
                throw new IllegalArgumentException(
                        "Seleccioná una foto existente o un archivo nuevo.");
            if (key && !current.contains(photo.key()))
                throw new IllegalArgumentException(
                        "La foto seleccionada no pertenece al artículo.");
            String file = key ? photo.key() : storage.write(photo.data());
            if (!used.add(file))
                throw new IllegalArgumentException("No repitas la misma foto en la galería.");
            after.add(Map.of("key", file, "illustrative", photo.illustrative()));
        }
        jdbc.update(
                "DELETE FROM articulo_imagenes WHERE comercio_id=? AND articulo_id=?",
                identity.storeId(),
                id);
        for (int i = 0; i < after.size(); i++) {
            var p = after.get(i);
            jdbc.update(
                    "INSERT INTO articulo_imagenes(comercio_id,articulo_id,posicion,clave,ilustrativa) VALUES(?,?,?,?,?)",
                    identity.storeId(),
                    id,
                    i + 1,
                    p.get("key"),
                    p.get("illustrative"));
        }
        jdbc.update(
                "UPDATE articulos SET imagen_clave=?,imagen_ilustrativa=? WHERE comercio_id=? AND id=?",
                after.isEmpty() ? null : after.getFirst().get("key"),
                !after.isEmpty() && Boolean.TRUE.equals(after.getFirst().get("illustrative")),
                identity.storeId(),
                id);
        audit.record("articulo", id, "FOTOS", before, after);
        return after;
    }
}
