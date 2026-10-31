// Recibe las solicitudes de artículos y fotos y las deriva al servicio correspondiente.
package ar.com.alon.articles;

import ar.com.alon.articles.dto.ArticleInput;
import ar.com.alon.shared.commands.CommandExecutor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class ArticleController {
    private final ArticleService service;
    private final ArticleQueries queries;
    private final CommandExecutor commands;
    private final ArticlePhotoService photos;

    public ArticleController(
            ArticleService service,
            ArticleQueries queries,
            CommandExecutor commands,
            ArticlePhotoService photos) {
        this.service = service;
        this.queries = queries;
        this.commands = commands;
        this.photos = photos;
    }

    @PostMapping("/operations/articles")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createArticle(@Valid @RequestBody ArticleInput input) {
        return commands.execute(
                "createArticle",
                input,
                () -> {
                    var article = service.createArticle(input);
                    photos.replace(((Number) article.get("id")).longValue(), input.photos());
                    return article;
                });
    }

    @PutMapping("/operations/articles/{id}")
    public Map<String, Object> updateArticle(
            @PathVariable long id, @Valid @RequestBody ArticleInput input) {
        return commands.execute(
                "updateArticle",
                java.util.List.of(id, input),
                () -> {
                    var article = service.updateArticle(id, input);
                    photos.replace(id, input.photos());
                    return article;
                });
    }

    @PostMapping("/operations/articles/{id}/deactivate")
    public Map<String, Object> deactivateArticle(@PathVariable long id) {
        return commands.execute(
                "setArticleActive",
                java.util.List.of(id, false),
                () -> service.setArticleActive(id, false));
    }

    @PostMapping("/operations/articles/{id}/activate")
    public Map<String, Object> activateArticle(@PathVariable long id) {
        return commands.execute(
                "setArticleActive",
                java.util.List.of(id, true),
                () -> service.setArticleActive(id, true));
    }

    @GetMapping("/alon/articles")
    public List<Map<String, Object>> articles(
            HttpServletRequest request,
            @RequestParam(defaultValue = "false") boolean inactive,
            @RequestParam(defaultValue = "") String category) {
        return queries.articles((String) request.getAttribute("alonRole"), inactive, category);
    }
}
