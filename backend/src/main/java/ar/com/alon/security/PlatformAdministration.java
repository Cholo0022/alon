package ar.com.alon.security;

import ar.com.alon.users.UserService;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class PlatformAdministration {
    private final JdbcTemplate jdbc;
    private final RequestIdentity identity;
    private final UserService users;
    private final TotpSecurity crypto;
    private final Clock clock;

    /** Reúne cuentas del negocio, generación criptográfica y reloj para administrar licencias. */
    public PlatformAdministration(
            JdbcTemplate jdbc,
            RequestIdentity identity,
            UserService users,
            TotpSecurity crypto,
            Clock clock) {
        this.jdbc = jdbc;
        this.identity = identity;
        this.users = users;
        this.crypto = crypto;
        this.clock = clock;
    }

    /** Exige el rol SUPER también al invocar directamente el servicio. */
    private void requireSuper() {
        if (!"SUPER".equals(identity.user().get("rol")))
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Solo alon puede administrar gerentes y licencias.");
    }

    /** Lista gerentes y claves emitidas sin devolver contraseñas ni hashes. */
    public Map<String, Object> dashboard() {
        requireSuper();
        return Map.of(
                "managers",
                jdbc.queryForList(
                        "SELECT id,usuario,nombre,permisos,security_email,activo FROM usuarios WHERE comercio_id=? AND rol='DUENO' ORDER BY id DESC",
                        identity.storeId()),
                "licenses",
                jdbc.queryForList(
                        "SELECT id,meses,creado_en,activar_hasta,usada_en,revocada FROM licencias WHERE comercio_id=? ORDER BY creado_en DESC LIMIT 50",
                        identity.storeId()),
                "business",
                jdbc.queryForMap(
                        "SELECT nombre,licencia_controlada,licencia_hasta FROM comercios WHERE id=?",
                        identity.storeId()));
    }

    /** Genera una contraseña aleatoria que solo se devuelve a la sesión del superusuario. */
    public Map<String, String> password() {
        requireSuper();
        return Map.of("password", crypto.token());
    }

    /**
     * Crea un gerente con correo, contraseña y accesos elegidos; emite su primera clave de
     * licencia.
     */
    public Map<String, Object> create(ManagerInput input) {
        requireSuper();
        var created =
                users.createUser(
                        new UserService.UserInput(
                                input.username(),
                                input.name(),
                                input.password(),
                                "DUENO",
                                input.permissions()));
        long id = ((Number) created.get("id")).longValue();
        jdbc.update(
                "UPDATE usuarios SET security_email=? WHERE comercio_id=? AND id=?",
                input.email().trim(),
                identity.storeId(),
                id);
        return Map.of("managerId", id, "license", issue(input.months()));
    }

    /**
     * Edita únicamente gerentes del mismo negocio e invalida sesiones y aprobaciones anteriores.
     */
    public void update(long id, ManagerUpdate input) {
        requireSuper();
        int found =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM usuarios WHERE comercio_id=? AND id=? AND rol='DUENO'",
                        Integer.class,
                        identity.storeId(),
                        id);
        if (found != 1)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No se encontró el gerente.");
        users.updateUser(
                id,
                new UserService.UserUpdateInput(
                        input.username(),
                        input.name(),
                        input.password(),
                        "DUENO",
                        input.permissions()));
        jdbc.update(
                "UPDATE usuarios SET security_email=? WHERE comercio_id=? AND id=?",
                input.email().trim(),
                identity.storeId(),
                id);
    }

    /** Emite una clave de 128 bits, guarda su hash y anula claves anteriores todavía sin usar. */
    public Map<String, Object> issue(int months) {
        requireSuper();
        if (months < 1 || months > 120)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Elegí entre 1 y 120 meses.");
        jdbc.queryForObject(
                "SELECT id FROM comercios WHERE id=? FOR UPDATE", Long.class, identity.storeId());
        byte[] bytes = new byte[16];
        new java.security.SecureRandom().nextBytes(bytes);
        String raw = HexFormat.of().withUpperCase().formatHex(bytes);
        String key =
                String.join(
                        "-",
                        raw.substring(0, 8),
                        raw.substring(8, 16),
                        raw.substring(16, 24),
                        raw.substring(24));
        LocalDateTime now = LocalDateTime.now(clock);
        jdbc.update(
                "UPDATE licencias SET revocada=TRUE WHERE comercio_id=? AND usada_en IS NULL",
                identity.storeId());
        jdbc.update(
                "INSERT INTO licencias(id,comercio_id,creado_por,clave_hash,meses,creado_en,activar_hasta) VALUES(?,?,?,?,?,?,?)",
                UUID.randomUUID().toString(),
                identity.storeId(),
                identity.userId(),
                crypto.hash(raw),
                months,
                now,
                now.plusDays(30));
        jdbc.update("UPDATE comercios SET licencia_controlada=TRUE WHERE id=?", identity.storeId());
        return Map.of("key", key, "months", months, "activateBefore", now.plusDays(30).toString());
    }

    /**
     * Activa, exclusivamente como superusuario, una clave del propio negocio y extiende la vigencia
     * por meses calendario, sin duplicar usos concurrentes.
     */
    public Map<String, Object> activate(String key) {
        requireSuper();
        var business =
                jdbc.queryForMap(
                        "SELECT licencia_hasta FROM comercios WHERE id=? FOR UPDATE",
                        identity.storeId());
        String normalized = key.replaceAll("[-\\s]", "").toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-F0-9]{32}"))
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Clave de licencia inválida.");
        LocalDateTime now = LocalDateTime.now(clock);
        var rows =
                jdbc.queryForList(
                        "SELECT id,meses FROM licencias WHERE comercio_id=? AND clave_hash=? AND usada_en IS NULL AND revocada=FALSE AND activar_hasta>? FOR UPDATE",
                        identity.storeId(),
                        crypto.hash(normalized),
                        now);
        if (rows.size() != 1)
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "La clave no corresponde a este negocio, venció o ya fue utilizada.");
        Object value = business.get("licencia_hasta");
        LocalDateTime until =
                value instanceof java.sql.Timestamp stamp
                        ? stamp.toLocalDateTime()
                        : value instanceof LocalDateTime date ? date : now;
        LocalDateTime expiration =
                (until.isAfter(now) ? until : now)
                        .plusMonths(((Number) rows.getFirst().get("meses")).intValue());
        jdbc.update(
                "UPDATE comercios SET licencia_controlada=TRUE,licencia_hasta=? WHERE id=?",
                expiration,
                identity.storeId());
        jdbc.update(
                "UPDATE licencias SET usada_en=?,usada_por=? WHERE id=?",
                now,
                identity.userId(),
                rows.getFirst().get("id"));
        return Map.of("expiresAt", expiration.toString());
    }

    public record ManagerInput(
            @NotBlank @Size(max = 80) String username,
            @NotBlank @Size(max = 160) String name,
            @NotBlank @Size(min = 8, max = 128) String password,
            @NotBlank @Email @Size(max = 254) String email,
            @NotNull @Size(min = 1, max = 20) List<@NotNull @Size(max = 30) String> permissions,
            @Min(1) @Max(120) int months) {}

    public record ManagerUpdate(
            @NotBlank @Size(max = 80) String username,
            @NotBlank @Size(max = 160) String name,
            @Size(max = 128) String password,
            @NotBlank @Email @Size(max = 254) String email,
            @NotNull @Size(min = 1, max = 20) List<@NotNull @Size(max = 30) String> permissions) {}
}
