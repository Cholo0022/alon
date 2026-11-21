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
    /** Recibe y conserva las dependencias necesarias para las reglas de alta de rubros. */
    public CategoryService(
            JdbcTemplate jdbc, RequestIdentity identity, Clock clock, AuditLog audit) {
        super(jdbc, identity, clock, audit);
    }

    /**
     * Valida que el nombre de rubro no esté repetido en el comercio y crea el registro con un
     * código propio.
     */
    @Transactional
    public Map<String, Object> createCategory(CategoryInput input) {
        String name = required(input.name(), "El nombre del rubro es obligatorio.");
        ensureUniqueName(name, 0);
        String code = "RUB-" + java.util.UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        long id =
                insert(
                        "INSERT INTO rubros (comercio_id,codigo,nombre) VALUES (?,?,?)",
                        identity.storeId(),
                        code,
                        name);
        return Map.of("id", id, "codigo", code, "nombre", name);
    }

    @Transactional
    public Map<String, Object> updateCategory(long id, CategoryInput input) {
        String name = required(input.name(), "El nombre del rubro es obligatorio.");
        var before = lockCategory(id);
        ensureUniqueName(name, id);
        jdbc.update(
                "UPDATE rubros SET nombre=? WHERE comercio_id=? AND id=?",
                name,
                identity.storeId(),
                id);
        Map<String, Object> after =
                Map.of(
                        "id",
                        id,
                        "codigo",
                        before.get("codigo"),
                        "nombre",
                        name,
                        "estado",
                        before.get("estado"));
        audit.record("rubro", id, "MODIFICACION", before, after);
        return after;
    }

    @Transactional
    public Map<String, Object> setActive(long id, boolean active) {
        var before = lockCategory(id);
        String state = active ? "ACTIVO" : "INACTIVO";
        if (state.equals(before.get("estado"))) return before;
        jdbc.update(
                "UPDATE rubros SET estado=? WHERE comercio_id=? AND id=?",
                state,
                identity.storeId(),
                id);
        Map<String, Object> after = new java.util.LinkedHashMap<>(before);
        after.put("estado", state);
        audit.record("rubro", id, active ? "REACTIVACION" : "DESACTIVACION", before, after);
        return after;
    }

    private Map<String, Object> lockCategory(long id) {
        return jdbc.queryForMap(
                "SELECT id,codigo,nombre,estado FROM rubros WHERE comercio_id=? AND id=? FOR UPDATE",
                identity.storeId(),
                id);
    }

    private void ensureUniqueName(String name, long excludedId) {
        Integer exists =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM rubros WHERE comercio_id=? AND LOWER(TRIM(nombre))=LOWER(TRIM(?)) AND id<>?",
                        Integer.class,
                        identity.storeId(),
                        name,
                        excludedId);
        if (exists != null && exists > 0)
            throw new IllegalArgumentException("Ya existe un rubro con ese nombre.");
    }
}
