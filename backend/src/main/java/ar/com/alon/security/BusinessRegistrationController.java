package ar.com.alon.security;

import ar.com.alon.users.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Una confirmación crea ficha, gerente, base e instalación; los reintentos usan la misma ficha. */
@RestController
@RequestMapping("/api/v1/super/businesses/confirm")
public class BusinessRegistrationController {
    private final BusinessRegistration registration;
    private final BusinessProvisioning provisioning;
    private final RequestIdentity identity;

    public BusinessRegistrationController(
            BusinessRegistration registration,
            BusinessProvisioning provisioning,
            RequestIdentity identity) {
        this.registration = registration;
        this.provisioning = provisioning;
        this.identity = identity;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> confirm(@Valid @RequestBody Confirmation input) {
        if (!"SUPER".equals(identity.user().get("rol")))
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Solo Alon puede confirmar negocios.");
        if (!Set.of("ACTIVO", "PRUEBA").contains(input.business().estado()))
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Confirmá el nuevo negocio como Activo o Prueba.");
        var manager = input.manager();
        var setup =
                new BusinessProvisioning.Input(
                        manager.username(),
                        manager.name(),
                        manager.password(),
                        manager.email(),
                        manager.months(),
                        new ArrayList<>(UserService.ALL_PERMISSIONS));
        provisioning.validateProvision(setup);
        long id;
        try {
            id = registration.reserve(input);
        } catch (DuplicateKeyException duplicate) {
            Long existing = registration.find(input);
            if (existing == null) throw duplicate;
            id = existing;
        }
        try {
            var result = new HashMap<String, Object>(provisioning.provision(id, setup));
            result.put("id", id);
            if (!Boolean.TRUE.equals(result.get("running")))
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "La instalación todavía no confirmó su inicio. Reintentá la confirmación.");
            return ResponseEntity.ok(result);
        } catch (ResponseStatusException failure) {
            return ResponseEntity.status(failure.getStatusCode())
                    .body(
                            Map.of(
                                    "id",
                                    id,
                                    "registered",
                                    true,
                                    "running",
                                    false,
                                    "error",
                                    "La ficha del negocio quedó guardada, pero el alta no está completa. "
                                            + failure.getReason()
                                            + " Reintentá con los mismos datos; no se duplicará el negocio."));
        }
    }

    public record Confirmation(
            @NotBlank
                    @Pattern(
                            regexp =
                                    "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
                    String requestId,
            @NotNull @Valid PlatformBusinesses.Business business,
            @NotNull @Valid Manager manager) {}

    public record Manager(
            @NotBlank @Size(min = 3, max = 80) String username,
            @NotBlank @Size(max = 160) String name,
            @NotBlank @Size(min = 4, max = 128) String password,
            @NotBlank @Email @Size(max = 254) String email,
            @Min(1) @Max(120) int months) {}
}
