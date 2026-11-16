package ar.com.alon.security;

import ar.com.alon.users.UserService;
import jakarta.validation.constraints.*;
import java.io.IOException;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Aprovisiona esquemas e instancias separados; nunca copia datos del comercio administrador. */
@Service
public class BusinessProvisioning {
    private final JdbcTemplate central;
    private final DataSource source;
    private final RequestIdentity identity;
    private final PasswordHasher passwords;
    private final TotpSecurity crypto;
    private final boolean enabled;
    private final String url, adminUser, adminPassword, clientHost;
    private final Path jar, web, directory;
    private final Map<Long, Process> processes = new ConcurrentHashMap<>();
    private volatile HttpClient http;
    private volatile boolean resuming;
    private volatile int resumeFailures;

    private HttpClient client() {
        if (http == null) {
            synchronized (processes) {
                if (http == null)
                    http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build();
            }
        }
        return http;
    }

    public BusinessProvisioning(
            JdbcTemplate central,
            DataSource source,
            RequestIdentity identity,
            PasswordHasher passwords,
            TotpSecurity crypto,
            @Value("${alon.provisioning.enabled:false}") boolean enabled,
            @Value("${spring.datasource.url}") String url,
            @Value("${alon.provisioning.username:${spring.datasource.username}}") String adminUser,
            @Value("${alon.provisioning.password:${spring.datasource.password}}")
                    String adminPassword,
            @Value("${alon.provisioning.client-host:localhost}") String clientHost,
            @Value("${alon.provisioning.jar:./target/alon-api-0.1.0-SNAPSHOT.jar}") String jar,
            @Value("${alon.provisioning.web:../frontend/dist}") String web,
            @Value("${alon.provisioning.directory:./data/businesses}") String directory) {
        this.central = central;
        this.source = source;
        this.identity = identity;
        this.passwords = passwords;
        this.crypto = crypto;
        this.enabled = enabled;
        this.resuming = enabled;
        this.url = url;
        this.adminUser = adminUser;
        this.adminPassword = adminPassword;
        this.clientHost = clientHost;
        this.jar = Path.of(jar).toAbsolutePath().normalize();
        this.web = Path.of(web).toAbsolutePath().normalize();
        this.directory = Path.of(directory).toAbsolutePath().normalize();
    }

    private void authorize() {
        authorizeSuper();
        if (!enabled)
            throw error(
                    HttpStatus.CONFLICT,
                    "El alta automática no está habilitada en este servidor. Iniciá Alon con el iniciador actualizado.");
    }

    private void authorizeSuper() {
        if (!"SUPER".equals(identity.user().get("rol")))
            throw error(HttpStatus.FORBIDDEN, "Solo Alon puede habilitar instalaciones.");
    }

    public Map<String, Object> changeState(long id, String state, String reason) {
        authorizeSuper();
        if ("BAJA".equals(state))
            throw error(
                    HttpStatus.BAD_REQUEST,
                    "La baja definitiva requiere confirmar el nombre del negocio.");
        if (!Set.of("PREPARACION", "PRUEBA", "ACTIVO", "SUSPENDIDO", "BAJA").contains(state)
                || reason == null
                || reason.length() > 300)
            throw error(
                    HttpStatus.BAD_REQUEST,
                    "Elegí un estado válido y un motivo de hasta 300 caracteres.");
        boolean stateRequested = false;
        try (var lock = source.getConnection()) {
            acquire(lock, id);
            try {
                var businesses =
                        central.queryForList(
                                "SELECT estado,eliminado_en,baja_iniciada_en FROM plataforma_negocios WHERE id=?",
                                id);
                if (businesses.isEmpty())
                    throw error(HttpStatus.NOT_FOUND, "No se encontró el negocio.");
                String before = (String) businesses.getFirst().get("estado");
                if (businesses.getFirst().get("baja_iniciada_en") != null)
                    throw error(
                            HttpStatus.CONFLICT,
                            businesses.getFirst().get("eliminado_en") != null
                                    ? "El negocio fue eliminado definitivamente. Registrá uno nuevo para volver a operar."
                                    : "La baja está incompleta. Reintentá la baja para eliminar los recursos pendientes; este negocio no se puede reactivar.");
                var installations =
                        central.queryForList(
                                "SELECT * FROM plataforma_instalaciones WHERE negocio_id=?", id);
                Path folder =
                        installations.isEmpty()
                                ? null
                                : directory.resolve(
                                        identifier(installations.getFirst().get("esquema")));
                central.update(
                        "UPDATE plataforma_negocios SET estado_objetivo=?,estado_aplicacion='APLICANDO',estado_error='',estado_actor=?,estado_objetivo_motivo=? WHERE id=?",
                        state,
                        identity.userId(),
                        reason.trim(),
                        id);
                stateRequested = true;
                boolean operational = Set.of("ACTIVO", "PRUEBA").contains(state);
                boolean prepared =
                        !installations.isEmpty()
                                && Boolean.TRUE.equals(installations.getFirst().get("iniciar"));
                if (prepared
                        && !operational
                        && healthy(installations.getFirst())
                        && !supportsServiceControl(installations.getFirst()))
                    stopLegacyInstallation(installations.getFirst(), folder.resolve("alon.jar"));
                if (folder != null) writeState(folder, state);
                if (prepared && operational) {
                    try {
                        launch(installations.getFirst());
                    } catch (InterruptedException failure) {
                        Thread.currentThread().interrupt();
                        throw new IOException("Inicio interrumpido.", failure);
                    }
                }
                if (prepared
                        && operational
                        && healthy(installations.getFirst())
                        && !verifyEnabled(installations.getFirst(), state))
                    throw error(
                            HttpStatus.CONFLICT,
                            "No se confirmó la habilitación real. Consultá el estado y reintentá.");
                if (prepared && !operational && !verifyBlocked(installations.getFirst(), state))
                    throw error(
                            HttpStatus.CONFLICT,
                            "El negocio sigue respondiendo sin confirmar el bloqueo. No se aplicó el cambio. Consultá el estado de su instalación y reintentá.");
                {
                    var transaction =
                            new org.springframework.transaction.support.TransactionTemplate(
                                    new org.springframework.jdbc.support.JdbcTransactionManager(
                                            source));
                    transaction.executeWithoutResult(
                            tx -> {
                                central.update(
                                        "UPDATE plataforma_negocios SET estado=?,estado_motivo=?,estado_actualizado_en=CURRENT_TIMESTAMP,estado_objetivo=NULL,estado_aplicacion='CONFIRMADO',estado_error='' WHERE id=?",
                                        state,
                                        reason.trim(),
                                        id);
                                central.update(
                                        "INSERT INTO plataforma_auditoria(usuario_id,negocio_id,accion,detalle) VALUES(?,?,'CAMBIO_ESTADO',?)",
                                        identity.userId(),
                                        id,
                                        before + " → " + state + ". " + reason.trim());
                            });
                }

                return Map.of(
                        "ok",
                        true,
                        "state",
                        state,
                        "installationLinked",
                        !installations.isEmpty(),
                        "accessBlocked",
                        prepared && !operational);
            } catch (RuntimeException | IOException failure) {
                if (stateRequested) stateFailed(id);
                throw failure;
            } finally {
                release(lock, id);
            }
        } catch (SQLException exception) {
            throw error(
                    HttpStatus.CONFLICT,
                    "No se pudo guardar el estado del negocio en la base de datos. No se confirmó el cambio.");
        } catch (IOException exception) {
            throw error(
                    HttpStatus.CONFLICT,
                    "No se pudo controlar la instalación del negocio: "
                            + exception.getMessage()
                            + " No se confirmó el cambio.");
        }
    }

    private void stateFailed(long id) {
        central.update(
                "UPDATE plataforma_negocios SET estado_aplicacion='ERROR',estado_error='Cambio incompleto. Consultá la instalación y reintentá; se recuperará al reiniciar.' WHERE id=? AND estado_aplicacion='APLICANDO'",
                id);
    }

    private boolean verifyEnabled(Map<String, Object> row, String state) {
        try {
            var response =
                    client().send(
                                    HttpRequest.newBuilder(
                                                    URI.create(
                                                            baseUrl(row) + "/api/v1/auth/status"))
                                            .timeout(Duration.ofSeconds(3))
                                            .GET()
                                            .build(),
                                    HttpResponse.BodyHandlers.discarding());
            return response.statusCode() == 200
                    && response.headers()
                            .firstValue("X-Alon-Service-Control")
                            .orElse("")
                            .equals("enabled")
                    && response.headers()
                            .firstValue("X-Alon-Service-State")
                            .orElse("")
                            .equals(state);
        } catch (Exception failure) {
            return false;
        }
    }

    /** Baja explícita, reintentable y limitada a la instalación generada para este negocio. */
    public Map<String, Object> deleteBusiness(long id, String reason, String confirmation) {
        authorize();
        if (reason == null || reason.length() > 300)
            throw error(HttpStatus.BAD_REQUEST, "Revisá el motivo de la baja.");
        boolean deleting = false;
        try (var lock = source.getConnection()) {
            acquire(lock, id);
            try {
                var business =
                        central.queryForList(
                                "SELECT nombre,eliminado_en FROM plataforma_negocios WHERE id=?",
                                id);
                if (business.isEmpty())
                    throw error(HttpStatus.NOT_FOUND, "No se encontró el negocio.");
                if (!business.getFirst().get("nombre").equals(confirmation))
                    throw error(
                            HttpStatus.BAD_REQUEST,
                            "Escribí el nombre exacto del negocio para confirmar la eliminación definitiva.");
                if (business.getFirst().get("eliminado_en") != null)
                    return Map.of("ok", true, "state", "BAJA", "deleted", true);
                var rows =
                        central.queryForList(
                                "SELECT * FROM plataforma_instalaciones WHERE negocio_id=?", id);
                // Persistir el bloqueo antes de cualquier operación irreversible. Nunca se reanuda
                // una baja fallida.
                central.update(
                        "UPDATE plataforma_negocios SET estado='BAJA',estado_objetivo=NULL,estado_aplicacion='APLICANDO',estado_error='',baja_paso='DETENER',estado_motivo=?,baja_iniciada_en=COALESCE(baja_iniciada_en,CURRENT_TIMESTAMP),estado_actualizado_en=CURRENT_TIMESTAMP WHERE id=?",
                        reason.trim(),
                        id);
                deleting = true;
                central.update(
                        "UPDATE plataforma_instalaciones SET iniciar=FALSE WHERE negocio_id=?", id);
                central.update(
                        "INSERT INTO plataforma_auditoria(usuario_id,negocio_id,accion,detalle) VALUES(?,?,'BAJA_INICIADA',?)",
                        identity.userId(),
                        id,
                        reason.trim());
                if (!rows.isEmpty()) {
                    var row = rows.getFirst();
                    String schema = identifier(row.get("esquema"));
                    String user = identifier(row.get("db_usuario"));
                    validateDeletionTarget(schema, user, lock.getCatalog(), adminUser, clientHost);
                    Path folder = deletionFolder(directory, schema);
                    if (jar.startsWith(folder) || web.startsWith(folder))
                        throw error(
                                HttpStatus.CONFLICT,
                                "La carpeta coincide con archivos del administrador. No se puede eliminar.");
                    if (Files.exists(folder)) writeState(folder, "BAJA");
                    var tracked = processes.get(id);
                    if (tracked != null && tracked.isAlive()) {
                        tracked.destroy();
                        try {
                            tracked.onExit().get(30, java.util.concurrent.TimeUnit.SECONDS);
                        } catch (Exception exception) {
                            throw new IOException(
                                    "No se pudo detener el proceso del negocio.", exception);
                        }
                    }
                    stopLegacyInstallation(row, folder.resolve("alon.jar"));
                    // Un puerto ocupado impide eliminar mientras pueda quedar una instancia en
                    // ejecución.
                    try (var probe = new java.net.ServerSocket()) {
                        probe.bind(
                                new InetSocketAddress(
                                        "127.0.0.1", ((Number) row.get("puerto")).intValue()));
                    }
                    try (var connection =
                                    DriverManager.getConnection(url, adminUser, adminPassword);
                            var statement = connection.createStatement()) {
                        validateDeletionTarget(
                                schema, user, connection.getCatalog(), adminUser, clientHost);
                        deletionStep(id, "BASE");
                        statement.executeUpdate("DROP DATABASE IF EXISTS `" + schema + "`");
                        deletionStep(id, "USUARIO");
                        statement.executeUpdate(
                                "DROP USER IF EXISTS '" + user + "'@'" + clientHost + "'");
                    }
                    deletionStep(id, "ARCHIVOS");
                    deleteInstallationFiles(directory, schema);
                    processes.remove(id);
                }
                deletionStep(id, "FINALIZAR");
                var transaction =
                        new org.springframework.transaction.support.TransactionTemplate(
                                new org.springframework.jdbc.support.JdbcTransactionManager(
                                        source));
                transaction.executeWithoutResult(
                        tx -> {
                            central.update(
                                    "DELETE FROM plataforma_instalaciones WHERE negocio_id=?", id);
                            central.update(
                                    "UPDATE plataforma_negocios SET eliminado_en=CURRENT_TIMESTAMP,estado_aplicacion='CONFIRMADO',estado_error='',baja_paso='COMPLETA',gestion_url='',tienda_url='',base_referencia='' WHERE id=?",
                                    id);
                            central.update(
                                    "INSERT INTO plataforma_auditoria(usuario_id,negocio_id,accion,detalle) VALUES(?,?,'BAJA_DEFINITIVA',?)",
                                    identity.userId(),
                                    id,
                                    "Instalación, base de datos y usuario MySQL eliminados.");
                        });
                return Map.of("ok", true, "state", "BAJA", "deleted", true);
            } catch (RuntimeException | SQLException | IOException failure) {
                if (deleting) deletionFailed(id);
                throw failure;
            } finally {
                release(lock, id);
            }
        } catch (SQLException | IOException exception) {
            throw error(
                    HttpStatus.CONFLICT,
                    "No se completó la baja definitiva. Puede haber recursos pendientes de detener o eliminar. Revisá permisos DROP de MySQL y los archivos, y reintentá la baja; no reactives este negocio.");
        }
    }

    private void deletionStep(long id, String step) {
        central.update("UPDATE plataforma_negocios SET baja_paso=? WHERE id=?", step, id);
    }

    private void deletionFailed(long id) {
        central.update(
                "UPDATE plataforma_negocios SET estado_aplicacion='ERROR',estado_error='Baja incompleta. Reintentá para continuar desde el recurso pendiente.' WHERE id=? AND baja_iniciada_en IS NOT NULL AND eliminado_en IS NULL",
                id);
    }

    static void validateDeletionTarget(
            String schema, String user, String centralSchema, String adminUser, String host) {
        if (centralSchema == null
                || centralSchema.isBlank()
                || !schema.matches("alon_n_[a-f0-9]{20}")
                || !user.equals("alon_u_" + schema.substring(7))
                || schema.equalsIgnoreCase(String.valueOf(centralSchema))
                || user.equalsIgnoreCase(adminUser)
                || !host.matches("[a-zA-Z0-9.:%_-]{1,120}"))
            throw error(
                    HttpStatus.CONFLICT,
                    "La instalación no tiene un destino seguro para eliminar.");
    }

    static Path deletionFolder(Path root, String schema) throws IOException {
        if (!schema.matches("alon_n_[a-f0-9]{20}")) throw new IOException("Esquema inválido.");
        Path base = root.toAbsolutePath().normalize();
        Path folder = base.resolve(schema).normalize();
        if (!folder.startsWith(base) || folder.equals(base))
            throw new IOException("Ruta inválida.");
        for (Path part = folder; part != null; part = part.getParent())
            if (Files.isSymbolicLink(part)
                    || Files.exists(part, LinkOption.NOFOLLOW_LINKS)
                            && (!Files.exists(part) || !part.toRealPath().equals(part)))
                throw new IOException("La ruta contiene enlaces.");
        if (Files.exists(folder) && !folder.toRealPath().equals(folder))
            throw new IOException("La ruta de instalación fue redirigida.");
        return folder;
    }

    static void deleteInstallationFiles(Path root, String schema) throws IOException {
        Path folder = deletionFolder(root, schema);
        if (!Files.exists(folder)) return;
        Files.walkFileTree(
                folder,
                new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult preVisitDirectory(
                            Path dir, java.nio.file.attribute.BasicFileAttributes attributes)
                            throws IOException {
                        if (!dir.toRealPath().equals(dir))
                            throw new IOException("La ruta contiene enlaces.");
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFile(
                            Path file, java.nio.file.attribute.BasicFileAttributes attributes)
                            throws IOException {
                        Files.delete(file);
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult postVisitDirectory(Path folder, IOException failure)
                            throws IOException {
                        if (failure != null) throw failure;
                        Files.delete(folder);
                        return FileVisitResult.CONTINUE;
                    }
                });
    }

    private static void writeState(Path folder, String state) throws IOException {
        Files.createDirectories(folder);
        Path temporary = Files.createTempFile(folder, "service-state-", ".tmp");
        try {
            Files.writeString(temporary, state);
            try {
                Files.move(
                        temporary,
                        folder.resolve("service-state"),
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(
                        temporary,
                        folder.resolve("service-state"),
                        StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    /**
     * Las versiones anteriores se detienen solo si se verifica el ejecutable exacto del negocio.
     */
    private void stopLegacyInstallation(Map<String, Object> row, Path executable)
            throws IOException {
        try (var all = ProcessHandle.allProcesses()) {
            var owned =
                    all.filter(
                                    process -> {
                                        var info = process.info();
                                        return ownsJavaProcess(
                                                info.command().orElse(""),
                                                info.arguments().orElse(new String[0]),
                                                executable);
                                    })
                            .findFirst();
            if (owned.isEmpty()) owned = WindowsInstanceProcess.find(executable);
            if (owned.isEmpty()) {
                if (healthy(row))
                    throw error(
                            HttpStatus.CONFLICT,
                            "La instalación anterior sigue encendida y no se pudo verificar su proceso. Reiniciá Windows e iniciá Alon actualizado antes de aplicar la baja o inhabilitación.");
                return;
            }
            if (!owned.get().destroy())
                throw new IOException("No se pudo detener la instancia anterior.");
            try {
                owned.get().onExit().get(30, java.util.concurrent.TimeUnit.SECONDS);
            } catch (Exception exception) {
                throw new IOException("La instancia anterior todavía no se detuvo.", exception);
            }
            if (healthy(row))
                throw error(
                        HttpStatus.CONFLICT,
                        "La instalación anterior todavía responde. No se confirmó su inhabilitación.");
        }
    }

    static boolean ownsJavaProcess(String command, String[] arguments, Path executable) {
        if (command.isBlank()) return false;
        try {
            String name = Path.of(command).getFileName().toString().toLowerCase(Locale.ROOT);
            if (!Set.of("java", "java.exe", "javaw.exe").contains(name)) return false;
            for (int i = 0; i < arguments.length - 1; i++)
                if (arguments[i].equals("-jar")
                        && Path.of(arguments[i + 1])
                                .toAbsolutePath()
                                .normalize()
                                .equals(executable.toAbsolutePath().normalize())) return true;
        } catch (InvalidPathException exception) {
            return false;
        }
        return false;
    }

    private String businessState(long id) {
        var rows =
                central.queryForList(
                        "SELECT COALESCE(estado_objetivo,estado) AS estado FROM plataforma_negocios WHERE id=?",
                        id);
        if (rows.isEmpty()) throw error(HttpStatus.NOT_FOUND, "No se encontró el negocio.");
        return (String) rows.getFirst().get("estado");
    }

    private void requireOperational(long id) {
        if (!Set.of("ACTIVO", "PRUEBA").contains(businessState(id)))
            throw error(
                    HttpStatus.CONFLICT,
                    "El negocio debe estar Activo o en Prueba para iniciar o habilitar su sistema.");
    }

    public Map<String, Object> status(long id) {
        authorize();
        var rows =
                central.queryForList(
                        "SELECT * FROM plataforma_instalaciones WHERE negocio_id=?", id);
        if (rows.isEmpty())
            return Map.of(
                    "estado", "SIN_CREAR", "running", false, "businessState", businessState(id));
        return describe(rows.getFirst());
    }

    private Map<String, Object> describe(Map<String, Object> row) {
        String state = businessState(((Number) row.get("negocio_id")).longValue());
        return Map.of(
                "businessState",
                state,
                "estado",
                row.get("estado"),
                "error",
                row.get("error"),
                "prepared",
                row.get("iniciar"),
                "running",
                Set.of("ACTIVO", "PRUEBA").contains(state)
                        && healthy(row)
                        && verifyEnabled(row, state),
                "port",
                row.get("puerto"),
                "username",
                row.get("gerente_usuario"),
                "managementUrl",
                BusinessLinks.management(
                        ((Number) row.get("negocio_id")).longValue(),
                        businessSlug(((Number) row.get("negocio_id")).longValue())),
                "shopUrl",
                BusinessLinks.shop(
                        ((Number) row.get("negocio_id")).longValue(),
                        businessSlug(((Number) row.get("negocio_id")).longValue())));
    }

    public Map<String, Object> start(long id) {
        authorize();
        requireOperational(id);
        var row =
                central.queryForMap(
                        "SELECT * FROM plataforma_instalaciones WHERE negocio_id=?", id);
        if (!Boolean.TRUE.equals(row.get("iniciar")))
            throw error(HttpStatus.CONFLICT, "Primero completá el alta y el gerente.");
        try (var lock = source.getConnection()) {
            acquire(lock, id);
            try {
                launch(row);
                markReady(id, row);
                return describe(
                        central.queryForMap(
                                "SELECT * FROM plataforma_instalaciones WHERE negocio_id=?", id));
            } finally {
                release(lock, id);
            }
        } catch (IOException | InterruptedException | SQLException exception) {
            throw error(
                    HttpStatus.CONFLICT,
                    "No se pudo iniciar la instancia. Revisá sus registros y reintentá.");
        }
    }

    public void validateProvision(Input input) {
        authorize();
        validate(input);
        if (!Files.isRegularFile(jar) || !Files.isRegularFile(web.resolve("index.html")))
            throw error(
                    HttpStatus.CONFLICT,
                    "Falta compilar el servidor o el frontend antes de habilitar negocios.");
        if (!clientHost.matches("[a-zA-Z0-9.:%_-]{1,120}"))
            throw error(HttpStatus.CONFLICT, "Revisá el host de conexión de las instalaciones.");
    }

    public Map<String, Object> provision(long id, Input input) {
        validateProvision(input);
        try (var lock = source.getConnection()) {
            acquire(lock, id);
            try {
                return provisionLocked(id, input);
            } finally {
                release(lock, id);
            }
        } catch (SQLException exception) {
            throw error(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "No se pudo conectar con la base administradora. No se habilitó el negocio.");
        }
    }

    private Map<String, Object> provisionLocked(long id, Input input) {
        var businesses = central.queryForList("SELECT * FROM plataforma_negocios WHERE id=?", id);
        if (businesses.isEmpty()) throw error(HttpStatus.NOT_FOUND, "Primero registrá el negocio.");
        var business = businesses.getFirst();
        if (!Set.of("ACTIVO", "PRUEBA").contains(business.get("estado")))
            throw error(
                    HttpStatus.CONFLICT,
                    "El negocio debe estar activo o en prueba para habilitar su sistema.");
        var rows =
                central.queryForList(
                        "SELECT * FROM plataforma_instalaciones WHERE negocio_id=?", id);
        if (rows.isEmpty()) {
            int port = portFor(id);
            String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 20);
            central.update(
                    "INSERT INTO plataforma_instalaciones(negocio_id,esquema,db_usuario,db_clave_cifrada,puerto,identificador,gerente_usuario,vencimiento_licencia) VALUES(?,?,?,?,?,?,?,?)",
                    id,
                    "alon_n_" + suffix,
                    "alon_u_" + suffix,
                    crypto.encrypt("Al0n!" + crypto.token()),
                    port,
                    UUID.randomUUID().toString(),
                    input.username().trim(),
                    LocalDateTime.now(ZoneId.of("America/Argentina/Buenos_Aires"))
                            .plusMonths(input.months()));
        }
        var row =
                central.queryForMap(
                        "SELECT * FROM plataforma_instalaciones WHERE negocio_id=?", id);
        if (!input.username().trim().equals(row.get("gerente_usuario")))
            throw error(
                    HttpStatus.CONFLICT,
                    "El reintento debe usar el usuario gerente reservado: "
                            + row.get("gerente_usuario"));
        String setupFingerprint =
                crypto.hash(
                        tools.jackson.databind.json.JsonMapper.builder()
                                .build()
                                .writeValueAsString(input));
        String reservedFingerprint = (String) row.get("alta_huella");
        if (reservedFingerprint != null && !passwords.verify(setupFingerprint, reservedFingerprint))
            throw error(
                    HttpStatus.CONFLICT,
                    "El alta tiene otros datos de gerente o licencia reservados. No se cambió la contraseña. Reintentá con los datos originales; si ya está preparado, usá Iniciar instalación.");
        if (reservedFingerprint == null) {
            if (Boolean.TRUE.equals(row.get("iniciar")))
                throw error(
                        HttpStatus.CONFLICT,
                        "La instalación ya tiene un gerente preparado. Usá Iniciar instalación; no se cambió su contraseña ni licencia.");
            central.update(
                    "UPDATE plataforma_instalaciones SET alta_huella=? WHERE negocio_id=?",
                    passwords.hash(setupFingerprint),
                    id);
        }
        central.update(
                "UPDATE plataforma_instalaciones SET estado='PREPARANDO',error='' WHERE negocio_id=?",
                id);
        try {
            if (!Boolean.TRUE.equals(row.get("iniciar"))) {
                createDatabase(row);
                seed(row, business, input);
                central.update(
                        "UPDATE plataforma_instalaciones SET iniciar=TRUE WHERE negocio_id=?", id);
            }
            launch(row);
            markReady(id, row);
            central.update(
                    "INSERT INTO plataforma_auditoria(usuario_id,negocio_id,accion) VALUES(?,?,'HABILITAR_INSTALACION')",
                    identity.userId(),
                    id);
            return describe(
                    central.queryForMap(
                            "SELECT * FROM plataforma_instalaciones WHERE negocio_id=?", id));
        } catch (Exception exception) {
            String message =
                    exception instanceof ResponseStatusException rejected
                            ? rejected.getReason()
                            : exception instanceof SQLException
                                    ? "MySQL no permitió crear o preparar la base. Revisá permisos CREATE DATABASE, CREATE USER y GRANT del usuario de aprovisionamiento."
                                    : "La instalación no terminó. Revisá los registros de su carpeta y reintentá; se conserva la misma base y gerente.";
            central.update(
                    "UPDATE plataforma_instalaciones SET estado='ERROR',error=? WHERE negocio_id=?",
                    message,
                    id);
            throw error(HttpStatus.CONFLICT, message);
        }
    }

    private void createDatabase(Map<String, Object> row) throws SQLException {
        String schema = identifier(row.get("esquema")), user = identifier(row.get("db_usuario"));
        String password = crypto.decrypt((String) row.get("db_clave_cifrada"));
        // Solo identificadores generados internamente; ninguna entrada de formulario forma SQL DDL.
        try (var connection = DriverManager.getConnection(url, adminUser, adminPassword);
                var statement = connection.createStatement()) {
            statement.executeUpdate(
                    "CREATE DATABASE IF NOT EXISTS `"
                            + schema
                            + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
            try (var create =
                    connection.prepareStatement(
                            "CREATE USER IF NOT EXISTS '"
                                    + user
                                    + "'@'"
                                    + clientHost
                                    + "' IDENTIFIED BY ?")) {
                create.setString(1, password);
                create.executeUpdate();
            }
            statement.executeUpdate(
                    "GRANT ALL PRIVILEGES ON `"
                            + schema
                            + "`.* TO '"
                            + user
                            + "'@'"
                            + clientHost
                            + "'");
        }
        Flyway.configure()
                .dataSource(tenantUrl(url, schema), user, password)
                .locations("classpath:db/migration")
                .cleanDisabled(true)
                .load()
                .migrate();
    }

    private void seed(Map<String, Object> row, Map<String, Object> business, Input input)
            throws SQLException {
        try (var connection =
                DriverManager.getConnection(
                        tenantUrl(url, identifier(row.get("esquema"))),
                        identifier(row.get("db_usuario")),
                        crypto.decrypt((String) row.get("db_clave_cifrada")))) {
            connection.setAutoCommit(false);
            try {
                try (var update =
                        connection.prepareStatement(
                                "UPDATE comercios SET nombre=?,licencia_controlada=TRUE,licencia_hasta=? WHERE id=1")) {
                    update.setString(
                            1,
                            ((String) business.get("nombre"))
                                    .substring(
                                            0,
                                            Math.min(
                                                    150,
                                                    ((String) business.get("nombre")).length())));
                    update.setObject(2, row.get("vencimiento_licencia"));
                    update.executeUpdate();
                }
                try (var count = connection.createStatement();
                        var found = count.executeQuery("SELECT COUNT(*) FROM usuarios")) {
                    found.next();
                    if (found.getInt(1) == 0)
                        try (var insert =
                                connection.prepareStatement(
                                        "INSERT INTO usuarios(comercio_id,usuario,nombre,password_hash,rol,permisos,security_email) VALUES(1,?,?,?,'DUENO',?,?)")) {
                            insert.setString(1, input.username().trim());
                            insert.setString(2, input.name().trim());
                            insert.setString(3, passwords.hash(input.password()));
                            insert.setString(4, String.join(",", UserService.ALL_PERMISSIONS));
                            insert.setString(5, input.email().trim());
                            insert.executeUpdate();
                        }
                    else
                        try (var query =
                                connection.prepareStatement(
                                        "SELECT usuario,nombre,password_hash,security_email,rol FROM usuarios WHERE comercio_id=1 AND usuario=?")) {
                            query.setString(1, input.username().trim());
                            try (var existing = query.executeQuery()) {
                                if (!existing.next()
                                        || !"DUENO".equals(existing.getString("rol"))
                                        || !passwords.verify(
                                                input.password(),
                                                existing.getString("password_hash"))
                                        || !input.name().trim().equals(existing.getString("nombre"))
                                        || !input.email()
                                                .trim()
                                                .equals(existing.getString("security_email")))
                                    throw error(
                                            HttpStatus.CONFLICT,
                                            "Ya existe un gerente con otros datos. No se cambió su contraseña. Reintentá con los datos originales.");
                            }
                        }
                }
                connection.commit();
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    private synchronized void launch(Map<String, Object> row)
            throws IOException, InterruptedException {
        long id = ((Number) row.get("negocio_id")).longValue();
        requireOperational(id);
        Path folder = directory.resolve(identifier(row.get("esquema")));
        Files.createDirectories(folder);
        writeState(folder, businessState(id));
        if (healthy(row)) {
            if (supportsServiceControl(row)
                    && supportsBusinessLinks(row, businessSlug(id))
                    && installationCurrent(jar, web, folder)) return;
            stopLegacyInstallation(row, folder.resolve("alon.jar"));
        }
        var previous = processes.get(id);
        if (previous != null && previous.isAlive())
            throw error(
                    HttpStatus.CONFLICT,
                    "La instancia todavía está iniciando. Esperá y consultá su estado.");
        int port = ((Number) row.get("puerto")).intValue();
        try (var probe = new java.net.ServerSocket()) {
            probe.bind(new InetSocketAddress("127.0.0.1", port));
        }
        Files.copy(jar, folder.resolve("alon.jar"), StandardCopyOption.REPLACE_EXISTING);
        try (var files = Files.walk(web)) {
            for (Path file : files.toList()) {
                Path target = folder.resolve("web").resolve(web.relativize(file));
                if (Files.isDirectory(file)) Files.createDirectories(target);
                else Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        boolean windows = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");
        String java =
                Path.of(System.getProperty("java.home"), "bin", windows ? "javaw.exe" : "java")
                        .toString();
        var builder =
                new ProcessBuilder(
                        java, "-Xms64m", "-Xmx384m", "-jar", folder.resolve("alon.jar").toString());
        builder.directory(folder.toFile())
                .redirectOutput(folder.resolve("servidor.log").toFile())
                .redirectError(folder.resolve("servidor-error.log").toFile());
        var env = builder.environment();
        // No heredar credenciales administrativas, perfiles ni rutas del comercio central.
        env.keySet()
                .removeIf(
                        key ->
                                key.startsWith("SPRING_")
                                        || key.startsWith("ALON_")
                                        || key.equals("PORT")
                                        || key.equals("SERVER_ADDRESS"));
        env.put("SPRING_DATASOURCE_URL", tenantUrl(url, identifier(row.get("esquema"))));
        env.put("SPRING_DATASOURCE_USERNAME", identifier(row.get("db_usuario")));
        env.put("SPRING_DATASOURCE_PASSWORD", crypto.decrypt((String) row.get("db_clave_cifrada")));
        env.put("PORT", Integer.toString(port));
        env.put("SERVER_ADDRESS", "127.0.0.1");
        env.put("SERVER_SERVLET_SESSION_COOKIE_NAME", "ALON_SESSION_" + id);
        env.put("SESSION_COOKIE_SECURE", "false");
        env.put("ALON_PROVISIONING_ENABLED", "false");
        env.put("ALON_SERVICE_STATE_FILE", folder.resolve("service-state").toString());
        env.put(
                "SPRING_APPLICATION_JSON",
                "{\"alon\":{\"instance-id\":\""
                        + row.get("identificador")
                        + "\",\"business-slug\":\""
                        + businessSlug(id)
                        + "\",\"instance-web\":true,\"browser-cookie-name\":\"ALON_BROWSER_"
                        + id
                        + "\",\"local-accounts\":false}}");
        var process = builder.start();
        processes.put(id, process);
        for (int attempt = 0; attempt < 180; attempt++) {
            if (healthy(row)) return;
            if (!process.isAlive())
                throw error(
                        HttpStatus.CONFLICT,
                        "La instancia se detuvo al arrancar. Revisá su servidor.log y reintentá.");
            Thread.sleep(1000);
        }
        throw error(
                HttpStatus.CONFLICT,
                "La instancia sigue iniciando. Consultá su estado antes de reintentar.");
    }

    static boolean installationCurrent(Path jar, Path web, Path folder) throws IOException {
        Path installedJar = folder.resolve("alon.jar");
        if (!Files.isRegularFile(installedJar) || Files.mismatch(jar, installedJar) != -1)
            return false;
        try (var files = Files.walk(web)) {
            for (Path source : files.filter(Files::isRegularFile).toList()) {
                Path target = folder.resolve("web").resolve(web.relativize(source));
                if (!Files.isRegularFile(target) || Files.mismatch(source, target) != -1)
                    return false;
            }
        }
        return true;
    }

    private boolean healthy(Map<String, Object> row) {
        try {
            var request =
                    HttpRequest.newBuilder(URI.create(baseUrl(row) + "/api/v1/health"))
                            .timeout(Duration.ofSeconds(2))
                            .GET()
                            .build();
            var response = client().send(request, HttpResponse.BodyHandlers.discarding());
            return response.statusCode() == 200
                    && response.headers()
                            .firstValue("X-Alon-Instance")
                            .orElse("")
                            .equals(row.get("identificador"));
        } catch (Exception exception) {
            return false;
        }
    }

    private boolean supportsServiceControl(Map<String, Object> row) {
        try {
            var request =
                    HttpRequest.newBuilder(URI.create(baseUrl(row) + "/api/v1/health"))
                            .timeout(Duration.ofSeconds(2))
                            .GET()
                            .build();
            var response = client().send(request, HttpResponse.BodyHandlers.discarding());
            return response.statusCode() == 200
                    && response.headers()
                            .firstValue("X-Alon-Instance")
                            .orElse("")
                            .equals(row.get("identificador"))
                    && response.headers()
                            .firstValue("X-Alon-Service-Control")
                            .orElse("")
                            .equals("enabled");
        } catch (Exception exception) {
            return false;
        }
    }

    private boolean verifyBlocked(Map<String, Object> row, String state) {
        try {
            var request =
                    HttpRequest.newBuilder(URI.create(baseUrl(row) + "/api/v1/auth/status"))
                            .timeout(Duration.ofSeconds(3))
                            .GET()
                            .build();
            var response = client().send(request, HttpResponse.BodyHandlers.discarding());
            return confirmsBlock(
                    response.statusCode(),
                    response.headers().firstValue("X-Alon-Service-Control").orElse(""),
                    response.headers().firstValue("X-Alon-Service-State").orElse(""),
                    state);
        } catch (java.net.ConnectException stopped) {
            return true;
        } catch (Exception exception) {
            return false;
        }
    }

    static boolean confirmsBlock(
            int status, String capability, String actualState, String expectedState) {
        return status == 403
                && capability.equals("enabled")
                && actualState.equals(expectedState)
                && Set.of("BAJA", "SUSPENDIDO", "PREPARACION").contains(expectedState);
    }

    /** Recupera instancias ya preparadas sin recrear usuarios ni extender licencias. */
    @EventListener(ApplicationReadyEvent.class)
    public void resume() {
        if (!enabled) return;
        resuming = true;
        resumeFailures = 0;
        Thread.ofPlatform()
                .daemon()
                .name("alon-business-resume")
                .start(
                        () -> {
                            try {
                                for (var row :
                                        central.queryForList(
                                                "SELECT * FROM plataforma_instalaciones WHERE iniciar=TRUE")) {
                                    long id = ((Number) row.get("negocio_id")).longValue();
                                    try (var lock = source.getConnection()) {
                                        try {
                                            acquire(lock, id);
                                        } catch (ResponseStatusException busy) {
                                            resumeFailures++;
                                            continue;
                                        }
                                        try {
                                            if (central.queryForList(
                                                            "SELECT negocio_id FROM plataforma_instalaciones WHERE negocio_id=? AND iniciar=TRUE",
                                                            id)
                                                    .isEmpty()) continue;
                                            String state = businessState(id);
                                            writeState(
                                                    directory.resolve(
                                                            identifier(row.get("esquema"))),
                                                    state);
                                            if (Set.of("ACTIVO", "PRUEBA").contains(state)) {
                                                launch(row);
                                                markReady(id, row);
                                            } else {
                                                if (healthy(row) && !supportsServiceControl(row))
                                                    stopLegacyInstallation(
                                                            row,
                                                            directory
                                                                    .resolve(
                                                                            identifier(
                                                                                    row.get(
                                                                                            "esquema")))
                                                                    .resolve("alon.jar"));
                                                if (!verifyBlocked(row, state))
                                                    throw error(
                                                            HttpStatus.CONFLICT,
                                                            "No se pudo confirmar la inhabilitación pendiente de esta instalación.");
                                            }
                                            confirmRecoveredState(id);
                                        } finally {
                                            release(lock, id);
                                        }
                                    } catch (Exception exception) {
                                        resumeFailures++;
                                        stateFailed(id);
                                        central.update(
                                                "UPDATE plataforma_instalaciones SET estado='ERROR',error='No se pudo sincronizar la instalación. Consultá el estado y reintentá.' WHERE negocio_id=?",
                                                row.get("negocio_id"));
                                    }
                                }
                                for (var pending :
                                        central.queryForList(
                                                "SELECT n.id FROM plataforma_negocios n LEFT JOIN plataforma_instalaciones i ON i.negocio_id=n.id WHERE n.estado_objetivo IS NOT NULL AND n.baja_iniciada_en IS NULL AND (i.negocio_id IS NULL OR i.iniciar=FALSE)")) {
                                    long id = ((Number) pending.get("id")).longValue();
                                    try (var lock = source.getConnection()) {
                                        acquire(lock, id);
                                        try {
                                            var rows =
                                                    central.queryForList(
                                                            "SELECT * FROM plataforma_instalaciones WHERE negocio_id=?",
                                                            id);
                                            if (!rows.isEmpty())
                                                writeState(
                                                        directory.resolve(
                                                                identifier(
                                                                        rows.getFirst()
                                                                                .get("esquema"))),
                                                        businessState(id));
                                            confirmRecoveredState(id);
                                        } finally {
                                            release(lock, id);
                                        }
                                    } catch (Exception failure) {
                                        resumeFailures++;
                                        stateFailed(id);
                                    }
                                }
                            } catch (RuntimeException failure) {
                                resumeFailures++;
                            } finally {
                                resuming = false;
                            }
                        });
    }

    private void confirmRecoveredState(long id) {
        var transaction =
                new org.springframework.transaction.support.TransactionTemplate(
                        new org.springframework.jdbc.support.JdbcTransactionManager(source));
        transaction.executeWithoutResult(
                tx -> {
                    central.update(
                            "INSERT INTO plataforma_auditoria(usuario_id,negocio_id,accion,detalle) SELECT estado_actor,id,'ESTADO_RECUPERADO',CONCAT(estado,' -> ',estado_objetivo,'. ',estado_objetivo_motivo) FROM plataforma_negocios WHERE id=? AND estado_objetivo IS NOT NULL AND estado_actor IS NOT NULL AND baja_iniciada_en IS NULL",
                            id);
                    central.update(
                            "UPDATE plataforma_negocios SET estado=estado_objetivo,estado_motivo=estado_objetivo_motivo,estado_objetivo=NULL,estado_aplicacion='CONFIRMADO',estado_error='' WHERE id=? AND estado_objetivo IS NOT NULL AND baja_iniciada_en IS NULL",
                            id);
                });
    }

    public Map<String, Object> readiness() {
        return Map.of(
                "ready",
                !resuming && resumeFailures == 0,
                "starting",
                resuming,
                "failures",
                resumeFailures);
    }

    private void markReady(long id, Map<String, Object> row) {
        String slug = businessSlug(id);
        central.update(
                "UPDATE plataforma_instalaciones SET estado='LISTO',error='' WHERE negocio_id=?",
                id);
        central.update(
                "UPDATE plataforma_negocios SET gestion_url=?,tienda_url=?,base_referencia=?,url_slug=? WHERE id=?",
                BusinessLinks.management(id, slug),
                BusinessLinks.shop(id, slug),
                row.get("esquema"),
                slug,
                id);
    }

    private String businessSlug(long id) {
        var business =
                central.queryForMap(
                        "SELECT nombre,url_slug FROM plataforma_negocios WHERE id=?", id);
        String slug = java.util.Objects.toString(business.get("url_slug"), "");
        return slug.matches("[a-z0-9]+(?:-[a-z0-9]+)*")
                ? slug
                : BusinessLinks.slug(String.valueOf(business.get("nombre")));
    }

    private static String baseUrl(Map<String, Object> row) {
        return "http://127.0.0.1:" + row.get("puerto");
    }

    private boolean supportsBusinessLinks(Map<String, Object> row, String slug) {
        try {
            var response =
                    client().send(
                                    HttpRequest.newBuilder(
                                                    URI.create(baseUrl(row) + "/api/v1/health"))
                                            .timeout(Duration.ofSeconds(2))
                                            .GET()
                                            .build(),
                                    HttpResponse.BodyHandlers.discarding());
            return response.statusCode() == 200
                    && response.headers()
                            .firstValue("X-Alon-Business-Slug")
                            .orElse("")
                            .equals(slug);
        } catch (Exception exception) {
            return false;
        }
    }

    static int portFor(long id) {
        if (id < 1 || id > 40000)
            throw error(HttpStatus.CONFLICT, "No quedan puertos locales reservables.");
        return 20000 + (int) id;
    }

    static String identifier(Object value) {
        String text = String.valueOf(value);
        if (!text.matches("alon_[nu]_[a-f0-9]{20}"))
            throw new IllegalArgumentException("Identificador de instalación inválido");
        return text;
    }

    static String tenantUrl(String original, String schema) {
        identifier(schema);
        if (!original.matches("jdbc:mysql://[^/?]+/[^?;]+(\\?.*)?"))
            throw error(
                    HttpStatus.CONFLICT,
                    "El alta requiere una URL MySQL con un solo servidor y base.");
        int slash = original.indexOf('/', "jdbc:mysql://".length()),
                query = original.indexOf('?', slash);
        return original.substring(0, slash + 1)
                + schema
                + (query < 0 ? "" : original.substring(query));
    }

    static void validate(Input input) {
        if (input.username().trim().equalsIgnoreCase("alon")
                || !input.username().trim().matches("[a-zA-Z0-9_.-]{3,80}")
                || !UserService.ALL_PERMISSIONS.containsAll(input.permissions()))
            throw error(
                    HttpStatus.BAD_REQUEST,
                    "Revisá el usuario del gerente y sus permisos. El nombre alon está reservado.");
    }

    private static ResponseStatusException error(HttpStatus status, String message) {
        return new ResponseStatusException(status, message);
    }

    private static void acquire(Connection connection, long id) throws SQLException {
        try (var statement = connection.prepareStatement("SELECT GET_LOCK(?,0)")) {
            statement.setString(1, "alon-provision-" + id);
            try (var row = statement.executeQuery()) {
                row.next();
                if (row.getInt(1) != 1)
                    throw error(
                            HttpStatus.CONFLICT,
                            "Este negocio ya se está preparando. Esperá y consultá su estado.");
            }
        }
    }

    private static void release(Connection connection, long id) throws SQLException {
        try (var statement = connection.prepareStatement("SELECT RELEASE_LOCK(?)")) {
            statement.setString(1, "alon-provision-" + id);
            statement.execute();
        }
    }

    public record Input(
            @NotBlank @Size(min = 3, max = 80) String username,
            @NotBlank @Size(max = 160) String name,
            @NotBlank @Size(min = 4, max = 128) String password,
            @NotBlank @Email @Size(max = 254) String email,
            @Min(1) @Max(120) int months,
            @NotNull @Size(min = 1, max = 20) List<@NotBlank String> permissions) {}
}
