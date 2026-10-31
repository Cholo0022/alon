// Recibe las solicitudes de compras y costos y las deriva al servicio correspondiente.
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

    /**
     * Recibe y conserva las dependencias necesarias para las operaciones y consultas de compras.
     */
    public PurchaseController(
            PurchaseService service, PurchaseQueries queries, CommandExecutor commands) {
        this.service = service;
        this.queries = queries;
        this.commands = commands;
    }

    /**
     * Recibe la solicitud y delega su ejecución al servicio. Devuelve el proveedor activo sin
     * nombre del comercio o lo crea bajo bloqueo para evitar duplicados concurrentes.
     */
    @PostMapping("/operations/purchases/default-supplier")
    public Map<String, Object> defaultSupplier() {
        return service.defaultSupplier();
    }

    /**
     * Recibe la solicitud y delega su ejecución al servicio. Registra cabecera, renglones, stock y
     * deuda o pago de la compra. La decisión explícita de costo modifica solo costo o conserva
     * precios; las solicitudes anteriores mantienen el cálculo histórico.
     */
    @PostMapping("/operations/purchases")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createPurchase(@Valid @RequestBody PurchaseInput input) {
        return commands.execute("createPurchase", input, () -> service.createPurchase(input));
    }

    /**
     * Recibe la solicitud y delega su ejecución al servicio. Corrige una compra reconstruyendo
     * cantidades y costo ponderado, ajusta saldo del proveedor y audita; rechaza cambios
     * incompatibles con pagos o salidas posteriores.
     */
    @PutMapping("/operations/purchases/{id}")
    public Map<String, Object> updatePurchase(
            @PathVariable long id, @Valid @RequestBody PurchaseInput input) {
        return commands.execute(
                "updatePurchase",
                java.util.List.of(id, input),
                () -> service.updatePurchase(id, input));
    }

    /**
     * Expone compras del comercio con proveedor, totales y condición de pago, desde el registro más
     * reciente mediante la API, delegando la consulta al repositorio.
     */
    @GetMapping("/alon/purchases")
    public List<Map<String, Object>> purchases() {
        return queries.purchases();
    }

    /**
     * Expone cabecera y renglones de una compra perteneciente al comercio mediante la API,
     * delegando la consulta al repositorio.
     */
    @GetMapping("/alon/purchases/{id}")
    public Map<String, Object> purchaseDetails(@PathVariable long id) {
        return queries.purchaseDetails(id);
    }
}
