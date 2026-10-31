package ar.com.alon.security;

import ar.com.alon.users.UserService;
import jakarta.servlet.http.*;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(noRollbackFor = AccessSecurityService.SecondFactorRejected.class)
public class AccessSecurityService {
    private final JdbcTemplate jdbc;
    private final UserService users;
    private final RequestIdentity identity;
    private final TotpSecurity crypto;
    private final Clock clock;
    private final SecurityNotifications mail;
    private final boolean secureCookie;
    private final boolean requireIp;
    private static final String COOKIE = "ALON_BROWSER";

    private record Pending(
            long user,
            long store,
            long version,
            String role,
            Instant expires,
            String stage,
            String browser,
            String secret) {}

    public static class SecondFactorRejected extends ResponseStatusException {
        /** Mantiene persistidos los contadores de intentos incluso cuando se rechaza el código. */
        public SecondFactorRejected(HttpStatus status, String message) {
            super(status, message);
        }
    }

    /**
     * Conecta sesión, validación de códigos, dispositivos, auditoría y avisos de correo del
     * gerente.
     */
    public AccessSecurityService(
            JdbcTemplate jdbc,
            UserService users,
            RequestIdentity identity,
            TotpSecurity crypto,
            Clock clock,
            SecurityNotifications mail,
            @Value("${server.servlet.session.cookie.secure:false}") boolean secureCookie,
            @Value("${alon.require-ip-approval:true}") boolean requireIp) {
        this.jdbc = jdbc;
        this.users = users;
        this.identity = identity;
        this.crypto = crypto;
        this.clock = clock;
        this.mail = mail;
        this.secureCookie = secureCookie;
        this.requireIp = requireIp;
    }

    /** Informa la vigencia del negocio; el superusuario conserva acceso para renovar licencias. */
    public Map<String, Object> license(Map<String, Object> user) {
        if (user.isEmpty()) return Map.of();
        var row =
                jdbc.queryForMap(
                        "SELECT licencia_controlada,licencia_hasta FROM comercios WHERE id=?",
                        number(user, "comercio_id"));
        boolean managed = enabled(row.get("licencia_controlada"));
        Object until = row.get("licencia_hasta");
        boolean active =
                "SUPER".equals(user.get("rol"))
                        || !managed
                        || until != null && instant(until).isAfter(clock.instant());
        return Map.of(
                "active",
                active,
                "managed",
                managed,
                "expiresAt",
                until == null ? "" : until.toString());
    }

    /**
     * Inicia sesión de alon con un código de Google o de recuperación, sin permitir activar un
     * teléfono anónimo.
     */
    public void superLogin(String code, HttpServletRequest request) {
        var rows =
                jdbc.queryForList(
                        "SELECT id,comercio_id FROM usuarios WHERE usuario='alon' AND rol='SUPER' AND activo=TRUE");
        if (rows.size() != 1)
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Primero vinculá Google Authenticator desde tu acceso inicial.");
        var user = locked(number(rows.getFirst(), "comercio_id"), number(rows.getFirst(), "id"));
        if (!enabled(user.get("mfa_enabled")))
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Primero vinculá Google Authenticator usando tu contraseña inicial.");
        verify(user, code, request);
        request.getSession(true);
        complete(user, request, "");
        event(user, "INGRESO_SUPER", request);
    }

    /** Obtiene un identificador numérico de una fila interna del usuario. */
    private long number(Map<String, Object> user, String key) {
        return ((Number) user.get(key)).longValue();
    }

    /** Normaliza indicadores booleanos que JDBC puede devolver como números. */
    private boolean enabled(Object value) {
        return Boolean.TRUE.equals(value) || value instanceof Number n && n.intValue() != 0;
    }

    /** Devuelve la fecha del reloj de negocio en un formato adecuado para MySQL. */
    private Timestamp now() {
        return Timestamp.from(clock.instant());
    }

    /** Convierte fechas JDBC, incluidas DATETIME de MySQL, al instante del servidor. */
    private Instant instant(Object value) {
        if (value instanceof Timestamp timestamp) return timestamp.toInstant();
        if (value instanceof LocalDateTime date)
            return date.atZone(ZoneId.systemDefault()).toInstant();
        throw new IllegalArgumentException("Fecha de seguridad inválida");
    }

    /** Limita las descripciones del cliente y descarta controles de texto no imprimibles. */
    private String text(String value, int max) {
        return value == null
                ? ""
                : value.replaceAll("[\\p{Cntrl}]", "")
                        .substring(0, Math.min(max, value.replaceAll("[\\p{Cntrl}]", "").length()));
    }

    /** Obtiene la cookie opaca sin aceptar valores arbitrarios o excesivamente largos. */
    private String browserToken(HttpServletRequest request) {
        if (request.getCookies() != null)
            for (var cookie : request.getCookies())
                if (COOKIE.equals(cookie.getName())
                        && cookie.getValue().matches("[A-Za-z0-9_-]{43}")) return cookie.getValue();
        return "";
    }

    /**
     * Emite una cookie de navegador inaccesible para JavaScript, con SameSite estricto y Secure en
     * HTTPS.
     */
    private void cookie(HttpServletRequest request, HttpServletResponse response, String token) {
        response.addHeader(
                "Set-Cookie",
                ResponseCookie.from(COOKIE, token)
                        .httpOnly(true)
                        .secure(secureCookie || request.isSecure())
                        .sameSite("Strict")
                        .path("/api/v1")
                        .maxAge(Duration.ofDays(90))
                        .build()
                        .toString());
    }

    /** Guarda eventos sin contraseñas, secretos, cookies ni códigos de recuperación. */
    private void event(Map<String, Object> user, String type, HttpServletRequest request) {
        jdbc.update(
                "INSERT INTO security_events(comercio_id,usuario_id,tipo,ip,navegador,creado_en) VALUES(?,?,?,?,?,?)",
                number(user, "comercio_id"),
                number(user, "id"),
                type,
                text(request.getRemoteAddr(), 64),
                text(request.getHeader("User-Agent"), 255),
                now());
    }

    /**
     * Consulta un usuario activo y bloquea su fila para serializar códigos y recuperaciones de un
     * solo uso.
     */
    private Map<String, Object> locked(long store, long id) {
        var row =
                jdbc.queryForMap(
                        "SELECT id,comercio_id,usuario,nombre,rol,permisos,activo,auth_version,mfa_enabled,mfa_secret,mfa_last_step,mfa_failures,mfa_blocked_until,security_email FROM usuarios WHERE comercio_id=? AND id=? FOR UPDATE",
                        store,
                        id);
        if (!enabled(row.get("activo")))
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "El acceso de este usuario ya no está habilitado.");
        return row;
    }

    /**
     * Establece una sesión completa únicamente después de comprobar MFA o la aprobación del
     * navegador.
     */
    private void complete(Map<String, Object> user, HttpServletRequest request, String browser) {
        request.getSession(true);
        request.changeSessionId();
        var session = request.getSession();
        session.removeAttribute("alonPending");
        session.removeAttribute("alonRecoveryDisplay");
        session.setAttribute(
                "alonUser",
                Map.of(
                        "id",
                        number(user, "id"),
                        "comercio_id",
                        number(user, "comercio_id"),
                        "auth_version",
                        number(user, "auth_version")));
        session.setAttribute(
                "alonSecurityExpires", clock.instant().plusSeconds(28800).toEpochMilli());
        session.setAttribute("alonSecurityVerified", true);
        session.setAttribute("alonSecurityVersion", number(user, "auth_version"));
        session.setAttribute("alonBrowserId", browser);
    }

    /**
     * Revalida en cada solicitud la segunda verificación o la cookie y aprobación vigentes del
     * empleado.
     */
    public boolean validSession(Map<String, Object> user, HttpServletRequest request) {
        var session = request.getSession(false);
        if (session == null
                || !(session.getAttribute("alonSecurityExpires") instanceof Long expires)
                || expires <= clock.millis()
                || !Boolean.TRUE.equals(session.getAttribute("alonSecurityVerified"))
                || !Objects.equals(
                        session.getAttribute("alonSecurityVersion"), number(user, "auth_version")))
            return false;
        if ("SUPER".equals(user.get("rol"))) return enabled(user.get("mfa_enabled"));
        if ("DUENO".equals(user.get("rol"))) return true;
        String token = browserToken(request);
        Object id = session.getAttribute("alonBrowserId");
        if (token.isEmpty() || !(id instanceof String)) return false;
        return jdbc.queryForObject(
                        "SELECT COUNT(*) FROM security_browsers WHERE id=? AND comercio_id=? AND usuario_id=? AND token_hash=? AND auth_version=? AND estado='APROBADO' AND vence_en>? AND (?=FALSE OR ip=?)",
                        Integer.class,
                        id,
                        number(user, "comercio_id"),
                        number(user, "id"),
                        crypto.hash(token),
                        number(user, "auth_version"),
                        now(),
                        requireIp,
                        text(request.getRemoteAddr(), 64))
                == 1;
    }

    /**
     * Obtiene la etapa pendiente si sigue vigente; al vencer descarta cualquier secreto temporal.
     */
    private Pending pending(HttpServletRequest request) {
        var session = request.getSession(false);
        if (session != null
                && session.getAttribute("alonPending") instanceof Pending p
                && p.expires().isAfter(clock.instant())) return p;
        if (session != null) {
            session.removeAttribute("alonPending");
            session.removeAttribute("alonRecoveryDisplay");
        }
        throw new ResponseStatusException(
                HttpStatus.UNAUTHORIZED, "La verificación venció. Iniciá sesión nuevamente.");
    }

    /** Revalida que la contraseña, rol y estado del usuario no hayan cambiado durante la espera. */
    private Map<String, Object> pendingUser(Pending p) {
        var user = locked(p.store(), p.user());
        if (number(user, "auth_version") != p.version() || !p.role().equals(user.get("rol")))
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "El acceso cambió. Iniciá sesión nuevamente.");
        return user;
    }

    /**
     * Devuelve solo información necesaria de la etapa de acceso, sin entregar nunca una sesión
     * parcial como usuario autorizado.
     */
    public Map<String, Object> challenge(HttpServletRequest request) {
        if (request.getSession(false) == null
                || request.getSession().getAttribute("alonPending") == null) return Map.of();
        Pending p;
        try {
            p = pending(request);
        } catch (ResponseStatusException e) {
            return Map.of();
        }
        Map<String, Object> user;
        try {
            user = pendingUser(p);
        } catch (ResponseStatusException
                | org.springframework.dao.EmptyResultDataAccessException error) {
            request.getSession().removeAttribute("alonPending");
            request.getSession().removeAttribute("alonRecoveryDisplay");
            return Map.of();
        }
        var result = new HashMap<String, Object>();
        result.put("stage", p.stage());
        result.put("username", user.get("usuario"));
        result.put("expiresAt", p.expires().toString());
        result.put("email", user.get("security_email"));
        if (p.stage().equals("DEVICE")) {
            var rows =
                    jdbc.queryForList(
                            "SELECT estado,referencia,vence_en FROM security_browsers WHERE id=? AND comercio_id=? AND usuario_id=?",
                            p.browser(),
                            p.store(),
                            p.user());
            if (rows.isEmpty()) result.put("deviceStatus", "VENCIDO");
            else {
                var row = rows.getFirst();
                result.put("reference", row.get("referencia"));
                result.put(
                        "deviceStatus",
                        instant(row.get("vence_en")).isAfter(clock.instant())
                                ? row.get("estado")
                                : "VENCIDO");
            }
        }
        if (p.stage().equals("RECOVERY"))
            result.put("recoveryCodes", request.getSession().getAttribute("alonRecoveryDisplay"));
        return result;
    }

    /**
     * Inicia el autenticador de alon, concede acceso al gerente con su contraseña o solicita
     * autorización de navegador para empleados.
     */
    public void begin(
            Map<String, Object> authenticated,
            HttpServletRequest request,
            HttpServletResponse response) {
        var user = locked(number(authenticated, "comercio_id"), number(authenticated, "id"));
        request.getSession(true);
        request.changeSessionId();
        var session = request.getSession();
        session.removeAttribute("alonUser");
        session.removeAttribute("alonSecurityVerified");
        session.removeAttribute("alonRecoveryDisplay");
        if ("DUENO".equals(user.get("rol"))) {
            complete(user, request, "");
            event(user, "INGRESO_GERENTE", request);
            return;
        }
        String stage = "MFA", browser = "", secret = "";
        if ("SUPER".equals(user.get("rol"))) {
            if (!enabled(user.get("mfa_enabled"))) {
                stage = "ENROLL";
                secret = crypto.newSecret();
            }
        } else {
            String token = browserToken(request);
            var found =
                    token.isEmpty()
                            ? List.<Map<String, Object>>of()
                            : jdbc.queryForList(
                                    "SELECT * FROM security_browsers WHERE token_hash=? AND comercio_id=? AND usuario_id=? AND auth_version=? AND vence_en>? AND estado IN ('PENDIENTE','APROBADO')",
                                    crypto.hash(token),
                                    number(user, "comercio_id"),
                                    number(user, "id"),
                                    number(user, "auth_version"),
                                    now());
            if (requireIp
                    && !found.isEmpty()
                    && !text(request.getRemoteAddr(), 64).equals(found.getFirst().get("ip")))
                found = List.of();
            if (!found.isEmpty() && "APROBADO".equals(found.getFirst().get("estado"))) {
                browser = found.getFirst().get("id").toString();
                complete(user, request, browser);
                jdbc.update("UPDATE security_browsers SET ultimo_uso=? WHERE id=?", now(), browser);
                event(user, "INGRESO_EMPLEADO", request);
                return;
            }
            if (!found.isEmpty()) browser = found.getFirst().get("id").toString();
            else {
                int count =
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM security_browsers WHERE comercio_id=? AND usuario_id=? AND creado_en>?",
                                Integer.class,
                                number(user, "comercio_id"),
                                number(user, "id"),
                                Timestamp.from(clock.instant().minusSeconds(3600)));
                if (count >= 10)
                    throw new ResponseStatusException(
                            HttpStatus.TOO_MANY_REQUESTS,
                            "Se alcanzó el límite de solicitudes. Intentá más tarde.");
                token = crypto.token();
                browser = UUID.randomUUID().toString();
                String reference =
                        UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
                jdbc.update(
                        "INSERT INTO security_browsers(id,comercio_id,usuario_id,token_hash,auth_version,referencia,ip,navegador,creado_en,vence_en) VALUES(?,?,?,?,?,?,?,?,?,?)",
                        browser,
                        number(user, "comercio_id"),
                        number(user, "id"),
                        crypto.hash(token),
                        number(user, "auth_version"),
                        reference,
                        text(request.getRemoteAddr(), 64),
                        text(request.getHeader("User-Agent"), 255),
                        now(),
                        Timestamp.from(clock.instant().plusSeconds(1800)));
                cookie(request, response, token);
                event(user, "SOLICITUD_NAVEGADOR", request);
                mail.owners(
                        number(user, "comercio_id"),
                        "Alon: solicitud de ingreso",
                        "El usuario "
                                + user.get("usuario")
                                + " solicita acceso. Referencia: "
                                + reference
                                + ". IP: "
                                + text(request.getRemoteAddr(), 64)
                                + ". Revisá la solicitud dentro de Alon. No apruebes solicitudes que no reconozcas.");
            }
            stage = "DEVICE";
        }
        session.setAttribute(
                "alonPending",
                new Pending(
                        number(user, "id"),
                        number(user, "comercio_id"),
                        number(user, "auth_version"),
                        user.get("rol").toString(),
                        clock.instant().plusSeconds(600),
                        stage,
                        browser,
                        secret));
    }

    /**
     * Entrega el QR y la clave manual únicamente al gerente que ya comprobó su contraseña y está
     * configurando MFA.
     */
    public Map<String, Object> enrollment(HttpServletRequest request) {
        var p = pending(request);
        var user = pendingUser(p);
        if (!p.stage().equals("ENROLL"))
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "No hay una activación pendiente.");
        return Map.of(
                "secret",
                p.secret(),
                "qr",
                crypto.qr(p.secret(), user.get("usuario").toString()),
                "email",
                user.get("security_email"));
    }

    /**
     * Aplica bloqueo por usuario ante códigos incorrectos y evita que varios navegadores eludan el
     * límite.
     */
    private void rejected(Map<String, Object> user) {
        int failures = ((Number) user.get("mfa_failures")).intValue() + 1;
        jdbc.update(
                "UPDATE usuarios SET mfa_failures=?,mfa_blocked_until=? WHERE comercio_id=? AND id=?",
                failures,
                failures >= 5 ? Timestamp.from(clock.instant().plusSeconds(300)) : null,
                number(user, "comercio_id"),
                number(user, "id"));
        throw new SecondFactorRejected(
                failures >= 5 ? HttpStatus.TOO_MANY_REQUESTS : HttpStatus.BAD_REQUEST,
                failures >= 5
                        ? "Demasiados códigos incorrectos. Esperá cinco minutos."
                        : "Código incorrecto, vencido o ya utilizado.");
    }

    /**
     * Comprueba que el usuario no esté temporalmente bloqueado y limpia una ventana de bloqueo
     * vencida.
     */
    private void checkBlock(Map<String, Object> user) {
        if (user.get("mfa_blocked_until") != null) {
            if (instant(user.get("mfa_blocked_until")).isAfter(clock.instant()))
                throw new SecondFactorRejected(
                        HttpStatus.TOO_MANY_REQUESTS,
                        "Esperá cinco minutos antes de volver a verificar.");
            user.put("mfa_failures", 0);
            jdbc.update(
                    "UPDATE usuarios SET mfa_failures=0,mfa_blocked_until=NULL WHERE comercio_id=? AND id=?",
                    number(user, "comercio_id"),
                    number(user, "id"));
        }
    }

    /**
     * Genera diez códigos independientes de 128 bits y guarda únicamente sus hashes para su uso
     * único.
     */
    private List<String> recovery(Map<String, Object> user) {
        jdbc.update(
                "DELETE FROM security_recovery_codes WHERE comercio_id=? AND usuario_id=?",
                number(user, "comercio_id"),
                number(user, "id"));
        var result = new ArrayList<String>();
        for (int i = 0; i < 10; i++) {
            String raw =
                    HexFormat.of()
                            .formatHex(Base64.getUrlDecoder().decode(crypto.token()))
                            .substring(0, 32)
                            .toUpperCase(Locale.ROOT);
            String code =
                    raw.substring(0, 8)
                            + "-"
                            + raw.substring(8, 16)
                            + "-"
                            + raw.substring(16, 24)
                            + "-"
                            + raw.substring(24);
            result.add(code);
            jdbc.update(
                    "INSERT INTO security_recovery_codes(comercio_id,usuario_id,code_hash) VALUES(?,?,?)",
                    number(user, "comercio_id"),
                    number(user, "id"),
                    crypto.hash(raw));
        }
        return result;
    }

    /**
     * Confirma un TOTP o consume atómicamente un código de recuperación sin permitir reutilizar
     * ninguno de ellos.
     */
    private void verify(Map<String, Object> user, String supplied, HttpServletRequest request) {
        checkBlock(user);
        String normalized =
                supplied == null ? "" : supplied.replaceAll("[-\\s]", "").toUpperCase(Locale.ROOT);
        if (normalized.matches("[A-F0-9]{32}")) {
            int changed =
                    jdbc.update(
                            "UPDATE security_recovery_codes SET used_at=? WHERE comercio_id=? AND usuario_id=? AND code_hash=? AND used_at IS NULL",
                            now(),
                            number(user, "comercio_id"),
                            number(user, "id"),
                            crypto.hash(normalized));
            if (changed != 1) rejected(user);
            event(user, "CODIGO_RECUPERACION_USADO", request);
            mail.owners(
                    number(user, "comercio_id"),
                    "Alon: se utilizó un código de recuperación",
                    "Se utilizó un código de recuperación de la cuenta "
                            + user.get("usuario")
                            + ". Si no fuiste vos, revisá tu acceso inmediatamente.");
        } else {
            long step =
                    crypto.matchingStep(
                            crypto.decrypt(user.get("mfa_secret").toString()),
                            normalized,
                            clock.instant().getEpochSecond(),
                            number(user, "mfa_last_step"));
            if (step < 0) rejected(user);
            jdbc.update(
                    "UPDATE usuarios SET mfa_last_step=? WHERE comercio_id=? AND id=?",
                    step,
                    number(user, "comercio_id"),
                    number(user, "id"));
        }
        jdbc.update(
                "UPDATE usuarios SET mfa_failures=0,mfa_blocked_until=NULL WHERE comercio_id=? AND id=?",
                number(user, "comercio_id"),
                number(user, "id"));
    }

    /**
     * Activa o reemplaza el autenticador después de comprobar su primer código, revoca sesiones
     * anteriores y muestra los códigos de emergencia.
     */
    public List<String> confirmEnrollment(String code, String email, HttpServletRequest request) {
        var p = pending(request);
        var user = pendingUser(p);
        if (!p.stage().equals("ENROLL"))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No hay activación pendiente.");
        checkBlock(user);
        long step = crypto.matchingStep(p.secret(), code, clock.instant().getEpochSecond(), -1);
        if (step < 0) rejected(user);
        if (!crypto.hasKey()
                && jdbc.queryForObject(
                                "SELECT COUNT(*) FROM usuarios WHERE mfa_enabled=TRUE",
                                Integer.class)
                        > 0)
            throw new IllegalStateException(
                    "Falta la clave de seguridad existente. Restaurá el archivo respaldado antes de activar otra cuenta.");
        var codes = recovery(user);
        jdbc.update(
                "UPDATE usuarios SET mfa_secret=?,mfa_enabled=TRUE,mfa_last_step=?,mfa_failures=0,mfa_blocked_until=NULL,security_email=?,auth_version=auth_version+1 WHERE comercio_id=? AND id=?",
                crypto.encrypt(p.secret()),
                step,
                email == null ? "" : email.trim(),
                p.store(),
                p.user());
        request.getSession()
                .setAttribute(
                        "alonPending",
                        new Pending(
                                p.user(),
                                p.store(),
                                p.version() + 1,
                                p.role(),
                                clock.instant().plusSeconds(600),
                                "RECOVERY",
                                "",
                                ""));
        request.getSession().setAttribute("alonRecoveryDisplay", codes);
        event(user, "AUTENTICADOR_CONFIGURADO", request);
        return codes;
    }

    /**
     * Completa el ingreso pendiente solo al comprobar MFA, guardar los códigos o recibir aprobación
     * del navegador.
     */
    public void advance(String code, boolean saved, HttpServletRequest request) {
        var p = pending(request);
        var user = pendingUser(p);
        if (p.stage().equals("MFA")) {
            verify(user, code, request);
            complete(user, request, "");
            event(user, "INGRESO_DUENO", request);
            mail.owners(
                    p.store(),
                    "Alon: ingreso de gerente",
                    "Se inició sesión con la cuenta "
                            + user.get("usuario")
                            + ". IP: "
                            + text(request.getRemoteAddr(), 64)
                            + ".");
        } else if (p.stage().equals("RECOVERY")) {
            if (!saved)
                throw new IllegalArgumentException(
                        "Confirmá que guardaste tus códigos de recuperación.");
            complete(user, request, "");
        } else if (p.stage().equals("DEVICE")) {
            String token = browserToken(request);
            if (!token.isEmpty()
                    && jdbc.queryForObject(
                                    "SELECT COUNT(*) FROM security_browsers WHERE id=? AND token_hash=? AND comercio_id=? AND usuario_id=? AND auth_version=? AND estado='APROBADO' AND vence_en>? AND (?=FALSE OR ip=?)",
                                    Integer.class,
                                    p.browser(),
                                    crypto.hash(token),
                                    p.store(),
                                    p.user(),
                                    p.version(),
                                    now(),
                                    requireIp,
                                    text(request.getRemoteAddr(), 64))
                            == 1) {
                complete(user, request, p.browser());
                jdbc.update(
                        "UPDATE security_browsers SET ultimo_uso=? WHERE id=?", now(), p.browser());
                event(user, "INGRESO_EMPLEADO", request);
            }
        } else
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Completá primero la activación del autenticador.");
    }

    /** Cancela una verificación y retira solicitudes aún pendientes sin conceder ningún acceso. */
    public void cancel(HttpServletRequest request) {
        var session = request.getSession(false);
        if (session == null) return;
        if (session.getAttribute("alonPending") instanceof Pending p && p.stage().equals("DEVICE"))
            jdbc.update(
                    "UPDATE security_browsers SET estado='CANCELADO' WHERE id=? AND comercio_id=? AND usuario_id=? AND estado='PENDIENTE'",
                    p.browser(),
                    p.store(),
                    p.user());
        session.removeAttribute("alonPending");
        session.removeAttribute("alonRecoveryDisplay");
    }

    /** Exige una cuenta administradora activa antes de acceder a administración de seguridad. */
    private Map<String, Object> owner() {
        var current = identity.user();
        if (!UserRoles.administrator(current.get("rol")))
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Solo el gerente puede administrar la seguridad.");
        var owner = locked(number(current, "comercio_id"), number(current, "id"));
        if ("SUPER".equals(owner.get("rol")) && !enabled(owner.get("mfa_enabled")))
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Activá el doble factor antes de administrar accesos.");
        return owner;
    }

    /**
     * Restringe los cambios del autenticador a alon; el correo del gerente lo administra el
     * superusuario.
     */
    private Map<String, Object> factorOwner() {
        var user = owner();
        if (!"SUPER".equals(user.get("rol")))
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "El acceso del gerente se administra desde la cuenta alon.");
        return user;
    }

    /**
     * Lista solicitudes, autorizaciones y eventos del propio comercio, junto con el correo de la
     * cuenta administradora.
     */
    public Map<String, Object> dashboard() {
        var user = owner();
        long store = number(user, "comercio_id");
        return Map.of(
                "authenticatorRequired",
                "SUPER".equals(user.get("rol")),
                "username",
                user.get("usuario"),
                "email",
                user.get("security_email"),
                "mailConfigured",
                mail.configured(),
                "mailPending",
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM security_mail_queue WHERE comercio_id=? AND enviado_en IS NULL",
                        Integer.class,
                        store),
                "mailFailed",
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM security_mail_queue WHERE comercio_id=? AND enviado_en IS NULL AND intentos>=3",
                        Integer.class,
                        store),
                "recoveryRemaining",
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM security_recovery_codes WHERE comercio_id=? AND usuario_id=? AND used_at IS NULL",
                        Integer.class,
                        store,
                        number(user, "id")),
                "requests",
                jdbc.queryForList(
                        "SELECT b.id,b.referencia,b.ip,b.navegador,b.creado_en,b.vence_en,u.usuario FROM security_browsers b JOIN usuarios u ON u.id=b.usuario_id AND u.comercio_id=b.comercio_id WHERE b.comercio_id=? AND b.estado='PENDIENTE' AND b.vence_en>? AND u.activo=TRUE AND u.auth_version=b.auth_version ORDER BY b.creado_en DESC LIMIT 100",
                        store,
                        now()),
                "browsers",
                jdbc.queryForList(
                        "SELECT b.id,b.ip,b.navegador,b.aprobado_en,b.ultimo_uso,b.vence_en,u.usuario FROM security_browsers b JOIN usuarios u ON u.id=b.usuario_id AND u.comercio_id=b.comercio_id WHERE b.comercio_id=? AND b.estado='APROBADO' AND b.vence_en>? AND u.activo=TRUE AND u.auth_version=b.auth_version ORDER BY b.aprobado_en DESC LIMIT 100",
                        store,
                        now()),
                "events",
                jdbc.queryForList(
                        "SELECT e.tipo,e.ip,e.navegador,e.creado_en,u.usuario FROM security_events e JOIN usuarios u ON u.id=e.usuario_id AND u.comercio_id=e.comercio_id WHERE e.comercio_id=? ORDER BY e.id DESC LIMIT 30",
                        store));
    }

    /**
     * Devuelve el contador completo de solicitudes vigentes, sin depender de la página del listado.
     */
    public int pendingCount() {
        var user = owner();
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM security_browsers b JOIN usuarios u ON u.comercio_id=b.comercio_id AND u.id=b.usuario_id WHERE b.comercio_id=? AND b.estado='PENDIENTE' AND b.vence_en>? AND u.activo=TRUE AND u.auth_version=b.auth_version",
                Integer.class,
                number(user, "comercio_id"),
                now());
    }

    /**
     * Aprueba, rechaza o revoca un navegador del comercio; la revocación se comprueba en cada
     * operación de sus sesiones.
     */
    public void decide(String id, String action, HttpServletRequest request) {
        var user = owner();
        String state =
                switch (action) {
                    case "approve" -> "APROBADO";
                    case "reject" -> "RECHAZADO";
                    case "revoke" -> "REVOCADO";
                    default -> throw new IllegalArgumentException("Acción inválida.");
                };
        String expected = action.equals("revoke") ? "APROBADO" : "PENDIENTE";
        int changed =
                jdbc.update(
                        "UPDATE security_browsers b JOIN usuarios u ON u.comercio_id=b.comercio_id AND u.id=b.usuario_id SET b.estado=?,b.aprobado_por=?,b.aprobado_en=?,b.vence_en=? WHERE b.id=? AND b.comercio_id=? AND b.estado=? AND b.vence_en>? AND u.activo=TRUE AND u.auth_version=b.auth_version",
                        state,
                        number(user, "id"),
                        now(),
                        Timestamp.from(
                                clock.instant()
                                        .plusSeconds(action.equals("approve") ? 90L * 86400 : 0)),
                        id,
                        number(user, "comercio_id"),
                        expected,
                        now());
        if (changed != 1)
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "La solicitud venció o ya fue atendida.");
        event(user, "NAVEGADOR_" + state, request);
    }

    /** Cambia el correo del propio gerente después de verificar un segundo factor actual. */
    public void email(String email, String code, HttpServletRequest request) {
        var user = factorOwner();
        verify(user, code, request);
        jdbc.update(
                "UPDATE usuarios SET security_email=? WHERE comercio_id=? AND id=?",
                email.trim(),
                number(user, "comercio_id"),
                number(user, "id"));
        event(user, "CORREO_SEGURIDAD_MODIFICADO", request);
    }

    /**
     * Invalida todos los códigos anteriores y entrega un nuevo conjunto tras verificar un factor
     * actual.
     */
    public List<String> regenerate(String code, HttpServletRequest request) {
        var user = factorOwner();
        verify(user, code, request);
        var result = recovery(user);
        event(user, "RECUPERACION_REGENERADA", request);
        return result;
    }

    /**
     * Inicia el reemplazo del celular solo con contraseña y segundo factor; conserva el
     * autenticador anterior hasta confirmar el nuevo.
     */
    public void reset(String password, String code, HttpServletRequest request) {
        var user = factorOwner();
        var credentials =
                new UserService.Credentials(
                        user.get("usuario").toString(),
                        user.get("nombre").toString(),
                        password,
                        jdbc.queryForObject(
                                "SELECT codigo FROM comercios WHERE id=?",
                                String.class,
                                number(user, "comercio_id")));
        users.authenticate(credentials);
        verify(user, code, request);
        request.getSession().removeAttribute("alonUser");
        request.getSession().removeAttribute("alonSecurityVerified");
        request.getSession()
                .setAttribute(
                        "alonPending",
                        new Pending(
                                number(user, "id"),
                                number(user, "comercio_id"),
                                number(user, "auth_version"),
                                user.get("rol").toString(),
                                clock.instant().plusSeconds(600),
                                "ENROLL",
                                "",
                                crypto.newSecret()));
    }
}
