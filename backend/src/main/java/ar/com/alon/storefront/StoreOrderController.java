// Recibe las solicitudes de pedidos y compradores de la tienda web y las deriva al servicio
// correspondiente.
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

    /**
     * Recibe y conserva las dependencias necesarias para la revisión y confirmación de pedidos web.
     */
    public StoreOrderController(
            StoreOrderService service, StoreOrderQueries queries, CommandExecutor commands) {
        this.service = service;
        this.queries = queries;
        this.commands = commands;
    }

    /**
     * Recibe la solicitud y delega su ejecución al servicio. Guarda un pedido ingresado desde
     * gestión con datos de cliente y total estimado, pendiente de confirmación.
     */
    @PostMapping("/operations/store-orders")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createStoreOrder(@Valid @RequestBody StoreOrderInput input) {
        return commands.execute("createStoreOrder", input, () -> service.createStoreOrder(input));
    }

    /**
     * Expone pedidos web del comercio con estado, datos de cliente y total mediante la API,
     * delegando la consulta al repositorio.
     */
    @GetMapping("/alon/store-orders")
    public List<Map<String, Object>> storeOrders() {
        return queries.storeOrders();
    }

    /**
     * Recibe la solicitud y delega su ejecución al servicio. Devuelve cabecera y renglones del
     * pedido del comercio, junto con disponibilidad y estado de caja para revisarlo.
     */
    @GetMapping("/alon/store-orders/{id}")
    public Map<String, Object> detail(@PathVariable long id) {
        return service.detail(id);
    }

    /**
     * Expone cantidad total de pedidos pendientes de confirmación, sin limitarla al tamaño de una
     * página mediante la API, delegando la consulta al repositorio.
     */
    @GetMapping("/alon/store-orders/pending-count")
    public Map<String, Long> pendingCount() {
        return Map.of("count", queries.pendingCount());
    }

    /**
     * Recibe la solicitud y delega su ejecución al servicio. Bloquea un pedido pendiente, excluye
     * el renglón indicado, recalcula su total y audita el cambio.
     */
    @PostMapping("/operations/store-orders/{id}/lines/{lineId}/exclude")
    public Map<String, Object> exclude(@PathVariable long id, @PathVariable long lineId) {
        return commands.execute(
                "excludeStoreLine", List.of(id, lineId), () -> service.exclude(id, lineId));
    }

    /**
     * Recibe la solicitud y delega su ejecución al servicio. Exige pedido pendiente, medio válido y
     * caja abierta; crea la venta vinculada con los renglones incluidos y conserva sus precios
     * acordados.
     */
    @PostMapping("/operations/store-orders/{id}/approve")
    public Map<String, Object> approve(@PathVariable long id, @Valid @RequestBody Approval input) {
        return commands.execute(
                "approveStoreOrder",
                List.of(id, input),
                () -> service.approve(id, input.paymentMethod()));
    }

    public record Approval(
            @jakarta.validation.constraints.NotBlank
                    @jakarta.validation.constraints.Pattern(
                            regexp = "EFECTIVO|TRANSFERENCIA|MERCADO_PAGO")
                    String paymentMethod) {}

    /**
     * Recibe la solicitud y delega su ejecución al servicio. Bloquea el pedido pendiente y lo marca
     * cancelado con auditoría, sin generar una venta.
     */
    @PostMapping("/operations/store-orders/{id}/cancel")
    public Map<String, Object> cancel(@PathVariable long id) {
        return commands.execute("cancelStoreOrder", id, () -> service.cancel(id));
    }
}
