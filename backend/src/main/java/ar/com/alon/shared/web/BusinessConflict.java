// Indica que una operación no puede continuar por el estado actual de los datos.
package ar.com.alon.shared.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class BusinessConflict extends ResponseStatusException {

    /**
     * Crea un conflicto de negocio con estado HTTP 409 y un mensaje que puede mostrar la interfaz.
     */
    public BusinessConflict(String message) {
        super(HttpStatus.CONFLICT, message);
    }
}
