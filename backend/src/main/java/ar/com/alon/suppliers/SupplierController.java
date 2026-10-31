// Recibe las solicitudes de proveedores y sus cuentas y las deriva al servicio correspondiente.
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

    /**
     * Recibe y conserva las dependencias necesarias para las operaciones y consultas de
     * proveedores.
     */
    public SupplierController(
            SupplierService service, SupplierQueries queries, CommandExecutor commands) {
        this.service = service;
        this.queries = queries;
        this.commands = commands;
    }

    /**
     * Recibe la solicitud y delega su ejecución al servicio. Crea un proveedor con código propio y
     * datos de identificación y contacto normalizados dentro del comercio.
     */
    @PostMapping("/operations/suppliers")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createSupplier(@Valid @RequestBody SupplierInput input) {
        return commands.execute("createSupplier", input, () -> service.createSupplier(input));
    }

    /**
     * Recibe la solicitud y delega su ejecución al servicio. Bloquea y actualiza los datos del
     * proveedor del comercio sin modificar su deuda pendiente.
     */
    @PutMapping("/operations/suppliers/{id}")
    public Map<String, Object> updateSupplier(
            @PathVariable long id, @Valid @RequestBody SupplierInput input) {
        return commands.execute(
                "updateSupplier",
                java.util.List.of(id, input),
                () -> service.updateSupplier(id, input));
    }

    /**
     * Recibe la baja lógica del proveedor y la ejecuta sin borrar su historial, reutilizando la
     * respuesta en reintentos.
     */
    @PostMapping("/operations/suppliers/{id}/deactivate")
    public Map<String, Object> deactivateSupplier(@PathVariable long id) {
        return commands.execute(
                "setSupplierActive",
                java.util.List.of(id, false),
                () -> service.setSupplierActive(id, false));
    }

    /**
     * Recibe la reactivación del proveedor y la ejecuta dentro del comercio con protección contra
     * reintentos.
     */
    @PostMapping("/operations/suppliers/{id}/activate")
    public Map<String, Object> activateSupplier(@PathVariable long id) {
        return commands.execute(
                "setSupplierActive",
                java.util.List.of(id, true),
                () -> service.setSupplierActive(id, true));
    }

    /**
     * Recibe la solicitud y delega su ejecución al servicio. Valida el pago y la caja abierta, lo
     * aplica a las compras con deuda más antiguas y registra recibos, egreso y saldo restante.
     */
    @PostMapping("/operations/suppliers/receipt")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createSupplierReceipt(
            @Valid @RequestBody SupplierReceiptInput input) {
        return commands.execute(
                "createSupplierReceipt", input, () -> service.createSupplierReceipt(input));
    }

    /**
     * Expone proveedores del comercio filtrados por estado, con identificación y saldo pendiente
     * mediante la API, delegando la consulta al repositorio.
     */
    @GetMapping("/alon/suppliers")
    public List<Map<String, Object>> suppliers(
            @RequestParam(defaultValue = "false") boolean inactive) {
        return queries.suppliers(inactive);
    }

    /**
     * Expone proveedores activos con código y nombre para los buscadores mediante la API, delegando
     * la consulta al repositorio.
     */
    @GetMapping("/alon/lookups/suppliers")
    public List<Map<String, Object>> lookupSuppliers() {
        return queries.lookupSuppliers();
    }
}
