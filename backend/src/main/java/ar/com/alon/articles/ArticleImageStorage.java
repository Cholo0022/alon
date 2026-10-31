// Guarda y recupera fotos de artículos usando ubicaciones controladas por Alon.
package ar.com.alon.articles;

import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class ArticleImageStorage {
    private final Path directory;

    public ArticleImageStorage(
            @Value("${alon.images-directory:./data/article-images}") String directory) {
        this.directory = Path.of(directory).toAbsolutePath().normalize();
    }

    public String write(String data) {
        if (!TransactionSynchronizationManager.isSynchronizationActive())
            throw new IllegalStateException(
                    "La imagen debe guardarse dentro de una operación del artículo.");
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(data);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("La imagen no es válida.");
        }
        if (bytes.length > 5 * 1024 * 1024)
            throw new IllegalArgumentException("Cada imagen debe pesar hasta 5 MB.");
        BufferedImage image;
        try (var stream = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) throw new IllegalArgumentException("Usá imágenes PNG o JPG.");
            var reader = readers.next();
            try {
                if (!Set.of("JPEG", "PNG")
                        .contains(reader.getFormatName().toUpperCase(Locale.ROOT)))
                    throw new IllegalArgumentException("Usá imágenes PNG o JPG.");
                reader.setInput(stream);
                int w = reader.getWidth(0), h = reader.getHeight(0);
                if (w < 1 || h < 1 || (long) w * h > 16000000)
                    throw new IllegalArgumentException("Cada imagen admite hasta 16 megapíxeles.");
                image = reader.read(0);
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            throw new IllegalArgumentException(
                    "No se pudo leer la imagen. Usá un archivo PNG o JPG válido.");
        }
        String key = UUID.randomUUID() + ".png";
        Path file = directory.resolve(key);
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        if (status != STATUS_COMMITTED)
                            try {
                                Files.deleteIfExists(file);
                            } catch (IOException e) {
                                org.slf4j.LoggerFactory.getLogger(ArticleImageStorage.class)
                                        .warn("No se pudo retirar una imagen sin referencia", e);
                            }
                    }
                });
        try {
            Files.createDirectories(directory);
            try (var out = Files.newOutputStream(file, StandardOpenOption.CREATE_NEW)) {
                if (!ImageIO.write(image, "png", out))
                    throw new IOException("No se pudo codificar la imagen.");
            }
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo guardar la imagen.", e);
        }
        return key;
    }

    public byte[] read(String key) throws IOException {
        if (!key.matches("[a-f0-9-]{36}\\.png") || !Files.isRegularFile(directory.resolve(key)))
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.NOT_FOUND);
        return Files.readAllBytes(directory.resolve(key));
    }
}
