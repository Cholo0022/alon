// Recibe las solicitudes de ventas y las deriva al servicio correspondiente.
package ar.com.alon.sales;

import ar.com.alon.sales.dto.SaleInput;
import ar.com.alon.shared.commands.CommandExecutor;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class SalesController {
    private final SalesService service;
    private final SalesQueries queries;
    private final CommandExecutor commands;

    /** Recibe y conserva las dependencias necesarias para las operaciones y consultas de ventas. */
    public SalesController(SalesService service, SalesQueries queries, CommandExecutor commands) {
        this.service = service;
        this.queries = queries;
        this.commands = commands;
    }

    /**
     * Recibe la solicitud y delega su ejecución al servicio. Valida artículos, cantidades,
     * descuentos y stock; registra la venta y sus movimientos de stock y caja o deuda dentro de una
     * transacción.
     */
    @PostMapping("/operations/sales")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createSale(@Valid @RequestBody SaleInput input) {
        return commands.execute("createSale", input, () -> service.createSale(input));
    }

    /**
     * Recibe la solicitud y delega su ejecución al servicio. Revierte una venta confirmada, repone
     * stock y contrapartidas de cobro o deuda y audita la anulación sin borrar el comprobante.
     */
    @PostMapping("/operations/sales/{id}/cancel")
    public Map<String, Object> cancelSale(@PathVariable long id) {
        return commands.execute("cancelSale", java.util.List.of(id), () -> service.cancelSale(id));
    }

    /**
     * Expone ventas del comercio con cliente, importes y estado mediante la API, delegando la
     * consulta al repositorio.
     */
    @GetMapping("/alon/sales")
    public List<Map<String, Object>> sales(
            @RequestParam(required = false) LocalDate date,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {
        return queries.sales(date, page, size);
    }
}
