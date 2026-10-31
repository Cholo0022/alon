// Obtiene el comercio y los permisos de la sesión que ya verificó el servidor.
package ar.com.alon.security;

import java.util.Arrays;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

/** La identidad proviene de la sesión verificada, no de los datos que envía el formulario. */
@Component
public class RequestIdentity {
    /**
     * Recupera la identidad validada por el filtro de permisos para la solicitud actual o exige
     * iniciar sesión.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> user() {
        if (RequestContextHolder.getRequestAttributes()
                        instanceof ServletRequestAttributes attributes
                && attributes.getRequest().getAttribute("alonIdentity") instanceof Map<?, ?> user) {
            return (Map<String, Object>) user;
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Iniciá sesión para continuar.");
    }

    /**
     * Devuelve el identificador del comercio de la sesión actual para aislar consultas y
     * escrituras.
     */
    public long storeId() {
        return ((Number) user().get("comercio_id")).longValue();
    }

    /**
     * Devuelve el identificador de la persona autenticada para registrar autoría de las
     * operaciones.
     */
    public long userId() {
        return ((Number) user().get("id")).longValue();
    }

    /** Comprueba si el usuario es dueño o tiene el permiso solicitado en su lista de accesos. */
    public boolean permits(String permission) {
        return "DUENO".equals(user().get("rol"))
                || Arrays.asList(String.valueOf(user().get("permisos")).split(","))
                        .contains(permission);
    }

    /** Rechaza con acceso prohibido la operación cuando el usuario no posee el permiso indicado. */
    public void require(String permission) {
        if (!permits(permission))
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "No tenés permiso para esta operación.");
    }
}
