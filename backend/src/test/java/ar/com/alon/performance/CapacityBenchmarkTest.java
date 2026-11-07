package ar.com.alon.performance;

import static org.assertj.core.api.Assertions.*;

import ar.com.alon.articles.ArticleQueries;
import ar.com.alon.cash.CashLedger;
import ar.com.alon.categories.CategoryQueries;
import ar.com.alon.customers.CustomerQueries;
import ar.com.alon.dashboard.DashboardQueries;
import ar.com.alon.reporting.ReportQueries;
import ar.com.alon.sales.SalesService;
import ar.com.alon.sales.dto.*;
import ar.com.alon.security.RequestIdentity;
import ar.com.alon.shared.audit.AuditLog;
import ar.com.alon.shared.commands.CommandExecutor;
import ar.com.alon.stock.StockRepository;
import ar.com.alon.suppliers.SupplierQueries;
import com.zaxxer.hikari.*;
import java.lang.management.ManagementFactory;
import java.math.BigDecimal;
import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.mock.web.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.*;
import tools.jackson.databind.json.JsonMapper;

/** Opt-in: no toca alon ni alon_validation, no limpia ni reutiliza bases existentes. */
@EnabledIfEnvironmentVariable(named = "ALON_PERF_MYSQL_URL", matches = ".*/alon_perf_validation_.*")
class CapacityBenchmarkTest {
    private static final int ARTICLES = 10_000, SALES = 100_000, LINES = 10;
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 6);
    private static final Clock CLOCK =
            Clock.fixed(
                    Instant.parse("2026-11-06T15:00:00Z"),
                    ZoneId.of("America/Argentina/Buenos_Aires"));
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private final Map<String, Object> report = new LinkedHashMap<>();
    private final Map<String, Object> measurements = new LinkedHashMap<>();
    private JdbcTemplate jdbc;
    private HikariDataSource pool;

    static class Identity extends RequestIdentity {
        private final long userId;

        Identity(long userId) {
            this.userId = userId;
        }

        @Override
        public long storeId() {
            return 1;
        }

        @Override
        public long userId() {
            return userId;
        }

        @Override
        public boolean permits(String permission) {
            return true;
        }

        @Override
        public boolean accountingEnabled() {
            return false;
        }

        @Override
        public Map<String, Object> user() {
            return Map.of("id", userId, "comercio_id", 1L, "rol", "DUENO");
        }
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.MINUTES)
    void benchmarkIsolatedCapacityAndConcurrentCheckouts() throws Exception {
        String url = System.getenv("ALON_PERF_MYSQL_URL");
        if (!url.matches("jdbc:mysql://[^/]+/alon_perf_validation_[0-9]{8}_[0-9a-f]{8}(?:\\?.*)?"))
            throw new IllegalArgumentException(
                    "Solo se admite una base nueva alon_perf_validation_FECHA_ID.");
        report.put("status", "running");
        report.put("started_at", Instant.now().toString());
        report.put(
                "scope",
                "Servicios Java + JDBC + transacciones reales; no HTTP, navegador, TLS ni autenticación. Sin fotos. Una caja contable compartida por 1, 3 o 5 puestos.");
        report.put(
                "fixture",
                Map.of(
                        "articles",
                        ARTICLES,
                        "sales",
                        SALES,
                        "sale_lines",
                        SALES * LINES,
                        "ordinary_articles",
                        9000,
                        "garments",
                        500,
                        "categories",
                        120,
                        "suppliers",
                        250,
                        "customers",
                        250));
        report.put(
                "git_revision", System.getenv().getOrDefault("ALON_PERF_GIT_REVISION", "unknown"));
        report.put(
                "source_fingerprint_sha256",
                System.getenv().getOrDefault("ALON_PERF_SOURCE_SHA256", "unknown"));
        report.put("measurements", measurements);
        try {
            var configuration = new HikariConfig();
            configuration.setJdbcUrl(url);
            configuration.setUsername(System.getenv("ALON_PERF_MYSQL_USER"));
            configuration.setPassword(System.getenv().getOrDefault("ALON_PERF_MYSQL_PASSWORD", ""));
            configuration.setMaximumPoolSize(8);
            configuration.setMinimumIdle(1);
            configuration.setConnectionTimeout(10_000);
            pool = new HikariDataSource(configuration);
            jdbc = new JdbcTemplate(pool);
            jdbc.setQueryTimeout(120);
            report.put("schema", jdbc.queryForObject("SELECT DATABASE()", String.class));
            if (jdbc.queryForObject(
                            "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()",
                            Long.class)
                    != 0)
                throw new IllegalStateException(
                        "La base de prueba debe estar vacía. No se borra ni se reutiliza.");
            var os =
                    (com.sun.management.OperatingSystemMXBean)
                            ManagementFactory.getOperatingSystemMXBean();
            report.put(
                    "host",
                    Map.of(
                            "cpu_logical",
                            os.getAvailableProcessors(),
                            "ram_bytes",
                            os.getTotalMemorySize(),
                            "os",
                            System.getProperty("os.name"),
                            "java",
                            System.getProperty("java.version")));
            report.put(
                    "mysql",
                    jdbc.queryForMap(
                            "SELECT VERSION() version,@@innodb_buffer_pool_size buffer_pool_bytes,@@max_connections max_connections,@@transaction_isolation transaction_isolation"));
            Flyway.configure().dataSource(pool).load().migrate();
            seed();
            verifyCompleteLists();
            benchmarkReads();
            for (boolean sharedStock : new boolean[] {false, true})
                for (int checkouts : new int[] {1, 3, 5}) benchmarkSales(checkouts, sharedStock);
            report.put("status", "completed");
        } catch (Exception | AssertionError error) {
            report.put("status", "failed");
            report.put("error_type", error.getClass().getName());
            // No se vuelcan URL, contraseñas ni mensajes del controlador JDBC al reporte público.
            throw error;
        } finally {
            report.put("finished_at", Instant.now().toString());
            Path output =
                    Path.of(
                                    System.getenv()
                                            .getOrDefault(
                                                    "ALON_PERF_REPORT",
                                                    "../output/performance/results.json"))
                            .toAbsolutePath();
            Files.createDirectories(output.getParent());
            Files.writeString(
                    output, JSON.writerWithDefaultPrettyPrinter().writeValueAsString(report));
            if (pool != null) pool.close();
            RequestContextHolder.resetRequestAttributes();
        }
    }

    private void seed() throws Exception {
        long start = System.nanoTime();
        prepareBenchmarkStores(jdbc);
        for (int id = 1; id <= 5; id++)
            jdbc.update(
                    "INSERT INTO usuarios(id,comercio_id,usuario,nombre,password_hash,rol,permisos) VALUES(?,1,?,?,'unused','DUENO','ventas')",
                    id,
                    "perf" + id,
                    "Puesto " + id);
        for (int id = 1; id <= 250; id++) {
            jdbc.update(
                    "INSERT INTO proveedores(id,comercio_id,codigo,nombre,especialidad,telefono,email,saldo_actual) VALUES(?,1,?,?,'Prueba',?,'',?)",
                    id,
                    "P" + id,
                    "Proveedor %04d".formatted(id),
                    "3446" + id,
                    id > 125 ? 100 : 0);
            jdbc.update(
                    "INSERT INTO clientes(id,comercio_id,codigo,nombre,telefono,email,limite_credito) VALUES(?,1,?,?,?,'',1000000)",
                    id,
                    "C" + id,
                    "Cliente %04d".formatted(id),
                    "3446" + id);
        }
        jdbc.update(
                "INSERT INTO proveedores(id,comercio_id,codigo,nombre,especialidad,telefono,email,saldo_actual) VALUES(1000,2,'OTHER','Otro proveedor','','','',100)");
        for (int id = 1; id <= 120; id++)
            jdbc.update(
                    "INSERT INTO rubros(id,comercio_id,codigo,nombre) VALUES(?,1,?,?)",
                    id,
                    "R" + id,
                    "Rubro %03d".formatted(id));
        try (Connection connection = pool.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement articles =
                    connection.prepareStatement(
                            "INSERT INTO articulos(id,comercio_id,codigo,nombre,rubro_id,proveedor_principal_id,unidad,costo,precio_venta,stock,stock_minimo,tipo_producto,modelo,marca,talle,sistema_talle,color,material,publico,temporada,descripcion) VALUES(?,1,?,?,?,1,'UNIDAD',60,100,9900,5,?,?, 'Sin marca',?,'LETRAS','Unico','Sin especificar','UNISEX','TODO EL AÑO','Prueba')")) {
                for (int id = 1; id <= ARTICLES; id++) {
                    boolean apparel = id > 9000;
                    String size = apparel && id % 2 == 0 ? "M" : "S";
                    articles.setInt(1, id);
                    articles.setString(2, "PERF%05d".formatted(id));
                    articles.setString(
                            3,
                            apparel
                                    ? "Prenda %04d · %s · Unico".formatted((id - 9001) / 2, size)
                                    : "Producto %05d".formatted(id));
                    articles.setInt(4, (id - 1) % 120 + 1);
                    articles.setString(5, apparel ? "INDUMENTARIA" : "ARTICULO");
                    articles.setString(6, apparel ? "Modelo " + (id - 9001) / 2 : "");
                    articles.setString(7, apparel ? size : "");
                    articles.addBatch();
                    if (id % 1000 == 0) {
                        articles.executeBatch();
                        connection.commit();
                    }
                }
            }
            try (PreparedStatement sales =
                    connection.prepareStatement(
                            "INSERT INTO ventas(id,comercio_id,numero,fecha,fecha_hora,cliente_id,tipo_venta,medio_pago,subtotal,total,costo_total) VALUES(?,1,?,?,?,?,'CONTADO','EFECTIVO',1000,1000,600)")) {
                System.out.println("[CAPACIDAD] 10.000 articulos cargados. Preparando ventas...");
                for (int id = 1; id <= SALES; id++) {
                    LocalDate day = TODAY.minusDays((id - 1) % 365);
                    sales.setInt(1, id);
                    sales.setString(2, "HIST-%06d".formatted(id));
                    sales.setDate(3, java.sql.Date.valueOf(day));
                    sales.setTimestamp(4, Timestamp.valueOf(day.atTime(12, 0)));
                    sales.setInt(5, (id - 1) % 250 + 1);
                    sales.addBatch();
                    if (id % 1000 == 0) {
                        sales.executeBatch();
                        connection.commit();
                    }
                }
            }
            try (PreparedStatement lines =
                    connection.prepareStatement(
                            "INSERT INTO venta_detalle(venta_id,renglon,articulo_id,cantidad,precio_unitario,costo_unitario,total_linea) VALUES(?,?,?,1,100,60,100)")) {
                System.out.println("[CAPACIDAD] 100.000 ventas cargadas. Preparando renglones...");
                int count = 0;
                for (int sale = 1; sale <= SALES; sale++)
                    for (int line = 1; line <= LINES; line++) {
                        lines.setInt(1, sale);
                        lines.setInt(2, line);
                        lines.setInt(3, ((sale - 1) * LINES + line - 1) % ARTICLES + 1);
                        lines.addBatch();
                        if (++count % 1000 == 0) {
                            lines.executeBatch();
                            connection.commit();
                        }
                    }
            }
        }
        System.out.println("[CAPACIDAD] 1.000.000 de renglones cargados. Comprobando datos...");
        jdbc.update(
                "INSERT INTO cajas(comercio_id,fecha,numero_sesion,abierto_en,cerrado_en,saldo_inicial,estado) VALUES(1,?,1,?,NULL,0,'ABIERTA')",
                TODAY,
                TODAY.atTime(8, 0));
        for (String table :
                List.of("articulos", "ventas", "venta_detalle", "proveedores", "clientes"))
            jdbc.execute("ANALYZE TABLE " + table);
        assertThat(number("SELECT COUNT(*) FROM articulos WHERE comercio_id=1"))
                .isEqualTo(ARTICLES);
        assertThat(number("SELECT COUNT(*) FROM ventas WHERE comercio_id=1")).isEqualTo(SALES);
        assertThat(number("SELECT COUNT(*) FROM venta_detalle")).isEqualTo((long) SALES * LINES);
        report.put("seed_ms", (System.nanoTime() - start) / 1_000_000.0);
        report.put(
                "history_note",
                "Historial sintético insertado por lotes para consultas: no se generaron auditorías ni movimientos históricos de caja/stock. Las ventas concurrentes sí recorren la lógica transaccional y sus movimientos reales.");
    }

    /** V1 ya crea ALON (id 1) en la base nueva: se adapta esa fila, sin volver a insertarla. */
    static void prepareBenchmarkStores(JdbcTemplate jdbc) {
        int changed =
                jdbc.update(
                        "UPDATE comercios SET codigo='PERF',nombre='Comercio de prueba' WHERE id=1 AND codigo='ALON'");
        if (changed != 1)
            throw new IllegalStateException(
                    "No se encontró el comercio inicial esperado después de las migraciones en la base de prueba nueva.");
        jdbc.update("INSERT INTO comercios(id,codigo,nombre) VALUES(2,'OTHER','Otro comercio')");
    }

    private void verifyCompleteLists() {
        var identity = new Identity(1);
        var suppliers = new SupplierQueries(jdbc, identity, CLOCK);
        var request = bind("", 0);
        var response =
                ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes())
                        .getResponse();
        assertThat(suppliers.suppliers(false, true)).hasSize(100);
        assertThat(response.getHeader("X-Total-Count")).isEqualTo("125");
        request.setParameter("page", "1");
        assertThat(suppliers.suppliers(false, true)).hasSize(25);
        assertThat(response.getHeader("X-Total-Count")).isEqualTo("125");
        request.setParameter("q", "Proveedor 0201");
        request.setParameter("page", "0");
        assertThat(suppliers.suppliers(false, true))
                .singleElement()
                .satisfies(row -> assertThat(((Number) row.get("id")).longValue()).isEqualTo(201));
        request.setParameter("q", "Rubro 120");
        assertThat(new CategoryQueries(jdbc, identity, CLOCK).categories()).hasSize(1);
        request.setParameter("q", "3446250");
        assertThat(new CustomerQueries(jdbc, identity, CLOCK).customers(false)).hasSize(1);
        request.setParameter("q", "");
        assertThat(new ArticleQueries(jdbc, identity, CLOCK).grouped(false, "", "", 0, 100))
                .hasSize(100);
        assertThat(response.getHeader("X-Total-Count")).isEqualTo("9500");
        report.put(
                "complete_list_checks",
                "passed: tenant, debt before pagination, 125 debtors, second page, category 120, customer phone 250, 9500 grouped products");
        RequestContextHolder.resetRequestAttributes();
    }

    private void benchmarkReads() {
        var identity = new Identity(1);
        var articles = new ArticleQueries(jdbc, identity, CLOCK);
        var reports = new ReportQueries(jdbc, identity, CLOCK);
        var dashboard = new DashboardQueries(jdbc, identity, CLOCK);
        measure(
                "search_exact_code",
                () -> {
                    bind("", 0);
                    return articles.articles("DUENO", false, "", "", "PERF09999");
                });
        measure(
                "search_name_selective",
                () -> {
                    bind("Producto 08999", 0);
                    return articles.articles("DUENO", false);
                });
        measure(
                "search_name_broad",
                () -> {
                    bind("Producto", 0);
                    return articles.articles("DUENO", false);
                });
        measure(
                "catalog_grouped_first_page",
                () -> {
                    bind("", 0);
                    return articles.grouped(false, "", "", 0, 100);
                });
        measure(
                "catalog_grouped_last_page",
                () -> {
                    bind("", 94);
                    return articles.grouped(false, "", "", 94, 100);
                });
        measure(
                "report_daily_month",
                () -> {
                    bind("", 0);
                    return reports.dailySales("2026-11", "2026-11");
                });
        measure(
                "report_top_articles_year",
                () -> {
                    bind("", 0);
                    return reports.topArticles("2025-11", "2026-11");
                });
        measure(
                "report_monthly_year",
                () -> {
                    bind("", 0);
                    return reports.monthlySales("2025-11", "2026-11");
                });
        measure(
                "dashboard_week",
                () -> {
                    bind("", 0);
                    return dashboard.summary();
                });
        report.put(
                "query_plans",
                Map.of(
                        "exact_code",
                                jdbc.queryForList(
                                        "EXPLAIN SELECT id FROM articulos WHERE comercio_id=1 AND codigo='PERF09999'"),
                        "name_contains",
                                jdbc.queryForList(
                                        "EXPLAIN SELECT id FROM articulos WHERE comercio_id=1 AND LOWER(CONCAT_WS(' ',codigo,nombre)) LIKE '%producto%'")));
        RequestContextHolder.resetRequestAttributes();
    }

    private void measure(String name, Supplier<Object> action) {
        System.out.println("[CAPACIDAD] Midiendo " + name);
        for (int i = 0; i < 3; i++) JSON.writeValueAsString(action.get());
        var timings = new ArrayList<Double>();
        for (int i = 0; i < 30; i++) {
            long start = System.nanoTime();
            JSON.writeValueAsString(action.get());
            timings.add((System.nanoTime() - start) / 1_000_000.0);
        }
        measurements.put(name, stats(timings));
    }

    private void benchmarkSales(int checkouts, boolean sharedStock) throws Exception {
        System.out.println(
                "[CAPACIDAD] Ventas simultaneas: "
                        + checkouts
                        + " puestos; stock "
                        + (sharedStock ? "compartido" : "diferente"));
        int samples =
                Integer.parseInt(System.getenv().getOrDefault("ALON_PERF_SALES_PER_WORKER", "40"));
        if (samples < 20 || samples > 200)
            throw new IllegalArgumentException("Usá entre 20 y 200 ventas por puesto.");
        long beforeSales = number("SELECT COUNT(*) FROM ventas WHERE comercio_id=1"),
                beforeCommands =
                        number("SELECT COUNT(*) FROM comandos_procesados WHERE comercio_id=1"),
                beforeMovements =
                        number("SELECT COUNT(*) FROM movimientos_caja WHERE comercio_id=1");
        long beforeLines = number("SELECT COUNT(*) FROM venta_detalle"),
                beforeStockMoves =
                        number("SELECT COUNT(*) FROM movimientos_stock WHERE comercio_id=1");
        BigDecimal beforeStock =
                jdbc.queryForObject(
                        "SELECT SUM(stock) FROM articulos WHERE comercio_id=1", BigDecimal.class);
        var timings = new ConcurrentLinkedQueue<Double>();
        var retries = new ConcurrentLinkedQueue<Double>();
        var failures = new ConcurrentLinkedQueue<String>();
        var barrier = new CyclicBarrier(checkouts);
        var workers = Executors.newFixedThreadPool(checkouts);
        long wall = System.nanoTime();
        try {
            var tasks = new ArrayList<Future<?>>();
            for (int worker = 0; worker < checkouts; worker++) {
                final int slot = worker;
                tasks.add(
                        workers.submit(
                                () -> {
                                    var identity = new Identity(slot + 1);
                                    var audit = new AuditLog(jdbc, identity, JSON);
                                    var sales =
                                            new SalesService(
                                                    jdbc,
                                                    identity,
                                                    CLOCK,
                                                    audit,
                                                    new StockRepository(jdbc, identity, CLOCK),
                                                    new CashLedger(jdbc, identity, CLOCK));
                                    var transaction =
                                            new TransactionTemplate(
                                                    new JdbcTransactionManager(pool));
                                    var lines = new ArrayList<SaleLine>();
                                    for (int line = 1; line <= LINES; line++)
                                        lines.add(
                                                new SaleLine(
                                                        (sharedStock ? 0 : slot * LINES) + line,
                                                        BigDecimal.ONE,
                                                        BigDecimal.ZERO));
                                    var input =
                                            new SaleInput(1, TODAY, "CONTADO", "EFECTIVO", lines);
                                    try {
                                        for (int i = 0; i < 3; i++)
                                            commitSale(
                                                    transaction,
                                                    sales,
                                                    identity,
                                                    input,
                                                    "WARM-" + UUID.randomUUID());
                                        barrier.await(30, TimeUnit.SECONDS);
                                        for (int i = 0; i < samples; i++) {
                                            String key = "PERF-" + UUID.randomUUID();
                                            long start = System.nanoTime();
                                            var receipt =
                                                    commitSale(
                                                            transaction,
                                                            sales,
                                                            identity,
                                                            input,
                                                            key);
                                            timings.add((System.nanoTime() - start) / 1_000_000.0);
                                            if (i % 10 == 0) {
                                                start = System.nanoTime();
                                                assertThat(
                                                                commitSale(
                                                                                transaction,
                                                                                sales,
                                                                                identity,
                                                                                input,
                                                                                key)
                                                                        .get("id")
                                                                        .toString())
                                                        .isEqualTo(receipt.get("id").toString());
                                                retries.add(
                                                        (System.nanoTime() - start) / 1_000_000.0);
                                            }
                                        }
                                    } catch (Exception | AssertionError error) {
                                        failures.add(error.getClass().getName());
                                    } finally {
                                        RequestContextHolder.resetRequestAttributes();
                                    }
                                }));
            }
            for (var task : tasks) task.get(10, TimeUnit.MINUTES);
        } finally {
            workers.shutdownNow();
        }
        long expected = (long) checkouts * (samples + 3);
        assertThat(failures).isEmpty();
        assertThat(number("SELECT COUNT(*) FROM ventas WHERE comercio_id=1") - beforeSales)
                .isEqualTo(expected);
        assertThat(
                        number("SELECT COUNT(*) FROM comandos_procesados WHERE comercio_id=1")
                                - beforeCommands)
                .isEqualTo(expected);
        assertThat(
                        number("SELECT COUNT(*) FROM movimientos_caja WHERE comercio_id=1")
                                - beforeMovements)
                .isEqualTo(expected);
        assertThat(number("SELECT COUNT(*) FROM venta_detalle") - beforeLines)
                .isEqualTo(expected * LINES);
        assertThat(
                        number("SELECT COUNT(*) FROM movimientos_stock WHERE comercio_id=1")
                                - beforeStockMoves)
                .isEqualTo(expected * LINES);
        assertThat(
                        beforeStock.subtract(
                                jdbc.queryForObject(
                                        "SELECT SUM(stock) FROM articulos WHERE comercio_id=1",
                                        BigDecimal.class)))
                .isEqualByComparingTo(BigDecimal.valueOf(expected * LINES));
        var result = new LinkedHashMap<String, Object>(stats(new ArrayList<>(timings)));
        result.put("checkouts", checkouts);
        result.put("stock_mode", sharedStock ? "same_articles" : "different_articles");
        result.put("warmup_sales", checkouts * 3);
        result.put("committed_sales", expected);
        result.put("retries", stats(new ArrayList<>(retries)));
        result.put(
                "wall_seconds_including_warmup_and_retries",
                (System.nanoTime() - wall) / 1_000_000_000.0);
        result.put("integrity", "passed: stock, detail, cash, command idempotency and sale count");
        measurements.put(
                "sales_" + checkouts + "_" + (sharedStock ? "shared" : "different"), result);
    }

    private Map<String, Object> commitSale(
            TransactionTemplate transaction,
            SalesService sales,
            Identity identity,
            SaleInput input,
            String key) {
        var request = bind("", 0);
        request.addHeader("Idempotency-Key", key);
        var executor = new CommandExecutor(jdbc, identity, request, JSON);
        return transaction.execute(
                status -> executor.execute("createSale", input, () -> sales.createSale(input)));
    }

    private MockHttpServletRequest bind(String query, int page) {
        var request = new MockHttpServletRequest();
        request.setParameter("q", query);
        request.setParameter("page", String.valueOf(page));
        request.setParameter("size", "100");
        request.setParameter("includeTotal", "true");
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(request, new MockHttpServletResponse()));
        return request;
    }

    private long number(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }

    static Map<String, Object> stats(List<Double> timings) {
        var sorted = timings.stream().sorted().toList();
        if (sorted.isEmpty()) return Map.of("samples", 0);
        return Map.of(
                "samples",
                sorted.size(),
                "p50_ms",
                percentile(sorted, 0.50),
                "p95_ms",
                percentile(sorted, 0.95),
                "p99_ms",
                percentile(sorted, 0.99),
                "max_ms",
                sorted.getLast(),
                "mean_ms",
                sorted.stream().mapToDouble(Double::doubleValue).average().orElse(0),
                "p95_under_proposed_2000ms",
                percentile(sorted, 0.95) <= 2000);
    }

    private static double percentile(List<Double> sorted, double percentile) {
        return sorted.get((int) Math.ceil(sorted.size() * percentile) - 1);
    }
}
