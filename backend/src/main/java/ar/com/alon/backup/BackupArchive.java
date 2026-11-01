package ar.com.alon.backup;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

final class BackupArchive {
    static final long MAX_EXPANDED = 2L * 1024 * 1024 * 1024;

    static void extract(InputStream input, Path destination) throws IOException {
        Files.createDirectories(destination.resolve("article-images"));
        var names = new HashSet<String>();
        long total = 0;
        int entries = 0;
        try (var zip = new ZipInputStream(new BufferedInputStream(input))) {
            ZipEntry entry;
            byte[] buffer = new byte[64 * 1024];
            while ((entry = zip.getNextEntry()) != null) {
                if (++entries > 100_000) throw invalid();
                String name = entry.getName();
                if (entry.isDirectory()) {
                    if (!Set.of("article-images/", "security/").contains(name)) throw invalid();
                    continue;
                }
                if (!names.add(name)
                        || names.size() > 100_000
                        || !(Set.of("database.sql", "security/mfa.key", "LEEME.txt").contains(name)
                                || name.matches("article-images/[0-9a-fA-F-]{36}\\.png")))
                    throw invalid();
                Path target = destination.resolve(name).normalize();
                if (!target.startsWith(destination.normalize())) throw invalid();
                Files.createDirectories(target.getParent());
                long size = 0;
                try (var output = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW)) {
                    int count;
                    while ((count = zip.read(buffer)) >= 0) {
                        size += count;
                        total += count;
                        if (total > MAX_EXPANDED
                                || (!name.equals("database.sql") && size > 64L * 1024 * 1024))
                            throw invalid();
                        output.write(buffer, 0, count);
                    }
                }
            }
        }
        if (!names.contains("database.sql") || Files.size(destination.resolve("database.sql")) == 0)
            throw invalid();
        if (names.contains("security/mfa.key")
                && Files.size(destination.resolve("security/mfa.key")) != 32) throw invalid();
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException(
                "Seleccioná un ZIP completo y válido generado por Backup de Alon (hasta 2 GB descomprimidos).");
    }

    static void cleanup(Path directory) throws IOException {
        if (directory == null || !Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) return;
        // Solo se invoca sobre directorios temporales creados por esta operación.
        try (var files = Files.walk(directory)) {
            for (Path file : files.sorted(Comparator.reverseOrder()).toList()) Files.delete(file);
        }
    }
}
