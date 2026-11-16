package ar.com.alon.security;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/super/businesses/{id}/state")
public class BusinessStateController {
    private final BusinessProvisioning provisioning;

    public BusinessStateController(BusinessProvisioning provisioning) {
        this.provisioning = provisioning;
    }

    @PutMapping
    public Map<String, Object> change(@PathVariable long id, @Valid @RequestBody StateInput input) {
        if ("BAJA".equals(input.state()))
            return provisioning.deleteBusiness(id, input.reason(), input.confirmation());
        return provisioning.changeState(id, input.state(), input.reason());
    }

    public record StateInput(
            @NotNull @Pattern(regexp = "PREPARACION|PRUEBA|ACTIVO|SUSPENDIDO|BAJA") String state,
            @NotNull @Size(max = 300) String reason,
            @Size(max = 160) String confirmation) {}
}
