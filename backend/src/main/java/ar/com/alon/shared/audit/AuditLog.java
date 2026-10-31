// Guarda quién hizo cada cambio y cuáles eran los datos anteriores y los nuevos.
package ar.com.alon.shared.audit;

import ar.com.alon.security.RequestIdentity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class AuditLog {
    private final JdbcTemplate jdbc;
    private final RequestIdentity identity;
    private final JsonMapper mapper;

    public AuditLog(JdbcTemplate jdbc, RequestIdentity identity, JsonMapper mapper) {
        this.jdbc = jdbc;
        this.identity = identity;
        this.mapper = mapper;
    }

    public void record(String entity, long id, String action, Object before, Object after) {
        jdbc.update(
                "INSERT INTO auditoria_operaciones(comercio_id,usuario_id,entidad,entidad_id,accion,datos_anteriores,datos_nuevos) VALUES(?,?,?,?,?,?,?)",
                identity.storeId(),
                identity.userId(),
                entity,
                id,
                action,
                mapper.writeValueAsString(before),
                mapper.writeValueAsString(after));
    }
}
