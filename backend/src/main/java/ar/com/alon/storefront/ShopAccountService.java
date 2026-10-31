// Administra las cuentas de compradores, separadas de las cuentas del personal.
package ar.com.alon.storefront;

import ar.com.alon.security.PasswordHasher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.*;
import java.sql.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Las cuentas de compradores están separadas de las cuentas del personal. */
@Service
public class ShopAccountService {
    private final JdbcTemplate jdbc;
    private final PasswordHasher passwords;
    private final long storeId;
    private final String dummyHash;

    /**
     * Recibe y conserva las dependencias necesarias para las cuentas de compradores y el comercio
     * público configurado.
     */
    public ShopAccountService(
            JdbcTemplate jdbc,
            PasswordHasher passwords,
            @Value("${alon.bootstrap-store-id:1}") long storeId) {
        this.jdbc = jdbc;
        this.passwords = passwords;
        this.storeId = storeId;
        this.dummyHash = passwords.hash("Alon-unknown-account-password");
    }

    /**
     * Devuelve el comercio configurado para la tienda pública, independientemente de la sesión de
     * gestión.
     */
    public long storeId() {
        return storeId;
    }

    public record Registration(
            @NotBlank @Size(max = 60) String firstName,
            @NotBlank @Size(max = 60) String lastName,
            @NotBlank @Pattern(regexp = "[0-9]{7,8}") String dni,
            @NotBlank @Size(max = 180) String address,
            @NotBlank @Size(max = 20) String postalCode,
            @NotBlank @Size(max = 100) String city,
            @NotBlank @Size(max = 100) String province,
            @NotBlank @Size(max = 100) String country,
            @NotBlank @Size(max = 25) String phone,
            @NotBlank @Size(min = 4, max = 128) String password) {}

    public record Login(
            @NotBlank @Size(max = 25) String phone, @NotBlank @Size(max = 128) String password) {}

    public record PasswordChange(@NotBlank @Size(min = 4, max = 128) String password) {}

    public record PasswordReset(
            @NotBlank @Size(max = 25) String phone,
            @NotBlank @Size(min = 4, max = 128) String password) {}

    /**
     * Reemplaza la contraseña de una cuenta compradora por teléfono, según el modo simple
     * solicitado para esta tienda.
     */
    @Transactional
    public void resetPassword(PasswordReset input) {
        if (input.password() == null
                || input.password().isBlank()
                || input.password().length() < 4
                || input.password().length() > 128)
            throw new IllegalArgumentException(
                    "La contraseña debe tener entre 4 y 128 caracteres.");
        String phone = normalizePhone(input.phone()), digits = phone.substring(1);
        boolean local = digits.length() == 10 && !input.phone().trim().startsWith("+");
        var ids =
                jdbc.queryForList(
                        "SELECT id FROM compradores_tienda WHERE comercio_id=? AND telefono IN (?,?,?) AND activo=TRUE FOR UPDATE",
                        Long.class,
                        storeId,
                        phone,
                        local ? "+54" + digits : phone,
                        local ? "+549" + digits : phone);
        if (ids.size() != 1)
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "No se encontró una única cuenta con ese teléfono. Revisá el número ingresado.");
        jdbc.update(
                "UPDATE compradores_tienda SET password_hash=? WHERE comercio_id=? AND id=? AND activo=TRUE",
                passwords.hash(input.password()),
                storeId,
                ids.getFirst());
    }

    /**
     * Cambia únicamente la contraseña del comprador de la sesión, sin pedir la anterior ni códigos.
     */
    @Transactional
    public Map<String, Object> changePassword(String password, HttpServletRequest request) {
        var user = require(request);
        if (password == null
                || password.isBlank()
                || password.length() < 4
                || password.length() > 128)
            throw new IllegalArgumentException(
                    "La contraseña debe tener entre 4 y 128 caracteres.");
        long id = ((Number) user.get("id")).longValue();
        int changed =
                jdbc.update(
                        "UPDATE compradores_tienda SET password_hash=? WHERE comercio_id=? AND id=? AND activo=TRUE AND SHA2(password_hash,256)=?",
                        passwords.hash(password),
                        storeId,
                        id,
                        request.getSession(false).getAttribute("alonShopperCredential"));
        if (changed != 1)
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Ingresá a tu cuenta para cambiar la contraseña.");
        return start(account(id), request);
    }

    /**
     * Obtiene una huella interna de la contraseña para revocar otras sesiones cuando se reemplaza.
     */
    private String credentialVersion(long id) {
        return jdbc
                .query(
                        "SELECT SHA2(password_hash,256) FROM compradores_tienda WHERE comercio_id=? AND id=? AND activo=TRUE",
                        (rs, row) -> rs.getString(1),
                        storeId,
                        id)
                .stream()
                .findFirst()
                .orElse("");
    }

    /**
     * Valida el teléfono y lo normaliza al formato internacional con signo más y entre diez y
     * quince dígitos.
     */
    public static String normalizePhone(String input) {
        if (input == null || !input.matches("[+0-9 ()-]{8,25}"))
            throw new IllegalArgumentException(
                    "Ingresá el código de área y el número de teléfono.");
        String digits = input.replaceAll("[^0-9]", "");
        if (digits.length() < 10 || digits.length() > 15)
            throw new IllegalArgumentException(
                    "Ingresá el código de área y número, por ejemplo 3446236311.");
        return "+" + digits;
    }

    /**
     * Registra al comprador con teléfono normalizado, domicilio y ubicación; deriva su contraseña y
     * devuelve la cuenta creada.
     */
    @Transactional
    public Map<String, Object> register(Registration input) {
        String phone = normalizePhone(input.phone());
        String digits = phone.substring(1);
        if (digits.length() == 10
                && !input.phone().trim().startsWith("+")
                && jdbc.queryForObject(
                                "SELECT COUNT(*) FROM compradores_tienda WHERE comercio_id=? AND telefono IN (?,?,?)",
                                Integer.class,
                                storeId,
                                phone,
                                "+54" + digits,
                                "+549" + digits)
                        > 0)
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Ya existe una cuenta con ese teléfono. Ingresá con tu contraseña.");
        var key = new GeneratedKeyHolder();
        String hash = passwords.hash(input.password());
        jdbc.update(
                c -> {
                    var s =
                            c.prepareStatement(
                                    "INSERT INTO compradores_tienda(comercio_id,nombre,apellido,dni,domicilio,telefono,password_hash,codigo_postal,ciudad,provincia,pais) VALUES(?,?,?,?,?,?,?,?,?,?,?)",
                                    Statement.RETURN_GENERATED_KEYS);
                    s.setLong(1, storeId);
                    s.setString(2, input.firstName().trim());
                    s.setString(3, input.lastName().trim());
                    s.setString(4, input.dni());
                    s.setString(5, input.address().trim());
                    s.setString(6, normalizePhone(input.phone()));
                    s.setString(7, hash);
                    s.setString(8, input.postalCode().trim());
                    s.setString(9, input.city().trim());
                    s.setString(10, input.province().trim());
                    s.setString(11, input.country().trim());
                    return s;
                },
                key);
        return account(Objects.requireNonNull(key.getKey()).longValue());
    }

    /** Comprueba teléfono y contraseña de un comprador activo sin revelar si la cuenta existe. */
    public Map<String, Object> login(Login input) {
        String phone = normalizePhone(input.phone());
        String digits = phone.substring(1);
        boolean local = digits.length() == 10 && !input.phone().trim().startsWith("+");
        var rows =
                jdbc.queryForList(
                        "SELECT id,password_hash FROM compradores_tienda WHERE comercio_id=? AND telefono IN (?,?,?) AND activo=TRUE",
                        storeId,
                        phone,
                        local ? "+54" + digits : phone,
                        local ? "+549" + digits : phone);
        if (rows.isEmpty()) passwords.verify(input.password(), dummyHash);
        var matches =
                rows.stream()
                        .filter(
                                row ->
                                        passwords.verify(
                                                input.password(),
                                                String.valueOf(row.get("password_hash"))))
                        .toList();
        if (matches.size() != 1)
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Teléfono o contraseña incorrectos.");
        return account(((Number) matches.getFirst().get("id")).longValue());
    }

    /**
     * Lee los datos públicos de una cuenta activa del comercio, excluyendo su hash de contraseña.
     */
    private Map<String, Object> account(long id) {
        var rows =
                jdbc.queryForList(
                        "SELECT id,nombre,apellido,dni,domicilio,codigo_postal,ciudad,provincia,pais,telefono,telefono_verificado FROM compradores_tienda WHERE comercio_id=? AND id=? AND activo=TRUE",
                        storeId,
                        id);
        return rows.isEmpty() ? Map.of() : rows.getFirst();
    }

    /**
     * Recupera la cuenta activa vinculada a la sesión de comprador o devuelve un mapa vacío si no
     * hay una válida.
     */
    public Map<String, Object> current(HttpServletRequest request) {
        Object saved =
                request.getSession(false) == null
                        ? null
                        : request.getSession(false).getAttribute("alonShopper");
        if (!(saved instanceof Long id)) return Map.of();
        Object version = request.getSession(false).getAttribute("alonShopperCredential");
        if (!(version instanceof String value)
                || value.isEmpty()
                || !value.equals(credentialVersion(id))) return Map.of();
        return account(id);
    }

    /**
     * Exige una sesión activa de comprador y devuelve su cuenta; rechaza pedidos de usuarios sin
     * identificar.
     */
    public Map<String, Object> require(HttpServletRequest request) {
        var user = current(request);
        if (user.isEmpty())
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Ingresá a tu cuenta para enviar un pedido.");
        return user;
    }

    /**
     * Renueva el identificador de sesión y vincula la cuenta del comprador para evitar reutilizar
     * una sesión anterior.
     */
    public Map<String, Object> start(Map<String, Object> user, HttpServletRequest request) {
        request.getSession(true);
        request.changeSessionId();
        long id = ((Number) user.get("id")).longValue();
        request.getSession().setAttribute("alonShopper", id);
        request.getSession().setAttribute("alonShopperCredential", credentialVersion(id));
        return user;
    }
}
