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

    /**
     * Recibe y conserva las dependencias necesarias para altas, edición y consultas de artículos.
     */
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

    /**
     * Recibe la solicitud y delega su ejecución al servicio. Valida código, nombre, rubro,
     * proveedor y precios; crea el artículo con sus fotos y registra el stock inicial.
     */
    @PostMapping({"/operations/articles", "/operations/purchases/articles"})
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

    @GetMapping("/alon/articles/grouped")
    public List<Map<String, Object>> grouped(
            @RequestParam(defaultValue = "false") boolean inactive,
            @RequestParam(defaultValue = "") String category,
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {
        return queries.grouped(inactive, category, q, page, size);
    }

    @GetMapping("/alon/articles/{id}/group")
    public Map<String, Object> group(@PathVariable long id) {
        return queries.group(id);
    }

    @PutMapping("/operations/articles/{id}/group")
    public Map<String, Object> updateGroup(
            @PathVariable long id,
            @Valid @RequestBody ar.com.alon.articles.dto.ApparelGroupUpdateInput input) {
        return commands.execute(
                "updateApparelGroup",
                List.of(id, input),
                () -> {
                    var items = service.updateApparelGroup(id, input);
                    if (input.common() != null && input.common().photos() != null) {
                        photos.replace(id, input.common().photos());
                        for (var item : items)
                            if (((Number) item.get("id")).longValue() != id)
                                service.copyApparelPhotos(
                                        id, ((Number) item.get("id")).longValue());
                    }
                    return Map.of("items", items);
                });
    }

    @GetMapping("/alon/articles/{id}/variants")
    public List<Map<String, Object>> variants(@PathVariable long id) {
        return queries.variants(id);
    }

    @PostMapping("/operations/articles/{id}/variants")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> addApparelVariants(
            @PathVariable long id,
            @Valid @RequestBody ar.com.alon.articles.dto.AddApparelVariantsInput input) {
        return commands.execute(
                "addApparelVariants",
                List.of(id, input),
                () -> Map.of("items", service.addApparelVariants(id, input)));
    }

    @PostMapping("/operations/articles/apparel")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createCatalogApparel(
            @Valid @RequestBody ar.com.alon.articles.dto.PurchaseApparelInput input) {
        return commands.execute(
                "createCatalogApparel",
                input,
                () -> {
                    var items = service.createCatalogApparel(input);
                    for (var item : items)
                        photos.replace(
                                ((Number) item.get("id")).longValue(), input.product().photos());
                    return Map.of("items", items);
                });
    }

    @PostMapping("/operations/purchases/apparel")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createPurchaseApparel(
            @Valid @RequestBody ar.com.alon.articles.dto.PurchaseApparelInput input) {
        return commands.execute(
                "createPurchaseApparel",
                input,
                () -> {
                    var items = service.createPurchaseApparel(input);
                    for (var item : items)
                        photos.replace(
                                ((Number) item.get("id")).longValue(), input.product().photos());
                    return Map.of("items", items);
                });
    }

    /**
     * Recibe la solicitud y delega su ejecución al servicio. Bloquea el artículo del comercio,
     * valida sus nuevos datos y actualiza precios, fotos y stock dejando trazabilidad de los
     * ajustes.
     */
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

    /**
     * Recibe la baja lógica del artículo y la ejecuta sin borrar su historial, reutilizando la
     * respuesta en reintentos.
     */
    @PostMapping("/operations/articles/{id}/deactivate")
    public Map<String, Object> deactivateArticle(@PathVariable long id) {
        return commands.execute(
                "setArticleActive",
                java.util.List.of(id, false),
                () -> service.setArticleActive(id, false));
    }

    /**
     * Recibe la reactivación del artículo y la ejecuta dentro del comercio con protección contra
     * reintentos.
     */
    @PostMapping("/operations/articles/{id}/activate")
    public Map<String, Object> activateArticle(@PathVariable long id) {
        return commands.execute(
                "setArticleActive",
                java.util.List.of(id, true),
                () -> service.setArticleActive(id, true));
    }

    /**
     * Expone artículos del comercio, con búsqueda, paginación y datos visibles según los permisos
     * mediante la API, delegando la consulta al repositorio.
     */
    @GetMapping("/alon/articles")
    public List<Map<String, Object>> articles(
            HttpServletRequest request,
            @RequestParam(defaultValue = "false") boolean inactive,
            @RequestParam(defaultValue = "") String category,
            @RequestParam(defaultValue = "") String kind,
            @RequestParam(defaultValue = "") String code) {
        return queries.articles(
                (String) request.getAttribute("alonRole"), inactive, category, kind, code);
    }
}
