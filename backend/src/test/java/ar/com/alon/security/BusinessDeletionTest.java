package ar.com.alon.security;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.*;
import java.sql.*;
import java.util.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

class BusinessDeletionTest {
    @TempDir Path temporary;
    private static final String SCHEMA = "alon_n_0123456789abcdefabcd";
    private static final String USER = "alon_u_0123456789abcdefabcd";

    @Test
    void rejectsCentralDatabaseOtherUsersAndUntrustedIdentifiers() {
        BusinessProvisioning.validateDeletionTarget(SCHEMA, USER, "central", "root", "localhost");
        for (String schema :
                List.of("central", "mysql", SCHEMA + "`;DROP DATABASE central", SCHEMA)) {
            assertThatThrownBy(
                            () ->
                                    BusinessProvisioning.validateDeletionTarget(
                                            schema, USER, SCHEMA, "root", "localhost"))
                    .isInstanceOf(ResponseStatusException.class);
        }
        assertThatThrownBy(
                        () ->
                                BusinessProvisioning.validateDeletionTarget(
                                        SCHEMA,
                                        "alon_u_ffffffffffffffffffff",
                                        "central",
                                        "root",
                                        "localhost"))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(
                        () ->
                                BusinessProvisioning.validateDeletionTarget(
                                        SCHEMA, USER, "central", USER, "localhost"))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void removesOnlyTheSelectedInstallationAndSupportsRetry() throws Exception {
        Path root = temporary.toRealPath();
        Path target = root.resolve(SCHEMA);
        Files.createDirectories(target.resolve("data/photos"));
        Files.writeString(target.resolve("data/photos/photo.png"), "test");
        Files.writeString(target.resolve("alon.jar"), "test");
        Path other = root.resolve("other-business.txt");
        Files.writeString(other, "preserve");
        BusinessProvisioning.deleteInstallationFiles(root, SCHEMA);
        assertThat(target).doesNotExist();
        assertThat(Files.readString(other)).isEqualTo("preserve");
        BusinessProvisioning.deleteInstallationFiles(root, SCHEMA);
        assertThatThrownBy(
                        () ->
                                BusinessProvisioning.deleteInstallationFiles(
                                        root, "../other-business.txt"))
                .isInstanceOf(java.io.IOException.class);
        assertThat(other).exists();
    }

    private BusinessProvisioning service(JdbcTemplate central, DataSource source) {
        var identity =
                new RequestIdentity() {
                    @Override
                    public Map<String, Object> user() {
                        return Map.of("rol", "SUPER", "id", 1L);
                    }
                };
        return new BusinessProvisioning(
                central,
                source,
                identity,
                null,
                null,
                true,
                "jdbc:mysql://deletion.test/central",
                "root",
                "",
                "localhost",
                temporary.toString(),
                temporary.toString(),
                temporary.toString());
    }

    private DataSource lockedSource() throws Exception {
        var source = mock(DataSource.class);
        var connection = mock(Connection.class);
        var statement = mock(PreparedStatement.class);
        var result = mock(ResultSet.class);
        when(source.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(result);
        when(result.getInt(1)).thenReturn(1);
        return source;
    }

    @Test
    void missingOrIncorrectConfirmationNeverChangesStateOrDeletesAnything() throws Exception {
        var central = mock(JdbcTemplate.class);
        when(central.queryForList(
                        "SELECT nombre,eliminado_en FROM plataforma_negocios WHERE id=?", 7L))
                .thenReturn(List.of(Map.of("nombre", "Las Tres L")));
        var service = service(central, lockedSource());
        for (String confirmation : new String[] {null, "", "Otro negocio"})
            assertThatThrownBy(() -> service.deleteBusiness(7L, "", confirmation))
                    .isInstanceOf(ResponseStatusException.class);
        verify(central, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void anIncompletePurgeCannotBeReactivated() throws Exception {
        var central = mock(JdbcTemplate.class);
        when(central.queryForList(
                        "SELECT estado,eliminado_en,baja_iniciada_en FROM plataforma_negocios WHERE id=?",
                        7L))
                .thenReturn(List.of(Map.of("estado", "BAJA", "baja_iniciada_en", "2026-11-14")));
        var service = service(central, lockedSource());
        assertThatThrownBy(() -> service.changeState(7L, "ACTIVO", ""))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("baja está incompleta");
        verify(central, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void aFailedStateFileKeepsARecoverableTargetAndDoesNotConfirmTheChange() throws Exception {
        var central = mock(JdbcTemplate.class);
        when(central.queryForList(
                        "SELECT estado,eliminado_en,baja_iniciada_en FROM plataforma_negocios WHERE id=?",
                        7L))
                .thenReturn(List.of(Map.of("estado", "ACTIVO")));
        when(central.queryForList("SELECT * FROM plataforma_instalaciones WHERE negocio_id=?", 7L))
                .thenReturn(List.of(Map.of("esquema", SCHEMA, "iniciar", false)));
        // A file at the installation folder makes writing its state fail deterministically.
        Files.writeString(temporary.resolve(SCHEMA), "not a directory");
        assertThatThrownBy(
                        () -> service(central, lockedSource()).changeState(7L, "SUSPENDIDO", "QA"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("No se confirmó");
        verify(central)
                .update(contains("estado_objetivo=?"), eq("SUSPENDIDO"), eq(1L), eq("QA"), eq(7L));
        verify(central).update(contains("estado_aplicacion='ERROR'"), eq(7L));
        verify(central, never())
                .update(
                        startsWith("UPDATE plataforma_negocios SET estado=?,"),
                        any(),
                        any(),
                        any());
    }

    @Test
    void stateEndpointPassesTheExplicitConfirmationToDeletion() {
        var service = mock(BusinessProvisioning.class);
        new BusinessStateController(service)
                .change(7L, new BusinessStateController.StateInput("BAJA", "", "Las Tres L"));
        verify(service).deleteBusiness(7L, "", "Las Tres L");
        verify(service, never()).changeState(anyLong(), anyString(), anyString());
    }
}
