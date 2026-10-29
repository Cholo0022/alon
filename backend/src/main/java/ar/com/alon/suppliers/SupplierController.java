package ar.com.alon.suppliers;

import ar.com.alon.shared.commands.CommandExecutor;
import ar.com.alon.suppliers.dto.SupplierInput;
import ar.com.alon.suppliers.dto.SupplierReceiptInput;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class SupplierController {
    private final SupplierService service;
    private final SupplierQueries queries;
    private final CommandExecutor commands;

    public SupplierController(
            SupplierService service, SupplierQueries queries, CommandExecutor commands) {
        this.service = service;
        this.queries = queries;
        this.commands = commands;
    }

    @PostMapping("/operations/suppliers")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createSupplier(@Valid @RequestBody SupplierInput input) {
        return commands.execute("createSupplier", input, () -> service.createSupplier(input));
    }

    @PutMapping("/operations/suppliers/{id}")
    public Map<String, Object> updateSupplier(
            @PathVariable long id, @Valid @RequestBody SupplierInput input) {
        return commands.execute(
                "updateSupplier",
                java.util.List.of(id, input),
                () -> service.updateSupplier(id, input));
    }

    @PostMapping("/operations/suppliers/{id}/deactivate")
    public Map<String, Object> deactivateSupplier(@PathVariable long id) {
        return commands.execute(
                "setSupplierActive",
                java.util.List.of(id, false),
                () -> service.setSupplierActive(id, false));
    }

    @PostMapping("/operations/suppliers/{id}/activate")
    public Map<String, Object> activateSupplier(@PathVariable long id) {
        return commands.execute(
                "setSupplierActive",
                java.util.List.of(id, true),
                () -> service.setSupplierActive(id, true));
    }

    @PostMapping("/operations/suppliers/receipt")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createSupplierReceipt(
            @Valid @RequestBody SupplierReceiptInput input) {
        return commands.execute(
                "createSupplierReceipt", input, () -> service.createSupplierReceipt(input));
    }

    @GetMapping("/demo/suppliers")
    public List<Map<String, Object>> suppliers(
            @RequestParam(defaultValue = "false") boolean inactive) {
        return queries.suppliers(inactive);
    }

    @GetMapping("/demo/lookups/suppliers")
    public List<Map<String, Object>> lookupSuppliers() {
        return queries.lookupSuppliers();
    }
}
