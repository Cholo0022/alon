package ar.com.alon.security;

import java.io.IOException;
import java.nio.file.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.Map;

/** El estado administrativo y la prueba controlan el acceso, nunca los pagos del abono. */
final class InstanceTerms {
    private InstanceTerms() {}

    static String state(String file, Clock clock) throws IOException {
        String state = Files.readString(Path.of(file)).trim();
        if (!"PRUEBA".equals(state)) return state;
        return deadline(file).isAfter(LocalDateTime.now(localClock(clock)))
                ? state
                : "PRUEBA_VENCIDA";
    }

    private static Clock localClock(Clock clock) {
        return clock.withZone(ZoneId.of("America/Argentina/Buenos_Aires"));
    }

    private static LocalDateTime deadline(String file) throws IOException {
        try {
            return LocalDateTime.parse(
                    Files.readString(Path.of(file).resolveSibling("service-trial-until")).trim());
        } catch (RuntimeException failure) {
            throw new IOException("No se pudo verificar el plazo de prueba.", failure);
        }
    }

    static Map<String, Object> status(String file, Clock clock) {
        try {
            String state = state(file, clock);
            boolean trial = state.startsWith("PRUEBA");
            LocalDateTime until = trial ? deadline(file) : null;
            long days =
                    until == null
                            ? -1
                            : ChronoUnit.DAYS.between(
                                    LocalDate.now(localClock(clock)),
                                    until.minusNanos(1).toLocalDate());
            boolean active = "ACTIVO".equals(state) || "PRUEBA".equals(state);
            return Map.of(
                    "managed",
                    trial,
                    "active",
                    active,
                    "expiresAt",
                    until == null ? "" : until.toString(),
                    "expirationDate",
                    until == null ? "" : until.minusNanos(1).toLocalDate().toString(),
                    "daysRemaining",
                    days,
                    "expiringSoon",
                    trial && active && days <= 10);
        } catch (IOException failure) {
            return Map.of("managed", true, "active", false, "expiresAt", "", "expiringSoon", false);
        }
    }
}
