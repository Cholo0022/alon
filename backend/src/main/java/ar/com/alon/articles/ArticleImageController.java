// Recibe las solicitudes de artículos y fotos y las deriva al servicio correspondiente.
package ar.com.alon.articles;

import ar.com.alon.articles.dto.ArticlePhotoInput;
import ar.com.alon.shared.commands.CommandExecutor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.io.IOException;
import java.util.*;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1")
public class ArticleImageController {
    private final JdbcTemplate jdbc;
    private final ArticlePhotoService photos;
    private final ArticleImageStorage storage;
    private final CommandExecutor commands;

    /** Recibe y conserva las dependencias necesarias para galerías y archivos de imágenes. */
    public ArticleImageController(
            JdbcTemplate jdbc,
            ArticlePhotoService photos,
            ArticleImageStorage storage,
            CommandExecutor commands) {
        this.jdbc = jdbc;
        this.photos = photos;
        this.storage = storage;
        this.commands = commands;
    }

    public record Upload(@NotBlank @Size(max = 7000000) String data, boolean illustrative) {}

    public record Gallery(@NotNull @Size(max = 4) List<@NotNull @Valid ArticlePhotoInput> photos) {}

    /**
     * Recibe una portada y reemplaza la galería mediante una operación protegida contra reintentos
     * duplicados.
     */
    @PostMapping("/operations/articles/{id}/image")
    public Map<String, Object> upload(@PathVariable long id, @Valid @RequestBody Upload input) {
        return commands.execute(
                "articleCover",
                List.of(id, input),
                () -> {
                    var gallery =
                            photos.replace(
                                    id,
                                    List.of(
                                            new ArticlePhotoInput(
                                                    null, input.data(), input.illustrative())));
                    return gallery.getFirst();
                });
    }

    /** Expone la galería del artículo después de validar su acceso en el servicio. */
    @GetMapping("/operations/articles/{id}/images")
    public List<Map<String, Object>> list(@PathVariable long id) {
        return photos.list(id);
    }

    /**
     * Recibe la galería completa y solicita su reemplazo con una clave de operación para evitar
     * duplicados.
     */
    @PutMapping("/operations/articles/{id}/images")
    public Map<String, Object> replace(@PathVariable long id, @Valid @RequestBody Gallery input) {
        return commands.execute(
                "articlePhotos",
                List.of(id, input),
                () -> Map.of("photos", photos.replace(id, input.photos())));
    }

    /** Sirve un archivo PNG solo si la clave es válida y sigue vinculada a un artículo activo. */
    @GetMapping("/shop/images/{key}")
    public ResponseEntity<byte[]> image(@PathVariable String key) throws IOException {
        if (!key.matches("[a-f0-9-]{36}\\.png"))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        Long count =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM articulo_imagenes i JOIN articulos a ON a.id=i.articulo_id AND a.comercio_id=i.comercio_id WHERE i.clave=? AND a.estado='ACTIVO'",
                        Long.class,
                        key);
        if (count == null || count == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(storage.read(key));
    }
}
