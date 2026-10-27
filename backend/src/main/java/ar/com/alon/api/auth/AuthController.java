package ar.com.alon.api.auth;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final JdbcTemplate jdbc;
    private static final long STORE_ID = 1L;
    public static final Set<String> ALL_PERMISSIONS = Set.of("inicio", "ventas", "articulos", "rubros", "clientes", "compras", "proveedores", "caja", "informes", "tienda", "usuarios");
    private static final Set<String> VENDOR_PERMISSIONS = Set.of("ventas", "clientes", "caja");
    private final SecureRandom random = new SecureRandom();

    public AuthController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @EventListener(ApplicationReadyEvent.class)
    public void seedDemoAccounts() {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM usuarios WHERE comercio_id=?", Integer.class, STORE_ID);
        if (count == null || count > 0) return;
        insert("demo", "demo", "demo", "DUENO", ALL_PERMISSIONS);
        insert("vendedor1", "vendedor1", "demo", "VENDEDOR", VENDOR_PERMISSIONS);
    }

    @GetMapping("/status")
    public Map<String, Object> status(HttpServletRequest request) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM usuarios WHERE comercio_id=?", Integer.class, STORE_ID);
        Object user = request.getSession(false) == null ? null : request.getSession(false).getAttribute("alonUser");
        return Map.of("setupRequired", count == null || count == 0, "user", user == null ? Map.of() : user);
    }

    @PostMapping("/setup")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> setup(@RequestBody Credentials input, HttpServletRequest request) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM usuarios WHERE comercio_id=?", Integer.class, STORE_ID);
        if (count != null && count > 0) throw new ResponseStatusException(HttpStatus.CONFLICT, "El comercio ya tiene usuarios configurados.");
        validate(input);
        long id = insert(input.username(), input.name(), input.password(), "DUENO", ALL_PERMISSIONS);
        return startSession(id, request);
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody Credentials input, HttpServletRequest request) {
        List<Map<String,Object>> rows = jdbc.queryForList("SELECT id,nombre,usuario,password_hash,rol,permisos,activo FROM usuarios WHERE comercio_id=? AND usuario=?", STORE_ID, input.username() == null ? "" : input.username().trim());
        if (rows.isEmpty() || !isActive(rows.getFirst().get("activo")) || !verify(input.password(), String.valueOf(rows.getFirst().get("password_hash")))) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario o contraseña incorrectos.");
        return startSession(((Number) rows.getFirst().get("id")).longValue(), request);
    }

    @PostMapping("/logout")
    public Map<String, Object> logout(HttpServletRequest request) { if (request.getSession(false) != null) request.getSession(false).invalidate(); return Map.of("ok", true); }

    @GetMapping("/users")
    public List<Map<String,Object>> users() { return jdbc.queryForList("SELECT id,usuario,nombre,rol,permisos,activo FROM usuarios WHERE comercio_id=? ORDER BY nombre", STORE_ID); }

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String,Object> createUser(@RequestBody UserInput input) {
        String role = input.role() == null ? "PERSONALIZADO" : input.role().toUpperCase();
        if (!Set.of("DUENO", "VENDEDOR", "PERSONALIZADO").contains(role)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rol inválido.");
        Set<String> customPermissions = ALL_PERMISSIONS.stream().filter(permission -> !"usuarios".equals(permission)).collect(java.util.stream.Collectors.toSet());
        Set<String> permissions = "DUENO".equals(role) ? ALL_PERMISSIONS : "VENDEDOR".equals(role) ? VENDOR_PERMISSIONS : (input.permissions() == null ? Set.of() : input.permissions().stream().filter(customPermissions::contains).collect(java.util.stream.Collectors.toUnmodifiableSet()));
        if (permissions.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Elegí al menos un acceso.");
        String name = input.name() == null || input.name().isBlank() ? input.username() : input.name();
        validate(new Credentials(input.username(), name, input.password()));
        long id = insert(input.username(), name, input.password(), role, permissions);
        return jdbc.queryForMap("SELECT id,usuario,nombre,rol,permisos,activo FROM usuarios WHERE id=?", id);
    }

    @PatchMapping("/users/{id}")
    public Map<String,Object> updateUser(@PathVariable long id, @RequestBody UserUpdateInput input) {
        Map<String,Object> existing = jdbc.queryForMap("SELECT id,usuario,nombre,rol,permisos,activo FROM usuarios WHERE id=? AND comercio_id=?", id, STORE_ID);
        String username = input.username() == null || input.username().isBlank() ? String.valueOf(existing.get("usuario")) : input.username().trim();
        String name = input.name() == null || input.name().isBlank() ? username : input.name().trim();
        if (!username.matches("[\\p{L}\\p{N}._-]{3,80}")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El usuario debe tener entre 3 y 80 letras o números.");
        String role = input.role() == null ? String.valueOf(existing.get("rol")) : input.role().toUpperCase();
        if (!Set.of("DUENO", "VENDEDOR", "PERSONALIZADO").contains(role)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rol inválido.");
        Set<String> permissions = "DUENO".equals(role) ? ALL_PERMISSIONS : "VENDEDOR".equals(role) ? VENDOR_PERMISSIONS : (input.permissions() == null ? Set.of() : input.permissions().stream().filter(p -> ALL_PERMISSIONS.contains(p) && !"usuarios".equals(p)).collect(java.util.stream.Collectors.toUnmodifiableSet()));
        if (permissions.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Elegí al menos un acceso.");
        String password = input.password() == null ? "" : input.password();
        if (!password.isBlank() && password.length() < 4) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La contraseña debe tener al menos 4 caracteres.");
        if (password.isBlank()) jdbc.update("UPDATE usuarios SET usuario=?,nombre=?,rol=?,permisos=? WHERE id=? AND comercio_id=?", username, name, role, String.join(",", permissions), id, STORE_ID);
        else jdbc.update("UPDATE usuarios SET usuario=?,nombre=?,password_hash=?,rol=?,permisos=? WHERE id=? AND comercio_id=?", username, name, hash(password), role, String.join(",", permissions), id, STORE_ID);
        return jdbc.queryForMap("SELECT id,usuario,nombre,rol,permisos,activo FROM usuarios WHERE id=?", id);
    }

    @PatchMapping("/users/{id}/active")
    public Map<String,Object> setActive(@PathVariable long id, @RequestBody ActiveInput input, HttpServletRequest request) {
        Map<?,?> current = (Map<?,?>) request.getSession(false).getAttribute("alonUser");
        if (id == ((Number) current.get("id")).longValue() && !input.active()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No podés desactivar tu propia sesión.");
        jdbc.update("UPDATE usuarios SET activo=? WHERE id=? AND comercio_id=?", input.active(), id, STORE_ID);
        return Map.of("ok", true);
    }

    private long insert(String username, String name, String password, String role, Set<String> permissions) {
        jdbc.update("INSERT INTO usuarios(comercio_id,usuario,nombre,password_hash,rol,permisos) VALUES(?,?,?,?,?,?)", STORE_ID, username.trim(), name.trim(), hash(password), role, String.join(",", permissions));
        return jdbc.queryForObject("SELECT id FROM usuarios WHERE comercio_id=? AND usuario=?", Long.class, STORE_ID, username.trim());
    }
    private Map<String,Object> startSession(long id, HttpServletRequest request) {
        Map<String,Object> user = jdbc.queryForMap("SELECT id,usuario,nombre,rol,permisos,activo FROM usuarios WHERE id=?", id);
        if (request.getSession(false) != null) request.changeSessionId();
        request.getSession(true).setAttribute("alonUser", user);
        return user;
    }
    private void validate(Credentials c) {
        if (c.username() == null || !c.username().trim().matches("[\\p{L}\\p{N}._-]{3,80}")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El usuario debe tener entre 3 y 80 letras o números.");
        if (c.name() == null || c.name().isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ingresá el nombre de la persona.");
        if (c.password() == null || c.password().length() < 4) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La contraseña debe tener al menos 4 caracteres.");
    }
    private static boolean isActive(Object value) { return Boolean.TRUE.equals(value) || value instanceof Number number && number.intValue() != 0; }
    private String hash(String password) {
        byte[] salt = new byte[16]; random.nextBytes(salt);
        byte[] digest = derive(password, salt);
        return "pbkdf2$" + Base64.getEncoder().encodeToString(salt) + "$" + Base64.getEncoder().encodeToString(digest);
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
    public record ActiveInput(boolean active) {}
}
