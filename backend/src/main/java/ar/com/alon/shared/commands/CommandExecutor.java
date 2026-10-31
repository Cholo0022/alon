// Evita guardar dos veces la misma operación cuando se reintenta una solicitud.
package ar.com.alon.shared.commands;

import ar.com.alon.security.RequestIdentity;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** Guarda juntos los cambios, su registro de auditoría y el resultado para futuros reintentos. */
@Service
public class CommandExecutor {
    private final JdbcTemplate jdbc;
    private final RequestIdentity identity;
    private final HttpServletRequest request;
    private final JsonMapper mapper;

    public CommandExecutor(
            JdbcTemplate jdbc,
            RequestIdentity identity,
            HttpServletRequest request,
            JsonMapper mapper) {
        this.jdbc = jdbc;
        this.identity = identity;
        this.request = request;
        this.mapper = mapper;
    }

    @Transactional
    public Map<String, Object> execute(
            String command, Object input, Supplier<Map<String, Object>> action) {
        String key = request.getHeader("Idempotency-Key");
        if (key == null || !key.matches("[A-Za-z0-9_-]{16,80}"))
            throw new IllegalArgumentException("Falta un identificador válido de operación.");
        // Ordena las escrituras de un comercio para que no se pisen entre sí.
        jdbc.queryForObject(
                "SELECT id FROM comercios WHERE id=? FOR UPDATE", Long.class, identity.storeId());
        String hash =
                digest(command + ":" + identity.userId() + ":" + mapper.writeValueAsString(input));
        var saved =
                jdbc.queryForList(
                        "SELECT solicitud_hash,respuesta FROM comandos_procesados WHERE comercio_id=? AND clave=?",
                        identity.storeId(),
                        key);
        if (!saved.isEmpty()) {
            if (!hash.equals(saved.getFirst().get("solicitud_hash")))
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "El identificador de operación ya se utilizó para otros datos.");
            return mapper.readValue(
                    String.valueOf(saved.getFirst().get("respuesta")),
                    new TypeReference<Map<String, Object>>() {});
        }
        Map<String, Object> result = action.get();
        jdbc.update(
                "INSERT INTO comandos_procesados(comercio_id,clave,solicitud_hash,respuesta) VALUES(?,?,?,?)",
                identity.storeId(),
                key,
                hash,
                mapper.writeValueAsString(result));
        return result;
    }

    private String digest(String text) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
