package ar.com.alon.backup;

import ar.com.alon.security.RequestIdentity;
import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.security.GeneralSecurityException;
import java.sql.*;
import java.util.*;
import javax.crypto.Cipher;
import javax.crypto.spec.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class BackupRestoreService {
    private final BackupService backups;
    private final RequestIdentity identity;
    private final RestoreGate gate;
    private final String url, user, password;
    private final Path directory, images, key;

    public BackupRestoreService(
            BackupService backups,
            RequestIdentity identity,
            RestoreGate gate,
            @Value("${spring.datasource.url}") String url,
            @Value("${spring.datasource.username}") String user,
            @Value("${spring.datasource.password}") String password,
            @Value("${alon.backup-directory:./data/backups}") String directory,
            @Value("${alon.images-directory}") String images,
            @Value("${alon.security-key-file}") String key) {
        this.backups = backups;
        this.identity = identity;
        this.gate = gate;
        this.url = url;
        this.user = user;
        this.password = password;
        this.directory = Path.of(directory).toAbsolutePath().normalize();
        this.images = Path.of(images).toAbsolutePath().normalize();
        this.key = Path.of(key).toAbsolutePath().normalize();
    }

    public record Result(String safetyBackup, String message) {}

    public Result restore(InputStream upload, String confirmation) {
        if (!identity.permits("backup"))
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Tu usuario no tiene permiso de Backup.");
        if (!"RESTAURAR".equals(confirmation))
            throw new IllegalArgumentException(
                    "Escribí RESTAURAR para confirmar el reemplazo de los datos.");
        gate.begin();
        Path work = null, stagedImages = null, previous = null;
        String safety = null;
        boolean imageInstalled = false, imageSaved = false, keyChanged = false;
        boolean markerWritten = false,
                committed = false,
                commitAttempted = false,
                recoveryRequired = false;
        byte[] oldKey = null;
        Connection connection = null;
        try {
            validateDestinations();
            Files.createDirectories(directory);
            work = Files.createTempDirectory(directory, ".restore-");
            restrict(work, true);
            BackupArchive.extract(upload, work);
            connection = DriverManager.getConnection(url, user, password);
            Map<String, BackupSql.Table> schema = BackupSql.schema(connection);
            BackupSql.validate(work.resolve("database.sql"), schema);
            // Una copia de emergencia debe finalizar antes de tocar cualquier dato existente.
            safety = backups.create().name();
            Files.createDirectories(images.getParent());
            Files.createDirectories(key.getParent());
            stagedImages = Files.createTempDirectory(images.getParent(), ".alon-restore-images-");
            previous = Files.createTempDirectory(images.getParent(), ".alon-before-restore-");
            restrict(previous, true);
            try (var files = Files.list(work.resolve("article-images"))) {
                for (Path file : files.toList())
                    Files.copy(file, stagedImages.resolve(file.getFileName()));
            }
            oldKey = Files.exists(key) ? Files.readAllBytes(key) : null;
            try (var stmt = connection.createStatement()) {
                stmt.execute(
                        "SET SESSION SQL_MODE='STRICT_ALL_TABLES,NO_AUTO_VALUE_ON_ZERO,NO_ENGINE_SUBSTITUTION'");
                stmt.execute("SET SESSION time_zone='+00:00'");
                stmt.execute("SET SESSION FOREIGN_KEY_CHECKS=0");
            }
            connection.setAutoCommit(false);
            for (String table : schema.keySet()) {
                if (table.equals("flyway_schema_history")) continue;
                try (var stmt = connection.createStatement()) {
                    stmt.executeUpdate("DELETE FROM `" + table + "`");
                }
            }
            BackupSql.apply(work.resolve("database.sql"), connection);
            validateReferences(connection, schema.keySet());
            validateRecoveredAccess(connection, work.resolve("security/mfa.key"));
            validateImages(connection, stagedImages);
            markerWritten = true;
            Files.writeString(
                    directory.resolve(RestoreGate.MARKER),
                    "Restauración interrumpida: revisar antes de volver a habilitar Alon.\nCopia previa: "
                            + safety
                            + "\nImágenes anteriores: "
                            + previous
                            + "\nClave anterior dentro de la copia previa.\n",
                    StandardOpenOption.CREATE_NEW);
            markerWritten = true;
            if (Files.exists(images, LinkOption.NOFOLLOW_LINKS)) {
                Files.move(images, previous.resolve("images"), StandardCopyOption.ATOMIC_MOVE);
                imageSaved = true;
            }
            Files.move(stagedImages, images, StandardCopyOption.ATOMIC_MOVE);
            imageInstalled = true;
            if (Files.exists(work.resolve("security/mfa.key"))) {
                keyChanged = true;
                Files.write(key, Files.readAllBytes(work.resolve("security/mfa.key")));
                restrict(key, false);
            }
            commitAttempted = true;
            connection.commit();
            committed = true;
            gate.expireSessions();
            Files.delete(directory.resolve(RestoreGate.MARKER));
            markerWritten = false;
            return new Result(
                    safety,
                    "Backup restaurado. Volvé a ingresar con los usuarios y el autenticador que tenía esta copia. Se conservó una copia de seguridad del estado anterior.");
        } catch (Exception failure) {
            if (committed) {
                // Los datos están confirmados. Un error de limpieza no debe deshacer solo los
                // archivos.
                gate.expireSessions();
                recoveryRequired = true;
            } else {
                boolean rollbackOk = true;
                if (connection != null)
                    try {
                        if (!connection.getAutoCommit()) connection.rollback();
                    } catch (SQLException e) {
                        rollbackOk = false;
                    }
                if (commitAttempted)
                    rollbackOk = false; // Una conexión perdida durante COMMIT deja un resultado
                // incierto.
                try {
                    if (imageInstalled)
                        Files.move(images, stagedImages, StandardCopyOption.ATOMIC_MOVE);
                    if (imageSaved)
                        Files.move(
                                previous.resolve("images"), images, StandardCopyOption.ATOMIC_MOVE);
                    if (keyChanged) {
                        if (oldKey == null) Files.deleteIfExists(key);
                        else {
                            Files.write(key, oldKey);
                            restrict(key, false);
                        }
                    }
                } catch (IOException e) {
                    rollbackOk = false;
                }
                recoveryRequired = !rollbackOk;
                if (!recoveryRequired && markerWritten)
                    try {
                        Files.deleteIfExists(directory.resolve(RestoreGate.MARKER));
                        markerWritten = false;
                    } catch (IOException e) {
                        recoveryRequired = true;
                    }
            }
            if (recoveryRequired) {
                try {
                    Files.createDirectories(directory);
                    if (!Files.exists(directory.resolve(RestoreGate.MARKER)))
                        Files.writeString(
                                directory.resolve(RestoreGate.MARKER),
                                "Se requiere revisar la restauración. Copia previa: " + safety,
                                StandardOpenOption.CREATE_NEW);
                } catch (IOException ignored) {
                    /* El bloqueo en memoria se conserva incluso si no se puede escribir el marcador. */
                }
                gate.requireRecovery();
                throw new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "No se pudo confirmar la recuperación completa. Alon quedó bloqueado para revisión. Copia previa: "
                                + (safety == null ? "no disponible" : safety)
                                + ".");
            }
            if (failure instanceof IllegalArgumentException invalid) throw invalid;
            if (failure instanceof ResponseStatusException controlled) throw controlled;
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "No se pudo restaurar el backup. Los cambios fueron revertidos; se conserva el estado anterior. Revisá que el ZIP esté completo, el espacio disponible y su compatibilidad.");
        } finally {
            if (markerWritten) {
                recoveryRequired = true;
                gate.requireRecovery();
            }
            if (connection != null)
                try {
                    connection.close();
                } catch (SQLException ignored) {
                }
            if (!recoveryRequired) {
                cleanup(work);
                cleanup(stagedImages);
                cleanup(previous);
            }
            gate.end();
        }
    }

    private void validateDestinations() throws IOException {
        if (images.getParent() == null
                || key.getParent() == null
                || Files.isSymbolicLink(images)
                || Files.isSymbolicLink(key)
                || directory.startsWith(images)
                || key.startsWith(images)
                || images.startsWith(directory))
            throw new IOException("Rutas de backup, imágenes y clave incompatibles.");
        if (Files.exists(directory.resolve(RestoreGate.MARKER)))
            throw new IOException("Revisión de recuperación pendiente.");
    }

    private static void cleanup(Path path) {
        try {
            BackupArchive.cleanup(path);
        } catch (IOException ignored) {
            /* Solo residuos temporales; la copia previa no se elimina. */
        }
    }

    private static void restrict(Path path, boolean directory) throws IOException {
        var posix = Files.getFileAttributeView(path, PosixFileAttributeView.class);
        if (posix != null)
            posix.setPermissions(
                    PosixFilePermissions.fromString(directory ? "rwx------" : "rw-------"));
        var acl = Files.getFileAttributeView(path, AclFileAttributeView.class);
        if (acl != null)
            acl.setAcl(
                    List.of(
                            AclEntry.newBuilder()
                                    .setType(AclEntryType.ALLOW)
                                    .setPrincipal(Files.getOwner(path))
                                    .setPermissions(EnumSet.allOf(AclEntryPermission.class))
                                    .build()));
    }

    private static void validateRecoveredAccess(Connection connection, Path key)
            throws SQLException, IOException, GeneralSecurityException {
        try (var stmt = connection.createStatement();
                var rows =
                        stmt.executeQuery(
                                "SELECT COUNT(*) FROM usuarios WHERE activo=TRUE AND rol IN ('SUPER','DUENO')")) {
            rows.next();
            if (rows.getLong(1) < 1)
                throw new IllegalArgumentException(
                        "El backup no contiene una cuenta administradora activa.");
        }
        try (var stmt = connection.createStatement();
                var rows =
                        stmt.executeQuery(
                                "SELECT mfa_secret,mfa_enabled FROM usuarios WHERE mfa_secret IS NOT NULL OR mfa_enabled=TRUE")) {
            byte[] bytes = Files.exists(key) ? Files.readAllBytes(key) : null;
            while (rows.next()) {
                String encrypted = rows.getString(1);
                if (bytes == null || bytes.length != 32 || encrypted == null)
                    throw new IllegalArgumentException(
                            "Al backup le falta la clave del autenticador o sus datos de acceso están incompletos.");
                byte[] secret = Base64.getDecoder().decode(encrypted);
                if (secret.length < 29)
                    throw new IllegalArgumentException("El autenticador del backup no es válido.");
                Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                cipher.init(
                        Cipher.DECRYPT_MODE,
                        new SecretKeySpec(bytes, "AES"),
                        new GCMParameterSpec(128, Arrays.copyOf(secret, 12)));
                cipher.doFinal(Arrays.copyOfRange(secret, 12, secret.length));
            }
        }
    }

    private static void validateImages(Connection connection, Path images) throws SQLException {
        try (var stmt = connection.createStatement();
                var rows =
                        stmt.executeQuery(
                                "SELECT imagen_clave FROM articulos WHERE imagen_clave IS NOT NULL UNION SELECT clave FROM articulo_imagenes")) {
            while (rows.next()) {
                String name = rows.getString(1);
                if (!name.matches("[0-9a-fA-F-]{36}\\.png")
                        || !Files.isRegularFile(images.resolve(name), LinkOption.NOFOLLOW_LINKS))
                    throw new IllegalArgumentException(
                            "El backup está incompleto: faltan imágenes de artículos.");
            }
        }
    }

    private static void validateReferences(Connection connection, Set<String> tables)
            throws SQLException {
        record Reference(String parent, List<String> child, List<String> target) {}
        for (String table : tables) {
            var references = new LinkedHashMap<String, Reference>();
            try (var keys =
                    connection
                            .getMetaData()
                            .getImportedKeys(connection.getCatalog(), null, table)) {
                while (keys.next()) {
                    String parent = keys.getString("PKTABLE_NAME");
                    if (!tables.contains(parent))
                        throw new IllegalArgumentException(
                                "El backup tiene referencias fuera de Alon.");
                    var reference =
                            references.computeIfAbsent(
                                    keys.getString("FK_NAME"),
                                    name ->
                                            new Reference(
                                                    parent, new ArrayList<>(), new ArrayList<>()));
                    reference.child().add(keys.getString("FKCOLUMN_NAME"));
                    reference.target().add(keys.getString("PKCOLUMN_NAME"));
                }
            }
            for (var reference : references.values()) {
                var join = new ArrayList<String>();
                var notNull = new ArrayList<String>();
                for (int i = 0; i < reference.child().size(); i++) {
                    join.add(
                            "c.`"
                                    + reference.child().get(i)
                                    + "`=p.`"
                                    + reference.target().get(i)
                                    + "`");
                    notNull.add("c.`" + reference.child().get(i) + "` IS NOT NULL");
                }
                String sql =
                        "SELECT 1 FROM `"
                                + table
                                + "` c LEFT JOIN `"
                                + reference.parent()
                                + "` p ON "
                                + String.join(" AND ", join)
                                + " WHERE "
                                + String.join(" AND ", notNull)
                                + " AND p.`"
                                + reference.target().getFirst()
                                + "` IS NULL LIMIT 1";
                try (var stmt = connection.createStatement();
                        var rows = stmt.executeQuery(sql)) {
                    if (rows.next())
                        throw new IllegalArgumentException(
                                "El backup contiene operaciones con referencias incompletas.");
                }
            }
        }
    }
}
