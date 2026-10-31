// Consulta rubros del comercio actual para mostrarlos en pantalla.
package ar.com.alon.categories;

import ar.com.alon.security.RequestIdentity;
import ar.com.alon.shared.persistence.ReadPage;
import java.time.*;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CategoryQueries {
    private final JdbcTemplate jdbc;
    private final RequestIdentity identity;
    private final Clock clock;
    private final ReadPage pages;

    /** Recibe y conserva las dependencias necesarias para las consultas paginadas de rubros. */
    public CategoryQueries(JdbcTemplate jdbc, RequestIdentity identity, Clock clock) {
        this.jdbc = jdbc;
        this.identity = identity;
        this.clock = clock;
        this.pages = new ReadPage(jdbc);
    }

    /** Consulta rubros del comercio ordenados por nombre. */
    public List<Map<String, Object>> categories() {
        return pages.query(
                "codigo,nombre",
                "SELECT id, codigo, nombre FROM rubros WHERE comercio_id = ? ORDER BY nombre",
                identity.storeId());
    }
}
