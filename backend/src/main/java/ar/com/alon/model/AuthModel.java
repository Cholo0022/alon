package ar.com.alon.model;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthModel {
    private static final long STORE_ID = 1L;
    public static final Set<String> ALL_PERMISSIONS = Set.of("inicio", "ventas", "articulos", "rubros", "clientes", "compras", "proveedores", "caja", "informes", "tienda", "usuarios");
    private static final Set<String> VENDOR_PERMISSIONS = Set.of("ventas", "clientes", "caja");
    private final JdbcTemplate jdbc;
    private final SecureRandom random = new SecureRandom();

    public AuthModel(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @EventListener(ApplicationReadyEvent.class)
    public void seedDemoAccounts() {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM usuarios WHERE comercio_id=?", Integer.class, STORE_ID);
        if (count == null || count > 0) return;
        insert("demo", "demo", "demo", "DUENO", ALL_PERMISSIONS);
        insert("vendedor1", "vendedor1", "demo", "VENDEDOR", VENDOR_PERMISSIONS);
    }

    public boolean setupRequired() {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM usuarios WHERE comercio_id=?", Integer.class, STORE_ID);
        return count == null || count == 0;
    }

    public Map<String, Object> setup(Credentials input) {
        if (!setupRequired()) throw new ResponseStatusException(HttpStatus.CONFLICT, "El comercio ya tiene usuarios configurados.");
        validate(input);
        return userById(insert(input.username(), input.name(), input.password(), "DUENO", ALL_PERMISSIONS));
    }

    public Map<String, Object> authenticate(Credentials input) {
        List<Map<String,Object>> rows = jdbc.queryForList("SELECT id,nombre,usuario,password_hash,rol,permisos,activo FROM usuarios WHERE comercio_id=? AND usuario=?", STORE_ID, input.username() == null ? "" : input.username().trim());
        if (rows.isEmpty() || !isActive(rows.getFirst().get("activo")) || !verify(input.password(), String.valueOf(rows.getFirst().get("password_hash")))) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario o contraseña incorrectos.");
        return userById(((Number) rows.getFirst().get("id")).longValue());
    }

    public List<Map<String,Object>> users() {
        return jdbc.queryForList("SELECT id,usuario,nombre,rol,permisos,activo FROM usuarios WHERE comercio_id=? ORDER BY nombre", STORE_ID);
    }

    public Map<String,Object> createUser(UserInput input) {
        String role = input.role() == null ? "PERSONALIZADO" : input.role().toUpperCase();
        if (!Set.of("DUENO", "VENDEDOR", "PERSONALIZADO").contains(role)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rol inválido.");
        Set<String> customPermissions = ALL_PERMISSIONS.stream().filter(permission -> !"usuarios".equals(permission)).collect(Collectors.toSet());
        Set<String> permissions = "DUENO".equals(role) ? ALL_PERMISSIONS : "VENDEDOR".equals(role) ? VENDOR_PERMISSIONS : (input.permissions() == null ? Set.of() : input.permissions().stream().filter(customPermissions::contains).collect(Collectors.toUnmodifiableSet()));
        if (permissions.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Elegí al menos un acceso.");
        String name = input.name() == null || input.name().isBlank() ? input.username() : input.name();
        validate(new Credentials(input.username(), name, input.password()));
        return userById(insert(input.username(), name, input.password(), role, permissions));
    }

    public Map<String,Object> updateUser(long id, UserUpdateInput input) {
        Map<String,Object> existing = jdbc.queryForMap("SELECT id,usuario,nombre,rol,permisos,activo FROM usuarios WHERE id=? AND comercio_id=?", id, STORE_ID);
        String username = input.username() == null || input.username().isBlank() ? String.valueOf(existing.get("usuario")) : input.username().trim();
        String name = input.name() == null || input.name().isBlank() ? username : input.name().trim();
        if (!username.matches("[\\p{L}\\p{N}._-]{3,80}")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El usuario debe tener entre 3 y 80 letras o números.");
        String role = input.role() == null ? String.valueOf(existing.get("rol")) : input.role().toUpperCase();
        if (!Set.of("DUENO", "VENDEDOR", "PERSONALIZADO").contains(role)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rol inválido.");
        Set<String> permissions = "DUENO".equals(role) ? ALL_PERMISSIONS : "VENDEDOR".equals(role) ? VENDOR_PERMISSIONS : (input.permissions() == null ? Set.of() : input.permissions().stream().filter(p -> ALL_PERMISSIONS.contains(p) && !"usuarios".equals(p)).collect(Collectors.toUnmodifiableSet()));
        if (permissions.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Elegí al menos un acceso.");
        String password = input.password() == null ? "" : input.password();
        if (!password.isBlank() && password.length() < 4) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La contraseña debe tener al menos 4 caracteres.");
        if (password.isBlank()) jdbc.update("UPDATE usuarios SET usuario=?,nombre=?,rol=?,permisos=? WHERE id=? AND comercio_id=?", username, name, role, String.join(",", permissions), id, STORE_ID);
        else jdbc.update("UPDATE usuarios SET usuario=?,nombre=?,password_hash=?,rol=?,permisos=? WHERE id=? AND comercio_id=?", username, name, hash(password), role, String.join(",", permissions), id, STORE_ID);
        return userById(id);
    }

    public void setActive(long id, boolean active, long currentUserId) {
        if (id == currentUserId && !active) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No podés desactivar tu propia sesión.");
        int changed = jdbc.update("UPDATE usuarios SET activo=? WHERE id=? AND comercio_id=?", active, id, STORE_ID);
        if (changed == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No se encontró el usuario.");
    }

    private long insert(String username, String name, String password, String role, Set<String> permissions) {
        jdbc.update("INSERT INTO usuarios(comercio_id,usuario,nombre,password_hash,rol,permisos) VALUES(?,?,?,?,?,?)", STORE_ID, username.trim(), name.trim(), hash(password), role, String.join(",", permissions));
        return jdbc.queryForObject("SELECT id FROM usuarios WHERE comercio_id=? AND usuario=?", Long.class, STORE_ID, username.trim());
    }

    private Map<String,Object> userById(long id) { return jdbc.queryForMap("SELECT id,usuario,nombre,rol,permisos,activo FROM usuarios WHERE id=?", id); }

    private void validate(Credentials c) {
        if (c.username() == null || !c.username().trim().matches("[\\p{L}\\p{N}._-]{3,80}")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El usuario debe tener entre 3 y 80 letras o números.");
        if (c.name() == null || c.name().isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ingresá el nombre de la persona.");
        if (c.password() == null || c.password().length() < 4) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La contraseña debe tener al menos 4 caracteres.");
    }

    private static boolean isActive(Object value) { return Boolean.TRUE.equals(value) || value instanceof Number number && number.intValue() != 0; }
    private String hash(String password) {
        byte[] salt = new byte[16]; random.nextBytes(salt);
        return "pbkdf2$" + Base64.getEncoder().encodeToString(salt) + "$" + Base64.getEncoder().encodeToString(derive(password, salt));
    }
    private boolean verify(String password, String stored) {
        if (password == null) return false;
        try { String[] parts = stored.split("\\$"); return parts.length == 3 && "pbkdf2".equals(parts[0]) && MessageDigest.isEqual(Base64.getDecoder().decode(parts[2]), derive(password, Base64.getDecoder().decode(parts[1]))); }
        catch (RuntimeException ex) { return false; }
    }
    private byte[] derive(String password, byte[] salt) {
        try { var spec = new javax.crypto.spec.PBEKeySpec(password.toCharArray(), salt, 210_000, 256); return javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded(); }
        catch (java.security.GeneralSecurityException ex) { throw new IllegalStateException(ex); }
    }

    public record Credentials(String username, String name, String password) {}
    public record UserInput(String username, String name, String password, String role, List<String> permissions) {}
    public record UserUpdateInput(String username, String name, String password, String role, List<String> permissions) {}
}
