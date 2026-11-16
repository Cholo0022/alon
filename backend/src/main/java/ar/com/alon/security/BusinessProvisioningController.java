package ar.com.alon.security;

import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/super/businesses/{id}/installation")
public class BusinessProvisioningController {
    private final BusinessProvisioning provisioning;

    public BusinessProvisioningController(BusinessProvisioning provisioning) {
        this.provisioning = provisioning;
    }

    @GetMapping
    public Map<String, Object> status(@PathVariable long id) {
        return provisioning.status(id);
    }

    @PostMapping
    public Map<String, Object> create(
            @PathVariable long id, @Valid @RequestBody BusinessProvisioning.Input input) {
        return provisioning.provision(id, input);
    }

    @PostMapping("/start")
    public Map<String, Object> start(@PathVariable long id) {
        return provisioning.start(id);
    }
}
