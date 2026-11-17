package ar.com.alon.security;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

@EnabledIfEnvironmentVariable(named = "ALON_TEST_MYSQL_URL", matches = "jdbc:mysql://[^/]+/alon_validation(?:[?].*)?")
class PlatformReceiptsMySqlTest {
    private JdbcTemplate jdbc;
    private TransactionTemplate transaction;
    private PlatformReceipts service;

    @BeforeEach
    void prepare() {
        var source = new DriverManagerDataSource(System.getenv("ALON_TEST_MYSQL_URL"),
                System.getenv().getOrDefault("ALON_TEST_MYSQL_USER", "root"),
                System.getenv().getOrDefault("ALON_TEST_MYSQL_PASSWORD", ""));
        Flyway.configure().dataSource(source).cleanDisabled(true).load().migrate();
        jdbc = new JdbcTemplate(source);
        transaction = new TransactionTemplate(new JdbcTransactionManager(source));
        var identity = mock(RequestIdentity.class);
        when(identity.user()).thenReturn(Map.of("rol", "SUPER"));
        when(identity.userId()).thenReturn(1L);
        service = new PlatformReceipts(jdbc, identity);
    }

    private void business(long id) {
        jdbc.update("INSERT INTO plataforma_negocios(id,nombre,razon_social,cuit,rubro,localidad,responsable,telefono,email,plan,abono,estado,alta,gestion_url,tienda_url,base_referencia,notas) VALUES(?,'Cliente recibo','','','','','','','','Inicial',1000,'ACTIVO','2026-11-16','','','','')", id);
    }

    private PlatformReceipts.Receipt input(String request, String period) {
        return new PlatformReceipts.Receipt(request, LocalDate.of(2026, 11, 16), period, 2, new BigDecimal("10"));
    }

    @Test
    void snapshotsAmountsReplaysAndRejectsOverlappingPeriodsWithoutChangingAccess() {
        transaction.executeWithoutResult(tx -> {
            business(97001);
            var input = input(UUID.randomUUID().toString(), "2026-11");
            var result = service.issue(97001, input);
            assertThat(result.get("total")).isEqualTo(new BigDecimal("1800.00"));
            jdbc.update("UPDATE plataforma_negocios SET abono=2000 WHERE id=97001");
            assertThat(service.issue(97001, input).get("id")).isEqualTo(result.get("id"));
            assertThatThrownBy(() -> service.issue(97001, input(UUID.randomUUID().toString(), "2026-12"))).isInstanceOf(ResponseStatusException.class);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM plataforma_recibos WHERE negocio_id=97001", Long.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT SUM(CASE WHEN tipo='CARGO' THEN importe ELSE -importe END) FROM plataforma_movimientos WHERE negocio_id=97001", BigDecimal.class)).isEqualByComparingTo("0");
            assertThat(jdbc.queryForObject("SELECT estado FROM plataforma_negocios WHERE id=97001", String.class)).isEqualTo("ACTIVO");
            tx.setRollbackOnly();
        });
    }

    @Test
    void cancellationPreservesReceiptAndReleasesPeriodsOnlyForItsBusiness() {
        transaction.executeWithoutResult(tx -> {
            business(97002);
            business(97003);
            long id = ((Number) service.issue(97002, input(UUID.randomUUID().toString(), "2026-11")).get("id")).longValue();
            assertThatThrownBy(() -> service.cancel(97003, id, new PlatformReceipts.Cancellation("Error"))).isInstanceOf(ResponseStatusException.class);
            service.cancel(97002, id, new PlatformReceipts.Cancellation("Importe incorrecto"));
            service.cancel(97002, id, new PlatformReceipts.Cancellation("Reintento"));
            assertThat(jdbc.queryForObject("SELECT estado FROM plataforma_recibos WHERE id=?", String.class, id)).isEqualTo("ANULADO");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM plataforma_periodos_cobrados WHERE negocio_id=97002", Long.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM plataforma_movimientos WHERE recibo_id=? AND anulado=TRUE", Long.class, id)).isEqualTo(2);
            service.issue(97002, input(UUID.randomUUID().toString(), "2026-11"));
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM plataforma_recibos WHERE negocio_id=97002", Long.class)).isEqualTo(2);
            tx.setRollbackOnly();
        });
    }
}
