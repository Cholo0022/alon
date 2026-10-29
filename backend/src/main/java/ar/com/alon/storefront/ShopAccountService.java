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

/** Shopper identities are independent of staff users and never grant management access. */
@Service
public class ShopAccountService {
    private final JdbcTemplate jdbc;
    private final PasswordHasher passwords;
    private final long storeId;
    private final String dummyHash;

    public ShopAccountService(
            JdbcTemplate jdbc,
            PasswordHasher passwords,
            @Value("${alon.bootstrap-store-id:1}") long storeId) {
        this.jdbc = jdbc;
        this.passwords = passwords;
        this.storeId = storeId;
        this.dummyHash = passwords.hash("Alon-unknown-account-password");
    }

    public long storeId() {
        return storeId;
    }

    public record Registration(
            @NotBlank @Size(max = 60) String firstName,
            @NotBlank @Size(max = 60) String lastName,
            @NotBlank @Pattern(regexp = "[0-9]{7,8}") String dni,
            @NotBlank @Size(max = 180) String address,
            @NotBlank @Size(max = 25) String phone,
            @NotBlank @Size(min = 12, max = 128) String password) {}

    public record Login(
            @NotBlank @Size(max = 25) String phone, @NotBlank @Size(max = 128) String password) {}

    public static String normalizePhone(String input) {
        if (input == null || !input.matches("[+0-9 ()-]{8,25}"))
            throw new IllegalArgumentException("Ingresá el teléfono con código de país y área.");
        String digits = input.replaceAll("[^0-9]", "");
        if (digits.length() < 10 || digits.length() > 15)
            throw new IllegalArgumentException(
                    "Ingresá un teléfono de 10 a 15 dígitos, incluido el código de país.");
        return "+" + digits;
    }

    @Transactional
    public Map<String, Object> register(Registration input) {
        var key = new GeneratedKeyHolder();
        String hash = passwords.hash(input.password());
        jdbc.update(
                c -> {
                    var s =
                            c.prepareStatement(
                                    "INSERT INTO compradores_tienda(comercio_id,nombre,apellido,dni,domicilio,telefono,password_hash) VALUES(?,?,?,?,?,?,?)",
                                    Statement.RETURN_GENERATED_KEYS);
                    s.setLong(1, storeId);
                    s.setString(2, input.firstName().trim());
                    s.setString(3, input.lastName().trim());
                    s.setString(4, input.dni());
                    s.setString(5, input.address().trim());
                    s.setString(6, normalizePhone(input.phone()));
                    s.setString(7, hash);
                    return s;
                },
                key);
        return account(Objects.requireNonNull(key.getKey()).longValue());
    }

    public Map<String, Object> login(Login input) {
        var rows =
                jdbc.queryForList(
                        "SELECT id,password_hash FROM compradores_tienda WHERE comercio_id=? AND telefono=? AND activo=TRUE",
                        storeId,
                        normalizePhone(input.phone()));
        // Always perform the password derivation, including for unknown accounts.
        String stored =
                rows.isEmpty() ? dummyHash : String.valueOf(rows.getFirst().get("password_hash"));
        if (!passwords.verify(input.password(), stored) || rows.isEmpty())
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Teléfono o contraseña incorrectos.");
        return account(((Number) rows.getFirst().get("id")).longValue());
    }

    private Map<String, Object> account(long id) {
        var rows =
                jdbc.queryForList(
                        "SELECT id,nombre,apellido,dni,domicilio,telefono,telefono_verificado FROM compradores_tienda WHERE comercio_id=? AND id=? AND activo=TRUE",
                        storeId,
                        id);
        return rows.isEmpty() ? Map.of() : rows.getFirst();
    }

    public Map<String, Object> current(HttpServletRequest request) {
        Object saved =
                request.getSession(false) == null
                        ? null
                        : request.getSession(false).getAttribute("alonShopper");
        return saved instanceof Long id ? account(id) : Map.of();
    }

    public Map<String, Object> require(HttpServletRequest request) {
        var user = current(request);
        if (user.isEmpty())
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Ingresá a tu cuenta para enviar un pedido.");
        return user;
    }

    public Map<String, Object> start(Map<String, Object> user, HttpServletRequest request) {
        request.getSession(true);
        request.changeSessionId();
        request.getSession().setAttribute("alonShopper", ((Number) user.get("id")).longValue());
        return user;
    }
}
