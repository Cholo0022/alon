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

    public CategoryPriceController(CategoryPriceService service, CommandExecutor commands) {
        this.service = service;
        this.commands = commands;
    }

    @PostMapping("/preview")
    public Map<String, Object> preview(@Valid @RequestBody CategoryPriceService.Input input) {
        return service.preview(input);
    }

    @PostMapping
    public Map<String, Object> apply(@Valid @RequestBody CategoryPriceService.Input input) {
        return commands.execute("increaseCategoryPrices", input, () -> service.apply(input));
    }
}
