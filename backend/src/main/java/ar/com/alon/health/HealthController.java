// Permite comprobar si el servidor de Alon está funcionando.
package ar.com.alon.health;

import java.time.Instant;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {
    @GetMapping("/api/v1/health")
    public Map<String, String> health() {
        return Map.of("status", "ok", "service", "alon-api", "time", Instant.now().toString());
    }
}
