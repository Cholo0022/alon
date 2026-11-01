package ar.com.alon.backup;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.server.ResponseStatusException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
public class RestoreGate extends OncePerRequestFilter {
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock(true);
    private final AtomicBoolean restoring = new AtomicBoolean();
    public static final String MARKER = "restore-recovery-required.txt";
    private final ReentrantLock admission = new ReentrantLock();
    private volatile boolean recoveryRequired;

    public RestoreGate(@Value("${alon.backup-directory:./data/backups}") String directory) {
        recoveryRequired = Files.exists(Path.of(directory).resolve(MARKER));
    }

    public void requireRecovery() {
        recoveryRequired = true;
    }

    private final AtomicLong epoch = new AtomicLong();

    public void begin() {
        if (recoveryRequired)
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Alon necesita revisar una recuperación interrumpida.");
        if (!restoring.compareAndSet(false, true))
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Ya hay una restauración en curso.");
        try {
            if (lock.writeLock().tryLock(30, TimeUnit.SECONDS)) return;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        restoring.set(false);
        throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Hay operaciones en curso. Esperá a que terminen y volvé a intentar.");
    }

    public void end() {
        lock.writeLock().unlock();
        restoring.set(false);
    }

    public void expireSessions() {
        epoch.incrementAndGet();
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        if (!req.getRequestURI().startsWith("/api/")) {
            chain.doFilter(req, res);
            return;
        }
        res.setHeader("Cache-Control", "no-store");
        boolean restoreRequest =
                req.getMethod().equals("POST")
                        && req.getRequestURI().equals("/api/v1/backups/restore");
        boolean acquired = false, admitted = false;
        try {
            if (recoveryRequired
                    || restoring.get()
                    || (restoreRequest && !(admitted = admission.tryLock()))
                    || (!restoreRequest
                            && !(acquired = lock.readLock().tryLock(0, TimeUnit.SECONDS)))) {
                res.setStatus(503);
                res.setHeader(
                        "X-Alon-Maintenance", recoveryRequired ? "recovery-required" : "restore");
                res.setHeader("Retry-After", "10");
                res.setContentType("application/json;charset=UTF-8");
                res.getWriter()
                        .write(
                                recoveryRequired
                                        ? "{\"error\":\"Alon necesita revisar una recuperación interrumpida. No se habilitarán operaciones hasta completar la revisión.\"}"
                                        : "{\"error\":\"Alon está restaurando un backup. Esperá a que termine antes de continuar.\"}");
                return;
            }
            HttpSession session = req.getSession(false);
            if (session != null) {
                Object saved = session.getAttribute("alonRestoreEpoch");
                if (saved instanceof Long value && value != epoch.get()) session.invalidate();
                else session.setAttribute("alonRestoreEpoch", epoch.get());
            }
            chain.doFilter(req, res);
            // Los ingresos que crean una sesión durante esta solicitud también llevan la versión.
            session = req.getSession(false);
            if (!restoreRequest && session != null)
                session.setAttribute("alonRestoreEpoch", epoch.get());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            res.sendError(503);
        } finally {
            if (acquired) lock.readLock().unlock();
            if (admitted) admission.unlock();
        }
    }
}
