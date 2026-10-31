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
            @NotBlank @Pattern(regexp = "#[0-9a-fA-F]{6}") String backgroundColor,
            @Pattern(
                            regexp =
                                    "clasica|minimal|boutique|urbana|editorial|mercado|natural|tecnologica|artesanal|vitrina")
                    String shopTemplate,
            @Size(max = 120) String shopTitle,
            @Size(max = 240) String shopDescription) {
        /**
         * Conserva compatibilidad con los formularios anteriores que solo editaban identidad y
         * colores.
         */
        public Input(
                String name,
                String slogan,
                String primaryColor,
                String secondaryColor,
                String backgroundColor) {
            this(name, slogan, primaryColor, secondaryColor, backgroundColor, null, null, null);
        }
    }

    /** Consulta identidad, colores, plantilla y textos de la tienda del comercio autenticado. */
    public Map<String, Object> current() {
        return read(identity.storeId());
    }

    /** Lee la identidad visual del comercio indicado para gestión o para la tienda pública. */
    public Map<String, Object> read(long storeId) {
        return jdbc.queryForMap(
                "SELECT nombre,slogan,color_principal,color_secundario,color_fondo,plantilla_tienda,titulo_tienda,descripcion_tienda FROM comercios WHERE id=?",
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
        if (input.shopTitle() != null && input.shopTitle().isBlank())
            throw new IllegalArgumentException("El título de bienvenida es obligatorio.");
        if (input.shopDescription() != null && input.shopDescription().isBlank())
            throw new IllegalArgumentException("La descripción de bienvenida es obligatoria.");
        var before = current();
        String template =
                input.shopTemplate() == null
                        ? before.get("plantilla_tienda").toString()
                        : input.shopTemplate();
        if (!java.util.Set.of(
                        "clasica",
                        "minimal",
                        "boutique",
                        "urbana",
                        "editorial",
                        "mercado",
                        "natural",
                        "tecnologica",
                        "artesanal",
                        "vitrina")
                .contains(template))
            throw new IllegalArgumentException("Elegí una de las diez plantillas disponibles.");
        jdbc.update(
                "UPDATE comercios SET nombre=?,slogan=?,color_principal=?,color_secundario=?,color_fondo=?,plantilla_tienda=?,titulo_tienda=?,descripcion_tienda=? WHERE id=?",
                input.name().trim(),
                input.slogan().trim(),
                input.primaryColor().toLowerCase(java.util.Locale.ROOT),
                input.secondaryColor().toLowerCase(java.util.Locale.ROOT),
                input.backgroundColor().toLowerCase(java.util.Locale.ROOT),
                template,
                input.shopTitle() == null ? before.get("titulo_tienda") : input.shopTitle().trim(),
                input.shopDescription() == null
                        ? before.get("descripcion_tienda")
                        : input.shopDescription().trim(),
                identity.storeId());
        var after = current();
        audit.record("comercio", identity.storeId(), "MODIFICACION", before, after);
        return after;
    }
}
