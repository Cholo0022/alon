// Datos públicos del usuario conectado; las contraseñas no forman parte de esta respuesta.
package ar.com.alon.users;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

/** Identidad que recibe la interfaz, sin claves ni datos secretos. */
public record SessionUser(
        long id,
        @JsonProperty("comercio_id") long storeId,
        String usuario,
        String nombre,
        String rol,
        String permisos,
        boolean activo) {
    /**
     * Convierte una fila de usuario en los datos seguros de sesión, excluyendo su contraseña y
     * admitiendo las representaciones booleanas de JDBC.
     */
    public static SessionUser from(Map<String, Object> row) {
        if (row.isEmpty()) return null;
        Object active = row.get("activo");
        return new SessionUser(
                ((Number) row.get("id")).longValue(),
                ((Number) row.get("comercio_id")).longValue(),
                String.valueOf(row.get("usuario")),
                String.valueOf(row.get("nombre")),
                String.valueOf(row.get("rol")),
                String.valueOf(row.get("permisos")),
                Boolean.TRUE.equals(active)
                        || active instanceof Number number && number.intValue() != 0);
    }
}
