package ar.com.alon.api.operations;

import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = OperationsController.class)
public class OperationsExceptionHandler {
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> invalidInput(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> conflict(IllegalStateException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", exception.getMessage()));
    }

    @ExceptionHandler({DataIntegrityViolationException.class, EmptyResultDataAccessException.class})
    public ResponseEntity<Map<String, String>> invalidReference(Exception exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "No se pudo guardar: revisá los datos relacionados y que el número de documento no esté repetido."));
    }
}
