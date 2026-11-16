package ar.com.alon.security;

import static org.assertj.core.api.Assertions.*;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

class BusinessProvisioningTest {
    @TempDir Path temporary;

    @Test
    void requiresProofFromTheActualControlBeforeConfirmingDisable() {
        assertThat(BusinessProvisioning.confirmsBlock(403, "enabled", "SUSPENDIDO", "SUSPENDIDO"))
                .isTrue();
        assertThat(BusinessProvisioning.confirmsBlock(200, "enabled", "SUSPENDIDO", "SUSPENDIDO"))
                .isFalse();
        assertThat(BusinessProvisioning.confirmsBlock(403, "", "SUSPENDIDO", "SUSPENDIDO"))
                .isFalse();
        assertThat(BusinessProvisioning.confirmsBlock(403, "enabled", "BAJA", "SUSPENDIDO"))
                .isFalse();
        assertThat(BusinessProvisioning.confirmsBlock(403, "enabled", "ACTIVO", "ACTIVO"))
                .isFalse();
    }

    @Test
    void detectsOutdatedOrMissingBusinessFilesBeforeReusingAnInstance() throws Exception {
        Path root = temporary.resolve("source");
        Path installed = temporary.resolve("business");
        java.nio.file.Files.createDirectories(root.resolve("web/assets"));
        java.nio.file.Files.createDirectories(installed.resolve("web/assets"));
        java.nio.file.Files.writeString(root.resolve("alon.jar"), "server-one");
        java.nio.file.Files.writeString(installed.resolve("alon.jar"), "server-one");
        java.nio.file.Files.writeString(root.resolve("web/index.html"), "index-one");
        java.nio.file.Files.writeString(installed.resolve("web/index.html"), "index-one");
        java.nio.file.Files.writeString(root.resolve("web/assets/app.js"), "ui-one");
        java.nio.file.Files.writeString(installed.resolve("web/assets/app.js"), "ui-one");
        assertThat(
                        BusinessProvisioning.installationCurrent(
                                root.resolve("alon.jar"), root.resolve("web"), installed))
                .isTrue();
        java.nio.file.Files.writeString(root.resolve("web/assets/app.js"), "ui-two");
        assertThat(
                        BusinessProvisioning.installationCurrent(
                                root.resolve("alon.jar"), root.resolve("web"), installed))
                .isFalse();
        java.nio.file.Files.writeString(installed.resolve("web/assets/app.js"), "ui-two");
        java.nio.file.Files.writeString(root.resolve("alon.jar"), "server-two");
        assertThat(
                        BusinessProvisioning.installationCurrent(
                                root.resolve("alon.jar"), root.resolve("web"), installed))
                .isFalse();
    }

    @Test
    void identifiesOnlyTheExactJavaExecutableOfTheSelectedBusiness() {
        var executable = temporary.resolve("business-one/alon.jar");
        assertThat(
                        BusinessProvisioning.ownsJavaProcess(
                                "java", new String[] {"-jar", executable.toString()}, executable))
                .isTrue();
        assertThat(
                        BusinessProvisioning.ownsJavaProcess(
                                "java",
                                new String[] {
                                    "-jar", temporary.resolve("business-two/alon.jar").toString()
                                },
                                executable))
                .isFalse();
        assertThat(
                        BusinessProvisioning.ownsJavaProcess(
                                "powershell",
                                new String[] {"-jar", executable.toString()},
                                executable))
                .isFalse();
        assertThat(
                        BusinessProvisioning.ownsJavaProcess(
                                "java", new String[] {executable.toString()}, executable))
                .isFalse();
    }

    @Test
    void replacesOnlyTheDatabaseAndPreservesConnectionOptions() {
        assertThat(
                        BusinessProvisioning.tenantUrl(
                                "jdbc:mysql://localhost:3306/central?useSSL=false&serverTimezone=UTC",
                                "alon_n_0123456789abcdefabcd"))
                .isEqualTo(
                        "jdbc:mysql://localhost:3306/alon_n_0123456789abcdefabcd?useSSL=false&serverTimezone=UTC");
        assertThatThrownBy(
                        () ->
                                BusinessProvisioning.tenantUrl(
                                        "jdbc:mysql://localhost/central",
                                        "central`; DROP DATABASE central"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () ->
                                BusinessProvisioning.tenantUrl(
                                        "jdbc:postgresql://localhost/central",
                                        "alon_n_0123456789abcdefabcd"))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void rejectsManagerAccessBeforeAnyDatabaseOrProcessOperation() {
        var identity =
                new RequestIdentity() {
                    @Override
                    public Map<String, Object> user() {
                        return Map.of("rol", "DUENO");
                    }
                };
        var service =
                new BusinessProvisioning(
                        new JdbcTemplate(),
                        null,
                        identity,
                        null,
                        null,
                        true,
                        "",
                        "",
                        "",
                        "localhost",
                        temporary.toString(),
                        temporary.toString(),
                        temporary.toString());
        assertThatThrownBy(() -> service.status(1)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.provision(1, null))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.start(1)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.changeState(1, "BAJA", "Fin del servicio"))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.deleteBusiness(1, "Fin del servicio", "Negocio"))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void rejectsReservedUsersUnknownPermissionsAndInvalidPortReservations() {
        assertThatThrownBy(
                        () ->
                                BusinessProvisioning.validate(
                                        new BusinessProvisioning.Input(
                                                "alon",
                                                "Gerente",
                                                "Clave123456789",
                                                "gerente@example.test",
                                                1,
                                                List.of("ventas"))))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(
                        () ->
                                BusinessProvisioning.validate(
                                        new BusinessProvisioning.Input(
                                                "gerente",
                                                "Gerente",
                                                "Clave123456789",
                                                "gerente@example.test",
                                                1,
                                                List.of("administracion"))))
                .isInstanceOf(ResponseStatusException.class);
        assertThat(BusinessProvisioning.portFor(1)).isEqualTo(20001);
        assertThatThrownBy(() -> BusinessProvisioning.portFor(40001))
                .isInstanceOf(ResponseStatusException.class);
    }
}
