package ar.com.alon.storefront;

import ar.com.alon.shared.commands.CommandExecutor;
import ar.com.alon.storefront.dto.StoreOrderInput;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class StoreOrderController {
    private final StoreOrderService service;
    private final StoreOrderQueries queries;
    private final CommandExecutor commands;

    public StoreOrderController(
            StoreOrderService service, StoreOrderQueries queries, CommandExecutor commands) {
        this.service = service;
        this.queries = queries;
        this.commands = commands;
    }

    @PostMapping("/operations/store-orders")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createStoreOrder(@Valid @RequestBody StoreOrderInput input) {
        return commands.execute("createStoreOrder", input, () -> service.createStoreOrder(input));
    }

    @GetMapping("/demo/store-orders")
    public List<Map<String, Object>> storeOrders() {
        return queries.storeOrders();
    }
}
