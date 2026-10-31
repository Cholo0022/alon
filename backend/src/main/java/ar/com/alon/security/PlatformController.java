package ar.com.alon.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class PlatformController {
    private final PlatformAdministration platform;
    private final LoginThrottle throttle;

    /** Conecta el panel exclusivo de alon y la activación exclusiva del superusuario. */
    public PlatformController(PlatformAdministration platform, LoginThrottle throttle) {
        this.platform = platform;
        this.throttle = throttle;
    }

    /** Consulta gerentes y licencias sin secretos guardados. */
    @GetMapping("/super/dashboard")
    public Map<String, Object> dashboard() {
        return platform.dashboard();
    }

    /** Devuelve una contraseña fuerte para el alta o cambio de acceso del gerente. */
    @PostMapping("/super/password")
    public Map<String, String> password() {
        return platform.password();
    }

    /** Crea el gerente y devuelve una sola vez la clave de licencia recién emitida. */
    @PostMapping("/super/managers")
    public Map<String, Object> create(
            @Valid @RequestBody PlatformAdministration.ManagerInput input) {
        return platform.create(input);
    }

    /** Actualiza correo, contraseña opcional y permisos del gerente seleccionado. */
    @PutMapping("/super/managers/{id}")
    public Map<String, Object> update(
            @PathVariable long id, @Valid @RequestBody PlatformAdministration.ManagerUpdate input) {
        platform.update(id, input);
        return Map.of("ok", true);
    }

    /** Emite la próxima renovación sin sumar meses hasta que el superusuario la active. */
    @PostMapping("/super/licenses")
    public Map<String, Object> issue(@Valid @RequestBody Months input) {
        return platform.issue(input.months());
    }

    /** Limita intentos y aplica una clave de licencia de un solo uso al negocio autenticado. */
    @PostMapping("/super/licenses/activate")
    public Map<String, Object> activate(
            @Valid @RequestBody Activation input, HttpServletRequest request) {
        throttle.check("license:" + request.getRemoteAddr());
        return platform.activate(input.key());
    }

    public record Months(@Min(1) @Max(120) int months) {}

    public record Activation(@NotBlank @Size(max = 80) String key) {}
}
