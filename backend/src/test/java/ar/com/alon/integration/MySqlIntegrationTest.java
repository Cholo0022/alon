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
import org.springframework.mock.web.MockHttpServletResponse;
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
                                                    "licencias",
                                                    "security_recovery_codes",
                                                    "security_browsers",
                                                    "security_events",
                                                    "security_mail_queue",
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
                                                    "pedido_tienda_detalle",
                                                    "articulo_imagenes",
                                                    "pedidos_tienda",
                                                    "compradores_tienda",
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
        when(identity.user()).thenReturn(Map.of("rol", "SUPER", "id", 1L, "comercio_id", 1L));
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
                "INSERT INTO articulos(id,comercio_id,codigo,nombre,rubro_id,proveedor_principal_id,unidad,costo,precio_venta,cargar_web,redondeo_precio) VALUES(1,1,'A1','Artículo',1,1,'UNIDAD',0,200,TRUE,FALSE)");
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

    private ar.com.alon.storefront.ShopService shop() {
        var accounts =
                new ar.com.alon.storefront.ShopAccountService(jdbc, mock(PasswordHasher.class), 1);
        return new ar.com.alon.storefront.ShopService(jdbc, accounts);
    }

    private Map<String, Object> buyer() {
        jdbc.update(
                "INSERT IGNORE INTO compradores_tienda(id,comercio_id,nombre,apellido,dni,domicilio,telefono,password_hash) VALUES(1,1,'Ana','Prueba','30111222','Calle de prueba 123','+5491100000000','unused')");
        return jdbc.queryForMap("SELECT * FROM compradores_tienda WHERE id=1");
    }

    private ar.com.alon.storefront.ShopService.Checkout shopInput(String units) {
        return new ar.com.alon.storefront.ShopService.Checkout(
                List.of(new ar.com.alon.storefront.ShopService.Line(1, d(units), d("200"))),
                "EFECTIVO");
    }

    private ar.com.alon.storefront.StoreOrderService storeOrders() {
        return new ar.com.alon.storefront.StoreOrderService(
                jdbc,
                identity,
                clock,
                new AuditLog(jdbc, identity, JsonMapper.builder().build()),
                sales,
                customers);
    }

    @Test
    void shopSubmissionKeepsStockAndReplaysOneOrder() {
        jdbc.update("UPDATE articulos SET stock=10 WHERE id=1");
        var buyer = buyer();
        var service = shop();
        var first =
                transaction.execute(
                        s -> service.checkout(shopInput("2"), buyer, "shop-request-123456"));
        var replay =
                transaction.execute(
                        s -> service.checkout(shopInput("2"), buyer, "shop-request-123456"));
        assertThat(first).isEqualTo(replay);
        assertThat(stock()).isEqualByComparingTo("10");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pedidos_tienda", Integer.class))
                .isEqualTo(1);
        assertThatThrownBy(
                        () ->
                                transaction.execute(
                                        s ->
                                                service.checkout(
                                                        shopInput("3"),
                                                        buyer,
                                                        "shop-request-123456")))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }

    @Test
    void storeApprovalDeductsOnceAndRecordsStaff() {
        transaction.execute(
                s -> cash.openCash(new CashOpenInput(LocalDate.now(clock), BigDecimal.ZERO)));
        jdbc.update("UPDATE articulos SET stock=10 WHERE id=1");
        var buyer = buyer();
        long id =
                ((Number)
                                transaction
                                        .execute(
                                                s ->
                                                        shop().checkout(
                                                                        shopInput("2"),
                                                                        buyer,
                                                                        "approve-order-123456"))
                                        .get("id"))
                        .longValue();
        transaction.execute(s -> storeOrders().approve(id, "EFECTIVO"));
        assertThat(stock()).isEqualByComparingTo("8");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT m.usuario_id FROM movimientos_stock m JOIN ventas v ON v.numero=m.referencia AND v.comercio_id=m.comercio_id WHERE v.pedido_tienda_id=?",
                                Long.class,
                                id))
                .isEqualTo(1L);
        assertThatThrownBy(() -> transaction.execute(s -> storeOrders().approve(id, "EFECTIVO")))
                .isInstanceOf(BusinessConflict.class);
        assertThat(stock()).isEqualByComparingTo("8");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM movimientos_stock", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void storeShortageRollsBackWholeOrderAndExclusionPreservesStock() {
        transaction.execute(
                s -> cash.openCash(new CashOpenInput(LocalDate.now(clock), BigDecimal.ZERO)));
        jdbc.update("UPDATE articulos SET stock=10 WHERE id=1");
        jdbc.update(
                "INSERT INTO articulos(id,comercio_id,codigo,nombre,rubro_id,proveedor_principal_id,unidad,costo,precio_venta,stock,cargar_web) VALUES(2,1,'A2','Otro artículo',1,1,'UNIDAD',0,100,3,TRUE)");
        var buyer = buyer();
        var input =
                new ar.com.alon.storefront.ShopService.Checkout(
                        List.of(
                                new ar.com.alon.storefront.ShopService.Line(1, d("2"), d("200")),
                                new ar.com.alon.storefront.ShopService.Line(2, d("2"), d("100"))),
                        "EFECTIVO");
        long id =
                ((Number)
                                transaction
                                        .execute(
                                                s ->
                                                        shop().checkout(
                                                                        input,
                                                                        buyer,
                                                                        "shortage-order-123456"))
                                        .get("id"))
                        .longValue();
        jdbc.update("UPDATE articulos SET stock=0 WHERE id=2");
        assertThatThrownBy(() -> transaction.execute(s -> storeOrders().approve(id, "EFECTIVO")))
                .isInstanceOf(BusinessConflict.class);
        assertThat(stock()).isEqualByComparingTo("10");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM movimientos_stock", Integer.class))
                .isZero();
        long line =
                jdbc.queryForObject(
                        "SELECT id FROM pedido_tienda_detalle WHERE pedido_id=? AND articulo_id=2",
                        Long.class,
                        id);
        transaction.execute(s -> storeOrders().exclude(id, line));
        transaction.execute(s -> storeOrders().approve(id, "EFECTIVO"));
        assertThat(stock()).isEqualByComparingTo("8");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT total_estimado FROM pedidos_tienda WHERE id=?",
                                BigDecimal.class,
                                id))
                .isEqualByComparingTo("400");
    }

    @Test
    void shopRejectsStalePricesAndDuplicateProducts() {
        jdbc.update("UPDATE articulos SET stock=10 WHERE id=1");
        var buyer = buyer();
        var stale =
                new ar.com.alon.storefront.ShopService.Checkout(
                        List.of(new ar.com.alon.storefront.ShopService.Line(1, d("2"), d("1"))),
                        "EFECTIVO");
        assertThatThrownBy(
                        () ->
                                transaction.execute(
                                        s -> shop().checkout(stale, buyer, "stale-price-123456")))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        var duplicate =
                new ar.com.alon.storefront.ShopService.Checkout(
                        List.of(
                                shopInput("2").lines().getFirst(),
                                shopInput("2").lines().getFirst()),
                        "EFECTIVO");
        assertThatThrownBy(
                        () ->
                                transaction.execute(
                                        s ->
                                                shop().checkout(
                                                                duplicate,
                                                                buyer,
                                                                "duplicate-line-123456")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pedidos_tienda", Integer.class))
                .isZero();
    }

    @Test
    void shopCatalogHidesUnavailableInactiveAndOtherStores() {
        assertThat((List<?>) shop().catalog("", "", 0).get("items")).isEmpty();
        jdbc.update("UPDATE articulos SET stock=10 WHERE id=1");
        var result = shop().catalog("", "", 0);
        assertThat((List<?>) result.get("items")).hasSize(1);
        assertThat(((Map<?, ?>) ((List<?>) result.get("items")).getFirst()).containsKey("costo"))
                .isFalse();
        jdbc.update("UPDATE articulos SET estado='INACTIVO' WHERE id=1");
        assertThat((List<?>) shop().catalog("", "", 0).get("items")).isEmpty();
        when(identity.storeId()).thenReturn(2L);
        assertThatThrownBy(() -> storeOrders().detail(1))
                .isInstanceOf(org.springframework.dao.EmptyResultDataAccessException.class);
    }

    @Test
    void unpublishedArticlesAreExcludedFromCatalogCartAndCheckout() {
        jdbc.update("UPDATE articulos SET stock=10,cargar_web=FALSE WHERE id=1");
        assertThat((List<?>) shop().catalog("", "", 0).get("items")).isEmpty();
        assertThat((List<?>) shop().catalog("", "", 0).get("categories")).isEmpty();
        assertThat(shop().catalog("", "", 0).get("total")).isEqualTo(0L);
        assertThat(shop().cart(new ar.com.alon.storefront.ShopService.CartInput(List.of(1L))))
                .isEmpty();
        var buyer = buyer();
        assertThatThrownBy(
                        () ->
                                transaction.execute(
                                        s ->
                                                shop().checkout(
                                                                shopInput("1"),
                                                                buyer,
                                                                "unpublished-123456")))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("ya no está disponible");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pedidos_tienda", Long.class)).isZero();
        jdbc.update("UPDATE articulos SET cargar_web=TRUE WHERE id=1");
        assertThat((List<?>) shop().catalog("", "", 0).get("items")).hasSize(1);
    }

    @Test
    void shopperChangesOwnPasswordWithoutOldPasswordAndRevokesOtherSessions() {
        var accounts = new ar.com.alon.storefront.ShopAccountService(jdbc, new PasswordHasher(), 1);
        var user =
                transaction.execute(
                        status ->
                                accounts.register(
                                        new ar.com.alon.storefront.ShopAccountService.Registration(
                                                "Ana",
                                                "Prueba",
                                                "30111222",
                                                "Calle 123",
                                                "1000",
                                                "Buenos Aires",
                                                "Buenos Aires",
                                                "Argentina",
                                                "+5491100000000",
                                                "87654321")));
        long id = ((Number) user.get("id")).longValue();
        var first = new MockHttpServletRequest();
        var second = new MockHttpServletRequest();
        accounts.start(user, first);
        accounts.start(user, second);
        assertThat(accounts.current(first)).isNotEmpty();
        assertThat(accounts.current(second)).isNotEmpty();
        assertThatThrownBy(() -> accounts.changePassword("12345678", new MockHttpServletRequest()))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThatThrownBy(() -> accounts.changePassword("1234567", first))
                .isInstanceOf(IllegalArgumentException.class);
        jdbc.update(
                "INSERT INTO compradores_tienda(id,comercio_id,nombre,apellido,dni,domicilio,telefono,password_hash) VALUES(9999,1,'Otro','Comprador','30111223','Calle 124','+5491100000001','hash-ajeno-no-modificar')");
        var changed = transaction.execute(status -> accounts.changePassword("12345678", first));
        assertThat(changed).containsEntry("id", id).doesNotContainKey("password_hash");
        assertThat(accounts.current(first)).isNotEmpty();
        assertThat(accounts.current(second)).isEmpty();
        assertThat(
                        accounts.login(
                                new ar.com.alon.storefront.ShopAccountService.Login(
                                        "+5491100000000", "12345678")))
                .containsEntry("id", id);
        assertThatThrownBy(
                        () ->
                                accounts.login(
                                        new ar.com.alon.storefront.ShopAccountService.Login(
                                                "+5491100000000", "87654321")))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT password_hash FROM compradores_tienda WHERE id=9999",
                                String.class))
                .isEqualTo("hash-ajeno-no-modificar");
    }

    @Test
    void registrationStoresLocationAndUsesItInOrderAddress() {
        var accounts = new ar.com.alon.storefront.ShopAccountService(jdbc, new PasswordHasher(), 1);
        var user =
                transaction.execute(
                        s ->
                                accounts.register(
                                        new ar.com.alon.storefront.ShopAccountService.Registration(
                                                "Ana",
                                                "Prueba",
                                                "30111222",
                                                "Calle 123",
                                                "11000",
                                                "Montevideo",
                                                "Montevideo",
                                                "Uruguay",
                                                "+598 99 123 456",
                                                "Shopper-test-2026!")));
        assertThat(user)
                .containsEntry("codigo_postal", "11000")
                .containsEntry("ciudad", "Montevideo")
                .containsEntry("provincia", "Montevideo")
                .containsEntry("pais", "Uruguay");
        jdbc.update("UPDATE articulos SET stock=10 WHERE id=1");
        transaction.execute(s -> shop().checkout(shopInput("1"), user, "location-order-123456"));
        assertThat(jdbc.queryForObject("SELECT domicilio FROM pedidos_tienda", String.class))
                .isEqualTo("Calle 123, 11000, Montevideo, Montevideo, Uruguay");
    }

    @Test
    void cancellingAnOrderDoesNotDeductStock() {
        jdbc.update("UPDATE articulos SET stock=10 WHERE id=1");
        var buyer = buyer();
        long id =
                ((Number)
                                transaction
                                        .execute(
                                                s ->
                                                        shop().checkout(
                                                                        shopInput("2"),
                                                                        buyer,
                                                                        "cancel-order-123456"))
                                        .get("id"))
                        .longValue();
        transaction.execute(s -> storeOrders().cancel(id));
        assertThat(stock()).isEqualByComparingTo("10");
        assertThatThrownBy(() -> transaction.execute(s -> storeOrders().approve(id, "EFECTIVO")))
                .isInstanceOf(BusinessConflict.class);
    }

    @Test
    void concurrentStoreApprovalsCannotOversell() throws Exception {
        transaction.execute(
                s -> cash.openCash(new CashOpenInput(LocalDate.now(clock), BigDecimal.ZERO)));
        jdbc.update("UPDATE articulos SET stock=2 WHERE id=1");
        var buyer = buyer();
        long a =
                ((Number)
                                transaction
                                        .execute(
                                                s ->
                                                        shop().checkout(
                                                                        shopInput("2"),
                                                                        buyer,
                                                                        "concurrent-shop-a-123456"))
                                        .get("id"))
                        .longValue();
        long b =
                ((Number)
                                transaction
                                        .execute(
                                                s ->
                                                        shop().checkout(
                                                                        shopInput("2"),
                                                                        buyer,
                                                                        "concurrent-shop-b-123456"))
                                        .get("id"))
                        .longValue();
        try (var executor = Executors.newFixedThreadPool(2)) {
            var tasks =
                    List.of(a, b).stream()
                            .map(
                                    id ->
                                            executor.submit(
                                                    () -> {
                                                        try {
                                                            transaction.execute(
                                                                    s ->
                                                                            storeOrders()
                                                                                    .approve(
                                                                                            id,
                                                                                            "EFECTIVO"));
                                                            return true;
                                                        } catch (BusinessConflict e) {
                                                            return false;
                                                        }
                                                    }))
                            .toList();
            int successes = 0;
            for (var task : tasks) if (task.get(30, TimeUnit.SECONDS)) successes++;
            assertThat(successes).isEqualTo(1);
            assertThat(stock()).isEqualByComparingTo("0");
        }
    }

    @Test
    void imageUploadChecksBytesAndRollsBackFile(
            @org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
        var storage = new ar.com.alon.articles.ArticleImageStorage(directory.toString());
        var photos =
                new ar.com.alon.articles.ArticlePhotoService(
                        jdbc,
                        identity,
                        storage,
                        new AuditLog(jdbc, identity, JsonMapper.builder().build()));
        var images =
                new ar.com.alon.articles.ArticleImageController(
                        jdbc, photos, storage, mock(CommandExecutor.class));
        var png = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(
                new java.awt.image.BufferedImage(2, 2, java.awt.image.BufferedImage.TYPE_INT_RGB),
                "png",
                png);
        var input =
                new ar.com.alon.articles.dto.ArticlePhotoInput(
                        null, Base64.getEncoder().encodeToString(png.toByteArray()), false);
        String key =
                transaction.execute(
                        s -> {
                            return String.valueOf(
                                    photos.replace(1, List.of(input)).getFirst().get("key"));
                        });
        assertThat(images.image(key).getBody()).isNotEmpty();
        assertThatThrownBy(() -> images.image("../../outside.png"))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        transaction.execute(
                s -> {
                    photos.replace(1, List.of(input));
                    s.setRollbackOnly();
                    return null;
                });
        try (var files = java.nio.file.Files.list(directory)) {
            assertThat(files.count()).isEqualTo(1);
        }
        var invalid =
                new ar.com.alon.articles.dto.ArticlePhotoInput(
                        null, Base64.getEncoder().encodeToString("<svg></svg>".getBytes()), false);
        assertThatThrownBy(
                        () ->
                                transaction.execute(
                                        s -> {
                                            return photos.replace(
                                                    1, List.of(input, input, input, invalid));
                                        }))
                .isInstanceOf(IllegalArgumentException.class);
        try (var files = java.nio.file.Files.list(directory)) {
            assertThat(files.count()).isEqualTo(1);
        }
        assertThat(photos.list(1)).hasSize(1);
        var four = transaction.execute(s -> photos.replace(1, List.of(input, input, input, input)));
        assertThat(four).hasSize(4);
        assertThatThrownBy(
                        () ->
                                transaction.execute(
                                        s ->
                                                photos.replace(
                                                        1,
                                                        List.of(
                                                                input, input, input, input,
                                                                input))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(photos.list(1)).hasSize(4);
        var reordered = new java.util.ArrayList<ar.com.alon.articles.dto.ArticlePhotoInput>();
        for (int i = 3; i >= 0; i--)
            reordered.add(
                    new ar.com.alon.articles.dto.ArticlePhotoInput(
                            String.valueOf(four.get(i).get("key")), null, false));
        transaction.execute(s -> photos.replace(1, reordered));
        assertThat(
                        jdbc.queryForObject(
                                "SELECT imagen_clave FROM articulos WHERE id=1", String.class))
                .isEqualTo(four.get(3).get("key"));
        jdbc.update("UPDATE articulos SET stock=1 WHERE id=1");
        assertThat(
                        (List<?>)
                                ((Map<?, ?>)
                                                ((List<?>) shop().catalog("", "", 0).get("items"))
                                                        .getFirst())
                                        .get("photos"))
                .hasSize(4);
        var foreign =
                new ar.com.alon.articles.dto.ArticlePhotoInput(
                        java.util.UUID.randomUUID() + ".png", null, false);
        assertThatThrownBy(() -> transaction.execute(s -> photos.replace(1, List.of(foreign))))
                .isInstanceOf(IllegalArgumentException.class);
        transaction.execute(s -> photos.replace(1, List.of()));
        assertThat(photos.list(1)).isEmpty();
    }

    @Test
    void webApprovalRequiresPaymentAndTodaysOpenCashWithoutSideEffects() {
        jdbc.update("UPDATE articulos SET stock=10 WHERE id=1");
        var buyer = buyer();
        long id =
                ((Number)
                                transaction
                                        .execute(
                                                s ->
                                                        shop().checkout(
                                                                        shopInput("2"),
                                                                        buyer,
                                                                        "closed-web-order-123456"))
                                        .get("id"))
                        .longValue();
        assertThatThrownBy(() -> transaction.execute(s -> storeOrders().approve(id, null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () ->
                                transaction.execute(
                                        s -> storeOrders().approve(id, "CUENTA_CORRIENTE")))
                .isInstanceOf(IllegalArgumentException.class);
        transaction.execute(
                s ->
                        cash.openCash(
                                new CashOpenInput(
                                        LocalDate.now(clock).minusDays(1), BigDecimal.ZERO)));
        for (String method : List.of("EFECTIVO", "TRANSFERENCIA", "MERCADO_PAGO"))
            assertThatThrownBy(() -> transaction.execute(s -> storeOrders().approve(id, method)))
                    .isInstanceOf(BusinessConflict.class);
        assertThat(stock()).isEqualByComparingTo("10");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ventas", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM movimientos_caja", Integer.class))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM movimientos_stock", Integer.class))
                .isZero();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT estado FROM pedidos_tienda WHERE id=?", String.class, id))
                .isEqualTo("PENDIENTE_CONFIRMACION");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM clientes", Integer.class))
                .isEqualTo(1);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(
            strings = {"EFECTIVO", "TRANSFERENCIA", "MERCADO_PAGO"})
    void webApprovalCreatesLinkedSaleAndCashAtOrderPrices(String method) {
        jdbc.update("UPDATE articulos SET stock=10 WHERE id=1");
        var buyer = buyer();
        long cashId =
                ((Number)
                                transaction
                                        .execute(
                                                s ->
                                                        cash.openCash(
                                                                new CashOpenInput(
                                                                        LocalDate.now(clock),
                                                                        BigDecimal.ZERO)))
                                        .get("id"))
                        .longValue();
        long orderId =
                ((Number)
                                transaction
                                        .execute(
                                                s ->
                                                        shop().checkout(
                                                                        shopInput("2"),
                                                                        buyer,
                                                                        "paid-web-order-123456"))
                                        .get("id"))
                        .longValue();
        jdbc.update("UPDATE articulos SET precio_venta=250 WHERE id=1");
        var result = transaction.execute(s -> storeOrders().approve(orderId, method));
        long saleId = ((Number) result.get("venta_id")).longValue();
        var sale = jdbc.queryForMap("SELECT * FROM ventas WHERE id=?", saleId);
        assertThat(sale.get("origen")).isEqualTo("WEB");
        assertThat(sale.get("pedido_tienda_id")).isEqualTo(orderId);
        assertThat(sale.get("medio_pago")).isEqualTo(method);
        assertThat((BigDecimal) sale.get("total")).isEqualByComparingTo("400");
        assertThat(String.valueOf(sale.get("numero"))).startsWith("VW-");
        var movement =
                jdbc.queryForMap(
                        "SELECT * FROM movimientos_caja WHERE referencia=?", sale.get("numero"));
        assertThat(movement.get("caja_id")).isEqualTo(cashId);
        assertThat(movement.get("categoria")).isEqualTo("VENTA");
        assertThat(movement.get("medio_pago")).isEqualTo(method);
        assertThat((BigDecimal) movement.get("importe")).isEqualByComparingTo("400");
        assertThat(stock()).isEqualByComparingTo("8");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT venta_id FROM pedidos_tienda WHERE id=?",
                                Long.class,
                                orderId))
                .isEqualTo(saleId);
        assertThatThrownBy(() -> transaction.execute(s -> storeOrders().approve(orderId, method)))
                .isInstanceOf(BusinessConflict.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ventas", Integer.class)).isEqualTo(1);
        transaction.execute(s -> sales.cancelSale(saleId));
        assertThat(stock()).isEqualByComparingTo("10");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT estado FROM pedidos_tienda WHERE id=?",
                                String.class,
                                orderId))
                .isEqualTo("CANCELADO");
    }

    @Test
    void pendingOrderCountCoversAllPagesAndOnlyTheCurrentStore() {
        var rows = new java.util.ArrayList<Object[]>();
        for (int i = 0; i < 150; i++)
            rows.add(
                    new Object[] {
                        1, "Cliente de prueba", "Sin contacto", "Pedido de prueba", BigDecimal.ZERO
                    });
        jdbc.batchUpdate(
                "INSERT INTO pedidos_tienda(comercio_id,nombre_cliente,contacto,detalle,total_estimado) VALUES(?,?,?,?,?)",
                rows);
        jdbc.update("INSERT INTO comercios(id,codigo,nombre) VALUES(2,'OTRO','Otro comercio')");
        jdbc.update(
                "INSERT INTO pedidos_tienda(comercio_id,nombre_cliente,contacto,detalle,total_estimado) VALUES(2,'Otro cliente','Sin contacto','Otro pedido',0)");
        var queries = new ar.com.alon.storefront.StoreOrderQueries(jdbc, identity, clock);
        assertThat(queries.pendingCount()).isEqualTo(150);
        jdbc.update(
                "UPDATE pedidos_tienda SET estado='CONFIRMADO' WHERE comercio_id=1 ORDER BY id LIMIT 1");
        jdbc.update(
                "UPDATE pedidos_tienda SET estado='CANCELADO' WHERE comercio_id=1 AND estado='PENDIENTE_CONFIRMACION' ORDER BY id LIMIT 1");
        assertThat(queries.pendingCount()).isEqualTo(148);
        when(identity.storeId()).thenReturn(2L);
        assertThat(queries.pendingCount()).isEqualTo(1);
        jdbc.update("UPDATE pedidos_tienda SET estado='CANCELADO' WHERE comercio_id=2");
        assertThat(queries.pendingCount()).isZero();
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
    void concurrentOwnerRoleChangesPreserveOneOwner() throws Exception {
        jdbc.update(
                "INSERT INTO usuarios(id,comercio_id,usuario,nombre,password_hash,rol,permisos) VALUES(2,1,'other','Otro','unused','DUENO','ventas')");
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<Boolean> first = () -> changeOwnerToSeller(1);
            Callable<Boolean> second = () -> changeOwnerToSeller(2);
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

    private boolean changeOwnerToSeller(long id) {
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
    void tenStorefrontTemplatesPersistWithoutChangingCommerceData() {
        when(identity.user()).thenReturn(Map.of("rol", "DUENO"));
        jdbc.update("INSERT INTO comercios(id,codigo,nombre) VALUES(2,'OTRO','Otro comercio')");
        var audit = new AuditLog(jdbc, identity, JsonMapper.builder().build());
        var business = new ar.com.alon.business.BusinessService(jdbc, identity, audit);
        var article = jdbc.queryForMap("SELECT * FROM articulos WHERE id=1");
        assertThat(business.current()).containsEntry("plantilla_tienda", "clasica");
        for (String template :
                List.of(
                        "clasica",
                        "minimal",
                        "boutique",
                        "urbana",
                        "editorial",
                        "mercado",
                        "natural",
                        "tecnologica",
                        "artesanal",
                        "vitrina")) {
            var input =
                    new ar.com.alon.business.BusinessService.Input(
                            "Comercio",
                            "Slogan",
                            "#006b73",
                            "#102a43",
                            "#f4f7f9",
                            template,
                            "Bienvenidos a la tienda",
                            "Nuestros productos");
            transaction.execute(status -> business.update(input));
            assertThat(business.read(1))
                    .containsEntry("plantilla_tienda", template)
                    .containsEntry("titulo_tienda", "Bienvenidos a la tienda");
            assertThat(jdbc.queryForMap("SELECT * FROM articulos WHERE id=1")).isEqualTo(article);
            assertThat(business.read(2)).containsEntry("plantilla_tienda", "clasica");
        }
        transaction.execute(
                status ->
                        business.update(
                                new ar.com.alon.business.BusinessService.Input(
                                        "Comercio", "Slogan", "#006b73", "#102a43", "#f4f7f9")));
        assertThat(business.current()).containsEntry("plantilla_tienda", "vitrina");
        assertThatThrownBy(
                        () ->
                                transaction.execute(
                                        status ->
                                                business.update(
                                                        new ar.com.alon.business.BusinessService
                                                                .Input(
                                                                "Comercio",
                                                                "Slogan",
                                                                "#006b73",
                                                                "#102a43",
                                                                "#f4f7f9",
                                                                "inexistente",
                                                                "Título",
                                                                "Descripción"))))
                .isInstanceOf(IllegalArgumentException.class);
        when(identity.user()).thenReturn(Map.of("rol", "VENDEDOR"));
        assertThatThrownBy(
                        () ->
                                transaction.execute(
                                        status ->
                                                business.update(
                                                        new ar.com.alon.business.BusinessService
                                                                .Input(
                                                                "Comercio",
                                                                "Slogan",
                                                                "#006b73",
                                                                "#102a43",
                                                                "#f4f7f9",
                                                                "minimal",
                                                                "Título",
                                                                "Descripción"))))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThat(business.current()).containsEntry("plantilla_tienda", "vitrina");
    }

    @Test
    void businessBrandIsSharedWithShopAndRestrictedToOwner() {
        when(identity.user()).thenReturn(Map.of("rol", "DUENO"));
        jdbc.update("INSERT INTO comercios(id,codigo,nombre) VALUES(2,'OTRO','Otro comercio')");
        var audit = new AuditLog(jdbc, identity, JsonMapper.builder().build());
        var business = new ar.com.alon.business.BusinessService(jdbc, identity, audit);
        var input =
                new ar.com.alon.business.BusinessService.Input(
                        "  Almacén Norte  ", "Siempre cerca", "#ABCDEF", "#102030", "#fefefe");
        var saved = transaction.execute(s -> business.update(input));
        assertThat(saved)
                .containsEntry("nombre", "Almacén Norte")
                .containsEntry("slogan", "Siempre cerca")
                .containsEntry("color_principal", "#abcdef");
        assertThat((Map<?, ?>) shop().catalog("", "", 0).get("store")).isEqualTo(saved);
        assertThat(business.read(2))
                .containsEntry("nombre", "Otro comercio")
                .containsEntry("slogan", "");
        when(identity.user()).thenReturn(Map.of("rol", "VENDEDOR"));
        assertThatThrownBy(() -> transaction.execute(s -> business.update(input)))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }

    @Test
    void categoryIncreaseIsScopedReviewedAndIdempotent() {
        var audit = new AuditLog(jdbc, identity, JsonMapper.builder().build());
        var prices = new ar.com.alon.articles.CategoryPriceService(jdbc, identity, audit);
        jdbc.update(
                "INSERT INTO rubros(id,comercio_id,codigo,nombre) VALUES(2,1,'R2','Otro rubro')");
        jdbc.update(
                "INSERT INTO articulos(id,comercio_id,codigo,nombre,rubro_id,proveedor_principal_id,unidad,costo,precio_venta,redondeo_precio,stock) VALUES(2,1,'A2','Con redondeo',1,1,'UNIDAD',50,105,TRUE,5),(3,1,'A3','Otro rubro',2,1,'UNIDAD',50,100,FALSE,5),(4,1,'A4','Inactivo',1,1,'UNIDAD',50,100,FALSE,5)");
        jdbc.update("UPDATE articulos SET estado='INACTIVO' WHERE id=4");
        var input = new ar.com.alon.articles.CategoryPriceService.Input(1, d("10"), null);
        var preview = prices.preview(input);
        assertThat(preview.get("count")).isEqualTo(2);
        jdbc.update("UPDATE articulos SET precio_venta=201 WHERE id=1");
        var stale =
                new ar.com.alon.articles.CategoryPriceService.Input(
                        1, d("10"), preview.get("expectedHash").toString());
        assertThatThrownBy(() -> transaction.execute(s -> prices.apply(stale)))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT precio_venta FROM articulos WHERE id=2", BigDecimal.class))
                .isEqualByComparingTo("105");
        var fresh = prices.preview(input);
        var reviewed =
                new ar.com.alon.articles.CategoryPriceService.Input(
                        1, d("10"), fresh.get("expectedHash").toString());
        var request = new MockHttpServletRequest();
        request.addHeader("Idempotency-Key", "category-prices-123456");
        var commands = new CommandExecutor(jdbc, identity, request, JsonMapper.builder().build());
        for (int i = 0; i < 2; i++)
            transaction.execute(
                    s ->
                            commands.execute(
                                    "categoryIncrease", reviewed, () -> prices.apply(reviewed)));
        assertThat(
                        jdbc.queryForObject(
                                "SELECT precio_venta FROM articulos WHERE id=1", BigDecimal.class))
                .isEqualByComparingTo("221.10");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT precio_venta FROM articulos WHERE id=2", BigDecimal.class))
                .isEqualByComparingTo("120");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT precio_venta FROM articulos WHERE id=3", BigDecimal.class))
                .isEqualByComparingTo("100");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT precio_venta FROM articulos WHERE id=4", BigDecimal.class))
                .isEqualByComparingTo("100");
        assertThat(jdbc.queryForObject("SELECT stock FROM articulos WHERE id=2", BigDecimal.class))
                .isEqualByComparingTo("5");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM auditoria_operaciones WHERE accion='AUMENTO_RUBRO'",
                                Integer.class))
                .isEqualTo(2);
        when(identity.storeId()).thenReturn(2L);
        assertThatThrownBy(() -> prices.preview(input))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private PurchaseInput purchaseWithPayment(String document, String condition, String method) {
        return new PurchaseInput(
                1,
                "FACTURA",
                document,
                LocalDate.now(clock),
                List.of(new PurchaseLineInput(1, d("2"), d("100"))),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                condition,
                method);
    }

    @Test
    void purchaseCostChoiceOnlyChangesCostAndAlwaysReceivesStock() {
        jdbc.update(
                "UPDATE articulos SET stock=10,costo=50,precio_venta=222,flete_unitario=7,flete_porcentaje=8,margen_porcentaje=40,redondeo_precio=TRUE WHERE id=1");
        var original =
                jdbc.queryForMap(
                        "SELECT precio_venta,flete_unitario,flete_porcentaje,margen_porcentaje,redondeo_precio FROM articulos WHERE id=1");
        for (boolean update : List.of(false, true)) {
            buy(
                    new PurchaseInput(
                            1,
                            "REMITO",
                            "COST-" + update,
                            LocalDate.now(clock),
                            List.of(new PurchaseLineInput(1, d("2"), d("80"))),
                            d("15"),
                            d("0"),
                            "CUENTA_CORRIENTE",
                            null,
                            update));
            assertThat(cost()).isEqualByComparingTo(update ? "80" : "50");
            assertThat(stock()).isEqualByComparingTo(update ? "14" : "12");
            assertThat(
                            jdbc.queryForMap(
                                    "SELECT precio_venta,flete_unitario,flete_porcentaje,margen_porcentaje,redondeo_precio FROM articulos WHERE id=1"))
                    .isEqualTo(original);
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pagos_proveedores", Integer.class))
                .isZero();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT saldo_actual FROM proveedores WHERE id=1",
                                BigDecimal.class))
                .isEqualByComparingTo("350");
    }

    @Test
    void defaultPurchaseSupplierIsReusableAndBelongsToCurrentStore() {
        var first = transaction.execute(s -> purchases.defaultSupplier());
        var again = transaction.execute(s -> purchases.defaultSupplier());
        assertThat(first).isEqualTo(again).containsEntry("nombre", "Proveedor sin nombre");
        jdbc.update("INSERT INTO comercios(id,codigo,nombre) VALUES(2,'OTRO','Otro')");
        when(identity.storeId()).thenReturn(2L);
        var other = transaction.execute(s -> purchases.defaultSupplier());
        assertThat(other.get("id")).isNotEqualTo(first.get("id"));
        assertThat(
                        jdbc.queryForObject(
                                "SELECT comercio_id FROM proveedores WHERE id=?",
                                Long.class,
                                other.get("id")))
                .isEqualTo(2L);
    }

    @Test
    void purchaseAndCashHistoryShowLatestRegistrationsFirst() {
        long first = buy(purchase("FIRST", "1", "10", "0"));
        var older = purchase("BACKDATED", "1", "10", "0");
        long last =
                buy(
                        new PurchaseInput(
                                older.supplierId(),
                                older.documentType(),
                                older.documentNumber(),
                                LocalDate.now(clock).minusDays(5),
                                older.lines(),
                                older.extraCosts(),
                                older.paid(),
                                older.paymentType(),
                                older.paymentMethod()));
        var queries = new ar.com.alon.purchases.PurchaseQueries(jdbc, identity, clock);
        assertThat(queries.purchases())
                .extracting(row -> ((Number) row.get("id")).longValue())
                .containsExactly(last, first);
        transaction.execute(s -> cash.openCash(new CashOpenInput(LocalDate.now(clock), d("0"))));
        transaction.execute(s -> cash.closeCash(new CashCloseInput(LocalDate.now(clock), d("0"))));
        transaction.execute(s -> cash.openCash(new CashOpenInput(LocalDate.now(clock), d("0"))));
        transaction.execute(s -> cash.closeCash(new CashCloseInput(LocalDate.now(clock), d("0"))));
        var cashQueries = new ar.com.alon.cash.CashQueries(jdbc, identity, clock);
        assertThat(cashQueries.cash())
                .extracting(row -> ((Number) row.get("sesion")).intValue())
                .containsExactly(2, 1);
    }

    @Test
    void purchasePaymentOriginsRespectCashAndCredit() {
        var external =
                transaction.execute(
                        s ->
                                purchases.createPurchase(
                                        purchaseWithPayment("EXT", "EFECTIVO", "EFECTIVO")));
        long externalId = ((Number) external.get("id")).longValue();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT pagado FROM compras WHERE id=?",
                                BigDecimal.class,
                                externalId))
                .isEqualByComparingTo("200");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT caja_id FROM pagos_proveedores WHERE compra_id=?",
                                Long.class,
                                externalId))
                .isNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM movimientos_caja", Integer.class))
                .isZero();
        transaction.execute(
                s ->
                        purchases.createPurchase(
                                purchaseWithPayment("CRED", "CUENTA_CORRIENTE", "TRANSFERENCIA")));
        assertThat(
                        jdbc.queryForObject(
                                "SELECT saldo_actual FROM proveedores WHERE id=1",
                                BigDecimal.class))
                .isEqualByComparingTo("200");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pagos_proveedores", Integer.class))
                .isEqualTo(1);
        transaction.execute(s -> cash.openCash(new CashOpenInput(LocalDate.now(clock), d("1000"))));
        transaction.execute(
                s ->
                        purchases.createPurchase(
                                purchaseWithPayment("CAJA", "EFECTIVO_CAJA", "EFECTIVO")));
        assertThat(
                        jdbc.queryForObject(
                                "SELECT SUM(importe) FROM movimientos_caja WHERE tipo='EGRESO'",
                                BigDecimal.class))
                .isEqualByComparingTo("200");
        assertThat((BigDecimal) cash.cashSummary(LocalDate.now(clock)).get("efectivo_esperado"))
                .isEqualByComparingTo("800");
        var queries = new ar.com.alon.purchases.PurchaseQueries(jdbc, identity, clock);
        assertThat(queries.purchaseDetails(externalId))
                .containsEntry("condicion_pago", "EFECTIVO")
                .containsEntry("medio_pago", "EFECTIVO");
        assertThat(queries.purchases())
                .extracting(row -> row.get("condicion_pago"))
                .containsExactlyInAnyOrder("EFECTIVO", "CUENTA_CORRIENTE", "EFECTIVO_CAJA");
    }

    @Test
    void purchaseCashRequiresOpenCashAndValidMethod() {
        assertThatThrownBy(
                        () ->
                                transaction.execute(
                                        s ->
                                                purchases.createPurchase(
                                                        purchaseWithPayment(
                                                                "CERRADA",
                                                                "EFECTIVO_CAJA",
                                                                "EFECTIVO"))))
                .isInstanceOf(BusinessConflict.class);
        assertThatThrownBy(
                        () ->
                                transaction.execute(
                                        s ->
                                                purchases.createPurchase(
                                                        purchaseWithPayment(
                                                                "MEDIO",
                                                                "EFECTIVO_CAJA",
                                                                "TRANSFERENCIA"))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM compras", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pagos_proveedores", Integer.class))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM movimientos_stock", Integer.class))
                .isZero();
        assertThat(stock()).isEqualByComparingTo("0");
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
                        d("200"),
                        null,
                        false);
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

    /** Obtiene los campos de identidad que usa el ingreso sin leer la contraseña. */
    private Map<String, Object> securityUser(long id) {
        return jdbc.queryForMap(
                "SELECT id,comercio_id,usuario,nombre,rol,permisos,activo,auth_version,mfa_enabled,security_email FROM usuarios WHERE id=?",
                id);
    }

    @Test
    void securityRequiresEnrollmentAndApprovalAndRevokesBrowsers(
            @org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
        jdbc.update("UPDATE usuarios SET rol='SUPER' WHERE id=1");
        var crypto = new ar.com.alon.security.TotpSecurity(directory.resolve("mfa.key").toString());
        var mail = mock(ar.com.alon.security.SecurityNotifications.class);
        var service =
                new ar.com.alon.security.AccessSecurityService(
                        jdbc, auth, identity, crypto, clock, mail, false, true);
        var owner = new MockHttpServletRequest();
        owner.setRemoteAddr("127.0.0.1");
        transaction.execute(
                status -> {
                    service.begin(securityUser(1), owner, new MockHttpServletResponse());
                    return null;
                });
        assertThat(service.challenge(owner)).containsEntry("stage", "ENROLL");
        assertThat(owner.getSession().getAttribute("alonUser")).isNull();
        var denied = new MockHttpServletResponse();
        new ar.com.alon.security.PermissionFilter(auth, service)
                .doFilter(
                        ownerForPath(owner, "/api/v1/alon/sales"),
                        denied,
                        new org.springframework.mock.web.MockFilterChain());
        assertThat(denied.getStatus()).isEqualTo(401);
        String secret = service.enrollment(owner).get("secret").toString();
        var codes =
                transaction.execute(
                        status ->
                                service.confirmEnrollment(
                                        crypto.code(
                                                secret, clock.instant().getEpochSecond() / 30, 6),
                                        "owner@example.test",
                                        owner));
        assertThat(codes).hasSize(10).doesNotHaveDuplicates();
        assertThat(owner.getSession().getAttribute("alonUser")).isNull();
        transaction.execute(
                status -> {
                    service.advance(null, true, owner);
                    return null;
                });
        assertThat(service.validSession(securityUser(1), owner)).isTrue();
        assertThat(jdbc.queryForObject("SELECT mfa_secret FROM usuarios WHERE id=1", String.class))
                .doesNotContain(secret);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT security_email FROM usuarios WHERE id=1", String.class))
                .isEqualTo("owner@example.test");
        jdbc.update(
                "INSERT INTO usuarios(id,comercio_id,usuario,nombre,password_hash,rol,permisos) VALUES(2,1,'staff','Personal','unused','VENDEDOR','ventas')");
        var staff = new MockHttpServletRequest();
        staff.setRemoteAddr("192.0.2.10");
        staff.addHeader("User-Agent", "Navegador de prueba");
        var response = new MockHttpServletResponse();
        transaction.execute(
                status -> {
                    service.begin(securityUser(2), staff, response);
                    return null;
                });
        String token = response.getHeader("Set-Cookie").split(";", 2)[0].split("=", 2)[1];
        staff.setCookies(new jakarta.servlet.http.Cookie("ALON_BROWSER", token));
        assertThat(response.getHeader("Set-Cookie")).contains("HttpOnly", "SameSite=Strict");
        assertThat(service.challenge(staff))
                .containsEntry("stage", "DEVICE")
                .containsEntry("deviceStatus", "PENDIENTE");
        transaction.execute(
                status -> {
                    service.advance(null, false, staff);
                    return null;
                });
        assertThat(staff.getSession().getAttribute("alonUser")).isNull();
        when(identity.user()).thenReturn(Map.of("id", 1L, "comercio_id", 1L, "rol", "DUENO"));
        String id =
                jdbc.queryForObject(
                        "SELECT id FROM security_browsers WHERE usuario_id=2", String.class);
        assertThat(service.pendingCount()).isEqualTo(1);
        jdbc.update("INSERT INTO comercios(id,codigo,nombre) VALUES(2,'OTRO','Otro comercio')");
        jdbc.update(
                "INSERT INTO usuarios(id,comercio_id,usuario,nombre,password_hash,rol,permisos) VALUES(3,2,'other','Otro dueño','unused','DUENO','ventas')");
        when(identity.user()).thenReturn(Map.of("id", 3L, "comercio_id", 2L, "rol", "DUENO"));
        assertThatThrownBy(
                        () ->
                                transaction.execute(
                                        status -> {
                                            service.decide(id, "approve", owner);
                                            return null;
                                        }))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        when(identity.user()).thenReturn(Map.of("id", 1L, "comercio_id", 1L, "rol", "DUENO"));
        transaction.execute(
                status -> {
                    service.decide(id, "approve", owner);
                    return null;
                });
        transaction.execute(
                status -> {
                    service.advance(null, false, staff);
                    return null;
                });
        assertThat(service.validSession(securityUser(2), staff)).isTrue();
        staff.setRemoteAddr("192.0.2.11");
        assertThat(service.validSession(securityUser(2), staff)).isFalse();
        staff.setRemoteAddr("192.0.2.10");
        var future =
                new ar.com.alon.security.AccessSecurityService(
                        jdbc,
                        auth,
                        identity,
                        crypto,
                        Clock.offset(clock, Duration.ofHours(9)),
                        mail,
                        false,
                        true);
        assertThat(future.validSession(securityUser(2), staff)).isFalse();
        transaction.execute(
                status -> {
                    service.decide(id, "revoke", owner);
                    return null;
                });
        assertThat(service.validSession(securityUser(2), staff)).isFalse();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM security_recovery_codes WHERE used_at IS NULL",
                                Integer.class))
                .isEqualTo(10);
    }

    /** Reutiliza solo la sesión en una nueva solicitud de prueba de permisos. */
    private MockHttpServletRequest ownerForPath(MockHttpServletRequest original, String path) {
        var request = new MockHttpServletRequest("GET", path);
        request.setSession((org.springframework.mock.web.MockHttpSession) original.getSession());
        return request;
    }

    @Test
    void recoveryCodesAreSingleUseAndFailedFactorsAreRateLimited(
            @org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) {
        jdbc.update("UPDATE usuarios SET rol='SUPER' WHERE id=1");
        var crypto = new ar.com.alon.security.TotpSecurity(directory.resolve("mfa.key").toString());
        var mail = mock(ar.com.alon.security.SecurityNotifications.class);
        var service =
                new ar.com.alon.security.AccessSecurityService(
                        jdbc, auth, identity, crypto, clock, mail, false, true);
        var request = new MockHttpServletRequest();
        transaction.execute(
                status -> {
                    service.begin(securityUser(1), request, new MockHttpServletResponse());
                    return null;
                });
        String secret = service.enrollment(request).get("secret").toString();
        var codes =
                transaction.execute(
                        status ->
                                service.confirmEnrollment(
                                        crypto.code(
                                                secret, clock.instant().getEpochSecond() / 30, 6),
                                        "owner@example.test",
                                        request));
        transaction.execute(
                status -> {
                    service.advance(null, true, request);
                    return null;
                });
        var login = new MockHttpServletRequest();
        transaction.execute(
                status -> {
                    service.begin(securityUser(1), login, new MockHttpServletResponse());
                    return null;
                });
        assertThat(service.challenge(login)).containsEntry("stage", "MFA");
        transaction.execute(
                status -> {
                    service.advance(codes.getFirst(), false, login);
                    return null;
                });
        assertThat(service.validSession(securityUser(1), login)).isTrue();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM security_recovery_codes WHERE used_at IS NULL",
                                Integer.class))
                .isEqualTo(9);
        var retry = new MockHttpServletRequest();
        transaction.execute(
                status -> {
                    service.begin(securityUser(1), retry, new MockHttpServletResponse());
                    return null;
                });
        transaction.execute(
                status -> {
                    assertThatThrownBy(() -> service.advance(codes.getFirst(), false, retry))
                            .isInstanceOf(
                                    ar.com.alon.security.AccessSecurityService.SecondFactorRejected
                                            .class);
                    return null;
                });
        for (int i = 0; i < 4; i++)
            transaction.execute(
                    status -> {
                        assertThatThrownBy(() -> service.advance("invalid", false, retry))
                                .isInstanceOf(
                                        ar.com.alon.security.AccessSecurityService
                                                .SecondFactorRejected.class);
                        return null;
                    });
        assertThat(retry.getSession().getAttribute("alonUser")).isNull();
        transaction.execute(
                status -> {
                    assertThatThrownBy(() -> service.advance(codes.get(1), false, retry))
                            .isInstanceOf(
                                    ar.com.alon.security.AccessSecurityService.SecondFactorRejected
                                            .class);
                    return null;
                });
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM security_recovery_codes WHERE used_at IS NULL",
                                Integer.class))
                .isEqualTo(9);
        var expired =
                new ar.com.alon.security.AccessSecurityService(
                        jdbc,
                        auth,
                        identity,
                        crypto,
                        Clock.offset(clock, Duration.ofMinutes(11)),
                        mail,
                        false,
                        true);
        assertThat(expired.challenge(retry)).isEmpty();
    }

    @Test
    void superuserControlsManagerPermissionsAndSingleUseCalendarLicenses(
            @org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) {
        jdbc.update("UPDATE usuarios SET usuario='alon',rol='SUPER' WHERE id=1");
        var crypto = new ar.com.alon.security.TotpSecurity(directory.resolve("mfa.key").toString());
        var security =
                new ar.com.alon.security.AccessSecurityService(
                        jdbc,
                        auth,
                        identity,
                        crypto,
                        clock,
                        mock(ar.com.alon.security.SecurityNotifications.class),
                        false,
                        true);
        var platform =
                new ar.com.alon.security.PlatformAdministration(
                        jdbc, identity, auth, crypto, clock);
        var request = new MockHttpServletRequest();
        assertThatThrownBy(() -> security.superLogin("123456", request))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        transaction.execute(
                status -> {
                    security.begin(securityUser(1), request, new MockHttpServletResponse());
                    return null;
                });
        String secret = security.enrollment(request).get("secret").toString();
        var codes =
                transaction.execute(
                        status ->
                                security.confirmEnrollment(
                                        crypto.code(
                                                secret, clock.instant().getEpochSecond() / 30, 6),
                                        "super@example.test",
                                        request));
        transaction.execute(
                status -> {
                    security.advance(null, true, request);
                    return null;
                });
        var login = new MockHttpServletRequest();
        transaction.execute(
                status -> {
                    security.superLogin(codes.getFirst(), login);
                    return null;
                });
        assertThat(security.validSession(securityUser(1), login)).isTrue();
        assertThatThrownBy(
                        () ->
                                transaction.execute(
                                        status -> {
                                            security.superLogin(
                                                    codes.getFirst(), new MockHttpServletRequest());
                                            return null;
                                        }))
                .isInstanceOf(
                        ar.com.alon.security.AccessSecurityService.SecondFactorRejected.class);
        var created =
                transaction.execute(
                        status ->
                                platform.create(
                                        new ar.com.alon.security.PlatformAdministration
                                                .ManagerInput(
                                                "manager",
                                                "Gerente",
                                                "Contraseña segura 2026",
                                                "manager@example.test",
                                                List.of("ventas", "clientes"),
                                                1)));
        long manager = ((Number) created.get("managerId")).longValue();
        String key = ((Map<?, ?>) created.get("license")).get("key").toString();
        assertThat(security.license(securityUser(manager))).containsEntry("active", false);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT security_email FROM usuarios WHERE id=?",
                                String.class,
                                manager))
                .isEqualTo("manager@example.test");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT permisos FROM usuarios WHERE id=?", String.class, manager))
                .doesNotContain("usuarios", "costos", "negocio");
        when(identity.user()).thenReturn(Map.of("rol", "DUENO", "id", manager, "comercio_id", 1L));
        when(identity.userId()).thenReturn(manager);
        assertThatThrownBy(() -> platform.issue(2))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThatThrownBy(
                        () ->
                                auth.createUser(
                                        new UserService.UserInput(
                                                "escalate",
                                                "Escalada",
                                                "Contraseña segura 2026",
                                                "DUENO",
                                                List.of("ventas"))))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThatThrownBy(() -> platform.activate(key))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        when(identity.user()).thenReturn(Map.of("rol", "SUPER", "id", 1L, "comercio_id", 1L));
        when(identity.userId()).thenReturn(1L);
        jdbc.update("INSERT INTO comercios(id,codigo,nombre) VALUES(2,'OTRO','Otro negocio')");
        when(identity.storeId()).thenReturn(2L);
        assertThatThrownBy(() -> transaction.execute(status -> platform.activate(key)))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        when(identity.storeId()).thenReturn(1L);
        var active = transaction.execute(status -> platform.activate(key));
        assertThat(active.get("expiresAt")).isEqualTo("2026-11-28T12:00");
        assertThat(security.license(securityUser(manager))).containsEntry("active", true);
        assertThatThrownBy(() -> transaction.execute(status -> platform.activate(key)))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        when(identity.user()).thenReturn(Map.of("rol", "SUPER", "id", 1L, "comercio_id", 1L));
        when(identity.userId()).thenReturn(1L);
        var next = transaction.execute(status -> platform.issue(2));
        when(identity.user()).thenReturn(Map.of("rol", "DUENO", "id", manager, "comercio_id", 1L));
        when(identity.userId()).thenReturn(manager);
        assertThatThrownBy(() -> platform.activate(next.get("key").toString()))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        when(identity.user()).thenReturn(Map.of("rol", "SUPER", "id", 1L, "comercio_id", 1L));
        when(identity.userId()).thenReturn(1L);
        var renewed = transaction.execute(status -> platform.activate(next.get("key").toString()));
        assertThat(renewed.get("expiresAt")).isEqualTo("2027-01-28T12:00");
        var future =
                new ar.com.alon.security.AccessSecurityService(
                        jdbc,
                        auth,
                        identity,
                        crypto,
                        Clock.offset(clock, Duration.ofDays(120)),
                        mock(ar.com.alon.security.SecurityNotifications.class),
                        false,
                        true);
        assertThat(future.license(securityUser(manager))).containsEntry("active", false);
        assertThat(future.license(securityUser(1))).containsEntry("active", true);
    }

    @Test
    void managerEntersWithCreatedPasswordWithoutAuthenticator(
            @org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
        String password = "Contraseña gerente 2026";
        jdbc.update(
                "UPDATE usuarios SET password_hash=?,permisos='ventas,seguridad',security_email='gerente@example.test' WHERE id=1",
                new PasswordHasher().hash(password));
        var crypto =
                new ar.com.alon.security.TotpSecurity(directory.resolve("unused.key").toString());
        var security =
                new ar.com.alon.security.AccessSecurityService(
                        jdbc,
                        auth,
                        identity,
                        crypto,
                        clock,
                        mock(ar.com.alon.security.SecurityNotifications.class),
                        false,
                        true);
        assertThatThrownBy(
                        () ->
                                auth.authenticate(
                                        new UserService.Credentials(
                                                "owner", null, "Incorrecta", null)))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        var verified =
                auth.authenticate(new UserService.Credentials("owner", null, password, null));
        var request = new MockHttpServletRequest("GET", "/api/v1/alon/sales");
        transaction.execute(
                status -> {
                    security.begin(verified, request, new MockHttpServletResponse());
                    return null;
                });
        assertThat(security.challenge(request)).isEmpty();
        assertThat(security.validSession(verified, request)).isTrue();
        assertThat(request.getSession().getAttribute("alonUser")).isNotNull();
        assertThat(crypto.hasKey()).isFalse();
        when(identity.user()).thenReturn(Map.of("id", 1L, "comercio_id", 1L, "rol", "DUENO"));
        assertThat(security.dashboard()).containsEntry("authenticatorRequired", false);
        assertThatThrownBy(() -> security.regenerate("123456", request))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        var allowed = new MockHttpServletResponse();
        new ar.com.alon.security.PermissionFilter(auth, security)
                .doFilter(request, allowed, new org.springframework.mock.web.MockFilterChain());
        assertThat(allowed.getStatus()).isEqualTo(200);
        jdbc.update("UPDATE comercios SET licencia_controlada=TRUE,licencia_hasta=NULL WHERE id=1");
        var blocked = new MockHttpServletResponse();
        new ar.com.alon.security.PermissionFilter(auth, security)
                .doFilter(request, blocked, new org.springframework.mock.web.MockFilterChain());
        assertThat(blocked.getStatus()).isEqualTo(403);
        assertThat(blocked.getContentAsString()).contains("licencia");
        jdbc.update(
                "UPDATE usuarios SET mfa_enabled=TRUE,mfa_secret='No se debe consultar' WHERE id=1");
        var existingFactor = new MockHttpServletRequest();
        transaction.execute(
                status -> {
                    security.begin(securityUser(1), existingFactor, new MockHttpServletResponse());
                    return null;
                });
        assertThat(security.challenge(existingFactor)).isEmpty();
        assertThat(security.validSession(securityUser(1), existingFactor)).isTrue();
    }

    @Test
    void realHttpSessionRejectsForgeryAndReplaysOneBusinessOperation(
            @org.junit.jupiter.api.io.TempDir java.nio.file.Path imageDirectory) throws Exception {
        jdbc.update("UPDATE usuarios SET rol='SUPER' WHERE id=1");
        String password = "Clave de prueba segura 2026";
        jdbc.update(
                "UPDATE usuarios SET password_hash=?,permisos=? WHERE id=1",
                new PasswordHasher().hash(password),
                String.join(",", UserService.ALL_PERMISSIONS));
        var application =
                new org.springframework.boot.SpringApplication(ar.com.alon.AlonApplication.class);
        try (var context =
                application.run(
                        "--server.port=0",
                        "--alon.images-directory=" + imageDirectory,
                        "--alon.security-key-file=" + imageDirectory.resolve("mfa.key"),
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
            var publicCatalog =
                    client.send(
                            java.net.http.HttpRequest.newBuilder(
                                            java.net.URI.create(origin + "/api/v1/shop/catalog"))
                                    .GET()
                                    .build(),
                            java.net.http.HttpResponse.BodyHandlers.ofString());
            assertThat(publicCatalog.statusCode()).isEqualTo(200);
            var anonymousOrder =
                    client.send(
                            java.net.http.HttpRequest.newBuilder(
                                            java.net.URI.create(origin + "/api/v1/shop/orders"))
                                    .header("Content-Type", "application/json")
                                    .header("X-CSRF-Token", csrf)
                                    .header("Idempotency-Key", "anonymous-shop-123456")
                                    .POST(
                                            java.net.http.HttpRequest.BodyPublishers.ofString(
                                                    "{\"paymentMethod\":\"EFECTIVO\",\"lines\":[{\"articleId\":1,\"quantity\":1,\"expectedPrice\":200}]}"))
                                    .build(),
                            java.net.http.HttpResponse.BodyHandlers.ofString());
            assertThat(anonymousOrder.statusCode()).isEqualTo(401);
            var shopper =
                    client.send(
                            java.net.http.HttpRequest.newBuilder(
                                            java.net.URI.create(origin + "/api/v1/shop/register"))
                                    .header("Content-Type", "application/json")
                                    .header("X-CSRF-Token", csrf)
                                    .POST(
                                            java.net.http.HttpRequest.BodyPublishers.ofString(
                                                    mapper.writeValueAsString(
                                                            Map.of(
                                                                    "firstName",
                                                                    "Ana",
                                                                    "lastName",
                                                                    "Prueba",
                                                                    "dni",
                                                                    "30111222",
                                                                    "address",
                                                                    "Calle 123",
                                                                    "postalCode",
                                                                    "1000",
                                                                    "city",
                                                                    "Buenos Aires",
                                                                    "province",
                                                                    "CABA",
                                                                    "country",
                                                                    "Argentina",
                                                                    "phone",
                                                                    "+54 9 11 0000 0000",
                                                                    "password",
                                                                    "Shopper-test-2026!"))))
                                    .build(),
                            java.net.http.HttpResponse.BodyHandlers.ofString());
            assertThat(shopper.statusCode()).withFailMessage(shopper.body()).isEqualTo(201);
            assertThat(shopper.body()).doesNotContain("password_hash");
            var staffDenied =
                    client.send(
                            java.net.http.HttpRequest.newBuilder(
                                            java.net.URI.create(origin + "/api/v1/alon/customers"))
                                    .GET()
                                    .build(),
                            java.net.http.HttpResponse.BodyHandlers.ofString());
            assertThat(staffDenied.statusCode()).isEqualTo(401);
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
            assertThat(mapper.readTree(login.body()).path("challenge").path("stage").asText())
                    .isEqualTo("ENROLL");
            var enrollment =
                    client.send(
                            java.net.http.HttpRequest.newBuilder(
                                            java.net.URI.create(
                                                    origin + "/api/v1/auth/security/enroll"))
                                    .header("X-CSRF-Token", csrf)
                                    .POST(java.net.http.HttpRequest.BodyPublishers.noBody())
                                    .build(),
                            java.net.http.HttpResponse.BodyHandlers.ofString());
            assertThat(enrollment.statusCode()).isEqualTo(200);
            String secret = mapper.readTree(enrollment.body()).path("secret").asText();
            var crypto = context.getBean(ar.com.alon.security.TotpSecurity.class);
            String code = crypto.code(secret, java.time.Instant.now().getEpochSecond() / 30, 6);
            var confirmation =
                    client.send(
                            java.net.http.HttpRequest.newBuilder(
                                            java.net.URI.create(
                                                    origin + "/api/v1/auth/security/confirm"))
                                    .header("X-CSRF-Token", csrf)
                                    .header("Content-Type", "application/json")
                                    .POST(
                                            java.net.http.HttpRequest.BodyPublishers.ofString(
                                                    mapper.writeValueAsString(
                                                            Map.of(
                                                                    "code",
                                                                    code,
                                                                    "email",
                                                                    "owner@example.test"))))
                                    .build(),
                            java.net.http.HttpResponse.BodyHandlers.ofString());
            assertThat(confirmation.statusCode()).isEqualTo(200);
            var finish =
                    client.send(
                            java.net.http.HttpRequest.newBuilder(
                                            java.net.URI.create(
                                                    origin + "/api/v1/auth/security/continue"))
                                    .header("X-CSRF-Token", csrf)
                                    .header("Content-Type", "application/json")
                                    .POST(
                                            java.net.http.HttpRequest.BodyPublishers.ofString(
                                                    "{\"saved\":true}"))
                                    .build(),
                            java.net.http.HttpResponse.BodyHandlers.ofString());
            assertThat(finish.statusCode()).isEqualTo(200);
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
            var imageBytes = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(
                    new java.awt.image.BufferedImage(
                            2, 2, java.awt.image.BufferedImage.TYPE_INT_RGB),
                    "png",
                    imageBytes);
            var badArticle = new java.util.LinkedHashMap<String, Object>();
            badArticle.put("code", "BAD-PHOTOS");
            badArticle.put("name", "Artículo con foto inválida");
            badArticle.put("categoryId", 1);
            badArticle.put("supplierId", 1);
            badArticle.put("unit", "UNIDAD");
            badArticle.put("cost", 100);
            badArticle.put("initialStock", 3);
            badArticle.put("minimumStock", 0);
            badArticle.put(
                    "photos",
                    List.of(
                            Map.of(
                                    "data",
                                    Base64.getEncoder().encodeToString(imageBytes.toByteArray()),
                                    "illustrative",
                                    false),
                            Map.of(
                                    "data",
                                    Base64.getEncoder().encodeToString("<svg></svg>".getBytes()),
                                    "illustrative",
                                    false)));
            var invalidPhoto =
                    client.send(
                            java.net.http.HttpRequest.newBuilder(
                                            java.net.URI.create(
                                                    origin + "/api/v1/operations/articles"))
                                    .header("Content-Type", "application/json")
                                    .header("X-CSRF-Token", csrf)
                                    .header("Idempotency-Key", "bad-photos-http-123456")
                                    .POST(
                                            java.net.http.HttpRequest.BodyPublishers.ofString(
                                                    mapper.writeValueAsString(badArticle)))
                                    .build(),
                            java.net.http.HttpResponse.BodyHandlers.ofString());
            assertThat(invalidPhoto.statusCode())
                    .withFailMessage(invalidPhoto.body())
                    .isEqualTo(400);
            assertThat(
                            jdbc.queryForObject(
                                    "SELECT COUNT(*) FROM articulos WHERE codigo='BAD-PHOTOS'",
                                    Integer.class))
                    .isZero();
            assertThat(
                            jdbc.queryForObject(
                                    "SELECT COUNT(*) FROM movimientos_stock WHERE referencia='ALTA-BAD-PHOTOS'",
                                    Integer.class))
                    .isZero();
            try (var files = java.nio.file.Files.list(imageDirectory)) {
                assertThat(
                                files.filter(
                                                path ->
                                                        !path.getFileName()
                                                                .toString()
                                                                .equals("mfa.key"))
                                        .count())
                        .isZero();
            }
            jdbc.update("UPDATE usuarios SET activo=FALSE WHERE id=1");
            var denied =
                    client.send(
                            java.net.http.HttpRequest.newBuilder(
                                            java.net.URI.create(origin + "/api/v1/alon/customers"))
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
                            .replace("USE alon;", "");
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
    void optionalAlonSampleDataLoadsIntoTheMigratedSchema() throws Exception {
        String sql =
                java.nio.file.Files.readString(
                                java.nio.file.Path.of("../database/alon-sample-data.sql"))
                        .replace("USE alon;", "");
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
