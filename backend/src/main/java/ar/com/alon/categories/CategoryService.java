// Organiza las reglas de rubros.
package ar.com.alon.categories;

import ar.com.alon.categories.dto.CategoryInput;
import ar.com.alon.security.RequestIdentity;
import ar.com.alon.shared.audit.AuditLog;
import ar.com.alon.shared.persistence.StoreJdbcSupport;
import java.math.*;
import java.sql.*;
import java.time.*;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CategoryService extends StoreJdbcSupport {
    public CategoryService(
            JdbcTemplate jdbc, RequestIdentity identity, Clock clock, AuditLog audit) {
        super(jdbc, identity, clock, audit);
    }

    @Transactional
    public Map<String, Object> createCategory(CategoryInput input) {
        String name = required(input.name(), "El nombre del rubro es obligatorio.");
        Integer exists =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM rubros WHERE comercio_id=? AND LOWER(TRIM(nombre))=LOWER(TRIM(?))",
                        Integer.class,
                        identity.storeId(),
                        name);
        if (exists != null && exists > 0)
            throw new IllegalArgumentException("Ya existe un rubro con ese nombre.");
        String code = "RUB-" + java.util.UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        long id =
                insert(
                        "INSERT INTO rubros (comercio_id,codigo,nombre) VALUES (?,?,?)",
                        identity.storeId(),
                        code,
                        name);
        return Map.of("id", id, "codigo", code, "nombre", name);
    }
}
