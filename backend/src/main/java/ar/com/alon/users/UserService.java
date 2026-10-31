// Administra cuentas, contraseñas y permisos; conserva al menos un gerente activo.
package ar.com.alon.users;

import ar.com.alon.security.PasswordHasher;
import ar.com.alon.security.RequestIdentity;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class UserService {
    private final long bootstrapStoreId;
    private final boolean localAccounts;
    private final PasswordHasher passwords;
    private final RequestIdentity identity;
    private final String dummyHash;
    public static final Set<String> ALL_PERMISSIONS =
            Set.of(
                    "inicio",
                    "ventas",
                    "articulos",
                    "rubros",
                    "clientes",
                    "compras",
                    "proveedores",
                    "caja",
                    "informes",
                    "tienda",
                    "usuarios",
                    "descuentos",
                    "costos",
                    "seguridad",
                    "negocio");
    private static final Set<String> VENDOR_PERMISSIONS =
            Set.of("ventas", "clientes", "caja", "tienda");
    private final JdbcTemplate jdbc;

    /**
     * Recibe y conserva las dependencias necesarias para las cuentas, permisos y derivación de
     * contraseñas del comercio.
     */
    public UserService(
            JdbcTemplate jdbc,
            PasswordHasher passwords,
            RequestIdentity identity,
            @Value("${alon.bootstrap-store-id:1}") long bootstrapStoreId,
            @Value("${alon.local-accounts:false}") boolean localAccounts) {
        this.jdbc = jdbc;
        this.passwords = passwords;
        this.identity = identity;
        this.bootstrapStoreId = bootstrapStoreId;
        this.localAccounts = localAccounts;
        this.dummyHash = passwords.hash(java.util.UUID.randomUUID().toString());
    }

    // Estas cuentas se crean únicamente si se habilita el perfil local y el comercio no tiene
    // usuarios.

    /**
     * Crea cuentas locales de prueba únicamente si la opción está habilitada y el comercio aún no
     * tiene usuarios.
     */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seedLocalAccounts() {
        if (!localAccounts) return;
        lockStore(bootstrapStoreId);
        Integer count =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM usuarios WHERE comercio_id=?",
                        Integer.class,
                        bootstrapStoreId);
        if (count == null || count > 0) return;
        insert(
                bootstrapStoreId,
                "alon_pruebas",
                "Cuenta de prueba Alon",
                "CambiarAlon-2026!",
                "DUENO",
                ALL_PERMISSIONS);
        insert(
                bootstrapStoreId,
                "vendedor1",
                "Vendedor de prueba",
                "CambiarAlon-2026!",
                "VENDEDOR",
                VENDOR_PERMISSIONS);
    }

    /**
     * Indica si el comercio inicial carece de usuarios y necesita crear su primera cuenta
     * administradora.
     */
    public boolean setupRequired() {
        Integer count =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM usuarios WHERE comercio_id=?",
                        Integer.class,
                        bootstrapStoreId);
        return count == null || count == 0;
    }

    /**
     * Bloquea el comercio inicial y crea el primer gerente solo si aún no existen usuarios
     * configurados.
     */
    @Transactional
    public Map<String, Object> setup(Credentials input) {
        lockStore(bootstrapStoreId);
        if (!setupRequired())
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "El comercio ya tiene usuarios configurados.");
        if (!"alon".equals(input.username()))
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "La cuenta del superusuario debe llamarse alon.");
        validate(input);
        return userById(
                insert(
                        bootstrapStoreId,
                        input.username(),
                        input.name(),
                        input.password(),
                        "SUPER",
                        ALL_PERMISSIONS),
                bootstrapStoreId);
    }

    /**
     * Verifica comercio, usuario activo y contraseña sin revelar cuentas inexistentes; actualiza
     * hashes antiguos y devuelve datos de sesión.
     */
    @Transactional
    public Map<String, Object> authenticate(Credentials input) {
        Long storeId =
                input.storeCode() == null || input.storeCode().isBlank()
                        ? bootstrapStoreId
                        : jdbc
                                .query(
                                        "SELECT id FROM comercios WHERE codigo=?",
                                        (rs, row) -> rs.getLong(1),
                                        input.storeCode().trim())
                                .stream()
                                .findFirst()
                                .orElse(-1L);
        List<Map<String, Object>> rows =
                jdbc.queryForList(
                        "SELECT id,password_hash,activo FROM usuarios WHERE comercio_id=? AND usuario=?",
                        storeId,
                        input.username() == null ? "" : input.username().trim());
        String stored =
                rows.isEmpty() ? dummyHash : String.valueOf(rows.getFirst().get("password_hash"));
        boolean verified = passwords.verify(input.password(), stored);
        if (rows.isEmpty() || !verified || !isActive(rows.getFirst().get("activo")))
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Usuario o contraseña incorrectos.");
        long id = ((Number) rows.getFirst().get("id")).longValue();
        if (passwords.needsUpgrade(stored))
            jdbc.update(
                    "UPDATE usuarios SET password_hash=? WHERE id=? AND comercio_id=?",
                    passwords.hash(input.password()),
                    id,
                    storeId);
        return userById(id, storeId);
    }

    /**
     * Revalida usuario, estado y versión de autenticación contra la base para descartar sesiones
     * vencidas o revocadas.
     */
    public Map<String, Object> sessionUser(Map<?, ?> session) {
        if (!(session.get("id") instanceof Number id)
                || !(session.get("comercio_id") instanceof Number store)) return Map.of();
        try {
            Map<String, Object> current = userById(id.longValue(), store.longValue());
            return isActive(current.get("activo"))
                            && java.util.Objects.equals(
                                    String.valueOf(session.get("auth_version")),
                                    String.valueOf(current.get("auth_version")))
                    ? current
                    : Map.of();
        } catch (org.springframework.dao.EmptyResultDataAccessException exception) {
            return Map.of();
        }
    }

    /**
     * Consulta usuarios del comercio sin exponer hashes de contraseña y aplica búsqueda y
     * paginación.
     */
    public List<Map<String, Object>> users() {
        return new ar.com.alon.shared.persistence.ReadPage(jdbc)
                .query(
                        "usuario,nombre",
                        "SELECT id,usuario,nombre,rol,permisos,activo FROM usuarios WHERE comercio_id=? AND rol NOT IN ('SUPER','DUENO') ORDER BY nombre,id",
                        identity.storeId());
    }

    /**
     * Valida credenciales, rol y permisos efectivos antes de crear el usuario del comercio con la
     * contraseña derivada.
     */
    @Transactional
    public Map<String, Object> createUser(UserInput input) {
        validatePermissions(input.permissions());
        String role =
                input.role() == null ? "PERSONALIZADO" : input.role().toUpperCase(Locale.ROOT);
        if (!Set.of("DUENO", "VENDEDOR", "PERSONALIZADO").contains(role))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rol inválido.");
        Set<String> customPermissions =
                ALL_PERMISSIONS.stream()
                        .filter(permission -> !"usuarios".equals(permission))
                        .collect(Collectors.toSet());
        Set<String> permissions =
                "DUENO".equals(role)
                        ? (input.permissions() == null
                                ? ALL_PERMISSIONS
                                : Set.copyOf(input.permissions()))
                        : "VENDEDOR".equals(role)
                                ? VENDOR_PERMISSIONS
                                : (input.permissions() == null
                                        ? Set.of()
                                        : input.permissions().stream()
                                                .filter(customPermissions::contains)
                                                .collect(Collectors.toUnmodifiableSet()));
        if (permissions.stream()
                .noneMatch(permission -> !Set.of("descuentos", "costos").contains(permission)))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Elegí al menos un acceso.");
        String name =
                input.name() == null || input.name().isBlank() ? input.username() : input.name();
        validateDelegation(role, permissions, null);
        validate(new Credentials(input.username(), name, input.password(), null));
        return userById(
                insert(
                        identity.storeId(),
                        input.username(),
                        name,
                        input.password(),
                        role,
                        permissions),
                identity.storeId());
    }

    /**
     * Valida y actualiza credenciales, rol y permisos; protege al último gerente activo y renueva
     * la versión que invalida sesiones anteriores.
     */
    @Transactional
    public Map<String, Object> updateUser(long id, UserUpdateInput input) {
        validatePermissions(input.permissions());
        lockStore(identity.storeId());
        Map<String, Object> existing =
                jdbc.queryForMap(
                        "SELECT id,usuario,nombre,rol,permisos,activo FROM usuarios WHERE id=? AND comercio_id=?",
                        id,
                        identity.storeId());
        String username =
                input.username() == null || input.username().isBlank()
                        ? String.valueOf(existing.get("usuario"))
                        : input.username().trim();
        String name =
                input.name() == null ? String.valueOf(existing.get("nombre")) : input.name().trim();
        if (!username.matches("[\\p{L}\\p{N}._-]{3,80}"))
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "El usuario debe tener entre 3 y 80 letras o números.");
        String role =
                input.role() == null
                        ? String.valueOf(existing.get("rol"))
                        : input.role().toUpperCase(Locale.ROOT);
        if (!Set.of("DUENO", "VENDEDOR", "PERSONALIZADO").contains(role))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rol inválido.");
        Set<String> permissions =
                "DUENO".equals(role)
                        ? (input.permissions() == null
                                ? ALL_PERMISSIONS
                                : Set.copyOf(input.permissions()))
                        : "VENDEDOR".equals(role)
                                ? VENDOR_PERMISSIONS
                                : (input.permissions() == null
                                        ? java.util.Arrays.stream(
                                                        String.valueOf(existing.get("permisos"))
                                                                .split(","))
                                                .filter(ALL_PERMISSIONS::contains)
                                                .filter(p -> !"usuarios".equals(p))
                                                .collect(Collectors.toSet())
                                        : input.permissions().stream()
                                                .filter(
                                                        p ->
                                                                ALL_PERMISSIONS.contains(p)
                                                                        && !"usuarios".equals(p))
                                                .collect(Collectors.toUnmodifiableSet()));
        if (permissions.stream()
                .noneMatch(permission -> !Set.of("descuentos", "costos").contains(permission)))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Elegí al menos un acceso.");
        validateDelegation(role, permissions, existing);
        if ("DUENO".equals(existing.get("rol"))
                && isActive(existing.get("activo"))
                && !"DUENO".equals(role)) preserveOwner(id);
        String password = input.password() == null ? "" : input.password();
        if (!password.isBlank()) validatePassword(password);
        if (name.isBlank() || name.length() > 160)
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "El nombre es demasiado largo.");
        if (password.isBlank())
            jdbc.update(
                    "UPDATE usuarios SET usuario=?,nombre=?,rol=?,permisos=?,auth_version=auth_version+1 WHERE id=? AND comercio_id=?",
                    username,
                    name,
                    role,
                    String.join(",", permissions),
                    id,
                    identity.storeId());
        else
            jdbc.update(
                    "UPDATE usuarios SET usuario=?,nombre=?,password_hash=?,rol=?,permisos=?,auth_version=auth_version+1 WHERE id=? AND comercio_id=?",
                    username,
                    name,
                    passwords.hash(password),
                    role,
                    String.join(",", permissions),
                    id,
                    identity.storeId());
        return userById(id, identity.storeId());
    }

    /**
     * Cambia el acceso del usuario y revoca sus sesiones anteriores; impide desactivar al último
     * gerente o la propia sesión.
     */
    @Transactional
    public void setActive(long id, boolean active, long currentUserId) {
        lockStore(identity.storeId());
        var target = userById(id, identity.storeId());
        validateDelegation(String.valueOf(target.get("rol")), Set.of(), target);
        if (!active) preserveOwner(id);
        if (id == currentUserId && !active)
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "No podés desactivar tu propia sesión.");
        int changed =
                jdbc.update(
                        "UPDATE usuarios SET activo=?,auth_version=auth_version+1 WHERE id=? AND comercio_id=?",
                        active,
                        id,
                        identity.storeId());
        if (changed == 0)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No se encontró el usuario.");
    }

    /**
     * Inserta el usuario con contraseña derivada y permisos serializados y recupera su id dentro
     * del comercio.
     */
    private long insert(
            long storeId,
            String username,
            String name,
            String password,
            String role,
            Set<String> permissions) {
        jdbc.update(
                "INSERT INTO usuarios(comercio_id,usuario,nombre,password_hash,rol,permisos) VALUES(?,?,?,?,?,?)",
                storeId,
                username.trim(),
                name.trim(),
                passwords.hash(password),
                role,
                String.join(",", permissions));
        return jdbc.queryForObject(
                "SELECT id FROM usuarios WHERE comercio_id=? AND usuario=?",
                Long.class,
                storeId,
                username.trim());
    }

    /**
     * Consulta un usuario por su identificador y comercio para obtener sus datos y versión de
     * autenticación.
     */
    private Map<String, Object> userById(long id, long storeId) {
        return jdbc.queryForMap(
                "SELECT id,comercio_id,usuario,nombre,rol,permisos,activo,auth_version,mfa_enabled,security_email FROM usuarios WHERE id=? AND comercio_id=?",
                id,
                storeId);
    }

    /**
     * Bloquea el comercio para serializar cambios que deben conservar al menos un gerente activo.
     */
    private void lockStore(long storeId) {
        jdbc.queryForObject("SELECT id FROM comercios WHERE id=? FOR UPDATE", Long.class, storeId);
    }

    /** Rechaza un cambio que dejaría al comercio sin ningún gerente activo. */
    private void preserveOwner(long id) {
        Map<String, Object> user = userById(id, identity.storeId());
        if (!"DUENO".equals(user.get("rol")) || !isActive(user.get("activo"))) return;
        Integer others =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM usuarios WHERE comercio_id=? AND rol IN ('DUENO','SUPER') AND activo=TRUE AND id<>?",
                        Integer.class,
                        identity.storeId(),
                        id);
        if (others == null || others == 0)
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Debe quedar al menos un gerente activo.");
    }

    /**
     * Evita que un gerente cree otros gerentes, modifique al superusuario o delegue permisos
     * superiores.
     */
    private void validateDelegation(
            String role, Set<String> permissions, Map<String, Object> target) {
        if (target != null && "SUPER".equals(target.get("rol")))
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "La cuenta alon se administra desde Seguridad.");
        if ("SUPER".equals(identity.user().get("rol"))) return;
        if ("DUENO".equals(role) || target != null && "DUENO".equals(target.get("rol")))
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Solo el superusuario puede administrar gerentes.");
        if (permissions.stream().anyMatch(permission -> !identity.permits(permission)))
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "No podés otorgar accesos que no tenés habilitados.");
    }

    /** Rechaza permisos desconocidos o nulos dentro de la lista enviada por el formulario. */
    private void validatePermissions(List<String> permissions) {
        if (permissions != null
                && permissions.stream()
                        .anyMatch(
                                permission ->
                                        permission == null
                                                || !ALL_PERMISSIONS.contains(permission)))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Hay permisos no válidos.");
    }

    /**
     * Exige una contraseña de entre doce y ciento veintiocho caracteres antes de derivarla y
     * guardarla.
     */
    private void validatePassword(String password) {
        if (password == null || password.length() < 12 || password.length() > 128)
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "La contraseña debe tener entre 12 y 128 caracteres.");
    }

    /**
     * Valida formato de usuario, nombre y contraseña para el alta o configuración de una cuenta.
     */
    private void validate(Credentials c) {
        if (c.username() == null || !c.username().trim().matches("[\\p{L}\\p{N}._-]{3,80}"))
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "El usuario debe tener entre 3 y 80 letras o números.");
        if (c.name() == null || c.name().isBlank() || c.name().length() > 160)
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Ingresá el nombre de la persona.");
        validatePassword(c.password());
    }

    /**
     * Interpreta el indicador de acceso activo tanto si JDBC lo devuelve como booleano como si lo
     * devuelve como número.
     */
    private static boolean isActive(Object value) {
        return Boolean.TRUE.equals(value)
                || value instanceof Number number && number.intValue() != 0;
    }

    public record Credentials(
            @jakarta.validation.constraints.Size(max = 80) String username,
            @jakarta.validation.constraints.Size(max = 160) String name,
            @jakarta.validation.constraints.Size(max = 128) String password,
            @jakarta.validation.constraints.Size(max = 40) String storeCode) {}

    public record UserInput(
            @Size(max = 80) String username,
            @Size(max = 160) String name,
            @Size(max = 128) String password,
            @Size(max = 20) String role,
            @Size(max = 20) List<@NotNull @Size(max = 30) String> permissions) {}

    public record UserUpdateInput(
            @Size(max = 80) String username,
            @Size(max = 160) String name,
            @Size(max = 128) String password,
            @Size(max = 20) String role,
            @Size(max = 20) List<@NotNull @Size(max = 30) String> permissions) {}
}
