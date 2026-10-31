// Recibe las solicitudes de resumen del negocio y las deriva al servicio correspondiente.
package ar.com.alon.dashboard;

import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class DashboardController {
    private final DashboardQueries queries;

    /** Recibe y conserva las dependencias necesarias para el resumen del panel inicial. */
    public DashboardController(DashboardQueries queries) {
        this.queries = queries;
    }

    /**
     * Expone indicadores de ventas, stock, caja y cuentas del panel inicial del comercio mediante
     * la API, delegando la consulta al repositorio.
     */
    @GetMapping("/alon/summary")
    public Map<String, Object> summary() {
        return queries.summary();
    }
}
