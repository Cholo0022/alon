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

    public CategoryController(
            CategoryService service, CategoryQueries queries, CommandExecutor commands) {
        this.service = service;
        this.queries = queries;
        this.commands = commands;
    }

    @PostMapping("/operations/categories")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createCategory(@Valid @RequestBody CategoryInput input) {
        return commands.execute("createCategory", input, () -> service.createCategory(input));
    }

    @GetMapping("/demo/categories")
    public List<Map<String, Object>> categories() {
        return queries.categories();
    }
}
