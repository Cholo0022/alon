// Permite comprobar si el servidor de Alon está funcionando.
package ar.com.alon.health;

import java.time.Instant;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {
    @org.springframework.beans.factory.annotation.Value("${alon.instance-id:central}")
    private String instanceId = "central";

    @org.springframework.beans.factory.annotation.Value("${alon.business-slug:}")
    private String businessSlug = "";

    /**
     * Recibe la solicitud y delega su ejecución al servicio. Devuelve un estado básico de
     * disponibilidad de la API con nombre del servicio y hora de respuesta.
     */
    @GetMapping("/api/v1/health")
    public Map<String, String> health(jakarta.servlet.http.HttpServletResponse response) {
        response.setHeader("X-Alon-Instance", instanceId);
        response.setHeader("X-Alon-Business-Slug", businessSlug);
        return Map.of("status", "ok", "service", "alon-api", "time", Instant.now().toString());
    }
}
