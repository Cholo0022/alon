package ar.com.alon.purchases;

import ar.com.alon.purchases.dto.PurchaseInput;
import ar.com.alon.shared.commands.CommandExecutor;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class PurchaseController {
    private final PurchaseService service;
    private final PurchaseQueries queries;
    private final CommandExecutor commands;

    public PurchaseController(
            PurchaseService service, PurchaseQueries queries, CommandExecutor commands) {
        this.service = service;
        this.queries = queries;
        this.commands = commands;
    }

    @PostMapping("/operations/purchases")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createPurchase(@Valid @RequestBody PurchaseInput input) {
        return commands.execute("createPurchase", input, () -> service.createPurchase(input));
    }

    @PutMapping("/operations/purchases/{id}")
    public Map<String, Object> updatePurchase(
            @PathVariable long id, @Valid @RequestBody PurchaseInput input) {
        return commands.execute(
                "updatePurchase",
                java.util.List.of(id, input),
                () -> service.updatePurchase(id, input));
    }

    @GetMapping("/demo/purchases")
    public List<Map<String, Object>> purchases() {
        return queries.purchases();
    }

    @GetMapping("/demo/purchases/{id}")
    public Map<String, Object> purchaseDetails(@PathVariable long id) {
        return queries.purchaseDetails(id);
    }
}
