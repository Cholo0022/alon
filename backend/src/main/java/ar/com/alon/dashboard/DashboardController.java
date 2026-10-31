// Recibe las solicitudes de resumen del negocio y las deriva al servicio correspondiente.
package ar.com.alon.dashboard;

import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class DashboardController {
    private final DashboardQueries queries;

    public DashboardController(DashboardQueries queries) {
        this.queries = queries;
    }

    @GetMapping("/alon/summary")
    public Map<String, Object> summary() {
        return queries.summary();
    }
}
