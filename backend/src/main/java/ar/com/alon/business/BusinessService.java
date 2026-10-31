// Guarda la identidad del negocio para compartirla entre Alon y la tienda web.
package ar.com.alon.business;

import ar.com.alon.security.RequestIdentity;
import ar.com.alon.shared.audit.AuditLog;
import jakarta.validation.constraints.*;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class BusinessService {
    private final JdbcTemplate jdbc;
    private final RequestIdentity identity;
    private final AuditLog audit;

    /**
     * Recibe y conserva las dependencias necesarias para la lectura y modificación de identidad del
     * comercio.
     */
    public BusinessService(JdbcTemplate jdbc, RequestIdentity identity, AuditLog audit) {
        this.jdbc = jdbc;
        this.identity = identity;
        this.audit = audit;
    }

    public record Input(
            @NotBlank @Size(max = 150) String name,
            @NotNull @Size(max = 180) String slogan,
            @NotBlank @Pattern(regexp = "#[0-9a-fA-F]{6}") String primaryColor,
            @NotBlank @Pattern(regexp = "#[0-9a-fA-F]{6}") String secondaryColor,
            @NotBlank @Pattern(regexp = "#[0-9a-fA-F]{6}") String backgroundColor) {}

    /** Consulta nombre, slogan y colores del comercio autenticado. */
    public Map<String, Object> current() {
        return read(identity.storeId());
    }

    /** Lee la identidad visual del comercio indicado para gestión o para la tienda pública. */
    public Map<String, Object> read(long storeId) {
        return jdbc.queryForMap(
                "SELECT nombre,slogan,color_principal,color_secundario,color_fondo FROM comercios WHERE id=?",
                storeId);
    }

    /**
     * Exige rol dueño, bloquea el comercio y actualiza su identidad y colores dejando auditoría de
     * los valores anteriores y nuevos.
     */
    @Transactional
    public Map<String, Object> update(Input input) {
        if (!"DUENO".equals(identity.user().get("rol")))
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Solo el dueño puede modificar el negocio.");
        jdbc.queryForObject(
                "SELECT id FROM comercios WHERE id=? FOR UPDATE", Long.class, identity.storeId());
        var before = current();
        jdbc.update(
                "UPDATE comercios SET nombre=?,slogan=?,color_principal=?,color_secundario=?,color_fondo=? WHERE id=?",
                input.name().trim(),
                input.slogan().trim(),
                input.primaryColor().toLowerCase(java.util.Locale.ROOT),
                input.secondaryColor().toLowerCase(java.util.Locale.ROOT),
                input.backgroundColor().toLowerCase(java.util.Locale.ROOT),
                identity.storeId());
        var after = current();
        audit.record("comercio", identity.storeId(), "MODIFICACION", before, after);
        return after;
    }
}
