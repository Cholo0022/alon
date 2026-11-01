package ar.com.alon.backup;

import ar.com.alon.security.RequestIdentity;
import java.io.*;
import java.net.URI;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.zip.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class BackupService {
    private final RequestIdentity identity;
    private final String url, username, password, executable;
    private final Path directory, images, key;
    private final java.util.concurrent.atomic.AtomicBoolean running =
            new java.util.concurrent.atomic.AtomicBoolean();

    public BackupService(
            RequestIdentity identity,
            @Value("${spring.datasource.url}") String url,
            @Value("${spring.datasource.username}") String username,
            @Value("${spring.datasource.password}") String password,
            @Value("${alon.backup-directory:./data/backups}") String directory,
            @Value("${alon.images-directory}") String images,
            @Value("${alon.security-key-file}") String key,
            @Value("${alon.mysqldump:}") String executable) {
        this.identity = identity;
        this.url = url;
        this.username = username;
        this.password = password;
        this.directory = Path.of(directory).toAbsolutePath().normalize();
        this.images = Path.of(images);
        this.key = Path.of(key);
        this.executable = executable;
    }

    private void authorize() {
        if (!identity.permits("backup"))
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Tu usuario no tiene permiso de Backup.");
    }

    public record Backup(String name, long size, Instant created) {}

    private Backup describe(Path path) throws IOException {
        return new Backup(
                path.getFileName().toString(),
                Files.size(path),
                Files.getLastModifiedTime(path).toInstant());
    }

    public List<Backup> list() throws IOException {
        authorize();
        if (!Files.exists(directory)) return List.of();
        try (var files = Files.list(directory)) {
            var result = new ArrayList<Backup>();
            for (Path file :
                    files.filter(
                                    p ->
                                            validName(p.getFileName().toString())
                                                    && Files.isRegularFile(
                                                            p, LinkOption.NOFOLLOW_LINKS))
                            .toList()) result.add(describe(file));
            result.sort(Comparator.comparing(Backup::created).reversed());
            return result;
        }
    }

    private boolean validName(String name) {
        return name.matches("alon-backup-[0-9a-f-]{36}\\.zip");
    }

    public Path download(String name) {
        authorize();
        if (!validName(name))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Backup no encontrado.");
        Path file = directory.resolve(name);
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Backup no encontrado.");
        return file;
    }

    private String dumpExecutable() {
        if (!executable.isBlank()) return executable;
        for (String version : List.of("8.4", "8.0")) {
            Path path =
                    Path.of(
                            "C:/Program Files/MySQL/MySQL Server "
                                    + version
                                    + "/bin/mysqldump.exe");
            if (Files.isRegularFile(path)) return path.toString();
        }
        return "mysqldump";
    }

    public Backup create() {
        authorize();
        if (!running.compareAndSet(false, true))
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Ya hay un backup en preparación.");
        Path sql = null, partial = null, errors = null;
        Process process = null;
        try {
            Files.createDirectories(directory);
            sql = Files.createTempFile(directory, "dump-", ".tmp");
            partial = Files.createTempFile(directory, "backup-", ".partial");
            errors = Files.createTempFile(directory, "dump-errors-", ".tmp");
            URI connection = URI.create(url.substring("jdbc:".length()));
            String database = connection.getPath().substring(1);
            if (!database.matches("[A-Za-z0-9_]+"))
                throw new IOException("Unsupported database name");
            var builder =
                    new ProcessBuilder(
                            dumpExecutable(),
                            "--no-defaults",
                            "--host=" + connection.getHost(),
                            "--port=" + (connection.getPort() < 0 ? 3306 : connection.getPort()),
                            "--user=" + username,
                            "--single-transaction",
                            "--quick",
                            "--skip-lock-tables",
                            "--no-tablespaces",
                            "--hex-blob",
                            "--set-gtid-purged=OFF",
                            "--default-character-set=utf8mb4",
                            "--result-file=" + sql,
                            "--databases",
                            database);
            builder.environment().put("MYSQL_PWD", password);
            builder.redirectError(errors.toFile());
            builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
            process = builder.start();
            if (!process.waitFor(10, TimeUnit.MINUTES)) {
                process.destroyForcibly().waitFor();
                throw new IOException("Dump timeout");
            }
            if (process.exitValue() != 0 || Files.size(sql) == 0)
                throw new IOException("Dump failed");
            // Se publica el archivo solamente después de terminar y cerrar el ZIP completo.
            try (var zip = new ZipOutputStream(Files.newOutputStream(partial))) {
                add(zip, sql, "database.sql");
                if (Files.exists(images)) {
                    try (var files = Files.walk(images)) {
                        for (Path file :
                                files.filter(p -> Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS))
                                        .toList())
                            add(
                                    zip,
                                    file,
                                    "article-images/"
                                            + images.relativize(file)
                                                    .toString()
                                                    .replace('\\', '/'));
                    }
                }
                if (Files.exists(key)) add(zip, key, "security/mfa.key");
                zip.putNextEntry(new ZipEntry("LEEME.txt"));
                zip.write(
                        ("Backup completo de Alon · "
                                        + Instant.now()
                                        + "\nContiene toda la base MySQL, imágenes y la clave MFA si existe.\nContiene datos privados y secretos: guardalo en un lugar protegido.\nRestauración: detener Alon, importar database.sql en MySQL y reponer article-images y security/mfa.key en las rutas configuradas. Usar una versión compatible de Alon.\nLas imágenes se copian después del volcado: crear el backup sin cargas ni cambios de imágenes en curso.\nLa configuración de conexión y el código del programa se conservan por separado.\n")
                                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
                zip.closeEntry();
            }
            Path target = directory.resolve("alon-backup-" + UUID.randomUUID() + ".zip");
            Files.move(partial, target, StandardCopyOption.ATOMIC_MOVE);
            return describe(target);
        } catch (InterruptedException e) {
            if (process != null) process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "Se interrumpió la creación del backup.");
        } catch (IOException | IllegalArgumentException e) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "No se pudo completar el backup. Revisá el espacio disponible, mysqldump y los permisos de conexión a MySQL.");
        } finally {
            for (Path file : new Path[] {sql, partial, errors})
                if (file != null)
                    try {
                        Files.deleteIfExists(file);
                    } catch (IOException ignored) {
                    }
            running.set(false);
        }
    }

    private void add(ZipOutputStream zip, Path file, String name) throws IOException {
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Invalid backup file");
        zip.putNextEntry(new ZipEntry(name));
        Files.copy(file, zip);
        zip.closeEntry();
    }
}
