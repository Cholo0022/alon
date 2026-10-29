package ar.com.alon.integration;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import ar.com.alon.articles.ArticleQueries;
import ar.com.alon.articles.ArticleService;
import ar.com.alon.articles.dto.ArticleInput;
import ar.com.alon.cash.CashService;
import ar.com.alon.cash.dto.CashCloseInput;
import ar.com.alon.cash.dto.CashOpenInput;
import ar.com.alon.customers.CustomerQueries;
import ar.com.alon.customers.CustomerService;
import ar.com.alon.customers.dto.CustomerInput;
import ar.com.alon.dashboard.DashboardQueries;
import ar.com.alon.purchases.PurchaseService;
import ar.com.alon.purchases.dto.PurchaseInput;
import ar.com.alon.purchases.dto.PurchaseLineInput;
import ar.com.alon.sales.SalesService;
import ar.com.alon.sales.dto.SaleInput;
import ar.com.alon.sales.dto.SaleLine;
import ar.com.alon.security.*;
import ar.com.alon.shared.audit.AuditLog;
import ar.com.alon.shared.commands.CommandExecutor;
import ar.com.alon.shared.web.BusinessConflict;
import ar.com.alon.users.UserService;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/** Never points at a development database: the schema name is deliberately restricted. */
@EnabledIfEnvironmentVariable(named = "ALON_TEST_MYSQL_URL", matches = ".*alon_validation.*")
class MySqlIntegrationTest {
    private static DriverManagerDataSource source;
    private JdbcTemplate jdbc;
    private TransactionTemplate transaction;
    private RequestIdentity identity;
    private PurchaseService purchases;
    private SalesService sales;
    private CashService cash;
    private ArticleService catalog;
    private CustomerService customers;
    private UserService auth;
    private final Clock clock =
            Clock.fixed(
                    Instant.parse("2026-10-28T15:00:00Z"),
                    ZoneId.of("America/Argentina/Buenos_Aires"));

    private static BigDecimal d(String value) {
        return new BigDecimal(value);
    }

    @BeforeAll
    static void migrateDisposableSchema() {
        String url = System.getenv("ALON_TEST_MYSQL_URL");
        if (!url.matches("jdbc:mysql://[^/]+/alon_validation(?:\\?.*)?"))
            throw new IllegalArgumentException("Solo se permite la base alon_validation.");
        source =
                new DriverManagerDataSource(
                        url,
                        System.getenv().getOrDefault("ALON_TEST_MYSQL_USER", "root"),
                        System.getenv().getOrDefault("ALON_TEST_MYSQL_PASSWORD", ""));
        var flyway = Flyway.configure().dataSource(source).cleanDisabled(false).load();
        flyway.clean();
        flyway.migrate();
        flyway.validate();
    }

    @BeforeEach
    void prepareFixtures() {
        jdbc = new JdbcTemplate(source);
        jdbc.execute(
                (org.springframework.jdbc.core.ConnectionCallback<Void>)
                        connection -> {
                            try (var statement = connection.createStatement()) {
                                statement.execute("SET FOREIGN_KEY_CHECKS=0");
                                try {
                                    for (String table :
                                            List.of(
                                                    "comandos_procesados",
                                                    "auditoria_operaciones",
                                                    "movimientos_stock",
                                                    "pagos_proveedores",
                                                    "movimientos_caja",
                                                    "gastos",
                                                    "venta_detalle",
                                                    "ventas",
                                                    "compra_detalle",
                                                    "compras",
                                                    "pedidos_tienda",
                                                    "articulos",
                                                    "rubros",
                                                    "clientes",
                                                    "proveedores",
                                                    "cajas",
                                                    "usuarios",
                                                    "comercios"))
                                        statement.execute("DELETE FROM " + table);
                                } finally {
                                    statement.execute("SET FOREIGN_KEY_CHECKS=1");
                                }
                            }
                            return null;
                        });
        jdbc.update("INSERT INTO comercios(id,codigo,nombre) VALUES(1,'ALON','Comercio')");
        transaction = new TransactionTemplate(new JdbcTransactionManager(source));
        identity = mock(RequestIdentity.class);
        when(identity.storeId()).thenReturn(1L);
        when(identity.userId()).thenReturn(1L);
        when(identity.permits(anyString())).thenReturn(true);
        jdbc.update(
                "INSERT INTO usuarios(id,comercio_id,usuario,nombre,password_hash,rol,permisos) VALUES(1,1,'owner','Dueño','unused','DUENO','ventas')");
        jdbc.update(
                "INSERT INTO proveedores(id,comercio_id,codigo,nombre,especialidad,telefono,email) VALUES(1,1,'P1','Proveedor','','','')");
        jdbc.update("INSERT INTO rubros(id,comercio_id,codigo,nombre) VALUES(1,1,'R1','Rubro')");
        jdbc.update(
                "INSERT INTO clientes(id,comercio_id,codigo,nombre,telefono,email,limite_credito) VALUES(1,1,'C1','Cliente','','',100000)");
        jdbc.update(
                "INSERT INTO articulos(id,comercio_id,codigo,nombre,rubro_id,proveedor_principal_id,unidad,costo,precio_venta) VALUES(1,1,'A1','Artículo',1,1,'UNIDAD',0,200)");
        var audit = new AuditLog(jdbc, identity, JsonMapper.builder().build());
        purchases =
                new PurchaseService(
                        jdbc,
                        identity,
                        clock,
                        audit,
                        new ar.com.alon.stock.StockRepository(jdbc, identity, clock),
                        new ar.com.alon.cash.CashLedger(jdbc, identity, clock));
        sales =
                new SalesService(
                        jdbc,
                        identity,
                        clock,
                        audit,
                        new ar.com.alon.stock.StockRepository(jdbc, identity, clock),
                        new ar.com.alon.cash.CashLedger(jdbc, identity, clock));
        cash =
                new CashService(
                        jdbc,
                        identity,
                        clock,
                        audit,
                        new ar.com.alon.cash.CashLedger(jdbc, identity, clock));
        catalog =
                new ArticleService(
                        jdbc,
                        identity,
                        clock,
                        audit,
                        new ar.com.alon.stock.StockRepository(jdbc, identity, clock));
        customers = new CustomerService(jdbc, identity, clock, audit);
        auth = new UserService(jdbc, new PasswordHasher(), identity, 1, false);
    }

    private PurchaseInput purchase(String document, String quantity, String cost, String extras) {
        return new PurchaseInput(
                1,
                "FACTURA",
                document,
                LocalDate.now(clock),
                List.of(new PurchaseLineInput(1, d(quantity), d(cost))),
                d(extras),
                d("0"),
                "CUENTA_CORRIENTE",
                "EFECTIVO");
    }

    private long buy(PurchaseInput input) {
        return transaction.execute(
                status -> ((Number) purchases.createPurchase(input).get("id")).longValue());
    }

    private BigDecimal stock() {
        return jdbc.queryForObject("SELECT stock FROM articulos WHERE id=1", BigDecimal.class);
    }

    private BigDecimal cost() {
        return jdbc.queryForObject("SELECT costo FROM articulos WHERE id=1", BigDecimal.class);
    }

    @Test
    void installsFreshSchemaAndRejectsCrossStoreReferences() {
        jdbc.update("INSERT INTO comercios(id,codigo,nombre) VALUES(2,'OTRO','Otro comercio')");
        when(identity.storeId()).thenReturn(2L);
        assertThatThrownBy(
                        () ->
                                transaction.execute(
                                        status ->
                                                customers.updateCustomer(
                                                        1,
                                                        new CustomerInput(
                                                                "Intruso", "", "", d("0")))))
                .isInstanceOf(org.springframework.dao.EmptyResultDataAccessException.class);
        assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        "INSERT INTO ventas(comercio_id,numero,fecha,cliente_id,tipo_venta,medio_pago) VALUES(2,'X',CURRENT_DATE,1,'CONTADO','EFECTIVO')"))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    void revisesCostFreightAndQuantityAndKeepsAudit() {
        long id = buy(purchase("F1", "10", "100", "0"));
        transaction.execute(
                status -> purchases.updatePurchase(id, purchase("F1", "10", "200", "100")));
        assertThat(stock()).isEqualByComparingTo("10");
        assertThat(cost()).isEqualByComparingTo("210");
        transaction.execute(
                status -> purchases.updatePurchase(id, purchase("F1", "5", "200", "50")));
        assertThat(stock()).isEqualByComparingTo("5");
        assertThat(cost()).isEqualByComparingTo("210");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM auditoria_operaciones WHERE entidad='compra'",
                                Integer.class))
                .isEqualTo(2);
    }

    @Test
    void repeatedArticlesDoNotCreatePhantomStockOnEdit() {
        var input =
                new PurchaseInput(
                        1,
                        "FACTURA",
                        "F1",
                        LocalDate.now(clock),
                        List.of(
                                new PurchaseLineInput(1, d("2"), d("100")),
                                new PurchaseLineInput(1, d("3"), d("100"))),
                        d("0"),
                        d("0"),
                        "CUENTA_CORRIENTE",
                        "EFECTIVO");
        long id = buy(input);
        transaction.execute(status -> purchases.updatePurchase(id, input));
        assertThat(stock()).isEqualByComparingTo("5");
        assertThat(cost()).isEqualByComparingTo("100");
    }

    @Test
    void saleRollbackAndReversalPreserveStockAndCashReports() {
        long purchaseId = buy(purchase("F1", "10", "100", "0"));
        transaction.execute(
                status -> cash.openCash(new CashOpenInput(LocalDate.now(clock), d("0"))));
        var input =
                new SaleInput(
                        1,
                        LocalDate.now(clock),
                        "CONTADO",
                        "EFECTIVO",
                        List.of(new SaleLine(1, d("2"), d("0"))));
        long id =
                transaction.execute(
                        status -> ((Number) sales.createSale(input).get("id")).longValue());
        assertThat(stock()).isEqualByComparingTo("8");
        assertThatThrownBy(
                        () ->
                                transaction.execute(
                                        status ->
                                                purchases.updatePurchase(
                                                        purchaseId,
                                                        purchase("F1", "10", "200", "0"))))
                .isInstanceOf(IllegalArgumentException.class);
        transaction.execute(status -> sales.cancelSale(id));
        assertThat(stock()).isEqualByComparingTo("10");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT ventas_efectivo FROM vw_cierre_caja", BigDecimal.class))
                .isEqualByComparingTo("0");
        assertThat(jdbc.queryForObject("SELECT gastos FROM vw_cierre_caja", BigDecimal.class))
                .isEqualByComparingTo("0");
        var tooMuch =
                new SaleInput(
                        1,
                        LocalDate.now(clock),
                        "CONTADO",
                        "EFECTIVO",
                        List.of(new SaleLine(1, d("11"), d("0"))));
        assertThatThrownBy(() -> transaction.execute(status -> sales.createSale(tooMuch)))
                .isInstanceOf(BusinessConflict.class);
        assertThat(stock()).isEqualByComparingTo("10");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ventas", Integer.class)).isEqualTo(1);
    }

    @Test
    void cashReopeningAndCrossSessionReversalKeepNetReportsConsistent() {
        buy(purchase("F1", "10", "100", "0"));
        transaction.execute(
                status -> cash.openCash(new CashOpenInput(LocalDate.now(clock), d("0"))));
        long saleId =
                transaction.execute(
                        status ->
                                ((Number)
                                                sales.createSale(
                                                                new SaleInput(
                                                                        1,
                                                                        LocalDate.now(clock),
                                                                        "CONTADO",
                                                                        "EFECTIVO",
                                                                        List.of(
                                                                                new SaleLine(
                                                                                        1, d("1"),
                                                                                        d("0")))))
                                                        .get("id"))
                                        .longValue());
        var firstClose =
                transaction.execute(
                        status ->
                                cash.closeCash(new CashCloseInput(LocalDate.now(clock), d("100"))));
        assertThat(new BigDecimal(firstClose.get("diferencia").toString()))
                .isEqualByComparingTo("0");
        transaction.execute(
                status -> cash.openCash(new CashOpenInput(LocalDate.now(clock), d("100"))));
        transaction.execute(status -> sales.cancelSale(saleId));
        var secondClose =
                transaction.execute(
                        status -> cash.closeCash(new CashCloseInput(LocalDate.now(clock), d("0"))));
        assertThat(new BigDecimal(secondClose.get("diferencia").toString()))
                .isEqualByComparingTo("0");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT SUM(ventas_efectivo) FROM vw_cierre_caja",
                                BigDecimal.class))
                .isEqualByComparingTo("0");
    }

    @Test
    void concurrentOwnerDemotionsPreserveOneOwner() throws Exception {
        jdbc.update(
                "INSERT INTO usuarios(id,comercio_id,usuario,nombre,password_hash,rol,permisos) VALUES(2,1,'other','Otro','unused','DUENO','ventas')");
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<Boolean> first = () -> demote(1);
            Callable<Boolean> second = () -> demote(2);
            var results = executor.invokeAll(List.of(first, second));
            assertThat(
                            results.stream()
                                    .filter(
                                            result -> {
                                                try {
                                                    return result.get();
                                                } catch (Exception e) {
                                                    throw new RuntimeException(e);
                                                }
                                            })
                                    .count())
                    .isEqualTo(1);
        }
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM usuarios WHERE rol='DUENO' AND activo=TRUE",
                                Integer.class))
                .isEqualTo(1);
    }

    private boolean demote(long id) {
        try {
            transaction.execute(
                    status ->
                            auth.updateUser(
                                    id,
                                    new UserService.UserUpdateInput(
                                            null, null, null, "VENDEDOR", null)));
            return true;
        } catch (org.springframework.web.server.ResponseStatusException exception) {
            return false;
        }
    }

    @Test
    void replayIsAtomicAndRejectsKeyReuseWithDifferentData() {
        var request = new MockHttpServletRequest();
        request.addHeader("Idempotency-Key", "operation-123456789");
        var executor = new CommandExecutor(jdbc, identity, request, JsonMapper.builder().build());
        var input = new CustomerInput("Nuevo", "", "", d("0"));
        var first =
                transaction.execute(
                        status ->
                                executor.execute(
                                        "customer", input, () -> customers.createCustomer(input)));
        var replay =
                transaction.execute(
                        status ->
                                executor.execute(
                                        "customer", input, () -> customers.createCustomer(input)));
        assertThat(String.valueOf(replay.get("id"))).isEqualTo(String.valueOf(first.get("id")));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM clientes", Integer.class))
                .isEqualTo(2);
        assertThatThrownBy(
                        () ->
                                transaction.execute(
                                        status ->
                                                executor.execute(
                                                        "different",
                                                        input,
                                                        () -> customers.createCustomer(input))))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }

    @Test
    void stockAdjustmentsHaveAnAuthorAndSignedDelta() {
        buy(purchase("F1", "10", "100", "0"));
        var input =
                new ArticleInput(
                        "A1",
                        "Artículo",
                        1,
                        1,
                        "UNIDAD",
                        d("100"),
                        d("0"),
                        d("0"),
                        false,
                        false,
                        d("0"),
                        false,
                        d("9.125"),
                        d("0.001"),
                        d("200"));
        transaction.execute(status -> catalog.updateArticle(1, input));
        assertThat(stock()).isEqualByComparingTo("9.125");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT cantidad FROM movimientos_stock WHERE tipo='AJUSTE'",
                                BigDecimal.class))
                .isEqualByComparingTo("-0.875");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT usuario_id FROM movimientos_stock WHERE tipo='AJUSTE'",
                                Long.class))
                .isEqualTo(1L);
    }

    @Test
    void boundedReadsSearchAndKeepFinancialInformationPrivate() {
        var request = new MockHttpServletRequest();
        request.setParameter("q", "Cliente");
        request.setParameter("size", "1");
        org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(
                new org.springframework.web.context.request.ServletRequestAttributes(request));
        try {

            request.setParameter("q", "");
            assertThat(new ArticleQueries(jdbc, identity, clock).articles("DUENO", false, "Rubro"))
                    .hasSize(1);
            assertThat(
                            new ArticleQueries(jdbc, identity, clock)
                                    .articles("DUENO", false, "Inexistente"))
                    .isEmpty();
            request.setParameter("q", "Cliente");
            assertThat(new CustomerQueries(jdbc, identity, clock).customers(false)).hasSize(1);
            request.setParameter("page", "1");
            assertThat(new CustomerQueries(jdbc, identity, clock).customers(false)).isEmpty();
            request.setParameter("page", "0");
            request.setParameter("q", "%");
            assertThat(new CustomerQueries(jdbc, identity, clock).customers(false)).isEmpty();
            request.setParameter("q", "");
            when(identity.permits(anyString())).thenReturn(false);
            assertThat(
                            new ArticleQueries(jdbc, identity, clock)
                                    .articles("VENDEDOR", false)
                                    .getFirst())
                    .doesNotContainKeys("costo", "margen_porcentaje");
            assertThat(new DashboardQueries(jdbc, identity, clock).summary())
                    .doesNotContainKey("margen_bruto");
        } finally {
            org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes();
        }
    }

    @Test
    void realHttpSessionRejectsForgeryAndReplaysOneBusinessOperation() throws Exception {
        String password = "Clave de prueba segura 2026";
        jdbc.update(
                "UPDATE usuarios SET password_hash=? WHERE id=1",
                new PasswordHasher().hash(password));
        var application =
                new org.springframework.boot.SpringApplication(ar.com.alon.AlonApplication.class);
        try (var context =
                application.run(
                        "--server.port=0",
                        "--spring.datasource.url=" + System.getenv("ALON_TEST_MYSQL_URL"),
                        "--spring.datasource.username="
                                + System.getenv().getOrDefault("ALON_TEST_MYSQL_USER", "root"),
                        "--spring.datasource.password="
                                + System.getenv().getOrDefault("ALON_TEST_MYSQL_PASSWORD", ""),
                        "--spring.main.banner-mode=off")) {
            String origin =
                    "http://127.0.0.1:" + context.getEnvironment().getProperty("local.server.port");
            var client =
                    java.net.http.HttpClient.newBuilder()
                            .cookieHandler(
                                    new java.net.CookieManager(
                                            null, java.net.CookiePolicy.ACCEPT_ALL))
                            .build();
            var mapper = JsonMapper.builder().build();
            var status =
                    client.send(
                            java.net.http.HttpRequest.newBuilder(
                                            java.net.URI.create(origin + "/api/v1/auth/status"))
                                    .GET()
                                    .build(),
                            java.net.http.HttpResponse.BodyHandlers.ofString());
            String csrf = mapper.readTree(status.body()).get("csrfToken").asString();
            var forbidden =
                    client.send(
                            java.net.http.HttpRequest.newBuilder(
                                            java.net.URI.create(origin + "/api/v1/auth/login"))
                                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString("{}"))
                                    .header("Content-Type", "application/json")
                                    .build(),
                            java.net.http.HttpResponse.BodyHandlers.ofString());
            assertThat(forbidden.statusCode()).isEqualTo(403);
            var login =
                    client.send(
                            java.net.http.HttpRequest.newBuilder(
                                            java.net.URI.create(origin + "/api/v1/auth/login"))
                                    .POST(
                                            java.net.http.HttpRequest.BodyPublishers.ofString(
                                                    mapper.writeValueAsString(
                                                            Map.of(
                                                                    "username",
                                                                    "owner",
                                                                    "password",
                                                                    password))))
                                    .header("Content-Type", "application/json")
                                    .header("X-CSRF-Token", csrf)
                                    .build(),
                            java.net.http.HttpResponse.BodyHandlers.ofString());
            assertThat(login.statusCode()).withFailMessage(login.body()).isEqualTo(200);
            var command =
                    java.net.http.HttpRequest.newBuilder(
                                    java.net.URI.create(origin + "/api/v1/operations/customers"))
                            .POST(
                                    java.net.http.HttpRequest.BodyPublishers.ofString(
                                            "{\"name\":\"Desde HTTP\",\"phone\":\"\",\"email\":\"\",\"creditLimit\":0}"))
                            .header("Content-Type", "application/json")
                            .header("X-CSRF-Token", csrf)
                            .header("Idempotency-Key", "http-operation-123456")
                            .build();
            var first = client.send(command, java.net.http.HttpResponse.BodyHandlers.ofString());
            var replay = client.send(command, java.net.http.HttpResponse.BodyHandlers.ofString());
            assertThat(first.statusCode()).withFailMessage(first.body()).isEqualTo(201);
            assertThat(mapper.readTree(replay.body())).isEqualTo(mapper.readTree(first.body()));
            assertThat(
                            jdbc.queryForObject(
                                    "SELECT COUNT(*) FROM clientes WHERE nombre='Desde HTTP'",
                                    Integer.class))
                    .isEqualTo(1);
            jdbc.update("UPDATE usuarios SET activo=FALSE WHERE id=1");
            var denied =
                    client.send(
                            java.net.http.HttpRequest.newBuilder(
                                            java.net.URI.create(origin + "/api/v1/demo/customers"))
                                    .GET()
                                    .build(),
                            java.net.http.HttpResponse.BodyHandlers.ofString());
            assertThat(denied.statusCode()).isEqualTo(401);
        }
    }

    @Test
    void legacyBaselinePreservesDataAndCompatibilityScriptsAreRepeatable() throws Exception {
        var old = Flyway.configure().dataSource(source).cleanDisabled(false).target("1").load();
        old.clean();
        old.migrate();
        jdbc.update("UPDATE comercios SET nombre='Instalación anterior' WHERE id=1");
        jdbc.execute("DROP TABLE flyway_schema_history");
        for (String file :
                List.of("V005__sesiones_caja_mismo_dia.sql", "V006__fecha_hora_ventas.sql")) {
            String sql =
                    java.nio.file.Files.readString(
                                    java.nio.file.Path.of("../database/migrations/" + file))
                            .replace("USE alon_demo;", "");
            try (var connection = source.getConnection()) {
                var resource =
                        new org.springframework.core.io.ByteArrayResource(
                                sql.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(
                        connection,
                        new org.springframework.core.io.support.EncodedResource(
                                resource, java.nio.charset.StandardCharsets.UTF_8));
            }
        }
        new ar.com.alon.config.LegacySchemaAdoption()
                .adoptLegacySchema()
                .migrate(Flyway.configure().dataSource(source).load());
        assertThat(jdbc.queryForObject("SELECT nombre FROM comercios WHERE id=1", String.class))
                .isEqualTo("Instalación anterior");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='usuarios' AND column_name='auth_version'",
                                Integer.class))
                .isEqualTo(1);
    }

    @Test
    void optionalDemoDataLoadsIntoTheMigratedSchema() throws Exception {
        String sql =
                java.nio.file.Files.readString(java.nio.file.Path.of("../database/demo-data.sql"))
                        .replace("USE alon_demo;", "");
        try (var connection = source.getConnection()) {
            var resource =
                    new org.springframework.core.io.ByteArrayResource(
                            sql.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(
                    connection,
                    new org.springframework.core.io.support.EncodedResource(
                            resource, java.nio.charset.StandardCharsets.UTF_8));
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ventas", Integer.class))
                .isEqualTo(1000);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM articulos", Integer.class))
                .isEqualTo(100);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cajas", Integer.class)).isEqualTo(10);
    }
}
