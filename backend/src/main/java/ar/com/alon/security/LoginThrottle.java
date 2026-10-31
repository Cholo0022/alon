// Limita los intentos seguidos de ingreso para proteger las cuentas.
package ar.com.alon.security;

import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Limita los intentos de ingreso por dirección de origen. */
@Component
public class LoginThrottle {
    private record Window(long start, int attempts) {}

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final Clock clock;

    /**
     * Recibe y conserva las dependencias necesarias para el reloj de las ventanas de intentos de
     * acceso.
     */
    public LoginThrottle(Clock clock) {
        this.clock = clock;
    }

    /**
     * Registra intentos de acceso por dirección, limpia ventanas vencidas y bloquea el exceso de
     * intentos dentro de quince minutos.
     */
    public synchronized void check(String address) {
        long now = clock.millis();
        windows.entrySet().removeIf(entry -> now - entry.getValue().start() >= 900_000);
        if (!windows.containsKey(address) && windows.size() >= 10_000) reject();
        Window window = windows.getOrDefault(address, new Window(now, 0));
        if (window.attempts() >= 10) reject();
        windows.put(address, new Window(window.start(), window.attempts() + 1));
    }

    /**
     * Interrumpe el acceso con estado 429 e indica que debe esperarse antes de intentar nuevamente.
     */
    private void reject() {
        throw new ResponseStatusException(
                HttpStatus.TOO_MANY_REQUESTS,
                "Demasiados intentos. Volvé a intentar en 15 minutos.");
    }
}
