package ar.com.alon.shared.web;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import ar.com.alon.security.RequestIdentity;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

class RecordDeletionTest {
    @Test
    void permanentlyDeletesOnlyTheSelectedStoresRecord() {
        for (String[] entity :
                new String[][] {
                    {"articles", "articulos"},
                    {"customers", "clientes"},
                    {"suppliers", "proveedores"}
                }) {
            var jdbc = mock(JdbcTemplate.class);
            var identity = mock(RequestIdentity.class);
            when(identity.storeId()).thenReturn(7L);
            when(jdbc.queryForMap(
                            "SELECT id,nombre FROM "
                                    + entity[1]
                                    + " WHERE id=? AND comercio_id=? FOR UPDATE",
                            9L,
                            7L))
                    .thenReturn(Map.of("id", 9L, "nombre", "Prueba"));
            when(jdbc.update("DELETE FROM " + entity[1] + " WHERE id=? AND comercio_id=?", 9L, 7L))
                    .thenReturn(1);
            assertThat(new RecordDeletionService(jdbc, identity).delete(entity[0], 9L))
                    .containsEntry("eliminado", true);
            verify(jdbc)
                    .update("DELETE FROM " + entity[1] + " WHERE id=? AND comercio_id=?", 9L, 7L);
        }
    }

    @Test
    void linkedOperationsAreReportedWithoutDeletingHistory() {
        var jdbc = mock(JdbcTemplate.class);
        var identity = mock(RequestIdentity.class);
        when(identity.storeId()).thenReturn(7L);
        when(jdbc.queryForMap(anyString(), eq(9L), eq(7L))).thenReturn(Map.of("nombre", "Prueba"));
        when(jdbc.update("DELETE FROM clientes WHERE id=? AND comercio_id=?", 9L, 7L))
                .thenThrow(new DataIntegrityViolationException("foreign key"));
        assertThatThrownBy(() -> new RecordDeletionService(jdbc, identity).delete("customers", 9L))
                .hasMessageContaining("operaciones o registros vinculados");
        verify(jdbc, never()).update(contains("ventas"), anyLong(), anyLong());
    }

    @Test
    void articleStockMovementsBlockDeletionExceptInitialStock() {
        var jdbc = mock(JdbcTemplate.class);
        var identity = mock(RequestIdentity.class);
        when(identity.storeId()).thenReturn(7L);
        when(jdbc.queryForMap(anyString(), eq(9L), eq(7L))).thenReturn(Map.of("nombre", "Prueba"));
        when(jdbc.queryForObject(anyString(), eq(Long.class), eq(9L), eq(7L))).thenReturn(1L);
        assertThatThrownBy(() -> new RecordDeletionService(jdbc, identity).delete("articles", 9L))
                .hasMessageContaining("historial");
        verify(jdbc, never()).update(anyString(), anyLong(), anyLong());
    }
}
