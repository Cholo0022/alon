// Recibe las solicitudes de artículos y fotos y las deriva al servicio correspondiente.
package ar.com.alon.articles;

import ar.com.alon.shared.commands.CommandExecutor;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/operations/articles/prices")
public class CategoryPriceController {
    private final CategoryPriceService service;
    private final CommandExecutor commands;

    /**
     * Recibe y conserva las dependencias necesarias para la simulación y aplicación de aumentos por
     * rubro.
     */
    public CategoryPriceController(CategoryPriceService service, CommandExecutor commands) {
        this.service = service;
        this.commands = commands;
    }

    /**
     * Recibe la solicitud y delega su ejecución al servicio. Devuelve cantidad de artículos, hasta
     * diez ejemplos y la huella con la que deberá confirmarse el aumento.
     */
    @PostMapping("/preview")
    public Map<String, Object> preview(@Valid @RequestBody CategoryPriceService.Input input) {
        return service.preview(input);
    }

    /**
     * Recibe la solicitud y delega su ejecución al servicio. Verifica que la simulación siga
     * vigente, actualiza los precios de venta del rubro y audita cada cambio en una transacción.
     */
    @PostMapping
    public Map<String, Object> apply(@Valid @RequestBody CategoryPriceService.Input input) {
        return commands.execute("increaseCategoryPrices", input, () -> service.apply(input));
    }
}
