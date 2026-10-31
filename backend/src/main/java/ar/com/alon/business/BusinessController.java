// Recibe las solicitudes de nombre, slogan y colores del negocio y las deriva al servicio
// correspondiente.
package ar.com.alon.business;

import ar.com.alon.shared.commands.CommandExecutor;
import ar.com.alon.storefront.ShopAccountService;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class BusinessController {
    private final BusinessService service;
    private final CommandExecutor commands;
    private final ShopAccountService shop;

    /**
     * Recibe y conserva las dependencias necesarias para la identidad del comercio en gestión y
     * tienda.
     */
    public BusinessController(
            BusinessService service, CommandExecutor commands, ShopAccountService shop) {
        this.service = service;
        this.commands = commands;
        this.shop = shop;
    }

    /** Entrega la identidad del comercio de la sesión de gestión. */
    @GetMapping("/business")
    public Map<String, Object> current() {
        return service.current();
    }

    /** Expone la identidad del comercio configurado para la tienda pública. */
    @GetMapping("/shop/business")
    public Map<String, Object> storefront() {
        return service.read(shop.storeId());
    }

    /**
     * Recibe los cambios de identidad y los delega al servicio como una operación protegida contra
     * reintentos.
     */
    @PutMapping("/operations/business")
    public Map<String, Object> update(@Valid @RequestBody BusinessService.Input input) {
        return commands.execute("updateBusiness", input, () -> service.update(input));
    }
}
