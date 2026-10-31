// Recibe las solicitudes de rubros y las deriva al servicio correspondiente.
package ar.com.alon.categories;

import ar.com.alon.categories.dto.CategoryInput;
import ar.com.alon.shared.commands.CommandExecutor;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class CategoryController {
    private final CategoryService service;
    private final CategoryQueries queries;
    private final CommandExecutor commands;

    /** Recibe y conserva las dependencias necesarias para el alta y consulta de rubros. */
    public CategoryController(
            CategoryService service, CategoryQueries queries, CommandExecutor commands) {
        this.service = service;
        this.queries = queries;
        this.commands = commands;
    }

    /**
     * Recibe la solicitud y delega su ejecución al servicio. Valida que el nombre de rubro no esté
     * repetido en el comercio y crea el registro con un código propio.
     */
    @PostMapping("/operations/categories")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createCategory(@Valid @RequestBody CategoryInput input) {
        return commands.execute("createCategory", input, () -> service.createCategory(input));
    }

    /**
     * Expone rubros del comercio ordenados por nombre mediante la API, delegando la consulta al
     * repositorio.
     */
    @GetMapping("/alon/categories")
    public List<Map<String, Object>> categories() {
        return queries.categories();
    }
}
