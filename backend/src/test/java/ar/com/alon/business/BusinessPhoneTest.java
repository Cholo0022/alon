package ar.com.alon.business;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import ar.com.alon.security.RequestIdentity;
import ar.com.alon.shared.audit.AuditLog;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class BusinessPhoneTest {
    @Test
    void savesClearsAndPreservesTheOptionalPhoneForOlderClients() {
        for (String phone : new String[] {"3446 - 236311", "", " 3446 - 236311 ", "legacy"}) {
            var jdbc = mock(JdbcTemplate.class);
            var identity = mock(RequestIdentity.class);
            when(identity.user()).thenReturn(Map.of("rol", "DUENO"));
            when(identity.permits("negocio")).thenReturn(true);
            when(identity.storeId()).thenReturn(1L);
            when(jdbc.queryForMap(anyString(), eq(1L)))
                    .thenReturn(
                            Map.of(
                                    "plantilla_tienda",
                                    "clasica",
                                    "titulo_tienda",
                                    "Hola",
                                    "descripcion_tienda",
                                    "Tienda",
                                    "telefono_tienda",
                                    "3446 - 236311"));
            var service = new BusinessService(jdbc, identity, mock(AuditLog.class));
            service.update(
                    new BusinessService.Input(
                            "Negocio",
                            "Slogan",
                            "#006b73",
                            "#102a43",
                            "#f4f7f9",
                            null,
                            null,
                            null,
                            phone.equals("legacy") ? null : phone));
            var update =
                    mockingDetails(jdbc).getInvocations().stream()
                            .filter(call -> call.getMethod().getName().equals("update"))
                            .findFirst()
                            .orElseThrow();
            assertThat(update.<String>getArgument(0)).contains("telefono_tienda=?");
            assertThat((String) update.getArgument(9))
                    .isEqualTo(phone.equals("legacy") ? "3446 - 236311" : phone.trim());
            assertThat((Long) update.getArgument(10)).isEqualTo(1L);
        }
    }
}
