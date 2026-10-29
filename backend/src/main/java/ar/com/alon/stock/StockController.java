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

    @GetMapping("/demo/stock-movements")
    public List<Map<String, Object>> stockMovements() {
        return queries.stockMovements();
    }
}
