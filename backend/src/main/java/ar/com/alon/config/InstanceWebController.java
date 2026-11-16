package ar.com.alon.config;

import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

@RestController
@ConditionalOnProperty(name = "alon.instance-web", havingValue = "true")
public class InstanceWebController {
    private final Path index;
    private final String slug;

    public InstanceWebController(
            @Value("${alon.web-directory:./web}") String directory,
            @Value("${alon.business-slug:}") String slug) {
        index = Path.of(directory).resolve("index.html");
        this.slug = slug;
    }

    @GetMapping(
            value = {"/", "/tienda", "/tienda/"},
            produces = MediaType.TEXT_HTML_VALUE)
    public FileSystemResource index() {
        return new FileSystemResource(index);
    }

    @GetMapping(
            value = {"/{business}", "/tienda/{business}", "/{business}/", "/tienda/{business}/"},
            produces = MediaType.TEXT_HTML_VALUE)
    public FileSystemResource business(@PathVariable String business) {
        if (slug.isBlank() || !slug.equals(business))
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.NOT_FOUND);
        return index();
    }
}
