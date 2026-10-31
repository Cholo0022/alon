// Recibe las solicitudes de caja y movimientos de dinero y las deriva al servicio correspondiente.
package ar.com.alon.cash;

import ar.com.alon.cash.dto.CashCloseInput;
import ar.com.alon.cash.dto.CashOpenInput;
import ar.com.alon.cash.dto.ExpenseInput;
import ar.com.alon.shared.commands.CommandExecutor;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class CashController {
    private final CashService service;
    private final CashQueries queries;
    private final CommandExecutor commands;

    public CashController(CashService service, CashQueries queries, CommandExecutor commands) {
        this.service = service;
        this.queries = queries;
        this.commands = commands;
    }

    @PostMapping("/operations/cash/open")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> openCash(@Valid @RequestBody CashOpenInput input) {
        return commands.execute("openCash", input, () -> service.openCash(input));
    }

    @PostMapping("/operations/cash/expenses")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> addExpense(@Valid @RequestBody ExpenseInput input) {
        return commands.execute("addExpense", input, () -> service.addExpense(input));
    }

    @PostMapping("/operations/cash/close")
    public Map<String, Object> closeCash(@Valid @RequestBody CashCloseInput input) {
        return commands.execute("closeCash", input, () -> service.closeCash(input));
    }

    @GetMapping("/operations/cash/summary")
    public Map<String, Object> cashSummary(@RequestParam LocalDate date) {
        return service.cashSummary(date);
    }

    @GetMapping("/alon/cash/status")
    public Map<String, Object> cashStatus(@RequestParam LocalDate date) {
        return queries.cashStatus(date);
    }

    @GetMapping("/alon/reports/cash")
    public List<Map<String, Object>> cash() {
        return queries.cash();
    }
}
