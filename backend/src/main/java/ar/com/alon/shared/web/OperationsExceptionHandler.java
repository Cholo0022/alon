// Convierte los errores del servidor en mensajes que la interfaz puede mostrar.
package ar.com.alon.shared.web;

import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class OperationsExceptionHandler {
    /**
     * Convierte una validación de negocio fallida en una respuesta 400 con el mensaje de error para
     * el usuario.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> invalidInput(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
    }

    /**
     * Devuelve un conflicto 409 ante duplicados o referencias de datos incompatibles sin revelar
     * detalles SQL.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> invalidReference(Exception exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(
                        Map.of(
                                "error",
                                "Ya existe un registro con esos datos o una referencia no es válida."));
    }

    /** Traduce la ausencia de un registro consultado a una respuesta 404 legible. */
    @ExceptionHandler(EmptyResultDataAccessException.class)
    public ResponseEntity<Map<String, String>> notFound(EmptyResultDataAccessException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "No se encontró el registro solicitado."));
    }

    /** Conserva el código HTTP de una excepción controlada y devuelve su motivo como error JSON. */
    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> status(
            org.springframework.web.server.ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatusCode())
                .body(
                        Map.of(
                                "error",
                                exception.getReason() == null
                                        ? "No se pudo completar la solicitud."
                                        : exception.getReason()));
    }

    /**
     * Responde 400 cuando la estructura, los campos o los formatos de la solicitud no superan la
     * validación.
     */
    @ExceptionHandler({
        org.springframework.web.bind.MethodArgumentNotValidException.class,
        org.springframework.http.converter.HttpMessageNotReadableException.class,
        org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
        jakarta.validation.ConstraintViolationException.class,
        org.springframework.web.method.annotation.HandlerMethodValidationException.class
    })
    public ResponseEntity<Map<String, String>> invalidRequest(Exception exception) {
        return ResponseEntity.badRequest()
                .body(
                        Map.of(
                                "error",
                                "Revisá los campos obligatorios, formatos, límites y cantidades de la solicitud."));
    }

    /**
     * Devuelve un conflicto 409 cuando otra operación impide completar la modificación concurrente.
     */
    @ExceptionHandler(org.springframework.dao.PessimisticLockingFailureException.class)
    public ResponseEntity<Map<String, String>> concurrentChange(Exception exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", "Otra operación modificó estos datos. Volvé a intentar."));
    }

    /**
     * Conserva el código de error del protocolo HTTP y presenta un mensaje sobre ruta, método o
     * formato de la solicitud.
     */
    @ExceptionHandler({
        org.springframework.web.HttpRequestMethodNotSupportedException.class,
        org.springframework.web.HttpMediaTypeNotSupportedException.class,
        org.springframework.web.HttpMediaTypeNotAcceptableException.class,
        org.springframework.web.bind.MissingServletRequestParameterException.class,
        org.springframework.web.servlet.resource.NoResourceFoundException.class
    })
    public ResponseEntity<Map<String, String>> protocolError(Exception exception) {
        var error = (org.springframework.web.ErrorResponse) exception;
        return ResponseEntity.status(error.getStatusCode())
                .body(
                        Map.of(
                                "error",
                                "Revisá la dirección, el método y el formato de la solicitud."));
    }

    /**
     * Registra el fallo técnico en el servidor y responde 500 con un mensaje que no expone datos
     * internos.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> unexpected(Exception exception) {
        org.slf4j.LoggerFactory.getLogger(getClass())
                .error("Fallo inesperado al procesar la solicitud", exception);
        return ResponseEntity.internalServerError()
                .body(Map.of("error", "No se pudo completar la operación. Intentá nuevamente."));
    }
}
