package ar.com.alon.backup;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import ar.com.alon.security.RequestIdentity;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.server.ResponseStatusException;

class BackupServiceTest {
    @TempDir Path directory;

    private BackupService service(String role) {
        var identity = mock(RequestIdentity.class);
        when(identity.permits("backup")).thenReturn("SUPER".equals(role));
        return new BackupService(
                identity,
                "invalid-url",
                "user",
                "secret",
                directory.toString(),
                directory.resolve("images").toString(),
                directory.resolve("mfa.key").toString(),
                "");
    }

    @Test
    void deniesManagersAndEmployeesEvenWhenCalledDirectly() {
        for (String role : new String[] {"DUENO", "VENDEDOR"}) {
            var service = service(role);
            assertThatThrownBy(service::list)
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("403");
            assertThatThrownBy(service::create)
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("403");
            assertThatThrownBy(() -> service.download("anything"))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("403");
        }
    }

    @Test
    void listsOnlyCompletedArchivesAndRejectsTraversal() throws Exception {
        String name = "alon-backup-12345678-1234-1234-1234-123456789abc.zip";
        Files.writeString(directory.resolve(name), "zip");
        Files.writeString(directory.resolve("backup-incomplete.partial"), "partial");
        var service = service("SUPER");
        assertThat(service.list()).extracting(BackupService.Backup::name).containsExactly(name);
        assertThat(service.download(name)).isEqualTo(directory.resolve(name));
        assertThatThrownBy(() -> service.download("../mfa.key"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
    }

    @Test
    void failedBackupLeavesNoArchiveOrTemporarySecretsAndCanBeRetried() throws Exception {
        var service = service("SUPER");
        for (int i = 0; i < 2; i++) {
            assertThatThrownBy(service::create)
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("503");
            try (var files = Files.list(directory)) {
                assertThat(files.toList()).isEmpty();
            }
        }
    }
}
