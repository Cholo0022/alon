// Recibe las solicitudes de clientes y sus cuentas y las deriva al servicio correspondiente.
package ar.com.alon.customers;

import ar.com.alon.customers.dto.CustomerInput;
import ar.com.alon.shared.commands.CommandExecutor;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class CustomerController {
    private final CustomerService service;
    private final CustomerQueries queries;
    private final CommandExecutor commands;

    /**
     * Recibe y conserva las dependencias necesarias para las operaciones y consultas de clientes.
     */
    public CustomerController(
            CustomerService service, CustomerQueries queries, CommandExecutor commands) {
        this.service = service;
        this.queries = queries;
        this.commands = commands;
    }

    /**
     * Recibe la solicitud y delega su ejecución al servicio. Crea un cliente del comercio con
     * código propio, datos de contacto y límite de crédito validado.
     */
    @PostMapping("/operations/customers")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createCustomer(@Valid @RequestBody CustomerInput input) {
        return commands.execute("createCustomer", input, () -> service.createCustomer(input));
    }

    /**
     * Recibe la solicitud y delega su ejecución al servicio. Bloquea y actualiza los datos y límite
     * de crédito del cliente sin modificar su saldo de cuenta corriente.
     */
    @PutMapping("/operations/customers/{id}")
    public Map<String, Object> updateCustomer(
            @PathVariable long id, @Valid @RequestBody CustomerInput input) {
        return commands.execute(
                "updateCustomer",
                java.util.List.of(id, input),
                () -> service.updateCustomer(id, input));
    }

    /**
     * Recibe la baja lógica del cliente y la ejecuta sin borrar su historial, reutilizando la
     * respuesta en reintentos.
     */
    @PostMapping("/operations/customers/{id}/deactivate")
    public Map<String, Object> deactivateCustomer(@PathVariable long id) {
        return commands.execute(
                "setCustomerActive",
                java.util.List.of(id, false),
                () -> service.setCustomerActive(id, false));
    }

    /**
     * Recibe la reactivación del cliente y la ejecuta dentro del comercio con protección contra
     * reintentos.
     */
    @PostMapping("/operations/customers/{id}/activate")
    public Map<String, Object> activateCustomer(@PathVariable long id) {
        return commands.execute(
                "setCustomerActive",
                java.util.List.of(id, true),
                () -> service.setCustomerActive(id, true));
    }

    /**
     * Expone clientes del comercio filtrados por estado, con sus saldos y límites mediante la API,
     * delegando la consulta al repositorio.
     */
    @GetMapping("/alon/customers")
    public List<Map<String, Object>> customers(
            @RequestParam(defaultValue = "false") boolean inactive) {
        return queries.customers(inactive);
    }

    /**
     * Expone clientes activos con código y nombre para los buscadores mediante la API, delegando la
     * consulta al repositorio.
     */
    @GetMapping("/alon/lookups/customers")
    public List<Map<String, Object>> lookupCustomers() {
        return queries.lookupCustomers();
    }
}
