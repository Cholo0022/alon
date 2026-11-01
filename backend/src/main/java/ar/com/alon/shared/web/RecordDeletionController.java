package ar.com.alon.shared.web;

import ar.com.alon.security.RequestIdentity;
import ar.com.alon.shared.commands.CommandExecutor;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/operations")
public class RecordDeletionController {
    private final RecordDeletionService service;
    private final CommandExecutor commands;

    public RecordDeletionController(RecordDeletionService service, CommandExecutor commands) {
        this.service = service;
        this.commands = commands;
    }

    @DeleteMapping("/articles/{id}")
    public Map<String, Object> article(@PathVariable long id) {
        return commands.execute("deleteArticle", id, () -> service.delete("articles", id));
    }

    @DeleteMapping("/customers/{id}")
    public Map<String, Object> customer(@PathVariable long id) {
        return commands.execute("deleteCustomer", id, () -> service.delete("customers", id));
    }

    @DeleteMapping("/suppliers/{id}")
    public Map<String, Object> supplier(@PathVariable long id) {
        return commands.execute("deleteSupplier", id, () -> service.delete("suppliers", id));
    }
}

@Service
class RecordDeletionService {
    private final JdbcTemplate jdbc;
    private final RequestIdentity identity;

    RecordDeletionService(JdbcTemplate jdbc, RequestIdentity identity) {
        this.jdbc = jdbc;
        this.identity = identity;
    }

    @Transactional
    public Map<String, Object> delete(String kind, long id) {
        String table =
                switch (kind) {
                    case "articles" -> "articulos";
                    case "customers" -> "clientes";
                    case "suppliers" -> "proveedores";
                    default -> throw new IllegalArgumentException("Tipo de registro inválido.");
                };
        long store = identity.storeId();
        var row =
                jdbc.queryForMap(
                        "SELECT id,nombre FROM "
                                + table
                                + " WHERE id=? AND comercio_id=? FOR UPDATE",
                        id,
                        store);
        try {
            if (kind.equals("articles")) {
                Long movements =
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM movimientos_stock WHERE articulo_id=? AND comercio_id=? AND tipo<>'ALTA'",
                                Long.class,
                                id,
                                store);
                if (movements != null && movements > 0) throw linkedRecord();
                // El alta inicial y la galería pertenecen al registro; no son comprobantes
                // comerciales.
                jdbc.update(
                        "DELETE FROM movimientos_stock WHERE articulo_id=? AND comercio_id=? AND tipo='ALTA'",
                        id,
                        store);
                jdbc.update(
                        "DELETE FROM articulo_imagenes WHERE articulo_id=? AND comercio_id=?",
                        id,
                        store);
            }
            if (jdbc.update("DELETE FROM " + table + " WHERE id=? AND comercio_id=?", id, store)
                    != 1)
                throw new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "No se encontró el registro.");
        } catch (DataIntegrityViolationException e) {
            throw linkedRecord();
        }
        return Map.of("id", id, "nombre", row.get("nombre"), "eliminado", true);
    }

    private ResponseStatusException linkedRecord() {
        return new ResponseStatusException(
                HttpStatus.CONFLICT,
                "No se puede eliminar: el registro tiene operaciones o registros vinculados. El historial debe conservarse.");
    }
}
