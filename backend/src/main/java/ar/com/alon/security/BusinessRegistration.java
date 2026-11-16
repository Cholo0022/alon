package ar.com.alon.security;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Reserva durable del alta; confirma el registro antes de crear la base independiente. */
@Service
public class BusinessRegistration {
    private final JdbcTemplate jdbc;
    private final PlatformBusinesses registry;
    private final PasswordHasher passwords;
    private final TotpSecurity crypto;

    public BusinessRegistration(
            JdbcTemplate jdbc,
            PlatformBusinesses registry,
            PasswordHasher passwords,
            TotpSecurity crypto) {
        this.jdbc = jdbc;
        this.registry = registry;
        this.passwords = passwords;
        this.crypto = crypto;
    }

    @Transactional
    public long reserve(BusinessRegistrationController.Confirmation input) {
        Long existing = find(input);
        if (existing != null) return existing;
        long id = ((Number) registry.create(input.business()).get("id")).longValue();
        jdbc.update(
                "UPDATE plataforma_negocios SET alta_token=?,alta_gerente=?,alta_huella=? WHERE id=?",
                input.requestId(),
                input.manager().username().trim(),
                passwords.hash(fingerprint(input)),
                id);
        return id;
    }

    public Long find(BusinessRegistrationController.Confirmation input) {
        var found =
                jdbc.queryForList(
                        "SELECT id,nombre,alta_gerente,alta_huella,baja_iniciada_en FROM plataforma_negocios WHERE alta_token=?",
                        input.requestId());
        if (found.isEmpty()) return null;
        Map<String, Object> row = found.getFirst();
        if (!row.get("nombre").equals(input.business().nombre().trim())
                || !row.get("alta_gerente").equals(input.manager().username().trim())
                || !passwords.verify(fingerprint(input), (String) row.get("alta_huella"))
                || row.get("baja_iniciada_en") != null)
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "El alta ya está reservada con sus datos originales. No se aplicaron los cambios ni se cambió la contraseña. Reintentá con los mismos datos o continuá desde la instalación del negocio del listado.");
        return ((Number) row.get("id")).longValue();
    }

    private String fingerprint(BusinessRegistrationController.Confirmation input) {
        // JSON inequívoco; PBKDF2 protege también la contraseña incluida en la huella.
        return crypto.hash(
                tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(input));
    }
}
