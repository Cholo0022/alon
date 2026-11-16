package ar.com.alon.health;

import ar.com.alon.security.BusinessProvisioning;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

/** Disponibilidad agregada sin exponer negocios, usuarios ni credenciales. */
@RestController
public class BusinessReadinessController {
    private final BusinessProvisioning provisioning;

    public BusinessReadinessController(BusinessProvisioning provisioning) {
        this.provisioning = provisioning;
    }

    @GetMapping("/api/v1/health/businesses")
    public Map<String, Object> readiness() {
        return provisioning.readiness();
    }
}
