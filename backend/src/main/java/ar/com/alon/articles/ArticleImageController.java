package ar.com.alon.articles;

import ar.com.alon.security.RequestIdentity;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Only re-encoded raster bytes are served; browser filenames never determine paths. */
@RestController
@RequestMapping("/api/v1")
public class ArticleImageController {
    private final JdbcTemplate jdbc;
    private final RequestIdentity identity;
    private final Path directory;

    public ArticleImageController(
            JdbcTemplate jdbc,
            RequestIdentity identity,
            @Value("${alon.images-directory:./data/article-images}") String directory) {
        this.jdbc = jdbc;
        this.identity = identity;
        this.directory = Path.of(directory).toAbsolutePath().normalize();
    }

    public record Upload(@NotBlank @Size(max = 7000000) String data, boolean illustrative) {}

    @PostMapping("/operations/articles/{id}/image")
    @Transactional
    public Map<String, Object> upload(@PathVariable long id, @Valid @RequestBody Upload input)
            throws IOException {
        identity.require("articulos");
        jdbc.queryForObject(
                "SELECT id FROM comercios WHERE id=? FOR UPDATE", Long.class, identity.storeId());
        jdbc.queryForObject(
                "SELECT id FROM articulos WHERE comercio_id=? AND id=? FOR UPDATE",
                Long.class,
                identity.storeId(),
                id);
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(input.data());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("La imagen no es válida.");
        }
        if (bytes.length > 5 * 1024 * 1024)
            throw new IllegalArgumentException("La imagen debe pesar hasta 5 MB.");
        BufferedImage image;
        try (var stream = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) throw new IllegalArgumentException("Usá una imagen PNG o JPG.");
            var reader = readers.next();
            try {
                if (!Set.of("JPEG", "PNG")
                        .contains(reader.getFormatName().toUpperCase(Locale.ROOT)))
                    throw new IllegalArgumentException("Usá una imagen PNG o JPG.");
                reader.setInput(stream);
                int w = reader.getWidth(0), h = reader.getHeight(0);
                if (w < 1 || h < 1 || (long) w * h > 16000000)
                    throw new IllegalArgumentException("La imagen admite hasta 16 megapíxeles.");
                image = reader.read(0);
            } finally {
                reader.dispose();
            }
        }
        Files.createDirectories(directory);
        String key = UUID.randomUUID() + ".png";
        Path file = directory.resolve(key);
        try (var out = Files.newOutputStream(file, StandardOpenOption.CREATE_NEW)) {
            if (!ImageIO.write(image, "png", out))
                throw new IOException("No se pudo guardar la imagen.");
        }
        // Register cleanup on rollback, including failures during transaction commit.
        org.springframework.transaction.support.TransactionSynchronizationManager
                .registerSynchronization(
                        new org.springframework.transaction.support.TransactionSynchronization() {
                            @Override
                            public void afterCompletion(int status) {
                                if (status != STATUS_COMMITTED)
                                    try {
                                        Files.deleteIfExists(file);
                                    } catch (IOException e) {
                                        org.slf4j.LoggerFactory.getLogger(
                                                        ArticleImageController.class)
                                                .warn(
                                                        "No se pudo retirar una imagen sin referencia",
                                                        e);
                                    }
                            }
                        });
        jdbc.update(
                "UPDATE articulos SET imagen_clave=?,imagen_ilustrativa=? WHERE comercio_id=? AND id=?",
                key,
                input.illustrative(),
                identity.storeId(),
                id);
        return Map.of("key", key, "illustrative", input.illustrative());
    }

    @GetMapping("/shop/images/{key}")
    public ResponseEntity<byte[]> image(@PathVariable String key) throws IOException {
        if (!key.matches("[a-f0-9-]{36}\\.png"))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        Long references =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM articulos WHERE imagen_clave=? AND estado='ACTIVO'",
                        Long.class,
                        key);
        Path file = directory.resolve(key);
        if (references == null || references == 0 || !Files.isRegularFile(file))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(Files.readAllBytes(file));
    }
}
