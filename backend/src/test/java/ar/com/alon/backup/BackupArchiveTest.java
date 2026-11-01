package ar.com.alon.backup;

import static org.assertj.core.api.Assertions.*;

import java.io.*;
import java.nio.file.*;
import java.util.zip.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BackupArchiveTest {
    @TempDir Path temp;

    private byte[] zip(String name, byte[] content) throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry(name));
            zip.write(content);
            zip.closeEntry();
        }
        return bytes.toByteArray();
    }

    @Test
    void rejectsTraversalAndUnknownFilesWithoutEscapingTheTemporaryDirectory() throws Exception {
        for (String name :
                new String[] {
                    "../outside.sql",
                    "/database.sql",
                    "C:/outside.sql",
                    "article-images/../../outside.sql",
                    "program.exe"
                }) {
            byte[] bytes = zip(name, new byte[] {1});
            assertThatThrownBy(
                            () ->
                                    BackupArchive.extract(
                                            new ByteArrayInputStream(bytes), temp.resolve("work")))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThat(Files.exists(temp.resolve("outside.sql"))).isFalse();
        }
    }

    @Test
    void rejectsMissingSqlOrWrongMfaKeyAndAcceptsACompleteArchive() throws Exception {
        assertThatThrownBy(
                        () ->
                                BackupArchive.extract(
                                        new ByteArrayInputStream(zip("LEEME.txt", new byte[] {1})),
                                        temp.resolve("missing")))
                .isInstanceOf(IllegalArgumentException.class);
        var bytes = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("database.sql"));
            zip.write("SELECT 1;".getBytes());
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("security/mfa.key"));
            zip.write(new byte[32]);
            zip.closeEntry();
        }
        BackupArchive.extract(
                new ByteArrayInputStream(bytes.toByteArray()), temp.resolve("complete"));
        assertThat(Files.size(temp.resolve("complete/security/mfa.key"))).isEqualTo(32);
    }
}
