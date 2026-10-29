package ar.com.alon.security;

import java.util.Arrays;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

/** Identity is always established by the permission filter, never by a request body. */
@Component
public class RequestIdentity {
    @SuppressWarnings("unchecked")
    public Map<String, Object> user() {
        if (RequestContextHolder.getRequestAttributes()
                        instanceof ServletRequestAttributes attributes
                && attributes.getRequest().getAttribute("alonIdentity") instanceof Map<?, ?> user) {
            return (Map<String, Object>) user;
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Iniciá sesión para continuar.");
    }

    public long storeId() {
        return ((Number) user().get("comercio_id")).longValue();
    }

    public long userId() {
        return ((Number) user().get("id")).longValue();
    }

    public boolean permits(String permission) {
        return "DUENO".equals(user().get("rol"))
                || Arrays.asList(String.valueOf(user().get("permisos")).split(","))
                        .contains(permission);
    }

    public void require(String permission) {
        if (!permits(permission))
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "No tenés permiso para esta operación.");
    }
}
