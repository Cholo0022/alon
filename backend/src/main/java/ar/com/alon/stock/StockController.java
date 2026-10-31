// Recibe las solicitudes de existencias y movimientos de stock y las deriva al servicio
// correspondiente.
package ar.com.alon.stock;

import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class StockController {
    private final StockQueries queries;

    public StockController(StockQueries queries) {
        this.queries = queries;
    }

    @GetMapping("/alon/stock-movements")
    public List<Map<String, Object>> stockMovements() {
        return queries.stockMovements();
    }
}
