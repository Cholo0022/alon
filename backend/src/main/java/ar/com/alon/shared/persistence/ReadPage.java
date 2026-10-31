// Busca y pagina registros del comercio para no cargar todos los datos de una sola vez.
package ar.com.alon.shared.persistence;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Consultas con búsqueda y paginación para cargar solo los registros necesarios. */
@Component
public class ReadPage {
    private final JdbcTemplate jdbc;

    public ReadPage(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Map<String, Object>> query(String columns, String sql, Object... parameters) {
        var request =
                RequestContextHolder.getRequestAttributes()
                                instanceof ServletRequestAttributes attributes
                        ? attributes.getRequest()
                        : null;
        int page = integer(request == null ? null : request.getParameter("page"), 0, 0, 1_000_000);
        int size = integer(request == null ? null : request.getParameter("size"), 100, 1, 200);
        String search = request == null ? null : request.getParameter("q");
        List<Object> args = new ArrayList<>(Arrays.asList(parameters));
        if (search != null && !search.isBlank()) {
            if (search.length() > 160)
                throw new IllegalArgumentException("La búsqueda es demasiado larga.");
            String upper = sql.toUpperCase(java.util.Locale.ROOT);
            int group = upper.indexOf("GROUP BY"), order = upper.indexOf("ORDER BY");
            int insertion = group >= 0 ? group : order >= 0 ? order : sql.length();
            sql =
                    sql.substring(0, insertion)
                            + " AND LOWER(CONCAT_WS(' ',"
                            + columns
                            + ")) LIKE LOWER(?) "
                            + sql.substring(insertion);
            args.add("%" + search.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%");
            sql = sql.replace("LIKE LOWER(?)", "LIKE LOWER(?) ESCAPE '!'");
        }
        return jdbc.queryForList(sql + " LIMIT ? OFFSET ?", concat(args, size, (long) page * size));
    }

    private Object[] concat(List<Object> args, int size, long offset) {
        args.add(size);
        args.add(offset);
        return args.toArray();
    }

    private int integer(String value, int fallback, int minimum, int maximum) {
        if (value == null) return fallback;
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < minimum || parsed > maximum) throw new NumberFormatException();
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("La página o el tamaño solicitado no es válido.");
        }
    }
}
